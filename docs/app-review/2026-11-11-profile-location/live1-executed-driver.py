"""Root-owned actual MainActivity review of 0.4.2 profile retry/local save and current area.

No installation, authentication, run creation, DB writes, proxy setup or AVD
shutdown. The Root runs this only after scoped Android tests and debug proxy
setup; original XML/profile/DB rows/owned settings are restored in finally.
"""
from pathlib import Path
import argparse
import datetime
import hashlib
import importlib.util
import json
import os
import re
import time
import xml.etree.ElementTree as ET

R = Path('/workspace/runmoni')
W = R / 'work/profile-location'
E = R / 'docs/app-review/2026-11-11-profile-location'
os.umask(0o077)
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--name', required=True)
a = parser.parse_args()
assert re.fullmatch(r'live[1-9]\d*', a.name)


def module(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


hpath = R / 'work/running-experience/actual-ui-helper6.py'
envpath = W / 'android-review.py'
m = module('actual_profile_location_main_observer', hpath)
env = module('profile_location_review_environment', envpath)
m.W, m.E, m.NAME, m.VERSION, m.CODE = W, E, a.name, '0.4.2-dev', 24
sha = env.sha
out = E / (a.name + '.json')
assert not out.exists()
P = {
    'name': a.name, 'version': '0.4.2-dev', 'versionCode': 24, 'api': 29,
    'avd': 'Runmoni_Cloud_29', 'acceleration': 'TCG', 'binaryReference': 'binary-build4.json',
    'passed': False, 'completed': False, 'captures': [], 'startedAtUtc': env.now(),
    'physicalDeviceVerified': False, 'googleLoginVerified': False,
    'productionWritesVerified': False, 'android31RuntimeVerified': False,
    'signedReleaseRuntimeVerified': False, 'outdoorGpsVerified': False,
    'dozeVerified': False, 'oemBatteryPolicyVerified': False, 'syntheticGps': True,
    'databaseWritesByReview': False, 'sourceKind': 'actual Android MainActivity',
    'systemUiWaitActions': [], 'accessibilityRetries': 0, 'actualTaps': [],
    'profileSaves': [], 'profileSaveCount': 0,
    'saveCountMeaning': 'Observed local device-name change after a real Save and editor closure; not an internal serial/write-call count',
    'anonymousGuestFixture': True, 'syntheticProfileName': 'AreaDevice24',
    'deviceTimezone': None, 'weatherHttpStatusMeasured': False,
    'weatherRequestCoordinatesIndependentlyLogged': False,
    'decorativeAvatarBoundsMeasuredByAccessibility': False,
    'avatarWeatherSideBySideConfirmedByThisDriver': False,
    'requiredIndependentRootPixelReview': True,
    'rootPrivacyReviewRequiredBeforePublication': True,
    'profileConnectionAttempts': [], 'profileConnectionAttemptTapCount': 0,
    'profileUidDirectlyObserved': False, 'profileReceiptSerialsDirectlyObserved': False,
    'profileServerPermissionDeniedVerified': False, 'successfulServerProfileConnectionVerified': False,
    'profileErrorRemovalBetweenAttemptsObserved': False,
    'nativeGeocoderBackendResultIndependentlyVerified': False,
    'geocoderQueryCoordinatesIndependentlyLogged': False,
    'nativeMapFreshHttpStatusMeasured': False,
    'nativeMapBitmapContentIndependentlyVerifiedByDriver': False,
    'serverConnectionReceiptObservedInMainUi': False,
    'appTotalNetworkTrafficMeasured': False,
    'newAuthenticationRequestedByDriver': False,
    'originalGpsFixRestoredByThisDriver': False,

}
m.P = P
can_restore = False
old = before = baseline = binary = old_account = None
raw = Path(__file__).read_bytes()
for filename, data in (
    (a.name + '-executed-driver.py', raw),
    (a.name + '-actual-ui-helper.py', hpath.read_bytes()),
    (a.name + '-environment-helper.py', envpath.read_bytes()),
):
    env.exclusive_bytes(E / filename, data)
P.update(executedDriver=a.name + '-executed-driver.py', executedDriverSha256=sha(raw),
         observerHelper=a.name + '-actual-ui-helper.py', observerHelperSha256=sha(hpath.read_bytes()),
         environmentHelper=a.name + '-environment-helper.py', environmentHelperSha256=sha(envpath.read_bytes()))


def stage(label):
    P['stage'], P['updatedAtUtc'] = label, env.now()
    env.atomic_json(out, P)
    print(label, flush=True)


# Keep observer-only SystemUI diagnostics private. Normal Main captures call the
# unchanged helper and retain its real focused/resumed MainActivity guards.
_original_save_png = m.save_png


def observer_capture(name, kind, focus_required=True):
    if kind == 'system-ui-anr':
        assert focus_required is False
        data = m.adb('exec-out', 'screencap', '-p')
        m.dimensions(data)
        m.private_write(W / name, data)
        P.setdefault('privateObserverCaptures', []).append({'file': name, 'kind': kind,
            'sha256': sha(data), 'privateWorkOnly': True})
        return
    assert kind == 'main' and focus_required is True
    return _original_save_png(name, kind, focus_required=focus_required)


m.save_png = observer_capture


def app_nodes(root, label, desc=False):
    return [node for node in m.nodes(root, label, desc) if node.get('package') == m.PKG]


def exists(label, desc=False):
    return bool(app_nodes(m.tree(), label, desc))


def click_targets(root, label, desc=False):
    parents = {child: parent for parent in root.iter() for child in parent}
    targets = []
    for node in app_nodes(root, label, desc):
        target = node
        while target.get('clickable') != 'true' and target in parents:
            target = parents[target]
        if target.tag != 'node' or target.get('clickable') != 'true' or target.get('enabled', 'true') != 'true':
            continue
        if all(target is not existing for existing in targets):
            targets.append(target)
    return targets


def fulltap(label, desc=False):
    root = m.tree()
    hits = click_targets(root, label, desc)
    assert len(hits) == 1, ('Unambiguous enabled actual action', label, len(hits))
    bounds = list(m.rect(hits[0]))
    m.tap_node(hits[0], root, climb=False)
    P['actualTaps'].append({'label': label, 'description': desc, 'bounds': bounds})



INITIAL_NAME = 'AreaDevice24'
REQUESTED_NAME = 'AreaRunner24'
CONFIGURATION_ERROR = '이 앱에서 프로필 연결을 준비하지 못했어요. 최신 버전인지 확인해 주세요.'
ADULT_LABEL = '성인이며 크루·러너 기능을 이용할게요.'
assert re.fullmatch(r'[A-Za-z0-9]{2,16}', INITIAL_NAME)
assert re.fullmatch(r'[A-Za-z0-9]{2,16}', REQUESTED_NAME) and INITIAL_NAME != REQUESTED_NAME


def reveal(label, desc=False, clickable=False):
    for _ in range(12):
        root = m.tree()
        hits = click_targets(root, label, desc) if clickable else app_nodes(root, label, desc)
        if len(hits) == 1:
            return root
        assert len(hits) <= 1, ('Ambiguous review label', label)
        width, height = m.dimensions()
        m.sh('input', 'swipe', str(width // 2), str(height * 7 // 10), str(width // 2), str(height * 36 // 100), '350')
    raise AssertionError('Actual review content not reachable by scrolling: ' + label)


def wait_ready_artwork():
    def ready():
        root = m.tree()
        return not app_nodes(root, '준비 중') and not app_nodes(root, '미니미 준비 중', True)
    m.wait(ready, 'Actual MiniMi first-frame loading indicators cleared', 120)


def capture(suffix):
    wait_ready_artwork()
    assert m.dump() is None, 'No running service may be created or taken over'
    m.save_png(a.name + '-' + suffix + '.png', 'main')


def source_profile():
    try:
        return env.local_profile(m)
    except (RuntimeError, ET.ParseError, json.JSONDecodeError):
        return None  # SharedPreferences atomic rename can briefly race an observation.


def unchanged_profile():
    observed = source_profile()
    return observed if observed == local else None


def current_area_node(root, before_y=None):
    width, height = m.dimensions()
    nodes = [node for node in root.iter('node') if node.get('package') == m.PKG
             and re.fullmatch(r'현재 위치 · .+ 부근', node.get('text', ''))]
    visible = [node for node in nodes if 0 <= m.rect(node)[0] < m.rect(node)[2] <= width
               and 0 <= m.rect(node)[1] < m.rect(node)[3] <= (before_y if before_y is not None else height)]
    return visible[0] if len(visible) == 1 else None


def area_observation(node):
    value = node.get('text', '')
    place = value.split(' · ', 1)[1]
    approximate = re.fullmatch(r'-?\d+\.\d{2}, -?\d+\.\d{2} 부근', place) is not None
    return {'displayedText': value, 'bounds': list(m.rect(node)),
            'twoDecimalCoordinateFallbackDisplayed': approximate,
            'administrativeNameDisplayed': not approximate,
            'providerResultIndependentlyVerified': False}


def weather_observation():
    root = m.tree()
    buttons = app_nodes(root, '러닝 시작하기')
    if len(buttons) != 1:
        return None
    button_bounds = m.rect(buttons[0]); width, height = m.dimensions()
    limit = min(button_bounds[1], height)
    def choose(pattern):
        candidates = [node for node in root.iter('node') if node.get('package') == m.PKG
                      and re.fullmatch(pattern, node.get('text', ''))]
        return [node for node in candidates if 0 <= m.rect(node)[0] < m.rect(node)[2] <= width
                and 0 <= m.rect(node)[1] < m.rect(node)[3] <= limit]
    fields = {'date': choose(r'\d+월 \d+일 [월화수목금토일]요일'), 'time': choose(r'\d{2}:\d{2}'),
              'temperature': choose(r'-?\d+°C'), 'wind': choose(r'바람 \d+\.\d m/s')}
    if any(len(values) != 1 for values in fields.values()):
        return None
    area = current_area_node(root, limit)
    if area is None:
        return None
    rain = choose(r'1시간 강수 \d+\.\d mm')
    assert len(rain) <= 1
    return {**{key + 'Bounds': list(m.rect(nodes[0])) for key, nodes in fields.items()},
            'runButtonBounds': list(button_bounds), 'area': area_observation(area),
            'temperatureText': fields['temperature'][0].get('text'),
            'windText': fields['wind'][0].get('text'),
            'rainText': rain[0].get('text') if rain else None,
            'rainBounds': list(m.rect(rain[0])) if rain else None,
            'missingPrecipitationWasNotInvented': True}


def current_density():
    values = re.findall(r'(?:Physical|Override) density:\s*(\d+)', m.sh('wm', 'density'))
    assert values
    return int(values[-1])


def close_action(root):
    # FormDialog/ModalFrame uses a 48dp IconButton with contentDescription
    # '닫기'. Full-screen PageTopBar has visible text, and is not used here.
    targets = click_targets(root, '닫기', True)
    assert len(targets) == 1, 'Exactly one enabled profile X; no second stacked modal'
    bounds = list(m.rect(targets[0])); width, height = m.dimensions()
    minimum = round(48 * current_density() / 160)
    assert 0 <= bounds[0] < bounds[2] <= width and 0 <= bounds[1] < bounds[3] <= height
    assert bounds[2] - bounds[0] >= minimum and bounds[3] - bounds[1] >= minimum
    return bounds


def editable(root):
    fields = [node for node in root.iter('node') if node.get('package') == m.PKG
              and node.get('class') == 'android.widget.EditText' and node.get('enabled', 'true') == 'true']
    return fields[0] if len(fields) == 1 else None


def editor_with_name(expected):
    root = m.tree(); field = editable(root)
    return root if field is not None and field.get('text') == expected and app_nodes(root, '닉네임') else None


def type_requested_name():
    root = m.wait(lambda: editor_with_name(INITIAL_NAME), 'Shared editor shows the current synthetic device name')
    field = editable(root); assert field is not None
    m.tap_node(field, root)
    m.sh('input', 'keyevent', '123')
    m.sh('input', 'keyevent', *(['67'] * (len(INITIAL_NAME) + 2)))
    m.sh('input', 'text', REQUESTED_NAME)
    m.wait(lambda: editor_with_name(REQUESTED_NAME), 'Actual nickname EditText contains the requested synthetic name')
    ime = m.sh('dumpsys', 'input_method')
    shown = re.search(r'\bmInputShown=true\b|\bmIsInputViewShown=true\b', ime) is not None
    P['keyboardShownAfterActualTextEntry'] = shown
    if shown:
        m.sh('input', 'keyevent', '4')
        m.wait(lambda: editor_with_name(REQUESTED_NAME), 'Hiding an observed IME keeps the same profile editor open')
    P['actualNicknameTextEntryObserved'] = True


def confirm_adult():
    reveal(ADULT_LABEL)
    root = m.tree()
    choices = [node for node in root.iter('node') if node.get('package') == m.PKG
               and node.get('checkable') == 'true' and node.get('enabled', 'true') == 'true']
    assert len(choices) == 1, 'Only the explicit adult checkbox in this editor is selected'
    if choices[0].get('checked') != 'true':
        m.tap_node(choices[0], root)
        P['actualTaps'].append({'label': ADULT_LABEL, 'control': 'checkbox', 'bounds': list(m.rect(choices[0]))})
    def checked():
        root = m.tree()
        return any(node.get('package') == m.PKG and node.get('checkable') == 'true'
                   and node.get('checked') == 'true' for node in root.iter('node'))
    m.wait(checked, 'Adult self-confirmation is actually checked for this local review attempt')


def assert_inline_error():
    reveal(CONFIGURATION_ERROR)
    root = m.tree(); field = editable(root)
    assert field is not None and field.get('text') == REQUESTED_NAME
    assert app_nodes(root, '프로필') and app_nodes(root, CONFIGURATION_ERROR)
    assert not app_nodes(root, '확인이 필요해요'), 'No generic confirmation popup over this profile form'
    assert not any(node.get('package') == m.PKG and '연결 상태가 바뀌었어요' in node.get('text', '') for node in root.iter('node'))
    x_bounds = close_action(root)
    reveal('이 닉네임으로 연결', clickable=True)
    root = m.tree(); retry = click_targets(root, '이 닉네임으로 연결')
    retry_field = editable(root)
    assert len(retry) == 1 and retry_field is not None and retry_field.get('text') == REQUESTED_NAME
    assert not app_nodes(root, '확인이 필요해요')
    return {'requestedNickname': REQUESTED_NAME, 'inlineErrorText': CONFIGURATION_ERROR,
            'profileEditorStillOpen': True, 'retryEnabled': True,
            'retryBounds': list(m.rect(retry[0])), 'closeBounds': x_bounds,
            'genericModalAbsent': True, 'identityChangedWarningAbsent': True,
            'connectionReceiptSerialDirectlyObserved': False,
            'intermediateErrorClearObserved': False,
            'errorMeaning': 'FIREBASE_ENABLED=false local TEST build configuration guard, not a production PERMISSION_DENIED'}


def migrated_owned(items):
    result = set(items) | {key + ':0' for key in
        ('hair', 'headwear', 'face', 'top', 'bottom', 'costume', 'socks', 'shoes', 'ear', 'eyewear', 'wrist', 'waist', 'hydration')} | {'accessory:0'}
    for old_id, new_id in [('accessory:1', 'headwear:3'), ('accessory:2', 'headwear:1'), ('accessory:3', 'ear:1')]:
        if old_id in items:
            result.add(new_id)
    return result


def observe_saved_name():
    saved = source_profile()
    return saved if isinstance(saved, dict) and saved.get('name') == REQUESTED_NAME else None

try:
    stage('guards')
    assert m.adb('emu', 'avd', 'name').decode().splitlines()[0] == 'Runmoni_Cloud_29'
    assert m.sh('getprop', 'ro.build.version.sdk') == '29'
    assert m.dump() is None and not m.pid(), 'Main review starts only after force-stop and without an active recorder'
    P['deviceTimezone'] = m.sh('getprop', 'persist.sys.timezone')
    P['deviceUtcObserved'] = P['deviceTimezone'] in ('Etc/UTC', 'UTC', 'GMT')
    P['deviceTimeWasNotTreatedAsKoreanPhysicalPhoneObservation'] = True
    braw = (E / P['binaryReference']).read_bytes()
    binary = json.loads(braw)
    P['binaryReferenceSha256'] = sha(braw)
    assert binary['version'] == P['version'] and binary['versionCode'] == P['versionCode']
    assert binary['buildSucceeded'] is True and binary['signing'] == 'local TEST debug, not retained release signing'
    P['sourceMapsBefore'] = env.source_maps(binary)
    P['installed'] = []
    for package, expected in zip([m.PKG, m.PKG + '.test'], binary['binaries']):
        path = m.sh('pm', 'path', package).removeprefix('package:')
        digest = m.sh('sha256sum', path, timeout=120).split()[0]
        assert digest == expected['sha256']
        P['installed'].append({'package': package, 'sha256': digest})
    cfg = (R / 'app/build/generated/source/buildConfig/debug/com/runmoni/app/BuildConfig.java').read_text()
    assert 'boolean FIREBASE_ENABLED = false;' in cfg
    old_account = env.account_snapshot(m)
    m.private_write(W / (a.name + '-private-account-before.xml'), old_account['raw'])
    P['originalAccountPreferenceExists'] = old_account['present']
    P['originalAccountPreferenceSha256'] = sha(old_account['raw'])
    before = env.environment_snapshot(m)
    m.private_write(W / (a.name + '-private-environment-before.json'), (json.dumps(before) + '\n').encode())
    old = m.prefs()
    ET.fromstring(old)
    m.private_write(W / (a.name + '-private-before.xml'), old)
    baseline = m.snapshot('before')
    assert all(row['finishedAtMillis'] > 0 for row in baseline['runs'])
    P['originalPreferencesSha256'], P['originalDatabaseRowsSha256'] = sha(old), m.canonical(baseline)
    fixture = ET.fromstring(old)
    for kind, key, value in [
        ('string', 'communityBackend', 'firebase'), ('string', 'apiUrl', 'http://127.0.0.1:8787'),
        ('string', 'appEntryChoiceV1', 'guest'), ('boolean', 'communityGuest', 'true'),
        ('boolean', 'publicLocation', 'false'), ('boolean', 'voiceGuidance', 'false'), ('boolean', 'autoPause', 'false'),
    ]:
        m.set_pref(fixture, kind, key, value)
    profile_node = next(node for node in fixture if node.get('name') == 'localProfile')
    local = json.loads(profile_node.text)
    assert isinstance(local, dict) and isinstance(local.get('avatar'), dict) and isinstance(local.get('owned'), list)
    local['name'] = INITIAL_NAME
    expected_avatar = {key: 0 for key in ('hair', 'top', 'bottom', 'shoes', 'accessory', 'eyewear', 'wrist',
                                        'waist', 'hydration', 'face', 'socks', 'headwear', 'ear', 'costume')}
    expected_avatar.update(top=1, shoes=2, gender='male', runningKitColors={'top': 0, 'bottom': 0, 'shoes': 0})
    local['avatar'] = expected_avatar
    expected_owned = set(local['owned'])
    profile_node.text = json.dumps(local, ensure_ascii=False)
    can_restore = True
    m.write_prefs(ET.tostring(fixture, encoding='utf-8', xml_declaration=True))
    assert env.local_profile(m) == local
    original_local = env.profile_from_xml(old)
    assert local.get('mileage') == original_local.get('mileage') and local['owned'] == original_local['owned']
    P['fixturePreferenceWriteByReview'] = True
    P['fixtureWalletAndExistingOwnershipPreserved'] = True
    env.viewport(m, False)
    for key in ['window_animation_scale', 'transition_animation_scale', 'animator_duration_scale']:
        m.sh('settings', 'put', 'global', key, '0')
    for permission in ['android.permission.ACCESS_FINE_LOCATION', 'android.permission.ACCESS_COARSE_LOCATION']:
        m.sh('pm', 'grant', m.PKG, permission)
    m.sh('settings', 'put', 'secure', 'location_mode', '3')
    m.adb('emu', 'geo', 'fix', '126.978', '37.5665')
    m.launch()
    for _ in range(3):
        m.adb('emu', 'geo', 'fix', '126.978', '37.5665')
        time.sleep(1)


    stage('home-current-area-and-native-weather')
    m.tap_tab('홈')
    def native_weather_ready():
        return any(node.get('package') == m.PKG and node.get('text', '').startswith('예보: MET Norway')
                   for node in m.tree().iter('node'))
    def reveal_native_weather():
        if native_weather_ready():
            return True
        width, height = m.dimensions()
        m.sh('input', 'swipe', str(width // 2), str(height * 72 // 100), str(width // 2), str(height * 50 // 100), '300')
        return False
    m.wait(reveal_native_weather, 'Actual native MET forecast attribution after synthetic Seoul GPS', 120)
    P['nativeWeatherDisplayed'] = True
    # Device geocoding has its own bounded lookup. This settling interval is
    # not measured backend completion, a latency SLA, or an injected district.
    time.sleep(6)
    width, height = m.dimensions()
    for _ in range(3):
        m.sh('input', 'swipe', str(width // 2), str(height * 35 // 100), str(width // 2), str(height * 80 // 100), '300')
    P['homeWeatherBeforeRunBounds'] = m.wait(weather_observation,
        'Visible date/time, temperature/wind and actual current-area label before the Run action', 120)
    P['homeDateTimeAndWeatherObserved'] = True
    P['homeAreaObserved'] = P['homeWeatherBeforeRunBounds']['area']
    capture('home-current-area-weather')

    stage('explore-current-area-and-native-map')
    m.tap_tab('탐색')
    m.wait(lambda: exists('근처 러너'), 'Actual Explore page')
    m.adb('emu', 'geo', 'fix', '126.978', '37.5665')
    reveal('내 위치로 이동', clickable=True); fulltap('내 위치로 이동')
    map_retry = False
    def native_map_ready():
        global map_retry
        root = m.tree()
        if app_nodes(root, '지도 연결 실패'):
            if not map_retry and click_targets(root, '배경 지도 다시 불러오기', True):
                fulltap('배경 지도 다시 불러오기', True); map_retry = True
            return None
        if not app_nodes(root, '© OpenStreetMap contributors'):
            return None
        descriptions = [node.get('content-desc', '') for node in root.iter('node') if node.get('package') == m.PKG]
        if any('배경 지도를 불러오는 중입니다.' in value or '배경 지도 연결 실패.' in value for value in descriptions):
            return None
        maps = [node for node in root.iter('node') if node.get('package') == m.PKG
                and node.get('content-desc', '').startswith('확인된 위치 주변 지도.')]
        area = current_area_node(root)
        if len(maps) != 1 or area is None:
            return None
        return {'area': area_observation(area), 'mapBounds': list(m.rect(maps[0])),
                'mapDescription': maps[0].get('content-desc'), 'attributionVisible': True,
                'loadingOrFailureDescriptionAbsent': True, 'freshMapHttpStatusMeasured': False}
    P['exploreMapObservation'] = m.wait(native_map_ready,
        'Actual OSM attribution/current-area label with known-position canvas and no loading/failure state', 120)
    P['sameSyntheticPositionRequestedForHomeAndExplore'] = True
    P['sameGeocoderQueryCellIndependentlyVerified'] = False
    P['homeAndExploreDisplayedSamePlaceText'] = (P['homeAreaObserved']['displayedText'] ==
                                                P['exploreMapObservation']['area']['displayedText'])
    P['nativeMapDisplayed'] = True
    assert not exists('크루 검색')
    capture('explore-current-area')

    stage('actual-profile-connection-failure-and-same-name-retry')
    runtime_profile = m.wait(lambda: source_profile(), 'Actual local profile available before connection attempts')
    assert all(runtime_profile.get(key) == value for key, value in local.items() if key != 'owned')
    assert set(runtime_profile.get('owned', [])) == migrated_owned(set(local.get('owned', [])))
    P['startupOnlyDefaultOwnershipNormalizationObserved'] = runtime_profile.get('owned') != local.get('owned')
    P['connectionAttemptProfileBaselineTakenAfterStartup'] = True
    local = runtime_profile
    reveal('프로필 연결', clickable=True); fulltap('프로필 연결')
    m.wait(lambda: editor_with_name(INITIAL_NAME), 'Actual shared profile editor opened from Explore')
    type_requested_name(); confirm_adult()
    for attempt in (1, 2):
        reveal('이 닉네임으로 연결', clickable=True)
        fulltap('이 닉네임으로 연결')
        observation = assert_inline_error()
        m.wait(unchanged_profile, 'Failed profile connection preserves actual local profile/wallet/ownership')
        observation.update(attempt=attempt, observedAfterActualTap=True,
            localProfileExactlyPreserved=True, walletAndOwnershipPreserved=True,
            connectionSuccessUiObserved=False)
        P['profileConnectionAttempts'].append(observation)
        P['profileConnectionAttemptTapCount'] = len(P['profileConnectionAttempts'])
        stage('observed-configured-false-inline-error-after-tap-' + str(attempt))
    # The fast configured=false path may clear and restore the same error
    # within one frame; this collector does not claim to observe that transition
    # or expose the ViewModel's private UID/receipt counters.
    P['sameNicknameActualRetryTapsObserved'] = True
    P['profileConnectionErrorStayedInline'] = True
    reveal(CONFIGURATION_ERROR)
    close_action(m.tree())
    capture('profile-inline-retry')

    stage('actual-local-device-name-save-and-me')
    reveal('저장', clickable=True); fulltap('저장')
    def profile_closed_to_explore():
        root = m.tree()
        return editable(root) is None and bool(app_nodes(root, '근처 러너'))
    m.wait(profile_closed_to_explore, 'Actual local Save acknowledgment closes this editor to Explore')
    saved = m.wait(observe_saved_name, 'Actual requested device name persisted in SharedPreferences')
    assert all(saved.get(key) == value for key, value in local.items() if key not in ('name', 'owned'))
    assert set(saved.get('owned', [])) == migrated_owned(set(local.get('owned', [])))
    assert saved.get('mileage') == local.get('mileage')
    P['profileSaves'].append({'action': '저장', 'savedSyntheticName': REQUESTED_NAME,
        'observedPersistedProfileSha256': m.canonical(saved), 'localDeviceNameSave': True,
        'serverProfileConnection': False, 'walletPreserved': True,
        'existingOwnershipPreserved': True, 'onlyExistingDefaultOwnershipMigrationAllowed': True,
        'otherProfileFieldsPreserved': True})
    P['profileSaveCount'] = len(P['profileSaves'])
    P['localProfileSaveVerified'] = True
    P['localSaveAcknowledgementObservedViaClosure'] = True
    m.tap_tab('나')
    reveal('프로필 편집', clickable=True)
    m.wait(lambda: exists(REQUESTED_NAME), 'Me shows the actually saved synthetic device name')
    fulltap('프로필 편집')
    m.wait(lambda: editor_with_name(REQUESTED_NAME), 'Me opens the same saved shared profile editor')
    P['sameProfileEditorFromExploreAndMe'] = True
    P['profileCloseBoundsBeforeActualTap'] = close_action(m.tree())
    fulltap('닫기', True)  # Actual ModalFrame IconButton description, not PageTopBar text.
    m.wait(lambda: not editor_with_name(REQUESTED_NAME) and exists('프로필 편집'), 'Actual X returns to Me')
    assert observe_saved_name() == saved
    P['actualProfileCloseToMeVerified'] = True
    capture('me-saved-device-name')
    assert P['profileSaveCount'] == 1 and P['profileConnectionAttemptTapCount'] == 2
    assert len(P['captures']) == 4 and all(row['kind'] == 'main' for row in P['captures'])
    P['sourceMapsAfter'] = env.source_maps(binary)
    assert m.dump() is None
    P['passed'] = True
    stage('actions-passed-awaiting-restoration')

except BaseException as failure:
    P['passed'] = False
    P['failureType'] = type(failure).__name__
    P['failure'] = str(failure) if isinstance(failure, AssertionError) else 'Inspect retained local diagnostics'
    if can_restore:
        try:
            m.private_write(W / (a.name + '-private-failure.png'), m.adb('exec-out', 'screencap', '-p'))
        except Exception:
            pass
    stage('failed-before-finally')
finally:
    if can_restore:
        quiesced = False
        try:
            m.force_stop()
            quiesced = True
        except BaseException as failure:
            P['forceStopFailureType'] = type(failure).__name__
            P['passed'] = False
        if quiesced:
            try:
                after = m.snapshot('after')
                P['databaseExactlyRestored'] = m.canonical(after) == m.canonical(baseline)
            except BaseException as failure:
                P['databaseObservationFailureType'] = type(failure).__name__
                P['passed'] = False
            # A failed DB observation must never skip exact original XML/profile restore.
            try:
                m.write_prefs(old)
                P['preferencesRestoredExactly'] = m.prefs() == old
                P['profileRestoredExactly'] = env.local_profile(m) == env.profile_from_xml(old)
            except BaseException as failure:
                P['preferenceRestoreFailureType'] = type(failure).__name__
                P['passed'] = False
        try:
            env.restore_account(m, old_account)
            current_account = env.account_snapshot(m)
            P['accountPreferenceExistsExactlyRestored'] = current_account['present'] == old_account['present']
            P['accountPreferencesRestoredExactly'] = current_account['raw'] == old_account['raw']
        except BaseException as failure:
            P['accountPreferenceRestoreFailureType'] = type(failure).__name__
            P['passed'] = False
        try:
            equal, observed, failures = env.restore_environment(m, before)
            P['controlledEnvironmentExactlyRestored'] = equal
            P['restorationCommandFailures'] = failures
            m.private_write(W / (a.name + '-private-environment-after.json'), (json.dumps(observed) + '\n').encode())
        except BaseException as failure:
            P['environmentRestoreFailureType'] = type(failure).__name__
            P['passed'] = False
    if binary is not None:
        try:
            P['sourceMapsAfterRestoration'] = env.source_maps(binary)
            P['binaryEvidenceUnchanged'] = sha((E / P['binaryReference']).read_bytes()) == P['binaryReferenceSha256']
            assert P['binaryEvidenceUnchanged']
        except BaseException as failure:
            P['postRestorationInputObservationFailureType'] = type(failure).__name__
            P['passed'] = False
    P['driverSourceUnchanged'] = sha(Path(__file__).read_bytes()) == P['executedDriverSha256']
    P['observerHelperSourceUnchanged'] = sha(hpath.read_bytes()) == P['observerHelperSha256']
    P['environmentHelperSourceUnchanged'] = sha(envpath.read_bytes()) == P['environmentHelperSha256']
    P['passed'] = bool(P['passed'] and all(P.get(key) for key in [
        'binaryEvidenceUnchanged', 'driverSourceUnchanged', 'observerHelperSourceUnchanged', 'environmentHelperSourceUnchanged',
        'databaseExactlyRestored', 'preferencesRestoredExactly', 'profileRestoredExactly', 'accountPreferenceExistsExactlyRestored', 'accountPreferencesRestoredExactly', 'controlledEnvironmentExactlyRestored']))
    P['completed'], P['finishedAtUtc'] = True, env.now()
    stage('complete-passed' if P['passed'] else 'complete-failed')
raise SystemExit(0 if P['passed'] else 1)
