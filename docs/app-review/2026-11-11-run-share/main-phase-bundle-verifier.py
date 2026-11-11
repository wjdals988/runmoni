#!/usr/bin/env python3
"""Root-only evidence-bundle verifier; never an Android/instrumentation pass.

Preserves a restored FAILED sharing-parent attempt and joins only its completed
sharing observations to an independent passed map-only review on the same pair.
No network/Git/ADB/build. Default dry-run; --apply creates new public verifier/
bundle copies only. Never writes either raw parent proof or changes its status.
"""
import argparse
import datetime as dt
import hashlib
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path('/workspace/runmoni')
E = ROOT / 'docs/app-review/2026-11-11-run-share'
KIND = 'actual-main-phase-bundle'
MAPS = ('productSources', 'testSources', 'resources', 'rendererAssets', 'approvedSourceCopies', 'configurationSources')
RESTORED = ('ownGalleryOutputsRemoved', 'ownShareCacheOutputsRemoved', 'ownReceiverOutputsRemoved',
            'databaseExactlyRestored', 'preferencesRestoredExactly', 'profileRestoredExactly',
            'accountPreferencesRestoredExactly', 'controlledEnvironmentExactlyRestored', 'binaryEvidenceUnchanged',
            'driverSourceUnchanged', 'observerHelperSourceUnchanged', 'environmentHelperSourceUnchanged')
BOUNDARIES = ('GPSInjectedByThisDriver', 'physicalDeviceVerified', 'googleLoginVerified', 'productionWritesVerified',
              'android31RuntimeVerified', 'signedReleaseRuntimeVerified', 'socialAppDeliveryVerified')


def need(value, message):
    if not value:
        raise ValueError(message)


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def read(path):
    return json.loads(Path(path).read_text())


def encoded(value):
    return (json.dumps(value, ensure_ascii=False, indent=2) + '\n').encode()


def locate(reference):
    value = {'file': reference} if isinstance(reference, str) else reference
    need(isinstance(value, dict) and isinstance(value.get('file'), str), 'Actual public proof reference required')
    path = Path(value['file'])
    path = (path if path.is_absolute() else E / path).resolve()
    need(path.is_file() and path.is_relative_to(E.resolve()) and not any('private' in p.lower() for p in path.parts), 'Unsafe public proof')
    if 'sha256' in value:
        need(sha(path.read_bytes()) == value['sha256'], 'Original proof bytes changed: ' + path.name)
    return path


def reference(path):
    return {'file': path.relative_to(E).as_posix(), 'sha256': sha(path.read_bytes())}


def observed_maps(observed, binary):
    need(isinstance(observed, dict) and set(observed) == set(MAPS), 'Six observed source categories required')
    for category in MAPS:
        value, expected = observed[category], binary[category]
        need(value == expected or (isinstance(value, dict) and value.get('matches') is True and
             value.get('mismatches') == [] and value.get('fileCount') == len(expected)), 'Observed source maps differ')


def phase(path, wanted_pass):
    value = read(path)
    need(value.get('completed') is True and value.get('passed') is wanted_pass and
         value.get('stage') == ('complete-passed' if wanted_pass else 'complete-failed'), 'Raw phase status must stay explicit')
    need((value['version'], value['versionCode'], value['api']) == ('0.4.3-dev', 25, 29), 'Unexpected phase version/API')
    need(value['sourceKind'] == 'actual Android MainActivity', 'Phase is not actual MainActivity')
    need(all(value.get(key) is True for key in RESTORED) and value['restorationCommandFailures'] == [], 'Phase cleanup/source checks incomplete')
    need(all(value.get(key) is False for key in BOUNDARIES) and value['fixtureTrackPointCount'] == 0, 'Phase verification boundary differs')
    for stem in ('executedDriver', 'observerHelper', 'environmentHelper'):
        locate({'file': value[stem], 'sha256': value[stem + 'Sha256']})
    binary_path = locate({'file': value['binaryReference'], 'sha256': value['binaryReferenceSha256']})
    binary = read(binary_path)
    need(binary['buildSucceeded'] is True and binary['signing'] == 'local TEST debug, not retained release signing', 'Frozen TEST binary required')
    need(sorted(row['sha256'] for row in value['installed']) == sorted(row['sha256'] for row in binary['binaries']) and
         len(value['installed']) == len(binary['binaries']) == 2, 'Actual installed pair mismatch')
    for key in ('sourceMapsBefore', 'sourceMapsAfterRestoration'):
        observed_maps(value[key], binary)
    if 'sourceMapsAfter' in value:
        observed_maps(value['sourceMapsAfter'], binary)
    elif wanted_pass:
        need(False, 'A completed passed map phase must have its own after snapshot')
    return value, binary


def png_row(row):
    path = locate({'file': row['file'], 'sha256': row['sha256']})
    raw = path.read_bytes()
    need(raw[:8] == b'\x89PNG\r\n\x1a\n' and raw[12:16] == b'IHDR' and len(raw) == row['bytes'], 'Original capture PNG mismatch')
    need((int.from_bytes(raw[16:20], 'big'), int.from_bytes(raw[20:24], 'big')) == (row['width'], row['height']), 'Original PNG dimensions differ')
    need(row['kind'] == 'main' and row['sourceKind'] == 'actual Android MainActivity' and
         row['expectedWindowFocused'] is True and row['syntheticGps'] is False, 'Actual original Main capture required')


def derive(sharing_path, map_path, failure_review):
    sharing, sharing_binary = phase(sharing_path, False)
    map_only, map_binary = phase(map_path, True)
    need(sharing_binary == map_binary and sharing['installed'] == map_only['installed'], 'Both phases must use exactly the same frozen/installed pair')
    for key in ('originalPreferencesSha256', 'originalDatabaseRowsSha256', 'originalAccountPreferenceExists', 'originalAccountPreferenceSha256'):
        need(key in sharing and key in map_only and sharing[key] == map_only[key], 'Original data differs across phases: ' + key)
    need(dt.datetime.fromisoformat(map_only['startedAtUtc'].replace('Z', '+00:00')) >=
         dt.datetime.fromisoformat(sharing['finishedAtUtc'].replace('Z', '+00:00')), 'Phases must be independent and sequential')
    need(sharing['failureType'] == 'AssertionError' and len(sharing['captures']) == 3 and 'actualMapBounds' not in sharing,
         'This special bundle only covers a sharing parent that failed before final map capture')
    completed = ('actualSystemPhotoPickerUsed', 'actualCaptionKeyboardObserved', 'actualCaptionKeyboardBackInsideVisibleWindow',
                 'actualCaptionRealShellKeysObservedBeforeImeDismissal', 'actualMiniMiMovementActionObserved',
                 'actualGallerySaveAcknowledgmentObserved', 'galleryCacheReceiverPngBytesExactlyEqual',
                 'resultReturnedAfterEditorBack', 'nonceFixtureCleanupSucceeded', 'internalStorageVisibilityRestored')
    need(all(sharing.get(key) is True for key in completed), 'Completed sharing sub-observations are missing')
    need(sharing['previewAfterActualReopen'] == sharing['previewAfterChooserReturn'] == sharing['editedPreviewDescription'] and
         'Morning Miles' in sharing['editedPreviewDescription'] and '1:1' in sharing['defaultPreviewDescription'], 'Observed caption/Back/default differs')
    waits = sharing['collectorWaits']
    need(any(row['label'] == 'Actual clean discovery map and outside action bar' and row['completed'] is True for row in waits),
         'Sharing parent did not reach the final map collector')
    receiver = sharing['receiver']
    need(all(receiver.get(key) is True for key in ('passed', 'differentLinuxUid', 'clipDataMatchesSingleStream', 'readPermissionGranted',
             'writePermissionDenied', 'siblingReadPermissionDenied', 'siblingOpenDeniedBySecurityException',
             'prefixAndPersistableGrantAbsent', 'sourceUriCanonical', 'actualReadSucceeded')), 'Actual separate-UID READ receipt incomplete')
    need(receiver['receiverLinuxUid'] != receiver['targetLinuxUid'] and receiver['socialAppDeliveryVerified'] is False, 'Receiver boundary differs')
    gallery = sharing['galleryExport']
    received = locate({'file': receiver['file'], 'sha256': receiver['sha256']}).read_bytes()
    saved = locate({'file': gallery['file'], 'sha256': gallery['sha256']}).read_bytes()
    need(saved == received and (gallery['width'], gallery['height']) == (receiver['width'], receiver['height']) == (1080, 1920),
         'Actual saved/receiver PNG bytes differ')
    driver = locate({'file': sharing['executedDriver'], 'sha256': sharing['executedDriverSha256']}).read_text()
    need(".startswith('지도.')" in driver and 'assert len(maps) == len(options) == 1' in driver,
         'Failed collector source must retain its original locator/assertion')
    need(failure_review.get('failedCollectorPhase') == 'final-map-accessibility-locator' and
         failure_review.get('failedSelector') == "startswith('지도.')" and failure_review.get('observedMapPrefix') == '세계 지도.' and
         failure_review.get('originalSelectorWouldMatch') is False and failure_review.get('rawParentOverallFailedPreserved') is True and
         re.fullmatch(r'[0-9a-f]{64}', failure_review['privateFailureXmlSha256']) and
         re.fullmatch(r'[0-9a-f]{64}', failure_review['privateStageLogSha256']),
         'Explicit failure-locator review is required; original failure is not promoted')
    need(len(map_only['captures']) == 1 and map_only['recordCreatedByThisDriver'] is False and
         map_only['photoCreatedByThisDriver'] is False and map_only['syntheticCompletedRun'] is False and map_only['syntheticSourcePhoto'] is False,
         'Map phase must not replay sharing/create records or photos')
    bounds, action = map_only['actualMapBounds'], map_only['outsideMapActionBounds']
    need(len(bounds) == len(action) == 4 and action[1] >= bounds[3] - 1 and
         action[2] - action[0] >= 48 and action[3] - action[1] >= 48 and
         map_only['outsideMapActionAtLeast48DpAtActualDensity160'] is True, 'Actual map action/viewport geometry incomplete')
    captures = []
    for name, path, proof in (('completed-sharing-subobservations', sharing_path, sharing), ('independent-map-only', map_path, map_only)):
        for original in proof['captures']:
            png_row(original)
            row = dict(original, sourceProof=reference(path), sourcePhase=name, parentExecutionPassed=proof['passed'])
            captures.append(row)
    need(len({row['file'] for row in captures}) == len({row['sha256'] for row in captures}) == 4, 'Four different original captures required')
    return {'schemaVersion': 1, 'evidenceKind': KIND, 'version': '0.4.3-dev', 'versionCode': 25, 'api': 29,
            'bundleValidationPassed': True, 'bundleIsNotSingleExecution': True, 'singleExecutionPassedClaimed': False,
            'sharingParentOverallPassed': False, 'mapParentOverallPassed': True,
            'validationMeaning': 'Completed sharing sub-observations plus independent map review; neither reruns nor promotes the FAILED sharing parent.',
            'phases': {'sharing': reference(sharing_path), 'map': reference(map_path)},
            'binaryReference': sharing['binaryReference'], 'binaryReferenceSha256': sharing['binaryReferenceSha256'],
            'sameFrozenAndInstalledApkPairVerified': True, 'sameOriginalDatasetsVerified': True,
            'sharingSourceSnapshotFieldsActuallyPresent': [key for key in ('sourceMapsBefore', 'sourceMapsAfter', 'sourceMapsAfterRestoration') if key in sharing],
            'sourceSnapshotScope': 'Sharing before/after-restoration and map-only before/after/after-restoration are original per-phase measurements; no missing sharing after snapshot is invented.',
            'failureReview': failure_review, 'captures': captures,
            'physicalDeviceVerified': False, 'googleLoginVerified': False, 'productionWritesVerified': False,
            'signedReleaseRuntimeVerified': False, 'android31RuntimeVerified': False, 'socialAppDeliveryVerified': False}


def validate_bundle(path):
    bundle = read(path)
    need(bundle.get('evidenceKind') == KIND and 'passed' not in bundle and bundle.get('bundleIsNotSingleExecution') is True,
         'An evidence bundle must never pretend to be a passed Android execution')
    need(bundle['verifierSource']['sha256'] == sha(Path(__file__).read_bytes()), 'Use the exact reviewed verifier source')
    locate(bundle['verifierSource'])
    sharing_path, map_path = (locate(bundle['phases'][key]) for key in ('sharing', 'map'))
    expected = derive(sharing_path, map_path, bundle['failureReview'])
    need(bundle == dict(expected, verifierSource=bundle['verifierSource']), 'Bundle content differs from its actual raw phases')
    return bundle, read(sharing_path), read(map_path)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sharing-proof', required=True)
    parser.add_argument('--map-proof', required=True)
    parser.add_argument('--failure-ui', type=Path, required=True, help='Actual private failure XML; hash/bounds only are published')
    parser.add_argument('--sharing-log', type=Path, required=True, help='Actual private host stage log; never copied publicly')
    parser.add_argument('--output', default='main-review-bundle.json')
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    sharing_path, map_path = locate(args.sharing_proof), locate(args.map_proof)
    root = ET.fromstring(args.failure_ui.read_bytes())
    maps = [node for node in root.iter('node') if node.get('package') == 'com.runmoni.app' and node.get('content-desc', '').startswith('세계 지도.')]
    need(len(maps) == 1, 'Actual private failure XML must show the world-map descriptor missed by the old locator')
    log = args.sharing_log.read_text()
    need('return-back-reopen-observation\nfailed-before-finally\ncomplete-failed' in log and 'complete-passed' not in log,
         'Actual host log must preserve the original failing stage sequence')
    failure_review = {'failedCollectorPhase': 'final-map-accessibility-locator', 'failedSelector': "startswith('지도.')",
                      'observedMapPrefix': '세계 지도.', 'originalSelectorWouldMatch': False,
                      'privateFailureXmlSha256': sha(args.failure_ui.read_bytes()), 'privateStageLogSha256': sha(args.sharing_log.read_bytes()),
                      'actualWorldMapBoundsInFailureXml': maps[0].get('bounds'), 'rawParentOverallFailedPreserved': True}
    bundle = derive(sharing_path, map_path, failure_review)
    source = Path(__file__).read_bytes()
    source_path = E / 'main-phase-bundle-verifier.py'
    bundle['verifierSource'] = {'file': source_path.name, 'sha256': sha(source)}
    target = E / args.output
    need(target.parent == E and target.suffix == '.json' and not target.exists(), 'Fresh E-relative bundle output required')
    print(json.dumps({'dryRun': not args.apply, 'output': target.name, 'bundle': bundle}, ensure_ascii=False, indent=2))
    if args.apply:
        need(not source_path.exists() or source_path.read_bytes() == source, 'Do not overwrite a different published verifier')
        if not source_path.exists():
            with source_path.open('xb') as stream:
                stream.write(source)
        with target.open('xb') as stream:
            stream.write(encoded(bundle))


if __name__ == '__main__':
    main()
