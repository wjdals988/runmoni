#!/usr/bin/env python3
"""Root-only final Studio docs/feed preparation after publication and production proof.

Default is a guarded dry-run. Only --apply writes the enumerated docs/evidence/feed
files. This helper never runs Git, network, Android, builds, release or deployment
commands. The preparing agent must not execute it.

E/final-ui-context.json supplies liveProof, visualProof, scopeProof,
sourceComparison, galleryManifest and sessionCleanup. Every evidence reference
must resolve inside E; {file, sha256} references also verify the exact bytes.
Actual C/run/URLs/hashes come from Root's observed input files, never templates.
"""
from pathlib import Path
from zoneinfo import ZoneInfo
import argparse
import datetime as dt
import hashlib
import json
import re

R = Path('/workspace/runmoni')
W = R / 'work/ui-studio'
E = R / 'docs/app-review/2026-11-11-studio-ui'
B = Path('/workspace/mydashboard')
VERSION, CODE, JVM = '0.4.3-dev', 25, 189
OLD_C = '176a9cbe1112efa05e9fc55d913738ee1778a57f'
ORIGINAL_015_C = '75c39ca900b140cfb02c1a47807a5955c8b8db7f'
REVIEW = 'RELEASE_REVIEW_0.4.3_20261112.md'
BASE = 'app-review/2026-11-11-studio-ui'
MARKER = '<!-- verified-studio-release-0.4.3-v1 -->'
MAPS = ('productSources', 'testSources', 'resources', 'rendererAssets', 'approvedSourceCopies', 'configurationSources')
PROOFS = {'android-review1.json': (False, 53), 'android-review2.json': (True, 13),
          'native1.json': (False, 9), 'android-review3.json': (True, 2), 'native2.json': (True, 4)}
FRESH = {'android-review3.json', 'native2.json'}


def need(value, message):
    if not value:
        raise ValueError(message)


def digest(raw):
    return hashlib.sha256(raw).hexdigest()


def read(path):
    path = Path(path)
    need(path.is_file() and not path.is_symlink() and path.stat().st_size <= 3_000_000,
         'Missing or oversized actual JSON input: ' + path.name)
    return json.loads(path.read_text())


def encoded(value):
    return (json.dumps(value, ensure_ascii=False, indent=2) + '\n').encode()


def ref(reference):
    item = {'file': reference} if isinstance(reference, str) else reference
    need(isinstance(item, dict) and isinstance(item.get('file'), str), 'Evidence filename required')
    path = Path(item['file'])
    path = path if path.is_absolute() else E / path
    need(not path.is_symlink(), 'Symlink evidence is not allowed')
    path = path.resolve()
    need(path.is_relative_to(E.resolve()) and path.is_file(), 'Evidence must exist inside the Studio review directory')
    need(not any('private' in part.lower() or part == '.secrets' for part in path.parts), 'Private evidence must stay private')
    if 'sha256' in item:
        need(isinstance(item['sha256'], str) and re.fullmatch(r'[0-9a-f]{64}', item['sha256']) is not None and
             digest(path.read_bytes()) == item['sha256'], 'Evidence bytes differ: ' + path.name)
    return path


def true_fields(value, keys, label):
    need(all(value.get(key) is True for key in keys), label)


def false_fields(value, keys, label):
    need(all(value.get(key) is False for key in keys), label)


def observed_maps(observed, binary, label):
    need(isinstance(observed, dict) and set(observed) == set(MAPS), label + ': six actual source maps required')
    for key in MAPS:
        value, expected = observed[key], binary[key]
        need(value == expected or value == dict(fileCount=len(expected), matches=True, mismatches=[]),
             label + ': source mismatch in ' + key)


def installed_pair(proof, binary):
    need(len(binary['binaries']) == len(proof['installed']) == 2, 'Two installed app/test APK observations required')
    need({row['package'] for row in proof['installed']} == {'com.runmoni.app', 'com.runmoni.app.test'}, 'Installed package pair differs')
    need(sorted(row['sha256'] for row in proof['installed']) == sorted(row['sha256'] for row in binary['binaries']),
         'Installed pair differs from the frozen binaries')


def instrumentation_group(proof, name):
    groups = [group for group in proof['groups'] if group['name'] == name]
    need(len(groups) == 1, 'Missing or duplicate observed instrumentation group: ' + name)
    group = groups[0]
    need(group['passed'] is True and group['status'] == 'passed' and type(group['actual']) is int and
         group['actual'] == group['expected'] > 0, 'Group did not pass: ' + name)
    raw = ref({'file': group['raw'], 'sha256': group['rawSha256']}).read_text()
    need(re.search(r'OK \(' + str(group['actual']) + r' tests?\)', raw) is not None and
         not any(token in raw for token in ('FAILURES!!!', 'INSTRUMENTATION_FAILED', 'Process crashed')),
         'Raw instrumentation result differs: ' + name)
    return group


def verify_scope(scope, ci):
    need((scope['version'], scope['versionCode'], scope['finalLocalBinary']) == (VERSION, CODE, 'binary-build3.json'),
         'Final Studio scope identity differs')
    need(scope['finalLocalJvm'] == ci['jvm'] == dict(tests=JVM, failures=0, errors=0, skipped=0), 'Measured JVM result differs')
    need(scope['finalLocalLint']['Error'] == scope['finalLocalLint']['Fatal'] == 0, 'Local lint failed')
    need(scope['freshFinalAndroid']['total'] == 6 and scope['retainedEarlierAndroid']['total'] == 64 and
         scope['distinctPassedMethodsAcrossInputs'] == 70 and scope['all70FreshOnFinalBinaryClaimed'] is False,
         'Keep fresh6/retained64/distinct70 separate')
    need(set(scope['freshFinalAndroid']['proofs']) == FRESH and
         set(scope['retainedEarlierAndroid']['proofs']) == set(PROOFS) - FRESH, 'Scope proof selection differs')
    true_fields(scope, ('runtimeSourcesAreControlledComponentActivityFixtures', 'source1OverallFailedRetained',
                        'source2NativeOverallFailedRetained', 'staleConsentTestUpdatedToCurrentRootFlow',
                        'chatLatestBodyFullBoundsAndImeChecksRequired'), 'Scope boundaries/failures missing')
    false_fields(scope, ('physicalDeviceVerified', 'googleLoginVerified', 'signedReleaseRuntimeVerified',
                         'socialAppDeliveryVerified', 'productionWritesVerified', 'earlierConsumerTimeoutSpecificCauseVerified'),
                 'Scope runtime/cause boundary changed')
    binary = read(ref(scope['finalLocalBinary']))
    need(binary['buildSucceeded'] is True and binary['jvmReportsFresh'] is True and binary['jvm'] == ci['jvm'] and
         binary['lint'] == scope['finalLocalLint'] and binary['signing'] == 'local TEST debug, not retained release signing',
         'Final TEST debug build differs')
    audit = read(ref('scope-root-audit.json'))
    ref({'file': audit['source'], 'sha256': audit['sourceSha256']})
    need(ref(audit['scope']) == ref('android-scope-summary.json') and
         (audit['freshFinal'], audit['retainedEarlier'], audit['distinct']) == (6, 64, 70), 'Frozen-method audit scope differs')
    true_fields(audit, ('actualSelectorsAndMatchingFrozenSourceMethodsCrossChecked', 'originalScopeNotRewritten',
                        'source1SeparatePostObservationPreserved'), 'Frozen FQCN method audit incomplete')
    false_fields(audit, ('source1MissingAfterSnapshotFabricated', 'physicalDeviceVerified', 'googleLoginVerified'), 'Audit boundary changed')
    observations = {row['proof']: row for row in audit['observations']}
    need(len(observations) == len(audit['observations']) == len(PROOFS) and set(observations) == set(PROOFS), 'Audit phase set differs')
    proofs, binaries = {}, {}
    for name, (overall, count) in PROOFS.items():
        path = ref(name)
        proof = read(path)
        need(proof['completed'] is True and proof['passed'] is overall and proof['actualPassedProductMethods'] == count,
             'Retain the actual phase status: ' + name)
        true_fields(proof, ('preferencesRestoredExactly', 'profileRestoredExactly', 'communityAccountPreferencesRestoredExactly',
                           'controlledSettingsPermissionsPowerRestoredExactly', 'driverSourceUnchanged', 'observerHelperSourceUnchanged'),
                    'Formal phase restoration/source incomplete: ' + name)
        false_fields(proof, ('physicalDeviceVerified', 'googleLoginVerified', 'productionWritesVerified',
                            'android31RuntimeVerified', 'signedReleaseRuntimeVerified'), 'Formal runtime boundary changed')
        need((proof['version'], proof['versionCode'], proof['api']) == (VERSION, CODE, 29), 'Formal identity/API differs')
        for stem in ('executedDriver', 'observerHelper'):
            ref({'file': proof[stem], 'sha256': proof[stem + 'Sha256']})
        built = read(ref({'file': proof['binaryReference'], 'sha256': proof['binaryReferenceSha256']}))
        installed_pair(proof, built)
        fields = ['sourceMapsBefore'] if name == 'android-review1.json' else ['sourceMapsBefore', 'sourceMapsAfter', 'sourceMapsAfterRestoration']
        for field in fields:
            observed_maps(proof[field], built, name + '/' + field)
        if name != 'android-review1.json':
            need(proof['binaryEvidenceUnchanged'] is True, 'Frozen binary evidence changed')
        observation = observations[name]
        need(observation['sha256'] == digest(path.read_bytes()) and observation['binaryReference'] == proof['binaryReference'] and
             observation['passedGroupMethods'] == count and observation['parentOverallPassed'] is overall and
             observation['sourceSnapshotFieldsActuallyChecked'] == fields and
             re.fullmatch(r'[0-9a-f]{40}', observation['matchingTestBlobCommit']) is not None, 'Audit observation differs')
        true_fields(observation, ('allFrozenTestSourceBlobsMatched', 'installedPairMatches'), 'Frozen source/installed-pair audit failed')
        passed = [group for group in proof['groups'] if group['passed']]
        need(sum(instrumentation_group(proof, group['name'])['actual'] for group in passed) == count, 'Measured group total differs')
        if name in FRESH:
            need(built == binary, 'Fresh execution used another APK pair')
        proofs[name], binaries[name] = proof, built
    rows = scope['methods']
    need(len(rows) == len({row['method'] for row in rows}) == 70 and
         sum(row['freshOnFinalLocalBinary'] is True for row in rows) == 6, 'Distinct/fresh method rows differ')
    for row in rows:
        need(row['proof'] in proofs and row['freshOnFinalLocalBinary'] is (row['proof'] in FRESH), 'Method phase/fresh flag differs')
        path = ref({'file': row['proof'], 'sha256': row['proofSha256']})
        proof = proofs[path.name]
        group = instrumentation_group(proof, row['group'])
        need(row['raw'] == group['raw'] and row['rawSha256'] == group['rawSha256'] and
             row['binaryReference'] == proof['binaryReference'] and row['parentOverallPassed'] is proof['passed'] and
             row['passedGroupWithinOriginalParent'] is True, 'Method provenance differs')
        need(any(row['method'] == selector if '#' in selector else row['method'].startswith(selector + '#')
                 for selector in group['classes']), 'Method is outside its actually executed selector')
    post_path = ref(scope['source1SeparatePostPhaseSourceObservation'])
    post = read(post_path)
    need(ref(post['parentProof']) == ref('android-review1.json') and post['parentOverallPassed'] is False and
         post['allSixSourceMapsMatchFrozenInput'] is True and
         post['sourceMaps'] == {key: binaries['android-review1.json'][key] for key in MAPS}, 'Separate source1 post-observation differs')
    need('sourceMapsAfter' not in proofs['android-review1.json'] and 'sourceMapsAfterRestoration' not in proofs['android-review1.json'],
         'Do not retrofit missing snapshots into the original failed phase')
    return binary


def main_captures(live, binary):
    need((live['version'], live['versionCode'], live['api'], live['sourceKind']) ==
         (VERSION, CODE, 29, 'actual Android MainActivity'), 'Actual Main identity differs')
    true_fields(live, ('passed', 'completed', 'driverSourceUnchanged', 'observerHelperSourceUnchanged', 'environmentHelperSourceUnchanged',
                      'preferencesRestoredExactly', 'profileRestoredExactly', 'accountPreferencesRestoredExactly',
                      'controlledEnvironmentExactlyRestored', 'databaseExactlyRestored', 'binaryEvidenceUnchanged',
                      'actualSystemPhotoPickerUsed', 'actualCaptionKeyboardObserved', 'actualCaptionKeyboardBackFullyOnDisplay',
                      'actualCaptionKeyboardBackInsideVisibleWindow', 'actualCaptionRealShellKeysObservedBeforeImeDismissal',
                      'actualMiniMiMovementActionObserved', 'actualGallerySaveAcknowledgmentObserved', 'galleryCacheReceiverPngBytesExactlyEqual',
                      'resultReturnedAfterEditorBack', 'ownGalleryOutputsRemoved', 'ownShareCacheOutputsRemoved', 'ownReceiverOutputsRemoved',
                      'nonceFixtureCleanupSucceeded', 'originalHistoryExcludedFromExtraScreenshots', 'homeCaptureContainsOnlyNonceReviewRun',
                      'syntheticCompletedRun', 'syntheticSourcePhoto'),
                'Single actual Main path/restoration is incomplete')
    false_fields(live, ('physicalDeviceVerified', 'googleLoginVerified', 'productionWritesVerified', 'android31RuntimeVerified',
                       'signedReleaseRuntimeVerified', 'socialAppDeliveryVerified', 'syntheticGps', 'GPSInjectedByThisDriver',
                       'mapProviderChangedOrNewProviderAuthenticated', 'newAuthenticationRequestedByDriver'), 'Main runtime boundary changed')
    need(live['fixtureTrackPointCount'] == 0 and live['restorationCommandFailures'] == [] and
         live['fixture']['rawGpsIncluded'] is False and live['fixture']['fixtureDirectWalletWrites'] is False,
         'Main controlled fixture/cleanup differs')
    need(read(ref({'file': live['binaryReference'], 'sha256': live['binaryReferenceSha256']})) == binary, 'Main used another local binary')
    installed_pair(live, binary)
    for field in ('sourceMapsBefore', 'sourceMapsAfter', 'sourceMapsAfterRestoration'):
        observed_maps(live[field], binary, 'Actual Main/' + field)
    for stem in ('executedDriver', 'observerHelper', 'environmentHelper'):
        ref({'file': live[stem], 'sha256': live[stem + 'Sha256']})
    need('1:1' in live['defaultPreviewDescription'] and '9:16' in live['editedPreviewDescription'] and
         'Morning Miles' in live['editedPreviewDescription'] and
         live['previewAfterActualReopen'] == live['previewAfterChooserReturn'] == live['editedPreviewDescription'], 'Actual draft observations differ')
    need(type(live['galleryAcknowledgmentScrollActions']) is int and live['galleryAcknowledgmentScrollActions'] >= 0,
         'Actual save acknowledgement scroll count required')
    receiver = live['receiver']
    true_fields(receiver, ('passed', 'differentLinuxUid', 'readPermissionGranted', 'writePermissionDenied',
                          'siblingReadPermissionDenied', 'siblingOpenDeniedBySecurityException',
                          'prefixAndPersistableGrantAbsent', 'actualReadSucceeded', 'clipDataMatchesSingleStream'), 'Separate UID receiver verification incomplete')
    need(receiver['socialAppDeliveryVerified'] is False and receiver['networkRequestedByReceiver'] is False, 'Receiver is not SNS verification')
    gallery_export = live['galleryExport']
    gallery_raw = ref(gallery_export).read_bytes()
    receiver_raw = ref(receiver).read_bytes()
    need(gallery_raw == receiver_raw and digest(gallery_raw) == receiver['sha256'] == gallery_export['sha256'] and
         len(receiver_raw) == receiver['sizeBytes'], 'Actual saved/receiver PNG bytes differ')
    rows = live['captures']
    need(len(rows) == len({row['file'] for row in rows}) == 6, 'Six distinct actual Main originals required')
    for row in rows:
        raw = ref(row).read_bytes()
        need(raw[:8] == b'\x89PNG\r\n\x1a\n' and raw[12:16] == b'IHDR' and len(raw) == row['bytes'] and
             (int.from_bytes(raw[16:20], 'big'), int.from_bytes(raw[20:24], 'big')) == (row['width'], row['height']), 'Original Main PNG differs')
        need(row['kind'] == 'main' and row['sourceKind'] == 'actual Android MainActivity' and
             row['expectedWindowFocused'] is True and row['syntheticGps'] is False, 'Main capture provenance differs')
    return rows


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apply', action='store_true', help='Write only after every actual runtime/publication/production guard passes')
    args = parser.parse_args()
    context = read(E / 'final-ui-context.json')
    paths = {key: ref(context[key]) for key in ('liveProof', 'visualProof', 'scopeProof', 'sourceComparison', 'galleryManifest', 'sessionCleanup')}
    scope, live, visual = (read(paths[key]) for key in ('scopeProof', 'liveProof', 'visualProof'))
    release, feature = read(W / 'release-context.json'), read(W / 'feature-merge.json')
    need(release['avdOffBeforeDispatch'] is True, 'Root must confirm sequential AVD shutdown before signing dispatch')
    C, run = release['sourceRevision'], release['ciRun']
    need(re.fullmatch(r'[0-9a-f]{40}', C) is not None and type(run) is int and run > 0, 'Actual source C/CI run required')
    ci_url = f'https://github.com/wjdals988/runmoni/actions/runs/{run}'
    need(release['url'] == ci_url and feature['mergeRevision'] == C and feature['raw']['state'] == 'MERGED' and
         feature['raw']['mergeCommit']['oid'] == C and feature['url'] == feature['raw']['url'], 'Actual feature merge/CI differs')
    O = R / 'work/ci-release' / str(run)
    meta_path = O / f'runmoni-{VERSION}.metadata.json'
    meta, pub, ci, head = (read(path) for path in (meta_path, O / 'publication.json', O / 'ci-verification.json', O / 'public-head-verification.json'))
    need(C == meta['sourceRevision'] == pub['sourceRevision'] == ci['sourceRevision'], 'Actual APK source C differs')
    need((meta['version'], meta['versionCode'], meta['packageName'], meta['fileName']) ==
         (VERSION, CODE, 'com.runmoni.app', f'runmoni-{VERSION}.apk'), 'APK identity differs')
    need(meta['signed'] is True and meta['sourceDirty'] is False and pub['metadata'] == meta and
         meta['certificateSha256'] == read(R / 'config/android-release-signing.json')['certificateSha256'], 'Retained signed/clean metadata differs')
    true_fields(pub, ('publicReleaseConfirmed', 'publicationReady', 'signedCiVerified', 'sidecarBodiesVerified'), 'Actual public publication is required')
    need(pub['draft'] is False and pub['repository'] == 'wjdals988/runmoni' and pub['ciRun'] == ci['ciRun'] == run and
         pub['physicalDeviceTests'] == 0 and pub['googleLoginVerified'] is False and pub['apkBodyDownloaded'] is False,
         'Publication identity/runtime boundary differs')
    true_fields(ci, ('ciSuccess', 'signedApkInspectedByCi', 'nonDebuggablePackageVersionAndEmbeddedRevisionCheckedByCi'), 'CI verification incomplete')
    need(ci['jvm'] == dict(tests=JVM, failures=0, errors=0, skipped=0) and ci['lint']['Error'] == ci['lint']['Fatal'] == 0, 'Actual release CI failed')
    need(meta['connectionReadiness'] == ci['connectionReadiness'] and meta['connectionReadiness']['ready'] is True,
         'Connected configuration readiness missing; this is not runtime login success')
    false_fields(ci, ('physicalDeviceVerified', 'interactiveGoogleLoginVerified', 'apkBodyDownloadedByCloudReviewer'), 'CI runtime boundary changed')
    need(type(meta['sizeBytes']) is int and meta['sizeBytes'] > 0 and re.fullmatch(r'[0-9a-f]{64}', meta['sha256']) is not None, 'Actual APK size/hash required')
    url = f'https://github.com/wjdals988/runmoni/releases/download/v{VERSION}/{meta["fileName"]}'
    need(pub['releaseUrl'] == f'https://github.com/wjdals988/runmoni/releases/tag/v{VERSION}', 'Canonical release URL differs')
    assets = {row['name']: row for row in pub['assets']}
    need(len(assets) == len(pub['assets']) == 3 and set(assets) ==
         {meta['fileName'], f'runmoni-{VERSION}.metadata.json', f'SHA256SUMS-{VERSION}.txt'}, 'Three exact public assets required')
    for name, asset in assets.items():
        need(asset['state'] == 'uploaded' and asset['browser_download_url'] ==
             f'https://github.com/wjdals988/runmoni/releases/download/v{VERSION}/{name}', 'Public asset URL/state differs')
        if name.endswith('.apk'):
            need(asset['size'] == meta['sizeBytes'] and asset['digest'] == 'sha256:' + meta['sha256'], 'Actual APK asset differs')
        else:
            raw = (O / name).read_bytes()
            need(len(raw) == asset['size'] <= 1_000_000 and asset['digest'] == 'sha256:' + digest(raw), 'Actual public sidecar bytes differ')
    sums_path = O / f'SHA256SUMS-{VERSION}.txt'
    need(sums_path.read_text().strip() == f'{meta["sha256"]}  {meta["fileName"]}', 'Published checksum content differs')
    need(head['url'] == url and head['httpStatus'] == 200 and head['matchesVerifiedMetadata'] is True and
         head['contentLength'] == meta['sizeBytes'] and head['apkBodyDownloaded'] is False, 'Actual public HEAD differs')

    binary = verify_scope(scope, ci)
    comparison = read(paths['sourceComparison'])
    need(comparison['releaseSourceRevision'] == C and comparison['allSourceMapsMatch'] is True and
         comparison['sourceMaps'] == {key: binary[key] for key in MAPS}, 'Frozen execution sources were not compared with actual release C')
    freeze = read(ref(comparison['executedSourceFreeze']))
    need(freeze['sourceMaps'] == comparison['sourceMaps'] and binary['sourceFreezeReference'] == ref(comparison['executedSourceFreeze']).name,
         'Frozen input bytes differ')
    captures = main_captures(live, binary)
    true_fields(visual, ('actualRootViewImageReview', 'privacyReviewed', 'syntheticCompletedRunAndPhoto'), 'Root original PNG review required')
    false_fields(visual, ('userVisualApproval', 'physicalDeviceVerified', 'signedReleaseRuntimeVerified', 'originalHistoryOrWalletPublished'),
                 'Root review is not user approval or signed/physical runtime')
    need(len(visual['images']) == 6, 'Root must review six originals')
    for capture in captures:
        rows = [row for row in visual['images'] if row['file'] == capture['file'] and row['sha256'] == capture['sha256']]
        need(len(rows) == 1 and rows[0]['actuallyViewedWithViewImage'] is True and rows[0]['privacyReviewed'] is True,
             'Root must actually view/privacy-review each Main original')
        ref(rows[0])
    export_visual = read(ref('root-export-visual-review.json'))
    true_fields(export_visual, ('actuallyViewedWithViewImage', 'privacyReviewed', 'syntheticPhotoAndCompletedRecord'), 'Root export PNG review incomplete')
    false_fields(export_visual, ('userVisualApproval', 'actualSnsDeliveryVerified'), 'Export review is not user approval/SNS delivery')
    need(ref(export_visual) == ref(live['receiver']) and export_visual['sha256'] == live['receiver']['sha256'] and
         ref(export_visual['identicalGalleryExport']) == ref(live['galleryExport']) and
         export_visual['identicalGalleryExport']['bytesExactlyEqual'] is True and
         export_visual['identicalGalleryExport']['separatelyViewedWithViewImage'] is False,
         'Keep the actual viewed recipient PNG and byte-identical unseparately-viewed gallery PNG distinct')
    cleanup = read(paths['sessionCleanup'])
    true_fields(cleanup, ('ownedEmulatorNoLongerRunning', 'noUnrelatedProcessTerminated'), 'Exact owned AVD cleanup incomplete')
    need(cleanup['ownedAvd'] == 'Runmoni_Cloud_29' and cleanup['finalProcessState'] in ('Z', 'absent') and
         cleanup['physicalDeviceOrUserPcEmulatorUsed'] is False, 'Owned cloud-only cleanup boundary differs')
    cleanup_refs = {ref(row).name: row for row in cleanup['restorationProofs']}
    need(len(cleanup_refs) == 3 and set(cleanup_refs) == {paths['liveProof'].name, 'android-review3.json', 'native2.json'}, 'Cleanup restoration phase set differs')
    need(all(row['completed'] is True and row['originalParentPassed'] is True for row in cleanup_refs.values()), 'Cleanup phase did not complete/pass')

    board, node, baseline = (read(W / name) for name in ('dashboard-production.json', 'dashboard-node-tests.json', 'dashboard-baseline.json'))
    true_fields(board, ('productionVerified',), 'Actual dashboard Production proof required')
    false_fields(board, ('physicalDeviceVerified', 'interactiveGoogleLoginVerified', 'productionPartyWritesVerified', 'userVisualApproval'), 'Dashboard runtime boundary changed')
    deployment = board['dashboard']
    need(deployment['repository'] == 'wjdals988/mydashboard' and deployment['mainCi']['conclusion'] == 'success' and
         deployment['productionDeployment']['state'] == 'success' and re.fullmatch(r'[0-9a-f]{40}', deployment['mergeRevision']) is not None,
         'Actual dashboard main/Production incomplete')
    checks = board['productionPage']['checks']
    need(board['productionPage']['url'] == 'https://coldbrewventi.vercel.app/projects/runmoni' and
         board['productionPage']['httpStatus'] == 200 and len(checks) == 13 and all(value is True for value in checks.values()), 'Canonical thirteen checks incomplete')
    need(board['apk']['sourceRevision'] == C and board['apk']['versionCode'] == CODE and board['apk']['version'] == VERSION and
         board['apk']['sha256'] == meta['sha256'] and board['apk']['sizeBytes'] == meta['sizeBytes'] and board['apk']['url'] == url,
         'Production download differs from actual signed publication')
    need(deployment['localNodeTests'] == node and node['exitCode'] == 0 and node['tests'] == node['passed'] > 0 and
         node['failed'] == node['skipped'] == 0 and node['rawLog'] == 'dashboard-node-tests.log', 'Actual Node test result differs')
    node_raw = (W / node['rawLog']).read_bytes()
    need(digest(node_raw) == node['rawSha256'], 'Node test log bytes changed')
    for label, key in (('tests', 'tests'), ('pass', 'passed'), ('fail', 'failed'), ('skipped', 'skipped')):
        found = re.findall(r'(?m)^(?:ℹ\s*|#\s*)?' + label + r'\s+(\d+)\s*$', node_raw.decode())
        need(len(found) == 1 and int(found[0]) == node[key], 'Measured Node log count differs')
    gallery = read(paths['galleryManifest'])
    need((gallery['version'], gallery['versionCode']) == (VERSION, CODE) and gallery['rootVisualReviewVerified'] is True and
         gallery['userVisualApproval'] is False and len(gallery['images']) == 4, 'Four actually reviewed production gallery originals required')
    gallery_compare = gallery['sourceComparison']
    need(gallery_compare['releaseSourceRevision'] == C and gallery_compare['runtimeAppSourcesMatchRelease'] is True and
         ref(gallery_compare['proof']) == paths['sourceComparison'], 'Gallery source comparison differs')
    capture_set = {(row['file'], row['sha256']) for row in captures}
    for row in gallery['images']:
        ref(row)
        need((row['file'], row['sha256']) in capture_set and ref(row['captureProof']) == paths['liveProof'] and row['source'] == 'MainActivity' and
             row['actuallyViewedWithViewImage'] is True and row['privacyReviewed'] is True, 'Gallery is not a reviewed actual Main original')
    need(len({row['sha256'] for row in gallery['images']}) == len({row['key'] for row in gallery['images']}) == 4 and
         len(board['gallery']) == 4 and {row['sha256'] for row in board['gallery']} == {row['sha256'] for row in gallery['images']}, 'Production gallery selection differs')
    need(all(row['httpStatus'] == 200 and row['matchesCommittedOriginal'] is True for row in board['gallery']), 'Production PNG bytes differ')
    items = read(B / 'src/lib/projects.json')
    before = baseline['projects']
    need(len(items) == len(before) == baseline['projectCount'] and baseline['otherProjectCount'] == len(before) - 1 and
         [row['slug'] for row in items] == [row['slug'] for row in before] and
         [row for row in items if row['slug'] != 'runmoni'] == [row for row in before if row['slug'] != 'runmoni'], 'Other dashboard projects/order changed')
    old, project = (next(row for row in source if row['slug'] == 'runmoni') for source in (before, items))
    need(len(old['releaseNotes']) == baseline['oldReleaseNoteCount'] and project['releaseNotes'][1:] == old['releaseNotes'] and
         len(project['releaseNotes']) == baseline['oldReleaseNoteCount'] + 1 and project['sourceUrl'] == old['sourceUrl'], 'Original Runmoni release history/source changed')
    need(project['apk']['url'] == url and project['apk']['sha256'] == meta['sha256'] and project['apk']['versionCode'] == CODE and
         project['apk']['version'] == VERSION and project['apk']['fileName'] == meta['fileName'] and
         project['apk']['size'] == f'{meta["sizeBytes"]:,} bytes' and
         project['releaseNotes'][0]['version'] == VERSION and project['releaseNotes'][0]['versionCode'] == CODE, 'Local dashboard publication differs')
    expected_shots = [f'/reviews/runmoni/{VERSION}/{row["key"]}.png' for row in gallery['images']]
    need([row['src'] for row in project['screenshots']] == expected_shots and
         {row['path'] for row in board['gallery']} == set(expected_shots), 'Actual gallery paths differ')
    for src, row in zip(expected_shots, gallery['images']):
        path = (B / 'public' / src.lstrip('/')).resolve()
        need(path.is_relative_to((B / 'public/reviews').resolve()) and path.is_file() and
             digest(path.read_bytes()) == row['sha256'], 'Selected local dashboard PNG differs from reviewed original')
    need(set(baseline['oldImages']) == {row['src'] for row in old['screenshots']} and
         len(baseline['oldImages']) == baseline['oldScreenshotCount'], 'Original PNG inventory differs')
    for src, expected in baseline['oldImages'].items():
        path = (B / 'public' / src.lstrip('/')).resolve()
        need(path.is_relative_to((B / 'public/reviews').resolve()) and path.is_file() and digest(path.read_bytes()) == expected,
             'An old dashboard PNG changed or disappeared')
    need(digest((B / 'scripts/update-project-apk.mjs').read_bytes()) == baseline['updaterSha256'], 'Official dashboard updater changed')
    feed = read(R / 'updates/android.json')
    need(feed['schemaVersion'] == 1 and feed['stable'] is None and feed['preview']['versionCode'] == 24 and
         feed['preview']['sourceRevision'] == OLD_C and feed['preview']['certificateSha256'] == meta['certificateSha256'], 'Keep verified old preview24/stable null until this update')

    date = dt.datetime.fromisoformat(pub['publishedAt'].replace('Z', '+00:00')).astimezone(ZoneInfo('Asia/Seoul')).date().isoformat()
    need(project['updatedAt'] == project['releaseNotes'][0]['date'] == date.replace('-', '.'), 'Dashboard publication date differs from actual release time')
    local_warning, ci_warning = binary['lint']['Warning'], ci['lint']['Warning']
    relative = lambda path: path.relative_to(E).as_posix()
    review = f'''# Runmoni0.4.3-dev 전체 화면 UI·공유 게시 검증

frontend-design와 ui-ux-pro-max를 참고해 홈/최초 로그인/탐색/러닝/크루·파티·대화/나·옷장/기록·공유/설정·양식·앱정보·점검의 정보 위계와 조작을 정리했다. 승인 미니미와 정적 트랙, 기존 녹색과 제한된 route blue를 사용하고 과도한 카드·반복 문구를 줄였다. 스킬 추천을 최신 시장 조사나 사용성 개선율로 바꾸지 않는다. [계획·자체 비평](../design-system/runmoni/pages/studio-refresh.md).

## 공개 APK와 실제 출처

- {date} 한국 시간, 같은 보관키의 실기기 검증 전 개발 prerelease0.4.3-dev/code25. [APK]({url}) · [릴리스]({pub['releaseUrl']}) · [구현 PR]({feature['url']}).
- 실제 APK출처 C `{C}`. 크기 `{meta['sizeBytes']:,}`bytes, SHA256 `{meta['sha256']}`, 인증서 SHA256 `{meta['certificateSha256']}`. 후속 문서·피드 커밋 D로 C를 바꾸지 않는다.
- [서명 CI]({ci_url}) 새 JVM189(실패/오류/skip0),release lint오류0·경고{ci_warning}. non-debuggable/패키지·버전·내장 C/보관 인증서/연결 설정·임시 설정 정리와 공개3자산·sidecar/digest·HEAD200/크기를 확인했다. readiness는 설정 확인이며 휴대폰·Google·운영 쓰기 성공이 아니다. Root가 공개 APK 본문을 재다운로드하거나 실행했다고 쓰지 않는다.

## 공유를 사용하는 위치

‘나 → 러닝 기록 → 완료 기록 선택 → 공유’에서 별도 편집 화면으로 이동한다. 기본1:1과4:5·9:16,사진·문구·기록 배치·현재 미니미 위치/크기를 지원하며 미리보기와 같은 PNG를 저장하거나 Android 공유창으로 보낸다. Back은 기록으로 돌아간다. 사진 선택/갤러리 저장·SNS 전달/권한은 실제 휴대폰에서도 별도 확인한다.

## 실행과 검토 범위

- 최종 local TEST debug [{scope['finalLocalBinary']}]({BASE}/{scope['finalLocalBinary']}): 새 JVM189·실패/오류/skip0,lint오류0·경고{local_warning}. 빌드 당시 HEAD `{binary['sourceCommitAtBuild']}`를 공개 APK출처 C로 덮어쓰지 않으며,[전체 입력 SHA와 C의 대조]({BASE}/{relative(paths['sourceComparison'])})를 따로 확인했다. 로컬 검토 CA/Firebase 비활성과 connected release 설정은 다르다.
- 최종 source3 APK쌍에서 Android 일반2/native4=새6개를 실행했다. 앞선 입력의 보존64개와 합쳐 서로 다른70개 method의 통과를 관측했으며,70개 모두를 최종 바이너리에서 새로 실행한 것이 아니다. [범위/선택 원문]({BASE}/{relative(paths['scopeProof'])})와[실제 frozen FQCN method·설치쌍·source snapshot 대조]({BASE}/scope-root-audit.json)를 따른다. 처음 전체실패와 source2 native전체실패를 성공으로 바꾸지 않는다.
- 새6은 보통 글씨 채팅과 현재 위치 공유 동의의 일반2,실제320dp/font2의 대화상자4다. 채팅 최신 본문 전체 bounds/기존 독자 스크롤/실제 IME 입력·Back,현재 루트의100m·75초 고지/수동확인/본문 scroll/전체48dp 안전영역/취소 후 불변을 확인했다. 전체70개는 ComponentActivity/in-memory/inert-map fixture이며 실제 Main·휴대폰·Firebase 성공은 별도다.
- [실제 Main 단일 실행]({BASE}/{relative(paths['liveProof'])})은 전체 PASS다. nonce 합성 완료 기록/사진으로 홈·옷장·기본1:1/9:16 공유·실제 OS 사진 선택·Morning Miles 키 입력·미니미 조작·갤러리 저장·공유창·다른 UID의 READ·Back/재진입·지도 밖 조작을 확인했다. 저장·cache·수신 PNG bytes를 비교했다. 저장 안내는 실제 추가 스크롤 {live['galleryAcknowledgmentScrollActions']}회 뒤 확인했다. 접근성 설명의 비율·문구·미니미 포함 복원을 관측했으며 모든 위치 수치를 추정한 것이 아니다. 실제 SNS 게시나 새 지도 제공자 인증은 아니다.
- [Root 원본 PNG 검토]({BASE}/{relative(paths['visualProof'])}): 실제 Main 원본6장(홈/옷장/기본1:1/사진9:16/미니미 편집/지도)을 열어 내용과 개인정보를 확인했다. 모든 페이지의 실제 Main 촬영이나 사용자 시각 승인은 아니다. 운영 갤러리는 이 가운데 검증한4장이다. 이전 Launcher 결과는 baseline이며 이번 새 실행으로 요구하거나 계상하지 않는다.
- 원래 DB행/전체XML/프로필/account·소유 설정/권한/전원과 전/후/복원후6맵을 확인하고 nonce 출력만 정리했다. [정확한 소유 AVD 종료]({BASE}/{relative(paths['sessionCleanup'])})를 확인했다. 전체 OS 데이터·원래 GPS fix·picker 캐시 전체 복원을 주장하지 않는다.

## 실패 보존과 미확인 범위

처음 큰 글씨 러닝 지도 Text높이0/미니미 준비 timeout을 보존하고 큰 글씨 지도 높이와 장식 Stage의 loading semantics를 보완했다. 기존120초/전체loading조건을 완화하지 않았고 timeout의 구체 원인은 미확정이다. legacy 동의 fixture를 현재 root/run/nonce 동선으로 갱신하고 큰 글씨 채팅의 실제 본문·IME 측정을 강화했다. [실패/수정/원문]({BASE}/README.md)을 따른다. 이전 job0/startup_failure는 보존된 과거 실행이며 이번 실제 서명 CI 성공으로 그 원인을 확정하지 않는다.

승인 원본/58ID/13슬롯/9탭/0P·기존 기록/지갑/보유권·100m 동의/FGS/runId를 유지했다. 새 원화·키·Rules·의존성을 추가하지 않았다. 휴대폰·Google/운영 프로필/Firestore 쓰기·야외GPS/장시간 BG·Doze/OEM/API31+·네이버 운영 인증·카카오톡/Instagram 전달·사용자 시각 승인과 공개 서명 APK 런타임은 미검증이다. [지도 연결](MAP_PROVIDER_OPTIONS.md)·[프로필 점검](FIREBASE_PROFILE_CONNECTION_CHECK.md)을 따른다.

## 운영 대시보드와 업데이트 피드

[운영 대시보드](https://coldbrewventi.vercel.app/projects/runmoni) · [대시보드 PR]({deployment['pullRequest']}) · [main CI]({deployment['mainCi']['url']}) · Production `{deployment['productionDeployment']['id']}` · main `{deployment['mergeRevision']}`. canonical13항목과 검토한 PNG4장의 실제 운영 bytes,Node {node['tests']}개를 확인했다. 다른{baseline['otherProjectCount']}프로젝트·이전{baseline['oldReleaseNoteCount']}개 내역·이전{baseline['oldScreenshotCount']}개 PNG를 보존했다. [운영 근거]({BASE}/dashboard-production-verification.json).

preview25/stable null은 이 문서/피드 변경으로 준비한다. canonical raw 전체 bytes는 후속 문서 PR 병합 뒤 별도 확인한다. 기존0.4.2 실제 APK출처 `{OLD_C}`와0.1.5 실제 APK출처 `{ORIGINAL_015_C}`는 그대로다. 다음 작업은 휴대폰 같은키 업데이트·공유/위젯/알림·야외GPS/절전·Google/프로필 확인과 남은 승인 원본 기반 의복·크루 운영 기능이다.
'''
    headline = f'''# 0.4.3-dev 전체 화면 UI·공유 공개 — {date} 한국 시간

{MARKER}
- frontend-design/ui-ux-pro-max 기반 전체 화면 위계와 visible 공유 진입·기본1:1 사진/기록/미니미 PNG를 반영했다. 같은 보관키/code25,실제 APK출처 `{C}`,{meta['sizeBytes']:,}bytes,SHA256 `{meta['sha256']}`. 서명 CI{run} JVM189/lint0·경고{ci_warning},운영13항목/PNG4/Node{node['tests']}개를 확인했다.
- [게시 검증](docs/{REVIEW}). 최종 local새189/lint0·경고{local_warning},Android새6와이전보존64/서로다른70을구분한다. 실제Main단일PASS/Root원본6·원래DB/XML/account/소유환경복원·AVD종료를확인했다. 이전Launcher·실패원문을보존하고휴대폰/Google/SNS/운영네이버/사용자승인·공개서명APK런타임은별도다. canonical raw 피드는후속병합뒤확인한다.

'''
    continuation = f'''# 최신 이어가기 — 0.4.3-dev/code25 공개, {date}

{MARKER}
[게시 검증]({REVIEW})을 시작점으로 삼는다. 실제 APK출처 `{C}`를 후속 문서/피드 커밋으로 바꾸지 않는다. 같은 키 서명 CI{run}·공개3자산·운영 대시보드13항목/PNG4·Node{node['tests']}개를 확인했다. 공유 진입은 ‘나 → 러닝 기록 → 기록 선택 → 공유’다.

최종 Android 새6와 이전 보존64/서로 다른70,실제 Main 단일PASS/원본6을 구분한다.70개 최종 새 실행/모든 페이지 Main 촬영·이전Launcher 새 실행으로 합산하지 않는다. 원래 데이터/소유 환경을 복원하고 정확한 AVD를 종료했다. 기존0.4.2 출처176a9cbe...와0.1.5 출처75c39ca...·이전CI실패는 보존 이력이다.

1. 휴대폰에서 같은키 업데이트/기존 기록·아이템 유지와 사진 선택·1:1 PNG 저장·카카오톡/Instagram 전달을 확인한다. 실제 휴대폰·SNS 성공을 빌드/chooser 관측으로 대신하지 않는다.
2. 홈 위젯·러닝 알림/화면 끔/장시간GPS/절전·OEM/API31+를 확인한다. 이전 Launcher/ComponentActivity 결과를 새 실기기 제어로 확대하지 않는다.
3. 운영 프로필/닉네임 예약·Google 연결·지도 제공자 설정을 확인한다. 같은 Rules면 반복 게시를 요구하지 않는다. [프로필 점검](FIREBASE_PROFILE_CONNECTION_CHECK.md) · [지도 조건](MAP_PROVIDER_OPTIONS.md).
4. 승인 원본·58ID/13슬롯/9탭·0P/보유권·포인트를 보존하며 남은 의복과 크루 승계·신고/차단을 진행한다. 완료한 무료·성별·전체UI·공유를 재구현하지 않는다.

순차 heavy·캐시 우선/오프라인 작업자1·클라우드 전용 AVD/종료 확인을 유지한다. 새 키·공개 터널·미승인 원화·측정하지 않은 개선율을 추가하지 않는다. 아래는 이전 시점의 보존 기록이다.

'''
    preview = {key: meta[key] for key in ('version', 'versionCode', 'fileName', 'sizeBytes', 'sha256', 'certificateSha256', 'sourceRevision', 'signed')}
    preview.update(package=meta['packageName'], buildType='release', physicalDeviceTests=0, downloadUrl=url,
                   releaseUrl=pub['releaseUrl'], notes='전체 화면 UI와 기본1:1 사진·러닝 기록·미니미 공유를 개선한 실기기 검증 전 개발 프리뷰입니다.')
    existing = [R / path for path in ('AGENTS.md', 'CHANGELOG.md', 'updates/android.json', 'docs/DEVELOPMENT_CONTINUATION.md', 'docs/DEVELOPMENT_PRIORITIES.md')]
    candidate = R / 'docs/RELEASE_CANDIDATE_0.4.3_20261111.md'
    if candidate.is_file():
        existing.append(candidate)
    originals = {path: path.read_bytes() for path in existing}
    need(all(MARKER.encode() not in raw for raw in originals.values()), 'Verified release block already exists; do not duplicate it')
    plan = {R / 'docs' / REVIEW: review.encode(), R / 'updates/android.json': encoded(dict(feed, preview=preview)),
            R / 'CHANGELOG.md': headline.encode() + originals[R / 'CHANGELOG.md']}
    for name in ('DEVELOPMENT_CONTINUATION.md', 'DEVELOPMENT_PRIORITIES.md'):
        path = R / 'docs' / name
        plan[path] = continuation.encode() + originals[path]
    agents = originals[R / 'AGENTS.md'].decode()
    anchor = '## 변경 이력\n'
    need(agents.count(anchor) == 1, 'AGENTS history anchor changed')
    history = f'\n- {date} 공개:0.4.3-dev/code25 전체 화면 UI·visible 공유·기본1:1 사진/기록/미니미 PNG. {MARKER} 실제 APK출처 `{C}`를 후속 문서/피드로 바꾸지 않는다. [게시 근거](docs/{REVIEW}). 같은키 서명 CI{run} JVM189/lint0·local새189/Android새6+보존64/서로다른70·실제Main단일PASS/Root원본6/운영13+PNG4를 구분한다. 이전Launcher·실패원문/복원을 보존하고 휴대폰/Google/SNS/네이버운영·사용자승인·공개서명APK런타임은 미검증이다. 새 키/Rules/의존성/원화 없이 원본·데이터·과거 APK출처를 유지했다.\n'
    plan[R / 'AGENTS.md'] = agents.replace(anchor, anchor + history, 1).encode()
    if candidate in originals:
        plan[candidate] = (f'> 후속 공개 상태: [{date} 전체 화면 UI·공유 게시 검증]({REVIEW}). {MARKER} 아래의 미게시/시작 실패는 이전 후보 시점의 보존 이력이며,새 실제 APK출처는 게시 검증의 C를 따른다.\n\n'.encode() + originals[candidate])
    for name in ('publication.json', 'ci-verification.json', 'public-head-verification.json', f'runmoni-{VERSION}.metadata.json', f'SHA256SUMS-{VERSION}.txt'):
        plan[E / name] = (O / name).read_bytes()
    for name, source in (('dashboard-production-verification.json', W / 'dashboard-production.json'),
                         ('dashboard-node-tests.json', W / 'dashboard-node-tests.json'), ('dashboard-node-tests.log', W / 'dashboard-node-tests.log')):
        plan[E / name] = source.read_bytes()
    for path in plan:
        need(not path.is_symlink() and path.resolve().is_relative_to(R.resolve()) and
             not any('private' in part.lower() or part == '.secrets' for part in path.parts), 'Unsafe public output path')
        need(path in originals or not path.exists(), 'Immutable evidence/review output already exists: ' + path.name)
    report = {'apply': args.apply, 'actualApkSourceC': C, 'ciRun': run, 'liveProof': relative(paths['liveProof']),
              'freshFinalAndroid': 6, 'retainedEarlierAndroid': 64, 'distinctAcrossInputs': 70,
              'all70FreshClaimed': False, 'actualMainOriginals': 6, 'newLauncherExecutionsRequiredOrCounted': 0,
              'plannedFiles': {str(path.relative_to(R)): digest(raw) for path, raw in plan.items()}}
    print(json.dumps(report, ensure_ascii=False, indent=2))
    if not args.apply:
        return
    need(all(path.read_bytes() == raw for path, raw in originals.items()), 'Existing docs/feed changed while validating the plan')
    written = []
    try:
        for path, raw in plan.items():
            written.append(path)
            path.write_bytes(raw)
    except BaseException:
        for path in reversed(written):
            if path in originals:
                path.write_bytes(originals[path])
            else:
                path.unlink(missing_ok=True)
        raise


if __name__ == '__main__':
    main()
