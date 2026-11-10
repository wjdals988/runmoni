"""Root-owned MainActivity review of 0.4.1 wardrobe and adjacent native weather.

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
W = R / 'work/widget-wardrobe'
E = R / 'docs/app-review/2026-11-10-widget-wardrobe'
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
m = module('actual_widget_wardrobe_main_observer', hpath)
env = module('widget_wardrobe_review_environment', envpath)
m.W, m.E, m.NAME, m.VERSION, m.CODE = W, E, a.name, '0.4.1-dev', 23
sha = env.sha
out = E / (a.name + '.json')
assert not out.exists()
P = {
    'name': a.name, 'version': '0.4.1-dev', 'versionCode': 23, 'api': 29,
    'avd': 'Runmoni_Cloud_29', 'acceleration': 'TCG', 'binaryReference': 'binary-build4.json',
    'passed': False, 'completed': False, 'captures': [], 'startedAtUtc': env.now(),
    'physicalDeviceVerified': False, 'googleLoginVerified': False,
    'productionWritesVerified': False, 'android31RuntimeVerified': False,
    'signedReleaseRuntimeVerified': False, 'outdoorGpsVerified': False,
    'dozeVerified': False, 'oemBatteryPolicyVerified': False, 'syntheticGps': True,
    'databaseWritesByReview': False, 'sourceKind': 'actual Android MainActivity',
    'systemUiWaitActions': [], 'accessibilityRetries': 0, 'actualTaps': [],
    'profileSaves': [], 'profileSaveCount': 0,
    'saveCountMeaning': 'Distinct UI selection changes confirmed in persisted profile; not internal write call count',
    'anonymousGuestFixture': True, 'syntheticProfileName': '검토 러너',
    'deviceTimezone': None, 'weatherHttpStatusMeasured': False,
    'weatherRequestCoordinatesIndependentlyLogged': False,
    'decorativeAvatarBoundsMeasuredByAccessibility': False,
    'avatarWeatherSideBySideConfirmedByThisDriver': False,
    'requiredIndependentRootPixelReview': True,
}
m.P = P
can_restore = False
old = before = baseline = binary = None
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


CATEGORY_LABELS = ['헤어', '모자', '얼굴', '상의', '하의', '세트', '양말', '신발', '액세서리']


def closet_tab(label):
    assert label in CATEGORY_LABELS
    for _ in range(9):
        root = m.tree()
        if len(click_targets(root, label)) == 1:
            fulltap(label)
            return
        visible = [(CATEGORY_LABELS.index(text), node) for text in CATEGORY_LABELS
                   for node in click_targets(root, text)]
        assert visible, 'An actual closet category strip must remain visible'
        # The title and slot heading are not clickable; only category targets
        # supply this genuine horizontal strip's measured y coordinate.
        tops = [m.rect(node)[1] for _, node in visible]
        bottoms = [m.rect(node)[3] for _, node in visible]
        y = (max(tops) + min(bottoms)) // 2
        assert max(tops) < min(bottoms), 'Visible category targets share one actual row'
        width, height = m.dimensions()
        assert 0 < y < height
        later = CATEGORY_LABELS.index(label) > max(index for index, _ in visible)
        x1, x2 = ((width * 85 // 100, width * 15 // 100) if later else
                  (width * 15 // 100, width * 85 // 100))
        m.sh('input', 'swipe', str(x1), str(y), str(x2), str(y), '350')
    raise AssertionError('Closet category was not reachable by actual horizontal scrolling: ' + label)


def reveal_action(label):
    for _ in range(10):
        if len(click_targets(m.tree(), label)) == 1:
            return
        width, height = m.dimensions()
        m.sh('input', 'swipe', str(width // 2), str(height * 7 // 10), str(width // 2), str(height * 35 // 100), '350')
    raise AssertionError('Actual action not reachable by scrolling: ' + label)


def wait_ready_artwork():
    def ready():
        root = m.tree()
        return not app_nodes(root, '준비 중') and not app_nodes(root, '미니미 준비 중', True)
    m.wait(ready, 'Visible actual MiniMi first-frame loading indicators cleared', 120)


def capture(suffix):
    wait_ready_artwork()
    assert m.dump() is None, 'Review must never create or take over a running service'
    m.save_png(a.name + '-' + suffix + '.png', 'main')


def migrated_owned(items):
    result = set(items) | {key + ':0' for key in
        ('hair', 'headwear', 'face', 'top', 'bottom', 'costume', 'socks', 'shoes', 'ear', 'eyewear', 'wrist', 'waist', 'hydration')} | {'accessory:0'}
    for old_id, new_id in [('accessory:1', 'headwear:3'), ('accessory:2', 'headwear:1'), ('accessory:3', 'ear:1')]:
        if old_id in items:
            result.add(new_id)
    return result


def observe_saved_profile(expected_avatar):
    try:
        value = env.local_profile(m)
    except (RuntimeError, ET.ParseError, json.JSONDecodeError):
        # SharedPreferences atomic rename can briefly race this read. A later
        # real read must match; no old observation is reused as a new save.
        return None
    return value if isinstance(value, dict) and value.get('avatar') == expected_avatar else None


def save_selection(label, avatar_updates, acquired=None):
    global expected_avatar, expected_owned
    reveal_action(label)
    previous = m.prefs()
    next_avatar = dict(expected_avatar)
    next_avatar.update(avatar_updates)
    assert next_avatar != expected_avatar, 'Every counted selection changes an actual saved style field'
    fulltap(label)
    saved = m.wait(lambda: observe_saved_profile(next_avatar), 'Actual persisted outfit after UI selection: ' + label, 75)
    assert all(saved.get(key) == value for key, value in local.items() if key not in ('avatar', 'owned'))
    next_owned = migrated_owned(expected_owned)
    if acquired is not None:
        next_owned.add(acquired)
    assert set(saved.get('owned', [])) == next_owned, 'Only actual item acquisition/default migration may alter ownership'
    assert saved.get('mileage') == local.get('mileage'), 'Free clothing must preserve the original wallet balance'
    assert m.prefs() != previous, 'Counted UI selection must change persisted preferences'
    expected_avatar, expected_owned = next_avatar, next_owned
    P['profileSaves'].append({'action': label, 'avatar': next_avatar, 'profileSha256': m.canonical(saved),
        'walletPreserved': True, 'unrelatedProfileFieldsPreserved': True,
        'unrelatedAvatarFieldsPreserved': True, 'existingOwnershipPreserved': True,
        'acquiredOrAlreadyOwnedItem': acquired})
    P['profileSaveCount'] = len(P['profileSaves'])
    stage('persisted-selection-' + str(P['profileSaveCount']))
    return saved


def open_closet():
    m.tap_tab('나')
    reveal_action('미니미 꾸미기')
    fulltap('미니미 꾸미기')
    m.wait(lambda: exists('옷장'), 'Actual full-screen closet opened')


def close_closet():
    fulltap('닫기', True)
    m.wait(lambda: exists('미니미 꾸미기'), 'Actual closet close returns to Me')


def weather_observation():
    root = m.tree()
    buttons = app_nodes(root, '러닝 시작하기')
    if len(buttons) != 1:
        return None
    button_bounds = m.rect(buttons[0])
    width, height = m.dimensions()
    limit = button_bounds[1]
    def choose(pattern):
        found = [node for node in root.iter('node') if node.get('package') == m.PKG
                 and re.fullmatch(pattern, node.get('text', ''))]
        return [node for node in found if 0 <= m.rect(node)[0] < m.rect(node)[2] <= width
                and 0 <= m.rect(node)[1] < m.rect(node)[3] <= min(limit, height)]
    fields = {
        'dateBounds': choose(r'\d+월 \d+일 [월화수목금토일]요일'),
        'timeBounds': choose(r'\d{2}:\d{2}'),
        'temperatureBounds': choose(r'-?\d+°C'),
        'windBounds': choose(r'바람 \d+\.\d m/s'),
        'rainBounds': choose(r'1시간 강수 \d+\.\d mm'),
    }
    if any(len(nodes) != 1 for nodes in fields.values()):
        return None
    return {**{key: list(m.rect(nodes[0])) for key, nodes in fields.items()},
            'runButtonBounds': list(button_bounds),
            'weatherTexts': {key.removesuffix('Bounds'): nodes[0].get('text') for key, nodes in fields.items()
                             if key in ('temperatureBounds', 'windBounds', 'rainBounds')}}


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
    P['sourceMapsBefore'] = env.source_maps(binary)
    P['installed'] = []
    for package, expected in zip([m.PKG, m.PKG + '.test'], binary['binaries']):
        path = m.sh('pm', 'path', package).removeprefix('package:')
        digest = m.sh('sha256sum', path).split()[0]
        assert digest == expected['sha256']
        P['installed'].append({'package': package, 'sha256': digest})
    cfg = (R / 'app/build/generated/source/buildConfig/debug/com/runmoni/app/BuildConfig.java').read_text()
    assert 'boolean FIREBASE_ENABLED = false;' in cfg
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
    local['name'] = '검토 러너'
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

    stage('male-green-shorts-and-crew-socks')
    open_closet()
    closet_tab('하의')
    save_selection('그린 쇼츠', {'bottom': 2}, 'bottom:2')
    closet_tab('양말')
    save_selection('크루 양말', {'socks': 1}, 'socks:1')
    capture('male-crew')

    stage('home-adjacent-native-weather')
    close_closet()
    m.tap_tab('홈')
    def weather_ready():
        return any(node.get('package') == m.PKG and node.get('text', '').startswith('예보: MET Norway')
                   for node in m.tree().iter('node'))
    def reveal_native_weather():
        if weather_ready():
            return True
        width, height = m.dimensions()
        # A received forecast can make the hero taller after an initial scroll.
        # Keep seeking its real attribution, rather than reusing an old tree or
        # assuming one fixed swipe made the below-CTA provider action visible.
        m.sh('input', 'swipe', str(width // 2), str(height * 72 // 100),
             str(width // 2), str(height * 52 // 100), '300')
        return False
    m.wait(reveal_native_weather, 'Native MET forecast attribution rendered after synthetic Seoul GPS', 90)
    P['nativeWeatherDisplayed'] = True
    if exists('날씨 다시 확인'):
        fulltap('날씨 다시 확인')
    for _ in range(3):
        m.adb('emu', 'geo', 'fix', '126.978', '37.5665')
        time.sleep(1)
    m.sh('input', 'swipe', '240', '350', '240', '700', '300')
    time.sleep(1)
    P['homeWeatherBeforeRunBounds'] = m.wait(weather_observation,
        'Actual visible date/time and weather temperature/wind/rain before Run CTA', 90)
    P['homeDateTimeAndWeatherObserved'] = True
    assert env.local_profile(m).get('avatar') == expected_avatar
    capture('home-weather')

    stage('female-base-bottom-and-knee-socks')
    open_closet()
    save_selection('여자', {'gender': 'female'})
    # Gender selection itself preserves the old male-only shorts ID. Only the
    # following genuine base-bottom tap changes it; no unseen migration is used.
    assert expected_avatar['bottom'] == 2
    P['genderSwitchRetainedMaleOnlySavedBottomUntilExplicitSelection'] = True
    closet_tab('하의')
    save_selection('기본 하의', {'bottom': 0}, 'bottom:0')
    closet_tab('양말')
    save_selection('니삭스', {'socks': 2}, 'socks:2')
    capture('female-knee')

    stage('male-coral-shorts-with-retained-knee-socks')
    save_selection('남자', {'gender': 'male'})
    assert expected_avatar['socks'] == 2
    closet_tab('하의')
    save_selection('코랄 쇼츠', {'bottom': 3}, 'bottom:3')
    capture('male-coral')
    assert P['profileSaveCount'] == 7
    assert len(P['captures']) == 4 and all(row['kind'] == 'main' for row in P['captures'])
    P['currentSelectedOutfitsPersisted'] = True
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
            equal, observed, failures = env.restore_environment(m, before)
            P['controlledEnvironmentExactlyRestored'] = equal
            P['restorationCommandFailures'] = failures
            m.private_write(W / (a.name + '-private-environment-after.json'), (json.dumps(observed) + '\n').encode())
        except BaseException as failure:
            P['environmentRestoreFailureType'] = type(failure).__name__
            P['passed'] = False
    P['driverSourceUnchanged'] = sha(Path(__file__).read_bytes()) == P['executedDriverSha256']
    P['observerHelperSourceUnchanged'] = sha(hpath.read_bytes()) == P['observerHelperSha256']
    P['environmentHelperSourceUnchanged'] = sha(envpath.read_bytes()) == P['environmentHelperSha256']
    P['passed'] = bool(P['passed'] and all(P.get(key) for key in [
        'driverSourceUnchanged', 'observerHelperSourceUnchanged', 'environmentHelperSourceUnchanged',
        'databaseExactlyRestored', 'preferencesRestoredExactly', 'profileRestoredExactly', 'controlledEnvironmentExactlyRestored']))
    P['completed'], P['finishedAtUtc'] = True, env.now()
    stage('complete-passed' if P['passed'] else 'complete-failed')
raise SystemExit(0 if P['passed'] else 1)
