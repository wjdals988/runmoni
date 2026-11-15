"""Verify an actual merged dashboard deployment and canonical Runmoni bytes."""
import argparse
import datetime as dt
import hashlib
from html.parser import HTMLParser
import json
from pathlib import Path
import re
import subprocess
import urllib.request

R = Path('/workspace/runmoni')
E = R / 'docs/app-review/2026-11-15-companion'
W = R / 'work/companion-redesign'
REPO = 'wjdals988/mydashboard'
URL = 'https://coldbrewventi.vercel.app/projects/runmoni'


def need(value, message):
    if not value:
        raise AssertionError(message)


def read(path):
    return json.loads(path.read_text())


def api(path):
    return json.loads(subprocess.check_output(['gh', 'api', 'repos/' + REPO + '/' + path], timeout=45))


def fetch(url):
    request = urllib.request.Request(url, headers={'Cache-Control': 'no-cache', 'User-Agent': 'RunmoniReleaseVerification/0.7.0-dev'})
    with urllib.request.urlopen(request, timeout=35) as response:
        raw = response.read(4_000_001)
        need(len(raw) <= 4_000_000, 'Response exceeds bounded page/image limit')
        return response.status, raw, response.url


class Page(HTMLParser):
    def __init__(self):
        super().__init__()
        self.ignore = self.depth = 0
        self.text, self.download_text, self.download_links = [], [], []

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if tag in ('script', 'style'):
            self.ignore += 1
        if tag == 'section':
            self.depth = self.depth + 1 if self.depth else int(attrs.get('id') == 'download')
        if tag == 'a' and self.depth:
            self.download_links.append(attrs.get('href'))

    def handle_endtag(self, tag):
        if tag in ('script', 'style') and self.ignore:
            self.ignore -= 1
        if tag == 'section' and self.depth:
            self.depth -= 1

    def handle_data(self, text):
        if not self.ignore and text.strip():
            self.text.append(text.strip())
            if self.depth:
                self.download_text.append(text.strip())


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--pr', required=True, type=int)
    parser.add_argument('--merge', required=True)
    parser.add_argument('--plan', required=True, type=Path)
    parser.add_argument('--node-tests', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    need(re.fullmatch('[a-f0-9]{40}', args.merge), 'Actual merge revision required')
    need(not args.output.exists(), 'Never overwrite prior deployment evidence')
    plan, node = read(args.plan), read(args.node_tests)
    need(plan['mode'] == 'apply' and plan['dashboardWritten'] is True, 'Actual dashboard preparation required')
    need(node['exitCode'] == 0 and node['tests'] > 0 and node['tests'] == node['passed'] and node['failed'] == node['skipped'] == 0, 'Actual fresh Node checks required')
    need(hashlib.sha256((args.node_tests.parent / node['rawLog']).read_bytes()).hexdigest() == node['rawSha256'], 'Node log changed')
    project, meta = plan['project'], plan['metadata']
    need(meta['sourceRevision'] == plan['sourceRevision'] and meta['version'] == '0.7.0-dev' and meta['versionCode'] == 29, 'Actual new release identity')
    pr = api('pulls/' + str(args.pr))
    need(pr['merged'] is True and pr['merge_commit_sha'] == args.merge, 'Expected PR/merge not found')
    runs = api('actions/runs?head_sha=' + args.merge + '&event=push&per_page=20')['workflow_runs']
    runs = [r for r in runs if r['path'] == '.github/workflows/ci.yml']
    need(runs and runs[0]['status'] == 'completed' and runs[0]['conclusion'] == 'success', 'Main CI not successful')
    deployments = api('deployments?sha=' + args.merge + '&per_page=20')
    production = next((d for d in deployments if d['environment'].lower() == 'production'), None)
    need(production, 'No Production deployment for this actual merge')
    statuses = api('deployments/' + str(production['id']) + '/statuses')
    need(statuses and statuses[0]['state'] == 'success', 'Production not successful')
    status, body, final = fetch(URL)
    page = Page()
    page.feed(body.decode())
    visible, download = ' '.join(page.text), ' '.join(page.download_text)
    checks = {'http200': status == 200, 'canonicalUrl': final == URL,
              'downloadAnchor': project['apk']['url'] in page.download_links,
              'versionAndCode': bool(re.search(r'0\.7\.0-dev\s*\(\s*29\s*\)', download)),
              'fileSize': project['apk']['size'] in download, 'sha256': meta['sha256'] in download,
              'previewLabel': project['apk']['label'] in download,
              'description': project['description'] in visible,
              'screenshotsProvenance': project['screenshotsNote'] in visible}
    for shot in project['screenshots']:
        checks['gallery_' + Path(shot['src']).stem] = shot['src'] in body.decode()
    need(len(checks) == 13 and all(checks.values()), 'Canonical page checks failed: ' + str(checks))
    gallery = read(E / 'dashboard-gallery.json')
    images = []
    for shot, expected in zip(project['screenshots'], gallery['images']):
        status, raw, _ = fetch('https://coldbrewventi.vercel.app' + shot['src'])
        sha = hashlib.sha256(raw).hexdigest()
        need(status == 200 and sha == expected['sha256'], 'Production image differs from actual reviewed Main original')
        images.append({'path': shot['src'], 'httpStatus': status, 'sizeBytes': len(raw), 'sha256': sha, 'matchesCommittedOriginal': True, 'source': 'MainActivity'})
    proof = {'verifiedAtUtc': dt.datetime.now(dt.timezone.utc).isoformat(), 'productionVerified': True,
             'executedDriverSha256': hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
             'dashboard': {'repository': REPO, 'pullRequest': pr['html_url'], 'mergeRevision': args.merge,
                           'mainCi': {'runId': runs[0]['id'], 'conclusion': runs[0]['conclusion'], 'url': runs[0]['html_url']},
                           'productionDeployment': {'id': production['id'], 'state': statuses[0]['state'], 'environmentUrl': statuses[0]['environment_url']}, 'localNodeTests': node},
             'productionPage': {'url': URL, 'checks': checks, 'responseSha256': hashlib.sha256(body).hexdigest()},
             'apk': meta, 'gallery': images, 'physicalDeviceVerified': False,
             'interactiveGoogleLoginVerified': False, 'productionWritesVerified': False, 'userVisualApproval': False,
             'limits': ['HTTP/download anchor and four PNG bytes only; browser click not inferred.', 'Images are local TEST debug MainActivity/API29 with disclosed synthetic data, not public signed APK runtime.', 'No APK body downloaded. Canonical raw feed is a later independent check.']}
    with args.output.open('x') as output:
        output.write(json.dumps(proof, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps({'productionVerified': True, 'canonicalChecks': len(checks), 'originalPngs': len(images), 'output': str(args.output)}))


if __name__ == '__main__':
    main()
