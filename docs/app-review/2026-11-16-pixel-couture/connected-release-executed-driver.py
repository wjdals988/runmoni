#!/usr/bin/env python3
"""Future0.7.1 connected CI/draft gate. No request occurs until main() is run.

Every commit/run/attempt/count is supplied explicitly. Default verifies only;
--publish changes only an already-verified draft. --public-only also works
after main advances to a documentation commit, preserving the actual APK C.
Never uploads/replaces assets, reads Secrets, dispatches CI, or downloads APK bodies.
"""
import argparse
import base64
import datetime as dt
import hashlib
import io
import json
import os
from pathlib import Path
import re
import subprocess
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET
import zipfile

ROOT = next(p for p in Path(__file__).resolve().parents if (p / 'app/build.gradle.kts').exists())
WORK = ROOT / 'work/pixel-couture'
REPO, VERSION, CODE = 'wjdals988/runmoni', '0.7.1-dev', 30
CERTIFICATE = 'a21359c657f6797f5fb8229c3098e48bc72a45f9d25269f8d41ff5122465df4a'
TAG = 'v' + VERSION
WEB = 'https://github.com/' + REPO
BUILD_STEP = 'Build, test, sign and verify retained certificate'
REQUIRED_STEPS = ['Validate requested mode and restore retained key',
    'Inspect connections and inject Android client configuration', 'Retain sanitized connection readiness',
    'Prepare verified build tools, without an emulator', BUILD_STEP,
    'Upload and verify private release draft', 'Retain small verification reports',
    'Remove temporary signing and Android configuration files']


def require(value, explanation):
    if not value: raise RuntimeError(explanation)


def sha(raw): return hashlib.sha256(raw).hexdigest()
def utc(): return dt.datetime.now(dt.timezone.utc).isoformat()
def instant(value):
    parsed = dt.datetime.fromisoformat(value.replace('Z', '+00:00'))
    return parsed.replace(tzinfo=dt.timezone.utc) if parsed.tzinfo is None else parsed


def atomic(path, data):
    temporary = path.with_suffix(path.suffix + '.tmp')
    with temporary.open('w') as output:
        json.dump(data, output, ensure_ascii=False, indent=2); output.write('\n')
        output.flush(); os.fsync(output.fileno())
    os.replace(temporary, path)
    descriptor = os.open(path.parent, os.O_RDONLY | os.O_DIRECTORY)
    try: os.fsync(descriptor)
    finally: os.close(descriptor)


def child_env():
    return {key: value for key, value in os.environ.items()
            if not key.upper().startswith(('GIT_TRACE', 'GCM_TRACE'))
            and key.upper() not in ('GH_DEBUG', 'GIT_CURL_VERBOSE', 'SSLKEYLOGFILE')}


def api(path, method='GET', body=None, binary=False, limit=1048576, optional=False):
    args = ['gh', 'api', 'repos/' + REPO + '/' + path]
    if method != 'GET': args += ['--method', method, '--input', '-']
    if binary and path.startswith('releases/assets/'):
        args += ['-H', 'Accept: application/octet-stream']
    result = subprocess.run(args, input=None if body is None else json.dumps(body).encode(),
                            capture_output=True, timeout=90, env=child_env(), cwd=ROOT)
    if result.returncode:
        if optional and b'HTTP 404' in result.stderr: return None
        raise RuntimeError('GitHub operation failed; credentials/raw response omitted')
    require(len(result.stdout) <= limit, 'Bounded GitHub response exceeded its limit')
    return result.stdout if binary else json.loads(result.stdout)


def tag_commit(source, required=False):
    reference = api('git/ref/tags/' + TAG, optional=not required)
    if reference is None: return
    obj = reference['object']
    for _ in range(5):
        if obj.get('type') != 'tag': break
        require(re.fullmatch('[a-f0-9]{40}', obj.get('sha', '')), 'Tag object SHA format')
        obj = api('git/tags/' + obj['sha'])['object']
    require(obj.get('type') == 'commit' and obj.get('sha') == source, 'Version tag must retain actual APK source C')


def remote_asset(asset, release):
    tag = release['tag_name']
    require(tag == TAG or release['draft'] and re.fullmatch('untagged-[a-f0-9]{8,64}', tag), 'Pinned/temporary release tag')
    temporary = re.fullmatch(re.escape(WEB + '/releases/tag/') + r'(untagged-[a-f0-9]{8,64})', release.get('html_url', ''))
    if release['draft'] and temporary:
        tag = temporary[1]
    require(asset.get('state') == 'uploaded' and re.fullmatch('sha256:[0-9a-f]{64}', asset.get('digest', '')),
            'Uploaded asset digest is required')
    require(asset['browser_download_url'] == WEB + '/releases/download/' + tag + '/' + asset['name'], 'Pinned asset URL')


def verify_metadata(meta, apk, source):
    for key, expected in {'version': VERSION, 'versionCode': CODE, 'packageName': 'com.runmoni.app',
                          'fileName': f'runmoni-{VERSION}.apk', 'sourceRevision': source,
                          'sourceDirty': False, 'signed': True, 'certificateSha256': CERTIFICATE}.items():
        require(meta.get(key) == expected and (key not in ['sourceDirty', 'signed'] or type(meta[key]) is bool), 'Signed metadata mismatch: ' + key)
    require(type(meta['versionCode']) is int and type(meta.get('sizeBytes')) is int, 'Integer APK declarations')
    require(0 < meta['sizeBytes'] <= 512 * 1024 * 1024 and meta['sizeBytes'] == apk['size'], 'APK metadata/remote size')
    require(re.fullmatch('[0-9a-f]{64}', meta.get('sha256', '')) and 'sha256:' + meta['sha256'] == apk['digest'], 'APK metadata/remote SHA256')
    require(not any(key in meta for key in ['publiclyReleased', 'sourcePublicationConfirmed', 'validatedSourceCommit']), 'No rewritten publication claims in immutable sidecar')
    readiness = meta.get('connectionReadiness', {})
    for key, value in {'sourceRevision': source, 'ready': True, 'certificateVerified': True,
                       'certificateSha256': CERTIFICATE, 'firebase': 'ready', 'server': 'ready', 'backend': 'firebase'}.items():
        require(readiness.get(key) is value if isinstance(value, bool) else readiness.get(key) == value, 'Connected readiness mismatch: ' + key)
    for key in ['physicalDeviceVerified', 'interactiveGoogleLoginVerified', 'anonymousProviderVerified', 'publicRoomWritesVerified']:
        require(readiness.get(key) is False, 'CI readiness may not invent interactive/operating-write validation')
    for key, expected in [('downloadUrl', WEB + f'/releases/download/{TAG}/{meta["fileName"]}'), ('releaseUrl', WEB + '/releases/tag/' + TAG)]:
        require(key not in meta or meta[key] == expected, 'Pinned optional metadata URL')


def clean_log(raw):
    text = raw.decode('utf-8', 'replace')
    text = re.sub(r'\x1b\[[0-9;]*[A-Za-z]', '', text)
    return re.sub(r'(?m)^\d{4}-\d\d-\d\dT\S+\s', '', text)


def verify_ci(args, folder):
    run = api(f'actions/runs/{args.ci_run}/attempts/{args.ci_attempt}')
    require(run['head_sha'] == args.source and run['head_branch'] == 'main' and run['event'] == 'workflow_dispatch'
            and run['status'] == 'completed' and run['conclusion'] == 'success' and run['run_attempt'] == args.ci_attempt,
            'Exact source/run/attempt must have completed successfully')
    require(run['repository']['full_name'] == REPO and run['head_repository']['full_name'] == REPO,
            'Workflow repository cannot be a fork')
    require(run['path'].split('@')[0] == '.github/workflows/android-release.yml', 'Approved release workflow only')
    jobs_result = api(f'actions/runs/{args.ci_run}/attempts/{args.ci_attempt}/jobs?per_page=100')
    require(jobs_result['total_count'] == len(jobs_result['jobs']) == 1, 'Expected the complete single candidate job')
    job = jobs_result['jobs'][0]
    require(job['name'] == 'candidate' and job['status'] == 'completed' and job['conclusion'] == 'success', 'Successful candidate job')
    steps = {step['name']: step for step in job['steps']}
    require(len(steps) == len(job['steps']), 'Unique CI step names')
    for name in REQUIRED_STEPS: require(steps.get(name, {}).get('conclusion') == 'success', 'Required release step: ' + name)
    require(steps.get('Build, test and inspect unsigned candidate', {}).get('conclusion') == 'skipped', 'This is signed stage-draft, never unsigned')
    raw_log = api(f'actions/jobs/{job["id"]}/logs', binary=True, limit=8 * 1024 * 1024)
    (folder / 'private-job-log.txt').write_bytes(raw_log)
    log = clean_log(raw_log)
    require(set(re.findall(r'(?m)^\s*MODE:\s*([a-z-]+)\s*$', log)) == {'stage-draft'}, 'Actual CI MODE log must show stage-draft')
    require(set(re.findall(r'(?m)^\s*PROFILE:\s*([a-z-]+)\s*$', log)) == {'connected'}, 'Actual CI PROFILE log must show connected')
    source_events = []
    for line in log.splitlines():
        if line.lstrip().startswith('{'):
            try: event = json.loads(line)
            except json.JSONDecodeError: continue
            if isinstance(event, dict) and event.get('phase') == 'release_build': source_events.append(event)
    require(len(source_events) == 1 and source_events[0].get('sourceRevision') == args.source and source_events[0].get('unsigned') is False,
            'This job actually invoked signed build for C')
    artifacts = api(f'actions/runs/{args.ci_run}/artifacts?per_page=100')
    require(artifacts['total_count'] == len(artifacts['artifacts']), 'Complete bounded artifact listing')
    matches = [item for item in artifacts['artifacts'] if item['name'] == 'runmoni-verification-' + args.source]
    require(len(matches) == 1, 'One exact-source verification artifact')
    artifact = matches[0]
    require(artifact['expired'] is False and artifact['workflow_run']['id'] == args.ci_run and artifact['workflow_run']['head_sha'] == args.source,
            'Reports belong to this actual CI run')
    require(instant(job['started_at']) <= instant(artifact['created_at']) <= instant(job['completed_at']), 'Artifact from this CI attempt, not a previous attempt')
    require(0 < artifact['size_in_bytes'] <= 8 * 1024 * 1024 and re.fullmatch('sha256:[0-9a-f]{64}', artifact.get('digest', '')),
            'Bounded verification archive with server digest')
    raw_archive = api(f'actions/artifacts/{artifact["id"]}/zip', binary=True, limit=8 * 1024 * 1024)
    require(len(raw_archive) == artifact['size_in_bytes'] and 'sha256:' + sha(raw_archive) == artifact['digest'], 'Fresh report archive size/digest')
    (folder / 'private-verification.zip').write_bytes(raw_archive)
    build_start, build_end = instant(steps[BUILD_STEP]['started_at']), instant(steps[BUILD_STEP]['completed_at'])
    junit, lint_raw, build_raw, signer = [], [], [], []
    with zipfile.ZipFile(io.BytesIO(raw_archive)) as archive:
        members = [item for item in archive.infolist() if not item.is_dir()]
        require(len(members) <= 512 and len({item.filename for item in members}) == len(members), 'Bounded unique archive entries')
        require(sum(item.file_size for item in members) <= 32 * 1024 * 1024, 'Bounded expanded verification reports')
        for item in members:
            name = item.filename
            require(not name.startswith('/') and '..' not in Path(name).parts and not name.lower().endswith(('.apk', '.aab', '.jks', '.p12')),
                    'Reports never contain APK or signing material')
            if re.fullmatch(r'app/build/test-results/testDebugUnitTest/TEST-[^/]+\.xml', name):
                junit.append((name, archive.read(item)))
            elif name == 'app/build/reports/lint-results-release.xml': lint_raw.append(archive.read(item))
            elif name == f'outputs/runmoni-{VERSION}.apk.build.txt': build_raw.append(archive.read(item))
            elif name == 'config/android-release-signing.json': signer.append(archive.read(item))
    require(junit and len(lint_raw) == len(build_raw) == len(signer) == 1, 'Complete actual JVM/lint/build/certificate reports')
    counts = dict.fromkeys(['tests', 'failures', 'errors', 'skipped'], 0)
    suite_names = set()
    report_hashes = {}
    for name, raw in junit:
        require(b'<!DOCTYPE' not in raw and b'<!ENTITY' not in raw, 'No external XML entities')
        suite = ET.fromstring(raw)
        require(suite.tag == 'testsuite' and suite.get('name') not in suite_names, 'Unique JUnit test suites')
        suite_names.add(suite.get('name'))
        timestamp = instant(suite.attrib['timestamp'])
        require(build_start <= timestamp <= build_end, 'JUnit report timestamps must be inside this actual signed build')
        require(len(suite.findall('testcase')) == int(suite.get('tests', 0)), 'JUnit cases agree with declared count')
        for key in counts: counts[key] += int(suite.get(key, 0))
        report_hashes[name] = sha(raw)
    require(counts['tests'] == args.expected_jvm and all(counts[key] == 0 for key in ['failures', 'errors', 'skipped']), 'Exact fresh JVM count; no failures/errors/skips')
    lint = ET.fromstring(lint_raw[0])
    severities = {severity: sum(issue.get('severity') == severity for issue in lint.findall('issue')) for severity in ['Error', 'Fatal', 'Warning', 'Information']}
    require(severities == {'Error': 0, 'Fatal': 0, 'Warning': args.expected_lint_warnings, 'Information': args.expected_lint_information}, 'Exact actual CI lint counts')
    build = clean_log(build_raw[0])
    for task in ['testDebugUnitTest', 'lintRelease']:
        lines = re.findall(r'(?m)^> Task :app:' + task + r'([^\r\n]*)$', build)
        require(len(lines) == 1 and not lines[0].strip(), 'Task executed freshly, without UP-TO-DATE/FROM-CACHE/SKIPPED: ' + task)
    require('BUILD SUCCESSFUL' in build and 'BUILD FAILED' not in build, 'Successful actual signed Gradle execution')
    require(json.loads(signer[0]).get('certificateSha256') == CERTIFICATE, 'Retained signer in CI reports')
    return {'ciRun': args.ci_run, 'ciAttempt': args.ci_attempt, 'sourceRevision': args.source,
            'workflowSuccess': True, 'actualMode': 'stage-draft', 'actualProfile': 'connected',
            'inputsObservedInPrivateJobLog': True, 'privateJobLogSha256': sha(raw_log),
            'artifactId': artifact['id'], 'archiveSha256': sha(raw_archive), 'artifactCreatedAt': artifact['created_at'],
            'jvm': counts, 'lint': severities, 'junitReportSha256': report_hashes,
            'readinessInspection': {key: steps['Inspect connections and inject Android client configuration'][key] for key in ['started_at', 'completed_at']},
            'signedApkInspectedByCi': True, 'embeddedRevisionAndNonDebuggableVersionInspectedByCi': True,
            'apkBodyDownloadedByThisHelper': False, 'apkSignerIndependentlyInspectedByThisHelper': False}


def asset_fingerprint(release):
    return sorted((asset['id'], asset['name'], asset['size'], asset['digest'], asset['state']) for asset in release['assets'])


def source_release_notes(source):
    document = api(f'contents/docs/RELEASE_NOTES_{VERSION}.md?ref={source}')
    require(document.get('type') == 'file' and document.get('encoding') == 'base64' and 0 < document.get('size', 0) <= 65536,
            'Bounded release notes from actual source C')
    raw = base64.b64decode(document['content'])
    require(len(raw) == document['size'] and hashlib.sha1(b'blob ' + str(len(raw)).encode() + b'\0' + raw).hexdigest() == document['sha'],
            'Actual-source release notes Git blob')
    return raw.decode('utf-8'), sha(raw)


def public_assets(release, assets, sidecars, metadata):
    result = []
    for name, asset in assets.items():
        request = urllib.request.Request(asset['browser_download_url'], method='HEAD')
        with urllib.request.urlopen(request, timeout=30) as response:
            require(response.status == 200 and int(response.headers.get('Content-Length', -1)) == asset['size'], 'Public asset HEAD size/status')
        item = {'name': name, 'httpHeadStatus': 200, 'contentLength': asset['size'], 'matchesVerifiedMetadata': True}
        if name != metadata['fileName']:
            with urllib.request.urlopen(asset['browser_download_url'], timeout=30) as response:
                raw = response.read(65537); require(response.status == 200 and len(raw) <= 65536, 'Bounded public sidecar')
            require(raw == sidecars[name], 'Public sidecar bytes equal the verified actual-source C sidecar')
            item.update(publicBodyCompared=True, sha256=sha(raw))
        else: item['apkBodyDownloaded'] = False
        result.append(item)
    return result


def run(args):
    os.umask(0o077)
    require(re.fullmatch('[a-f0-9]{40}', args.source), 'Actual full40-character source C is mandatory')
    require(re.fullmatch('[a-z0-9][a-z0-9-]{0,47}', args.evidence_name), 'Fresh safe evidence name')
    require(json.loads((ROOT / 'config/android-release-signing.json').read_text())['certificateSha256'] == CERTIFICATE, 'Local certificate pin has not changed')
    folder = WORK / f'connected-release-{args.ci_run}-attempt{args.ci_attempt}' / args.evidence_name
    folder.mkdir(parents=True, exist_ok=False)
    source = Path(__file__).read_bytes(); (folder / 'executed-driver.py').write_bytes(source)
    proof = {'version': VERSION, 'versionCode': CODE, 'sourceRevision': args.source, 'ciRun': args.ci_run, 'ciAttempt': args.ci_attempt,
             'executedDriverSha256': sha(source), 'startedAt': utc(), 'passed': False, 'publishRequested': args.publish,
             'publicOnly': args.public_only, 'apkBodyDownloaded': False, 'assetsReuploadedOrReplaced': False,
             'physicalDeviceVerified': False, 'interactiveGoogleLoginVerified': False}
    def checkpoint(phase):
        proof['phase'] = phase; proof['updatedAt'] = utc(); atomic(folder / 'proof.json', proof)
        print(json.dumps({'phase': phase, 'version': VERSION, 'sourceRevision': args.source, 'ciRun': args.ci_run}), flush=True)
    checkpoint('started')
    try:
        if not args.public_only: require(api('git/ref/heads/main')['object']['sha'] == args.source, 'Main must still equal the actual reviewed build C')
        proof['ci'] = verify_ci(args, folder); checkpoint('fresh-connected-ci-verified')
        releases = api('releases?per_page=100')
        matches = [release for release in releases if release.get('target_commitish') == args.source and
                   (release['tag_name'] == TAG or release.get('draft') and release.get('name') == f'런모니 {VERSION} — 서명된 Android 개발 프리뷰')]
        require(len(matches) == 1, 'One exact-source version release, never an older asset set')
        release = api('releases/' + str(matches[0]['id']))
        require(release['prerelease'] is True and release['target_commitish'] == args.source, 'Actual-source development prerelease')
        expected_names = {f'runmoni-{VERSION}.apk', f'runmoni-{VERSION}.metadata.json', f'SHA256SUMS-{VERSION}.txt'}
        assets = {asset['name']: asset for asset in release['assets']}
        require(len(release['assets']) == 3 and set(assets) == expected_names, 'Exactly3 canonical assets')
        sidecars = {}
        for name, asset in assets.items():
            remote_asset(asset, release)
            if name.endswith('.apk'): continue
            require(0 < asset['size'] <= 65536, 'Bounded sidecar only')
            raw = api('releases/assets/' + str(asset['id']), binary=True, limit=65536)
            require(len(raw) == asset['size'] and 'sha256:' + sha(raw) == asset['digest'], 'Actual sidecar size/digest')
            sidecars[name] = raw; (folder / name).write_bytes(raw)
        metadata = json.loads(sidecars[f'runmoni-{VERSION}.metadata.json'])
        verify_metadata(metadata, assets[f'runmoni-{VERSION}.apk'], args.source)
        require(sidecars[f'SHA256SUMS-{VERSION}.txt'].decode().strip() == metadata['sha256'] + '  ' + metadata['fileName'], 'Exact one-line APK checksum')
        readiness_time = instant(metadata['connectionReadiness']['checkedAt'])
        inspection = proof['ci']['readinessInspection']
        require(instant(inspection['started_at']) <= readiness_time <= instant(inspection['completed_at']), 'Readiness was produced by this actual connected CI inspection')
        notes, notes_sha = source_release_notes(args.source)
        require('실기기 검증 전 개발 프리뷰' in notes, 'Honest development preview release notes')
        body = notes + f"\n\nAPK SHA-256: `{metadata['sha256']}`\nAPK bytes: {metadata['sizeBytes']}\nSource: `{args.source}`\n"
        require(release['body'].replace('\r\n', '\n') == body.replace('\r\n', '\n'), 'Exact actual-source release notes')
        tag_commit(args.source, required=not release['draft'])
        proof.update(metadata=metadata, releaseId=release['id'], draft=release['draft'], publicationReady=True,
                     releaseNotesSourceRevision=args.source, releaseNotesSha256=notes_sha,
                     assets=[{key: asset[key] for key in ['id', 'name', 'size', 'digest', 'state']} for asset in assets.values()])
        checkpoint('draft-and-signed-sidecars-verified')
        if args.publish:
            require(api('git/ref/heads/main')['object']['sha'] == args.source, 'Main source before publish')
            fresh = api('releases/' + str(release['id']))
            require(fresh['draft'] == release['draft'] and fresh['target_commitish'] == args.source and fresh['body'] == release['body'] and
                    asset_fingerprint(fresh) == asset_fingerprint(release), 'Draft changed after validation; nothing is published')
            if fresh['draft']:
                proof['publicationMutationRequested'] = True; checkpoint('requesting-publication')
                api('releases/' + str(release['id']), method='PATCH', body={'tag_name': TAG, 'target_commitish': args.source,
                    'draft': False, 'prerelease': True, 'make_latest': 'false'})
                proof['publicationResponseReceived'] = True
            release = api('releases/' + str(release['id']))
        if args.publish or args.public_only:
            require(release['draft'] is False and release['prerelease'] is True and release['tag_name'] == TAG and
                    release['target_commitish'] == args.source and release['html_url'] == WEB + '/releases/tag/' + TAG, 'Public actual-source prerelease')
            final_assets = {asset['name']: asset for asset in release['assets']}
            require(len(release['assets']) == 3 and {name: asset['id'] for name, asset in final_assets.items()} ==
                    {name: asset['id'] for name, asset in assets.items()}, 'Publication did not replace assets')
            for name, asset in final_assets.items():
                remote_asset(asset, release)
                require((asset['size'], asset['digest']) == (assets[name]['size'], assets[name]['digest']), 'Public immutable asset facts')
            tag_commit(args.source, required=True)
            proof.update(draft=False, publicReleaseConfirmed=True, releaseUrl=release['html_url'], publishedAt=release['published_at'])
            checkpoint('public-release-confirmed-awaiting-public-asset-checks')
            proof['publicAssets'] = public_assets(release, final_assets, sidecars, metadata)
        proof.update(passed=True, completed=True, finishedAt=utc())
        checkpoint('public-verified' if proof.get('publicReleaseConfirmed') else 'verified-without-publication')
        return 0
    except Exception as failure:
        proof.update(passed=False, completed=True, failureType=type(failure).__name__,
                     failure=str(failure) if isinstance(failure, RuntimeError) else 'Verification failed; inspect private bounded diagnostics', finishedAt=utc())
        checkpoint('failed'); return 1


def positive(value):
    result = int(value)
    if result < 1: raise argparse.ArgumentTypeError('A future actual positive value is required')
    return result


def nonnegative(value):
    result = int(value)
    if result < 0: raise argparse.ArgumentTypeError('An actual nonnegative count is required')
    return result


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', required=True, help='Actual reviewed merged build commit C, full40-hex')
    parser.add_argument('--ci-run', required=True, type=positive, help='Actual future release workflow run ID')
    parser.add_argument('--ci-attempt', required=True, type=positive, help='Actual workflow attempt, not a previous rerun')
    parser.add_argument('--expected-jvm', required=True, type=positive, help='Actual fresh CI method count for C')
    parser.add_argument('--expected-lint-warnings', required=True, type=nonnegative)
    parser.add_argument('--expected-lint-information', required=True, type=nonnegative)
    parser.add_argument('--evidence-name', required=True, help='Fresh immutable attempt folder name')
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument('--publish', action='store_true', help='Publish only after every fresh CI/draft gate passes')
    mode.add_argument('--public-only', action='store_true', help='Verify already-public source C even after main advances')
    raise SystemExit(run(parser.parse_args()))
