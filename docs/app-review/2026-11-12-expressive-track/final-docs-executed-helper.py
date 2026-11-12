#!/usr/bin/env python3
"""Root-only expressive-track docs/feed preparation after publication and production proof.

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
W = R / 'work/expressive-track'
E = R / 'docs/app-review/2026-11-12-expressive-track'
B = Path('/workspace/mydashboard')
VERSION, CODE, JVM = '0.4.4-dev', 26, 189
OLD_C = 'eb32a30d20b0223d436e80ca09c7c17e25d60c69'
OLDER_042_C = '176a9cbe1112efa05e9fc55d913738ee1778a57f'
ORIGINAL_015_C = '75c39ca900b140cfb02c1a47807a5955c8b8db7f'
REVIEW = 'RELEASE_REVIEW_0.4.4_20261112.md'
BASE = 'app-review/2026-11-12-expressive-track'
MARKER = '<!-- verified-expressive-release-0.4.4-v1 -->'
MAPS = ('productSources', 'testSources', 'resources', 'rendererAssets', 'approvedSourceCopies', 'configurationSources')
PREFIX = 'com.runmoni.app.ui.'
EXPECTED_GROUPS = {
    'android-review1.json': {
        'closet': (1, {PREFIX + 'ConsumerClosetTest'}),
        'studio': (2, {PREFIX + 'StudioRefreshUiTest'}),
        'main-design': (2, {PREFIX + 'RunmoniDesignTest'}),
        'entry': (4, {PREFIX + 'AppEntryUiTest'}),
        'home-weather': (2, {PREFIX + 'HomeWeatherLayoutTest'}),
        'colors': (2, {PREFIX + 'RunningKitColorsUiTest#namedRadioChoicesWrapWithoutShrinkingAcrossSmallAndLargeTextViewports',
                       PREFIX + 'RunningKitColorsUiTest#closetColorChangesKeepReadyItemSelectionsInventoryAndGender'}),
    },
    'native1.json': {
        'native-closet-hierarchy': (1, {PREFIX + 'HierarchyClosetLargeTextUxTest'}),
        'native-closet-navigation': (1, {PREFIX + 'ClosetNavigationNativeUiTest'}),
        'native-closet-colors': (1, {PREFIX + 'RunningKitColorsNativeUiTest'}),
    },
}


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
    need(path.is_relative_to(E.resolve()) and path.is_file(), 'Evidence must exist inside the expressive-track review directory')
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


def observed_motion_zero(proof, flag, field):
    need(proof.get(flag) is True, 'Actual zero-scale observation is required: ' + flag)
    values = proof[field]
    scales = {'window_animation_scale', 'transition_animation_scale', 'animator_duration_scale'}
    need(isinstance(values, dict) and set(values) == scales, 'All three actually read system motion scales are required')
    need(all(type(value) in (str, int, float) and float(value) == 0.0 for value in values.values()),
         'An observed system animation scale was not zero')


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


def frozen_test_methods(binary):
    """Read only exact matching frozen local test blobs; never execute Git/test code."""
    classes = {}
    for name, expected in binary['testSources'].items():
        path = R / name
        need(not path.is_symlink() and path.resolve().is_relative_to((R / 'app/src').resolve()) and
             path.is_file() and digest(path.read_bytes()) == expected, 'Current test blob differs from final frozen input: ' + path.name)
        # The selectors run in the instrumentation APK. JVM classes with the
        # same name must not supply those method IDs, even when their hashes match.
        if path.suffix != '.kt' or not path.resolve().is_relative_to((R / 'app/src/androidTest').resolve()):
            continue
        text = path.read_text()
        package = re.search(r'^package\s+([\w.]+)', text, re.M)
        starts = list(re.finditer(r'^class (\w+)\b', text, re.M))
        need(not starts or package is not None, 'Test source has no package')
        for index, match in enumerate(starts):
            fqcn = package[1] + '.' + match[1]
            need(fqcn not in classes, 'Duplicate fully-qualified test class')
            body = text[match.start():starts[index + 1].start() if index + 1 < len(starts) else len(text)]
            classes[fqcn] = re.findall(r'@Test\s+fun\s+(\w+)', body)
    return classes


def verify_scope(scope, ci):
    need(scope['finalLocalBinary'] == 'binary-build4.json', 'Final expressive-track local binary differs')
    if 'version' in scope:
        need(scope['version'] == VERSION, 'Scope version differs')
    if 'versionCode' in scope:
        need(scope['versionCode'] == CODE, 'Scope version code differs')
    need(scope['finalLocalJvm'] == ci['jvm'] == dict(tests=JVM, failures=0, errors=0, skipped=0), 'Measured fresh JVM result differs')
    need(scope['finalLocalLint']['Error'] == scope['finalLocalLint']['Fatal'] == 0, 'Local lint failed')
    need(scope['freshFinalAndroid']['total'] == scope['distinctPassedMethods'] == 16 and scope['earlierResultsReused'] is False,
         'Sixteen fresh distinct methods required; earlier results must not be reused')
    names = scope['freshFinalAndroid']['proofs']
    need(len(names) == 2 and set(names) == set(EXPECTED_GROUPS), 'Fresh default13/native3 proof selection differs')
    for key in ('physicalDeviceVerified', 'googleLoginVerified', 'signedReleaseRuntimeVerified',
                'socialAppDeliveryVerified', 'productionWritesVerified'):
        if key in scope:
            need(scope[key] is False, 'Scope runtime boundary changed: ' + key)
    binary = read(ref(scope['finalLocalBinary']))
    need((binary['version'], binary['versionCode'], binary['sourceFreezeReference']) == (VERSION, CODE, 'source-freeze4.json') and
         binary['buildSucceeded'] is True and binary['jvmReportsFresh'] is True and binary['jvm'] == ci['jvm'] and
         binary['lint'] == scope['finalLocalLint'] and binary['signing'] == 'local TEST debug, not retained release signing',
         'Final fresh TEST debug build differs')
    need(read(ref(binary['sourceFreezeReference']))['sourceMaps'] == {key: binary[key] for key in MAPS}, 'Final source-freeze4 differs')
    need(binary['approvedSourceCopies'] == {
        'docs/design/avatars/v0.3.0/running-motion-art-source.png':
        'f9035f29ced0c7ba513dfe24cb727410a7204dc482fc457e806c7a9b3cdbfa65'}, 'Approved original artwork changed')
    classes = frozen_test_methods(binary)
    methods = set()
    for name in names:
        proof = read(ref(name))
        true_fields(proof, ('passed', 'completed', 'preferencesRestoredExactly', 'profileRestoredExactly',
                           'communityAccountPreferencesRestoredExactly', 'controlledSettingsPermissionsPowerRestoredExactly',
                           'driverSourceUnchanged', 'observerHelperSourceUnchanged', 'binaryEvidenceUnchanged'),
                    'Fresh phase/restoration/source verification incomplete: ' + name)
        false_fields(proof, ('physicalDeviceVerified', 'googleLoginVerified', 'productionWritesVerified',
                            'android31RuntimeVerified', 'signedReleaseRuntimeVerified'), 'Formal runtime boundary changed')
        need((proof['version'], proof['versionCode'], proof['api']) == (VERSION, CODE, 29), 'Formal identity/API differs')
        need(read(ref({'file': proof['binaryReference'], 'sha256': proof['binaryReferenceSha256']})) == binary,
             'Fresh phase used another local APK pair')
        installed_pair(proof, binary)
        for field in ('sourceMapsBefore', 'sourceMapsAfter', 'sourceMapsAfterRestoration'):
            observed_maps(proof[field], binary, name + '/' + field)
        for stem in ('executedDriver', 'observerHelper'):
            ref({'file': proof[stem], 'sha256': proof[stem + 'Sha256']})
        observed_motion_zero(proof, 'motionScaleZeroObserved', 'motionScaleObservedBeforeProductGroups')
        expected = EXPECTED_GROUPS[name]
        need(len(proof['groups']) == len(expected) and {group['name'] for group in proof['groups']} == set(expected),
             'Actual executed group set differs')
        phase_total = 0
        for group_name, (count, selectors) in expected.items():
            group = instrumentation_group(proof, group_name)
            need(group['actual'] == count and len(group['classes']) == len(selectors) and set(group['classes']) == selectors and
                 group['originalProfilePreserved'] is True, 'Actual group selectors/count/profile preservation differ')
            native = name == 'native1.json'
            need(group['viewport']['native320Font2RequiredByClass'] is native, 'Actual native/default viewport observation differs')
            if native:
                need(float(group['viewport']['fontScale']) >= 1.9, 'Actual native large-text font scale required')
            ids = []
            for selector in group['classes']:
                clazz, *specific = selector.split('#')
                need(clazz in classes, 'Executed class is not in the actual frozen test blobs')
                selected = specific or classes[clazz]
                need(all(method in classes[clazz] for method in selected), 'Selected method is not in its frozen class')
                ids.extend(clazz + '#' + method for method in selected)
            need(len(ids) == len(set(ids)) == count and not methods.intersection(ids), 'Fresh distinct method IDs/count differ')
            methods.update(ids)
            phase_total += count
        need(proof['actualPassedProductMethods'] == phase_total == (3 if name == 'native1.json' else 13), 'Measured phase total differs')
    need(len(methods) == 16, 'Fresh distinct method union differs')
    if 'methods' in scope:
        reported = [row if isinstance(row, str) else row['method'] for row in scope['methods']]
        need(len(reported) == 16 and set(reported) == methods, 'Scope method list differs from the executed frozen selectors')
    return binary


def main_captures(live, binary):
    need((live['version'], live['versionCode'], live['api'], live['sourceKind']) ==
         (VERSION, CODE, 29, 'actual Android MainActivity'), 'Actual Main identity differs')
    true_fields(live, ('passed', 'completed', 'driverSourceUnchanged', 'observerHelperSourceUnchanged', 'environmentHelperSourceUnchanged',
                      'preferencesRestoredExactly', 'profileRestoredExactly', 'accountPreferencesRestoredExactly',
                      'controlledEnvironmentExactlyRestored', 'databaseExactlyRestored', 'binaryEvidenceUnchanged',
                      'resultReturnedAfterEditorBack', 'existingShareToolTabsTapped', 'actualClosetTabSelectedObserved', 'nonceFixtureCleanupSucceeded',
                      'originalHistoryExcludedFromExtraScreenshots', 'homeCaptureContainsOnlyNonceReviewRun', 'syntheticCompletedRun'),
                'Single actual Main changed-UI/basic-tool-tap path/restoration is incomplete')
    false_fields(live, ('physicalDeviceVerified', 'googleLoginVerified', 'productionWritesVerified', 'android31RuntimeVerified',
                       'signedReleaseRuntimeVerified', 'socialAppDeliveryVerified', 'syntheticGps', 'GPSInjectedByThisDriver',
                       'mapProviderChangedOrNewProviderAuthenticated', 'newAuthenticationRequestedByDriver',
                       'priorShareExportFlowReexecuted'), 'Main runtime/export boundary changed')
    observed_motion_zero(live, 'motionScaleZeroObservedInActualMain', 'motionScaleObservedBeforeMain')
    need(live['fixtureTrackPointCount'] == 0 and live['restorationCommandFailures'] == [] and
         live['fixture']['rawGpsIncluded'] is False and live['fixture']['fixtureDirectWalletWrites'] is False,
         'Main controlled fixture/cleanup differs')
    need(read(ref({'file': live['binaryReference'], 'sha256': live['binaryReferenceSha256']})) == binary, 'Main used another local binary')
    installed_pair(live, binary)
    for field in ('sourceMapsBefore', 'sourceMapsAfter', 'sourceMapsAfterRestoration'):
        observed_maps(live[field], binary, 'Actual Main/' + field)
    for stem in ('executedDriver', 'observerHelper', 'environmentHelper'):
        ref({'file': live[stem], 'sha256': live[stem + 'Sha256']})
    need('1:1' in live['defaultPreviewDescription'] and live['previewAfterActualReopen'] == live['defaultPreviewDescription'],
         'Actual basic 1:1 draft/reopen observation differs')
    # These operations belong to the published0.4.3 baseline, not this fresh UI scope.
    for key in ('actualSystemPhotoPickerUsed', 'actualCaptionKeyboardObserved', 'actualMiniMiMovementActionObserved',
                'actualGallerySaveAcknowledgmentObserved', 'galleryCacheReceiverPngBytesExactlyEqual'):
        if key in live:
            need(live[key] is False, 'Do not promote a prior export operation into this changed-UI execution: ' + key)
    rows = live['captures']
    need(len(rows) == len({row['file'] for row in rows}) == 4, 'Four distinct actual Main originals required')
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
    true_fields(visual, ('actualRootViewImageReview', 'privacyReviewed'), 'Root original PNG review required')
    false_fields(visual, ('userVisualApproval', 'physicalDeviceVerified', 'signedReleaseRuntimeVerified', 'originalHistoryOrWalletPublished'),
                 'Root review is not user approval or signed/physical runtime')
    need(len(visual['images']) == 4, 'Root must review four originals')
    for capture in captures:
        rows = [row for row in visual['images'] if row['file'] == capture['file'] and row['sha256'] == capture['sha256']]
        need(len(rows) == 1 and rows[0]['actuallyViewedWithViewImage'] is True and rows[0]['privacyReviewed'] is True,
             'Root must actually view/privacy-review each Main original')
        ref(rows[0])
    cleanup = read(paths['sessionCleanup'])
    true_fields(cleanup, ('ownedEmulatorNoLongerRunning', 'noUnrelatedProcessTerminated'), 'Exact owned AVD cleanup incomplete')
    need(cleanup['ownedAvd'] == 'Runmoni_Cloud_29' and cleanup['finalProcessState'] in ('Z', 'absent') and
         cleanup['physicalDeviceOrUserPcEmulatorUsed'] is False, 'Owned cloud-only cleanup boundary differs')
    cleanup_refs = {ref(row).name: row for row in cleanup['restorationProofs']}
    need(len(cleanup_refs) == 3 and set(cleanup_refs) == {paths['liveProof'].name, 'android-review1.json', 'native1.json'}, 'Cleanup restoration phase set differs')
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
    need(feed['schemaVersion'] == 1 and feed['stable'] is None and feed['preview']['versionCode'] == 25 and
         feed['preview']['sourceRevision'] == OLD_C and feed['preview']['certificateSha256'] == meta['certificateSha256'], 'Keep verified old preview25/stable null until this update')

    date = dt.datetime.fromisoformat(pub['publishedAt'].replace('Z', '+00:00')).astimezone(ZoneInfo('Asia/Seoul')).date().isoformat()
    need(project['updatedAt'] == project['releaseNotes'][0]['date'] == date.replace('-', '.'), 'Dashboard publication date differs from actual release time')
    contrast = read(ref('contrast.json'))
    need(len(contrast['endpointPairs']) == 6 and contrast['allPixelsOrAnimationFramesMeasured'] is False,
         'Keep six static endpoint contrast checks separate from pixel/motion verification')
    theme_path = R / 'app/src/main/java/com/runmoni/app/ui/RunmoniTheme.kt'
    need(digest(theme_path.read_bytes()) == binary['productSources'][str(theme_path.relative_to(R))], 'Theme source differs from frozen input')
    colors = dict(re.findall(r'val (\w+) = Color\(0xFF([0-9A-Fa-f]{6})\)', theme_path.read_text()))
    expected_pairs = {('#' + colors[front], '#' + colors[back]) for front in ('Primary', 'Muted') for back in ('Paper', 'Cream', 'MintWash')}
    need({(row['foreground'], row['background']) for row in contrast['endpointPairs']} == expected_pairs, 'Endpoint color roles differ from frozen theme')
    def luminance(value):
        rgb = [int(value[index:index + 2], 16) / 255 for index in (1, 3, 5)]
        linear = [component / 12.92 if component <= .04045 else ((component + .055) / 1.055) ** 2.4 for component in rgb]
        return sum(component * weight for component, weight in zip(linear, (.2126, .7152, .0722)))
    for row in contrast['endpointPairs']:
        high, low = sorted((luminance(row['foreground']), luminance(row['background'])), reverse=True)
        actual = (high + .05) / (low + .05)
        need(actual >= 4.5 and abs(row['ratio'] - actual) <= .011, 'Measured endpoint contrast differs')
    local_warning, ci_warning = binary['lint']['Warning'], ci['lint']['Warning']
    relative = lambda path: path.relative_to(E).as_posix()
    review = f"""# Runmoni0.4.4-dev 트랙·선택 강조 게시 검증

frontend-design/ui-ux-pro-max의 제안과 기존 사용자 피드백을 참고해 미니미 주변의 정적 민트/라임/클레이 트랙,홈4dp 색상 띠,공통 분류·옷장 탭의 선택 색면/150ms 전환을 반영했다. 승인 sprite는 그대로이며 그림을 실제 GPS 경로나 달성 성과로 표시하지 않는다. 모든 제안이 완료된 것은 아니며,P1/P2 공유 테마·큰 의복 비교·다크 테마 등은 별도 작업이다. [우선순위/설계 비평](UI_UX_EXPRESSIVE_PRIORITIES_20261112.md).

## 공개 APK와 출처

- {date} 한국 시간,같은 보관키의 실기기 검증 전 개발 prerelease0.4.4-dev/code26. [APK]({url}) · [릴리스]({pub['releaseUrl']}) · [구현 PR]({feature['url']}).
- 실제 APK출처 C `{C}`. 크기 `{meta['sizeBytes']:,}`bytes,SHA256 `{meta['sha256']}`,인증서 SHA256 `{meta['certificateSha256']}`. 후속 문서·피드 커밋 D로 C를 바꾸지 않는다.
- [서명 CI]({ci_url}) 새 JVM189(실패/오류/skip0),release lint오류0·경고{ci_warning}. non-debuggable/패키지·버전·내장 C/보관 인증서·연결 설정·임시 파일 정리와 공개3자산/sidecar/digest·HEAD200/크기를 확인했다. readiness는 설정 확인이며 휴대폰·Google·운영 쓰기 성공이 아니다. Root가 공개 APK 본문을 다시 다운로드하거나 실행했다고 쓰지 않는다.

## 최종 로컬 실행 범위

- 최종 입력 [{scope['finalLocalBinary']}]({BASE}/{scope['finalLocalBinary']}) / `source-freeze4.json`: 새 JVM189·실패/오류/skip0,lint오류0·경고{local_warning}. 초기0.4.4의 build-result.json/build-result2.json/build-result3.json과 각 원문은 이전 입력으로 보존하며189를 더하거나 최종 새 결과로 바꾸지 않는다. 빌드 당시 HEAD `{binary['sourceCommitAtBuild']}`를 공개 APK출처 C로 덮어쓰지 않으며,[전체 입력 SHA와 C의 대조]({BASE}/{relative(paths['sourceComparison'])})를 별도로 확인했다. TEST debug의 로컬 검토 CA/Firebase 비활성과 connected release 설정은 다르다.
- [Android 범위]({BASE}/{relative(paths['scopeProof'])}): 최종 APK쌍의 일반13/native옷장3=새16개가 모두 통과했다. 일반은 옷장1·Studio동선2·러닝/5탭2·첫 진입4·홈 날씨 배치2·색 선택2이며,native는 실제320dp/systemfont2에서 옷장 기본형/분류·마지막 품목/팔레트·색 선택 각1이다. 실제 원문 OK개수·selector·SHA가 맞는 frozen FQCN의 method를 대조해 서로 다른16개임을 확인했다. 이전0.4.3의70개나 Launcher 결과를 이번 새 실행에 합산하지 않았다.
- 이16개는 ComponentActivity/in-memory/inert-map fixture다. 일반/native phase가 세 system animation scale을 실제로 읽어0임을 확인하고 그 설정에서 조작/레이아웃을 검사했다. 전체 페이지 Main 촬영·휴대폰·Google/운영 백엔드 검증이 아니다. [정적 endpoint 대비]({BASE}/contrast.json)는 foreground/background6조합4.5이상이며 전체 픽셀/중간 애니메이션 프레임/실제150ms 시간이나 모든 모션 배율 검증을 뜻하지 않는다. 좌표의 height 제한과 Canvas clip 보강은 원문/입력별 기록을 따른다.
- [실제 Main 단일 PASS]({BASE}/{relative(paths['liveProof'])}): nonce 합성 완료 기록으로 홈·옷장·기본1:1 공유 편집·지도4장을 관측했다. Main에서도 세 system animation scale의 실제0을 읽고,옷장의 하의→상의 탭을 탭한 뒤 selected 속성을 확인했다. 기존 기록/미니미/배경 도구 탭을 실제로 탭하고,기록으로 Back·재진입 뒤 기본 draft 설명 일치와 지도 밖 조작을 확인했다. 도구 탭의 selected 속성을 관측하거나 이번 새 선택 색면이 이 도구에도 적용됐다고 주장하지 않는다. 기본1:1과 기존 기록/지갑·위치 정책을 유지한다.
- 이번 Main은 변경된 시각 영역과 기존 기본 공유 진입/왕복에 한정했다. 합성 사진 fixture는 준비/정리했으나 사진Picker·IME 문구 입력·사진 이동·갤러리 저장·공유창·별도 UID receiver/PNG export를 재실행하지 않았다(`priorShareExportFlowReexecuted=false`). 해당 성공은 [이전0.4.3 게시 검증](RELEASE_REVIEW_0.4.3_20261112.md)의 이전 입력 이력이며0.4.4 새 성공으로 표시하지 않는다. 실제 SNS 전달도 별도다.
- [Root 원본 검토]({BASE}/{relative(paths['visualProof'])}): 실제 Main 원본4장(홈/옷장/기본1:1/지도)을 열어 내용과 개인정보를 확인했다. 운영 갤러리4장은 이 원본이며,사용자 시각 승인·공개 서명 APK 실행이나 모든 페이지의 실제 Main 검증은 아니다.
- 각 formal phase의 원래 XML/프로필/account·소유 설정/권한/전원과 실제 전/후/복원후6맵·설치 APK쌍을 확인했다. Main의 원래 DB행/전체XML/프로필/account·소유 환경을 복원하고 nonce fixture를 정리했다. [정확한 소유 AVD 종료]({BASE}/{relative(paths['sessionCleanup'])})를 확인했다. 전체 OS 파일/원래 GPS fix/picker 캐시 전체 복원을 주장하지 않는다.

## 운영 대시보드와 남은 확인

[운영 대시보드](https://coldbrewventi.vercel.app/projects/runmoni) · [대시보드 PR]({deployment['pullRequest']}) · [main CI]({deployment['mainCi']['url']}) · Production `{deployment['productionDeployment']['id']}` · main `{deployment['mergeRevision']}`. canonical13항목과 검토한 원본 PNG4장의 운영 bytes,Node{node['tests']}개를 확인했다. 다른{baseline['otherProjectCount']}프로젝트·이전{baseline['oldReleaseNoteCount']}개 내역·이전{baseline['oldScreenshotCount']}개 PNG를 보존했다. [운영 근거]({BASE}/dashboard-production-verification.json).

preview26/stable null은 이 문서/피드 변경으로 준비하며,canonical raw 전체 bytes는 문서 PR 병합 뒤 별도 확인한다.0.4.3 실제 APK출처 `{OLD_C}`,0.4.2 `{OLDER_042_C}`,0.1.5 `{ORIGINAL_015_C}`를 후속 커밋으로 바꾸지 않는다.

승인 원본/58ID/13슬롯/9탭/0P·기록/지갑/보유권·100m 동의/FGS/runId는 유지했다. 새 원화·키·Rules·의존성을 추가하지 않았다. 휴대폰/Google/운영 Firestore/네이버 인증·야외GPS/장시간BG·Doze/OEM/API31+·SNS전달·사용자 시각 승인·공개 서명 APK 런타임과 실제150ms 시간/모든 중간 애니메이션 프레임·다른 배율 검증은 미확인이다. 다음은 휴대폰 같은키 업데이트/홈·옷장 선택·기록 유지,계획의P1/P2와 추가 프레임/다른 배율 검증이다.
"""
    headline = f"""# 0.4.4-dev 트랙·선택 강조 공개 — {date} 한국 시간

{MARKER}
- 정적 민트/라임/클레이 트랙·홈 색띠·공통/옷장 탭 선택 색면과150ms전환을 반영했다. 같은 보관키/code26,실제 APK출처 `{C}`,{meta['sizeBytes']:,}bytes,SHA256 `{meta['sha256']}`. 서명 CI{run} JVM189/lint0·경고{ci_warning},운영13항목/PNG4/Node{node['tests']}개를 확인했다.
- [게시 검증](docs/{REVIEW}). 최종 binary4 새JVM189/lint0·경고{local_warning},Android일반13/native3=새16과실제Main단일PASS/원본4·원래데이터/소유환경복원·AVD종료를확인했다.43의70/Launcher/사진Picker·gallery·receiver export는이번새결과로합산하지않는다. 휴대폰/Google/SNS/네이버운영·사용자승인·공개서명APK런타임·실제150ms시간/모든중간프레임·다른모션배율은별도다. system0배속설정읽기와그설정의조작은이번관측에포함한다. canonical raw피드는후속병합뒤확인한다.

"""
    continuation = f"""# 최신 이어가기 — 0.4.4-dev/code26 공개, {date}

{MARKER}
[게시 검증]({REVIEW})을 시작점으로 삼는다. 실제 APK출처 `{C}`를 후속 문서/피드 커밋으로 바꾸지 않는다. 같은키 서명 CI{run}·공개3자산·운영 대시보드13항목/PNG4·Node{node['tests']}개를 확인했다. 공유 진입은 기존 ‘나 → 러닝 기록 → 기록 선택 → 공유’다.

최종 binary4의 새 Android일반13/native옷장3=서로다른16,실제Main 단일PASS/원본4(홈/옷장/기본1:1/지도)를 구분한다. 이전43의70/Launcher/사진Picker·IME/갤러리/별도UID export는 이전 이력이며 이번 새 성공이 아니다. 원래DB행/XML/프로필/account·소유환경을 복원하고 정확한AVD를 종료했다. 과거0.4.3 APK출처eb32a30...·0.4.2 176a9cbe...·0.1.5 75c39ca...는 보존한다.

1. 휴대폰 같은키 업데이트 후 기존 기록/아이템,홈/옷장 트랙과 탭 선택·마지막 항목·실제 공유 대상을 확인한다. UI/chooser 이전 관측으로 SNS 성공을 대신하지 않는다.
2. 선택 전환의 중간 프레임·다른 모션 배율/빠른 연속 선택을 별도 실제기기에서 확인한다. 이번 system0배속 실제읽기/조작과 정적 endpoint6조합을 실제150ms시간·모든 프레임 보장으로 부르지 않는다.
3. [시각 강화 우선순위](UI_UX_EXPRESSIVE_PRIORITIES_20261112.md)의P1 완료 기록 구성·공유 카드 테마·옷장 비교 프리뷰를 실제 데이터/준비 원화로 진행한다. P2 다크 테마/연결모션은 별도 전 화면 대비/창/지도/IME 검증 뒤 적용한다.
4. 휴대폰/Google·운영 프로필·닉네임 예약·네이버 연결,알림/위젯·야외GPS/장시간BG·절전/OEM/API31+를 각각 확인한다. 기존 같은 Rules 재게시/키 재발급을 반복 요구하지 않는다.
5. 승인 원본/58ID/13슬롯/9탭·0P/보유권·포인트와 순차heavy·캐시우선/오프라인작업자1·클라우드전용AVD/종료 확인을 유지한다. 완료한 기본트랙/무료/성별/공유를 재구현하지 않는다.

아래는 이전 개발 시점의 보존 기록이다.

"""
    preview = {key: meta[key] for key in ('version', 'versionCode', 'fileName', 'sizeBytes', 'sha256', 'certificateSha256', 'sourceRevision', 'signed')}
    preview.update(package=meta['packageName'], buildType='release', physicalDeviceTests=0, downloadUrl=url,
                   releaseUrl=pub['releaseUrl'], notes='미니미 주변 트랙과 분류 탭 선택 강조를 더한 실기기 검증 전 개발 프리뷰입니다.')
    candidate = R / 'docs/UI_UX_EXPRESSIVE_PRIORITIES_20261112.md'
    existing = [R / path for path in ('AGENTS.md', 'CHANGELOG.md', 'updates/android.json', 'docs/DEVELOPMENT_CONTINUATION.md', 'docs/DEVELOPMENT_PRIORITIES.md')] + [candidate]
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
    history = f'\n- {date} 공개:0.4.4-dev/code26 정적 미니미트랙·홈색띠·분류탭선택강조. {MARKER} 실제 APK출처 `{C}`를 후속 문서/피드로 바꾸지 않는다. [게시 근거](docs/{REVIEW}). 같은키 서명 CI{run} JVM189/lint0·최종binary4새189/Android일반13+native3=새16·실제Main단일PASS/Root원본4·운영13+PNG4를 구분한다. 이전43의70/Launcher/사진Picker·갤러리·receiver export는 이번새결과가 아니다. 원래데이터/환경복원·AVD종료와 과거실패/출처를보존하고 휴대폰/Google/SNS/네이버운영·사용자승인·공개서명APK런타임·실제150ms시간/모든프레임·다른모션배율은별도다. system0설정읽기와조작은이번관측으로구분한다. 새키/Rules/의존성/원화없이기존데이터·원본을유지했다.\n'
    plan[R / 'AGENTS.md'] = agents.replace(anchor, anchor + history, 1).encode()
    plan[candidate] = (f'> {MARKER} 후속 공개 상태: [{date}0.4.4-dev/code26 게시 검증]({REVIEW}). P0 첫 묶음의 실제 APK/운영 갱신은 해당 근거를 따른다. 아래 미게시·검토 중 문구와 초기 빌드 결과는 이전 시점의 보존 이력이며,P1/P2는 별도 계획이다.\n\n'.encode() + originals[candidate])
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
              'freshFinalAndroid': 16, 'distinctPassedMethods': 16, 'earlierResultsReused': False,
              'actualMainOriginals': 4, 'newLauncherExecutionsRequiredOrCounted': 0, 'priorShareExportFlowReexecuted': False,
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
