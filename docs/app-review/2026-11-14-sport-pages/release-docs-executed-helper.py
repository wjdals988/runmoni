#!/usr/bin/env python3
"""Root-only 0.6 docs/feed finalizer, prepared without executing it.

Default: validate actual local/runtime/publication/Production evidence and print
the proposed write hashes. --apply writes only the enumerated public docs/feed.
No Git, network, Android, build, publish or deployment operations exist here.

Required E/final-ui-context.json contains SHA-bound {file, sha256} references:
scopeProof, liveProof, visualProof, widgetProof, widgetVisualProof,
sourceComparison, galleryManifest, sessionCleanup. Missing/contradictory schema
fails closed; a failed parent is never renamed or aliased to a successful one.
Root must review this source and supply real evidence before invoking it.
"""
from pathlib import Path
from zoneinfo import ZoneInfo
import argparse
import datetime as dt
import hashlib
import json
import re
import struct

R = Path('/workspace/runmoni')
W = R / 'work/sport-pages'
E = R / 'docs/app-review/2026-11-14-sport-pages'
B = Path('/workspace/mydashboard')
VERSION, CODE = '0.6.0-dev', 28
OLD_VERSION, OLD_CODE = '0.5.0-dev', 27
OLD_C = 'df09c233f711dc5a0573daf4e030e53af51dddf8'
ART = 'docs/design/avatars/v0.3.0/running-motion-art-source.png'
ART_SHA = 'f9035f29ced0c7ba513dfe24cb727410a7204dc482fc457e806c7a9b3cdbfa65'
MAPS = ('productSources', 'testSources', 'resources', 'rendererAssets', 'approvedSourceCopies', 'configurationSources')
REVIEW = 'RELEASE_REVIEW_0.6.0_20261114.md'
BASE = 'app-review/2026-11-14-sport-pages'
MARKER = '<!-- verified-sport-pages-release-0.6.0-v1 -->'
CANONICAL = 'https://coldbrewventi.vercel.app/projects/runmoni'
CONTEXT_KEYS = ('scopeProof', 'liveProof', 'visualProof', 'widgetProof', 'widgetVisualProof',
                'sourceComparison', 'galleryManifest', 'sessionCleanup')
# Frozen source resolves each class into its actual @Test methods. Input5 keeps
# whole successful groups72, retries only the failed groups3 once, and counts
# native15 separately. The original failed parent remains false and unresolved.
NORMAL_SELECTORS = '''ui.StudioRefreshUiTest
ui.RunmoniDesignTest
ui.AppEntryUiTest
ui.HomeWeatherLayoutTest
ui.CommunityRoomsTest
ui.HierarchyClosetUxTest
ui.CommunityPageNavigationTest
ui.AppInformationTest#aboutShowsInstalledVersionHistoryLinksLicensesAndHonestFailure
ui.AppInformationTest#topVersionOpensConfirmationAndDownloadsOnlyAfterExplicitChoice
ui.AppInformationTest#fullScreenChatKeepsCrewSelectionAndHidesBackgroundControls
ui.AppVersionShortcutLayoutTest
ui.DeviceChecksTest#missingConfigurationAndManualConfirmationStayDistinct
ui.DeviceChecksTest#anonymousRoomDisconnectRequiresExplicitConfirmation
ui.UnifiedProfileUiTest#onlineSaveRequiresMatchingNameUidAndAnAvailableOrOwnedObservation
ui.UnifiedProfileUiTest#failedConnectionKeepsTheNicknameAndWalletAndClosesOnlyForItsConnectionAcknowledgement
ui.ConsumerClosetTest
ui.RunningKitColorsUiTest#namedRadioChoicesWrapWithoutShrinkingAcrossSmallAndLargeTextViewports
ui.RunningKitColorsUiTest#closetColorChangesKeepReadyItemSelectionsInventoryAndGender
ui.ClosetNavigationUiTest
ui.RunShareUiTest
ui.RunShareControllerTest
ui.RunRecordScreenUiTest
ui.RunRecordNavigationUiTest
ui.RouteTileMapUiTest
ui.RunnerDiscoveryUiTest
ui.ModalDismissUiTest
ui.PopupNavigationUiTest
widget.RunWidgetFactoryTest
sharing.RunShareCardRendererTest'''.splitlines()
NATIVE_SELECTORS = '''ui.RunmoniDialogDesignTest
ui.AppEntryNativeUiTest
ui.AppInformationNativeTest
ui.HierarchyClosetLargeTextUxTest
ui.ClosetNavigationNativeUiTest
ui.RunningKitColorsNativeUiTest
ui.RunRecordScreenNativeUiTest
ui.RunShareImeUiTest#realKeyboardKeepsCloseAndScrollableExportActionsOutsideImeAndSystemBars
ui.RunShareImeUiTest#normalTextStoryCaptionStaysVisibleAndAcceptsRealKeysAcrossImeResize
ui.ModalDismissNativeUiTest'''.splitlines()
NORMAL_GROUP_COUNTS = {'screens': 10, 'community': 12, 'account-information': 8, 'closet-consumer': 1,
                       'closet-colors': 2, 'closet-navigation': 2, 'record-share': 11, 'map-modal': 16,
                       'widget-measurement': 7, 'share-png-renderer': 6}
NATIVE_GROUP_COUNTS = {'native-large-text': 12, 'native-share-font1': 1, 'native-form-pages': 2}


def need(ok, message):
    if not ok:
        raise ValueError(message)


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def encoded(value):
    return (json.dumps(value, ensure_ascii=False, indent=2) + '\n').encode()


def read(path):
    return json.loads(Path(path).read_text())


def safe(path, root):
    path = Path(path)
    need(not path.is_symlink() and path.resolve().is_relative_to(root.resolve()), 'Unsafe path: ' + str(path))
    need(not any('private' in part.lower() or part == '.secrets' for part in path.parts), 'Private evidence is not public input')
    return path


def reference(value, parent=E):
    need(isinstance(value, dict) and isinstance(value.get('file'), str) and
         re.fullmatch(r'[0-9a-f]{64}', value.get('sha256', '')) is not None, 'Explicit evidence file/SHA256 required')
    path = Path(value['file'])
    path = safe(path if path.is_absolute() else parent / path, E)
    need(path.is_file() and sha(path) == value['sha256'], 'Evidence changed or missing: ' + str(path))
    return path


def flags(value, names, expected=True):
    for name in names:
        need(value[name] is expected, f'{name} must remain {expected!r}')


def identity(value):
    need((value['version'], value['versionCode']) == (VERSION, CODE), 'Evidence belongs to another release')


def observed_maps(observation, binary):
    need(isinstance(observation, dict) and set(observation) == set(MAPS), 'All six source-map observations required')
    for category in MAPS:
        row = observation[category]
        if row == binary[category]:
            continue
        need(row['matches'] is True and row['mismatches'] == [] and row['fileCount'] == len(binary[category]),
             'Observed source-map mismatch: ' + category)


def pair_and_sources(proof, binary, binary_path, complete=True,
                     source_stages=('sourceMapsBefore', 'sourceMapsAfter', 'sourceMapsAfterRestoration')):
    identity(proof)
    flags(proof, ('completed',))
    if complete:
        flags(proof, ('passed',))
    need(proof['binaryReference'] == binary_path.name and
         proof['binaryReferenceSha256'] == sha(binary_path), 'Runtime must use the exact referenced APK pair')
    need((proof['api'], proof['avd'], proof['acceleration']) == (29, 'Runmoni_Cloud_29', 'TCG'), 'Cloud runtime scope differs')
    need(sorted(row['sha256'] for row in proof['installed']) == sorted(row['sha256'] for row in binary['binaries']),
         'Installed app/test APK pair differs from final build')
    for key in source_stages:
        observed_maps(proof[key], binary)
    flags(proof, ('driverSourceUnchanged', 'observerHelperSourceUnchanged', 'binaryEvidenceUnchanged'))
    for prefix, file_key, hash_key in (('driver', 'executedDriver', 'executedDriverSha256'),
                                      ('observer', 'observerHelper', 'observerHelperSha256')):
        reference({'file': proof[file_key], 'sha256': proof[hash_key]})
    flags(proof, ('physicalDeviceVerified', 'googleLoginVerified', 'productionWritesVerified',
                  'signedReleaseRuntimeVerified', 'android31RuntimeVerified'), False)


def class_methods(binary):
    classes = {}
    for name in binary['testSources']:
        if not name.endswith('.kt') or '/androidTest/' not in name:
            continue
        text = (R / name).read_text()
        package = re.search(r'^package\s+([\w.]+)', text, re.M)
        starts = list(re.finditer(r'^class (\w+)\b', text, re.M))
        for index, match in enumerate(starts):
            body = text[match.start():starts[index + 1].start() if index + 1 < len(starts) else len(text)]
            key = package[1] + '.' + match[1]
            need(key not in classes, 'Duplicate frozen Android test class')
            classes[key] = re.findall(r'@Test\s+fun\s+(\w+)', body)
    return classes


def expand(selectors, classes, short=False):
    methods = []
    for selector in selectors:
        fqcn, separator, method = (('com.runmoni.app.' if short else '') + selector).partition('#')
        need(fqcn in classes and classes[fqcn], 'Unknown/empty frozen test selector: ' + selector)
        chosen = [method] if separator else classes[fqcn]
        need(all(item in classes[fqcn] for item in chosen), 'Unknown frozen test method')
        methods.extend(fqcn + '#' + item for item in chosen)
    need(len(methods) == len(set(methods)), 'Duplicate method in a selected group')
    return set(methods)


def local_and_scope(scope):
    binary_name = scope['finalLocalBinary']
    match = re.fullmatch(r'binary-build([1-9][0-9]*)\.json', binary_name)
    need(match is not None and int(match[1]) >= 5, 'Actual final binary after the input4 product IME fix required')
    binary_path = safe(E / binary_name, E)
    binary = read(binary_path)
    identity(binary)
    flags(binary, ('buildSucceeded', 'jvmReportsFresh', 'actualInputsBoundByFullSourceMaps'))
    need(binary['sourceFreezeReference'] == f'source-freeze{match[1]}.json' and
         binary['signing'] == 'local TEST debug, not retained release signing', 'Local build/source-freeze/signing boundary differs')
    frozen = read(safe(E / binary['sourceFreezeReference'], E))
    identity(frozen)
    need(frozen['sourceMaps'] == {key: binary[key] for key in MAPS}, 'Source freeze and final binary inputs differ')
    for category in MAPS:
        need(isinstance(binary[category], dict) and binary[category], 'Empty frozen source map')
        for name, expected in binary[category].items():
            need(not Path(name).is_absolute() and '..' not in Path(name).parts and sha(safe(R / name, R)) == expected,
                 'Current source differs from executed input: ' + name)
    need(binary['approvedSourceCopies'][ART] == ART_SHA and sha(R / ART) == ART_SHA, 'Approved artwork changed')
    policy = 'app/src/main/java/com/runmoni/app/ui/RunmoniWindowPolicy.kt'
    need(policy in binary['productSources'], 'Final product input must include the shared API29 editable-page window fix')
    for consumer in ('RunmoniModal.kt', 'RunShareScreen.kt'):
        source = (R / 'app/src/main/java/com/runmoni/app/ui' / consumer).read_text()
        need('legacyEditablePageWindowPolicy()' in source and 'WindowInsets(0, 0, 0, 0)' in source,
             'Form/share must consume the shared fitted window policy without a second inset subtraction')
    jvm = binary['jvm']
    need(type(jvm['tests']) is int and jvm['tests'] > 0 and all(jvm[key] == 0 for key in ('failures', 'errors', 'skipped')),
         'Fresh local JVM results incomplete')
    need(binary['lint']['Error'] == binary['lint']['Fatal'] == 0, 'Final local lint failed')
    log = safe(E / binary['buildLog'], E).read_text()
    need('BUILD SUCCESSFUL' in log and re.search(r'(?m)^> Task :app:testDebugUnitTest\s*$', log) is not None,
         'Require actual fresh JVM task, not a cached result')
    for attempt, task in ((1, ':app:compileDebugKotlin'), (2, ':app:lintDebug')):
        failure = read(E / f'build{attempt}-failure.json')
        identity(failure)
        need(failure['buildSucceeded'] is False and failure['failedTask'] == task and
             failure['sourceFreezeReference'] == f'source-freeze{attempt}.json', 'Preserve the actual earlier failed build')
        raw = (E / f'build{attempt}-failed.txt').read_text()
        need('BUILD FAILED' in raw and task in raw, 'Earlier build failure raw missing')
    identity(scope)
    need(scope['distinctPassedMethods'] == scope['freshFinalAndroid']['total'] == 90 and
         scope['earlierInputResultsReused'] is False and scope['sameInputPassedGroupsRetained'] is True and
         scope['singleSuccessfulDefaultPhaseClaimed'] is False and scope['finalLocalJvm'] == binary['jvm'] and
         scope['finalLocalLint'] == binary['lint'] and scope['retryLimit'] == 1 and
         scope['unresolvedIntermittentSnapshotObserverFailure'] is True and scope['failureCauseConfirmed'] is False,
         'Same-input partition must preserve the failed parent/unknown cause and one retry limit')
    flags(scope, ('physicalDeviceVerified', 'googleLoginVerified', 'signedReleaseRuntimeVerified',
                  'socialAppDeliveryVerified', 'productionWritesVerified'), False)
    classes = class_methods(binary)
    normal, native = (expand(selectors, classes, True) for selectors in (NORMAL_SELECTORS, NATIVE_SELECTORS))
    need(len(normal) == 75 and len(native) == 15 and not normal & native, 'Reviewed frozen 75+15 selector coverage changed')
    retry_methods = expand(('ui.ConsumerClosetTest',
        'ui.RunningKitColorsUiTest#namedRadioChoicesWrapWithoutShrinkingAcrossSmallAndLargeTextViewports',
        'ui.RunningKitColorsUiTest#closetColorChangesKeepReadyItemSelectionsInventoryAndGender'), classes, True)
    need(len(retry_methods) == 3 and retry_methods <= normal, 'Only the exact failed3 methods may be retried/countable')
    retry_counts = {key: NORMAL_GROUP_COUNTS[key] for key in ('closet-consumer', 'closet-colors')}
    specs = {'android-review2.json': (NORMAL_GROUP_COUNTS, set(NORMAL_GROUP_COUNTS) - set(retry_counts), normal - retry_methods, 72, False, 'default', 75),
             'android-review3.json': (retry_counts, set(retry_counts), retry_methods, 3, True, 'default', 3),
             'native3.json': (NATIVE_GROUP_COUNTS, set(NATIVE_GROUP_COUNTS), native, 15, True, 'native', 15)}
    refs, included_rows = scope['proofReferences'], scope['includedGroups']
    need(isinstance(included_rows, list) and included_rows and
         all(isinstance(row, dict) and set(row) == {'proof', 'group'} for row in included_rows),
         'Explicit includedGroups proof/group rows required')
    need(len({(row['proof'], row['group']) for row in included_rows}) == len(included_rows), 'Duplicate counted group reference')
    included = {name: [row['group'] for row in included_rows if row['proof'] == name] for name in refs}
    need(set(refs) == set(specs) == {row['proof'] for row in included_rows} == set(scope['freshFinalAndroid']['proofs']),
         'Exactly ar2 retained groups/ar3 single retry/native3 whole suite with SHA-bound proofs required')
    seen, totals, proofs, excluded = set(), {}, {}, []
    for name, ref in refs.items():
        path = reference(ref)
        need(path.name == name, 'Proof map name/reference differ')
        proof = read(path)
        proofs[name] = proof
        counts, counted_names, expected_methods, expected_total, parent_passed, phase, planned = specs[name]
        pair_and_sources(proof, binary, binary_path, complete=parent_passed)
        need(proof['passed'] is parent_passed and proof['reviewPhase'] == phase and proof['plannedProductMethods'] == planned,
             'Preserve actual parent status/phase/planned method count')
        flags(proof, ('preferencesRestoredExactly', 'profileRestoredExactly', 'communityAccountPreferencesRestoredExactly',
                      'controlledSettingsPermissionsPowerRestoredExactly', 'motionScaleZeroObserved'))
        names = included[name]
        need(isinstance(names, list) and names and len(names) == len(set(names)), 'Unique counted group names required')
        groups = proof['groups']
        need(len({g['name'] for g in groups}) == len(groups) and set(names) == counted_names and
             {group['name']: group['expected'] for group in groups} == counts, 'Exact included/excluded partition group names/counts required')
        total, selected_suite = 0, set()
        for group in groups:
            raw_path = reference({'file': group['raw'], 'sha256': group['rawSha256']})
            raw = raw_path.read_text()
            count = group['expected']
            if group['name'] not in counted_names:
                need(name == 'android-review2.json' and group['name'] in retry_counts and group['passed'] is False and
                     'Detected multithreaded access to SnapshotStateObserver' in raw and 'name=main' in raw,
                     'Excluded original failures must retain their actual SnapshotStateObserver error')
                methods = expand(group['classes'], classes)
                need(len(methods) == count and methods <= retry_methods, 'Failed-group selector must match only its future retry methods')
                if group['name'] == 'closet-consumer':
                    need('FAILURES!!!' in raw and 'Tests run: 1,  Failures: 1' in raw, 'Preserve consumer1 original whole-group failure')
                else:
                    need('Process crashed' in raw, 'Preserve original colors process crash')
                excluded.append({'proof': name, 'group': group['name'], 'raw': group['raw'],
                                 'rawSha256': group['rawSha256'], 'originalParentPassed': False})
                continue
            need(type(count) is int and count > 0 and group['passed'] is True and group['actual'] == count and
                 re.search(r'OK \(' + str(count) + r' tests?\)', raw) is not None and
                 not any(token in raw for token in ('FAILURES!!!', 'INSTRUMENTATION_FAILED', 'Process crashed')) and
                 re.search(r'INSTRUMENTATION_STATUS_CODE:\s*-[34]\b', raw) is None, 'Counted group raw is not wholly successful')
            methods = expand(group['classes'], classes)
            need(len(methods) == count and not seen & methods, 'Counted frozen method duplicate/count mismatch')
            if methods <= native:
                viewport = group['viewport']
                font1 = methods == {'com.runmoni.app.ui.RunShareImeUiTest#normalTextStoryCaptionStaysVisibleAndAcceptsRealKeysAcrossImeResize'}
                need(viewport['native320Font2RequiredByClass'] is (not font1) and
                     float(viewport['fontScale']) == (1.0 if font1 else 2.0), 'Native target suite actual OS/font condition differs')
            seen.update(methods)
            selected_suite.update(methods)
            total += count
        need(proof['actualPassedProductMethods'] == total == expected_total and selected_suite == expected_methods,
             'Actual successful whole-group selector set/partition count differs')
        totals[name] = total
    need(scope['countedExecutionsByProof'] == totals and sum(totals.values()) == 90 and seen == normal | native and
         scope['passedMethodIds'] == sorted(seen), 'Raw/frozen selector union does not match the actual90-method scope')
    need(scope['excludedFailedGroups'] == excluded and len(excluded) == 2 and
         {row['group'] for row in excluded} == set(retry_counts) and
         totals == {'android-review2.json': 72, 'android-review3.json': 3, 'native3.json': 15},
         'Final scope must be the exact72+3+15 partition with both original failed groups preserved')
    input5_diagnostics(scope, proofs, binary_path, binary['sourceFreezeReference'])
    # Original input3 is not a source of final passed methods. Read its immutable
    # failure and completed restoration separately; never change its false parent.
    old_path, old_binary_path = E / 'android-review1.json', E / 'binary-build3.json'
    old, old_binary = read(old_path), read(old_binary_path)
    pair_and_sources(old, old_binary, old_binary_path, complete=False)
    need(old['passed'] is False, 'Preserve the actual input3 failed default parent')
    flags(old, ('preferencesRestoredExactly', 'profileRestoredExactly', 'communityAccountPreferencesRestoredExactly',
                'controlledSettingsPermissionsPowerRestoredExactly'))
    failures = [g for g in old['groups'] if g['name'] == 'community']
    need(len(failures) == 1 and failures[0]['passed'] is False, 'Preserve input3 community fixture failure')
    failed_raw = reference({'file': failures[0]['raw'], 'sha256': failures[0]['rawSha256']})
    need('The fixture must exercise a scrolled party page' in failed_raw.read_text() and
         'Tests run: 12,  Failures: 1' in failed_raw.read_text(), 'Original fixture-precondition failure raw changed')
    diagnostic = read(E / 'community-scroll-precondition.json')
    need(diagnostic['binaryReference'] == 'binary-build3.json' and diagnostic['testsRun'] == 12 and diagnostic['failures'] == 1 and
         diagnostic['actualChatBackPathReached'] is False and diagnostic['productNavigationFailureEstablished'] is False and
         diagnostic['sourceOrAssertionsChanged'] is False and diagnostic['recheckExecuted'] is False and
         reference(diagnostic['raw']) == failed_raw, 'Historical precondition diagnosis must remain distinct from the new complete suites')
    input3_diagnostics(old, old_binary, old_binary_path)
    input4_diagnostics()
    return binary, binary_path, proofs, totals


def input3_diagnostics(normal, binary, binary_path):
    """Keep both failed input3 parents/restoration/shutdown outside final90."""
    native = read(E / 'native1.json')
    pair_and_sources(native, binary, binary_path, complete=False)
    need(native['passed'] is False, 'Input3 native parent must remain failed')
    flags(native, ('preferencesRestoredExactly', 'profileRestoredExactly', 'communityAccountPreferencesRestoredExactly',
                   'controlledSettingsPermissionsPowerRestoredExactly'))
    for parent, count in ((normal, 63), (native, 13)):
        need(parent['actualPassedProductMethods'] == count, 'Historical whole-group count changed; do not count partial failed-group passes')
        actual = 0
        for group in parent['groups']:
            raw = reference({'file': group['raw'], 'sha256': group['rawSha256']}).read_text()
            if group['passed'] is True:
                n = group['expected']
                need(group['actual'] == n and re.search(r'OK \(' + str(n) + r' tests?\)', raw) is not None and
                     not any(value in raw for value in ('FAILURES!!!', 'Process crashed', 'INSTRUMENTATION_FAILED')),
                     'Historical successful whole-group raw changed')
                actual += n
        need(actual == count, 'Historical partial passes must stay outside the counted whole-group total')
    failed = [group for group in native['groups'] if group['name'] == 'native-form-pages']
    need(len(failed) == 1 and failed[0]['passed'] is False and failed[0]['expected'] == 2 and failed[0]['actual'] is None,
         'Preserve the original native form failed-group observation, not a manufactured OK count')
    raw = reference({'file': failed[0]['raw'], 'sha256': failed[0]['rawSha256']}).read_text()
    need(all(value in raw for value in ('imeKeepsCloseAndSubmitReachableWithoutSubmittingOnDismiss',
         'Full modal_native_submit stays outside actual OS/IME bounds', 'FAILURES!!!', 'Tests run: 2,  Failures: 1')),
         'Preserve the actual OS/IME bounds assertion failure')
    need('modal_native_submit host=' not in raw, 'Review newly supplied geometry explicitly rather than assume the original missing values')
    cleanup_path = E / 'session-cleanup-input3.json'
    cleanup = read(cleanup_path)
    flags(cleanup, ('ownedEmulatorNoLongerRunning', 'noUnrelatedProcessTerminated'))
    ownership = read(reference(cleanup['ownership']))
    need(cleanup['ownedPid'] == ownership['pid'] == 47376 and
         cleanup['ownedAvd'] == ownership['avd'] == 'Runmoni_Cloud_29' and
         cleanup['serial'] == ownership['serial'] == 'emulator-5556' and
         re.fullmatch(r'[0-9a-f]{64}', cleanup['ownerCommandSha256']) is not None and
         cleanup['signal'] == 'SIGTERM to verified exact owned PID' and cleanup['finalProcessState'] in ('Z', 'absent') and
         cleanup['physicalDeviceOrUserPcEmulatorUsed'] is False, 'Input3 exact owned-PID shutdown evidence differs')
    old = {'android-review1.json': normal, 'native1.json': native}
    rows = cleanup['restorationProofs']
    need(len(rows) == 2 and {reference(row).name for row in rows} == set(old), 'Both failed input3 parents require exact restoration provenance')
    for row in rows:
        need(row['completed'] is True and row['originalParentPassed'] is False and
             read(reference(row)) == old[reference(row).name], 'Never rewrite either input3 failed parent in cleanup')
    diagnostic = read(E / 'input3-diagnostic-review.json')
    identity(diagnostic)
    need(diagnostic['binaryReference'] == binary_path.name and diagnostic['normalParentPassed'] is False and
         diagnostic['nativeParentPassed'] is False and diagnostic['normalCountedPassedMethods'] == 63 and
         diagnostic['nativeCountedPassedMethods'] == 13, 'Historical diagnostic parent statuses/counts differ')
    flags(diagnostic, ('notFinalPassedScope', 'allScopedRestorationsVerified', 'earlierInputPassesWillNotSupplyFinal90'))
    refs = diagnostic['proofReferences']
    need(len(refs) == 3 and {reference(row) for row in refs} == {E / 'android-review1.json', E / 'native1.json', cleanup_path},
         'Input3 diagnostic must bind both original failed proofs and exact shutdown bytes')
    failures = diagnostic['failures']
    need(len(failures) == 2 and {(row['proof'], row['group']) for row in failures} ==
         {('android-review1.json', 'community'), ('native1.json', 'native-form-pages')}, 'Input3 diagnostic failure scope differs')
    native_failure = next(row for row in failures if row['proof'] == 'native1.json')
    need(native_failure['failedMethod'] == 'imeKeepsCloseAndSubmitReachableWithoutSubmittingOnDismiss' and
         native_failure['newDiagnosticsPlanned'] is True and 'not logged before the assertion' in native_failure['observation'] and
         'not yet established' in native_failure['observation'], 'Missing original geometry/uncertain cause must not become a product-overlap conclusion')


def input4_diagnostics():
    """Preserve interrupted input4 and its measured product coverage separately."""
    path, binary_path = E / 'native2.json', E / 'binary-build4.json'
    proof, binary = read(path), read(binary_path)
    pair_and_sources(proof, binary, binary_path, complete=False,
                     source_stages=('sourceMapsBefore', 'sourceMapsAfterRestoration'))
    need(proof['passed'] is False and proof['failureType'] == 'KeyboardInterrupt' and
         proof['actualPassedProductMethods'] == 0 and 'sourceMapsAfter' not in proof,
         'Input4 interrupted parent must stay false/zero without an invented all-groups source observation')
    flags(proof, ('preferencesRestoredExactly', 'profileRestoredExactly', 'communityAccountPreferencesRestoredExactly',
                  'controlledSettingsPermissionsPowerRestoredExactly'))
    failed = [g for g in proof['groups'] if g['name'] == 'native-form-pages']
    need(len(failed) == 1 and failed[0]['expected'] == 2 and failed[0]['passed'] is False and failed[0]['actual'] is None,
         'Input4 failed native form group must not become a successful partial count')
    group = failed[0]
    raw_path = reference({'file': group['raw'], 'sha256': group['rawSha256']})
    need(all(value in raw_path.read_text() for value in ('Full modal_native_submit stays outside actual OS/IME bounds',
         'imeKeepsCloseAndSubmitReachableWithoutSubmittingOnDismiss', 'FAILURES!!!', 'Tests run: 2,  Failures: 1')),
         'Measured input4 OS/IME assertion failure raw changed')
    geometry_path = reference({'file': group['pageWindowGeometry'], 'sha256': group['pageWindowGeometrySha256']})
    rows = [json.loads(line) for line in geometry_path.read_text().splitlines() if line.strip()]
    submits = [row for row in rows if row['tag'] == 'modal_native_submit']
    need(len(submits) == 1, 'One original pre-assertion submit geometry observation required')
    row, action = submits[0], submits[0]['action']
    need(row['sourceKind'] == 'Native ComponentActivity fixture geometry; no account or content text' and
         row['host'] == 'PAGE' and row['imeVisible'] is True and row['requireIme'] is True and
         row['activitySoftInputMode'] == 16 and row['fontScale'] == 2 and row['screenWidthDp'] == 320 and
         action['semanticPositionInWindow'] == action['layoutPositionInWindow'] == 'Offset(340.0, 680.0)' and
         action['semanticSize'] == action['layoutSize'] == '110 x 84' and
         row['visibleDisplayFrame'] == 'Rect(0, 36 - 480, 413)' and row['ime'] == 'Insets{left=0, top=0, right=0, bottom=441}',
         'Actual semantic/layout/OS IME geometry must agree; do not rename it a coordinate-only issue')
    bottom, safe_bottom = row['originalOsEdges']['bottom'], row['originalSafeEdges']['bottom']
    need((bottom['actualPx'], bottom['limitPx'], bottom['inwardMarginPx'], bottom['allowedTolerancePx'], bottom['passesOriginalInequality']) ==
         (764, 413, -351, .5, False) and
         (safe_bottom['actualPx'], safe_bottom['limitPx'], safe_bottom['passesOriginalInequality']) == (764, 782, True),
         'Actual input4 product layout kept its footer below the visible IME window')
    stop_path, cleanup_path = E / 'input4-stop-reason.json', E / 'session-cleanup-input4.json'
    stop = read(stop_path)
    need(stop['signalToExactCollector'] == 'SIGINT' and type(stop['exactCollectorPid']) is int and stop['exactCollectorPid'] > 1 and
         stop['sourceMutationBeforeRestoration'] is False and stop['notAProductPass'] is True and reference(stop['raw']) == raw_path and
         stop['actualSaveBottomPx'] == 764 and stop['actualOsImeSafeBottomPx'] == 413, 'Preserve exact collector stop before product mutation')
    flags(stop, ('semanticAndLayoutActionCoordinatesAgree', 'fixtureAdjustResizeExplicit', 'rootPrivateFailureScreenshotActuallyViewed'))
    cleanup = read(cleanup_path)
    flags(cleanup, ('ownedEmulatorNoLongerRunning', 'noUnrelatedProcessTerminated'))
    owner = read(reference(cleanup['ownership']))
    need(cleanup['ownedPid'] == owner['pid'] == 57956 and cleanup['ownedAvd'] == owner['avd'] == 'Runmoni_Cloud_29' and
         cleanup['serial'] == owner['serial'] == 'emulator-5556' and
         re.fullmatch(r'[0-9a-f]{64}', cleanup['ownerCommandSha256']) is not None and
         cleanup['signal'] == 'SIGTERM to verified exact owned PID' and cleanup['finalProcessState'] in ('Z', 'absent') and
         cleanup['physicalDeviceOrUserPcEmulatorUsed'] is False, 'Input4 exact owned-PID shutdown evidence differs')
    restorations = cleanup['restorationProofs']
    need(len(restorations) == 1 and reference(restorations[0]) == path and restorations[0]['completed'] is True and
         restorations[0]['originalParentPassed'] is False, 'Input4 cleanup must retain the interrupted native parent as false')
    diagnosis = read(E / 'input4-ime-diagnosis.json')
    identity(diagnosis)
    need(diagnosis['binaryReference'] == binary_path.name and diagnosis['nativeParentPassed'] is False and
         diagnosis['actualSubmitBottomPx'] == 764 and diagnosis['actualOsImeSafeBottomPx'] == 413 and
         diagnosis['actualBottomOcclusionPx'] == 764 - 413 == 351 and
         diagnosis['screenshotPublished'] is False and diagnosis['assertionsOrTimeoutRelaxed'] is False,
         'Input4 measured product defect/unchanged assertion diagnosis differs')
    flags(diagnosis, ('rootStoppedCollectorAfterCriticalFailure', 'notFinalPassedScope', 'sameThreadSemanticAndLayoutCoordinatesAgree',
                     'privateFailureScreenshotActuallyViewed', 'screenshotWasSyntheticInputFixtureNotMainActivity'))
    refs = diagnosis['proofReferences']
    need(len(refs) == 5 and {reference(ref) for ref in refs} == {path, raw_path, geometry_path, stop_path, cleanup_path},
         'Input4 diagnosis must bind all actual raw/geometry/stop/restoration evidence')


def input5_diagnostics(scope, proofs, binary_path, freeze_name):
    """No passing retry can erase or explain the original thread failure."""
    diagnosis = read(E / 'input5-thread-diagnosis.json')
    identity(diagnosis)
    need(diagnosis['binaryReference'] == binary_path.name == 'binary-build5.json' and
         diagnosis['binaryReferenceSha256'] == sha(binary_path) and
         diagnosis['sourceFreezeReference'] == freeze_name == 'source-freeze5.json', 'Thread diagnosis must bind the actual same input5 pair/freeze')
    flags(diagnosis, ('originalParentPassed', 'causeConfirmed', 'productFixClaimed', 'singleSuccessfulDefaultPhaseClaimed',
                      'earlierInputResultsReused'), False)
    flags(diagnosis, ('targetedRetryObservedPassed', 'sameInputPassedGroupsRetained'))
    need(diagnosis['targetedRetryLimit'] == 1 and diagnosis['proofReferences'] == scope['proofReferences'] and
         diagnosis['failedGroupNames'] == ['closet-consumer', 'closet-colors'], 'Exactly one actually passed retry of only the original failed groups required')
    for name, ref in diagnosis['proofReferences'].items():
        need(read(reference(ref)) == proofs[name], 'Thread diagnosis reference differs from the actual counted proof')
    observations = diagnosis['observations']
    need(isinstance(observations, list) and observations and all(isinstance(value, str) and value.strip() for value in observations),
         'Actual thread observations required; do not invent a capture/product cause')


def png_rows(proof):
    rows = proof['captures']
    need(len({row['file'] for row in rows}) == len(rows) == len({row['sha256'] for row in rows}), 'Duplicate original capture')
    for row in rows:
        path = reference(row)
        raw = path.read_bytes()
        need(raw[:8] == b'\x89PNG\r\n\x1a\n' and raw[12:16] == b'IHDR' and len(raw) == row['bytes'] and
             struct.unpack('>II', raw[16:24]) == (row['width'], row['height']) and min(row['width'], row['height']) > 0,
             'Original PNG bytes/dimensions differ')
    return rows


def root_visual(visual, rows, proof_path, source):
    flags(visual, ('actuallyViewedWithViewImage', 'privacyReviewed'))
    flags(visual, ('userVisualApproval',), False)
    need(reference(visual['proof']) == proof_path, 'Root visual inspection must bind the actual capture proof')
    need(len(visual['images']) == len(rows), 'Root must view every original, not only the selected gallery')
    need({(r['file'], r['sha256']) for r in rows} == {(r['file'], r['sha256']) for r in visual['images']}, 'Root visual set differs')
    for row in visual['images']:
        reference(row)
        flags(row, ('actuallyViewedWithViewImage', 'privacyReviewed'))
        need(row['source'] == source, 'Root image source label differs from actual collector')


def failed_main1_observation(paths, binary, binary_path):
    failed_path = E / 'live1.json'
    failed = read(failed_path)
    pair_and_sources(failed, binary, binary_path, complete=False,
                     source_stages=('sourceMapsBefore', 'sourceMapsAfterRestoration'))
    need(failed['name'] == 'live1' and failed['passed'] is False and failed['failureType'] == 'AssertionError' and
         failed['failure'] == 'Actual control not reachable: 코랄' and 'sourceMapsAfter' not in failed and
         'actualCoralThemeRestoredAfterBack' not in failed, 'Main1 observer failure/unchecked assertion must retain its original scope')
    flags(failed, ('nonceFixtureCleanupSucceeded', 'ownGalleryOutputsRemoved', 'ownShareCacheOutputsRemoved',
                   'ownReceiverOutputsRemoved', 'databaseExactlyRestored', 'preferencesRestoredExactly', 'profileRestoredExactly',
                   'accountPreferencesRestoredExactly', 'controlledEnvironmentExactlyRestored', 'environmentHelperSourceUnchanged'))
    reference({'file': failed['environmentHelper'], 'sha256': failed['environmentHelperSha256']})
    partial = png_rows(failed)
    suffixes = ('studio-home', 'profile-ime', 'studio-closet', 'record-result', 'default-card', 'coral-card')
    need(len(partial) == 6 and {row['file'] for row in partial} == {'live1-main-' + key + '.png' for key in suffixes},
         'Preserve exactly six original partial Main1 captures, never turn them into a complete Main review')
    for row in partial:
        need(row['kind'] == 'main' and row['sourceKind'] == 'actual Android MainActivity' and
             row['expectedWindowFocused'] is True and row['syntheticGps'] is False, 'Partial Main1 capture provenance differs')
    visual_path = E / 'root-main1-partial-review.json'
    visual = read(visual_path)
    root_visual(visual, partial, failed_path, 'MainActivity')
    need(visual['originalParentPassed'] is False and visual['notFinalMainReview'] is True, 'Partial pixel review must preserve failed parent/not-final status')
    diagnosis = read(E / 'main1-observer-diagnosis.json')
    need(diagnosis['binaryReference'] == binary_path.name and diagnosis['binaryReferenceSha256'] == sha(binary_path) and
         diagnosis['originalFailure'] == failed['failure'] and diagnosis['preservedPartialCaptureCount'] == 6 and
         reference(diagnosis['failedProof']) == failed_path and reference(diagnosis['finalMainProof']) == paths['liveProof'],
         'Main observer diagnosis must bind the actual failed6 and completely new final capture proof')
    flags(diagnosis, ('originalParentPassed', 'productFixClaimed', 'assertionsOrTimeoutRelaxed', 'reusedFailedMainCaptures'), False)
    need(isinstance(diagnosis['observations'], list) and diagnosis['observations'] and
         all(isinstance(value, str) and value.strip() for value in diagnosis['observations']), 'Actual observer diagnosis observations required')
    if 'partialRootVisualProof' in diagnosis:
        need(reference(diagnosis['partialRootVisualProof']) == visual_path, 'Optional partial Root visual reference differs')


def widget1_interruption_history(binary, binary_path, final_widget):
    """Recovery success restores state; it is not a widget/runtime test pass."""
    original_path, recovery_path = E / 'widget1.json', E / 'widget1-interruption-recovery.json'
    original, recovery = read(original_path), read(recovery_path)
    identity(original)
    need(original['name'] == 'widget1' and original['completed'] is False and original['passed'] is False and
         original['stage'] == 'guards' and original['captures'] == [] and original['launcherWidgetLifecycles'] == [] and
         original['binaryReference'] == binary_path.name and original['requiredBinaryEvidenceSha256'] == sha(binary_path),
         'Interrupted original widget1 must remain incomplete with no runtime capture/lifecycle success')
    for file_key, hash_key in (('executedDriver', 'executedDriverSha256'), ('observerHelper', 'observerHelperSha256'),
                               ('environmentHelper', 'environmentHelperSha256')):
        reference({'file': original[file_key], 'sha256': original[hash_key]})
    identity(recovery)
    flags(recovery, ('completed', 'passed', 'restorationOnly', 'originalAttemptNotRewritten', 'binaryEvidenceUnchanged',
                     'preferencesRestoredExactly', 'profileRestoredExactly', 'widgetCacheExactlyRestored',
                     'widgetCacheExistsExactlyRestored', 'databaseExactlyRestored', 'accountPreferencesRestoredExactly',
                     'controlledEnvironmentExactlyRestored', 'launcherWidgetIdsExactlyRestored'))
    flags(recovery, ('widgetRuntimeVerified', 'originalAttemptCompleted', 'originalAttemptPassed', 'newWidgetsRemoved',
                     'unrelatedProcessTerminated', 'databaseCopiedBack', 'preferencesAlreadyExactBeforeRecovery',
                     'widgetCacheAlreadyExactBeforeRecovery', 'controlledEnvironmentAlreadyExactBeforeRecovery'), False)
    need(recovery['name'] == 'widget1-interruption-recovery' and recovery['kind'] == 'owned interruption recovery only' and
         recovery['captures'] == [] and recovery['productTestsCounted'] == 0 and recovery['restorationCommandFailures'] == [] and
         recovery['binaryReference'] == binary_path.name and recovery['binaryReferenceSha256'] == sha(binary_path) and
         reference(recovery['originalAttempt']) == original_path, 'Restoration-only recovery must bind unchanged original proof and final binary metadata')
    for stage in ('sourceMapsBefore', 'sourceMapsAfterRestoration'):
        observed_maps(recovery[stage], binary)
    backups = recovery['backupReferences']
    expected = {'widget1-private-environment-before.json', 'widget1-private-preferences-before.xml',
                'widget1-private-widget-cache-before.xml', 'widget1-private-widget-ids-before.json'}
    need(len(backups) == 4 and {row['file'] for row in backups} == expected and
         all(row['privateWorkspaceBackup'] is True and re.fullmatch(r'[0-9a-f]{64}', row['sha256']) is not None for row in backups),
         'Four opaque private-backup names/hashes required; never copy their contents to public evidence')
    assessment = read(E / 'widget1-interruption-assessment.json')
    flags(assessment, ('environmentReadinessWaitReturnedReady', 'originalDriverProcessNoLongerRunning',
                       'recoveryOnlyNotWidgetRuntimeSuccess', 'originalIncompleteProofNotRewritten'))
    need(assessment['productTestsCounted'] == 0 and reference(assessment['originalAttempt']) == original_path and
         reference(assessment['recoveryProof']) == recovery_path, 'Interruption assessment must bind original incomplete bytes and exact recovery report')
    helper = reference(assessment['recoveryHelper'])
    need(helper.name == 'widget1-recovery-executed-helper.py', 'Actual archived recovery helper required; do not invent a startup source-unchanged claim')
    finished = dt.datetime.fromisoformat(recovery['finishedAtUtc'].replace('Z', '+00:00'))
    started = dt.datetime.fromisoformat(final_widget['startedAtUtc'].replace('Z', '+00:00'))
    need(finished.tzinfo is not None and started.tzinfo is not None and finished <= started,
         'Fresh widget2 runtime review must start after the actual completed scoped recovery')


def main_and_widget(paths, binary, binary_path):
    live, widget = (read(paths[key]) for key in ('liveProof', 'widgetProof'))
    for proof in (live, widget):
        pair_and_sources(proof, binary, binary_path)
        flags(proof, ('databaseExactlyRestored', 'preferencesRestoredExactly', 'profileRestoredExactly',
                      'controlledEnvironmentExactlyRestored', 'environmentHelperSourceUnchanged'))
        reference({'file': proof['environmentHelper'], 'sha256': proof['environmentHelperSha256']})
    need(paths['liveProof'].name == 'live2.json' and live['name'] == 'live2' and live['sourceKind'] == 'actual Android MainActivity',
         'Final Main must be the new live2 review, never the failed Main1 partial captures')
    flags(live, ('nonceFixtureCleanupSucceeded', 'ownGalleryOutputsRemoved', 'ownShareCacheOutputsRemoved',
                 'ownReceiverOutputsRemoved', 'accountPreferencesRestoredExactly', 'actualClosetTabSelectedObserved',
                 'actualCoralThemeSelectionObserved', 'resultReturnedAfterEditorBack', 'existingShareToolTabsTapped',
                 'originalHistoryExcludedFromExtraScreenshots', 'motionScaleZeroObservedInActualMain',
                 'actualProfileKeyboardObserved', 'actualProfileKeyboardControlsInsideVisibleWindow',
                 'actualProfileClosedWithoutSaving', 'actualCoralThemeRestoredAfterBack'))
    coral = live['actualReopenedCoralRadioAccessibility']
    need(coral['label'] == '코랄' and coral['checkable'] == coral['checked'] == 'true' and
         coral['clickable'] in ('true', 'false') and coral['observationDoesNotRequireRepeatClick'] is True and
         sum(row['label'] == '코랄' for row in live['actualTaps']) == 1,
         'Back/reopen must freshly observe checked Coral without a second tap; clickability is recorded, not assumed')
    visible, controls = live['actualProfileKeyboardWindowVisibleFrame'], live['actualProfileKeyboardControlBounds']
    density, minimum = live['actualProfileKeyboardDensityDpi'], live['actualProfileKeyboardMinimum48dpTargetPx']
    need(type(density) is int and density > 0 and type(minimum) in (int, float) and minimum > 0 and
         abs(minimum - 48 * density / 160) < .000001, 'Actual Main density and48dp pixel target must agree')
    need(isinstance(visible, list) and len(visible) == 4 and all(type(n) is int for n in visible) and
         0 <= visible[0] < visible[2] and 0 <= visible[1] < visible[3] and
         isinstance(controls, dict) and set(controls) == {'back', 'save'}, 'Actual Main IME visible frame and Back/Save rectangles required')
    for rect in controls.values():
        need(isinstance(rect, list) and len(rect) == 4 and all(type(n) is int for n in rect) and
             rect[2] - rect[0] + 1 >= minimum and rect[3] - rect[1] + 1 >= minimum and
             visible[0] <= rect[0] < rect[2] <= visible[2] and visible[1] <= rect[1] < rect[3] <= visible[3],
             'Actual Main profile Back/Save must be wholly inside the measured visible IME window')
    flags(live, ('GPSInjectedByThisDriver', 'priorShareExportFlowReexecuted',
                 'mapProviderChangedOrNewProviderAuthenticated', 'mapHttpStatusAndTileContentMeasuredByDriver'), False)
    # This limited collector does not enter its optional Picker path. Its
    # action keys are created only when that path is called; absent keys are
    # not manufactured false-valued observations or used to claim success.
    need('actualSystemPhotoPickerUsed' not in live and 'systemPhotoPickerRequestedByThisAttempt' not in live,
         'Limited Main scope unexpectedly entered the system photo Picker')
    need(live['fixtureTrackPointCount'] == 0 and '1:1' in live['defaultPreviewDescription'] and
         live['previewAfterActualReopen'] == live['defaultPreviewDescription'], 'Actual default square/Back/reopen observation differs')
    main = png_rows(live)
    suffixes = ('profile-ime', 'studio-closet', 'record-result', 'default-card', 'coral-card', 'map-clean')
    omitted = live.get('homeCaptureOmittedForOriginalHistoryPrivacy')
    if omitted is True:
        need(len(main) == 6, 'Privacy-omitted home must leave exactly six originals')
    else:
        need(omitted is None and live['homeCaptureContainsOnlyNonceReviewRun'] is True and len(main) == 7,
             'Actual home capture/privacy observation missing')
        suffixes += ('studio-home',)
    need({row['file'] for row in main} == {live['name'] + '-main-' + key + '.png' for key in suffixes}, 'Actual Main capture scope differs')
    profile_capture = next(row for row in main if row['file'] == live['name'] + '-main-profile-ime.png')
    need(visible[2] <= profile_capture['width'] and visible[3] < profile_capture['height'],
         'Actual profile visible frame must fit the full original screenshot with IME space below it')
    rect = coral['bounds']
    need(isinstance(rect, list) and len(rect) == 4 and all(type(n) is int for n in rect) and
         0 <= rect[0] < rect[2] <= profile_capture['width'] and 0 <= rect[1] < rect[3] <= profile_capture['height'],
         'Reopened checked Coral row must be actually visible within the device screenshot bounds')
    for row in main:
        need(row['kind'] == 'main' and row['sourceKind'] == 'actual Android MainActivity' and
             row['expectedWindowFocused'] is True and row['syntheticGps'] is False, 'Actual Main capture/focus differs')
    root_visual(read(paths['visualProof']), main, paths['liveProof'], 'MainActivity')
    failed_main1_observation(paths, binary, binary_path)
    need(paths['widgetProof'].name == 'widget2.json' and widget['name'] == 'widget2' and
         widget['sourceKind'] == 'actual Android Launcher3 AppWidget RemoteViews', 'Fresh actual widget2 proof required; interrupted widget1 is not runtime evidence')
    widget1_interruption_history(binary, binary_path, widget)
    flags(widget, ('widgetCacheExactlyRestored', 'widgetCacheExistsExactlyRestored', 'launcherWidgetIdsExactlyRestored',
                   'originalFontSettingRestoredBeforeWidgetRemoval', 'twoIndependentOsPinPlacements',
                   'temporaryWidgetCacheContainsNoMetrics'))
    flags(widget, ('sameWidgetResizingVerified', 'launcherResizeGestureExecuted', 'allLauncherSizesVerified',
                   'databaseWritesByReview', 'runCreatedByReview', 'notificationControlsExecuted', 'authenticationExecuted',
                   'foregroundServiceStartedByReview', 'syntheticGps', 'locationInjected', 'glyphInkMeasuredByThisDriver',
                   'wholeLauncherDatabaseOrAllOsBytesRestorationClaimed', 'userVisualApproval'), False)
    need(widget['driverWidgetUpdateBroadcastsSent'] == widget['httpRequestsMadeByDriver'] == 0 and
         len(widget['launcherWidgetLifecycles']) == 2 and
         all(row['newReviewWidgetRemoved'] is True for row in widget['launcherWidgetLifecycles']) and
         all(row['passed'] is True for row in widget['restorationAttempts']), 'Widget pin/removal/cleanup incomplete')
    launcher = png_rows(widget)
    need(len(launcher) == 2 and {row['systemFontScale'] for row in launcher} == {1.0, 2.0}, 'Separate actual font1/font2 idle Launcher captures required')
    for row in launcher:
        need(row['kind'] == 'launcher' and row['source'] == 'Launcher' and row['sourceKind'] == widget['sourceKind'], 'Launcher capture provenance differs')
        flags(row, ('launcherResumedActivityVerified', 'idleWidget', 'originalWidgetMetricsNotDisplayed'))
        need(row['sameWidgetResizingClaimed'] is False and row['syntheticGps'] is False, 'Do not infer widget resize/GPS coverage')
        flags(row['observedBounds'], ('allObservedWidgetChildrenInsideRootAndDisplay', 'openAction48dpBoundsVerified',
                                     'rootDescriptionIsIdleWithoutPlaceholderMetrics'))
    root_visual(read(paths['widgetVisualProof']), launcher, paths['widgetProof'], 'Launcher')
    return live, widget, main, launcher


def cleanup_proof(path, proofs, live, widget):
    """Bind restored attempts to either logged SIGTERM or later absence facts."""
    cleanup = read(path)
    flags(cleanup, ('ownedEmulatorNoLongerRunning', 'noUnrelatedProcessTerminated'))
    ownership_path = reference(cleanup['ownership'])
    need(ownership_path.name == 'owned-avd3.json', 'Final cleanup must bind the actual input5 owner, not input3/4 shutdown')
    ownership = read(ownership_path)
    need(type(cleanup['ownedPid']) is int and cleanup['ownedPid'] > 1 and
         cleanup['ownedPid'] not in (47376, 57956) and
         cleanup['ownedPid'] == ownership['pid'] and ownership['avd'] == cleanup['ownedAvd'] and
         ownership['serial'] == cleanup['serial'] == 'emulator-5556', 'Final AVD observation must bind the actual startup ownership')
    method = cleanup.get('cleanupMethod')
    if method == 'post-reconnection absence observation':
        flags(cleanup, ('noEmulatorProcessObserved', 'oldPidObservedAbsent'))
        flags(cleanup, ('processExitCauseConfirmed',), False)
        need(cleanup['ownedPid'] == 61846 and cleanup['signal'] is None and cleanup['ownerCommandSha256'] is None and
             cleanup['finalProcessState'] == 'absent' and cleanup['observedEmulatorProcesses'] == [] and
             re.fullmatch(r'[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}', cleanup['currentBootId']) is not None,
             'Require actual post-reconnection PID/emulator absence; never invent a signal, command hash or exit cause')
    elif method is None:
        need(isinstance(cleanup['ownerCommandSha256'], str) and
             re.fullmatch(r'[0-9a-f]{64}', cleanup['ownerCommandSha256']) is not None and
             cleanup['signal'] == 'SIGTERM to verified exact owned PID' and cleanup['finalProcessState'] in ('Z', 'absent'),
             'Legacy direct-termination branch requires its actual logged command/SIGTERM evidence')
    else:
        need(False, 'Unreviewed final AVD observation method')
    need(cleanup['ownedAvd'] == 'Runmoni_Cloud_29' and cleanup['physicalDeviceOrUserPcEmulatorUsed'] is False,
         'Cloud-only final AVD scope required')
    expected = dict(proofs, **{'live1.json': read(E / 'live1.json'), live['name'] + '.json': live,
                              widget['name'] + '.json': widget,
                              'widget1-interruption-recovery.json': read(E / 'widget1-interruption-recovery.json')})
    refs = cleanup['restorationProofs']
    need(len(refs) == len(expected) == 7 and {reference(row).name for row in refs} == set(expected),
         'Seven completed formal/Main/widget2/recovery references required; incomplete widget1 binds through recovery only')
    for row in refs:
        name = reference(row).name
        need(read(reference(row)) == expected[name] and row['completed'] is True and
             row['originalParentPassed'] is expected[name]['passed'], 'Cleanup must preserve the actual original parent status')


def publication(binary):
    release, feature = (read(W / name) for name in ('release-context.json', 'feature-merge.json'))
    need(release['avdOffBeforeDispatch'] is True, 'AVD must be off before sequential signing dispatch')
    C, run = release['sourceRevision'], release['ciRun']
    need(re.fullmatch(r'[a-f0-9]{40}', C) is not None and C not in (OLD_C, '0' * 40) and type(run) is int and run > 0,
         'Actual new APK source C and CI run required')
    ci_url = f'https://github.com/wjdals988/runmoni/actions/runs/{run}'
    need(release['url'] == ci_url and feature['mergeRevision'] == C and feature['raw']['state'] == 'MERGED' and
         feature['raw']['mergeCommit']['oid'] == C and feature['url'] == feature['raw']['url'] and
         re.fullmatch(r'https://github.com/wjdals988/runmoni/pull/[1-9][0-9]*', feature['url']) is not None, 'Actual merge/source/CI identity differs')
    O = R / 'work/ci-release' / str(run)
    names = (f'runmoni-{VERSION}.metadata.json', 'publication.json', 'ci-verification.json', 'public-head-verification.json')
    meta, pub, ci, head = (read(O / name) for name in names)
    need(C == meta['sourceRevision'] == pub['sourceRevision'] == ci['sourceRevision'], 'Actual APK source C differs')
    identity(meta)
    need(meta['packageName'] == 'com.runmoni.app' and meta['fileName'] == f'runmoni-{VERSION}.apk' and
         meta['certificateSha256'] == read(R / 'config/android-release-signing.json')['certificateSha256'] and
         meta['signed'] is True and meta['sourceDirty'] is False and pub['metadata'] == meta, 'Retained signer/clean APK identity differs')
    flags(pub, ('publicReleaseConfirmed', 'publicationReady', 'signedCiVerified', 'sidecarBodiesVerified'))
    need(pub['draft'] is False and pub['repository'] == 'wjdals988/runmoni' and pub['ciRun'] == ci['ciRun'] == run and
         pub['physicalDeviceTests'] == 0 and pub['googleLoginVerified'] is False and pub['apkBodyDownloaded'] is False,
         'Public release/runtime boundary differs')
    flags(ci, ('ciSuccess', 'signedApkInspectedByCi', 'nonDebuggablePackageVersionAndEmbeddedRevisionCheckedByCi'))
    need(ci['jvm'] == binary['jvm'] and ci['lint']['Error'] == ci['lint']['Fatal'] == 0, 'Actual release CI/JVM/lint differs from fresh final evidence')
    flags(ci, ('physicalDeviceVerified', 'interactiveGoogleLoginVerified', 'apkBodyDownloadedByCloudReviewer'), False)
    need(meta['connectionReadiness'] == ci['connectionReadiness'] and meta['connectionReadiness']['ready'] is True,
         'Configuration readiness required; it does not prove runtime authentication')
    need(type(meta['sizeBytes']) is int and meta['sizeBytes'] > 0 and re.fullmatch(r'[a-f0-9]{64}', meta['sha256']) is not None,
         'Actual public APK size/hash required')
    url = f'https://github.com/wjdals988/runmoni/releases/download/v{VERSION}/{meta["fileName"]}'
    need(pub['releaseUrl'] == f'https://github.com/wjdals988/runmoni/releases/tag/v{VERSION}', 'Canonical actual release URL differs')
    assets = {row['name']: row for row in pub['assets']}
    asset_names = {meta['fileName'], f'runmoni-{VERSION}.metadata.json', f'SHA256SUMS-{VERSION}.txt'}
    need(len(assets) == len(pub['assets']) == 3 and set(assets) == asset_names, 'Three distinct actual public assets required')
    for name, row in assets.items():
        need(row['state'] == 'uploaded' and row['browser_download_url'] == f'https://github.com/wjdals988/runmoni/releases/download/v{VERSION}/{name}',
             'Actual uploaded asset URL/state differs')
        size, digest = (meta['sizeBytes'], meta['sha256']) if name.endswith('.apk') else ((O / name).stat().st_size, sha(O / name))
        need(row['size'] == size and row['digest'] == 'sha256:' + digest, 'Actual asset/sidecar bytes differ')
    need((O / f'SHA256SUMS-{VERSION}.txt').read_text().strip() == f'{meta["sha256"]}  {meta["fileName"]}', 'Actual checksum sidecar differs')
    need(head['url'] == url and head['httpStatus'] == 200 and head['contentLength'] == meta['sizeBytes'] and
         head['matchesVerifiedMetadata'] is True and head['apkBodyDownloaded'] is False, 'Actual public HEAD proof required')
    return O, C, run, ci_url, feature, meta, pub, ci, url


def dashboard(paths, binary, C, run, meta, pub, url, main):
    comparison = read(paths['sourceComparison'])
    need(comparison['releaseSourceRevision'] == C and comparison['allSourceMapsMatch'] is True and
         comparison['sourceMaps'] == {key: binary[key] for key in MAPS}, 'Actual release C must match every executed input map')
    freeze_path = reference(comparison['executedSourceFreeze'])
    need(freeze_path == E / binary['sourceFreezeReference'] and read(freeze_path)['sourceMaps'] == comparison['sourceMaps'], 'Source comparison must bind actual final source freeze')
    board, node, baseline = (read(W / name) for name in ('dashboard-production.json', 'dashboard-node-tests.json', 'dashboard-baseline.json'))
    flags(board, ('productionVerified',))
    flags(board, ('physicalDeviceVerified', 'interactiveGoogleLoginVerified', 'productionPartyWritesVerified', 'userVisualApproval'), False)
    deployment, page = board['dashboard'], board['productionPage']
    need(deployment['repository'] == 'wjdals988/mydashboard' and deployment['mainCi']['conclusion'] == 'success' and
         deployment['productionDeployment']['state'] == 'success' and re.fullmatch(r'[a-f0-9]{40}', deployment['mergeRevision']) is not None,
         'Actual dashboard main CI/Production required')
    need(page['url'] == CANONICAL and page['httpStatus'] == 200 and len(page['checks']) == 13 and
         all(value is True for value in page['checks'].values()), 'Canonical thirteen actual Production checks required')
    need(all(board['apk'][key] == value for key, value in dict(sourceRevision=C, version=VERSION, versionCode=CODE,
         sha256=meta['sha256'], sizeBytes=meta['sizeBytes'], url=url).items()), 'Production APK differs from actual publication')
    need(deployment['localNodeTests'] == node and node['exitCode'] == 0 and node['tests'] == node['passed'] > 0 and
         node['failed'] == node['skipped'] == 0 and node['rawLog'] == 'dashboard-node-tests.log', 'Actual Node test observations required')
    raw_path = safe(W / node['rawLog'], W)
    need(sha(raw_path) == node['rawSha256'], 'Node raw log changed')
    for label, key in (('tests', 'tests'), ('pass', 'passed'), ('fail', 'failed'), ('skipped', 'skipped')):
        found = re.findall(r'(?m)^(?:ℹ\s*|#\s*)?' + label + r'\s+(\d+)\s*$', raw_path.read_text())
        need(len(found) == 1 and int(found[0]) == node[key], 'Measured Node log count differs')
    gallery = read(paths['galleryManifest'])
    identity(gallery)
    need(gallery['rootVisualReviewVerified'] is True and gallery['userVisualApproval'] is False and len(gallery['images']) == 4,
         'Exactly four actually reviewed Main originals required')
    compare = gallery['sourceComparison']
    need(compare['releaseSourceRevision'] == C and compare['runtimeAppSourcesMatchRelease'] is True and
         reference(compare['proof']) == paths['sourceComparison'], 'Gallery/actual C comparison differs')
    captures = {(row['file'], row['sha256']): row for row in main}
    for row in gallery['images']:
        reference(row)
        need((row['file'], row['sha256']) in captures and reference(row['captureProof']) == paths['liveProof'] and row['source'] == 'MainActivity',
             'Public gallery is not a final actual Main original')
        flags(row, ('actuallyViewedWithViewImage', 'privacyReviewed'))
        original = captures[(row['file'], row['sha256'])]
        need((row['width'], row['height']) == (original['width'], original['height']) and row['caption'].strip() and row['alt'].strip(), 'Actual gallery dimensions/captions required')
    live_name = read(paths['liveProof'])['name']
    selected_files = {row['file'] for row in gallery['images']}
    fixed_files = {live_name + '-main-' + suffix + '.png' for suffix in ('studio-home', 'studio-closet', 'record-result')}
    card_files = {live_name + '-main-' + suffix + '.png' for suffix in ('default-card', 'coral-card')}
    need(fixed_files <= selected_files and len(selected_files - fixed_files) == 1 and
         selected_files - fixed_files <= card_files, 'Select actual final home/closet/result and one Root-reviewed default/Coral original; never use failed Main1')
    need(len({row['key'] for row in gallery['images']}) == len({row['sha256'] for row in gallery['images']}) == 4 and
         len(board['gallery']) == 4 and {row['sha256'] for row in board['gallery']} == {row['sha256'] for row in gallery['images']}, 'Four Production PNG originals differ')
    need(all(row['httpStatus'] == 200 and row['matchesCommittedOriginal'] is True for row in board['gallery']), 'Production PNG bytes not verified')
    items, before = read(B / 'src/lib/projects.json'), baseline['projects']
    need(len(items) == len(before) == baseline['projectCount'] and baseline['otherProjectCount'] == len(before) - 1 and
         [row['slug'] for row in items] == [row['slug'] for row in before] and
         [row for row in items if row['slug'] != 'runmoni'] == [row for row in before if row['slug'] != 'runmoni'], 'Other dashboard projects/order changed')
    old, project = (next(row for row in source if row['slug'] == 'runmoni') for source in (before, items))
    need(baseline['oldReleaseNoteCount'] == len(old['releaseNotes']) == 20 and baseline['oldScreenshotCount'] == len(old['screenshots']) == 4 and
         (old['apk']['version'], old['apk']['versionCode']) == (OLD_VERSION, OLD_CODE) and project['releaseNotes'][1:] == old['releaseNotes'] and
         len(project['releaseNotes']) == 21 and project['sourceUrl'] == old['sourceUrl'], 'Original dashboard history/source changed')
    need(all(project['apk'][key] == value for key, value in dict(url=url, sha256=meta['sha256'], version=VERSION, versionCode=CODE,
         fileName=meta['fileName'], size=f'{meta["sizeBytes"]:,} bytes', releaseUrl=pub['releaseUrl']).items()) and
         (project['releaseNotes'][0]['version'], project['releaseNotes'][0]['versionCode']) == (VERSION, CODE), 'Local dashboard APK/notes differ')
    shot_paths = [f'/reviews/runmoni/{VERSION}/{row["key"]}.png' for row in gallery['images']]
    need([row['src'] for row in project['screenshots']] == shot_paths and {row['path'] for row in board['gallery']} == set(shot_paths), 'Production/local gallery paths differ')
    for src, row in zip(shot_paths, gallery['images']):
        need(sha(safe(B / 'public' / src.lstrip('/'), B / 'public/reviews')) == row['sha256'], 'Copied gallery PNG differs')
    need(set(baseline['oldImages']) == {row['src'] for row in old['screenshots']} and len(baseline['oldImages']) == 4, 'Old PNG inventory changed')
    for src, expected in baseline['oldImages'].items():
        need(sha(safe(B / 'public' / src.lstrip('/'), B / 'public/reviews')) == expected, 'Original dashboard PNG changed')
    need(sha(B / 'scripts/update-project-apk.mjs') == baseline['updaterSha256'], 'Official dashboard updater changed')
    published = dt.datetime.fromisoformat(pub['publishedAt'].replace('Z', '+00:00'))
    need(published.tzinfo is not None, 'Actual publication timestamp/timezone required')
    date = published.astimezone(ZoneInfo('Asia/Seoul')).date().isoformat()
    need(project['updatedAt'] == project['releaseNotes'][0]['date'] == date.replace('-', '.'), 'Dashboard actual publication date differs')
    return board, node, baseline, gallery, date


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    context = read(E / 'final-ui-context.json')
    paths = {key: reference(context[key]) for key in CONTEXT_KEYS}
    scope = read(paths['scopeProof'])
    binary, binary_path, proofs, totals = local_and_scope(scope)
    live, widget, captures, launcher = main_and_widget(paths, binary, binary_path)
    cleanup_proof(paths['sessionCleanup'], proofs, live, widget)
    O, C, run, ci_url, feature, meta, pub, ci, url = publication(binary)
    board, node, baseline, gallery, date = dashboard(paths, binary, C, run, meta, pub, url, captures)
    feed_path = R / 'updates/android.json'
    feed = read(feed_path)
    need(feed['schemaVersion'] == 1 and feed['stable'] is None and
         (feed['preview']['version'], feed['preview']['versionCode'], feed['preview']['sourceRevision']) == (OLD_VERSION, OLD_CODE, OLD_C) and
         feed['preview']['certificateSha256'] == meta['certificateSha256'], 'Keep verified preview27/source df09/stable null until the actual update')
    rel = lambda key: paths[key].relative_to(E).as_posix()
    jvm, local_warning, ci_warning = binary['jvm']['tests'], binary['lint']['Warning'], ci['lint']['Warning']
    cleanup = read(paths['sessionCleanup'])
    if cleanup.get('cleanupMethod') == 'post-reconnection absence observation':
        final_avd_status = ('7건의 완료 자료에서 원래 데이터·소유 환경 복원을 확인한 뒤, 재연결 시점의 /proc 열거에서 '
                            '소유 PID61846과 모든 emulator 프로세스가 없음을 관측했다. 종료 시그널은 보내지 않았으며 '
                            '이전 AVD가 사라진 방식·시각·원인은 확인하지 못했다.')
    else:
        final_avd_status = '7건의 완료 자료에서 복원을 확인하고, 실제 기록된 소유 PID 명령과 SIGTERM·최종 상태를 대조했다.'
    final_input = re.fullmatch(r'binary-build([1-9][0-9]*)\.json', binary_path.name)[1]
    total_text = ' + '.join(f'{name}의 선택된 전체 통과 그룹{count}' for name, count in totals.items()) + ' = 고유90개'
    excluded_text = ('동일 binary5의 ar2 정상 전체그룹72개·실패그룹 한 번 재실행 ar3의3개·native3 전체15개를 채택했다. '
                     'ar2는 parent=false이고 원래 두 실패 그룹을 보존하며 일반75개 단일phase 전체PASS를 주장하지 않는다. '
                     'SnapshotStateObserver 오류의 원인은 미확정이며 해결되었다고 쓰지 않는다. 입력3/4 결과는90개에 합산하지 않았다.')
    main_labels = '프로필 실제IME·옷장·기록 결과·기본1:1·실제 코랄 선택·지도' + ('·홈' if len(captures) == 7 else '(홈은 기존 기록 개인정보 때문에 생략)')
    limits = ('휴대폰·Google 로그인·운영 프로필/Firestore/네이버 인증·SNS 전달·야외 GPS/장시간 백그라운드·Doze/OEM/API31이상·'
              '공개 서명 APK 런타임·사용자 시각 승인은 별도 미검증이다. Launcher의 두 번의 새 배치를 같은 위젯 크기 변경이나 모든 런처 크기 검증으로 확대하지 않는다.')
    review = f'''# Runmoni0.6.0-dev 페이지 이동·위젯 게시 검증

frontend-design/ui-ux-pro-max와 [페이지 설계](../design-system/runmoni/pages/sport-pages.md)를 적용해 긴 설정·작성 화면은 독립 페이지와 뒤로가기로 연결하고, 짧은 진입·상태 화면은 내용이 들어가는 높이에서 중앙에 배치했다. 실제 최근7일 기록, 옷장 비교, 기록 거리/단위, 코랄 공유와 위젯의 수치·제어 위계를 정리했다. API29 작성 페이지에서 키보드가 저장 버튼을 가리던 문제는 기존 공유 화면의 framework fit/resize 정책을 공통화해 수정했다. framework가 맞춘 페이지에서는 Compose insets를 다시 빼지 않으며, 나갈 때 원래 soft-input mode와 소유한 layout flag3개를 복원한다. API30이상 경로는 그대로이며 해당 OS 실행은 이번 결과에 포함하지 않는다. 승인 미니미와 기존 데이터·권한·계정 경계를 유지한다.

## 실제 게시와 APK 출처

{date} 한국 시간, 같은 보관키의 실기기 검증 전 개발 prerelease {VERSION}/code{CODE}. [APK]({url}) · [릴리스]({pub['releaseUrl']}) · [구현 PR]({feature['url']}) · [서명 CI]({ci_url}).

실제 APK출처 C는 `{C}`이며 크기{meta['sizeBytes']:,}bytes, SHA256 `{meta['sha256']}`, 인증서 SHA256 `{meta['certificateSha256']}`다. 후속 문서·피드 커밋 D로 이 C를 바꾸지 않는다. 빌드 당시 로컬 HEAD `{binary['sourceCommitAtBuild']}`는 별도 기록으로 보존하고 [전체 입력과 공개 C 대조]({BASE}/{rel('sourceComparison')})를 확인했다. 공개0.5.0의 출처 `{OLD_C}`도 이전 APK의 값으로 보존한다.

서명 CI의 실제 JVM{jvm}(실패/오류/skip0), release lint오류0·경고{ci_warning}, non-debuggable/패키지·버전·내장 C/보관 인증서와 연결 설정 readiness를 확인했다. 실제 공개 APK·메타데이터·SHA256SUMS 세 자산의 digest/sidecar와 APK HEAD200·크기를 대조했다. readiness는 설정 확인이며 로그인 성공이 아니다. 검토자가 공개 APK 본문을 다시 다운로드하거나 실행한 것은 아니다.

## 이번 로컬 실행과 원본 사진

- [최종 input{final_input}]({BASE}/{binary_path.name})·{binary['sourceFreezeReference']}의 새 JVM{jvm}, lint오류0·경고{local_warning}을 확인했다. TEST debug/API29/AOSP/TCG·Firebase 비활성 검토와 실제 connected release 설정은 구분한다.
- [Android 범위]({BASE}/{rel('scopeProof')}): {total_text}. {excluded_text} 채택한 전체 통과 그룹의 raw SHA/실제 OK개수·frozen FQCN/method 합집합·동일 설치 APK쌍·전/후/복원후6개 입력 맵을 대조했다. 실패 그룹 안의 일부 성공과0.5의69개·이전 입력의 관측은 합산하지 않았다. includedGroups/excludedFailedGroups의 원문/SHA와 sameInputPassedGroupsRetained=true·singleSuccessfulDefaultPhaseClaimed=false·earlierInputResultsReused=false를 그대로 유지한다. native15개는 별도 실제 OS 조건의 전체 통과 실행이며14개는 실제320dp/font2, 공유 문구/IME1개는 실제480dp/font1 조건이다. 일반의 가상 viewport와 실제 OS 설정을 구분한다.
- UI 검사는 ComponentActivity/in-memory/inert-map 픽스처이며 실제 Main이나 운영 서버 성공이 아니다. RemoteViews 측정7개와 공유 Canvas PNG renderer6개는 별도 픽스처다. 코랄3비율·밝은/어두운 사진·남녀 승인 미니미의 PNG 검사는 실제 Main의 사진Picker·저장·SNS 실행을 대신하지 않는다.
- [실제 Main 최종 live2 완료]({BASE}/{rel('liveProof')}): {main_labels}, 새 원본{len(captures)}장. 기본1:1과 코랄 radio 실제 선택, 옷장 하의→상의 selected 상태·기록/미니미/배경 도구 탭 입력, Back·재진입 설명 일치와 지도 밖 조작 위치를 관측했다. 재진입 코랄 행의 실제 checkable/checked는 각각 `{live['actualReopenedCoralRadioAccessibility']['checkable']}`/`{live['actualReopenedCoralRadioAccessibility']['checked']}`, clickable은 `{live['actualReopenedCoralRadioAccessibility']['clickable']}`로 기록했다. 코랄 입력은 최초1회이며 재진입 뒤 다시 누르지 않고 fresh checked=true를 확인했다. 도구 탭의 selected 속성을 관측했다고 확대하지 않는다. 프로필 편집에서는 실제 닉네임 필드에 포커스를 두고 dumpsys의 IME 표시·Main의 실제 WindowManager visible frame·뒤로/저장48dp와 그 안에 들어가는 전체 경계를 검사했다. 내용을 입력하거나 저장·온라인 연결을 누르지 않았고 Back 뒤 편집 진입 직전의 저장 프로필이 같은지 확인했다. 위치 권한을 끄고 nonce 합성 완료 기록·합성 사진 fixture를 사용했으며 GPS 주입은 없었다. 지도 사진은 세계 지도 대체·위치 권한/타일 연결 오류 상태이며 지도 밖 조작을 관측한 범위다. 실제 위치·온라인 타일 HTTP/내용·지도 제공자 인증 성공은 확인하지 않았다. 이번 Main은 사진Picker·갤러리 저장·별도 UID receiver/PNG export·SNS 전달을 재실행하지 않았다.
- [새 실제 Launcher widget2]({BASE}/{rel('widgetProof')}): 복원 전용 절차가 끝난 뒤 새 idle 위젯을 font1과 font2에서 각각 OS pin 배치해 원본2장을 촬영했다. 새 위젯의 수치 없는 상태·경계·열기48dp 조작 영역을 확인하고 매번 새 위젯을 제거했다. 중단된 widget1의 결과를 이어 쓰거나 성공으로 바꾸지 않는다. 글리프 잉크/폰트 크기 측정은 이 Launcher collector의 결과가 아니며 별도 factory 픽스처와 구분한다. 러닝·FGS·알림 제어·같은 위젯 resizing·전체 런처 DB 복원을 실행했다고 쓰지 않는다.
- [Root 최종 Main 원본 검토]({BASE}/{rel('visualProof')})·[Root Launcher 원본 검토]({BASE}/{rel('widgetVisualProof')}): live2의 새 원본{len(captures)}장과 Launcher2장을 실제로 열어 내용·개인정보를 검토했다. 실패한 live1의 부분6장이나 그 Root검토를 최종 사진으로 재사용하지 않았다. 운영 갤러리4장은 최종 Main 원본 중 선택한 것이며 Launcher나 Canvas PNG를 앱 화면으로 표시하지 않는다. 사용자 시각 승인은 별도다.
- 원래 DB행/XML/프로필/account와 각 collector가 소유한 설정·권한·전원을 복원했다. Launcher는 원래 widget cache의 존재/내용·기존 widget ID를 복원하고 nonce fixture·새 widget을 정리했다. [복원과 최종 AVD 상태]({BASE}/{rel('sessionCleanup')})는 ar2/ar3/native3/실패live1/최종live2/widget2/복원전용report 일곱 완료 자료와 시작 ownership의 원래 bytes를 묶는다. {final_avd_status} 미완료 widget1 원문은 복원 report의 SHA로만 묶으며 완료했다고 쓰지 않는다. ar2와 live1의 parent=false를 true로 바꾸지 않으며 이전 입력3/4의 실패와 종료도 아래 별도 근거로 보존한다. 전체 OS bytes·모든 shared preferences·원래 GPS fix·전체 Picker cache 복원을 주장하지 않는다.

## 보존한 실패와 검증 경계

[build1]({BASE}/build1-failure.json)은 중첩 ColumnScope의 maxHeight 암묵 수신기로 compileDebugKotlin에서 실패했고, [build2]({BASE}/build2-failure.json)는 RemoteViews framework android:tint의 UseAppTint lint오류로 실패했다. 원문 build1-failed.txt/build2-failed.txt와 이전 source-freeze를 보존한다. build2에서 관측한 JVM 결과를 최종 input{final_input}의 새 결과에 더하지 않았다.

[입력3 기본 suite]({BASE}/android-review1.json)는 community의 nonzero-scroll fixture 전제에서 실패했다. 실제 내용이 한 화면에 들어가 beforeScroll이0이었고 채팅/Back 경로에 도달하지 않았으므로 제품 내비게이션 실패를 확정한 근거는 아니다. [당시 진단]({BASE}/community-scroll-precondition.json)·실패 raw·parent=false를 보존한다. 전체 성공 그룹63개와 실패 community그룹의 부분 통과11개를 구분하며 둘 다 최종90개에 합산하지 않았다. 기본 suite의 실패를 맞춤 창의 성공으로 대체하지 않았고, 별도 창 재검사 계획은 미실행 이력이다.

[입력3 native suite]({BASE}/native1.json)도 parent=false다. 전체 성공 그룹13개와 native-form-pages의2실행/1실패를 구분하며 실패 그룹의 일부 성공을 세지 않는다. [실패 원문]({BASE}/native1-native-form-pages.txt)은 `modal_native_submit`의 실제 OS/IME 경계 assertion에서 실패했다. submit geometry 값 기록이 assertion 뒤에 있어 실패 좌표는 남지 않았으며 실제 제품 겹침인지, 기본 ComponentActivity의 창 정책인지, 좌표 비교 문제인지는 당시 미확정이었다. Main은 adjustResize·enableEdgeToEdge 정책을 사용하지만 당시 native fixture는 이를 명시적으로 맞추지 않았다. 입력4에서는 같은 창 정책·사전 geometry JSONL·실패 PNG로 관측 조건을 정렬했으며, 이 조정만으로 해결했다고 쓰지 않는다. 기존 경계 assertion·좌표계·0.5px 허용오차·30초 대기 기준을 약화한 통과로 대체하지 않는다.

[입력3 종합 진단]({BASE}/input3-diagnostic-review.json)은 두 parent=false와 일반63/native13이라는 당시 전체 성공 그룹 수를 원래 값으로 묶는다. 원래 데이터·소유 환경·6개 입력 맵의 복원을 확인한 뒤 [소유 AVD 종료]({BASE}/session-cleanup-input3.json)에서 PID47376에 정확한 SIGTERM을 보내 Z상태를 확인했다. 최종 입력은 아래의 같은 APK 정상72+실패그룹 재실행3+native15를 따르며 입력3의 통과·실패나 이전0.5 결과는 최종90개에 보태지 않았다.

[입력4 native2]({BASE}/native2.json)에서는 같은 UI스레드의 semantic/layout 좌표가 `(340,680)–(450,764)`로 일치했다. [사전 geometry]({BASE}/native2-native-form-page-bounds.jsonl)의 실제 IME441px·visible frame하단413px에 비해 버튼 하단764px가351px 아래였고, safe content하단은782px에 머물렀다. Root는 개인정보 없는 합성 ComponentActivity 실패 PNG를 직접 열어 버튼 전체가 키보드에 가려짐을 확인했다. 이 사진은 실제 Main/휴대폰 사진이 아니며 공개하지 않는다. [진단]({BASE}/input4-ime-diagnosis.json)은 이를 좌표 API의 오차로 돌리지 않고 API29 작성 페이지의 실제 가림으로 기록한다.

[중단]({BASE}/input4-stop-reason.json)은 정확한 native collector에 SIGINT를 보낸 것이며 parent=false/KeyboardInterrupt·전체 성공 그룹 수0을 유지한다. 중간 중단이라 sourceMapsAfter는 없고, 실제 sourceMapsBefore/AfterRestoration의6개 맵·설치쌍·원래 XML/프로필/account·소유 환경의 복원만 확인한다. [입력4 종료]({BASE}/session-cleanup-input4.json)는 정확한 소유PID57956에 SIGTERM을 보내 Z상태를 확인했다. 검사나 제품을 고치기 전에 이 복원·종료를 완료했으며 입력4의 부분 관측은 최종90개에 더하지 않는다.

제품 수정은 `RunmoniWindowPolicy.kt`에서 API29 framework fit/resize와 원래 mode/layout flag 복원을 공유하고 FormDialog·공유 화면에 함께 적용한다. framework가 이미 맞춘 영역의 Compose insets는0으로 처리해 이중 차감을 막는다. 최종 native 검사는 기존 OS/IME/48dp 판정을 유지하고 앱 정보→업데이트 내역→앱 정보 복귀·닫힌 뒤 원래 flags/mode 복원도 확인한다. 이 IME 제품 수정과 아래의 원인 미확정 스레드 오류는 별도다.

[입력5 스레드 관측]({BASE}/input5-thread-diagnosis.json): [ar2 Consumer1]({BASE}/android-review2-closet-consumer.txt)은 SnapshotStateObserver의 previousThread247/Main2 교차 접근 예외였고, [Colors2]({BASE}/android-review2-closet-colors.txt)는 previousThread251/Main2 예외와 프로세스 crash였다. 이전 스레드의 이름·stack과 최초 진입 원인은 이 원문으로 확정할 수 없다. 제품의 MiniMi draw 콜백과 테스트의 캡처 경로는 검토 후보이며 어느 하나를 원인이나 해결책으로 단정하지 않는다. 이 실패는 입력5의 새 원문으로 보존한다.

ar2의 나머지 전체 통과 그룹72개를 유지하고 실패한1+2개 그룹은 일부 성공까지 모두 제외했다. 앱·테스트·APK쌍과6개 입력 맵을 바꾸지 않고 실패한3개만 [ar3]({BASE}/android-review3.json)에서 한 번 재실행해 각 그룹 전체 통과를 관측했다. Consumer1은 첫 시도에도 독립 instrumentation 프로세스였으므로 재실행을 새로운 격리나 원인 해결로 표시하지 않는다. 이 제한된 재실행도 실패하면 이 helper는 게시를 막는다. native3의 전체15개와 합쳐 중복 없는90개를 세지만 일반75개가 한 phase에서 모두 성공한 결과는 아니다. 원래 ar2의 false/crash·미확정 원인·복원과 실제 재실행 조건을 보존하며0.5의 timeout 관측을 이번 오류의 원인으로 가져오지 않는다.

[첫 Main live1]({BASE}/live1.json)은 부분6장 촬영 뒤 Back·재진입의 `Actual control not reachable: 코랄`에서 parent=false로 끝났다. 앱 crash나 checked-state assertion 실패로 관측한 것은 아니며, 해당 코랄 행의 실패 순간 checked/clickable 속성도 확인하지 못했다. 기존 observer가 관측용 도달 검색에 click action을 요구한 것은 실행 driver에서 확인했지만 이 사실만으로 정확히 그 행이 clickable=false였다고 확정하지 않는다. [부분 원본6장 Root검토]({BASE}/root-main1-partial-review.json)와 실행 driver·실패·원래 데이터/환경 복원을 보존한다. 조기 실패라 sourceMapsAfter는 없고 Before/AfterRestoration의6개 맵만 확인한다.

[Main observer 진단]({BASE}/main1-observer-diagnosis.json)에 따라 live2는 visible/checkable 행의 관측과 fresh checked=true 판정을 나눴고 재진입 뒤 코랄을 다시 누르지 않았다. 앱·테스트·APK쌍은 바꾸지 않았다. live1의 실패나 부분 사진을 성공으로 바꾸지 않고 최종 live2에서 전체 동선을 다시 실행·촬영해 새 원본만 채택했다. 이 관측 helper 보완과 원인 미확정 SnapshotStateObserver 오류는 별개다.

[첫 위젯 시도 widget1]({BASE}/widget1.json)은 환경이 starting으로 표시되고 ready 대기가 반환된 재연결 구간에 기존 exec세션/launcher driver 프로세스가 관측되지 않아 중단됐다. 종료코드·시그널 로그는 없으며 정확한 종료 방식이나 플랫폼 원인·앱 실패를 확정하지 않는다. 분리된 소유 AVD와 파일은 유지됐다. 원래 proof는 guards 단계·completed=false/passed=false·사진0·lifecycle0의 같은 bytes로 보존한다. 이 미완료 자료를 OS pin이나 위젯 실행 성공으로 읽지 않는다.

[복원 전용 report]({BASE}/widget1-interruption-recovery.json)에서는 원래 main XML·widget cache·소유 환경이 복원 전에는 달라져 있었음을 확인한 뒤 정확히 복원했다. 프로필/account·DB·캐시 존재/내용·기존 widget ID와6개 입력 맵도 대조했다. DB를 복사해 덮어쓰거나 새 위젯을 제거한 것은 아니며 미지의 widget ID가 있으면 제거하지 않는 범위였다. [중단 assessment]({BASE}/widget1-interruption-assessment.json)는 원래 미완료 proof·실제 복원 report·실행한 복원 helper archive의 SHA를 묶는다. 네 private backup은 이름/해시 메타데이터만 남기고 내용을 공개하지 않는다. 복원 report의 passed=true는 상태 복원 성공만 뜻하며 widgetRuntimeVerified=false·제품검사0·사진0을 그대로 유지한다. 실제 Launcher 결과는 복원 후 새 widget2의 전체 실행·새2장·Root검토로 별도 확인했다.

{limits} 실제150ms 시간·모든 중간 프레임·모든 모션 배율이나 전체 픽셀 대비를 측정한 결과도 아니다. 원본 SHA `{ART_SHA}`·58개 ID/13슬롯/9분류/0P·보유권·지갑·위치 동의/FGS/runId·계정/공유 수명 경계를 보존한다. 새 원화·키·Rules·의존성은 이 개편에 추가하지 않았다.

## 운영 대시보드와 다음 작업

[운영 대시보드]({CANONICAL}) · [대시보드 PR]({board['dashboard']['pullRequest']}) · [main CI]({board['dashboard']['mainCi']['url']}). 실제 Production {board['dashboard']['productionDeployment']['id']}, main `{board['dashboard']['mergeRevision']}`에서 canonical13항목·Main 원본PNG4장의 운영 bytes·Node{node['tests']}개를 확인했다. 다른{baseline['otherProjectCount']}개 프로젝트·이전20개 내역·이전4개 PNG와 공식 updater를 보존했다. [근거]({BASE}/dashboard-production-verification.json).

이번 변경은 검증된 메타데이터로 preview28만 준비하고 stable null을 유지한다. canonical raw 피드 전체 bytes는 후속 문서/피드 PR 병합 뒤 별도 확인한다. 다음은 같은 키 휴대폰 업데이트·기존 기록/아이템 보존, 실제 페이지 복귀/큰 글씨·위젯과 장시간 러닝, Google/운영 프로필·사진 저장·SNS 전달의 개별 확인이다. 남은 승인 원화 의복과 크루 승계·신고/차단도 독립적으로 진행한다.
'''
    headline = f'''# {VERSION} 페이지 이동·위젯 공개 — {date} 한국 시간

{MARKER}
긴 작성/설정 화면을 독립 페이지로 연결하고 뒤로가기·초안·읽던 위치를 유지한다. 실제7일 기록·옷장 비교·거리/단위·코랄 공유·위젯 위계를 정리하고 API29 작성 페이지의 IME 버튼 가림을 공통 window policy로 수정했다. 같은 보관키/code{CODE}, 실제 APK출처 `{C}`, {meta['sizeBytes']:,}bytes, SHA256 `{meta['sha256']}`다. [게시 검증](docs/{REVIEW}). 새 로컬 JVM{jvm}/lint오류0·경고{local_warning}, 실제 서명 CI{run} JVM{ci['jvm']['tests']}/lint오류0·경고{ci_warning}, input{final_input} Android고유90개와 최종 live2 새 원본{len(captures)}·새 widget2의 Launcher2장·운영13항목/PNG4/Node{node['tests']}개를 구분한다. {excluded_text} 이전0.5 결과·build1컴파일/build2lint·입력3 실패/당시 미확정 원인·입력4에서 측정한 실제 가림과 중단/복원/정확 종료를 별도 보존한다. 첫 Main의 관측 실패와 부분6장도 보존하고 최종 사진으로 재사용하지 않았다. 첫 위젯의 프로세스 소멸/미완료와 실제 scoped 복원은 runtime성공·검사수로 세지 않는다. {final_avd_status} 원래 위젯 상태의 복원도 별도 자료로 확인했다. {limits} canonical raw피드는 후속 병합 뒤 별도 확인한다.

'''
    followup = f'''# 최신 이어가기 — {VERSION}/code{CODE}, {date}

{MARKER}
[게시 검증]({REVIEW})과 [페이지 설계](../design-system/runmoni/pages/sport-pages.md)를 시작점으로 삼는다. 실제 APK출처 `{C}`는 후속 문서/피드 커밋으로 바꾸지 않는다. 같은 키 서명CI{run}·공개3자산·운영13항목/MainPNG4/Node{node['tests']}개를 확인했다. input{final_input}의 고유Android90개·최종 live2 새 원본{len(captures)}장·새 OS pin Launcher2장과 원래 데이터/소유 환경 복원·최종 AVD 상태 관측을 구분한다. {excluded_text} 이전0.5 검사나 입력1/2 빌드실패·입력3/4 실패를 새 결과에 합산하지 않는다. 첫 Main의 도달 관측 실패·부분6장과 [observer 진단](app-review/2026-11-14-sport-pages/main1-observer-diagnosis.json)을 보존하며 사진은 재사용하지 않는다. [입력3 진단](app-review/2026-11-14-sport-pages/input3-diagnostic-review.json)의 두 parent=false·일반63/native13·당시 원인미확정·PID47376 종료와 [입력4 실제 IME 가림/중단/복원](app-review/2026-11-14-sport-pages/input4-ime-diagnosis.json)·전체성공그룹0·PID57956 종료를 별도 보존한다. 최종 제품은 API29 framework fit/resize·단일 inset 처리·원래 flags/mode 복원을 Form/공유에 적용했다. {final_avd_status}

1. 휴대폰에서 같은 키 업데이트 뒤 기록/아이템·뒤로가기/초안/선택 탭·큰 글씨·위젯을 확인한다. 승인 여부는 사용자에게 남기고 새 키/같은 Rules 게시를 반복 요구하지 않는다.
2. 실제 사진Picker·caption·갤러리 PNG 저장·SNS 전달과 프로필 저장·Google/운영 연결을 각각 확인한다. 이번 Main의 프로필 IME/저장없이 Back·기본/코랄 미리보기와 native IME·Canvas PNG 검사로 대신하지 않는다.
3. 야외 GPS/화면 끔/장시간BG·알림과 위젯 제어·Doze/OEM/API31+를 확인한다. 이번 Launcher는 font1/font2 별도 새 배치이며 같은 위젯 resizing은 다음 범위다.
4. 이번 제한된 Main 밖의 페이지·가로/IME·빈 상태/실패/재시도와 남은 승인 원화 의복·크루 승계/신고/차단을 우선한다. 완료한 무료/성별/공유를 재구현하지 않는다.

승인 원본·58ID/13슬롯/9분류/0P·보유권/지갑과 순차 heavy·오프라인/작업자1·클라우드전용AVD/종료 또는 부재 관측의 구분을 유지한다. {limits}

아래는 이전 시점의 보존 기록이다.

'''
    preview = {key: meta[key] for key in ('version', 'versionCode', 'fileName', 'sizeBytes', 'sha256', 'certificateSha256', 'sourceRevision', 'signed')}
    preview.update(package=meta['packageName'], buildType='release', physicalDeviceTests=0, downloadUrl=url, releaseUrl=pub['releaseUrl'],
                   notes='페이지 이동·뒤로가기와 위젯·옷장 비교·코랄 공유를 개선한 실기기 검증 전 개발 프리뷰입니다.')
    existing = ('AGENTS.md', 'CHANGELOG.md', 'DESIGN.md', 'design-system/runmoni/MASTER.md', 'docs/DEVELOPMENT_CONTINUATION.md',
                'docs/DEVELOPMENT_PRIORITIES.md', 'updates/android.json', 'design-system/runmoni/pages/sport-pages.md',
                'docs/app-review/2026-11-14-sport-pages/README.md')
    originals = {R / name: (R / name).read_bytes() for name in existing}
    need(all(MARKER.encode() not in raw for raw in originals.values()), 'Finalized release prefix already exists; no duplicate replacement')
    readme = f'''# 0.6.0-dev 페이지 이동·위젯 검토 근거

{MARKER}
[최종 게시 검증](../../{REVIEW}) · [실제 Android90개 범위]({rel('scopeProof')}) · [최종 로컬 binary{final_input}]({binary_path.name}).

새 로컬 JVM{jvm}/lint오류0·경고{local_warning}, input{final_input} 고유Android90개와 실제 Main 원본{len(captures)}장·새 widget2의 Launcher2장을 구분한다. {excluded_text} [최종 Main live2]({rel('liveProof')})/[새 원본 Root검토]({rel('visualProof')})와 [새 Launcher widget2]({rel('widgetProof')})/[Root Launcher 원본 검토]({rel('widgetVisualProof')})는 TEST debug/API29/AOSP/TCG 근거다. [첫 Main 실패](live1.json)·[부분6장 검토](root-main1-partial-review.json)·[관측 helper 진단](main1-observer-diagnosis.json)은 별도 보존하며 최종 사진으로 재사용하지 않는다. [첫 위젯 미완료](widget1.json)·[프로세스 소멸 assessment](widget1-interruption-assessment.json)·[복원 전용 report](widget1-interruption-recovery.json)는 실제 scope 복원/검사0일 뿐 위젯 runtime 성공이 아니다. Main의 프로필 IME/visible frame·뒤로/저장 경계와 저장없이 Back, 코랄1회 선택 후 Back·재진입의 fresh checked=true를 확인했으며 실제 내용 변경·저장·온라인 연결은 실행하지 않았다. 지도는 세계 지도 대체·권한/연결 오류 상태 관측이며 실제 GPS·타일 연결/내용 성공이 아니다. 코랄 PNG renderer6개는 직접 Canvas 픽스처이며 실제 Main export나 사진Picker 실행이 아니다. 원래 데이터/소유 설정/새 위젯 정리와 [최종 AVD 상태 관측]({rel('sessionCleanup')})을 확인했다. {final_avd_status}

실제 공개 APK출처 `{C}`와 [서명 CI]({ci_url})·세 공개 자산·HEAD·[운영 대시보드13항목/PNG4](dashboard-production-verification.json)를 대조했다. 후속 문서·피드 커밋으로 C를 바꾸지 않는다. build1 컴파일/build2 lint 실패 원문·source-freeze1/2와 [입력3 진단](input3-diagnostic-review.json)·[별도 복원/종료](session-cleanup-input3.json)를 보존한다. 입력3의 두 parent=false·성공 그룹63/13·당시 submit좌표 미기록/원인미확정을 유지한다. [입력4 진단](input4-ime-diagnosis.json)에서는 semantic/layout 일치와 실제IME 안전하단413px보다 버튼하단764px가351px 아래임을 확인했고, [SIGINT 중단](input4-stop-reason.json)·전체성공그룹0·[별도 복원/종료](session-cleanup-input4.json)를 보존했다. 최종 제품은 API29 framework fit/resize·단일 inset 처리·원래 flags/mode 복원을 Form/공유에 적용했다. 이전0.5와 입력3/4 관측은 새90개에 합산하지 않는다. {limits} canonical raw 피드 전체 bytes는 문서/피드 병합 뒤 별도 확인한다.
'''
    plan = {R / 'docs' / REVIEW: review.encode(), E / 'README.md': readme.encode(), feed_path: encoded(dict(feed, preview=preview))}
    for name in ('AGENTS.md', 'CHANGELOG.md'):
        plan[R / name] = headline.encode() + originals[R / name]
    for name, target in (('DESIGN.md', 'docs/' + REVIEW), ('design-system/runmoni/MASTER.md', '../../docs/' + REVIEW)):
        prefix = f'# 최신 공개 — {VERSION}, {date}\n\n{MARKER} [게시 검증]({target}). 페이지 이동·뒤로가기·위젯의 실제 검증과 공개 APK출처 `{C}`를 따른다. 아래 후보와 과거 상태는 당시 이력으로 보존하며 승인 원본·데이터·접근성 기준을 유지한다. 사용자 시각 승인은 별도다.\n\n'
        plan[R / name] = prefix.encode() + originals[R / name]
    for name in ('docs/DEVELOPMENT_CONTINUATION.md', 'docs/DEVELOPMENT_PRIORITIES.md'):
        plan[R / name] = followup.encode() + originals[R / name]
    candidate = R / 'design-system/runmoni/pages/sport-pages.md'
    plan[candidate] = (f'> {MARKER} 후속 공개 상태: [{date} {VERSION}/code{CODE} 게시 검증](../../../docs/{REVIEW}). 실제 공개 C `{C}`와 input{final_input} Android90개·제한된 Main{len(captures)}장·별도 Launcher2장 근거를 따른다. 아래 후보/초기 실패·검토 중 문구는 이전 시점의 보존 기록이며 사용자 시각 승인은 별도다.\n\n'.encode() + originals[candidate])
    for name in ('publication.json', 'ci-verification.json', 'public-head-verification.json', f'runmoni-{VERSION}.metadata.json', f'SHA256SUMS-{VERSION}.txt'):
        plan[E / name] = (O / name).read_bytes()
    for name, source in (('dashboard-production-verification.json', W / 'dashboard-production.json'),
                         ('dashboard-node-tests.json', W / 'dashboard-node-tests.json'), ('dashboard-node-tests.log', W / 'dashboard-node-tests.log')):
        plan[E / name] = source.read_bytes()
    for path in plan:
        safe(path, R)
        need(path in originals or not path.exists(), 'Immutable new review/evidence already exists: ' + str(path))
    report = dict(apply=args.apply, actualApkSourceC=C, ciRun=run, freshLocalJvm=jvm, distinctPassedMethods=90,
                  countedExecutionsByProof=totals, excludedFailedGroups=scope['excludedFailedGroups'], earlierInputResultsReused=False,
                  sameInputPassedGroupsRetained=scope['sameInputPassedGroupsRetained'],
                  singleSuccessfulDefaultPhaseClaimed=scope['singleSuccessfulDefaultPhaseClaimed'],
                  unresolvedIntermittentSnapshotObserverFailure=scope['unresolvedIntermittentSnapshotObserverFailure'],
                  targetedRetryLimit=scope['retryLimit'],
                  actualMainOriginals=len(captures), actualLauncherOriginals=len(launcher), priorShareExportFlowReexecuted=False,
                  actualProfileKeyboardObserved=live['actualProfileKeyboardObserved'],
                  actualProfileClosedWithoutSaving=live['actualProfileClosedWithoutSaving'],
                  widgetRecoveryRestorationOnly=read(E / 'widget1-interruption-recovery.json')['restorationOnly'],
                  widgetRecoveryProductTestsCounted=read(E / 'widget1-interruption-recovery.json')['productTestsCounted'],
                  finalAvdObservationMethod=cleanup.get('cleanupMethod'),
                  finalAvdProcessState=cleanup['finalProcessState'], finalAvdSignal=cleanup['signal'],
                  plannedFiles={str(path.relative_to(R)): hashlib.sha256(raw).hexdigest() for path, raw in plan.items()})
    print(json.dumps(report, ensure_ascii=False, indent=2))
    if not args.apply:
        return
    need(all(path.read_bytes() == raw for path, raw in originals.items()), 'Existing docs/feed changed while validating')
    written = []
    try:
        for path, raw in plan.items():
            path.parent.mkdir(parents=True, exist_ok=True)
            if path in originals:
                written.append(path)
                path.write_bytes(raw)
            else:
                with path.open('xb') as stream:
                    written.append(path)
                    stream.write(raw)
    except BaseException:
        for path in reversed(written):
            if path in originals:
                path.write_bytes(originals[path])
            else:
                path.unlink(missing_ok=True)
        raise


if __name__ == '__main__':
    main()
