#!/usr/bin/env python3
"""ROOT ONLY, future execution: compare an actual commit C with executed input6.

Requires --source-commit (40 lowercase hex), --binary binary-build6.json and a
fresh --output basename. Reads local Git objects only; no checkout/network/build.
"""
import argparse
import datetime
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import subprocess

R = next(p for p in Path(__file__).resolve().parents if (p / 'app/build.gradle.kts').exists())
E = R / 'docs/app-review/2026-11-15-companion'
MAPS = ('productSources', 'testSources', 'resources', 'rendererAssets', 'approvedSourceCopies', 'configurationSources')
FREEZE_SHA = '0b9ee924c8bf814191f4565e559e70f9477d7b9bbd7c91a5176001d3f3634438'
APPROVED = {'docs/design/avatars/v0.3.0/running-motion-art-source.png'}
CONFIG = {'app/build.gradle.kts', 'app/src/main/AndroidManifest.xml', 'app/src/androidTest/AndroidManifest.xml',
          'build.gradle.kts', 'gradle.properties', 'settings.gradle.kts', '.github/workflows/android-release.yml',
          'scripts/generate-license-inventory.py', 'scripts/export-license-coordinates.init.gradle',
          'app/src/main/assets/open-source-licenses.json', 'THIRD_PARTY_NOTICES.md'}


def need(value, label):
    if not value:
        raise AssertionError(label)


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def git(*words):
    return subprocess.run(['git', '--no-replace-objects', '-C', str(R), *words], check=True,
                          stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                          env={**os.environ, 'GIT_NO_LAZY_FETCH': '1', 'GIT_ALLOW_PROTOCOL': '',
                               'GIT_TERMINAL_PROMPT': '0', 'GIT_OPTIONAL_LOCKS': '0'}).stdout


def run(args):
    need(re.fullmatch(r'[0-9a-f]{40}', args.source_commit), 'An explicit actual 40-hex commit C is required')
    need(args.binary == 'binary-build6.json', 'Only the executed input6 binary evidence is accepted')
    need(re.fullmatch(r'[a-z][a-z0-9-]{2,95}\.json', args.output), 'A fresh plain output basename is required')
    target = E / args.output
    need(not target.exists(), 'Output already exists; no evidence may be overwritten')
    binary_path, freeze_path = E / args.binary, E / 'source-freeze6.json'
    binary_raw, freeze_raw = binary_path.read_bytes(), freeze_path.read_bytes()
    binary, freeze = json.loads(binary_raw), json.loads(freeze_raw)
    need(sha(freeze_raw) == FREEZE_SHA == binary['sourceFreezeSha256'] and
         binary['sourceFreezeReference'] == freeze_path.name, 'Executed source-freeze6 SHA mismatch')
    need(all((v['version'], v['versionCode']) == ('0.7.0-dev', 29) for v in (binary, freeze)), 'Candidate version mismatch')
    need(binary['buildSucceeded'] is True and binary['jvmReportsFresh'] is True and
         binary['actualInputsBoundByFullSourceMaps'] is True and freeze['previousInputResultsReused'] is False,
         'Fresh successful executed build evidence is required')
    need(binary['jvm']['tests'] > 0 and all(binary['jvm'][k] == 0 for k in ('failures', 'errors', 'skipped')) and
         binary['lint']['Error'] == binary['lint']['Fatal'] == 0, 'Executed JVM/lint checks did not pass')
    need(binary['buildLog'] == 'build6.txt', 'Unexpected build-log reference')
    log_raw = (E / binary['buildLog']).read_bytes()
    need(sha(log_raw) == binary['buildLogSha256'] and b'BUILD SUCCESSFUL' in log_raw and b'BUILD FAILED' not in log_raw,
         'Executed successful build log mismatch')
    expected = freeze['sourceMaps']
    need(set(expected) == set(MAPS) and all(binary[k] == expected[k] for k in MAPS), 'Binary/freeze six maps differ')
    need(binary['signing'] == 'local TEST debug, not retained release signing' and
         freeze['localDebugNetworkOverlay']['mainNetworkSecurityConfigSha256'] ==
         expected['resources']['app/src/main/res/xml/network_security_config.xml'], 'Executed TEST/debug CA boundary mismatch')
    need(set(expected['configurationSources']) == CONFIG and set(expected['approvedSourceCopies']) == APPROVED,
         'Exactly eleven configuration files and one approved source are required')
    need(all(group and all(not PurePosixPath(p).is_absolute() and '..' not in PurePosixPath(p).parts and
         '.secrets' not in PurePosixPath(p).parts and re.fullmatch(r'[0-9a-f]{64}', h) for p, h in group.items())
         for group in expected.values()), 'Invalid or secret-containing source map')
    need(git('cat-file', '-t', args.source_commit).strip() == b'commit', 'C must be an actual local commit object')
    entries = {}
    for row in git('ls-tree', '-r', '-z', '--full-tree', args.source_commit).split(b'\0'):
        if row:
            meta, path = row.split(b'\t', 1)
            entries[path.decode('utf-8')] = meta.decode('ascii').split()
    paths = set(entries)
    selections = {
        'productSources': {p for p in paths if p.startswith('app/src/main/java/') and p.endswith(('.kt', '.java'))},
        'testSources': {p for p in paths if p.startswith(('app/src/test/', 'app/src/androidTest/')) and p.endswith(('.kt', '.java'))},
        'resources': {p for p in paths if p.startswith('app/src/main/res/')},
        'rendererAssets': {p for p in paths if p.startswith('app/src/main/assets/avatars/')},
        'approvedSourceCopies': APPROVED, 'configurationSources': CONFIG,
    }
    need(all(selections[k] == set(expected[k]) for k in MAPS), 'Extra or missing mapped paths in C')
    mapped = set().union(*selections.values())
    need({p for p in paths if p.startswith('app/src/')} == {p for p in mapped if p.startswith('app/src/')},
         'An additional source set or unmapped app/src file is outside the executed freeze')
    actual = {}
    for category in MAPS:
        actual[category] = {}
        for path in sorted(selections[category]):
            need(path in entries and entries[path][0] in ('100644', '100755') and entries[path][1] == 'blob',
                 'Missing or non-regular mapped Git object')
            actual[category][path] = sha(git('cat-file', 'blob', entries[path][2]))
        need(actual[category] == expected[category], 'Committed file hashes differ from the executed source map')
    need(binary_path.read_bytes() == binary_raw and freeze_path.read_bytes() == freeze_raw and
         (E / binary['buildLog']).read_bytes() == log_raw, 'Executed evidence changed during comparison')
    result = {'releaseSourceRevision': args.source_commit, 'allSourceMapsMatch': True, 'sourceMaps': actual,
              'executedSourceFreeze': {'file': freeze_path.name, 'sha256': sha(freeze_raw)},
              'executedBinary': {'file': binary_path.name, 'sha256': sha(binary_raw)},
              'comparedAtUtc': datetime.datetime.now(datetime.timezone.utc).isoformat(),
              'fileHashBasis': 'SHA256 of complete mapped regular Git blobs at the actual commit C; no working-tree subset',
              'mainActivitySource': {'file': 'app/src/main/java/com/runmoni/app/MainActivity.kt',
                  'sha256': actual['productSources']['app/src/main/java/com/runmoni/app/MainActivity.kt']},
              'executionBoundary': {'executedVariant': 'Local TEST debug with unconfigured production Firebase',
                  'connectedReleaseAndRetainedSigningRequireSeparateCiVerification': True,
                  'localDebugCaOverlayIsOutsideTheseSixCommittedMaps': True,
                  'generatedBuildConfigAndSigningCompared': False, 'binaryIdentityClaimed': False,
                  'runtimeEquivalenceClaimed': False, 'connectedReleaseRuntimeVerifiedByThisHelper': False}}
    raw = (json.dumps(result, ensure_ascii=False, indent=2) + '\n').encode()
    with target.open('xb') as output:
        output.write(raw)
    print(json.dumps({'file': target.name, 'sha256': sha(raw), 'maps': {k: len(v) for k, v in actual.items()}}))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source-commit', required=True)
    parser.add_argument('--binary', required=True)
    parser.add_argument('--output', required=True)
    try:
        run(parser.parse_args())
    except AssertionError as error:
        raise SystemExit(str(error)) from None
    except Exception:
        raise SystemExit('Source comparison failed; no success evidence was produced') from None
