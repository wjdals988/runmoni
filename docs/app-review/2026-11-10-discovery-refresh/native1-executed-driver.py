#!/usr/bin/env python3
"""루트 전용 Android 검증: 러닝 관리·지도·기록 페이지, 별도 native1개.

설치/부팅/다운로드/인증/DB 초기화는 하지 않는다.

실행 전에 XML과 검사 소유 설정·앱 런타임 권한·전원 상태만0600에
보관하고 복원한다. 전체 OS 설정의 동일성을 주장하지 않는다.
공개 proof JSON은 단계마다 atomic 교체한다. 캐시 사진은 해당
fresh 그룹에서 재생성된 Compose-target 픽스처만 내보낸다.
"""
import argparse
import datetime
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import shlex
import subprocess
import time
import xml.etree.ElementTree as ET

R = next(p for p in Path(__file__).resolve().parents if (p / 'app/build.gradle.kts').exists())
W = R / 'work/discovery-refresh'
E = R / 'docs/app-review/2026-11-10-discovery-refresh'
BG = R / 'work/background-running/host-review5.py'
VERSION, CODE = '0.4.0-dev', 22
MAPS = ('productSources', 'testSources', 'resources', 'rendererAssets', 'approvedSourceCopies', 'configurationSources')
CONTROLLED_SETTINGS = {
    'system': ('font_scale', 'accelerometer_rotation', 'user_rotation'),
    'global': ('window_animation_scale', 'transition_animation_scale', 'animator_duration_scale', 'display_size_forced'),
    'secure': ('display_density_forced', 'show_ime_with_hard_keyboard', 'ui_night_mode', 'location_mode', 'location_providers_allowed'),
}
PREFIX = 'com.runmoni.app.'
GROUPS = [('fixed-modals', 3, ['ui.ModalDismissUiTest'], False, []), ('unified-profile', 4, ['ui.UnifiedProfileUiTest'], False, []), ('discovery', 4, ['ui.RunnerDiscoveryUiTest'], False, []), ('weather', 5, ['data.RunningWeatherParserTest', 'ui.HomeWeatherUiTest'], False, []), ('map-gestures', 6, ['ui.RouteTileMapUiTest'], False, ['record-map-unavailable-font2-fixture']), ('popup-navigation', 1, ['ui.PopupNavigationUiTest'], False, []), ('closet', 1, ['ui.ConsumerClosetTest'], False, ['consumer-closet-native']), ('information', 8, ['ui.AppInformationTest'], False, []), ('record-page', 4, ['ui.RunRecordScreenUiTest'], False, []), ('crew-party', 4, ['ui.CommunityRoomsTest'], False, []), ('main-tabs', 1, ['ui.RunmoniDesignTest#fiveTabsFitNineViewportAndFontCases'], False, ['home-360-100', 'me-360-100', 'home-320-200', 'me-320-200']), ('running-controls', 1, ['ui.RunmoniDesignTest#runningActionsStayVisibleAcrossNineViewportAndFontCases'], False, [])]
GROUPS.append(('root-contracts', 10, ['ui.RunmoniUiTest#unavailableCommunityIsNotReportedAsAnEmptyServerResult', 'ui.RunmoniUiTest#fiveTabsOpenTheirActualScreens', 'ui.RunmoniUiTest#offlineRunActionsRequireFinishConfirmationAndShowSavedRecord', 'ui.RunmoniUiTest#approximateRunnerLocationRequiresDisclosureBeforeEnabling', 'ui.RunmoniUiTest#locationConsentCanBeDeclinedAndExistingSharingCanStopImmediately', 'ui.RunmoniUiTest#guestExploreRequestsProfileWithoutSharingOrConfirmation', 'ui.RunmoniUiTest#malformedFutureAndExpiredPresenceDoNotBecomeVisibleRunners', 'ui.RunmoniUiTest#crewOwnerCannotLeaveOrAssignDjToOwnerButCanManageOtherMembers', 'ui.RunmoniUiTest#guestConnectRequiresAdultAttestationWithoutInvite', 'ui.RunmoniUiTest#partyOrganizerUsesConfirmedDeletionInsteadOfLeaving'], False, []))
GROUPS = GROUPS[9:]
NATIVE_GROUPS = [('native-modal', 2, ['ui.ModalDismissNativeUiTest'], True, ['modal-about-close-native320-font2-fixture', 'modal-ime-close-native320-font2-fixture'])]
LANDSCAPE_GROUPS = [('landscape-modal', 1, ['ui.ModalDismissLandscapeUiTest'], 'landscape', ['modal-close-landscape-font2-fixture'])]



def guard(value, label):
    if not value:
        raise AssertionError(label)


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def now():
    return datetime.datetime.now(datetime.timezone.utc).isoformat()


def exclusive_bytes(path, raw, private=False):
    with path.open('xb') as output:
        output.write(raw)
        output.flush()
        os.fsync(output.fileno())
    if private:
        path.chmod(0o600)
        guard(path.stat().st_mode & 0o777 == 0o600, 'Private backup permission is exactly0600')
    descriptor = os.open(path.parent, os.O_RDONLY | os.O_DIRECTORY)
    try:
        os.fsync(descriptor)
    finally:
        os.close(descriptor)


def atomic_json(path, data):
    temporary = path.with_suffix(path.suffix + '.tmp')
    with temporary.open('w') as output:
        json.dump(data, output, ensure_ascii=False, indent=2)
        output.write('\n')
        output.flush()
        os.fsync(output.fileno())
    os.replace(temporary, path)
    descriptor = os.open(path.parent, os.O_RDONLY | os.O_DIRECTORY)
    try:
        os.fsync(descriptor)
    finally:
        os.close(descriptor)


def helper(name):
    spec = importlib.util.spec_from_file_location('ui_review_live_observer_helpers', BG)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)  #Its guarded main() never executes on import.
    module.NAME, module.E = name, W  #SystemUI failure images stay private WORK artifacts.
    return module


def shell(module, *words):
    #adb shell joins arguments remotely; quote every value, including settings
    #strings, instead of treating JSON escaping as shell escaping.
    return module.adb('shell', shlex.join([str(word) for word in words])).decode('utf-8', 'replace').strip()


def settings_map(module, namespace):
    guard(namespace in CONTROLLED_SETTINGS, 'Only owned settings namespaces are collected')
    #List is used only to distinguish an absent key from a stored literal null.
    #Unrelated values are never persisted, logged, compared, or rolled back.
    raw = module.adb('shell', 'settings', 'list', namespace).decode('utf-8', 'strict')
    observed_keys = [line.partition('=')[0] for line in raw.splitlines() if '=' in line]
    entries = {}
    for key in CONTROLLED_SETTINGS[namespace]:
        guard(observed_keys.count(key) <= 1, 'Owned settings keys are unambiguous')
        present = key in observed_keys
        value = None
        if present:
            value = module.adb('shell', 'settings', 'get', namespace, key).decode('utf-8', 'strict')
            value = value.removesuffix('\r\n') if value.endswith('\r\n') else value.removesuffix('\n')
        entries[key] = {'present': present, 'value': value}
    return entries


def environment_restoration_scope():
    return {'controlledSettingsOnly': True, 'fullOsSettingsRestorationClaimed': False,
            'settingsKeys': {namespace: list(keys) for namespace, keys in CONTROLLED_SETTINGS.items()},
            'noUnrelatedSettingsRolledBack': True, 'unrelatedOsAutomaticChangesComparedOrReverted': False,
            'windowManager': ['size', 'density'], 'uiModeManagerNightMode': True, 'allOurPackageRuntimePermissions': 'com.runmoni.app',
            'runtimePermissionGrantAndFlags': True, 'power': 'Observed Awake/Asleep wakefulness only'}


def permissions(module):
    raw = module.sh('dumpsys', 'package', module.PKG)
    values = {}
    for key, granted, flags in re.findall(r'^\s+([A-Za-z0-9_.]+): granted=(true|false), flags=\[([^\]]*)\]', raw, re.M):
        state = {'granted': granted == 'true', 'flags': sorted(flag.strip() for flag in flags.split('|') if flag.strip())}
        guard(key not in values or values[key] == state, 'Runtime permission state must be unambiguous')
        values[key] = state
    guard(values, 'Actual package runtime permissions were observed')
    return values


def observed_night_mode(module):
    match = re.search(r'Night mode:\s*(auto|no|yes)\b', module.sh('cmd', 'uimode', 'night'))
    guard(match is not None, 'Actual UiModeManager night mode is observable')
    return match.group(1)


def environment_snapshot(module):
    power = module.sh('dumpsys', 'power')
    wakefulness = re.search(r'mWakefulness=([A-Za-z]+)\b', power)
    guard(wakefulness is not None and wakefulness.group(1) in ['Awake', 'Asleep'],
          'Only an observed stable Awake/Asleep state can be restored exactly')
    return {'environmentRestorationScope': environment_restoration_scope(),
            'settings': {namespace: settings_map(module, namespace) for namespace in CONTROLLED_SETTINGS},
            'density': module.sh('wm', 'density'), 'size': module.sh('wm', 'size'),
            'nightMode': observed_night_mode(module),
            'permissions': permissions(module), 'powerAsleep': wakefulness.group(1) == 'Asleep',
            'powerWakefulness': wakefulness.group(1)}


def comparable_environment(value):
    #Only explicit owned settings, WM overrides, this package's runtime grants/
    #flags, and screen wakefulness are compared. Other OS state is out of scope.
    return {key: value[key] for key in ['settings', 'density', 'size', 'nightMode', 'permissions', 'powerAsleep', 'powerWakefulness']}


def restore_environment(module, before):
    failures = []
    def attempt(label, action):
        try:
            return action()
        except Exception as failure:
            failures.append({'operation': label, 'failureType': type(failure).__name__})
            return None
    if attempt('read current night mode', lambda: observed_night_mode(module)) != before['nightMode']:
        attempt('restore UiModeManager night mode', lambda: shell(module, 'cmd', 'uimode', 'night', before['nightMode']))
    for command, label in [('size', 'size'), ('density', 'density')]:
        pattern = r'Override ' + command + r':\s*([^\n]+)'
        match = re.search(pattern, before[label])
        attempt(label, lambda command=command, match=match: module.sh('wm', command, match.group(1).strip() if match else 'reset'))
    for namespace, keys in CONTROLLED_SETTINGS.items():
        desired = before['settings'][namespace]
        guard(set(desired) == set(keys), 'Restoration snapshot contains exactly owned setting keys')
        current = attempt('read current settings ' + namespace, lambda namespace=namespace: settings_map(module, namespace))
        if current is None:
            current = {}
        for key in keys:
            state = desired[key]
            if current.get(key) != state:
                command = ['settings', 'put', namespace, key, state['value']] if state['present'] else ['settings', 'delete', namespace, key]
                attempt('restore owned setting', lambda command=command: shell(module, *command))
    current_permissions = attempt('read current permission grants', lambda: permissions(module)) or {}
    flag_names = {'USER_SET': 'user-set', 'USER_FIXED': 'user-fixed', 'REVOKE_ON_UPGRADE': 'revoke-on-upgrade',
                  'REVIEW_REQUIRED': 'review-required', 'REVOKED_COMPAT': 'revoked-compat'}
    for key, desired in before['permissions'].items():
        current = current_permissions.get(key)
        if current is None or current['granted'] != desired['granted']:
            attempt('restore permission grant', lambda key=key, desired=desired: shell(module, 'pm', 'grant' if desired['granted'] else 'revoke', module.PKG, key))
    restored_permissions = attempt('read permission flags after grant restoration', lambda: permissions(module)) or {}
    for key, desired in before['permissions'].items():
        current = restored_permissions.get(key)
        actual_flags = set(current['flags']) if current else set()
        desired_flags = set(desired['flags'])
        for flag, argument in flag_names.items():
            if (flag in actual_flags) != (flag in desired_flags):
                attempt('restore permission flag', lambda key=key, flag=flag, argument=argument, desired_flags=desired_flags:
                        shell(module, 'pm', 'set-permission-flags' if flag in desired_flags else 'clear-permission-flags', module.PKG, key, argument))
    attempt('restore screen power', lambda: module.sh('input', 'keyevent', '223' if before['powerAsleep'] else '224'))
    deadline = time.monotonic() + 5
    while time.monotonic() < deadline:
        raw = attempt('wait for restored screen power', lambda: module.sh('dumpsys', 'power'))
        if raw is not None and re.search(r'mWakefulness=' + before['powerWakefulness'] + r'\b', raw):
            break
        time.sleep(.2)
    after = environment_snapshot(module)
    return comparable_environment(after) == comparable_environment(before), after, failures


def source_maps(binary):
    result = {}
    for section in MAPS:
        expected = binary.get(section)
        guard(isinstance(expected, dict) and expected, 'All six frozen source maps are required')
        mismatches = []
        for relative, digest in expected.items():
            path = R / relative
            guard(not Path(relative).is_absolute() and '..' not in Path(relative).parts and '.secrets' not in path.resolve().parts and
                  path.resolve().is_relative_to(R), 'Source evidence stays within non-secret repository files')
            if not path.is_file() or sha(path.read_bytes()) != digest:
                mismatches.append(relative)
        result[section] = {'fileCount': len(expected), 'matches': not mismatches, 'mismatches': mismatches}
    guard(all(value['matches'] for value in result.values()), 'Current sources/resources must equal all six build maps')
    return result


def profile_from_xml(raw):
    root = ET.fromstring(raw)
    node = next((node for node in root if node.get('name') == 'localProfile'), None)
    return None if node is None else json.loads(node.text)


def local_profile(module):
    return profile_from_xml(module.prefs())


def fixed_rotation(module):
    observed = re.findall(r'mFixedToUserRotation=(true|false)', module.sh('dumpsys', 'window'))
    guard(len(observed) == 1, 'Exactly one owned display fixed-to-user rotation state')
    return observed[0] == 'true'


def fixed_rotation_policy(module):
    # Preserve default/disabled/enabled rather than collapsing default toFalse.
    raw = shell(module, 'su', '0', 'sh', '-c',
                'if [ -e /data/system/display_settings.xml ]; then cat /data/system/display_settings.xml; else printf MISSING; fi')
    if raw == 'MISSING':
        return 'default'
    root = ET.fromstring(raw)
    displays = root.findall('display')
    guard(len(displays) <= 1, 'Only the single owned AVD display policy may be changed')
    values = {None: 'default', '0': 'default', '1': 'disabled', '2': 'enabled'}
    value = displays[0].get('fixedToUserRotation') if displays else None
    guard(value in values, 'Known actual fixed-to-user rotation tri-state policy')
    return values[value]


def viewport(module, native):
    landscape = native == 'landscape'
    # A force-stopped app leaves Launcher, whose portrait-only activity can
    # ignore user_rotation. Start only our safe local fixture MainActivity
    # before requesting landscape, then verify real display dimensions.
    if landscape:
        module.sh('wm', 'set-fix-to-user-rotation', 'enabled')
        guard(fixed_rotation(module) is True, 'Actual OS fixed-to-user rotation enabled for native landscape')
        module.launch()
    module.sh('settings','put','system','accelerometer_rotation','0')
    module.sh('settings','put','system','user_rotation','1' if landscape else '0')
    module.wait(lambda: (module.dimensions()[0] > module.dimensions()[1]) == landscape, 'Actual product group orientation')
    module.sh('wm', 'density', 'reset')
    module.sh('settings', 'put', 'system', 'font_scale', '1.0')
    if native:
        width, height = module.dimensions()
        short = min(width,height) if landscape else width
        density = round(short * 160 / 320)
        guard(density > 0 and abs(short * 160 / density - 320) <= 1, 'Physical display can represent actual320dp')
        module.sh('wm', 'density', str(density))
        module.sh('settings', 'put', 'system', 'font_scale', '2.0')
        module.sh('settings', 'put', 'secure', 'show_ime_with_hard_keyboard', '1')
    module.tree()  #Working fresh observer, never the standard idle-wait dump.
    result = {'density': module.sh('wm', 'density'), 'fontScale': module.sh('settings', 'get', 'system', 'font_scale'),
              'native320Font2RequiredByClass': bool(native and not landscape)}
    if landscape:
        width,height = module.dimensions()
        guard(width > height and abs(height * 160 / density - 320) <= 1, 'Actual native short side320dp in landscape')
        result.update(nativeLandscape320ShortSideRequiredByClass=True,widthPx=width,heightPx=height,
                      effectiveDensityDpi=density,shortSideDp=height*160/density,nativeLandscape=True)
    return result


def run(args):
    os.umask(0o077)
    native_continuation = args.phase == 'native-continuation'
    native_phase = args.phase in ['native', 'native-continuation']
    landscape_phase = args.phase == 'landscape'
    smoke_phase = args.phase == 'chat-smoke'
    smoke = [('chat-context-smoke', 1, ['ui.AppInformationTest#fullScreenChatKeepsCrewSelectionAndHidesBackgroundControls'], False, [])]
    remainder_phase = args.phase == 'remainder'
    groups = LANDSCAPE_GROUPS if landscape_phase else NATIVE_GROUPS if native_phase else GROUPS
    planned = sum(group[1] for group in groups)
    if native_continuation:
        groups, planned = NATIVE_GROUPS[4:], 1
    args.name = args.name or ('chat-smoke1' if smoke_phase else ('landscape1' if landscape_phase else ('native1' if native_phase else ('android-review4' if remainder_phase else 'android-review5'))))
    pattern = r'chat-smoke\d+' if smoke_phase else (r'landscape\d+' if landscape_phase else (r'native\d+' if native_phase else r'android-review\d+'))
    guard(re.fullmatch(pattern, args.name) is not None,
          'Restricted immutable attempt name for the selected independent phase')
    guard(sum(group[1] for group in GROUPS) == 16, 'Reviewed default method count')
    guard(sum(group[1] for group in NATIVE_GROUPS) == 2, 'Reviewed native portrait method count')
    guard(sum(group[1] for group in LANDSCAPE_GROUPS) == 1, 'Reviewed native landscape method count')
    W.mkdir(parents=True, exist_ok=True)
    E.mkdir(parents=True, exist_ok=True)
    out = E / (args.name + '.json')
    guard(not out.exists(), 'Do not overwrite a previous proof; use a new attempt name')
    raw_source = Path(__file__).read_bytes()
    executed = E / (args.name + '-executed-driver.py')
    exclusive_bytes(executed, raw_source)
    helper_source = BG.read_bytes()
    executed_helper = E / (args.name + '-live-observer-helper.py')
    exclusive_bytes(executed_helper, helper_source)
    proof = {'name': args.name, 'version': VERSION, 'versionCode': CODE, 'binaryReference': 'binary-build8.json',
             'reviewPhase': args.phase, 'independentPhaseNoPriorResultAggregation': True,
             'startedAtUtc': now(), 'phase': 'created', 'passed': False, 'completed': False, 'plannedProductMethods': planned,
             'actualPassedProductMethods': 0, 'groups': [], 'fixtures': [], 'executedDriver': executed.name,
             'executedDriverSha256': sha(raw_source), 'observerHelper': executed_helper.name, 'observerHelperSha256': sha(helper_source),
             'physicalDeviceVerified': False, 'googleLoginVerified': False,
             'productionWritesVerified': False, 'android31RuntimeVerified': False, 'signedReleaseRuntimeVerified': False,
             'observerMethodsCountAsProductTests': False,
             'environmentRestorationScope': environment_restoration_scope(),
             'limitations': ['UI images use safe ComponentActivity fixtures and test callbacks, not actual MainActivity accounts or production data.',
                            'Native modal cases inspect actual OS/IME insets and injected test controls at320dp/font2. They do not verify phone authentication.']}
    def checkpoint(phase):
        proof['phase'], proof['updatedAtUtc'] = phase, now()
        atomic_json(out, proof)
        print(json.dumps({'phase': phase, 'actualPassedProductMethods': proof['actualPassedProductMethods']}, ensure_ascii=False), flush=True)
    checkpoint('created')
    module, before_xml, before_environment, expected_profile = None, None, None, None
    binary, can_mutate, before_fixed_rotation, before_fixed_policy = None, False, None, None
    try:
        binary_path = E / 'binary-build8.json'
        binary_raw = binary_path.read_bytes()
        binary = json.loads(binary_raw)
        guard(binary['buildSucceeded'] and binary['version'] == VERSION and binary['versionCode'] == CODE, 'Frozen0.4.0-dev/code22 build')
        proof['binaryReferenceSha256'] = sha(binary_raw)
        proof['sourceMapsBefore'] = source_maps(binary)
        guard(sha(BG.read_bytes()) == proof['observerHelperSha256'], 'Frozen live-observer helper before import')
        module = helper(args.name)
        guard(module.adb('emu', 'avd', 'name').decode().splitlines()[0] == 'Runmoni_Cloud_29', 'Owned AVD only')
        guard(module.sh('getprop', 'ro.build.version.sdk') == '29' and module.sh('am', 'get-current-user') == '0', 'Owned API29 user0')
        proof.update(avd='Runmoni_Cloud_29', api=29, acceleration='TCG', serial='emulator-5556')
        config = (R / 'app/build/generated/source/buildConfig/debug/com/runmoni/app/BuildConfig.java').read_text()
        guard(re.search(r'boolean FIREBASE_ENABLED\s*=\s*false\s*;', config) is not None, 'Only unconfigured local debug, no Google/production writes')
        installed = []
        guard(len(binary['binaries']) == 2, 'Both APK references required')
        for package, expected, apk in zip([module.PKG, module.PKG + '.test'], binary['binaries'],
                                          ['app/build/outputs/apk/debug/app-debug.apk', 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk']):
            guard(expected['file'] == apk, 'App and test metadata order is explicit')
            path = module.sh('pm', 'path', package).removeprefix('package:')
            guard(path.startswith('/data/app/') and '\n' not in path, 'Single installed base APK')
            digest = module.sh('sha256sum', path, timeout=60).split()[0]
            guard(digest == expected['sha256'], 'Installed app/test pair equals the frozen build')
            installed.append({'package': package, 'sha256': digest})
        package = module.sh('dumpsys', 'package', module.PKG)
        guard(re.search(r'versionName=' + re.escape(VERSION) + r'(?:\s|$)', package) is not None and
              re.search(r'versionCode=' + str(CODE) + r'(?:\s|$)', package) is not None, 'Actual installed version/code')
        observer = json.loads((R / 'docs/app-review/2026-11-06-background-running/observer-build.json').read_text())
        guard(module.sh('sha256sum', '/data/local/tmp/runmoni-live-dump.jar').split()[0] == observer['jarSha256'], 'Working shell observer binary SHA')
        proof['installed'], proof['observerJarSha256'] = installed, observer['jarSha256']
        guard(module.dump() is None, 'Never take over an active recording service')
        guard(not module.pid(), 'Owner must leave the app force-stopped so both snapshots precede every mutation')
        if landscape_phase:
            before_fixed_rotation = fixed_rotation(module)
            before_fixed_policy = fixed_rotation_policy(module)
            proof['fixedToUserRotation'] = {'before': before_fixed_rotation, 'beforePolicy': before_fixed_policy,
                'forcedEnabledForLandscape': False, 'restoredExactly': False, 'restorationScope': 'Effective value and default/disabled/enabled policy only; full display-settings file bytes are not claimed identical'}
        before_environment = environment_snapshot(module)
        environment_path = W / (args.name + '-private-environment-before.json')
        exclusive_bytes(environment_path, (json.dumps(before_environment, ensure_ascii=False, indent=2) + '\n').encode(), private=True)
        proof['privateEnvironmentBackup'] = {'file': environment_path.name, 'sha256': sha(environment_path.read_bytes()), 'mode': '0600'}
        checkpoint('environment-snapshot-durable-before-mutation')
        before_xml = module.prefs()
        ET.fromstring(before_xml)
        xml_path = W / (args.name + '-private-before.xml')
        exclusive_bytes(xml_path, before_xml, private=True)
        expected_profile = profile_from_xml(before_xml)
        proof['privateXmlBackup'] = {'file': xml_path.name, 'sha256': sha(before_xml), 'mode': '0600'}
        checkpoint('xml-and-environment-backups-durable-before-any-mutation')
        can_mutate = True
        module.force_stop()
        guard(module.prefs() == before_xml, 'Quiescent original XML remained exact before fixture injection')
        fixture = ET.fromstring(before_xml)
        for kind, key, value in [('string', 'communityBackend', 'firebase'), ('string', 'apiUrl', 'http://127.0.0.1:8787'),
                                 ('string', 'appEntryChoiceV1', 'guest'), ('boolean', 'communityGuest', 'true'),
                                 ('boolean', 'publicLocation', 'false'), ('boolean', 'voiceGuidance', 'false'), ('boolean', 'autoPause', 'false')]:
            module.set_pref(fixture, kind, key, value)
        module.write_prefs(ET.tostring(fixture, encoding='utf-8', xml_declaration=True))
        # Formal fixtures must not trigger a MET lookup from landscape's preparatory MainActivity.
        for permission in ['android.permission.ACCESS_COARSE_LOCATION', 'android.permission.ACCESS_FINE_LOCATION']:
            shell(module, 'pm', 'revoke', module.PKG, permission)
        proof['previewLocationPermissionsTemporarilyRevokedForOfflineFixtures'] = True
        module.wake()
        for key in ['window_animation_scale', 'transition_animation_scale', 'animator_duration_scale']:
            module.sh('settings', 'put', 'global', key, '0')
        checkpoint('safe-local-fixture-ready')
        for group_name, expected, classes, native, captures in groups:
            full_classes = [PREFIX + name for name in classes]
            group = {'name': group_name, 'expected': expected, 'classes': full_classes, 'status': 'preparing',
                     'source': 'Fresh product am instrument run; no earlier execution reused', 'passed': False}
            proof['groups'].append(group)
            checkpoint('preparing-' + group_name)
            if native == 'landscape':
                proof['fixedToUserRotation']['forcedEnabledForLandscape'] = True
            group['viewport'] = viewport(module, native)
            module.sh('run-as', module.PKG, 'rm', '-f', 'cache/design-review/minimi-readiness.txt')
            module.sh('run-as', module.PKG, 'rm', '-f', 'cache/design-review/update-indicator-layout.jsonl')
            guard(shell(module, 'run-as', module.PKG, 'sh', '-c', 'test ! -e cache/design-review/minimi-readiness.txt') == '',
                  'Current group avatar readiness trace starts absent')
            for name in captures:
                guard(re.fullmatch(r'[A-Za-z0-9_-]+', name) is not None, 'Allowlisted review-cache name')
                module.sh('run-as', module.PKG, 'rm', '-f', 'cache/design-review/' + name + '.png')
                guard(shell(module, 'run-as', module.PKG, 'sh', '-c', 'test ! -e cache/design-review/' + name + '.png') == '',
                      'Fixture cache target absent before current product group')
            group['fixtureFreshness'] = 'Allowlisted cache images deleted before this exact execution'
            raw_path = E / (args.name + '-' + group_name + '.txt')
            group.update(status='running', raw=raw_path.name, startedAtUtc=now())
            checkpoint('running-' + group_name)
            started = time.monotonic()
            try:
                with raw_path.open('x') as log:
                    process = subprocess.run(module.A + ['shell', 'am', 'instrument', '-w', '-e', 'class', ','.join(full_classes),
                                             module.PKG + '.test/androidx.test.runner.AndroidJUnitRunner'],
                                             stdout=log, stderr=subprocess.STDOUT, timeout=1800)
                    log.flush()
                    os.fsync(log.fileno())
                raw = raw_path.read_text()
                match = re.search(r'OK \((\d+) tests?\)', raw)
                group['actual'] = int(match.group(1)) if match else None
                group['rawSha256'] = sha(raw_path.read_bytes())
                group['passed'] = process.returncode == 0 and group['actual'] == expected and not any(
                    marker in raw for marker in ['FAILURES!!!', 'Process crashed', 'INSTRUMENTATION_FAILED']) and not re.search(r'INSTRUMENTATION_STATUS_CODE:\s*-[34]\b', raw)
            finally:
                if raw_path.is_file():
                    group['rawSha256'] = sha(raw_path.read_bytes())
                group['elapsedSeconds'], group['finishedAtUtc'] = round(time.monotonic() - started, 2), now()
                group['status'] = 'passed' if group.get('passed') else 'failed'
                readiness = module.adb('shell', '-T', shlex.join(['run-as', module.PKG, 'sh', '-c',
                    'if [ -f cache/design-review/minimi-readiness.txt ]; then cat cache/design-review/minimi-readiness.txt; fi']))
                if readiness.strip():
                    guard(len(readiness) < 100_000 and all(re.fullmatch(r'.{1,250}: waitMillis=\d+; timeoutMillis=120000', line)
                          for line in readiness.decode().splitlines()), 'Only actual static-context readiness metrics may be exported')
                    readiness_path=E/(args.name+'-'+group_name+'-minimi-readiness.txt')
                    exclusive_bytes(readiness_path,readiness)
                    group.update(minimiReadinessRaw=readiness_path.name,minimiReadinessRawSha256=sha(readiness),
                                 minimiReadinessPerformanceSlaClaimed=False)
                proof['actualPassedProductMethods'] = sum(item.get('actual', 0) or 0 for item in proof['groups'] if item.get('passed'))
                checkpoint('finished-' + group_name)
            geometry = shell(module, 'run-as', module.PKG, 'sh', '-c',
                             'if [ -f cache/design-review/update-indicator-layout.jsonl ]; then cat cache/design-review/update-indicator-layout.jsonl; fi')
            if geometry:
                geometry_raw = (geometry + '\n').encode()
                rows = [json.loads(line) for line in geometry.splitlines()]
                geometry_path = E / (args.name + '-' + group_name + '-indicator-layout.jsonl')
                exclusive_bytes(geometry_path, geometry_raw)
                group.update(indicatorLayoutRaw=geometry_path.name, indicatorLayoutRawSha256=sha(geometry_raw),
                             indicatorLayoutRecordCount=len(rows))
                checkpoint('fresh-indicator-layout-observations-' + group_name)
            guard(group['passed'], 'Fresh product group failed: ' + group_name)
            guard(local_profile(module) == expected_profile, 'Original stored profile preserved after group: ' + group_name)
            group['originalProfilePreserved'] = True
            for name in captures:
                raw = module.adb('exec-out', 'run-as', module.PKG, 'cat', 'cache/design-review/' + name + '.png')
                width, height = module.dimensions(raw)
                destination = E / (args.name + '-' + name + '.png')
                exclusive_bytes(destination, raw)
                proof['fixtures'].append({'file': destination.name, 'sha256': sha(raw), 'bytes': len(raw), 'width': width, 'height': height,
                    'sourceGroup': group_name, 'sourceRaw': raw_path.name, 'sourceKind': ('API29 ComponentActivity device screenshot with native320dp short side landscape/font2; unavailable tiles/fake callbacks' if landscape_phase else 'API29 ComponentActivity device screenshot with native320dp/font2; unavailable tiles/fake callbacks' if native_phase else ('API29 ComponentActivity device screenshot; unavailable tiles/fake callbacks' if name.startswith('root-record-') else 'API29 ComponentActivity Compose-target fixture; unavailable tiles/fake callbacks')),
                    'freshness': 'Cache target removed immediately before the successful current group; newly created file read afterward',
                    'actualMainActivityScreenshot': False, 'actualSystemNotificationInteraction': False,
                    'native320Font2Group': bool(native and native != 'landscape'),
                    'nativeLandscape320ShortSideFont2Group': native == 'landscape'})
                checkpoint('fresh-fixture-exported-' + name)
            checkpoint('profile-and-fixtures-verified-' + group_name)
        guard(proof['actualPassedProductMethods'] == planned, 'Every method in this independent fresh phase passed')
        proof['sourceMapsAfter'] = source_maps(binary)
        guard(sha((E / 'binary-build8.json').read_bytes()) == proof['binaryReferenceSha256'], 'Build evidence remained immutable')
        proof['passed'] = True
        checkpoint('all' + str(planned) + '-passed-awaiting-exact-restoration')
    except BaseException as failure:
        proof['passed'] = False
        proof['failureType'] = type(failure).__name__
        proof['failure'] = str(failure) if isinstance(failure, AssertionError) else 'Review operation failed; inspect preserved raw group/private diagnostics'
        checkpoint('failed-before-finally')
        if module is not None and can_mutate:
            try:
                raw = module.adb('exec-out', 'screencap', '-p')
                module.dimensions(raw)
                path = W / (args.name + '-private-failure.png')
                exclusive_bytes(path, raw, private=True)
                proof['privateFailureScreenshot'] = path.name
            except Exception:
                pass
    finally:
        if module is not None and can_mutate:
            try:
                module.force_stop()
                if before_xml is not None:
                    module.write_prefs(before_xml)
                    proof['preferencesRestoredExactly'] = module.prefs() == before_xml
                    proof['profileRestoredExactly'] = local_profile(module) == expected_profile
                checkpoint('xml-restoration-attempted')
            except BaseException as failure:
                proof.update(preferencesRestoredExactly=False, preferenceRestoreFailureType=type(failure).__name__)
                checkpoint('xml-restoration-failed')
            try:
                if before_fixed_rotation is not None:
                    module.sh('wm', 'set-fix-to-user-rotation', before_fixed_policy)
                    after_fixed_rotation = fixed_rotation(module)
                    after_fixed_policy = fixed_rotation_policy(module)
                    proof['fixedToUserRotation'].update(after=after_fixed_rotation, afterPolicy=after_fixed_policy,
                        restoredExactly=after_fixed_rotation == before_fixed_rotation and after_fixed_policy == before_fixed_policy)
                    guard(proof['fixedToUserRotation']['restoredExactly'], 'Owned fixed-to-user rotation restored exactly')
            except BaseException as failure:
                proof.update(rotationRestoreFailureType=type(failure).__name__)
                checkpoint('rotation-restoration-failed')
            # A rotation-policy failure must not skip unrelated owned settings and permissions.
            try:
                if before_environment is not None:
                    equal, after, failures = restore_environment(module, before_environment)
                    private_after = W / (args.name + '-private-environment-after.json')
                    exclusive_bytes(private_after, (json.dumps(after, ensure_ascii=False, indent=2) + '\n').encode(), private=True)
                    proof['controlledSettingsPermissionsPowerRestoredExactly'] = equal
                    proof['settingsPermissionsPowerRestoredExactly'] = equal  #Same controlled-scope compatibility alias.
                    proof['restorationCommandFailures'] = failures
                checkpoint('environment-restoration-attempted')
            except BaseException as failure:
                proof.update(controlledSettingsPermissionsPowerRestoredExactly=False, settingsPermissionsPowerRestoredExactly=False,
                             environmentRestoreFailureType=type(failure).__name__)
                checkpoint('environment-restoration-failed')
        else:
            proof['restorationNotApplicableBeforeMutation'] = True
        if module is not None:
            proof['observerExecutionCount'] = module.P.get('observerDumps', 0)
            proof['systemUiWaitActionCount'] = len(module.P.get('systemUiWaitActions', []))
        proof['driverSourceUnchanged'] = sha(Path(__file__).read_bytes()) == proof['executedDriverSha256']
        proof['observerHelperSourceUnchanged'] = sha(BG.read_bytes()) == proof['observerHelperSha256']
        proof['passed'] = bool(proof['passed'] and proof['driverSourceUnchanged'] and proof['observerHelperSourceUnchanged'] and proof.get('preferencesRestoredExactly') and
                               proof.get('profileRestoredExactly') and proof.get('controlledSettingsPermissionsPowerRestoredExactly') and
                               (not landscape_phase or proof.get('fixedToUserRotation', {}).get('restoredExactly') is True))
        proof['completed'], proof['finishedAtUtc'] = True, now()
        checkpoint('complete-passed' if proof['passed'] else 'complete-failed')
    return 0 if proof['passed'] else 1


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--phase', choices=['default', 'native', 'landscape'], default='default', help='Independent16 targeted default or1 native portrait method')
    parser.add_argument('--name', default=None, help='Fresh immutable attempt name; default android-review1/native1 by phase')
    raise SystemExit(run(parser.parse_args()))
