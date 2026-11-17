#!/usr/bin/env python3
"""루트 전용 0.9 Android 검증 준비: phase별 현재 소스 @Test 개수와 새 APK/source freeze를 결합한다.

설치/부팅/다운로드/인증/DB 초기화는 하지 않는다.

실행 전에 XML과 검사 소유 설정·앱 런타임 권한·전원 상태만0600에
보관하고 복원한다. 전체 OS 설정의 동일성을 주장하지 않는다.
공개 proof JSON은 단계마다 atomic 교체한다. 캐시 사진은 해당
fresh 그룹에서 재생성된 Compose-target 픽스처만 내보낸다.
"""
import argparse
import base64
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
W = R / 'work/expressive-refresh'
E = R / 'docs/app-review/2026-11-17-expressive-refresh'
BG = W / 'actual-ui-helper.py'
VERSION, CODE = '0.9.0-dev', 32
MAPS = ('productSources', 'testSources', 'resources', 'rendererAssets', 'approvedSourceCopies', 'configurationSources')
CONTROLLED_SETTINGS = {
    'system': ('font_scale', 'accelerometer_rotation', 'user_rotation'),
    'global': ('window_animation_scale', 'transition_animation_scale', 'animator_duration_scale', 'display_size_forced'),
    'secure': ('display_density_forced', 'show_ime_with_hard_keyboard', 'ui_night_mode', 'location_mode', 'location_providers_allowed'),
}
PREFIX = 'com.runmoni.app.'

GROUP_SPECS = [
 ('backup-room', ['data.CloudBackupPersistenceTest','data.RunPersistenceTest','data.RunReliabilityTest','data.RunSpeedPersistenceTest'], False, []),
 ('atelier-materials', ['avatar.AtelierWardrobeTest','avatar.WardrobeRendererTest','avatar.ExpandedWardrobeTest'], False, []),
 ('atelier-expansion', ['ui.AtelierExpansionRenderingTest'], False, ['atelier-expansion-112']),
 ('application-hierarchy', ['ui.RunmoniDesignTest'], False, []),
 ('appearance', ['ui.AppearanceUiTest'], False, []),
 ('environment-score', ['ui.RunningEnvironmentUiTest'], False, ['environment-score-320-font2-light-fixture','environment-score-320-font2-dark-fixture']),
 ('closet-atelier', ['ui.ClosetAtelierUiTest'], False, []),
 ('widget-night', ['widget.RunWidgetNightTest'], False, ['widget-night-idle-font2-native-fixture']),
 ('widget-existing-layouts', ['widget.RunWidgetFactoryTest'], False, []),
 ('icon', ['ui.RunmoniIconTest'], False, []),
 ('share-renderer', ['sharing.RunShareCardRendererTest'], False, []),
 ('share-ui', ['ui.RunShareUiTest','ui.RunShareControllerTest'], False, []),
 ('record-auto-backup', ['ui.RunRecordScreenUiTest'], False, []),
 ('closet', ['ui.RunningKitColorsUiTest'], False, []),
 ('discovery', ['ui.RunnerDiscoveryUiTest'], False, []),
 ('avatar-identity-playback', ['ui.ApprovedAvatarRenderingTest','ui.AvatarPlaybackTest'], False, []),
]
NATIVE_SPECS = [('native-form-pages', ['ui.ModalDismissNativeUiTest'], True, ['modal-about-close-native320-font2-fixture', 'modal-ime-close-native320-font2-fixture']), ('native-large-text', ['ui.RunmoniDialogDesignTest', 'ui.AppEntryNativeUiTest', 'ui.AppInformationNativeTest', 'ui.HierarchyClosetLargeTextUxTest', 'ui.ClosetNavigationNativeUiTest', 'ui.RunningKitColorsNativeUiTest', 'ui.RunRecordScreenNativeUiTest', 'ui.RunShareImeUiTest#realKeyboardKeepsCloseAndScrollableExportActionsOutsideImeAndSystemBars'], True, ['home-active-native-320-font2-device', 'settings-320-200', 'closet-320-200', 'chat-input-default', 'entry-native-320-font2-fixture', 'about-320-200', 'closet-hierarchy-native-320-font2-fixture', 'closet-navigation-native-320-font2-fixture', 'run-share-native320-font2-ime-fixture']), ('native-share-font1', ['ui.RunShareImeUiTest#normalTextStoryCaptionStaysVisibleAndAcceptsRealKeysAcrossImeResize'], False, ['run-share-native480-font1-ime-caption-fixture'])]
LANDSCAPE_SPECS = [('community-return-scroll', ['ui.CommunityPageNavigationTest#partyChatBackReturnsToTheSamePartyScrollAndCrewTab'], 'landscape-font1', [])]
MAP_SDK_SPECS = [('native-map-sdk', ['ui.MapLibreMapPaneUiTest'], False, ['direct-native-map-320-font2-height360-layout-fixture'])]
MAP_ONLINE_SPECS = [('native-online-map', ['ui.NativeVectorMapOnlineUiTest'], False,
                     ['native-online-seoul-fixture', 'native-online-gestures-fixture'])]
FIREBASE_SDK_SPECS = [('firebase-demo-profile', ['data.FirebaseNicknameTest', 'data.FirebaseCommunityTest'], False, [])]
DISPATCHER_SPECS = [
    ('fixture-dispatcher-default', ['ui.FixtureDispatcherDefaultProbeTest'], False, []),
    ('fixture-dispatcher-standard', ['ui.FixtureDispatcherStandardProbeTest'], False, []),
]
DISPATCHER_REPORTS = {
    'fixture-dispatcher-default': ('default', 'cache/fixture-dispatcher-probe-default.json'),
    'fixture-dispatcher-standard': ('standard', 'cache/fixture-dispatcher-probe-standard.json'),
}
DISPATCHER_REPORT_LIMIT = 64 * 1024
DISPATCHER_REPORT_SCOPE = 'Compose test effect context only; not a product threading fix or failure probability'

def test_catalogue():
    result = {}
    for path in sorted((R / 'app/src/androidTest/java').rglob('*.kt')):
        raw = path.read_text()
        package = re.search(r'^package\s+([\w.]+)', raw, re.M)
        if not package:
            continue
        classes = list(re.finditer(r'^(?:(?:internal|public|open|abstract)\s+)*class\s+(\w+)\b', raw, re.M))
        for index, found in enumerate(classes):
            body = raw[found.end():classes[index + 1].start() if index + 1 < len(classes) else len(raw)]
            methods = re.findall(r'@Test(?:\([^)]*\))?\s+fun\s+(\w+)', body)
            if methods:
                result[package.group(1) + '.' + found.group(1)] = methods
    return result

def expand_selectors(selectors, catalogue):
    methods = []
    for selected in selectors:
        full = PREFIX + selected
        name, _, method = full.partition('#')
        guard(name in catalogue, 'Selected test class exists in current source: ' + name)
        selected_methods = [method] if method else catalogue[name]
        guard(all(value in catalogue[name] for value in selected_methods), 'Every selected method exists: ' + full)
        methods.extend(name + '#' + value for value in selected_methods)
    guard(len(methods) == len(set(methods)), 'No duplicate method in a group')
    return methods

def phase_groups(phase):
    specs = {'default': GROUP_SPECS, 'native': NATIVE_SPECS, 'landscape': LANDSCAPE_SPECS,
             'native-sdk': MAP_SDK_SPECS, 'native-online': MAP_ONLINE_SPECS, 'firebase-sdk': FIREBASE_SDK_SPECS,
             'dispatcher': DISPATCHER_SPECS}[phase]
    catalogue = test_catalogue()
    specs = list(specs)
    if False:
        known = {selector.split('#')[0] for _, selectors, _, _ in specs for selector in selectors}
        for full in sorted(catalogue):
            short = full.removeprefix(PREFIX)
            if 'Costume' in full and short not in known:
                specs.append(('costume-' + full.rsplit('.', 1)[-1], [short], False, []))
    groups, all_methods = [], []
    for name, selectors, native, captures in specs:
        methods = expand_selectors(selectors, catalogue)
        groups.append((name, len(methods), selectors, native, captures))
        all_methods.extend(methods)
    guard(len(all_methods) == len(set(all_methods)), 'No method is counted twice in this independent phase')
    return groups, all_methods



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
    module.NAME, module.E, module.W = name, W, W  #SystemUI/private SQLite diagnostics stay private WORK artifacts.
    module.VERSION, module.CODE = VERSION, CODE
    return module


def loading_diagnostic_names(module):
    raw = shell(module, 'run-as', module.PKG, 'sh', '-c',
        'if [ -d cache/design-review ]; then ls -1 cache/design-review; fi')
    return {name for name in raw.splitlines()
            if re.fullmatch(r'minimi-timeout-[a-f0-9]+-[0-9]+\.(?:txt|png)', name)}


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
    landscape = native == 'landscape-font1'
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
        module.sh('settings', 'put', 'system', 'font_scale', '1.0' if landscape else '2.0')
        module.sh('settings', 'put', 'secure', 'show_ime_with_hard_keyboard', '1')
    module.tree()  #Working fresh observer, never the standard idle-wait dump.
    result = {'density': module.sh('wm', 'density'), 'fontScale': module.sh('settings', 'get', 'system', 'font_scale'),
              'native320Font2RequiredByClass': native is True}
    if landscape:
        width,height = module.dimensions()
        guard(width > height and abs(height * 160 / density - 320) <= 1, 'Actual native short side320dp in landscape')
        result.update(nativeLandscape320ShortSideRequiredByClass=True,widthPx=width,heightPx=height,
                      effectiveDensityDpi=density,shortSideDp=height*160/density,nativeLandscape=True,hostFontScale='1.0',fixtureWidthDp=360,fixtureFontScale=1.0)
    return result


PREFERENCE_FILES = ('runmoni.xml', 'runmoni-community-account.xml', 'runmoni_appearance.xml')


def preference_file_snapshot(module, name):
    guard(name in PREFERENCE_FILES, 'Only the three explicitly owned product preference files may be inspected')
    names = module.adb('exec-out', 'run-as', module.PKG, 'ls', '-1', 'shared_prefs').decode().splitlines()
    guard(name + '.bak' not in names, 'No pending whole-file preference backup: ' + name)
    present = name in names
    raw = module.adb('exec-out', 'run-as', module.PKG, 'cat', 'shared_prefs/' + name) if present else b''
    if present:
        guard(len(raw) <= 2_000_000 and ET.fromstring(raw).tag == 'map', 'Bounded complete SharedPreferences XML: ' + name)
    return {'present': present, 'raw': raw}


def restore_preference_file(module, name, original):
    guard(name in PREFERENCE_FILES, 'Only an explicitly owned product preference file may be restored')
    guard(not module.pid(), 'Do not restore whole preference bytes under a live process')
    # Inspect any interrupted write, then replace only this exact owned XML.
    # A new .bak could override an exact XML on next launch; removing it here is
    # safe only because the durable original snapshot proved it was absent.
    pending = name + '.bak'
    names = module.adb('exec-out', 'run-as', module.PKG, 'ls', '-1', 'shared_prefs').decode().splitlines()
    if pending in names:
        module.sh('run-as', module.PKG, 'rm', '-f', 'shared_prefs/' + pending)
    if original['present']:
        ET.fromstring(original['raw'])
        module.adb('shell', '-T', 'run-as', module.PKG, 'dd', 'of=shared_prefs/' + name, data=original['raw'])
    else:
        module.sh('run-as', module.PKG, 'rm', '-f', 'shared_prefs/' + name)
    module.sh('sync')
    return preference_file_snapshot(module, name) == original


def account_snapshot(module):
    return preference_file_snapshot(module, 'runmoni-community-account.xml')


def restore_account(module, original):
    return restore_preference_file(module, 'runmoni-community-account.xml', original)


OWNED_FIXTURE_PREF = re.compile(r'^(?:profile-receipt-test-|sharing-choice-test-|widget-test-|expanded_wardrobe_|atelier_wallet_)[0-9a-fA-F-]+\.xml(?:\.bak)?$')


def owned_fixture_preferences(module):
    names = module.adb('exec-out', 'run-as', module.PKG, 'ls', '-1', 'shared_prefs').decode().splitlines()
    result = {}
    for name in sorted(names):
        if OWNED_FIXTURE_PREF.fullmatch(name):
            raw = module.adb('exec-out', 'run-as', module.PKG, 'cat', 'shared_prefs/' + name)
            guard(len(raw) <= 2_000_000, 'Bounded nonce-owned full preference bytes, including an interrupted new fixture write')
            result[name] = raw
    return result


def remove_new_owned_fixtures(module, original):
    guard(not module.pid(), 'Fixture cleanup requires a stopped app')
    current = owned_fixture_preferences(module)
    added = set(current) - set(original)
    for name in sorted(added):
        guard(OWNED_FIXTURE_PREF.fullmatch(name), 'Only allowlisted nonce-owned new test preferences may be removed')
        module.sh('run-as', module.PKG, 'rm', '-f', 'shared_prefs/' + name)
    for name, raw in sorted(original.items()):
        guard(OWNED_FIXTURE_PREF.fullmatch(name), 'Only observed original nonce-owned preference bytes may be restored')
        if current.get(name) != raw:
            module.adb('shell', '-T', 'run-as', module.PKG, 'dd', 'of=shared_prefs/' + name, data=raw)
    module.sh('sync')
    guard(owned_fixture_preferences(module) == original,
          'Original fixture file presence and whole XML preserved; only new test-owned files removed')
    return sorted(added)


def dispatcher_report_state(module, path):
    guard(path in {item[1] for item in DISPATCHER_REPORTS.values()}, 'Only the two exact dispatcher diagnostic paths may be inspected')
    quoted = shlex.quote(path)
    return shell(module, 'run-as', module.PKG, 'sh', '-c',
        'if [ -L ' + quoted + ' ]; then printf symlink; elif [ -f ' + quoted +
        ' ]; then printf regular; elif [ -e ' + quoted + ' ]; then printf other; else printf absent; fi')


def dispatcher_report_states(module):
    return {path: dispatcher_report_state(module, path) for _, path in DISPATCHER_REPORTS.values()}


def validate_dispatcher_report(report, mode, require_complete):
    expected_keys = {'schemaVersion', 'mode', 'scope', 'roundsRequested', 'roundsObserved',
                     'afterOnMainLooper', 'afterOffMainLooper', 'completed', 'observations'}
    guard(isinstance(report, dict) and set(report) == expected_keys, 'Exact controlled dispatcher report schema')
    guard(type(report['schemaVersion']) is int and report['schemaVersion'] == 1 and
          report['mode'] == mode and report['scope'] == DISPATCHER_REPORT_SCOPE,
          'Dispatcher report schema, mode and diagnostic-only scope match this fresh group')
    guard(type(report['completed']) is bool and type(report['roundsRequested']) is int and
          report['roundsRequested'] == 16 and type(report['roundsObserved']) is int and
          0 <= report['roundsObserved'] <= 16, 'Exactly16 requested rounds with bounded observed count')
    observations = report['observations']
    guard(isinstance(observations, list) and len(observations) <= 48, 'At most48 controlled round/stage observations')
    expected_order = [(round_number, stage) for round_number in range(16) for stage in ('before', 'worker', 'after')]
    observed_order = []
    for row in observations:
        guard(isinstance(row, dict) and set(row) == {'stage', 'round', 'thread', 'mainLooper', 'monotonicNanos', 'interceptor'},
              'Exact synthetic dispatcher observation fields')
        guard(type(row['round']) is int and 0 <= row['round'] < 16 and row['stage'] in ('before', 'worker', 'after') and
              type(row['mainLooper']) is bool and type(row['monotonicNanos']) is int and row['monotonicNanos'] > 0 and
              all(isinstance(row[key], str) and 0 < len(row[key]) <= 512 for key in ('thread', 'interceptor')),
              'Bounded actual thread, Looper, interceptor and monotonic-time observation')
        observed_order.append((row['round'], row['stage']))
    guard(len(observed_order) == len(set(observed_order)) and observed_order == expected_order[:len(observed_order)],
          'Unique ordered before/worker/after observations for every observed round')
    after = [row for row in observations if row['stage'] == 'after']
    workers = [row for row in observations if row['stage'] == 'worker']
    on_main = sum(row['mainLooper'] for row in after)
    guard(report['roundsObserved'] == len(after) and
          type(report['afterOnMainLooper']) is int and report['afterOnMainLooper'] == on_main and
          type(report['afterOffMainLooper']) is int and report['afterOffMainLooper'] == len(after) - on_main,
          'Dispatcher summary is derived exactly from observed after callbacks')
    guard(all(not row['mainLooper'] for row in workers), 'Observed physical-producer callbacks stay outside the Android main Looper')
    if report['completed'] or require_complete:
        guard(report['completed'] is True and report['roundsObserved'] == 16 and observed_order == expected_order,
              'A completed diagnostic contains16 rounds and all48 unique stage observations')
        if mode == 'standard':
            guard(on_main == 16 and report['afterOffMainLooper'] == 0, 'All16 Standard-dispatcher after callbacks resume on main')
    # Default is an observation. It has no minimum off-main count or escape assertion.
    return {'schemaVersion': report['schemaVersion'], 'mode': mode, 'completed': report['completed'],
            'roundsRequested': 16, 'roundsObserved': len(after), 'observationCount': len(observations),
            'uniqueRoundStageObservations': len(set(observed_order)),
            'stageCounts': {stage: sum(row['stage'] == stage for row in observations) for stage in ('before', 'worker', 'after')},
            'afterOnMainLooper': on_main, 'afterOffMainLooper': len(after) - on_main,
            'standardAll16AfterOnMainLooper': mode == 'standard' and len(after) == 16 and on_main == 16,
            'defaultMustEscapeAssertion': False, 'countsAsProductTest': False}


def export_dispatcher_report(module, attempt, group):
    mode, device_path = DISPATCHER_REPORTS[group['name']]
    state = dispatcher_report_state(module, device_path)
    group['diagnosticReportDeviceStateAfterRun'] = state
    guard(state == 'regular', 'Every dispatcher diagnostic must create its fresh regular report, including a failed case')
    # Bound transport itself, rather than reading an unbounded file and checking afterward.
    raw = module.adb('exec-out', 'run-as', module.PKG, 'head', '-c', str(DISPATCHER_REPORT_LIMIT + 1), device_path)
    guard(0 < len(raw) <= DISPATCHER_REPORT_LIMIT, 'Dispatcher diagnostic copy is at most64KiB')
    destination = E / (attempt + '-diagnostic-fixture-dispatcher-' + mode + '.json')
    exclusive_bytes(destination, raw, private=True)
    group['diagnosticReport'] = {'file': destination.name, 'sha256': sha(raw), 'bytes': len(raw),
        'fileMode': '0600', 'mode': mode, 'devicePath': device_path, 'copyLimitBytes': DISPATCHER_REPORT_LIMIT,
        'immutableExclusiveCreate': True, 'bothDeviceTargetsAbsentBeforeFirstInstrumentation': True,
        'countsAsProductTest': False}
    def unique_object(pairs):
        result = {}
        for key, value in pairs:
            guard(key not in result, 'No duplicate JSON fields in a dispatcher report')
            result[key] = value
        return result
    report = json.loads(raw.decode('utf-8', 'strict'), object_pairs_hook=unique_object)
    group['diagnosticReport'].update(validate_dispatcher_report(report, mode, group['instrumentationPassed']))
    group['diagnosticReportValidated'] = True


def clean_dispatcher_reports(module, before):
    paths = [item[1] for item in DISPATCHER_REPORTS.values()]
    guard(before == {path: 'absent' for path in paths}, 'Cleanup ownership requires both exact reports to have started absent')
    guard(not module.pid(), 'Dispatcher report cleanup requires a force-stopped app')
    observed = dispatcher_report_states(module)
    module.sh('run-as', module.PKG, 'rm', '-f', *paths)
    after = dispatcher_report_states(module)
    guard(after == before, 'Only the two new known dispatcher reports were removed and both are absent')
    return {'ownedDevicePaths': paths, 'bothAbsentBeforeFirstInstrumentation': True,
            'observedBeforeCleanup': observed, 'afterCleanup': after,
            'removedOnlyTwoNewKnownReportPaths': True, 'forceStoppedBeforeCleanup': True,
            'verifiedAbsentAfterCleanup': True, 'verifiedAbsentAfterEnvironmentRestoration': True,
            'broadCacheCleanup': False}


def run(args):
    os.umask(0o077)
    guard(re.fullmatch(r'binary-build[1-9][0-9]*\.json', args.binary) is not None, 'Explicit new binary-buildN.json reference')
    groups, planned_methods = phase_groups(args.phase)
    if args.groups:
        chosen = args.groups.split(',')
        guard(len(chosen) == len(set(chosen)) and set(chosen) <= {group[0] for group in groups}, 'Explicit subset of this phase groups')
        groups = [group for group in groups if group[0] in chosen]
        catalogue = test_catalogue()
        planned_methods = [method for group in groups for method in expand_selectors(group[2], catalogue)]
    planned = len(planned_methods)
    landscape_phase = args.phase == 'landscape'
    dispatcher_phase = args.phase == 'dispatcher'
    if dispatcher_phase:
        guard(len(groups) == 2 and {group[0] for group in groups} == set(DISPATCHER_REPORTS) and planned == 2,
              'The separate dispatcher comparison always selects both concrete one-method diagnostics')
    args.name = args.name or {'default': 'android-review1', 'native': 'native1', 'landscape': 'landscape1', 'native-sdk': 'map-sdk1', 'native-online': 'map-online1', 'firebase-sdk': 'firebase-sdk1', 'dispatcher': 'dispatcher1'}[args.phase]
    pattern = {'default': r'android-review[1-9][0-9]*', 'native': r'native[1-9][0-9]*', 'landscape': r'landscape[1-9][0-9]*', 'native-sdk': r'map-sdk[1-9][0-9]*', 'native-online': r'map-online[1-9][0-9]*', 'firebase-sdk': r'firebase-sdk[1-9][0-9]*', 'dispatcher': r'dispatcher[1-9][0-9]*'}[args.phase]
    guard(re.fullmatch(pattern, args.name) is not None, 'Fresh immutable attempt name for this phase')
    if args.phase == 'firebase-sdk':
        guard(args.demo_emulators_ready, 'Owner must prepare only the existing localhost demo Auth/Firestore emulators')
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
    proof = {'name': args.name, 'version': VERSION, 'versionCode': CODE, 'binaryReference': args.binary,
             'reviewPhase': args.phase, 'independentPhaseNoPriorResultAggregation': True,
             'startedAtUtc': now(), 'phase': 'created', 'passed': False, 'completed': False,
             'plannedProductMethods': 0 if dispatcher_phase else planned,
             'plannedMethodIdsFromCurrentSource': [] if dispatcher_phase else planned_methods,
             'plannedDiagnosticMethods': planned if dispatcher_phase else 0,
             'plannedDiagnosticMethodIdsFromCurrentSource': planned_methods if dispatcher_phase else [],
             'actualPassedProductMethods': 0, 'actualPassedDiagnosticMethods': 0,
             'diagnosticMethodsCountAsProductTests': False, 'groups': [], 'fixtures': [], 'executedDriver': executed.name,
             'executedDriverSha256': sha(raw_source), 'observerHelper': executed_helper.name, 'observerHelperSha256': sha(helper_source),
             'physicalDeviceVerified': False, 'googleLoginVerified': False,
             'productionWritesVerified': False, 'android31RuntimeVerified': False, 'signedReleaseRuntimeVerified': False,
             'observerMethodsCountAsProductTests': False,
             'environmentRestorationScope': environment_restoration_scope(),
             'preferenceRestorationScope': {'productFiles': list(PREFERENCE_FILES),
                'equality': 'Original presence plus whole bytes, checked again after environment restoration',
                'fixtureFiles': 'Only explicit nonce-owned allowlisted names, original bytes preserved',
                'broadPreferenceDirectoryCleanup': False, 'livePreferenceReplacement': False},
             'limitations': ['UI screens use safe ComponentActivity fixtures; share-card PNGs are direct Android Canvas renderer fixtures. Neither is actual MainActivity or production data.',
                            'Native page cases inspect actual OS/IME insets at320dp/font2. Map SDK failure cases use a missing local asset style, never the public basemap.',
                            'Appearance XML is restored byte-for-byte with original presence; this is a device preference, never an account/profile change. Database preservation includes backup origins; no DB copy-back is performed.',
                            'The explicit native-online phase alone permits the production OpenFreeMap HTTPS client: one ComponentActivity scene with synthetic Seoul points/runner and actual native touch/render observations. It is not GPS, Firebase, MainActivity, a real-person location, or a provider availability SLA.',
                            'The separate dispatcher phase measures two test effect contexts. Its method/report counts never contribute to product validation; Default has no must-escape assertion.']}
    def checkpoint(phase):
        proof['phase'], proof['updatedAtUtc'] = phase, now()
        atomic_json(out, proof)
        print(json.dumps({'phase': phase, 'actualPassedProductMethods': proof['actualPassedProductMethods'],
                          'actualPassedDiagnosticMethods': proof['actualPassedDiagnosticMethods']}, ensure_ascii=False), flush=True)
    checkpoint('created')
    module, before_xml, before_environment, expected_profile = None, None, None, None
    binary, can_mutate, before_fixed_rotation, before_fixed_policy = None, False, None, None
    before_account, before_appearance, before_fixture_preferences, before_data = None, None, None, None
    before_dispatcher_reports = None
    try:
        binary_path = E / args.binary
        binary_raw = binary_path.read_bytes()
        binary = json.loads(binary_raw)
        guard(binary['buildSucceeded'] and binary['version'] == VERSION and binary['versionCode'] == CODE, 'Frozen0.9.0-dev/code32 build')
        freeze_name = binary.get('sourceFreezeReference', '')
        guard(re.fullmatch(r'source-freeze[1-9][0-9]*\.json', freeze_name) is not None, 'Explicit source-freezeN evidence')
        freeze_raw = (E / freeze_name).read_bytes()
        freeze = json.loads(freeze_raw)
        guard(freeze['version'] == VERSION and freeze['versionCode'] == CODE and
              freeze['sourceMaps'] == {key: binary[key] for key in MAPS}, 'New freeze and binary have identical six source maps')
        guard(binary.get('sourceFreezeSha256') == sha(freeze_raw), 'Build records this exact fresh freeze hash')
        proof.update(sourceFreezeReference=freeze_name, sourceFreezeSha256=sha(freeze_raw),
                     onlineNativeBasemapVerified=False, syntheticOnlineNativeMapFixtureVerified=False,
                     publicMapRequestsPermitted=args.phase == 'native-online', actualMainActivityMapVerified=False,
                     onlineNativeBasemapVerificationScope='Only a successful explicit native-online ComponentActivity synthetic scene; never MainActivity/GPS/Firebase',
                     productionMapAvailabilityContractClaimed=False,
                     firebaseDemoSdkOnly=args.phase == 'firebase-sdk')
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
            digest = module.sh('sha256sum', path, timeout=120).split()[0]
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
        if dispatcher_phase:
            before_dispatcher_reports = dispatcher_report_states(module)
            proof['dispatcherReportPreflight'] = {'deviceStates': before_dispatcher_reports,
                'bothAbsentBeforeFirstInstrumentation': all(value == 'absent' for value in before_dispatcher_reports.values()),
                'existingReportsRemovedOrOverwritten': False}
            guard(proof['dispatcherReportPreflight']['bothAbsentBeforeFirstInstrumentation'],
                  'Both dispatcher report names must already be absent; preserve any existing report and stop')
            checkpoint('both-dispatcher-reports-observed-absent-before-any-mutation')
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
        main_snapshot = preference_file_snapshot(module, 'runmoni.xml')
        guard(main_snapshot['present'], 'Existing main preferences are required; do not create or reset an absent profile')
        before_xml = main_snapshot['raw']
        ET.fromstring(before_xml)
        proof['mainPreferencesOriginallyPresent'] = True
        xml_path = W / (args.name + '-private-before.xml')
        exclusive_bytes(xml_path, before_xml, private=True)
        expected_profile = profile_from_xml(before_xml)
        proof['privateXmlBackup'] = {'file': xml_path.name, 'sha256': sha(before_xml), 'mode': '0600'}
        checkpoint('xml-and-environment-backups-durable-before-any-mutation')
        before_account = account_snapshot(module)
        account_path = W / (args.name + '-private-account-before.xml')
        exclusive_bytes(account_path, before_account['raw'], private=True)
        proof['accountPreferencesOriginallyPresent'] = before_account['present']
        proof['privateAccountBackup'] = {'file': account_path.name, 'sha256': sha(before_account['raw']), 'mode': '0600'}
        before_appearance = preference_file_snapshot(module, 'runmoni_appearance.xml')
        appearance_path = W / (args.name + '-private-appearance-before.xml')
        exclusive_bytes(appearance_path, before_appearance['raw'], private=True)
        proof['appearancePreferencesOriginallyPresent'] = before_appearance['present']
        proof['privateAppearanceBackup'] = {'file': appearance_path.name, 'sha256': sha(before_appearance['raw']), 'mode': '0600'}
        before_fixture_preferences = owned_fixture_preferences(module)
        fixture_path = W / (args.name + '-private-fixture-preferences-before.json')
        fixture_bytes = (json.dumps({name: base64.b64encode(raw).decode('ascii')
            for name, raw in sorted(before_fixture_preferences.items())}, sort_keys=True) + '\n').encode()
        exclusive_bytes(fixture_path, fixture_bytes, private=True)
        proof['originalOwnedFixturePreferenceCount'] = len(before_fixture_preferences)
        proof['privateOwnedFixtureBackup'] = {'file': fixture_path.name, 'sha256': sha(fixture_bytes), 'mode': '0600'}
        before_data = module.snapshot('before')
        data_path = W / (args.name + '-private-data-before.json')
        data_bytes = (json.dumps(before_data, ensure_ascii=False, sort_keys=True) + '\n').encode()
        exclusive_bytes(data_path, data_bytes, private=True)
        proof['privateDataBefore'] = {'file': data_path.name, 'sha256': sha(data_bytes), 'mode': '0600',
            'tables': {key: len(value) for key, value in before_data.items()}}
        checkpoint('all-three-preferences-owned-fixtures-and-data-durable-before-mutation')
        can_mutate = True
        module.force_stop()
        guard(preference_file_snapshot(module, 'runmoni.xml') == main_snapshot, 'Quiescent original XML presence/bytes remained exact before fixture injection')
        guard(preference_file_snapshot(module, 'runmoni_appearance.xml') == before_appearance,
              'Quiescent original appearance presence/bytes remained exact before fixture injection')
        guard(account_snapshot(module) == before_account, 'Quiescent account markers remained exact before fixture injection')
        fixture = ET.fromstring(before_xml)
        for kind, key, value in [('string', 'communityBackend', 'firebase'), ('string', 'apiUrl', 'http://127.0.0.1:8787'),
                                 ('string', 'appEntryChoiceV1', 'guest'), ('boolean', 'communityGuest', 'true'),
                                 ('boolean', 'publicLocation', 'false'), ('boolean', 'voiceGuidance', 'false'), ('boolean', 'autoPause', 'false')]:
            module.set_pref(fixture, kind, key, value)
        module.write_prefs(ET.tostring(fixture, encoding='utf-8', xml_declaration=True))
        # Formal fixtures must not trigger a MET lookup from landscape's preparatory MainActivity.
        for permission in ['android.permission.ACCESS_COARSE_LOCATION', 'android.permission.ACCESS_FINE_LOCATION']:
            shell(module, 'pm', 'revoke', module.PKG, permission)
        proof['previewLocationPermissionsTemporarilyRevokedForSyntheticFixtures'] = True
        proof['previewLocationPermissionsTemporarilyRevokedForOfflineFixtures'] = args.phase != 'native-online'
        module.wake()
        for key in ['window_animation_scale', 'transition_animation_scale', 'animator_duration_scale']:
            module.sh('settings', 'put', 'global', key, '0')
        proof['motionScaleObservedBeforeProductGroups'] = {
            key: module.sh('settings', 'get', 'global', key)
            for key in ['window_animation_scale', 'transition_animation_scale', 'animator_duration_scale']}
        guard(all(value in ('0', '0.0') for value in proof['motionScaleObservedBeforeProductGroups'].values()),
              'Actual system animation settings are zero before selected-state checks')
        proof['motionScaleZeroObserved'] = True
        checkpoint('safe-local-fixture-ready')
        for group_name, expected, classes, native, captures in groups:
            full_classes = [PREFIX + name for name in classes]
            group = {'name': group_name, 'expected': expected, 'classes': full_classes, 'status': 'preparing',
                     'source': 'Fresh diagnostic am instrument run; no earlier execution reused' if dispatcher_phase else 'Fresh product am instrument run; no earlier execution reused',
                     'countsAsProductTest': not dispatcher_phase, 'diagnosticOnly': dispatcher_phase, 'passed': False}
            proof['groups'].append(group)
            checkpoint('preparing-' + group_name)
            if native == 'landscape-font1':
                proof['fixedToUserRotation']['forcedEnabledForLandscape'] = True
            group['viewport'] = viewport(module, native)
            if dispatcher_phase:
                mode, device_path = DISPATCHER_REPORTS[group_name]
                guard(dispatcher_report_state(module, device_path) == 'absent',
                      'The current dispatcher report target remains absent immediately before its exact execution')
                group.update(diagnosticMode=mode, diagnosticReportDevicePath=device_path,
                             diagnosticTargetAbsentBeforeThisInstrumentation=True)
            if not dispatcher_phase:
                module.sh('run-as', module.PKG, 'rm', '-f', 'cache/design-review/minimi-readiness.txt')
                module.sh('run-as', module.PKG, 'rm', '-f', 'cache/design-review/update-indicator-layout.jsonl')
                guard(shell(module, 'run-as', module.PKG, 'sh', '-c', 'test ! -e cache/design-review/minimi-readiness.txt') == '',
                      'Current group avatar readiness trace starts absent')
            for name in captures:
                guard(re.fullmatch(r'[A-Za-z0-9_-]+', name) is not None, 'Allowlisted review-cache name')
                module.sh('run-as', module.PKG, 'rm', '-f', ('cache/run-share-review/' if name.startswith('share-card-') else 'cache/widget-review/' if name.startswith(('widget-refresh-', 'widget-night-')) else 'cache/design-review/') + name + '.png')
                guard(shell(module, 'run-as', module.PKG, 'sh', '-c', 'test ! -e ' + ('cache/run-share-review/' if name.startswith('share-card-') else 'cache/widget-review/' if name.startswith(('widget-refresh-', 'widget-night-')) else 'cache/design-review/') + name + '.png') == '',
                      'Fixture cache target absent before current product group')
            if group_name == 'native-form-pages':
                module.sh('run-as', module.PKG, 'rm', '-f', 'cache/design-review/modal-page-window-bounds.jsonl', 'cache/design-review/modal-page-bounds-private-failure.png')
            if group_name == 'native-online-map':
                module.sh('run-as', module.PKG, 'rm', '-f', 'cache/design-review/native-online-observations.json',
                          'cache/design-review/native-online-private-failure.png')
                diagnostic_names = ['native-online-' + stage + '-' + suffix + '.png'
                                    for stage in ('before', 'after')
                                    for suffix in ('app-source-icon', 'expected-display', 'native-registered-icon', 'native-texture-private')]
                module.sh('run-as', module.PKG, 'rm', '-f', *['cache/design-review/' + name for name in diagnostic_names])
                for name in diagnostic_names:
                    guard(shell(module, 'run-as', module.PKG, 'sh', '-c',
                                'test ! -e cache/design-review/' + name) == '', 'Current native bitmap diagnostic starts absent')
                group['nativeBitmapDiagnosticTargetsCleared'] = diagnostic_names
                group['nativeBitmapDiagnosticsAreNotSystemScreenshots'] = True
                guard(shell(module, 'run-as', module.PKG, 'sh', '-c',
                            'test ! -e cache/design-review/native-online-observations.json') == '',
                      'Online native observations start absent for this exact execution')
            loading_diagnostics_before = set() if dispatcher_phase else loading_diagnostic_names(module)
            group['fixtureFreshness'] = ('Both dispatcher targets started absent; this target rechecked absent before execution; no existing report overwritten'
                                         if dispatcher_phase else 'Allowlisted cache images deleted before this exact execution')
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
                if dispatcher_phase:
                    group['instrumentationPassed'] = bool(group.get('passed'))
                    try:
                        export_dispatcher_report(module, args.name, group)
                    except BaseException as report_failure:
                        group.update(passed=False, diagnosticReportValidated=False,
                            diagnosticReportFailureType=type(report_failure).__name__,
                            diagnosticReportFailure=str(report_failure) if isinstance(report_failure, AssertionError) else 'Inspect the preserved bounded diagnostic report and raw instrumentation output')
                group['elapsedSeconds'], group['finishedAtUtc'] = round(time.monotonic() - started, 2), now()
                group['status'] = 'passed' if group.get('passed') else 'failed'
                readiness = b'' if dispatcher_phase else module.adb('shell', '-T', shlex.join(['run-as', module.PKG, 'sh', '-c',
                    'if [ -f cache/design-review/minimi-readiness.txt ]; then cat cache/design-review/minimi-readiness.txt; fi']))
                if readiness.strip():
                    guard(len(readiness) < 100_000 and all(re.fullmatch(r'.{1,250}: (?:awaitAvatarReady=(?:true|false); )?waitMillis=\d+; timeoutMillis=120000', line)
                          for line in readiness.decode().splitlines()), 'Only actual static-context readiness metrics may be exported')
                    readiness_path=E/(args.name+'-'+group_name+'-minimi-readiness.txt')
                    exclusive_bytes(readiness_path,readiness)
                    group.update(minimiReadinessRaw=readiness_path.name,minimiReadinessRawSha256=sha(readiness),
                                 minimiReadinessPerformanceSlaClaimed=False)
                proof['actualPassedProductMethods'] = sum(item.get('actual', 0) or 0 for item in proof['groups'] if item.get('passed') and item['countsAsProductTest'])
                proof['actualPassedDiagnosticMethods'] = sum(item.get('actual', 0) or 0 for item in proof['groups'] if item.get('passed') and item['diagnosticOnly'])
                checkpoint('finished-' + group_name)
            if group_name == 'native-form-pages':
                page_geometry = shell(module, 'run-as', module.PKG, 'sh', '-c',
                    'if [ -f cache/design-review/modal-page-window-bounds.jsonl ]; then cat cache/design-review/modal-page-window-bounds.jsonl; fi')
                if page_geometry:
                    guard(len(page_geometry) <= 200_000 and all(isinstance(json.loads(line), dict) for line in page_geometry.splitlines()), 'Bounded own-fixture geometry JSONL')
                    page_path = E / (args.name + '-native-form-page-bounds.jsonl')
                    exclusive_bytes(page_path, page_geometry.encode())
                    group.update(pageWindowGeometry=page_path.name, pageWindowGeometrySha256=sha(page_path.read_bytes()))
                private_png = module.adb('shell', '-T', shlex.join(['run-as', module.PKG, 'sh', '-c',
                    'if [ -f cache/design-review/modal-page-bounds-private-failure.png ]; then cat cache/design-review/modal-page-bounds-private-failure.png; fi']))
                if private_png:
                    guard(len(private_png) <= 4_000_000 and private_png[:8] == b'\x89PNG\r\n\x1a\n', 'Own-failure PNG diagnostic only')
                    private_path = W / (args.name + '-private-form-bounds-failure.png')
                    exclusive_bytes(private_path, private_png, private=True)
                    group.update(privateFailureCapture=private_path.name, privateFailureCaptureSha256=sha(private_png))
            geometry = '' if dispatcher_phase else shell(module, 'run-as', module.PKG, 'sh', '-c',
                             'if [ -f cache/design-review/update-indicator-layout.jsonl ]; then cat cache/design-review/update-indicator-layout.jsonl; fi')
            if geometry:
                geometry_raw = (geometry + '\n').encode()
                rows = [json.loads(line) for line in geometry.splitlines()]
                geometry_path = E / (args.name + '-' + group_name + '-indicator-layout.jsonl')
                exclusive_bytes(geometry_path, geometry_raw)
                group.update(indicatorLayoutRaw=geometry_path.name, indicatorLayoutRawSha256=sha(geometry_raw),
                             indicatorLayoutRecordCount=len(rows))
                checkpoint('fresh-indicator-layout-observations-' + group_name)
            guard(local_profile(module) == expected_profile, 'Original stored profile preserved after group: ' + group_name)
            group['originalProfilePreserved'] = True
            guard(module.dump() is None, 'Independent fixtures must not create/take over a recording service')
            if group_name == 'native-online-map':
                online_exists = shell(module, 'run-as', module.PKG, 'sh', '-c',
                    'if [ -f cache/design-review/native-online-observations.json ]; then printf present; fi') == 'present'
                if online_exists:
                    online_bytes = module.adb('exec-out', 'run-as', module.PKG, 'cat', 'cache/design-review/native-online-observations.json')
                    guard(len(online_bytes) < 100_000, 'Bounded synthetic-only native observations')
                    online = json.loads(online_bytes)
                    guard(online['syntheticFixture'] is True and online['actualMainActivity'] is False and
                          online['actualGps'] is False and online['firebaseUsed'] is False and
                          online['profileOrRecordingModified'] is False and
                          online['productionFactoryAndTransportUsed'] is True and
                          online['styleUri'] == 'https://tiles.openfreemap.org/styles/liberty' and
                          online['observationBudgetMillis'] == 120_000 and online['providerSlaClaimed'] is False,
                          'Only declared public-style synthetic scene metadata is exported')
                    online_path = E / (args.name + '-native-online-observations.json')
                    exclusive_bytes(online_path, online_bytes)
                    group.update(onlineNativeObservationRaw=online_path.name,
                                 onlineNativeObservationRawSha256=sha(online_bytes),
                                 onlineNativeObservationCompleted=online['completed'])
                    if group['passed']:
                        guard(online['completed'] is True and online['nativeFailure'] is None and
                              online['routePointCount'] == 4 and online['routeSegmentCount'] == 2 and
                              online['runnerCount'] == 1 and online['mapViewConstructions'] == 1,
                              'Successful online scene completed all declared native observations')
                        visible = [row for row in online['stages'] if row.get('stage') in ('visible-avatar-before', 'visible-avatar-after')]
                        guard(len(visible) == 2 and {row['stage'] for row in visible} == {'visible-avatar-before', 'visible-avatar-after'},
                              'Both before/after avatar visibility observations are required')
                        guard(len({row['expectedImageId'] for row in visible}) == 2 and all(
                            row['passed'] is True and row['projectionSupported'] is True and
                            row['sourceRefersToCurrentImage'] is True and row['renderedSymbolRefersToCurrentImage'] is True and
                            row['bitmapRoundTripPixelMismatches'] == 0 and row['global']['matched'] >= 12 and
                            row['global']['fraction'] >= .60 and row['global']['matchedDistinctChromaticColors'] >= 2 and
                            row['clothingBand42To72Percent']['matched'] >= 8 and row['clothingBand42To72Percent']['fraction'] >= .60
                            for row in visible), 'Native source readback and actual outfit pixels must both pass')
                        proof['actualNativeAvatarPixelsVerified'] = True
                        proof['actualNativeAvatarPixelsVerificationScope'] = 'Synthetic ComponentActivity native texture before/after outfit change; not Main/GPS/phone'
                else:
                    guard(not group['passed'], 'A passed online scene must produce fresh native observations')
                online_failure_exists = shell(module, 'run-as', module.PKG, 'sh', '-c',
                    'if [ -f cache/design-review/native-online-private-failure.png ]; then printf present; fi') == 'present'
                if online_failure_exists:
                    guard(not group['passed'], 'Failure-only native scene capture cannot belong to a passed group')
                    failure_png = module.adb('exec-out', 'run-as', module.PKG, 'cat', 'cache/design-review/native-online-private-failure.png')
                    guard(len(failure_png) < 6_000_000 and failure_png[:8] == b'\x89PNG\r\n\x1a\n', 'Bounded current online fixture failure image')
                    private_path = W / (args.name + '-private-native-online-failure.png')
                    exclusive_bytes(private_path, failure_png, private=True)
                    group.update(privateFailureCapture=private_path.name, privateFailureCaptureSha256=sha(failure_png))
            if not group['passed']:
                for filename in ([] if dispatcher_phase else sorted(loading_diagnostic_names(module) - loading_diagnostics_before)):
                    raw_diagnostic = module.adb('exec-out', 'run-as', module.PKG, 'cat', 'cache/design-review/' + filename)
                    guard(len(raw_diagnostic) <= (2_000_000 if filename.endswith('.png') else 250_000), 'Bounded failure-only controlled fixture diagnostic')
                    private_path = W / (args.name + '-' + group_name + '-private-' + filename)
                    exclusive_bytes(private_path, raw_diagnostic, private=True)
                    group.setdefault('privateLoadingDiagnostics', []).append({'file': private_path.name,
                        'sha256': sha(raw_diagnostic), 'bytes': len(raw_diagnostic), 'mode': '0600',
                        'newSinceThisGroupStarted': True, 'publicBodyIncluded': False})
                checkpoint('failed-group-preserved-continue-independent-' + group_name)
                continue
            for name in captures:
                raw = module.adb('exec-out', 'run-as', module.PKG, 'cat', ('cache/run-share-review/' if name.startswith('share-card-') else 'cache/widget-review/' if name.startswith(('widget-refresh-', 'widget-night-')) else 'cache/design-review/') + name + '.png')
                width, height = module.dimensions(raw)
                destination = E / (args.name + '-' + name + '.png')
                exclusive_bytes(destination, raw)
                proof['fixtures'].append({'file': destination.name, 'sha256': sha(raw), 'bytes': len(raw), 'width': width, 'height': height,
                    'sourceGroup': group_name, 'sourceRaw': raw_path.name, 'sourceKind': ('API29 system screenshot of actual production MapLibre/OpenFreeMap rendering in ComponentActivity with synthetic Seoul route/runner; no GPS/Firebase/MainActivity' if name.startswith('native-online-') else 'Android production approved-avatar renderer bitmap contact export fixture; not a screenshot or MainActivity' if name.startswith('atelier-expansion-') else 'Android Canvas renderer PNG export fixture, not a screenshot' if name.startswith('share-card-') else 'Android RemoteViews synthetic snapshot PNG fixture, not Launcher/Main screenshot' if name.startswith(('widget-refresh-', 'widget-night-')) else 'API29 ComponentActivity screenshot/Compose-target fixture with synthetic controls; not MainActivity'),
                    'freshness': 'Cache target removed immediately before the successful current group; newly created file read afterward',
                    'actualMainActivityScreenshot': False, 'actualSystemNotificationInteraction': False,
                    'native320Font2Group': native is True,
                    'nativeLandscape320ShortSideFont1HostFixtureFont1Group': native == 'landscape-font1'})
                checkpoint('fresh-fixture-exported-' + name)
            checkpoint('profile-and-fixtures-verified-' + group_name)
        proof['sourceMapsAfter'] = source_maps(binary)
        guard(proof['actualPassedDiagnosticMethods' if dispatcher_phase else 'actualPassedProductMethods'] == planned,
              'Every method in this independent fresh diagnostic phase passed' if dispatcher_phase else 'Every method in this independent fresh product phase passed')
        guard(sha((E / args.binary).read_bytes()) == proof['binaryReferenceSha256'], 'Build evidence remained immutable')
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
                    proof['preferencesRestoredExactly'] = restore_preference_file(module, 'runmoni.xml', {'present': True, 'raw': before_xml})
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
            try:
                guard(before_account is not None, 'Account backup exists')
                proof['communityAccountPreferencesRestoredExactly'] = restore_account(module, before_account)
                guard(proof['communityAccountPreferencesRestoredExactly'], 'Original account marker file/existence exactly restored')
                checkpoint('account-preferences-restoration-attempted')
            except BaseException as failure:
                proof.update(communityAccountPreferencesRestoredExactly=False, accountRestoreFailureType=type(failure).__name__)
                checkpoint('account-preferences-restoration-failed')
            try:
                guard(before_appearance is not None, 'Appearance backup exists before any mutation')
                proof['appearancePreferencesRestoredExactly'] = restore_preference_file(module, 'runmoni_appearance.xml', before_appearance)
                guard(proof['appearancePreferencesRestoredExactly'], 'Original appearance XML bytes/existence exactly restored')
                checkpoint('appearance-preferences-restoration-attempted')
            except BaseException as failure:
                proof.update(appearancePreferencesRestoredExactly=False, appearanceRestoreFailureType=type(failure).__name__)
                checkpoint('appearance-preferences-restoration-failed')
            try:
                guard(before_fixture_preferences is not None, 'Original nonce-owned fixture bytes were observed')
                proof['removedOnlyNewOwnedFixturePreferences'] = remove_new_owned_fixtures(module, before_fixture_preferences)
                proof['ownedFixturePreferencesCleaned'] = True
            except BaseException as failure:
                proof.update(ownedFixturePreferencesCleaned=False, fixtureCleanupFailureType=type(failure).__name__)
                checkpoint('owned-fixture-cleanup-failed')
            try:
                guard(before_data is not None, 'Original database rows were observed before any mutation')
                after_data = module.snapshot('after')
                data_after_path = W / (args.name + '-private-data-after.json')
                data_after_bytes = (json.dumps(after_data, ensure_ascii=False, sort_keys=True) + '\n').encode()
                exclusive_bytes(data_after_path, data_after_bytes, private=True)
                proof['privateDataAfter'] = {'file': data_after_path.name, 'sha256': sha(data_after_bytes), 'mode': '0600',
                    'tables': {key: len(value) for key, value in after_data.items()}}
                proof['originalDatabaseRowsPreservedExactly'] = module.canonical(after_data) == module.canonical(before_data)
                guard(proof['originalDatabaseRowsPreservedExactly'], 'All original rows and cloud backup origins remain exact; no DB copy-back')
                checkpoint('database-row-preservation-observed')
            except BaseException as failure:
                proof.update(originalDatabaseRowsPreservedExactly=False, dataPreservationFailureType=type(failure).__name__)
                checkpoint('database-row-preservation-failed')
            # A preference/data/rotation-policy failure must not skip unrelated owned settings and permissions.
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
        if module is not None and can_mutate and dispatcher_phase:
            # Run after the other restoration attempts, even after a failed case
            # or environment restore. No other cache name is removed here.
            try:
                module.force_stop()
                proof['dispatcherReportCleanup'] = clean_dispatcher_reports(module, before_dispatcher_reports)
                checkpoint('only-two-new-dispatcher-reports-cleaned-after-environment-restoration')
            except BaseException as failure:
                proof['dispatcherReportCleanup'] = {'verifiedAbsentAfterCleanup': False,
                    'verifiedAbsentAfterEnvironmentRestoration': False, 'failureType': type(failure).__name__,
                    'broadCacheCleanup': False}
                checkpoint('dispatcher-report-cleanup-failed')
        if module is not None and can_mutate:
            try:
                guard(not module.pid(), 'Final whole-file preference proof requires a force-stopped app')
                final_main = preference_file_snapshot(module, 'runmoni.xml')
                final_account = account_snapshot(module)
                final_appearance = preference_file_snapshot(module, 'runmoni_appearance.xml')
                final_fixture_preferences = owned_fixture_preferences(module)
                proof['allPreferencesStillExactAfterEnvironmentRestoration'] = (
                    final_main == {'present': True, 'raw': before_xml} and final_account == before_account and
                    final_appearance == before_appearance and final_fixture_preferences == before_fixture_preferences)
                guard(proof['allPreferencesStillExactAfterEnvironmentRestoration'],
                      'runmoni/account/appearance and all original owned XML remain exact after every restoration step')
            except BaseException as failure:
                proof.update(allPreferencesStillExactAfterEnvironmentRestoration=False,
                             finalPreferenceObservationFailureType=type(failure).__name__)
        if module is not None:
            proof['observerExecutionCount'] = module.P.get('observerDumps', 0)
            proof['systemUiWaitActionCount'] = len(module.P.get('systemUiWaitActions', []))
        proof['driverSourceUnchanged'] = sha(Path(__file__).read_bytes()) == proof['executedDriverSha256']
        proof['observerHelperSourceUnchanged'] = sha(BG.read_bytes()) == proof['observerHelperSha256']
        proof['passed'] = bool(proof['passed'] and proof['driverSourceUnchanged'] and proof['observerHelperSourceUnchanged'] and proof.get('preferencesRestoredExactly') and
                               proof.get('profileRestoredExactly') and proof.get('communityAccountPreferencesRestoredExactly') and
                               proof.get('appearancePreferencesRestoredExactly') and proof.get('allPreferencesStillExactAfterEnvironmentRestoration') and
                               proof.get('originalDatabaseRowsPreservedExactly') and proof.get('ownedFixturePreferencesCleaned') and proof.get('controlledSettingsPermissionsPowerRestoredExactly') and
                               (not dispatcher_phase or proof.get('dispatcherReportCleanup', {}).get('verifiedAbsentAfterEnvironmentRestoration') is True) and
                               (not landscape_phase or proof.get('fixedToUserRotation', {}).get('restoredExactly') is True))
        if binary is not None:
            try:
                proof['sourceMapsAfterRestoration'] = source_maps(binary)
                proof['binaryEvidenceUnchanged'] = sha((E / args.binary).read_bytes()) == proof['binaryReferenceSha256']
                guard(proof['binaryEvidenceUnchanged'], 'Frozen build evidence unchanged after restoration')
            except BaseException as observation_failure:
                proof['postRestorationSourceObservationFailureType'] = type(observation_failure).__name__
                proof['passed'] = False
        proof['syntheticOnlineNativeMapFixtureVerified'] = bool(proof['passed'] and args.phase == 'native-online')
        proof['onlineNativeBasemapVerified'] = proof['syntheticOnlineNativeMapFixtureVerified']
        proof['completed'], proof['finishedAtUtc'] = True, now()
        checkpoint('complete-passed' if proof['passed'] else 'complete-failed')
    return 0 if proof['passed'] else 1


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--phase', choices=['default','native','landscape','native-sdk','native-online','firebase-sdk','dispatcher'], default='default')
    parser.add_argument('--binary', required=True, help='New local binary-buildN.json in this review directory')
    parser.add_argument('--groups', default=None, help='Optional independent comma-separated subset; never imports prior results')
    parser.add_argument('--demo-emulators-ready', action='store_true', help='Only after owner prepares existing localhost demo emulators; no production Firebase')
    parser.add_argument('--name', default=None, help='Fresh immutable attempt name; default android-review1/native1/dispatcher1 by phase')
    raise SystemExit(run(parser.parse_args()))
