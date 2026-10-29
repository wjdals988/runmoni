#!/usr/bin/env python3
"""Link an existing approved key to GitHub; never generate or print credentials.

--login requests official GitHub CLI web authorization in an isolated temporary
configuration, then links secrets and removes that temporary login configuration.
"""
import argparse
import base64
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[2]
REPO = 'wjdals988/runmoni'
ENVIRONMENT = 'android-release'
POLICY = json.loads((ROOT / 'config/android-release-signing.json').read_text())
KEYTOOL = ROOT / 'work/cloud-android/jdk-21.0.13+9/bin/keytool'


class ConnectionError(Exception):
    pass


def child_env(auth_dir=None):
    env = {k: v for k, v in os.environ.items() if not k.upper().startswith(('GIT_TRACE', 'GCM_TRACE'))
           and k.upper() not in ('GH_DEBUG', 'GIT_CURL_VERBOSE', 'SSLKEYLOGFILE',
                                 'JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS')}
    if auth_dir is not None:
        # A separately consented owner login, not a replacement for injected task auth.
        env.pop('GH_TOKEN', None)
        env.pop('GITHUB_TOKEN', None)
        env['GH_CONFIG_DIR'] = str(auth_dir)
    env.update(GH_HOST='github.com', GIT_TERMINAL_PROMPT='0', GCM_INTERACTIVE='never')
    return env


def approved_inputs(state_file):
    state_file = Path(state_file)
    if not state_file.is_file() or state_file.stat().st_mode & 0o077:
        raise ConnectionError('Signing credential record is missing or not owner-only.')
    if state_file.stat().st_size > 65536:
        raise ConnectionError('Signing credential record exceeds its size limit.')
    data = json.loads(state_file.read_text())
    names = ('keyFile', 'storePassword', 'keyAlias', 'keyPassword')
    if not all(isinstance(data.get(n), str) and data[n] for n in names):
        raise ConnectionError('Signing credential record is incomplete.')
    key = Path(data['keyFile'])
    if not key.is_file() or key.stat().st_mode & 0o077 or not 0 < key.stat().st_size <= 48000:
        raise ConnectionError('Approved private key is missing, oversized, or not owner-only.')
    key_bytes = key.read_bytes()
    env = child_env() | {'RUNMONI_STORE_PASSWORD': data['storePassword']}
    result = subprocess.run([str(KEYTOOL), '-exportcert', '-keystore', str(key), '-storepass:env',
                             'RUNMONI_STORE_PASSWORD', '-alias', data['keyAlias']],
                            env=env, capture_output=True, timeout=30)
    if result.returncode or hashlib.sha256(result.stdout).hexdigest() != POLICY['certificateSha256']:
        raise ConnectionError('Private key does not match the approved distribution certificate.')
    if key.read_bytes() != key_bytes:
        raise ConnectionError('Key changed during verification; no credentials were sent.')
    return {'RUNMONI_STORE_PASSWORD': data['storePassword'].encode(),
            'RUNMONI_KEY_ALIAS': data['keyAlias'].encode(),
            'RUNMONI_KEY_PASSWORD': data['keyPassword'].encode(),
            'RUNMONI_KEYSTORE_BASE64': base64.b64encode(key_bytes)}


def request(arguments, body, env):
    try:
        result = subprocess.run(['gh', *arguments], input=body, capture_output=True, env=env, timeout=60)
    except (OSError, subprocess.TimeoutExpired):
        raise ConnectionError('GitHub command could not complete; raw details omitted.') from None
    if len(result.stdout) > 1048576:
        raise ConnectionError('GitHub response exceeded its size limit; raw details omitted.')
    # Keep enough evidence to distinguish API permissions from malformed requests.
    # Never serialize a raw error body, credential, header value, or secret stdin.
    status = re.search(rb'HTTP/\S+\s+(\d{3})', result.stdout)
    combined = result.stdout + result.stderr
    if status is None:
        status = re.search(rb'HTTP\s+(\d{3})', result.stderr)
    status_code = int(status[1]) if status else None
    kind = ('integration_permission' if b'Resource not accessible by integration' in combined
            else 'validation' if b'Validation Failed' in combined or b'Body should be a JSON' in combined
            else 'authentication' if b'Bad credentials' in combined
            else 'rate_limit' if b'API rate limit' in combined else None)
    scopes = re.search(rb'(?im)^x-oauth-scopes:[ \t]*([^\r\n]*)', result.stdout)
    repo_scope = bool(scopes and b'repo' in [s.strip() for s in scopes[1].split(b',')])
    print(json.dumps({'githubOperation': 'api' if arguments[0] == 'api' else 'secret-set',
                      'exitCode': result.returncode, 'httpStatus': status_code, 'errorKind': kind,
                      'oauthScopesHeaderPresent': scopes is not None, 'repoOAuthScopeGranted': repo_scope}), flush=True)
    return result.returncode, result.stdout


def connect(values, send, scope='repository'):
    if scope not in ('repository', 'environment'):
        raise ConnectionError('Unsupported secret scope; no credentials were sent.')
    if scope == 'environment':
        path = f'repos/{REPO}/environments/{ENVIRONMENT}'
        code, output = send(['api', '--include', path], None)
        status = re.search(rb'HTTP/\S+\s+(\d{3})', output)
        status = int(status[1]) if status else 0
        if status == 404:
            if send(['api', '--include', '--method', 'PUT', path, '--input', '-'], b'{}')[0]:
                raise ConnectionError('GitHub environment creation failed; no signing secrets were sent. See safe HTTP diagnostics.')
        elif code or status != 200:
            raise ConnectionError(f'GitHub environment access failed (HTTP {status}); no signing secrets were sent.')
        public_key_path = path + '/secrets/public-key'
    else:
        # Actions repository secrets avoid the separate Environment admin operation.
        public_key_path = f'repos/{REPO}/actions/secrets/public-key'
    if send(['api', '--include', public_key_path], None)[0]:
        raise ConnectionError('GitHub signing-secret access failed; no signing secrets were sent. See safe HTTP diagnostics.')
    written = []
    for name, value in values.items():
        arguments = ['secret', 'set', name, '--repo', REPO]
        if scope == 'environment':
            arguments += ['--env', ENVIRONMENT]
        if send(arguments, value)[0]:
            raise ConnectionError(f'Signing connection stopped after {len(written)} entries; do not start a release yet.')
        written.append(name)
        print(json.dumps({'connectedSecretName': name, 'valuePrinted': False}), flush=True)
    return {'repository': REPO, 'scope': scope, 'environment': ENVIRONMENT if scope == 'environment' else None,
            'certificateSha256': POLICY['certificateSha256'],
            'connectedSecretNames': written, 'connectedAt': datetime.now(timezone.utc).isoformat(),
            'appPublished': False}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--state-file', required=True, type=Path)
    parser.add_argument('--login', action='store_true', help='Official GitHub CLI web consent in temporary auth config')
    parser.add_argument('--scope', choices=['repository', 'environment'], default='repository')
    args = parser.parse_args(argv)
    try:
        values = approved_inputs(args.state_file)
        with tempfile.TemporaryDirectory(prefix='runmoni-github-auth-') as auth_dir:
            env = child_env(auth_dir if args.login else None)
            if args.login:
                print('GitHub security settings require owner authorization. Use only the official github.com login page.', flush=True)
                result = subprocess.run(['gh', 'auth', 'login', '--hostname', 'github.com', '--git-protocol',
                                         'https', '--web', '--scopes', 'repo'], env=env, timeout=1200)
                if result.returncode:
                    raise ConnectionError('GitHub owner authorization did not complete.')
            receipt = connect(values, lambda arguments, body: request(arguments, body, env), args.scope)
            output = ROOT / 'work/signing-connection/receipt.json'
            output.parent.mkdir(parents=True, exist_ok=True)
            output.write_text(json.dumps(receipt, indent=2) + '\n')
            print(json.dumps(receipt, indent=2))
        return 0
    except Exception as error:
        message = str(error) if isinstance(error, ConnectionError) else f'Connection failed ({type(error).__name__}); raw details omitted.'
        print(json.dumps({'ok': False, 'error': message}), file=sys.stderr)
        return 1


if __name__ == '__main__':
    raise SystemExit(main())
