"""Verify a feed-only publication against immutable, already public APK assets."""
from pathlib import Path
import datetime as dt
import hashlib
import json
import subprocess
import urllib.request

ROOT = Path('/workspace/runmoni')
EVIDENCE = ROOT / 'docs/app-review/2026-11-17-expressive-refresh'
SOURCE = '3d48c39102f33c36828be224a22a3d95b1018c7e'
CERT = 'a21359c657f6797f5fb8229c3098e48bc72a45f9d25269f8d41ff5122465df4a'
API = 'repos/wjdals988/runmoni/releases/tags/v0.9.0-dev'
BASE = 'https://github.com/wjdals988/runmoni/releases/download/v0.9.0-dev/'


def sha(body):
    return hashlib.sha256(body).hexdigest()


def small_public_body(name):
    request = urllib.request.Request(BASE + name, headers={'User-Agent': 'Runmoni-Feed-Publication-Verification'})
    with urllib.request.urlopen(request, timeout=30) as response:
        assert response.status == 200
        assert int(response.headers.get('Content-Length', '0')) <= 8192
        body = response.read(8193)
        assert len(body) <= 8192
        return body


def main():
    publication_path = EVIDENCE / 'connected-release-public.json'
    publication = json.loads(publication_path.read_bytes())
    assert publication['completed'] and publication['passed'] and publication['publicReleaseConfirmed']
    assert publication['apkBodyDownloaded'] is False and publication['assetsReuploadedOrReplaced'] is False
    release = json.loads(subprocess.check_output(['gh', 'api', API], cwd=ROOT, timeout=45))
    assert release['id'] == 449502105 and release['draft'] is False and release['prerelease'] is True
    assert release['target_commitish'] == SOURCE
    assets = {row['name']: row for row in release['assets']}
    assert len(assets) == 3
    for saved in publication['assets']:
        current = assets[saved['name']]
        assert all(current[key] == saved[key] for key in ('id', 'name', 'size', 'digest', 'state'))
    meta_body = small_public_body('runmoni-0.9.0-dev.metadata.json')
    sums_body = small_public_body('SHA256SUMS-0.9.0-dev.txt')
    assert sha(meta_body) == 'e87f2ae9784c850b99ceedb18197187ac1464ac3b53a6102a3d4aaba8fb91ae5'
    assert sha(sums_body) == '99eb885c0594883634625b4e212ea69082bb5ac7329b044494a5cac0fcc9fe09'
    metadata = json.loads(meta_body)
    assert metadata == publication['metadata']
    assert metadata == json.loads((EVIDENCE / 'runmoni-0.9.0-dev.metadata.json').read_bytes())
    feed_path = ROOT / 'updates/android.json'
    feed = json.loads(feed_path.read_bytes())
    old_feed = json.loads(subprocess.check_output(['git', 'show', SOURCE + ':updates/android.json'], cwd=ROOT))
    assert feed['schemaVersion'] == old_feed['schemaVersion'] == 1
    assert feed['stable'] == old_feed['stable'] is None
    assert old_feed['preview']['versionCode'] == 31
    preview = feed['preview']
    for key in ('version', 'versionCode', 'fileName', 'sizeBytes', 'sha256', 'certificateSha256', 'sourceRevision', 'signed'):
        assert preview[key] == metadata[key], key
    assert preview['version'] == '0.9.0-dev' and preview['versionCode'] == 32
    assert preview['sourceRevision'] == SOURCE and preview['certificateSha256'] == CERT
    assert preview['package'] == metadata['packageName'] == 'com.runmoni.app'
    assert preview['signed'] is True and preview['buildType'] == 'release' and preview['physicalDeviceTests'] == 0
    assert preview['downloadUrl'] == BASE + metadata['fileName']
    assert preview['releaseUrl'] == release['html_url']
    assert sums_body.decode().strip() == metadata['sha256'] + '  ' + metadata['fileName']
    assert assets[metadata['fileName']]['digest'] == 'sha256:' + metadata['sha256']
    assert assets[metadata['fileName']]['size'] == metadata['sizeBytes'] == 174915411
    freeze = json.loads((EVIDENCE / 'source-freeze11.json').read_bytes())
    for group, paths in freeze['sourceMaps'].items():
        for path, expected in paths.items():
            assert sha((ROOT / path).read_bytes()) == expected, (group, path)
    output = EVIDENCE / 'feed-publication-preflight.json'
    evidence = {
        'checkedAtUtc': dt.datetime.now(dt.timezone.utc).isoformat(),
        'passed': True, 'alreadyPublicReleaseId': release['id'],
        'sourceRevision': SOURCE, 'version': preview['version'], 'versionCode': 32,
        'previousFeedVersionCode': 31, 'stableChannelUnchanged': True,
        'publicSidecarBodiesMatchExactly': True,
        'publicMetadataSha256': sha(meta_body), 'publicChecksumSha256': sha(sums_body),
        'publicAssetIdsUnchanged': sorted(row['id'] for row in assets.values()),
        'feedSha256': sha(feed_path.read_bytes()),
        'allSixRuntimeSourceMapsUnchanged': True,
        'apkBodyDownloaded': False, 'apkRebuiltOrReuploaded': False,
        'nativeAppUpdateCheckExecuted': False, 'redDotObservedOnPhysicalDevice': False,
        'dashboardProductionPublished': False,
        'dashboardPullRequest': 'https://github.com/wjdals988/mydashboard/pull/34',
        'dashboardAuditBlocker': 'GHSA-fc5q-39xc-vjpf; no verified published picomatch patch as of 2026-11-18 05:09 UTC',
        'manualRefresh': '설정 → 앱 정보와 업데이트 → 업데이트 확인 (or 버전 정보 다시 확인)',
        'cacheBoundary': 'Automatic checks honor a six-hour successful-result cache; manual force check bypasses it subject to a thirty-second retry limit.',
        'executedDriverSha256': sha(Path(__file__).read_bytes()),
    }
    with output.open('x') as stream:
        stream.write(json.dumps(evidence, ensure_ascii=False, indent=2) + '\n')
    driver = EVIDENCE / 'feed-publication-preflight-executed-driver.py'
    with driver.open('xb') as stream:
        stream.write(Path(__file__).read_bytes())
    print(json.dumps(evidence, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
