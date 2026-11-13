#!/usr/bin/env python3
"""Root-only layout-renewal docs/feed preparation after actual publication proof.

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
W = R / 'work/layout-renewal'
E = R / 'docs/app-review/2026-11-13-layout-renewal'
B = Path('/workspace/mydashboard')
VERSION, CODE, JVM = '0.5.0-dev', 27, 189  # Expected count; actual fresh local/CI evidence must both agree.
OLD_VERSION, OLD_CODE = '0.4.4-dev', 26
OLD_C = '7d921417e63d41bac5942645f2d2a50f4e1d308a'
OLDER_043_C = 'eb32a30d20b0223d436e80ca09c7c17e25d60c69'
OLDER_042_C = '176a9cbe1112efa05e9fc55d913738ee1778a57f'
ORIGINAL_015_C = '75c39ca900b140cfb02c1a47807a5955c8b8db7f'
REVIEW = 'RELEASE_REVIEW_0.5.0_20261113.md'
BASE = 'app-review/2026-11-13-layout-renewal'
MARKER = '<!-- verified-layout-renewal-release-0.5.0-v1 -->'
MAPS = ('productSources', 'testSources', 'resources', 'rendererAssets', 'approvedSourceCopies', 'configurationSources')
PREFIX = 'com.runmoni.app.ui.'
EXPECTED_GROUPS = {'android-review2.json': {'screens': (10,
                                      {'com.runmoni.app.ui.AppEntryUiTest',
                                       'com.runmoni.app.ui.HomeWeatherLayoutTest',
                                       'com.runmoni.app.ui.RunmoniDesignTest',
                                       'com.runmoni.app.ui.StudioRefreshUiTest'},
                                      False),
                          'community': (7,
                                        {'com.runmoni.app.ui.CommunityRoomsTest',
                                         'com.runmoni.app.ui.HierarchyClosetUxTest'},
                                        False),
                          'account-information': (8,
                                                  {'com.runmoni.app.ui.AppInformationTest#aboutShowsInstalledVersionHistoryLinksLicensesAndHonestFailure',
                                                   'com.runmoni.app.ui.AppInformationTest#fullScreenChatKeepsCrewSelectionAndHidesBackgroundControls',
                                                   'com.runmoni.app.ui.AppInformationTest#topVersionOpensConfirmationAndDownloadsOnlyAfterExplicitChoice',
                                                   'com.runmoni.app.ui.AppVersionShortcutLayoutTest',
                                                   'com.runmoni.app.ui.DeviceChecksTest#anonymousRoomDisconnectRequiresExplicitConfirmation',
                                                   'com.runmoni.app.ui.DeviceChecksTest#missingConfigurationAndManualConfirmationStayDistinct',
                                                   'com.runmoni.app.ui.UnifiedProfileUiTest#failedConnectionKeepsTheNicknameAndWalletAndClosesOnlyForItsConnectionAcknowledgement',
                                                   'com.runmoni.app.ui.UnifiedProfileUiTest#onlineSaveRequiresMatchingNameUidAndAnAvailableOrOwnedObservation'},
                                                  False),
                          'closet': (5,
                                     {'com.runmoni.app.ui.ClosetNavigationUiTest',
                                      'com.runmoni.app.ui.ConsumerClosetTest',
                                      'com.runmoni.app.ui.RunningKitColorsUiTest#closetColorChangesKeepReadyItemSelectionsInventoryAndGender',
                                      'com.runmoni.app.ui.RunningKitColorsUiTest#namedRadioChoicesWrapWithoutShrinkingAcrossSmallAndLargeTextViewports'},
                                     False),
                          'record-share': (11,
                                           {'com.runmoni.app.ui.RunRecordNavigationUiTest',
                                            'com.runmoni.app.ui.RunRecordScreenUiTest',
                                            'com.runmoni.app.ui.RunShareControllerTest',
                                            'com.runmoni.app.ui.RunShareUiTest'},
                                           False),
                          'map-modal': (15,
                                        {'com.runmoni.app.ui.ModalDismissUiTest',
                                         'com.runmoni.app.ui.PopupNavigationUiTest',
                                         'com.runmoni.app.ui.RouteTileMapUiTest',
                                         'com.runmoni.app.ui.RunnerDiscoveryUiTest'},
                                        False)},
 'android-review3.json': {'closet': (1, {'com.runmoni.app.ui.ConsumerClosetTest'}, False),
                          'colors': (2,
                                     {'com.runmoni.app.ui.RunningKitColorsUiTest#closetColorChangesKeepReadyItemSelectionsInventoryAndGender',
                                      'com.runmoni.app.ui.RunningKitColorsUiTest#namedRadioChoicesWrapWithoutShrinkingAcrossSmallAndLargeTextViewports'},
                                     False),
                          'closet-navigation': (2, {'com.runmoni.app.ui.ClosetNavigationUiTest'}, False)},
 'native1.json': {'native-large-text': (12,
                                        {'com.runmoni.app.ui.AppEntryNativeUiTest',
                                         'com.runmoni.app.ui.AppInformationNativeTest',
                                         'com.runmoni.app.ui.ClosetNavigationNativeUiTest',
                                         'com.runmoni.app.ui.HierarchyClosetLargeTextUxTest',
                                         'com.runmoni.app.ui.RunRecordScreenNativeUiTest',
                                         'com.runmoni.app.ui.RunShareImeUiTest#realKeyboardKeepsCloseAndScrollableExportActionsOutsideImeAndSystemBars',
                                         'com.runmoni.app.ui.RunmoniDialogDesignTest',
                                         'com.runmoni.app.ui.RunningKitColorsNativeUiTest'},
                                        True),
                  'native-share-font1': (1,
                                         {'com.runmoni.app.ui.RunShareImeUiTest#normalTextStoryCaptionStaysVisibleAndAcceptsRealKeysAcrossImeResize'},
                                         False)}}
COUNTED_GROUPS = {
    'android-review2.json': ('screens', 'community', 'account-information', 'record-share', 'map-modal'),
    'android-review3.json': ('closet', 'colors', 'closet-navigation'),
    'native1.json': ('native-large-text', 'native-share-font1'),
}
EXCLUDED_FAILED_GROUPS = ({'proof': 'android-review2.json', 'group': 'closet'},)


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
    need(path.is_relative_to(E.resolve()) and path.is_file(), 'Evidence must exist inside the layout-renewal review directory')
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


def verify_earlier_input():
    """Keep the source2 failure and root stop immutable, outside the fresh69."""
    old = read(ref('android-review1.json'))
    previous = read(ref('binary-build2.json'))
    first = read(ref('binary-build1.json'))
    for binary, freeze in ((first, 'source-freeze1.json'), (previous, 'source-freeze2.json')):
        need((binary['version'], binary['versionCode'], binary['sourceFreezeReference']) == (VERSION, CODE, freeze)
             and read(ref(freeze))['sourceMaps'] == {key: binary[key] for key in MAPS},
             'Original input1/2 freeze must remain independently bound')
    need((old['version'], old['versionCode']) == (VERSION, CODE)
         and old['completed'] is True and old['passed'] is False
         and old['phase'] == 'complete-failed' and old['failureType'] == 'KeyboardInterrupt'
         and old['actualPassedProductMethods'] == 27
         and read(ref({'file': old['binaryReference'], 'sha256': old['binaryReferenceSha256']})) == previous,
         'Keep actual source2 parent failure/root interruption and27 partial passes')
    installed_pair(old, previous)
    true_fields(old, ('preferencesRestoredExactly', 'profileRestoredExactly',
                     'communityAccountPreferencesRestoredExactly', 'controlledSettingsPermissionsPowerRestoredExactly',
                     'driverSourceUnchanged', 'observerHelperSourceUnchanged', 'binaryEvidenceUnchanged'),
                'Original source2 finally/restoration must be complete before new source verification')
    for field in ('sourceMapsBefore', 'sourceMapsAfterRestoration'):
        observed_maps(old[field], previous, 'Original source2/' + field)
    need('sourceMapsAfter' not in old,
         'Do not invent an all-groups-completed source snapshot for the interrupted source2 attempt')
    passed = [group for group in old['groups'] if group.get('passed') is True]
    need(sum(instrumentation_group(old, group['name'])['actual'] for group in passed) == 27,
         'Retained source2 raw passes must stay27 and cannot supply final69')
    failed = [group for group in old['groups'] if group['name'] == 'closet']
    need(len(failed) == 1 and failed[0]['passed'] is False and failed[0]['expected'] == 1,
         'Actual ConsumerClosetTest failure must remain failed')
    raw_ref = {'file': failed[0]['raw'], 'sha256': failed[0]['rawSha256']}
    raw = ref(raw_ref).read_text()
    need(all(value in raw for value in ('ConsumerClosetTest', 'java.lang.IllegalStateException',
             'Method removeObserver must be called on the main thread', 'HomeConditionsKt$rememberHomeClock'))
         and 'Tests run: 1,  Failures: 1' in raw,
         'Preserve the observed HomeClock off-main observer-disposal failure')
    stop = read(ref('input2-stop-reason.json'))
    need(stop['signalToExactCollector'] == 'SIGINT' and stop['notAProductPass'] is True
         and stop['sourceMutationBeforeRestoration'] is False and stop['failureRaw'] == failed[0]['raw'],
         'Root stop and restoration-before-source-change boundary must remain explicit')


def verify_scope(scope, ci):
    verify_earlier_input()
    need(scope['finalLocalBinary'] == 'binary-build3.json', 'Final layout-renewal local binary differs')
    if 'version' in scope:
        need(scope['version'] == VERSION, 'Scope version differs')
    if 'versionCode' in scope:
        need(scope['versionCode'] == CODE, 'Scope version code differs')
    need(scope['finalLocalJvm'] == ci['jvm'] == dict(tests=JVM, failures=0, errors=0, skipped=0), 'Measured fresh JVM result differs')
    need(scope['finalLocalLint']['Error'] == scope['finalLocalLint']['Fatal'] == 0, 'Local lint failed')
    need(scope['freshFinalAndroid']['total'] == scope['distinctPassedMethods'] == 69
         and scope['earlierResultsReused'] is False and scope['earlierInputResultsReused'] is False
         and scope['sameInputPassedGroupsRetained'] is True
         and scope['singleSuccessfulDefaultPhaseClaimed'] is False,
         'Retain same-input51 plus split closet5 plus native13 without counting earlier-input results or a whole default PASS')
    names = scope['freshFinalAndroid']['proofs']
    need(len(names) == 3 and set(names) == set(EXPECTED_GROUPS), 'Same-input51/split-closet5/native13 proof selection differs')
    included = [{'proof': proof, 'group': name} for proof, groups in COUNTED_GROUPS.items() for name in groups]
    need(scope['includedGroups'] == included and scope['excludedFailedGroups'] == list(EXCLUDED_FAILED_GROUPS)
         and scope['countedExecutionsByProof'] == {'android-review2.json': 51, 'android-review3.json': 5, 'native1.json': 13},
         'Explicit counted and excluded groups must preserve the failed source3 parent')
    need(set(scope['proofReferences']) == set(names), 'Each final method proof needs its immutable actual reference')
    for name in names:
        need(set(scope['proofReferences'][name]) == {'file', 'sha256'}, 'Method proof bytes must be explicitly bound')
        need(ref(scope['proofReferences'][name]) == ref(name), 'Method proof reference differs')
    for key in ('physicalDeviceVerified', 'googleLoginVerified', 'signedReleaseRuntimeVerified',
                'socialAppDeliveryVerified', 'productionWritesVerified'):
        if key in scope:
            need(scope[key] is False, 'Scope runtime boundary changed: ' + key)
    binary = read(ref(scope['finalLocalBinary']))
    need((binary['version'], binary['versionCode'], binary['sourceFreezeReference']) == (VERSION, CODE, 'source-freeze3.json') and
         binary['buildSucceeded'] is True and binary['jvmReportsFresh'] is True and binary['jvm'] == ci['jvm'] and
         binary['lint'] == scope['finalLocalLint'] and binary['signing'] == 'local TEST debug, not retained release signing',
         'Final fresh TEST debug build differs')
    need(read(ref(binary['sourceFreezeReference']))['sourceMaps'] == {key: binary[key] for key in MAPS}, 'Final source-freeze3 differs')
    build_raw = ref(binary['buildLog']).read_text()
    need('BUILD SUCCESSFUL' in build_raw and 'BUILD FAILED' not in build_raw and
         re.search(r'(?m)^> Task :app:testDebugUnitTest\s*$', build_raw) is not None and
         re.search(r'(?m)^> Task :app:testDebugUnitTest (?:UP-TO-DATE|FROM-CACHE)\s*$', build_raw) is None,
         'Fresh local JVM execution must exist in the actual binary-build3 log')
    need(binary['approvedSourceCopies'] == {
        'docs/design/avatars/v0.3.0/running-motion-art-source.png':
        'f9035f29ced0c7ba513dfe24cb727410a7204dc482fc457e806c7a9b3cdbfa65'}, 'Approved original artwork changed')
    classes = frozen_test_methods(binary)
    methods = set()
    failed_closet_methods, replacement_closet_methods = set(), set()
    for name in names:
        proof = read(ref(name))
        true_fields(proof, ('completed', 'preferencesRestoredExactly', 'profileRestoredExactly',
                           'communityAccountPreferencesRestoredExactly', 'controlledSettingsPermissionsPowerRestoredExactly',
                           'driverSourceUnchanged', 'observerHelperSourceUnchanged', 'binaryEvidenceUnchanged',
                           'independentPhaseNoPriorResultAggregation'),
                    'Fresh phase/restoration/source verification incomplete: ' + name)
        if name == 'android-review2.json':
            need(proof['passed'] is False and proof['phase'] == 'complete-failed'
                 and proof['failureType'] == 'AssertionError'
                 and proof['failure'] == 'Every method in this independent fresh phase passed'
                 and proof['plannedProductMethods'] == 56 and proof['actualPassedProductMethods'] == 51,
                 'The source3 batch parent must remain whole-phase failed with only51 counted passes')
        else:
            need(proof['passed'] is True and proof['phase'] == 'complete-passed'
                 and proof['plannedProductMethods'] == (13 if name == 'native1.json' else 5),
                 'Independent closet5/native13 proof must actually pass completely')
        false_fields(proof, ('physicalDeviceVerified', 'googleLoginVerified', 'productionWritesVerified',
                            'android31RuntimeVerified', 'signedReleaseRuntimeVerified'), 'Formal runtime boundary changed')
        need((proof['version'], proof['versionCode'], proof['api']) == (VERSION, CODE, 29), 'Formal identity/API differs')
        need(proof['reviewPhase'] == ('native' if name == 'native1.json' else 'default'), 'Formal phase differs')
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
        for group_name, (count, selectors, native) in expected.items():
            group = next(group for group in proof['groups'] if group['name'] == group_name)
            need(group['expected'] == count and len(group['classes']) == len(selectors) and set(group['classes']) == selectors and
                 group['originalProfilePreserved'] is True, 'Actual group selectors/count/profile preservation differ')
            need(group['viewport']['native320Font2RequiredByClass'] is native, 'Actual native/default viewport observation differs')
            if native:
                need(float(group['viewport']['fontScale']) >= 1.9, 'Actual native large-text font scale required')
            else:
                need(.99 <= float(group['viewport']['fontScale']) <= 1.01,
                     'Actual default/font1 viewport must not be described as native320/font2')
            ids = []
            for selector in group['classes']:
                clazz, *specific = selector.split('#')
                need(clazz in classes, 'Executed class is not in the actual frozen test blobs')
                selected = specific or classes[clazz]
                need(all(method in classes[clazz] for method in selected), 'Selected method is not in its frozen class')
                ids.extend(clazz + '#' + method for method in selected)
            need(len(ids) == len(set(ids)) == count, 'Frozen selected method IDs/count differ')
            if name == 'android-review2.json' and group_name == 'closet':
                need(group_name not in COUNTED_GROUPS[name] and group['passed'] is False
                     and group['status'] == 'failed' and group['actual'] is None,
                     'Do not promote four passes inside the failed five-method batch into final coverage')
                raw = ref({'file': group['raw'], 'sha256': group['rawSha256']}).read_text()
                need(all(token in raw for token in ('ConsumerClosetTest:.', 'ClosetNavigationUiTest:..',
                     'RunningKitColorsUiTest.kt:181', 'ComposeTimeoutException',
                     'Condition still not satisfied after 30000 ms', 'FAILURES!!!', 'Tests run: 5,  Failures: 1'))
                     and not any(token in raw for token in ('INSTRUMENTATION_FAILED', 'Process crashed', 'IllegalStateException')),
                     'Retain the actual30-second semantics timeout, without inventing another renderer/lifecycle failure')
                failed_closet_methods = set(ids)
                continue
            need(group_name in COUNTED_GROUPS[name] and not methods.intersection(ids), 'Count only the explicit distinct successful groups')
            instrumentation_group(proof, group_name)
            need(group['actual'] == count, 'Actual successful group count differs')
            methods.update(ids)
            if name == 'android-review3.json':
                replacement_closet_methods.update(ids)
            phase_total += count
        need(proof['actualPassedProductMethods'] == phase_total == scope['countedExecutionsByProof'][name], 'Measured counted phase total differs')
    need(len(methods) == 69 and len(failed_closet_methods) == 5
         and failed_closet_methods == replacement_closet_methods,
         'Final51+5+13 distinct union must replace exactly the failed closet batch with the same methods')
    if 'methods' in scope:
        reported = [row if isinstance(row, str) else row['method'] for row in scope['methods']]
        need(len(reported) == 69 and set(reported) == methods, 'Scope method list differs from the executed frozen selectors')
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
    # Earlier photo/export execution is not rerun by this limited Main path.
    for key in ('actualSystemPhotoPickerUsed', 'actualCaptionKeyboardObserved', 'actualMiniMiMovementActionObserved',
                'actualGallerySaveAcknowledgmentObserved', 'galleryCacheReceiverPngBytesExactlyEqual'):
        if key in live:
            need(live[key] is False, 'Do not promote a prior export operation into this changed-UI execution: ' + key)
    rows = live['captures']
    need(len(rows) == len({row['file'] for row in rows}) == 5, 'Five distinct actual Main originals required')
    need({row['file'] for row in rows} == {live['name'] + '-main-' + suffix + '.png' for suffix in
         ('studio-home', 'studio-closet', 'record-result', 'default-card', 'map-clean')},
         'Five limited Main scenes must be home/closet/record-result/default-card/map-clean')
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
    attempt = read(ref('ci-attempt1-failure.json'))
    need(attempt['ciRun'] == release['previousFailedRun'] == 43414902441 and
         attempt['sourceRevision'] == release['previousSignedSource'] == '9a4ef0eba5b0a981b880dfa81b296e96f80b9699' and
         attempt['overallConclusion'] == 'failure' and attempt['signedBuildStepSucceeded'] is True and
         attempt['temporaryInputCleanupStepSucceeded'] is True and attempt['apkFromThisAttemptPublished'] is False and
         attempt['missingAtActualSourceVerified'] is True and attempt['taggedReleaseAbsentAfterFailure'] is True,
         'Preserve failed first signing source and missing-note publication failure')
    need(C != attempt['sourceRevision'] and feature['firstSigningAttemptSource'] == attempt['sourceRevision'] and
         feature['implementationUrl'] == release['implementationPullRequest'] == 'https://github.com/wjdals988/runmoni/pull/53' and
         feature['url'] == release['releaseNotesPullRequest'] == 'https://github.com/wjdals988/runmoni/pull/54',
         'Fresh rebuilt APK source must follow the actual release-note supplement, not rewrite the first APK')
    implementation_url = feature['implementationUrl']
    need(re.fullmatch(r'[0-9a-f]{40}', C) is not None and C not in
         {OLD_C, OLDER_043_C, OLDER_042_C, ORIGINAL_015_C} and type(run) is int and run > 0,
         'Actual new source C/CI run required; old publication provenance cannot be reused')
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
    need(len(visual['images']) == 5, 'Root must review five originals')
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
    cleanup_status = {'android-review2.json': False, 'android-review3.json': True, 'native1.json': True, paths['liveProof'].name: True}
    need(len(cleanup_refs) == 4 and set(cleanup_refs) == set(cleanup_status), 'Cleanup restoration proof set must retain the failed batch parent too')
    for name, passed in cleanup_status.items():
        row = cleanup_refs[name]
        parent = read(ref(row))
        need(row['completed'] is True and row['originalParentPassed'] is passed
             and parent['completed'] is True and parent['passed'] is passed,
             'Cleanup must not rewrite the original parent status: ' + name)

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
    need(baseline['oldReleaseNoteCount'] == len(old['releaseNotes']) == 19 and
         (old['apk']['version'], old['apk']['versionCode']) == (OLD_VERSION, OLD_CODE) and
         project['releaseNotes'][1:] == old['releaseNotes'] and
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
    need(feed['schemaVersion'] == 1 and feed['stable'] is None and
         (feed['preview']['version'], feed['preview']['versionCode']) == (OLD_VERSION, OLD_CODE) and
         feed['preview']['sourceRevision'] == OLD_C and feed['preview']['certificateSha256'] == meta['certificateSha256'],
         'Keep verified old preview26/stable null until this update')

    date = dt.datetime.fromisoformat(pub['publishedAt'].replace('Z', '+00:00')).astimezone(ZoneInfo('Asia/Seoul')).date().isoformat()
    need(project['updatedAt'] == project['releaseNotes'][0]['date'] == date.replace('-', '.'), 'Dashboard publication date differs from actual release time')
    contrast = read(ref('contrast.json'))
    need(len(contrast['endpointPairs']) >= 8 and contrast['allPixelsOrAnimationFramesMeasured'] is False,
         'Keep static endpoint checks separate from whole-pixel/motion verification')
    theme_path = R / 'app/src/main/java/com/runmoni/app/ui/RunmoniTheme.kt'
    need(digest(theme_path.read_bytes()) == binary['productSources'][str(theme_path.relative_to(R))], 'Theme source differs from frozen input')
    colors = dict(re.findall(r'val (\w+) = Color\(0xFF([0-9A-Fa-f]{6})\)', theme_path.read_text()))
    expected_pairs = {('#' + colors[front], '#' + colors[back]) for front in ('Primary', 'Muted') for back in ('Paper', 'Cream', 'MintWash')}
    expected_pairs |= {('#' + colors['RouteBlue'], '#' + colors[back]) for back in ('Paper', 'Cream')}
    actual_pairs = {(row['foreground'], row['background']) for row in contrast['endpointPairs']}
    need(expected_pairs.issubset(actual_pairs) and len(actual_pairs) == len(contrast['endpointPairs']),
         'Required endpoint roles must match the frozen theme, including plain profile RouteBlue')
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
    contrast_count = len(contrast['endpointPairs'])
    review = f"""# Runmoni0.5.0-dev 화면 구성 개편 게시 검증

frontend-design/ui-ux-pro-max와 기존 사용자 피드백에 따라 홈의 출발 장면과 독립 러닝 행동, 러닝의 수치/안내/경로, 프로필의 캐릭터 관리와 기간별 기록, 옷장 선택 작업영역, 크루/소속 파티, 기록 상세와 공유 편집의 구성을 재정리했다. 색만 일괄 교체하거나 각 화면을 동일한 카드 목록으로 만들지 않는다. 승인 원본과 실제 데이터/저장/권한/계정 경계는 보존한다. [구성 계획/자체 비평](../design-system/runmoni/pages/layout-renewal.md).

## 공개 APK와 출처

- {date} 한국 시간,같은 보관키의 실기기 검증 전 개발 prerelease{VERSION}/code{CODE}. [APK]({url}) · [릴리스]({pub['releaseUrl']}) · [구현 PR]({implementation_url}) · [릴리스 노트 보완 PR]({feature['url']}).
- 실제 APK출처 C {C}. 크기 {meta['sizeBytes']:,}bytes,SHA256 {meta['sha256']},인증서 SHA256 {meta['certificateSha256']}. 후속 문서/피드 커밋 D로 C를 바꾸지 않는다.
- [서명 CI]({ci_url}) 새 JVM{JVM}(실패/오류/skip0),release lint오류0·경고{ci_warning}. non-debuggable/패키지·버전·내장 C/보관 인증서·연결 설정·임시 파일 정리와 공개3자산/sidecar/digest·HEAD200/크기를 확인했다. readiness는 설정 확인이며 휴대폰·Google·운영 쓰기 성공이 아니다. Root가 공개 APK 본문을 다시 다운로드하거나 실행했다고 쓰지 않는다.

첫 서명 CI43414902441/출처9a4ef0eba5b0a981b880dfa81b296e96f80b9699는 빌드·서명과 임시 설정 정리를 마쳤으나 필수 RELEASE_NOTES_0.5.0-dev.md 누락으로 초안 업로드 전에 실패했다. [첫 실패 기록]({BASE}/ci-attempt1-failure.json)을 보존한다. PR54에서 문서만 보완하고 앱/검사/리소스/구성의 전체 입력 일치를 확인한 뒤 새 C에서 다시 빌드·서명했다. 이전 서명 APK의 출처를 새 C로 수정한 것이 아니다.

## 최종 로컬 실행 범위

- 최종 입력 [{scope['finalLocalBinary']}]({BASE}/{scope['finalLocalBinary']}) / source-freeze3.json: 새 JVM{JVM}·실패/오류/skip0,lint오류0·경고{local_warning}. 초기 입력1/2와 그 원문은 보존하고 새 실행에 더하지 않는다. 빌드 당시 HEAD {binary['sourceCommitAtBuild']}를 공개 APK출처 C로 바꾸지 않으며,[실제 전체 입력 SHA와 C 대조]({BASE}/{relative(paths['sourceComparison'])})를 별도로 확인했다. TEST debug의 로컬 검토 CA/Firebase 비활성과 connected release 설정은 다르다.
- [Android 범위]({BASE}/{relative(paths['scopeProof'])}): 최종 같은 입력3 APK쌍의 android-review2 정상5그룹51개를 유지하고,android-review3에서 옷장5개를 독립 그룹으로 재검사한 뒤 native1의13개를 더해 고유 메서드69개를 확인했다. android-review2는 옷장 묶음 실패로 전체 passed=false이며 정상51개만 채택했다. 실패한5개 묶음 속 일부 통과는 채택하지 않았다. android-review3의 Consumer1/Colors2/Navigation2는 각각 별도 instrumentation 프로세스이며 같은5개 method를 대체한다. 일반56개가 단일 phase에서 모두 통과했다고 쓰지 않는다.
- 범위 JSON은 includedGroups/excludedFailedGroups,각 원문 SHA와 source3 동일 입력,sameInputPassedGroupsRetained=true/earlierInputResultsReused=false를 명시한다. frozen FQCN/method와 selector·원문 OK개수·설치쌍·전/후/복원후6맵을 대조해51+5+13의 중복 없는69개임을 확인했다. 입력2의27부분 통과·이전0.4.4의16개/0.4.3의70개·Launcher는 합산하지 않았다. native13 중12개는 실제320dp/systemfont2이고1개는 실제480dp/font1의 긴 공유 문구/IME 검사이며,13개 전부를320/font2로 표시하지 않는다.
- 이69개는 ComponentActivity/in-memory/inert-map fixture다. 일반의 가상 viewport와 실제 OS 설정을 사용하는 native 검사를 구분한다. 선택한 정상 그룹·분리 재검사·native proof가 세 system animation scale을 실제로 읽어0임을 확인하고 그 설정에서 조작/레이아웃을 검사했다. 전체 페이지 Main 촬영·휴대폰·Google/운영 백엔드 검증이 아니다. [정적 endpoint 대비]({BASE}/contrast.json)는 frozen 색 역할{contrast_count}조합을 확인했으며,필수 Primary/Muted의 Paper/Cream/MintWash와 RouteBlue의 Paper/Cream 조합을 포함한다. 전체 픽셀/지도 배경/중간 애니메이션 프레임/실제150ms 시간이나 모든 모션 배율을 측정한 것은 아니다.
- [실제 Main 단일 PASS]({BASE}/{relative(paths['liveProof'])}): nonce 합성 완료 기록으로 홈·옷장·기록 결과·기본1:1 공유 편집·지도5화면을 관측했다. 이 제한된5화면을 전체 페이지 실제 Main 검증이라고 표시하지 않는다. Main에서도 세 system animation scale의 실제0을 읽고,옷장 하의→상의 탭의 selected 상태와 기존 기록/미니미/배경 도구 탭을 탭했다. 도구 탭의 selected 속성을 관측했다고 하지 않는다. 기록으로 Back·재진입 뒤 기본 draft 설명 일치와 지도 밖 조작을 확인했다. 승인 sprite/기본1:1·기록/지갑·위치 정책을 유지한다.
- 이번 Main은 변경된 구성의 일부와 기본 공유 진입/왕복에 한정했다. 합성 사진 fixture는 준비/정리했으나 사진Picker·IME 문구 입력·사진 이동·갤러리 저장·공유창·별도 UID receiver/PNG export를 재실행하지 않았다(priorShareExportFlowReexecuted=false). 앞서 완료한 export는 [0.4.3 게시 검증](RELEASE_REVIEW_0.4.3_20261112.md)의 이전 입력 이력이며 이번 새 Main 성공으로 합산하지 않는다. native의 별도 UI/IME fixture가 실제 Main의 사진/export 실행을 대신하지 않는다. 실제 SNS 전달도 별도다.
- [Root 원본 검토]({BASE}/{relative(paths['visualProof'])}): 실제 Main 원본5장(홈/옷장/기록 결과/기본1:1/지도)을 열어 내용과 개인정보를 확인했다. 운영 갤러리4장은 이 중 선택한 원본이며 사용자 시각 승인·공개 서명 APK 실행이나 나머지 페이지의 실제 Main 검증은 아니다.
- 실패한 android-review2를 포함한 각 formal proof의 원래 XML/프로필/account·소유 설정/권한/전원과 실제 전/후/복원후6맵·설치 APK쌍을 확인했다. Main의 원래 DB행/전체XML/프로필/account·소유 환경을 복원하고 nonce fixture를 정리했다. [정확한 소유 AVD 종료]({BASE}/{relative(paths['sessionCleanup'])})는 android-review2의 원래 false와 android-review3/native/Main의 true를 보존한4개 복원 근거를 따른다. 전체 OS 파일/원래 GPS fix/picker 캐시 전체 복원을 주장하지 않는다.

## 보존한 이전 실패와 중단

[입력2 android-review1 원문]({BASE}/android-review1.json)은 부분 통과27개 뒤 전체 passed=false/KeyboardInterrupt로 종료했다. 그중 [ConsumerClosetTest 실패]({BASE}/android-review1-closet.txt)는 ComponentActivity fixture에서 HomeClock의 onDispose가 removeObserver를 Main 스레드 밖에서 호출해 IllegalStateException이 발생한 제품코드의 수명 정리 위반을 보였다. 실제 Main/휴대폰에서 같은 예외를 관측했다고 하지 않는다. [Root 중단 이유]({BASE}/input2-stop-reason.json)에 따라 정확한 collector에 SIGINT를 보내고 finally의 원래 XML/프로필/account·소유 환경과 복원후6맵을 확인한 뒤 소스를 바꿨다. 중단된 시도에 전체 그룹 종료후 sourceMapsAfter가 있다고 만들지 않는다.

이후 시계/수신기 수명을 Main.immediate와 STARTED의 repeatOnLifecycle에 묶은 입력3으로 새 빌드했다. 입력1/2·27개 부분 통과·실패한 Consumer 검사와 Root 중단은 최종 고유69개에 더하거나 성공으로 고치지 않았다. 새 검사가 모든 생명주기/경합 가능성을 측정했다는 뜻은 아니다.

입력3의 [android-review2 옷장 묶음]({BASE}/android-review2-closet.txt)에서는 Consumer의 원래 Clock 경로는 통과했지만,RunningKitColorsUiTest의 L181에서 미니미 Canvas semantics 노드를30초 내 찾지 못해5실행/1실패였다. renderer 예외나 계정 변경을 이 원문에서 관측한 것은 아니며 묶음/캐시 등 원인은 미확정이다. 제품과30초 predicate를 바꾸지 않고 같은 APK에서 [Consumer]({BASE}/android-review3-closet.txt)·[Colors]({BASE}/android-review3-colors.txt)·[Navigation]({BASE}/android-review3-closet-navigation.txt)을 분리 실행해5개 통과를 확인했다. 분리 조건에서의 결과가 원인을 확정하거나 원래 묶음 실패를 없애지는 않는다. 전체 실패인 android-review2 원문과 그중 정상51개 유지/실패묶음 제외를 명시적으로 보존한다.

## 운영 대시보드와 남은 확인

[운영 대시보드](https://coldbrewventi.vercel.app/projects/runmoni) · [대시보드 PR]({deployment['pullRequest']}) · [main CI]({deployment['mainCi']['url']}) · Production {deployment['productionDeployment']['id']} · main {deployment['mergeRevision']}. canonical13항목과 검토한 원본 PNG4장의 운영 bytes,Node{node['tests']}개를 확인했다. 다른{baseline['otherProjectCount']}프로젝트·이전19개 내역·이전{baseline['oldScreenshotCount']}개 PNG를 보존했다. [운영 근거]({BASE}/dashboard-production-verification.json).

preview27/stable null은 이 문서/피드 변경으로 준비하며 canonical raw 전체 bytes는 문서 PR 병합 뒤 별도 확인한다.0.4.4 실제 APK출처 {OLD_C},0.4.3 {OLDER_043_C},0.4.2 {OLDER_042_C},0.1.5 {ORIGINAL_015_C}를 후속 커밋으로 바꾸지 않는다.

승인 원본/58ID/13슬롯/9탭/0P·기록/지갑/보유권·100m 동의/FGS/runId·프로필ACK·export/lifecycle/IME/삭제 확인은 유지했다. 새 원화·키·Rules·의존성을 추가하지 않았다. 휴대폰/Google/운영 Firestore/네이버 인증·야외GPS/장시간BG·Doze/OEM/API31+·SNS전달·사용자 시각 승인·공개 서명 APK 런타임과 실제150ms 시간/모든 중간 프레임·다른 배율 검증은 미확인이다. 다음은 같은 키 휴대폰 업데이트/기존 데이터 보존과 실제 행동 확인,나머지 실제 Main 화면 및 큰 글씨/가로/IME·각 오류 상태의 지속 검토다.
"""
    headline = f"""# {VERSION} 화면 구성 개편 공개 — {date} 한국 시간

{MARKER}
- 홈 출발/러닝 행동·현재 수치/경로·캐릭터/기간 기록·옷장 작업영역·소속 파티·기록/공유 편집의 구성을 개편했다. 같은 보관키/code{CODE},실제 APK출처 {C},{meta['sizeBytes']:,}bytes,SHA256 {meta['sha256']}. 서명 CI{run} JVM{JVM}/lint0·경고{ci_warning},운영13항목/PNG4/Node{node['tests']}개를 확인했다.
- [게시 검증](docs/{REVIEW}). 최종 binary3 새JVM{JVM}/lint0·경고{local_warning},동일입력 정상51+분리옷장5+native13=고유69와 실제Main 단일PASS/원본5·갤러리선택4·원래데이터/소유환경복원·AVD종료를 구분한다. ar2는 전체false로 유지하며 일반56 단일phasePASS를 주장하지 않는다. native13에는320/font2 12건과480/font1 IME1건이 포함된다. 입력1/2·27부분통과/Consumer의 HomeClock Main스레드 예외/Root중단은 원문으로 보존하고69에 합산하지 않는다. 입력3의30초semantics대기 실패와 원인미확정도 남기고 같은APK 분리재검사5의 결과만 대체했다. 이전44의16/43의70/Launcher/사진Picker·갤러리·receiver export는 이번 새 Main 결과가 아니다. 휴대폰/Google/SNS/네이버운영·사용자승인·공개서명APK런타임과 실제150ms시간/모든프레임·다른모션배율은 별도다. canonical raw피드는 후속 병합 뒤 확인한다.

"""
    continuation = f"""# 최신 이어가기 — {VERSION}/code{CODE} 공개, {date}

{MARKER}
[게시 검증]({REVIEW})과 [화면 구성 계획](../design-system/runmoni/pages/layout-renewal.md)을 시작점으로 삼는다. 실제 APK출처 {C}를 후속 문서/피드 커밋으로 바꾸지 않는다. 같은 키 서명 CI{run}·공개3자산·운영 대시보드13항목/PNG4·Node{node['tests']}개를 확인했다.

최종 binary3의 동일입력 정상51+독립옷장5+native13=고유69,실제 Main 단일PASS/원본5(홈/옷장/기록 결과/기본1:1/지도)와 이 중 갤러리4장을 구분한다. ar2전체false/옷장5묶음제외·동일입력정상51유지·ar3분리5PASS를 그대로 기록하며 단일일반56PASS라고 하지 않는다. 원인미확정인30초semantics대기는 제품/predicate 변경 없이 분리 조건으로 확인했을 뿐이다. native13은320/font2 12건+480/font1 IME1건이며 모든 실제 페이지/모든 OS 조합을 검증한 것은 아니다. 입력2의27부분통과·Consumer HomeClock 예외·Root중단은 원래 실패/복원 기록으로 보존하고 입력3 고유69에 더하지 않는다. 이전44의16/43의70/Launcher·실제 사진Picker·갤러리/별도UID export도 이전 이력이다. 이번 Main은 사진/export를 재실행하지 않았다. 원래DB행/XML/프로필/account·소유환경을 복원하고 정확한AVD를 종료했다. 과거0.4.4/0.4.3/0.4.2/0.1.5의 실제 APK출처를 보존한다.

1. 휴대폰 같은 키 업데이트 뒤 기존 기록/아이템,홈 시작·활성 수치/고정 제어·기간 기록·옷장 마지막 품목/색·크루 소속/파티·Back을 확인한다. 실제 사용과 공유 대상 확인을 fixture/chooser 관측으로 대신하지 않는다.
2. 이번 Main5화면 밖의 실제 페이지와 긴 이름/큰 글씨·가로/IME,캐시있는 실패/없는 실패·빈 상태를 계속 확인한다. 새 구성과 기존 저장/삭제·동의/계정 경계를 함께 본다.
3. 사진Picker·caption·사진/미니미 편집·갤러리/chooser·PNG 동일성은 새 입력에서 실제 실행할 때만 새 결과로 기록한다. native IME fixture와 실제 Main export/SNS 전달을 합치지 않는다.
4. 휴대폰/Google·운영 프로필·닉네임 예약·네이버 연결,알림/위젯·야외GPS/장시간BG·절전/OEM/API31+와 사용자 시각 승인을 각각 확인한다. 기존 같은 Rules 재게시/키 재발급을 반복 요구하지 않는다.
5. 승인 원본/58ID/13슬롯/9탭·0P/보유권·포인트와 순차heavy·캐시우선/오프라인작업자1·클라우드전용AVD/종료 확인을 유지한다. 정적 대비/system0 관찰을 모든 픽셀·실제150ms시간/모든 프레임·다른 배율 검증으로 바꾸지 않는다.

아래는 이전 개발 시점의 보존 기록이다.

"""
    preview = {key: meta[key] for key in ('version', 'versionCode', 'fileName', 'sizeBytes', 'sha256', 'certificateSha256', 'sourceRevision', 'signed')}
    preview.update(package=meta['packageName'], buildType='release', physicalDeviceTests=0, downloadUrl=url,
                   releaseUrl=pub['releaseUrl'], notes='홈·러닝·기록·옷장·크루 화면 구성을 개편한 실기기 검증 전 개발 프리뷰입니다.')
    candidate = R / 'design-system/runmoni/pages/layout-renewal.md'
    existing = [R / path for path in ('AGENTS.md', 'CHANGELOG.md', 'DESIGN.md', 'design-system/runmoni/MASTER.md', 'updates/android.json', 'docs/DEVELOPMENT_CONTINUATION.md', 'docs/DEVELOPMENT_PRIORITIES.md')] + [candidate]
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
    history = f'\n- {date} 공개:{VERSION}/code{CODE} 화면 구성 전면 개편. {MARKER} 실제 APK출처 {C}를 후속 문서/피드로 바꾸지 않는다. [게시 근거](docs/{REVIEW}). 같은키 서명 CI{run} JVM{JVM}/lint0·최종binary3 새{JVM}/동일입력정상51+분리옷장5+native13=고유69·실제Main단일PASS/제한된5화면·운영13+PNG4를 구분한다. native13은320/font2 12건+480/font1 IME1건이다. 이전44의16/43의70/Launcher·사진Picker/갤러리/receiver export는 이번새Main결과가 아니다. 원래데이터/소유환경복원·AVD종료와 과거실패/출처를 보존하며 휴대폰/Google/SNS/네이버운영·사용자승인·공개서명APK런타임·실제150ms시간/모든프레임·다른모션배율은 별도다. 새키/Rules/의존성/원화 없이 기존데이터·원본을 유지했다.\n'
    plan[R / 'AGENTS.md'] = (headline + agents.replace(anchor, anchor + history, 1)).encode()
    for name, target in [('DESIGN.md', 'docs/' + REVIEW), ('design-system/runmoni/MASTER.md', '../../docs/' + REVIEW)]:
        path = R / name
        prefix = f'# 최신 화면 구성 공개 — {VERSION}, {date}\n\n{MARKER} [게시 검증]({target}). 실제 APK출처 {C}와 새 화면 구성/검증 경계를 따른다. 아래 후보/과거 설명은 당시 이력이며 승인 원본·데이터·접근성 규칙은 유지한다.\n\n'
        plan[path] = prefix.encode() + originals[path]
    plan[candidate] = (f'> {MARKER} 후속 공개 상태: [{date} {VERSION}/code{CODE} 게시 검증](../../../docs/{REVIEW}). 실제 APK/운영 갱신·동일입력51+분리5+native13 고유69·제한된 Main5화면은 해당 근거를 따른다. 아래 후보/검토 중 문구와 초기 빌드 결과는 이전 시점의 보존 이력이다. 사용자 시각 승인은 별도다.\n\n'.encode() + originals[candidate])
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
              'freshFinalAndroid': 69, 'distinctPassedMethods': 69, 'earlierResultsReused': False,
              'retainedSameInputExecutions': 51, 'independentClosetExecutions': 5, 'nativeExecutions': 13,
              'sameInputPassedGroupsRetained': True, 'earlierInputResultsReused': False,
              'singleSuccessfulDefaultPhaseClaimed': False, 'earlierPartialPassesExcluded': 27,
              'earlierFailedProofPreserved': 'android-review1.json',
              'native320Font2Methods': 12, 'native480Font1ImeMethods': 1,
              'actualMainOriginals': 5, 'newLauncherExecutionsRequiredOrCounted': 0, 'priorShareExportFlowReexecuted': False,
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
