#!/usr/bin/env python3
"""Build and verify a release on Linux. Never creates keys or publishes an APK.

Signed builds require the retained .secrets/signing.properties or four explicit
RUNMONI_* signing variables. --unsigned produces a separate, unpublishable candidate.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
from urllib.parse import urlsplit
import zipfile

ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT / 'work/cloud-android'
SIGNING_VARIABLES = ('RUNMONI_KEYSTORE_FILE', 'RUNMONI_STORE_PASSWORD',
                     'RUNMONI_KEY_ALIAS', 'RUNMONI_KEY_PASSWORD')
CERTIFICATE = json.loads((ROOT / 'config/android-release-signing.json').read_text())['certificateSha256']


class ReleaseError(Exception):
    pass


def clean_env():
    # Prevent inherited diagnostic options from recording signing credentials.
    return {k: v for k, v in os.environ.items()
            if not k.upper().startswith(('GIT_TRACE', 'GCM_TRACE'))
            and k.upper() not in ('GIT_CURL_VERBOSE', 'SSLKEYLOGFILE',
                                 'JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS')}


def failure_categories(output):
    """Return fixed labels only; never copy build text or credential values."""
    patterns = {
        'plugin_resolution': r'Plugin \[id:.*?was not found|plugin.*could not be resolved',
        'dependency_resolution': r'Could not resolve|Could not download|Could not find [\w.]+:',
        'tls_trust': r'PKIX|SSLHandshakeException|certificate_unknown|unable to find valid certification path',
        'sdk_license': r'licenses? (?:have not been|not) accepted|LicenseNotAcceptedException',
        'sdk_location': r'SDK location not found|Failed to find Platform SDK|failed to find target with hash string',
        'memory_limit': r'OutOfMemoryError|Java heap space|unable to create native thread|daemon disappeared',
        'initialization_script': r'Could not compile initialization script|Could not open.*init.*cache',
        'http_403': r'status code 403|HTTP[^\r\n]*403',
        'http_429': r'status code 429|HTTP[^\r\n]*429',
        'connection_timeout': r'ConnectTimeoutException|Read timed out|connect timed out|Connection timed out',
    }
    categories = [label for label, pattern in patterns.items() if re.search(pattern, output, re.I | re.S)]
    # Only fixed, public repository hostnames can appear in diagnostics.
    for host in ('dl.google.com', 'repo.maven.apache.org', 'plugins.gradle.org',
                 'repository.map.naver.com', 'jitpack.io'):
        if host in output:
            categories.append('repository:' + host)
    return categories or ['unclassified']


def public_failure_detail(output, env, redactions=()):
    """Bound Gradle's error block after removing known credentials and URL data."""
    match = re.search(r'\* What went wrong:\s*\n(.*?)(?:\n\* (?:Try|Exception is):|\Z)', output, re.S)
    if not match:
        return ''
    detail = match[1]
    values = list(redactions) + [value for name, value in env.items()
                               if re.search(r'TOKEN|PASSWORD|SECRET|AUTH|KEY', name, re.I)]
    for value in sorted((v for v in values if v), key=len, reverse=True):
        detail = detail.replace(value, '[credential omitted]')
    detail = re.sub(r'https?://[^\s<>\"\']+', '[URL omitted]', detail)
    detail = re.sub(r'(?i)(?:gh[pousr]_|github_pat_)[A-Za-z0-9_]+', '[token omitted]', detail)
    return detail.strip()[:2000]


def checked(command, env, *, public_diagnostics=False, redactions=()):
    result = subprocess.run([str(c) for c in command], cwd=ROOT, env=env,
                            capture_output=True, text=True, timeout=1800)
    if result.returncode:
        # Gradle signing exceptions can contain configuration values.
        diagnostics = result.stdout + '\n' + result.stderr
        tasks = re.findall(r'^> Task (:[A-Za-z0-9_:]+) FAILED$', diagnostics, re.M)
        detail = ' Failed tasks: ' + ', '.join(tasks) if tasks else ''
        categories = ', '.join(failure_categories(diagnostics))
        message = ('Tool failed; raw signing/build diagnostics were omitted.'
                   + detail + ' Failure categories: ' + categories
                   + ' Tool: ' + Path(command[0]).name + '; exitCode: ' + str(result.returncode))
        if (public_diagnostics and not any(env.get(name) for name in SIGNING_VARIABLES)
                and not (ROOT / '.secrets/signing.properties').exists()):
            public = public_failure_detail(diagnostics, env, redactions)
            if public:
                message += ' Redacted uncredentialed Gradle error: ' + public
        raise ReleaseError(message)
    return result.stdout


def source_revision(env):
    if checked(['git', 'status', '--porcelain', '--untracked-files=normal'], env).strip():
        raise ReleaseError('Commit source changes before producing a release candidate.')
    revision = checked(['git', 'rev-parse', 'HEAD'], env).strip()
    if not re.fullmatch('[0-9a-f]{40}', revision):
        raise ReleaseError('Expected a full source commit.')
    return revision


def inspect_apk(badging, signature, *, unsigned=False):
    package = re.search(r"^package: name='([^']+)' versionCode='([0-9]+)' versionName='([^']+)'", badging, re.M)
    if not package or package[1] != 'com.runmoni.app':
        raise ReleaseError('Unexpected APK package or missing version.')
    if 'application-debuggable' in badging:
        raise ReleaseError('Debuggable APKs cannot enter the release workflow.')
    version = package[3]
    if not re.fullmatch(r'[0-9]+\.[0-9]+\.[0-9]+-[A-Za-z0-9]+(?:[.-][A-Za-z0-9]+)*', version):
        raise ReleaseError('Expected a development preview version.')
    signers = re.findall(r'^Signer #[0-9]+ certificate SHA-256 digest: ([0-9a-fA-F]{64})$', signature, re.M)
    if not unsigned and [s.lower() for s in signers] != [CERTIFICATE]:
        raise ReleaseError('APK signer differs from the approved Runmoni distribution certificate.')
    feed = json.loads((ROOT / 'updates/android.json').read_text())
    # This is the committed public baseline; remote publication is checked separately.
    previous = feed.get('preview', {})
    previous_code = previous.get('versionCode')
    if type(previous_code) is not int or int(package[2]) <= previous_code:
        raise ReleaseError('APK versionCode must exceed the public baseline.')
    return {'version': version, 'versionCode': int(package[2]), 'packageName': package[1]}


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def preserve(path, content):
    if path.exists():
        if path.read_bytes() != content:
            raise ReleaseError('Existing release output differs; nothing was overwritten.')
    else:
        with path.open('xb') as stream:
            stream.write(content)


def build(args):
    env = clean_env()
    revision = source_revision(env)
    jdk = BASE / 'jdk-21.0.13+9'
    sdk = BASE / 'sdk'
    tools = sdk / 'build-tools/36.0.0'
    required = [jdk / 'bin/java', BASE / 'gradle-8.13/bin/gradle', tools / 'apksigner', tools / 'aapt2']
    if not all(p.is_file() for p in required):
        raise ReleaseError('Prepare the pinned toolchain with prepare-android.py --build-only first.')
    pidfile = BASE / 'emulator.pid'
    if pidfile.exists():
        proc = Path('/proc') / pidfile.read_text().strip() / 'cmdline'
        if proc.exists() and b'Runmoni_Cloud_29' in proc.read_bytes():
            raise ReleaseError('Stop the owned cloud AVD before building.')
    local_signing = (ROOT / '.secrets/signing.properties').is_file()
    present = [bool(env.get(n)) for n in SIGNING_VARIABLES]
    if not args.unsigned and not all(present) and not local_signing:
        raise ReleaseError('Retained release signing inputs are missing. No replacement key was generated.')
    if not args.unsigned and any(present) and not all(present):
        raise ReleaseError('Signing environment is incomplete; no fallback key was used.')
    env.update(JAVA_HOME=str(jdk), ANDROID_HOME=str(sdk),
               ANDROID_USER_HOME=str(BASE / 'user'), GRADLE_USER_HOME=str(BASE / 'gradle-home'))
    # Export only the public DER certificate to memory; reject a wrong key before building.
    if not args.unsigned and all(present):
        key = Path(env['RUNMONI_KEYSTORE_FILE']).resolve()
        if not key.is_file():
            raise ReleaseError('Configured retained keystore file is missing.')
        certificate = subprocess.run([str(jdk / 'bin/keytool'), '-exportcert', '-keystore', str(key),
                        '-storepass:env', 'RUNMONI_STORE_PASSWORD', '-alias', env['RUNMONI_KEY_ALIAS']],
                        cwd=ROOT, env=env, capture_output=True, timeout=30)
        if certificate.returncode or hashlib.sha256(certificate.stdout).hexdigest() != CERTIFICATE:
            raise ReleaseError('Retained key could not be verified against the public certificate.')
    with tempfile.TemporaryDirectory(prefix='release-', dir=BASE) as temporary:
        temp = Path(temporary)
        init = temp / 'release.init.gradle'
        signing = 'android.buildTypes.release.signingConfig = null\n' if args.unsigned or all(present) else ''
        init.write_text("allprojects { plugins.withId('com.android.application') {\n"
                        "android.buildToolsVersion = '36.0.0'\n" + signing + '} }\n')
        command = [BASE / 'gradle-8.13/bin/gradle', '-p', ROOT, '-I', init,
                   '--no-daemon', '--no-parallel', '--max-workers=1', '--console=plain']
        if args.online:
            proxy = urlsplit(env.get('HTTPS_PROXY', ''))
            if proxy.hostname:
                command += [f'-Dhttps.proxyHost={proxy.hostname}', f'-Dhttps.proxyPort={proxy.port or 80}',
                            f'-Dhttp.proxyHost={proxy.hostname}', f'-Dhttp.proxyPort={proxy.port or 80}']
            command += ['-Djavax.net.ssl.trustStore=' + str(BASE / 'proxy-truststore'),
                        '-Djavax.net.ssl.trustStorePassword=changeit', '-Dorg.gradle.internal.http.maxConnections=1']
        else:
            command += ['--offline']
        command += [':app:assembleRelease', ':app:testDebugUnitTest', ':app:lintRelease']
        print(json.dumps({'phase': 'release_build', 'sourceRevision': revision, 'unsigned': args.unsigned}), flush=True)
        # Gradle produces the unsigned APK; only the later apksigner needs these.
        build_env = {name: value for name, value in env.items() if name not in SIGNING_VARIABLES}
        output = checked(command, build_env, public_diagnostics=not local_signing,
                         redactions=[env.get(name, '') for name in SIGNING_VARIABLES])
        # Persist only successful output, with configured credentials removed even if a tool echoed one.
        for name in SIGNING_VARIABLES:
            if env.get(name):
                output = output.replace(env[name], '[signing input omitted]')
        (temp / 'build.txt').write_text(output)
        input_apk = ROOT / 'app/build/outputs/apk/release' / (
            'app-release-unsigned.apk' if args.unsigned or all(present) else 'app-release.apk')
        apk = temp / 'candidate.apk'
        if not args.unsigned and all(present):
            checked([tools / 'apksigner', 'sign', '--ks', key, '--ks-key-alias', env['RUNMONI_KEY_ALIAS'],
                     '--ks-pass', 'env:RUNMONI_STORE_PASSWORD', '--key-pass', 'env:RUNMONI_KEY_PASSWORD',
                     '--out', apk, input_apk], env)
        else:
            shutil.copyfile(input_apk, apk)
        signature = '' if args.unsigned else checked([tools / 'apksigner', 'verify', '--verbose', '--print-certs', apk], env)
        facts = inspect_apk(checked([tools / 'aapt2', 'dump', 'badging', apk], env), signature, unsigned=args.unsigned)
        if source_revision(env) != revision:
            raise ReleaseError('Source changed during the build; candidate was not exported.')
        with zipfile.ZipFile(apk) as package:
            embedded = [n for n in package.namelist() if n.endswith('/version-control-info.textproto')]
            if len(embedded) != 1 or f'revision: "{revision}"' not in package.read(embedded[0]).decode():
                raise ReleaseError('Embedded APK source revision differs from the build commit.')
        version = facts['version']
        directory = ROOT / 'outputs' if not args.unsigned else ROOT / 'outputs/release-candidate' / revision
        directory.mkdir(parents=True, exist_ok=True)
        name = f'runmoni-{version}' + ('-unsigned' if args.unsigned else '') + '.apk'
        metadata = {**facts, 'fileName': name, 'sizeBytes': apk.stat().st_size, 'sha256': digest(apk),
                    'signed': not args.unsigned, 'sourceRevision': revision, 'sourceDirty': False}
        if args.unsigned:
            metadata['productionUpdateCompatible'] = False
        else:
            metadata['certificateSha256'] = CERTIFICATE
        # Validate all destinations before creating any canonical file.
        files = {name: apk.read_bytes(), name.removesuffix('.apk') + '.metadata.json':
                 (json.dumps(metadata, indent=2) + '\n').encode(),
                 f'SHA256SUMS-{version}' + ('-unsigned' if args.unsigned else '') + '.txt':
                 f"{metadata['sha256']}  {name}\n".encode()}
        for filename, data in files.items():
            if (directory / filename).exists() and (directory / filename).read_bytes() != data:
                raise ReleaseError('Existing release output differs; nothing was overwritten.')
        for filename, data in files.items():
            preserve(directory / filename, data)
        preserve(directory / (name + '.build.txt'), output.encode())
        print(json.dumps({'outputDirectory': str(directory), **metadata}, indent=2))


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--unsigned', action='store_true', help='Separate unpublishable build, never public output')
    parser.add_argument('--online', action='store_true', help='Only for missing dependency caches')
    args = parser.parse_args(argv)
    try:
        build(args)
        return 0
    except Exception as error:
        message = str(error) if isinstance(error, ReleaseError) else f'Operation failed ({type(error).__name__}); raw details omitted.'
        print(json.dumps({'ok': False, 'error': message}), file=sys.stderr)
        return 1


if __name__ == '__main__':
    raise SystemExit(main())
