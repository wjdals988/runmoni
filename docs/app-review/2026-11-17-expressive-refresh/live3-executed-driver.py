"""Root-only actual MainActivity run-share review; prepare source is not execution.

Requires an already installed frozen local TEST pair and an idle owned API29 AVD.
Creates only a nonce-owned zero-point run and synthetic photo through the explicit
test fixture. All own resources are deleted and original preferences/accounts,
Room rows, controlled settings and app permissions are restored in finally.
"""
from pathlib import Path
import argparse
import datetime
import importlib.util
import io
import json
import os
import re
import shlex
import time
import xml.etree.ElementTree as ET
from PIL import Image

R = Path('/workspace/runmoni')
W = R / 'work/expressive-refresh'
E = R / 'docs/app-review/2026-11-17-expressive-refresh'
# Read-only scope validation happens before any Main/Android mutation. This
# candidate accepts only fresh groups bound to the selected exact new input;
# neither old-input results nor restoration-only recovery count as new tests.
import hashlib
SCOPE_PATH = E / 'android-scope-summary.json'
SOURCE_MAPS = ('productSources', 'testSources', 'resources', 'rendererAssets', 'approvedSourceCopies', 'configurationSources')


def scope_reference(parent, ref):
    assert isinstance(ref, dict) and isinstance(ref.get('file'), str)
    assert re.fullmatch(r'[0-9a-f]{64}', ref.get('sha256', ''))
    path = Path(ref['file'])
    path = (path if path.is_absolute() else parent / path).resolve(strict=True)
    assert path.is_file() and path.is_relative_to(R)
    assert not any(part == '.secrets' or 'private' in part.lower() for part in path.parts)
    assert hashlib.sha256(path.read_bytes()).hexdigest() == ref['sha256'], ('Changed scope evidence', path.name)
    return path


def scope_source_observation(value, binary):
    assert set(value) == set(SOURCE_MAPS)
    for key in SOURCE_MAPS:
        expected = binary[key]
        assert value[key] == expected or value[key] == {'fileCount': len(expected), 'matches': True, 'mismatches': []}


def scope_binary(path):
    value = json.loads(path.read_text())
    assert value['version'] == '0.9.0-dev' and value['versionCode'] == 32 and value['buildSucceeded'] is True
    frozen_path = scope_reference(path.parent, {'file': value['sourceFreezeReference'], 'sha256': value['sourceFreezeSha256']})
    frozen = json.loads(frozen_path.read_text())
    assert (frozen['version'], frozen['versionCode']) == ('0.9.0-dev', 32)
    assert frozen['sourceMaps'] == {key: value[key] for key in SOURCE_MAPS}
    return value


_SCOPE_VALIDATOR_PATH = W / 'validate-scope11.py'
_SCOPE_VALIDATOR_SHA = hashlib.sha256(_SCOPE_VALIDATOR_PATH.read_bytes()).hexdigest()


def validate_scope(binary_name, env):
    assert hashlib.sha256(_SCOPE_VALIDATOR_PATH.read_bytes()).hexdigest() == _SCOPE_VALIDATOR_SHA
    spec = importlib.util.spec_from_file_location('exact_scope11_validator', _SCOPE_VALIDATOR_PATH)
    verifier = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(verifier)
    return verifier.validate_scope(binary_name)


os.umask(0o077)
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--name', required=True)
parser.add_argument('--nonce', required=True)
parser.add_argument('--binary', required=True)
a = parser.parse_args()
assert re.fullmatch(r'live[1-9]\d*', a.name)
assert re.fullmatch(r'[a-z0-9]{8,32}', a.nonce)
assert re.fullmatch(r'binary-build[1-9]\d*\.json', a.binary)
INPUT_NUMBER = int(re.fullmatch(r'binary-build([1-9]\d*)\.json', a.binary).group(1))
INSTALL_PATH = E / f'installation{INPUT_NUMBER}.json'
PREINSTALL_XML = W / f'pre-install{INPUT_NUMBER}-private.xml'
PREINSTALL_APPEARANCE = W / f'pre-install{INPUT_NUMBER}-appearance-private.json'


def module(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


hpath = W / 'actual-ui-helper.py'
envpath = W / 'android-review.py'
m = module('actual_run_share_main_observer', hpath)
env = module('run_share_review_environment', envpath)
_scope, _scope_sha = validate_scope(a.binary, env)
m.W, m.E, m.NAME, m.VERSION, m.CODE = W, E, a.name, '0.9.0-dev', 32
sha = env.sha
out = E / (a.name + '.json')
assert not out.exists()
TEST = m.PKG + '.test'
PHOTO = 'RunmoniReview-' + a.nonce + '.png'
RECORD = 'runmoni-share-review-' + a.nonce
MARKER = 'cache/run-share-review/main-fixture-' + a.nonce + '.json'
CACHE = 'cache/exports/run-shares'
RECEIVER = 'cache/run-share-receiver'
ITEM_PLAN = R / 'docs/design/avatars/v0.9.0/item-plan.json'
PLAN_RAW = ITEM_PLAN.read_bytes()
PLAN = json.loads(PLAN_RAW)
assert PLAN['sourceSha256'] == 'f9035f29ced0c7ba513dfe24cb727410a7204dc482fc457e806c7a9b3cdbfa65'
assert len(PLAN['items']) == 39
NEW_ITEMS = {(row['slot'], row['variant']): row['name'] for row in PLAN['items']}
assert len(NEW_ITEMS) == 39
P = {'name': a.name, 'nonce': a.nonce, 'version': '0.9.0-dev', 'versionCode': 32,
     'api': 29, 'avd': 'Runmoni_Cloud_29', 'acceleration': 'TCG',
     'binaryReference': a.binary, 'sourceKind': 'actual Android MainActivity',
     'formalScopeReference': SCOPE_PATH.name, 'formalScopeSha256': _scope_sha,
     'formalScopeEarlierInputResultsReused': _scope['earlierInputResultsReused'],
     'formalScopeSingleInputExecutionClaimed': not _scope['earlierInputResultsReused'],
     'passed': False, 'completed': False, 'captures': [], 'actualTaps': [],
     'startedAtUtc': env.now(), 'systemUiWaitActions': [], 'accessibilityRetries': 0,
     'syntheticGps': False, 'GPSInjectedByThisDriver': False,
     'originalGpsFixRestoredByThisDriver': False, 'fixtureTrackPointCount': 0,
     'syntheticCompletedRun': True, 'syntheticSourcePhoto': True,
     'productionWritesVerified': False, 'newAuthenticationRequestedByDriver': False,
     'physicalDeviceVerified': False, 'googleLoginVerified': False,
     'signedReleaseRuntimeVerified': False, 'android31RuntimeVerified': False,
     'socialAppDeliveryVerified': False, 'appTotalNetworkTrafficMeasured': False,
     'itemPlanSha256': sha(PLAN_RAW), 'weatherInjectedByThisDriver': False,
     'appearancePreferencesRestoredExactly': False, 'allOwnedPreferencesStillExactAfterEnvironmentRestoration': False,
     'restorationCommandFailures': [], 'rootPrivacyReviewRequiredBeforePublication': True,
     'requiredIndependentRootPixelReview': True}
m.P = P
can_restore = fixture_attempted = False
old = before = baseline = binary = old_account = old_appearance = old_fixture_preferences = None
cache_before = receiver_before = gallery_before = None
marker = None
own_gallery = set()
own_cache = set()
own_receiver = set()
for filename, raw in ((a.name + '-executed-driver.py', Path(__file__).read_bytes()),
                      (a.name + '-actual-ui-helper.py', hpath.read_bytes()),
                      (a.name + '-environment-helper.py', envpath.read_bytes()),
                      (a.name + '-scope-validator.py', _SCOPE_VALIDATOR_PATH.read_bytes())):
    env.exclusive_bytes(E / filename, raw)
P.update(executedDriver=a.name + '-executed-driver.py', executedDriverSha256=sha(Path(__file__).read_bytes()),
         observerHelper=a.name + '-actual-ui-helper.py', observerHelperSha256=sha(hpath.read_bytes()),
         environmentHelper=a.name + '-environment-helper.py', environmentHelperSha256=sha(envpath.read_bytes()),
         scopeValidator=a.name + '-scope-validator.py', scopeValidatorSha256=_SCOPE_VALIDATOR_SHA)


def stage(label):
    P['stage'], P['updatedAtUtc'] = label, env.now()
    env.atomic_json(out, P)
    print(label, flush=True)


def shell(*args, **kwargs):
    return m.adb('shell', shlex.join([str(v) for v in args]), **kwargs).decode().strip()


def listing(package, directory):
    raw = shell('run-as', package, 'ls', '-1', directory, allow_failure=True)
    if 'No such file or directory' in raw:
        return set()
    assert not any(token in raw for token in ('Permission denied', 'not debuggable', 'not found'))
    return {line for line in raw.splitlines() if line}


def private_read(package, path):
    return m.adb('exec-out', 'run-as', package, 'cat', path)


def gallery():
    # Query only this package's own exported-card directory, never user photos.
    raw = shell('content', 'query', '--uri', 'content://media/external/images/media',
                '--projection', '_id', '--where',
                "owner_package_name='com.runmoni.app' AND relative_path='Pictures/Runmoni/'")
    if raw == 'No result found.':
        return set()
    ids = re.findall(r'\b_id=(\d+)\b', raw)
    assert ids and 'Error' not in raw and 'Exception' not in raw
    return {'content://media/external/images/media/' + value for value in ids}


def gallery_owned(uri, *, allow_pending=False):
    assert re.fullmatch(r'content://media/external/images/media/\d+', uri)
    raw = shell('content', 'query', '--uri', uri, '--projection',
                '_id:_display_name:relative_path:mime_type:is_pending:owner_package_name')
    assert 'owner_package_name=com.runmoni.app' in raw
    assert 'relative_path=Pictures/Runmoni/' in raw and 'mime_type=image/png' in raw
    name = re.search(r'\b_display_name=(Runmoni-[0-9a-f-]{36}\.png)\b', raw)
    pending = re.search(r'\bis_pending=([01])\b', raw)
    assert name and pending and (allow_pending or pending.group(1) == '0')
    return name.group(1)


def instrumentation(method):
    raw = m.adb('shell', 'am', 'instrument', '-w', '-e', 'class',
                'com.runmoni.app.RunShareMainFixture#' + method,
                '-e', 'runmoniShareNonce', a.nonce,
                TEST + '/androidx.test.runner.AndroidJUnitRunner', timeout=240)
    env.exclusive_bytes(W / (a.name + '-private-fixture-' + method + '.txt'), raw, private=True)
    text = raw.decode(errors='replace')
    assert re.search(r'OK \(1 test\)', text) and 'FAILURES!!!' not in text, method


_original_save_png = m.save_png


def observer_capture(name, kind, focus_required=True):
    if kind == 'system-ui-anr':
        assert focus_required is False
        raw = m.adb('exec-out', 'screencap', '-p')
        m.dimensions(raw)
        m.private_write(W / name, raw)
        P.setdefault('privateObserverCaptures', []).append({'file': name, 'sha256': sha(raw), 'privateWorkOnly': True})
        return
    assert kind == 'main' and focus_required is True
    result = _original_save_png(name, kind, focus_required=focus_required)
    P['captures'][-1]['syntheticGps'] = False
    P['captures'][-1]['GPSInjectedByThisDriver'] = False
    return result


m.save_png = observer_capture


def app_nodes(root, label, desc=False):
    return [node for node in m.nodes(root, label, desc) if node.get('package') == m.PKG]


def click_targets(root, label, desc=False, system=False):
    parents = {child: parent for parent in root.iter() for child in parent}
    result = []
    nodes = m.nodes(root, label, desc) if system else app_nodes(root, label, desc)
    for node in nodes:
        if system and node.get('package') in (m.PKG, TEST):
            continue
        target = node
        while target.get('clickable') != 'true' and target in parents:
            target = parents[target]
        if target.tag == 'node' and target.get('clickable') == 'true' and target.get('enabled', 'true') == 'true':
            if all(target is not old for old in result):
                result.append(target)
    return result


def fulltap(label, desc=False, system=False):
    root = m.tree()
    hits = click_targets(root, label, desc, system)
    assert len(hits) == 1, ('Unambiguous actual action', label, len(hits))
    bounds = list(m.rect(hits[0]))
    m.tap_node(hits[0], root, climb=False)
    P['actualTaps'].append({'label': label, 'description': desc, 'system': system, 'bounds': bounds})



def choose_gender(label):
    expected={'남자':'male','여자':'female'}[label]
    current=env.local_profile(m)
    already=isinstance(current,dict) and current.get('avatar',{}).get('gender')==expected
    if already:
        assert app_nodes(m.tree(),label), 'Selected gender remains visibly labelled'
    else:
        fulltap(label)
    m.wait(lambda: env.local_profile(m).get('avatar',{}).get('gender')==expected,
           'Actual persisted gender '+label,90)
    P.setdefault('genderObservations',[]).append({'label':label,'alreadyPersistedBeforeAction':already,
        'tapPerformed':not already,'persistedGenderMatched':True})


def selected_tab_observed(label):
    root = m.tree()
    parents = {child: parent for parent in root.iter() for child in parent}
    for node in app_nodes(root, label):
        while node is not None:
            if node.get('selected') == 'true':
                return True
            if node.get('scrollable') == 'true':
                break
            node = parents.get(node)
    return False


def reveal_horizontal_closet_tab(label, selected_label, max_swipes=4, direction="right"):
    # Derive the swipe band from the ACTUAL selected Tab and its scroll strip,
    # never a page-height fraction or the vertical item-list scroll region.
    width, height = m.dimensions()
    display = (0, 0, width, height)
    root = m.tree()
    parents = {child: parent for parent in root.iter() for child in parent}
    selected = []
    for value in app_nodes(root, selected_label):
        node = value
        while node is not None:
            if node.get('selected') == 'true' and node.get('enabled', 'true') == 'true':
                if all(node is not old for old in selected):
                    selected.append(node)
                break
            node = parents.get(node)
    assert len(selected) == 1, 'One observed selected closet tab anchors horizontal reveal'
    anchor = selected[0]
    anchor_bounds = m.rect(anchor)
    assert 0 <= anchor_bounds[0] < anchor_bounds[2] <= width and 0 <= anchor_bounds[1] < anchor_bounds[3] <= height
    category_labels = {'헤어', '모자', '안경', '상의', '하의', '양말', '신발', '세트', '액세서리'}
    def horizontal_strip(node):
        if node.get('package') != m.PKG or node.get('scrollable') != 'true':
            return False
        l, t, r, b = m.rect(node)
        labels = {child.get('text') for child in node.iter('node') if child.get('text') in category_labels}
        # Compose exposes this horizontal strip as android.view.View on API29.
        # Its measured wide bounds and descendant category labels identify it;
        # the selected tab need not advertise a click action for observation.
        return r > l and b > t and r - l >= 3 * (b - t) and len(labels) >= 2
    strip = parents.get(anchor)
    while strip is not None and not horizontal_strip(strip):
        strip = parents.get(strip)
    assert strip is not None, 'Selected Tab must belong to an observed horizontal category strip'
    strip_bounds = m.rect(strip)
    left, top, right, bottom = (max(0, strip_bounds[0]), max(0, strip_bounds[1]),
                                min(width, strip_bounds[2]), min(height, strip_bounds[3]))
    y = (anchor_bounds[1] + anchor_bounds[3]) // 2
    start_x, end_x = left + (right - left) * 4 // 5, left + (right - left) // 5
    assert left < end_x < start_x < right and top < y < bottom
    assert direction in ('left','right')
    if direction=='left': start_x,end_x=end_x,start_x
    observation = {'targetLabel': label, 'selectedAnchorLabel': selected_label,
        'actualDisplayBounds': list(display), 'selectedTabBounds': list(anchor_bounds),
        'observedStripClass': strip.get('class'), 'observedStripBounds': list(strip_bounds),
        'stripOrientationEvidence': 'Observed wide scrollable ancestor containing category tabs; not an inferred Android class',
        'selectedAnchorClickable': anchor.get('clickable') == 'true',
        'visibleStripBounds': [left, top, right, bottom], 'horizontalOnly': True,
        'maximumSwipes': max_swipes, 'attempts': [], 'completeTargetObserved': False}
    P['actualClosetHorizontalTabReveal'] = observation
    for attempt in range(max_swipes + 1):
        root = m.tree()
        parents = {child: parent for parent in root.iter() for child in parent}
        strips = [node for node in root.iter('node') if node.get('package') == m.PKG and
                  horizontal_strip(node) and
                  m.rect(node) == strip_bounds]
        assert len(strips) == 1, 'The observed category strip must remain in place before horizontal input'
        hits = []
        for target in click_targets(root, label):
            bounds = m.rect(target)
            current = target
            while current is not None and current is not strips[0]:
                current = parents.get(current)
            # Edge-touching nodes may be partially clipped by the strip. Move
            # them inward rather than accepting truncated accessibility bounds.
            if current is strips[0] and left < bounds[0] < bounds[2] < right and top <= bounds[1] < bounds[3] <= bottom:
                if bounds[2] - bounds[0] >= anchor_bounds[2] - anchor_bounds[0] - 1:
                    hits.append(target)
        assert len(hits) <= 1, 'One fully visible enabled costume tab is allowed'
        observation['attempts'].append({'observationIndex': attempt, 'completeTargetCount': len(hits)})
        if hits:
            observation.update(completeTargetObserved=True, completeTargetBounds=list(m.rect(hits[0])),
                               swipesPerformed=attempt, targetClickable=True, targetEnabled=True)
            return
        if attempt == max_swipes:
            break
        coords = [start_x, y, end_x, y]
        observation['attempts'][-1]['horizontalSwipeInputCoordinates'] = coords
        m.sh('input', 'swipe', *map(str, coords), '350')
    raise AssertionError('Fully visible actual horizontal closet tab not reachable: ' + label)


def scroll_controls(down=True):
    root = m.tree()
    nodes = [node for node in root.iter('node') if node.get('package') == m.PKG and node.get('scrollable') == 'true']
    if nodes:
        left, top, right, bottom = m.rect(max(nodes, key=lambda n: m.rect(n)[3] - m.rect(n)[1]))
    else:
        width, height = m.dimensions()
        left, top, right, bottom = 0, int(height * .58), width, int(height * .88)
    # Keep page swipes outside the editable preview/slider gesture surfaces.
    x = left + max(1, min(12, (right - left) // 10))
    high, low = top + (bottom - top) * 2 // 10, top + (bottom - top) * 8 // 10
    m.sh('input', 'swipe', str(x), str(low if down else high), str(x), str(high if down else low), '350')


def reveal(label, desc=False):
    # The tools sit above their controls; reveal them upwards first. Use the actual
    # tallest scroll viewport rather than a tiny nested horizontal radio row.
    directions = (False, True) if label in ('사진', '배경', '기록', '미니미', '사진 추가', '사진 바꾸기') else (True, False)
    for down in directions:
        for _ in range(14):
            root = m.tree()
            if len(click_targets(root, label, desc)) == 1:
                return root
            scroll_controls(down)
    raise AssertionError('Actual control not reachable: ' + label)


def tap_reveal(label, desc=False):
    reveal(label, desc)
    fulltap(label, desc)


def preview_description():
    for _ in range(12):
        root = m.tree()
        values = [node for node in root.iter('node') if node.get('package') == m.PKG
                  and node.get('content-desc', '').startswith('공유 이미지 미리보기.')]
        assert len(values) <= 1, 'One visible preview is allowed'
        if values:
            node = values[0]
            left, top, right, bottom = m.rect(node)
            text = node.get('content-desc', '')
            aspect = 9/16 if '9:16' in text else 4/5 if '4:5' in text else 1
            if bottom > top and abs((right-left)/(bottom-top) - aspect) < .025:
                return text
        scroll_controls(False)
    raise AssertionError('The actual full preview must be reachable by upward scrolling')


def actual_profile_keyboard_320_font2():
    """Actual local profile editor, private diagnostics, no save/account operation."""
    def viewport_state():
        return {'density': m.sh('wm', 'density'), 'size': m.sh('wm', 'size'),
            'fontScale': m.sh('settings', 'get', 'system', 'font_scale'),
            'userRotation': m.sh('settings', 'get', 'system', 'user_rotation'),
            'accelerometerRotation': m.sh('settings', 'get', 'system', 'accelerometer_rotation'),
            'hardwareKeyboardIme': m.sh('settings', 'get', 'secure', 'show_ime_with_hard_keyboard')}
    original_viewport = viewport_state()
    profile_before = env.local_profile(m)
    observed = {'sourceKind': 'actual Android MainActivity', 'privateDiagnosticsOnly': True,
        'startedAtUtc': env.now(), 'standardViewportBefore': original_viewport,
        'profileSaveOrConnectionActionRequested': False}
    P['actualProfileKeyboardNativeObservation'] = observed
    try:
        m.force_stop()
        observed['nativeViewport'] = env.viewport(m, True)
        m.launch()
        m.wait(lambda: bool(app_nodes(m.tree(), '홈') and app_nodes(m.tree(), '나')), 'Actual Main native320/font2 navigation', 90)
        m.tap_tab('나')
        tap_reveal('프로필 편집')
        m.wait(lambda: bool(app_nodes(m.tree(), '프로필')), 'Actual native320/font2 profile editor')
        field = None
        for _ in range(12):
            root = m.tree()
            fields = [n for n in root.iter('node') if n.get('package') == m.PKG and n.get('class') == 'android.widget.EditText'
                      and m.rect(n)[2] > m.rect(n)[0] and m.rect(n)[3] > m.rect(n)[1]]
            assert len(fields) <= 1, ('profile-nickname-editor-count',len(fields),'maximum1')
            if fields:
                field = fields[0]
                m.tap_node(field, root)
                break
            scroll_controls(True)
        assert field is not None, 'Actual nickname input must be reachable'
        m.wait(lambda: bool(re.search(r'\bmInputShown=true\b|\bmIsInputViewShown=true\b', m.sh('dumpsys', 'input_method'))),
               'Actual native320/font2 Main profile IME shown', 30)
        # Preserve the actual native frame and controls before geometry checks.
        initial_root=m.tree()
        initial_raw=m.adb('exec-out','screencap','-p');m.dimensions(initial_raw)
        initial_image_name=a.name+'-private-main-profile-ime320-font2-before-reveal.png'
        initial_tree_name=a.name+'-private-main-profile-ime320-font2-before-reveal-ui.xml'
        initial_tree_raw=ET.tostring(initial_root,encoding='utf-8',xml_declaration=True)
        env.exclusive_bytes(W/initial_image_name,initial_raw,private=True)
        env.exclusive_bytes(W/initial_tree_name,initial_tree_raw,private=True)
        initial_back=click_targets(initial_root,'뒤로',True);initial_save=click_targets(initial_root,'저장')
        observed['nativeBeforeGeometryAssertions']={
            'screenshot':{'file':initial_image_name,'sha256':sha(initial_raw),'privateWorkOnly':True},
            'hierarchy':{'file':initial_tree_name,'sha256':sha(initial_tree_raw),'privateWorkOnly':True},
            'backBounds':[list(m.rect(node)) for node in initial_back],
            'saveBounds':[list(m.rect(node)) for node in initial_save]}
        # Observe native geometry before any assertion, and reveal the complete
        # save control inside the actual app scroll viewport above the IME.
        # This remains reveal-only: never tap save or a connection action.
        windows = m.sh('dumpsys', 'window', 'windows')
        window_name = a.name + '-private-main-profile-ime320-font2-windows.txt'
        env.exclusive_bytes(W / window_name, windows.encode(), private=True)
        blocks = re.split(r'(?m)^  Window #\d+ ', windows)
        main = [block for block in blocks if re.search(
            r'^Window\{[^\n}]+ com\.runmoni\.app/com\.runmoni\.app\.MainActivity\}:', block)]
        assert len(main) == 1, ('profile-main-window-count',len(main),1)
        frame = re.search(r'\bvisible=\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]', main[0])
        assert frame, ('profile-visible-frame-observed',bool(frame))
        visible = list(map(int, frame.groups()))
        density_text = m.sh('wm', 'density')
        density = re.search(r'Override density:\s*(\d+)', density_text)
        actual_font=m.sh('settings','get','system','font_scale')
        assert density and actual_font=='2.0', ('profile-native-viewport',density_text,actual_font,'fontScale2.0')
        width, height = m.dimensions()
        assert abs(width*160/int(density[1])-320) <= 1, ('profile-width-dp',width*160/int(density[1]),'320±1',int(density[1]))
        assert 0 <= visible[0] < visible[2] <= width and 0 <= visible[1] < visible[3] < height, ('profile-visible-window',visible,[width,height])
        minimum = 48*int(density[1])/160
        def fully_visible(node):
            bounds=m.rect(node)
            return (bounds[2]-bounds[0]+1 >= minimum and bounds[3]-bounds[1]+1 >= minimum and
                    visible[0] <= bounds[0] < bounds[2] <= visible[2] and
                    visible[1] <= bounds[1] < bounds[3] <= visible[3])
        reveal_observation={'maximumAttempts':28,'minimum48dpTargetPx':minimum,
            'visibleWindowBounds':visible,'saveOrConnectionTapped':False,'attempts':[]}
        observed['boundedAboveImeSaveReveal']=reveal_observation
        complete=False
        for down in (True,False):
            for attempt in range(14):
                root=m.tree();save_hits=click_targets(root,'저장')
                assert len(save_hits)<=1, ('profile-save-count',len(save_hits),[list(m.rect(node)) for node in save_hits])
                row={'direction':'down' if down else 'up','directionAttempt':attempt,
                     'saveBounds':list(m.rect(save_hits[0])) if save_hits else None,
                     'fullTargetAboveIme':bool(save_hits and fully_visible(save_hits[0]))}
                reveal_observation['attempts'].append(row)
                if row['fullTargetAboveIme']:
                    complete=True;break
                candidates=[]
                for node in root.iter('node'):
                    if node.get('package')==m.PKG and node.get('scrollable')=='true':
                        bounds=m.rect(node)
                        clipped=[max(bounds[0],visible[0]),max(bounds[1],visible[1]),
                                 min(bounds[2],visible[2]),min(bounds[3],visible[3])]
                        if clipped[2]-clipped[0]>=48 and clipped[3]-clipped[1]>=48:
                            candidates.append((node,clipped))
                assert candidates, ('profile-scroll-viewport-above-ime',visible,'minimumIntersection48px')
                node,clipped=max(candidates,key=lambda pair:pair[1][3]-pair[1][1])
                left,top,right,bottom=clipped
                x=left+max(1,min(12,(right-left)//10))
                high,low=top+(bottom-top)*2//10,top+(bottom-top)*8//10
                coords=[x,low if down else high,x,high if down else low]
                assert all(visible[0]<coords[i]<visible[2] for i in (0,2)), ('profile-swipe-x-inside-visible',coords,visible)
                assert all(visible[1]<coords[i]<visible[3] for i in (1,3)), ('profile-swipe-y-above-ime',coords,visible)
                row.update(observedScrollableBounds=list(m.rect(node)),actualClippedViewport=clipped,
                           swipeCoordinates=coords,actualHierarchyUsed=True)
                m.sh('input','swipe',*(str(value) for value in coords),'350')
            if complete:break
        root=m.tree();back,save=click_targets(root,'뒤로',True),click_targets(root,'저장')
        controls={'back':[list(m.rect(node)) for node in back],'save':[list(m.rect(node)) for node in save]}
        final_ime_raw=m.sh('dumpsys','input_method')
        final_ime_visible=bool(re.search(r'\bmInputShown=true\b|\bmIsInputViewShown=true\b',final_ime_raw))
        final_ime_name=a.name+'-private-main-profile-ime320-font2-final-input-method.txt'
        env.exclusive_bytes(W/final_ime_name,final_ime_raw.encode(),private=True)
        final_windows=m.sh('dumpsys','window','windows')
        final_window_name=a.name+'-private-main-profile-ime320-font2-final-windows.txt'
        env.exclusive_bytes(W/final_window_name,final_windows.encode(),private=True)
        final_blocks=re.split(r'(?m)^  Window #\d+ ',final_windows)
        final_main=[block for block in final_blocks if re.search(
            r'^Window\{[^\n}]+ com\.runmoni\.app/com\.runmoni\.app\.MainActivity\}:',block)]
        final_frame=(re.search(r'\bvisible=\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]',final_main[0])
                     if len(final_main)==1 else None)
        final_visible=list(map(int,final_frame.groups())) if final_frame else None
        raw=m.adb('exec-out','screencap','-p');m.dimensions(raw)
        name=a.name+'-private-main-profile-ime320-font2.png'
        tree_name=a.name+'-private-main-profile-ime320-font2-ui.xml'
        env.exclusive_bytes(W/name,raw,private=True)
        tree_raw=ET.tostring(root,encoding='utf-8',xml_declaration=True)
        env.exclusive_bytes(W/tree_name,tree_raw,private=True)
        focus=m.focus('main')
        observed.update(actualKeyboardVisible=final_ime_visible,visibleWindowBounds=final_visible,
            initialVisibleWindowBounds=visible,finalMainWindowCount=len(final_main),
            actualControlBoundsBeforeAssertions=controls,minimum48dpTargetPx=minimum,
            densityDpi=int(density[1]),physicalWidthPx=width,actualComputedWidthDp=width*160/int(density[1]),
            actualFontScale=2.0,screenshot={'file':name,'sha256':sha(raw),'privateWorkOnly':True,
                'sourceKind':'actual Android MainActivity','expectedWindowFocused':focus},
            nativeHierarchy={'file':tree_name,'sha256':sha(tree_raw),'privateWorkOnly':True},
            initialWindowDiagnostic={'file':window_name,'sha256':sha(windows.encode()),'privateWorkOnly':True},
            windowDiagnostic={'file':final_window_name,'sha256':sha(final_windows.encode()),'privateWorkOnly':True},
            finalInputMethodDiagnostic={'file':final_ime_name,'sha256':sha(final_ime_raw.encode()),'privateWorkOnly':True})
        assert final_ime_visible, ('profile-ime-still-shown-after-reveal',final_ime_visible,'no-extra-wait')
        assert len(final_main)==1 and final_visible is not None, ('profile-final-visible-frame',len(final_main),final_visible)
        assert (0 <= final_visible[0] < final_visible[2] <= width and
                0 <= final_visible[1] < final_visible[3] < height), ('profile-final-above-ime-frame',final_visible,[width,height])
        visible=final_visible
        assert complete, ('profile-save-whole-target-revealed',controls,visible,minimum,'maximumAttempts28')
        assert len(back)==len(save)==1, ('profile-control-counts',len(back),len(save),controls)
        assert fully_visible(back[0]), ('profile-back-full48dp-inside-visible',controls['back'],visible,minimum,int(density[1]))
        assert fully_visible(save[0]), ('profile-save-full48dp-inside-visible',controls['save'],visible,minimum,int(density[1]))
        assert focus, ('profile-main-window-focused',focus)
        observed.update(actualControlsInsideVisibleWindow=True,
            actualControlBounds={key:value[0] for key,value in controls.items()})
        fulltap('뒤로', True)
        m.wait(lambda: bool(app_nodes(m.tree(), '나')), 'Actual profile close returns parent without save')
        # Keep the original predicate and30-second wait. If it fails, retain
        # native evidence before finally force-stops/restores the viewport.
        close_ime={'samples':[],'lastRaw':None}
        close_ime_started=time.monotonic()
        def original_keyboard_hidden_predicate():
            value=m.sh('dumpsys','input_method')
            shown=bool(re.search(r'\bmInputShown=true\b|\bmIsInputViewShown=true\b',value))
            close_ime['lastRaw']=value
            close_ime['samples'].append({'elapsedHostSeconds':round(time.monotonic()-close_ime_started,3),
                'originalAnyShownPredicate':shown,'inputMethodRawSha256':sha(value.encode())})
            return not shown
        try:
            m.wait(original_keyboard_hidden_predicate,'Actual Main profile close hides IME',30)
        except BaseException:
            close_diagnostic={'originalWaitFailed':True,'originalBudgetSeconds':30,
                'failureTimeElapsedHostSeconds':round(time.monotonic()-close_ime_started,3),
                'failureObservedAtUtc':env.now(),'afterFailureReaderTimings':{},
                'predicateUnchanged':True,'samples':close_ime['samples'],
                'diagnosticsCapturedAfterFailureBeforeForceStop':True,'extraWaitOrRetryPerformed':False}
            observed['closeImeFailureDiagnostic']=close_diagnostic
            def preserve_close_file(suffix,raw):
                filename=a.name+'-private-main-profile-after-close-'+suffix
                env.exclusive_bytes(W/filename,raw,private=True)
                return {'file':filename,'sha256':sha(raw),'privateWorkOnly':True}
            if close_ime['lastRaw'] is not None:
                close_diagnostic['lastRawObservedAtElapsedHostSeconds']=close_ime['samples'][-1]['elapsedHostSeconds']
                close_diagnostic['lastRawInsideOriginalWait']=preserve_close_file('last-input-method.txt',close_ime['lastRaw'].encode())
            for label,suffix,reader in (
                ('screenshot','native.png',lambda:m.adb('exec-out','screencap','-p')),
                ('window','windows.txt',lambda:m.sh('dumpsys','window','windows').encode()),
                ('inputMethodAfterFailure','current-input-method.txt',lambda:m.sh('dumpsys','input_method').encode())):
                diagnostic_started=time.monotonic()
                timing={'startedAtUtc':env.now(),
                    'startElapsedHostSeconds':round(diagnostic_started-close_ime_started,3)}
                close_diagnostic['afterFailureReaderTimings'][label]=timing
                try:close_diagnostic[label]=preserve_close_file(suffix,reader())
                except BaseException as diagnostic_failure:
                    close_diagnostic[label+'FailureType']=type(diagnostic_failure).__name__
                finally:
                    diagnostic_finished=time.monotonic()
                    timing.update({'finishedAtUtc':env.now(),
                        'endElapsedHostSeconds':round(diagnostic_finished-close_ime_started,3),
                        'diagnosticElapsedHostSeconds':round(diagnostic_finished-diagnostic_started,3)})
            raise
        assert env.local_profile(m) == profile_before, 'profile-unchanged-without-saving'
        observed.update(profileUnchangedWithoutSaving=True, actualKeyboardHiddenAfterClose=True, passed=True)
        P.update(actualProfileKeyboardObserved=True, actualProfileKeyboardNative320Font2=True,
                 actualProfileKeyboardControlsInsideVisibleWindow=True, actualProfileClosedWithoutSaving=True)
    finally:
        m.force_stop()
        env.viewport(m, False)
        observed['standardViewportAfter'] = viewport_state()
        observed['viewportRestoredExactly'] = observed['standardViewportAfter'] == original_viewport
        assert observed['viewportRestoredExactly'], ('profile-viewport-restored',observed['standardViewportAfter'],original_viewport)
        observed['profileStillUnchanged'] = env.local_profile(m) == profile_before
        assert observed['profileStillUnchanged'], 'profile-still-unchanged-after-native-viewport-restoration'
        m.launch()
        m.wait(lambda: bool(app_nodes(m.tree(), '홈') and app_nodes(m.tree(), '나')), 'Actual standard Main navigation restored', 90)
        observed['finishedAtUtc'] = env.now()


def actual_closet_pause_resume(avatar_pixels_ready):
    """Separate real Main readiness-gated observation; original test is unchanged."""
    original_scale = m.sh('settings', 'get', 'global', 'animator_duration_scale')
    assert original_scale in ('0', '0.0')
    observed = {'sourceKind': 'actual Android MainActivity',
        'originalAnimatorDurationScale': original_scale, 'samples': [],
        'startedAtUtc': env.now(), 'original200msTestAssertionChanged': False,
        'originalTestFailureCauseEstablished': False,
        'scope': 'Readiness-gated settled running/pause/resume pixels only; not a no-transient-loading or timing/FPS claim'}
    P['actualClosetPauseResumeObservation'] = observed
    started = time.monotonic()

    def canvas_bounds():
        if not avatar_pixels_ready():
            return False
        root = m.tree()
        candidates = [m.rect(node) for node in app_nodes(root, '러닝 미니미', True)]
        candidates = sorted(candidates, key=lambda b: (b[2]-b[0])*(b[3]-b[1]), reverse=True)
        if not candidates:
            return False
        area = lambda b: (b[2]-b[0])*(b[3]-b[1])
        assert len(candidates) == 1 or area(candidates[0]) > area(candidates[1]), 'One largest actual primary avatar Canvas'
        return candidates[0]

    def sample(phase, bounds):
        assert m.focus('main'), 'MainActivity remains focused for diagnostic pixels'
        raw = m.adb('exec-out', 'screencap', '-p')
        width, height = m.dimensions(raw)
        left, top, right, bottom = bounds
        assert 0 <= left < right <= width and 0 <= top < bottom <= height
        pixels = Image.open(io.BytesIO(raw)).convert('RGB').crop(bounds).tobytes()
        name = f'{a.name}-private-closet-{phase}-{len(observed["samples"])+1:02d}.png'
        env.exclusive_bytes(W / name, raw, private=True)
        row = {'phase': phase, 'file': name, 'privateWorkOnly': True,
            'sourceKind': 'actual Android MainActivity', 'expectedWindowFocused': True,
            'sha256': sha(raw), 'bounds': list(bounds), 'width': width, 'height': height,
            'cropPixelSha256': sha(pixels), 'capturedAtUtc': env.now(),
            'elapsedSeconds': round(time.monotonic()-started, 3)}
        observed['samples'].append(row)
        return row['cropPixelSha256']

    def moving(phase, bounds):
        hashes = set()
        deadline = time.monotonic()+15
        while len(hashes) < 3 and time.monotonic() < deadline:
            hashes.add(sample(phase, bounds))
            time.sleep((.17, .29, .11)[len(observed['samples']) % 3])
        assert len(hashes) >= 3, 'Three actual ready Main avatar pixel states within separate15s observation'
        observed[phase+'DistinctPixelHashes'] = len(hashes)

    try:
        m.sh('settings', 'put', 'global', 'animator_duration_scale', '1')
        observed['enabledAnimatorDurationScaleObserved'] = m.sh('settings', 'get', 'global', 'animator_duration_scale')
        assert observed['enabledAnimatorDurationScaleObserved'] in ('1', '1.0')
        bounds = m.wait(canvas_bounds, 'Actual Main running primary avatar Canvas ready', 120)
        observed['runningCanvasReadyObserved'] = True
        moving('running', bounds)
        fulltap('달리기 멈춤', True)
        m.wait(lambda: bool(app_nodes(m.tree(), '달리기 재생', True)), 'Actual Main pause labelled state', 90)
        observed['actualPauseLabelObserved'] = True
        paused_bounds = m.wait(canvas_bounds, 'Actual Main paused primary avatar Canvas ready', 120)
        assert paused_bounds == bounds
        observed['pausedCanvasReadyObserved'] = True
        paused = [sample('paused', bounds)]
        for _ in range(4):
            time.sleep(.2)
            paused.append(sample('paused', bounds))
        assert len(set(paused)) == 1, 'Five exact settled paused Main avatar pixel hashes'
        observed['pausedFivePixelHashesIdentical'] = True
        fulltap('달리기 재생', True)
        m.wait(lambda: bool(app_nodes(m.tree(), '달리기 멈춤', True)), 'Actual Main resumed labelled state', 90)
        observed['actualResumeLabelObserved'] = True
        resumed_bounds = m.wait(canvas_bounds, 'Actual Main resumed primary avatar Canvas ready', 120)
        assert resumed_bounds == bounds
        observed['resumedCanvasReadyObserved'] = True
        moving('resumed', bounds)
        observed['passed'] = True
    finally:
        m.sh('settings', 'put', 'global', 'animator_duration_scale', original_scale)
        observed['animatorDurationScaleRestoredExactly'] = m.sh('settings', 'get', 'global', 'animator_duration_scale') == original_scale
        observed['finishedAtUtc'] = env.now()
        observed['elapsedSeconds'] = round(time.monotonic()-started, 3)
        assert observed['animatorDurationScaleRestoredExactly']


def caption_field_echoes(expected):
    root = m.tree()
    return any(node.get('package') == m.PKG and node.get('class') == 'android.widget.EditText'
               and node.get('focused') == 'true' and node.get('text') == expected for node in root.iter('node'))


def radio_choice_observed(label):
    # Compose RadioButton roles expose Selected as checked/checkable in the
    # platform accessibility tree; Tab roles expose it as selected instead.
    root = m.tree()
    parents = {child: parent for parent in root.iter() for child in parent}
    for node in app_nodes(root, label):
        while node is not None:
            if node.get('checkable') == 'true':
                return node.get('checked') == 'true'
            node = parents.get(node)
    return False


def selected_item_observation(label):
    # Radio roles can export checked/checkable; other selectable roles export
    # selected. Read the actual selected ancestor, never infer from colour/icon.
    root = m.tree()
    parents = {child: parent for parent in root.iter() for child in parent}
    matches = []
    for value in app_nodes(root, label):
        node = value
        while node is not None:
            selected = (node.get('checkable') == 'true' and node.get('checked') == 'true') or node.get('selected') == 'true'
            if selected:
                left, top, right, bottom = m.rect(node)
                if node.get('enabled', 'true') == 'true' and right > left and bottom > top:
                    if all(node is not old for old in matches):
                        matches.append(node)
                break
            node = parents.get(node)
    assert len(matches) <= 1, 'The actually selected item must be unambiguous'
    if not matches:
        return False
    node = matches[0]
    return {'checkable': node.get('checkable'), 'checked': node.get('checked'),
            'selected': node.get('selected'), 'clickable': node.get('clickable'),
            'enabled': node.get('enabled', 'true'), 'bounds': list(m.rect(node))}


def reveal_visible_radio(label):
    # Read an already selected RadioButton without requiring another click.
    # Selected Compose rows may truthfully export clickable=false.
    for down in (True, False):
        for _ in range(14):
            root = m.tree()
            parents = {child: parent for parent in root.iter() for child in parent}
            matches = []
            for value in app_nodes(root, label):
                node = value
                while node is not None:
                    if node.get('checkable') == 'true':
                        left, top, right, bottom = m.rect(node)
                        if node.get('enabled', 'true') == 'true' and right > left and bottom > top:
                            if all(node is not old for old in matches):
                                matches.append(node)
                        break
                    node = parents.get(node)
            if len(matches) == 1:
                value = matches[0]
                return {'checkable': value.get('checkable'), 'checked': value.get('checked'),
                        'clickable': value.get('clickable'), 'enabled': value.get('enabled', 'true'),
                        'bounds': list(m.rect(value)), 'observationDoesNotRequireRepeatClick': True}
            assert len(matches) <= 1, 'Actual selected radio must be unambiguous'
            scroll_controls(down)
    raise AssertionError('Actual visible radio not reachable: ' + label)


def await_actual_native_map():
    """Bounded accessibility readiness only, never pixel/provider verification.

    Production exposes its Material progress as android.widget.ProgressBar.
    No source change/test-tag export is required. Require its absence in the
    viewport, actual native semantics and full credit; a raster fallback fails.
    Root still must inspect the ORIGINAL screenshot for roads/labels/blank GL.
    """
    state = {'observationBudgetSeconds': 90, 'providerSlaClaimed': False,
             'loadingIndicatorSeen': False, 'consecutiveReadySamples': 0, 'samples': [],
             'pixelContentVerifiedByDriver': False, 'httpOrTileBodiesMeasured': False}
    P['actualNativeMapReadinessObservation'] = state

    def sample():
        root = m.tree()
        fallback = app_nodes(root, '벡터 지도 연결 실패 · 다시 시도')
        assert not fallback, 'Actual Main changed to raster fallback; do not label its screenshot native-rendered'
        maps = [n for n in root.iter('node') if n.get('package') == m.PKG and
                n.get('content-desc', '').startswith(('탐색용 벡터 지도.', '확인된 위치 주변 벡터 지도.'))]
        options = click_targets(root, '지도 조작')
        credits = app_nodes(root, '© OpenMapTiles · Data from OpenStreetMap')
        if len(maps) != 1 or len(options) != 1 or len(credits) != 1:
            state['consecutiveReadySamples'] = 0
            state['samples'].append({'nativeViewportCount': len(maps), 'enabledMapOptionsCount': len(options),
                                     'fullCreditCount': len(credits), 'ready': False})
            return False
        bounds = m.rect(maps[0])
        def intersects_viewport(node):
            left, top, right, bottom = m.rect(node)
            return left < bounds[2] and right > bounds[0] and top < bounds[3] and bottom > bounds[1]
        loading = [node for node in root.iter('node') if node.get('package') == m.PKG and
                   (node.get('class') == 'android.widget.ProgressBar' or
                    node.get('resource-id', '').split('/')[-1] == 'map_vector_loading') and intersects_viewport(node)]
        state['loadingIndicatorSeen'] |= bool(loading)
        ready = not loading and bounds[2] > bounds[0] and bounds[3] > bounds[1]
        state['consecutiveReadySamples'] = state['consecutiveReadySamples'] + 1 if ready else 0
        state['samples'].append({'nativeViewportBounds': list(bounds), 'loadingIndicatorCount': len(loading),
                                 'fullCreditCount': len(credits), 'enabledMapOptionsCount': len(options), 'ready': ready})
        return root if state['consecutiveReadySamples'] >= 2 else False

    root = m.wait(sample, 'Actual Main native map retains viewport/credit/controls and leaves loading state', 90)
    state['loadingAbsenceObservedBeforeCapture'] = True
    state['rasterFallbackAccepted'] = False
    state['independentRootPixelInspectionStillRequired'] = True
    return root


def ready():
    def settled():
        root = m.tree()
        return bool(app_nodes(root, '기록 공유') and len(click_targets(root, '이미지 저장')) == 1 and len(click_targets(root, '공유하기')) == 1)
    return m.wait(settled, 'Actual export controls enabled after image render', 120)


def capture(suffix):
    m.save_png(a.name + '-main-' + suffix + '.png', 'main')


def current_appearance_mode():
    # Observe an actual completed app preference write. Do not inject mode bytes
    # to claim the settings UI worked; only actual labelled taps choose a mode.
    try:
        value = env.preference_file_snapshot(m, 'runmoni_appearance.xml')
        if not value['present']:
            return 'system'
        root = ET.fromstring(value['raw'])
        return next((node.text for node in root if node.get('name') == 'mode'), 'system')
    except (AssertionError, ET.ParseError):
        return None


def choose_appearance(mode, label, *, settings_capture=None):
    assert (mode, label) in (('light', '밝게'), ('dark', '어둡게'))
    fulltap('계정 및 러닝 설정', True)
    m.wait(lambda: bool(app_nodes(m.tree(), '설정')), 'Actual settings page opened')
    tap_reveal(label)
    m.wait(lambda: radio_choice_observed(label) and current_appearance_mode() == mode,
           'Actual selected appearance row and persisted device mode ' + mode, 90)
    observed = env.preference_file_snapshot(m, 'runmoni_appearance.xml')
    P.setdefault('actualAppearanceSelections', []).append({'mode': mode, 'label': label,
        'actualLabelledTapRequired': True, 'checkedRadioObserved': True,
        'wholePreferenceSha256': sha(observed['raw']), 'persistedModeMatched': True})
    if settings_capture:
        assert mode == 'dark'
        capture(settings_capture)
    fulltap('뒤로', True)
    m.wait(lambda: bool(app_nodes(m.tree(), '홈') and app_nodes(m.tree(), '나')),
           'Settings Back returns to actual root navigation')


def own_detail():
    m.tap_tab('나')
    started = marker['run']['startedAtMillis']
    zone = datetime.timezone.utc  # Require actual UTC device below; never relabel as Korean phone time.
    when = datetime.datetime.fromtimestamp(started / 1000, zone)
    signature = (f'{when.month}월 {when.day}일 ({"월화수목금토일"[when.weekday()]}) {when:%H:%M}', '5.24 km', '30:00')
    for row in baseline['runs']:
        old_when = datetime.datetime.fromtimestamp(row['startedAtMillis'] / 1000, zone)
        old_signature = (f'{old_when.month}월 {old_when.day}일 ({"월화수목금토일"[old_when.weekday()]}) {old_when:%H:%M}',
                         f'{row["distanceMeters"] / 1000:.2f} km', m.duration(row['activeDurationMillis']))
        assert old_signature != signature, 'Never target an ambiguous preexisting record'
    for _ in range(14):
        root = m.tree()
        hits = []
        for node in root.iter('node'):
            text = '\n'.join(n.get('text', '') for n in node.iter('node'))
            if node.get('package') == m.PKG and node.get('clickable') == 'true' and all(part in text for part in signature):
                hits.append(node)
        if hits:
            hits.sort(key=lambda n: (m.rect(n)[2] - m.rect(n)[0]) * (m.rect(n)[3] - m.rect(n)[1]))
            assert len(hits) == 1 or m.rect(hits[0]) != m.rect(hits[1])
            m.tap_node(hits[0], root, climb=False)
            break
        scroll_controls(True)
    else:
        raise AssertionError('Owned synthetic completed record not reachable')
    m.wait(lambda: bool(app_nodes(m.tree(), '러닝 기록')), 'Actual owned result page')
    text = '\n'.join(n.get('text', '') for n in m.tree().iter('node'))
    assert all(part in text for part in signature)
    P['ownResultUiSignatureObserved'] = list(signature)


DOCUMENTS_PACKAGES = ('com.android.documentsui', 'com.google.android.documentsui')


def own_document_rows(root):
    """An actual DocumentsUI list/grid item with an exact owned filename and document icon."""
    parents = {child: parent for parent in root.iter() for child in parent}
    result = []
    title_ids = ('android:id/title',) + tuple(package + ':id/title' for package in DOCUMENTS_PACKAGES)
    icon_ids = tuple(package + ':id/' + icon for package in DOCUMENTS_PACKAGES
                     for icon in ('icon_mime', 'icon_mime_lg', 'icon_thumb'))
    for item in root.iter('node'):
        if item.get('package') not in DOCUMENTS_PACKAGES or item.get('enabled', 'true') != 'true':
            continue
        title = (item.get('text') == PHOTO and item.get('class', '').endswith('TextView')
                 and item.get('resource-id') in title_ids)
        grid = (item.get('content-desc', '').split(', ', 1)[0] == PHOTO
                and item.get('class', '').endswith(('LinearLayout', 'FrameLayout')))
        if not (title or grid):
            continue
        ancestors = []
        current = item
        while current in parents:
            current = parents[current]
            ancestors.append(current)
        lists = [node for node in ancestors if node.get('class', '').endswith(('RecyclerView', 'GridView', 'ListView'))
                 or node.get('resource-id', '').endswith(':id/dir_list')]
        if not lists:
            continue
        # The actual API29 grid exposes filename+metadata on a thumbnail holder,
        # without a visible TextView. Neither query fields nor suggestions have this icon/list structure.
        holders = [item]
        for ancestor in ancestors:
            if ancestor in lists:
                break
            holders.append(ancestor)
        if any(node.get('resource-id') in icon_ids for holder in holders for node in holder.iter('node')):
            result.append(item)
    return result


def tap_documents_action(label, desc=False):
    root = m.tree()
    hits = [n for n in m.nodes(root, label, desc) if n.get('package') in DOCUMENTS_PACKAGES]
    assert len(hits) == 1, ('Exactly one actual DocumentsUI action', label, len(hits))
    m.tap_node(hits[0], root, climb=False)


def restore_documents_visibility():
    if not P.get('internalStorageVisibilityTemporarilyEnabled') or P.get('internalStorageVisibilityRestored'):
        return
    for _ in range(4):
        root = m.tree()
        hide = m.nodes(root, 'Hide internal storage')
        show = m.nodes(root, 'Show internal storage')
        if hide:
            assert len(hide) == 1
            m.tap_node(hide[0], root, climb=False)
            tap_documents_action('More options', True)
            root = m.tree()
            assert len(m.nodes(root, 'Show internal storage')) == 1, 'Original hidden-device setting is observable again'
            m.sh('input', 'keyevent', '4')
            P['internalStorageVisibilityRestored'] = True
            return
        if show:
            assert len(show) == 1
            m.sh('input', 'keyevent', '4')
            P['internalStorageVisibilityRestored'] = True
            return
        more = m.nodes(root, 'More options', True)
        if len(more) == 1:
            m.tap_node(more[0], root, climb=False)
        else:
            focus = next((l for l in m.sh('dumpsys', 'window', 'displays').splitlines() if 'mCurrentFocus=' in l), '')
            assert any(package + '/' in focus for package in DOCUMENTS_PACKAGES)
            m.sh('input', 'keyevent', '4')
    raise AssertionError('Temporary DocumentsUI storage visibility must be restored')


def browse_own_photo_folder():
    tap_documents_action('Show roots', True)
    root = m.tree()
    labels = (m.sh('getprop', 'ro.product.model'), 'Internal storage', 'Internal shared storage')
    storage = [n for n in root.iter('node') if n.get('text') in labels]
    if not storage:
        m.sh('input', 'keyevent', '4')
        tap_documents_action('More options', True)
        root = m.tree()
        show = m.nodes(root, 'Show internal storage')
        assert len(show) == 1, 'Only observed temporary storage visibility is changed'
        m.tap_node(show[0], root, climb=False)
        P['internalStorageVisibilityTemporarilyEnabled'] = True
        tap_documents_action('Show roots', True)
        root = m.tree()
        storage = [n for n in root.iter('node') if n.get('text') in labels]
    assert len(storage) == 1, 'Exactly one actual device storage root'
    m.tap_node(storage[0], root, climb=False)
    for folder in ('Pictures', 'RunmoniReview'):
        tap_documents_action(folder)
    root = m.tree()
    assert len(own_document_rows(root)) == 1, 'The exact nonce photo is visible in its own folder'
    P['systemPhotoPickerFolderNavigationUsed'] = True
    restore_documents_visibility()
    root = m.tree()
    assert len(own_document_rows(root)) == 1, 'Owned file remains after restoring picker visibility'
    return root


def pick_own_photo():
    fulltap('사진 추가')
    P['systemPhotoPickerRequestedByThisAttempt'] = True
    m.wait(lambda: not m.focus('main'), 'Real system image picker leaves Main')
    root = m.tree()
    m.private_write(W / (a.name + '-private-picker-initial.xml'), ET.tostring(root, encoding='utf-8'))
    stage('actual-system-photo-picker-opened')
    candidates = own_document_rows(root)
    assert len(candidates) <= 1
    if not candidates:
        root = browse_own_photo_folder()
        candidates = own_document_rows(root)
    assert len(candidates) == 1, 'Only one actual nonce image document row may be selected'
    node = candidates[0]
    P['systemPhotoPickerOwnFilename'] = PHOTO
    P['systemPhotoPickerOwnFilenameBounds'] = list(m.rect(node))
    P['systemPhotoPickerOwnFilenameResourceId'] = node.get('resource-id')
    P['systemPhotoPickerOwnFilenameNodeClass'] = node.get('class')
    P['systemPhotoPickerOwnFilenameIsGridDescription'] = node.get('content-desc', '').split(', ', 1)[0] == PHOTO
    P['systemPhotoPickerSelectionSource'] = 'exact filename in a DocumentsUI list/grid document holder with MIME/thumbnail icon'
    m.tap_node(node, root, climb=False)
    stage('actual-nonce-document-file-row-selected')
    m.wait(lambda: bool(m.focus('main') and app_nodes(m.tree(), '사진 바꾸기')), 'Actual picker callback loads own synthetic photo', 90)
    ready()
    P['actualSystemPhotoPickerUsed'] = True


def dismiss_owned_document_picker():
    P['ownedPickerDismissalBackActions'] = 0
    if not P.get('systemPhotoPickerRequestedByThisAttempt'):
        P['ownedPickerDismissalNotRequested'] = True
        return
    for _ in range(4):
        windows = m.sh('dumpsys', 'window', 'displays')
        focus = next((line for line in windows.splitlines() if 'mCurrentFocus=' in line), '')
        if not any(package + '/' in focus for package in DOCUMENTS_PACKAGES):
            P['ownedPickerNoLongerForegroundAtCleanup'] = True
            return
        m.sh('input', 'keyevent', '4')
        P['ownedPickerDismissalBackActions'] += 1
        time.sleep(.4)
    raise AssertionError('The picker opened by this review must leave the foreground after bounded Back actions')


try:
    stage('guards-and-original-backups')
    assert m.adb('emu', 'avd', 'name').decode().splitlines()[0] == 'Runmoni_Cloud_29'
    assert m.sh('getprop', 'ro.build.version.sdk') == '29'
    assert not m.pid() and m.dump() is None, 'Start force-stopped with no recorder'
    P['deviceTimezone'] = m.sh('getprop', 'persist.sys.timezone')
    assert P['deviceTimezone'] in ('UTC', 'Etc/UTC', 'GMT')
    braw = (E / a.binary).read_bytes()
    binary = json.loads(braw)
    P['binaryReferenceSha256'] = sha(braw)
    assert binary['version'] == P['version'] and binary['versionCode'] == P['versionCode']
    assert binary['buildSucceeded'] and binary['signing'] == 'local TEST debug, not retained release signing'
    installation_raw = INSTALL_PATH.read_bytes()
    installation = json.loads(installation_raw)
    assert installation['binaryReference'] == a.binary
    assert (installation['version'], installation['versionCode']) == ('0.9.0-dev', 32)
    assert installation['uninstallOrDataReset'] is False
    assert installation['preInstallPreferencesPreservedExactly'] is True
    assert installation['preInstallAppearancePreferencesPreservedExactly'] is True
    assert all(path.is_file() and path.stat().st_mode & 0o777 == 0o600
               for path in (PREINSTALL_XML, PREINSTALL_APPEARANCE)), 'Exact selected-input private install backups exist'
    preinstall_xml = PREINSTALL_XML.read_bytes()
    ET.fromstring(preinstall_xml)
    assert sha(preinstall_xml) == installation['preInstallXmlSha256']
    preinstall_appearance = json.loads(PREINSTALL_APPEARANCE.read_text())
    assert isinstance(preinstall_appearance['present'], bool)
    if preinstall_appearance['present']:
        assert isinstance(preinstall_appearance['xml'], str)
        ET.fromstring(preinstall_appearance['xml'])
    else:
        assert preinstall_appearance['xml'] is None
    P['installationReference'] = INSTALL_PATH.name
    P['installationReferenceSha256'] = sha(installation_raw)
    P['preInstallInputNumber'] = INPUT_NUMBER
    P['sourceMapsBefore'] = env.source_maps(binary)
    P['installed'] = []
    for package, expected in zip((m.PKG, TEST), binary['binaries']):
        path = m.sh('pm', 'path', package).removeprefix('package:')
        digest = m.sh('sha256sum', path, timeout=120).split()[0]
        assert digest == expected['sha256']
        P['installed'].append({'package': package, 'sha256': digest})
    cfg = (R / 'app/build/generated/source/buildConfig/debug/com/runmoni/app/BuildConfig.java').read_text()
    assert 'boolean FIREBASE_ENABLED = false;' in cfg
    old_account = env.account_snapshot(m)
    old_appearance = env.preference_file_snapshot(m, 'runmoni_appearance.xml')
    old_fixture_preferences = env.owned_fixture_preferences(m)
    before = env.environment_snapshot(m)
    old = m.prefs()
    ET.fromstring(old)
    assert old == preinstall_xml, 'Original whole XML still matches this selected input installation'
    assert old_appearance == {'present': preinstall_appearance['present'],
        'raw': preinstall_appearance['xml'].encode('utf-8') if preinstall_appearance['present'] else b''}, \
        'Original appearance presence and whole bytes still match this selected input installation'
    baseline = m.snapshot('before')
    assert all(row['finishedAtMillis'] > 0 for row in baseline['runs'])
    assert not any(row['id'] == RECORD for row in baseline['runs'])
    for suffix, raw in (('before.xml', old), ('account-before.xml', old_account['raw']),
                        ('appearance-before.xml', old_appearance['raw']),
                        ('environment-before.json', (json.dumps(before) + '\n').encode())):
        m.private_write(W / (a.name + '-private-' + suffix), raw)
    P.update(originalPreferencesSha256=sha(old), originalDatabaseRowsSha256=m.canonical(baseline),
             originalAccountPreferenceExists=old_account['present'], originalAccountPreferenceSha256=sha(old_account['raw']),
             originalAppearancePreferenceExists=old_appearance['present'],
             privateAppearanceBackup={'file': a.name + '-private-appearance-before.xml',
                 'sha256': sha(old_appearance['raw']), 'mode': '0600'})
    m.private_write(W / (a.name + '-private-owned-fixture-preferences-before.json'),
        (json.dumps({name: raw.hex() for name, raw in old_fixture_preferences.items()}) + '\n').encode())
    cache_before, receiver_before, gallery_before = listing(m.PKG, CACHE), listing(TEST, RECEIVER), gallery()
    m.private_write(W / (a.name + '-private-output-inventory-before.json'),
                    (json.dumps({'shareCacheFiles': sorted(cache_before), 'receiverFiles': sorted(receiver_before),
                                 'galleryUris': sorted(gallery_before)}) + '\n').encode())
    # Product export retention deletes old own files. Refuse to alter any baseline cache through that side effect.
    for name in cache_before:
        assert re.fullmatch(r'runmoni-card-[0-9a-f-]{36}\.png', name), 'Unexpected baseline export cache entry'
        modified = int(shell('run-as', m.PKG, 'stat', '-c', '%Y', CACHE + '/' + name))
        assert int(m.sh('date', '+%s')) - modified < 6 * 86400, 'Do not age-clean preexisting exports during review'
    fixture = ET.fromstring(old)
    for kind, key, value in [('string', 'communityBackend', 'firebase'), ('string', 'apiUrl', 'http://127.0.0.1:8787'),
                             ('string', 'appEntryChoiceV1', 'guest'), ('boolean', 'communityGuest', 'true'),
                             ('boolean', 'publicLocation', 'false'), ('boolean', 'voiceGuidance', 'false'), ('boolean', 'autoPause', 'false')]:
        m.set_pref(fixture, kind, key, value)
    node = next(n for n in fixture if n.get('name') == 'localProfile')
    profile = json.loads(node.text)
    original_profile = env.profile_from_xml(old)
    profile['name'] = 'ShareRunner26'
    avatar = {key: 0 for key in ('hair', 'top', 'bottom', 'shoes', 'accessory', 'eyewear', 'wrist', 'waist', 'hydration', 'face', 'socks', 'headwear', 'ear', 'costume')}
    avatar.update(top=1, shoes=2, gender='male', runningKitColors={'top': 0, 'bottom': 0, 'shoes': 0})
    profile['avatar'] = avatar
    assert profile['mileage'] == original_profile['mileage'] and profile['owned'] == original_profile['owned']
    node.text = json.dumps(profile, ensure_ascii=False)
    can_restore = True
    m.write_prefs(ET.tostring(fixture, encoding='utf-8', xml_declaration=True))
    env.viewport(m, False)
    for key in ('window_animation_scale', 'transition_animation_scale', 'animator_duration_scale'):
        m.sh('settings', 'put', 'global', key, '0')
    P['motionScaleObservedBeforeMain'] = {key: m.sh('settings', 'get', 'global', key)
        for key in ('window_animation_scale', 'transition_animation_scale', 'animator_duration_scale')}
    assert all(v in ('0', '0.0') for v in P['motionScaleObservedBeforeMain'].values())
    P['motionScaleZeroObservedInActualMain'] = True
    # The existing owned environment snapshot restores this key exactly in finally.
    m.sh('settings', 'put', 'secure', 'show_ime_with_hard_keyboard', '1')
    for permission in ('android.permission.ACCESS_FINE_LOCATION', 'android.permission.ACCESS_COARSE_LOCATION'):
        m.sh('pm', 'revoke', m.PKG, permission)
    stage('prepare-nonce-owned-synthetic-run-and-photo')
    fixture_attempted = True
    instrumentation('prepareOwnedFixture')
    marker = json.loads(private_read(m.PKG, MARKER))
    assert marker['nonce'] == a.nonce and marker['recordId'] == RECORD and marker['photoDisplayName'] == PHOTO and marker['stage'] == 'ready'
    P['fixture'] = {key: marker[key] for key in ('schemaVersion', 'nonce', 'recordId', 'photoSha256', 'photoDisplayName', 'sourceKind', 'rawGpsIncluded', 'fixtureDirectWalletWrites')}
    P['fixture']['distanceMeters'], P['fixture']['activeDurationMillis'] = 5240.0, 1800000
    m.force_stop()
    m.launch()
    m.wait(lambda: bool(app_nodes(m.tree(), '홈') and app_nodes(m.tree(), '나')), 'Guest Main navigation')
    stage('actual-main-native320-font2-profile-keyboard-without-save')
    actual_profile_keyboard_320_font2()
    stage('actual-home-light-and-dark')
    def avatar_pixels_ready():
        root = m.tree()
        retry = app_nodes(root, '미니미 다시 불러오기', True)
        assert not retry, 'Actual Main avatar exposes retry; do not capture it as sprite-ready'
        return not (app_nodes(root, '미니미 준비 중', True) or app_nodes(root, '준비 중'))
    P['originalHistoryExcludedFromExtraScreenshots'] = True
    choose_appearance('light', '밝게')
    if not baseline['runs']:
        m.wait(avatar_pixels_ready, 'Actual light Main home sprite leaves loading state', 120)
        capture('home-light')
        P['homeCaptureContainsOnlyNonceReviewRun'] = True
    else:
        P['homeCaptureOmittedForOriginalHistoryPrivacy'] = True
    choose_appearance('dark', '어둡게')
    if not baseline['runs']:
        m.wait(avatar_pixels_ready, 'Actual dark Main home sprite leaves loading state', 120)
        capture('home-dark')
    choose_appearance('light', '밝게')
    P['actualHomeAppearanceModesObserved'] = ['light', 'dark']
    P['weatherStateWasNotSynthesizedForMainScreenshots'] = True
    stage('actual-new-wardrobe-selection')
    m.tap_tab('나')
    P['profileImeNotRerunInThisMainScope'] = False
    tap_reveal('미니미 꾸미기')
    m.wait(lambda: bool(app_nodes(m.tree(), '옷장')), 'Actual standalone closet visible', 90)
    m.wait(avatar_pixels_ready, 'Placed actual Main closet avatar leaves loading state', 120)
    stage('actual-closet-readiness-gated-pause-resume-observation')
    actual_closet_pause_resume(avatar_pixels_ready)
    root = m.tree()
    assert not (app_nodes(root, '마일리지') or app_nodes(root, '포인트')), 'Do not capture original wallet values'
    for tab_label in ('하의', '상의'):
        fulltap(tab_label)
        m.wait(lambda: selected_tab_observed(tab_label),
               'Actual Main closet selected state observed: ' + tab_label)
    P['actualClosetTabSelectedObserved'] = True
    stage('actual-new-mesh-cap-selection')
    choose_gender('남자')
    reveal_horizontal_closet_tab('모자', '상의', direction='left')
    fulltap('모자')
    m.wait(lambda: selected_tab_observed('모자'), 'Actual headwear tab selected')
    P['actualHeadwearSelections'] = []
    for gender_label, item_label, item_id, suffix in [
            ('남자',NEW_ITEMS[('headwear', 4)],4,'new-cap')]:
        choose_gender(gender_label)
        tap_reveal(item_label)
        def selected_headwear():
            observed = selected_item_observation(item_label)
            current = env.local_profile(m)
            if not observed or not isinstance(current,dict): return False
            avatar=current.get('avatar',{})
            if avatar.get('headwear')!=item_id: return False
            if avatar.get('gender')!=('female' if gender_label=='여자' else 'male'): return False
            return observed
        semantics=m.wait(selected_headwear,'Actual selected and persisted '+gender_label+'/'+item_label,90)
        m.wait(avatar_pixels_ready,'New headwear actual Main bitmap ready',120)
        P['actualHeadwearSelections'].append({'gender':gender_label,'label':item_label,'itemId':item_id,'selectionSemantics':semantics,'persistedAvatarMatched':True})
        capture(suffix)
    P['femaleAndSingleCostumeScenesNotRerunInThisMainScope'] = True
    P['actualNewItemSelections'] = []
    current_tab = '모자'
    for tab, slot, item_id in [('상의','top',7),('하의','bottom',6),('양말','socks',5),('신발','shoes',5)]:
        label = NEW_ITEMS[(slot, item_id)]
        reveal_horizontal_closet_tab(tab, current_tab, direction='left' if tab=='상의' else 'right')
        fulltap(tab)
        m.wait(lambda: selected_tab_observed(tab), 'Actual new material tab '+tab)
        tap_reveal(label)
        def selected_material():
            observed=selected_item_observation(label)
            current=env.local_profile(m)
            return observed if observed and current['avatar'].get(slot)==item_id and current['avatar'].get('costume')==0 else False
        semantics=m.wait(selected_material,'Actual new material selected and persisted '+label,90)
        m.wait(avatar_pixels_ready,'Actual new material bitmap ready',120)
        P['actualNewItemSelections'].append({'label':label,'slot':slot,'itemId':item_id,'selectionSemantics':semantics,'persistedAvatarMatched':True})
        current_tab=tab
    capture('new-technical-set')
    P['technicalSetMeaning'] = 'A combination of four individually selected new garments and new headwear; not a claim that one costume item was selected'
    fulltap('뒤로',True)
    m.wait(lambda: bool(app_nodes(m.tree(),'나')),'Actual profile navigation restored after closet')
    stage('actual-full-photo-share')
    own_detail()
    P['canonicalOwnedRecordResultVerifiedWithoutPublishingOtherHistory'] = True
    fulltap('공유 이미지 만들기',True)
    ready()
    P['defaultPreviewDescription']=preview_description()
    assert '1:1' in P['defaultPreviewDescription']
    pick_own_photo()
    P['photoCleanPreviewDescription']=preview_description()
    tap_reveal('기록')
    tap_reveal('스탬프')
    m.wait(lambda: radio_choice_observed('스탬프'),'Actual stamp style checked')
    ready()
    P['photoStampPreviewDescription']=preview_description()
    capture('sharedphoto-stamp')
    P['actualFullPhotoAndStampStyleSelected']=True
    fulltap('러닝 기록으로 돌아가기',True)
    m.wait(lambda: bool(app_nodes(m.tree(),'러닝 기록')),'Editor back returns to canonical record')
    fulltap('공유 이미지 만들기',True)
    ready()
    tap_reveal('사진')
    m.wait(lambda: bool(app_nodes(m.tree(),'사진 바꾸기')),'Selected photo preserved after Back and explicit photo-tab selection')
    tap_reveal('기록')
    reveal_visible_radio('스탬프')
    m.wait(lambda: radio_choice_observed('스탬프'),'Stamp draft restored after Back')
    P['actualPhotoAndStyleDraftRestoredAfterBack']=True
    fulltap('러닝 기록으로 돌아가기',True)
    fulltap('뒤로',True)
    m.wait(lambda: bool(app_nodes(m.tree(),'나')),'Root navigation restored before map')
    choose_appearance('dark', '어둡게', settings_capture='settings-dark')
    m.tap_tab('탐색')
    m.wait(lambda: bool(app_nodes(m.tree(),'근처 러너') and click_targets(m.tree(),'지도 조작')),'Actual discovery outside action',90)
    root=await_actual_native_map()
    maps=[n for n in root.iter('node') if n.get('package')==m.PKG and n.get('content-desc','').startswith(('탐색용 벡터 지도.','확인된 위치 주변 벡터 지도.'))]
    options=click_targets(root,'지도 조작')
    assert len(maps)==len(options)==1
    P['actualMapBounds'],P['outsideMapActionBounds']=list(m.rect(maps[0])),list(m.rect(options[0]))
    assert P['outsideMapActionBounds'][1]>=P['actualMapBounds'][3]-1
    P.update(mapProviderChanged=False,newProviderAccountOrPaidServiceCreated=False,nativeVectorViewportObserved=True,
        nativeVectorLoadingAbsenceObservedBeforeCapture=True,nativeVectorPixelContentVerifiedByDriver=False,
        nativeVectorBasemapRequiresIndependentPixelReview=True,mapHttpStatusAndTileContentMeasuredByDriver=False)
    assert current_appearance_mode() == 'dark'
    capture('map-dark')
    expected_capture_count=5+(0 if P.get('homeCaptureOmittedForOriginalHistoryPrivacy') else 2)
    assert len(P['captures'])==expected_capture_count and all(row['kind']=='main' and row['syntheticGps'] is False for row in P['captures'])
    P['currentScope']='Actual Main light/dark home through labelled settings taps, new mesh cap and four new separate garments, full-photo stamp share and explicit-tab draft restoration, dark settings and free vector map. Synthetic photo/run clearly labelled; no injected weather, GPS, Google or operating writes. Readiness-gated closet pause/resume and actual320dp/font2 profile keyboard/Back without saving are separate private observations; female/single-costume scenes are not rerun.'
    # A separate real-navigation observation with OS animation enabled. All
    # seven captures above were made with motion disabled. No frame timing,
    # FPS, onset latency or visible interpolation is inferred from settled UI.
    stage('actual-navigation-with-os-motion-enabled')
    selected_profile_before_motion = env.local_profile(m)
    for key in ('window_animation_scale', 'transition_animation_scale', 'animator_duration_scale'):
        m.sh('settings', 'put', 'global', key, '1')
    observed_enabled_motion = {key: m.sh('settings', 'get', 'global', key)
        for key in ('window_animation_scale', 'transition_animation_scale', 'animator_duration_scale')}
    assert all(v in ('1', '1.0') for v in observed_enabled_motion.values())
    m.tap_tab('나')
    m.wait(lambda: len(click_targets(m.tree(), '미니미 꾸미기')) == 1,
           'One settled profile action with OS animations enabled', 90)
    m.tap_tab('탐색')
    m.wait(lambda: bool(app_nodes(m.tree(), '근처 러너')) and
        len(click_targets(m.tree(), '지도 조작')) == 1,
        'One settled discovery action with OS animations enabled', 90)
    assert env.local_profile(m) == selected_profile_before_motion
    assert current_appearance_mode() == 'dark' and m.dump() is None
    P['enabledMotionNavigationObservation'] = {
        'globalScales': observed_enabled_motion, 'actualTabSequence': ['나', '탐색'],
        'settledPageActionWasUnique': True, 'selectedAvatarAndProfilePreserved': True,
        'frameTimingOrFpsMeasured': False, 'interpolationVisuallyVerified': False,
        'countsAsProductInstrumentationMethod': False}
    P['sourceMapsAfter'] = env.source_maps(binary)
    assert m.dump() is None
    P['passed'] = True
    stage('actions-passed-awaiting-exact-restoration')
except BaseException as failure:
    P['passed'] = False
    P['failureType'] = type(failure).__name__
    P['failure'] = str(failure) if isinstance(failure, AssertionError) else 'Inspect retained private diagnostics'
    if can_restore:
        try:
            m.private_write(W / (a.name + '-private-failure.png'), m.adb('exec-out', 'screencap', '-p'))
        except Exception:
            pass
        try:
            m.private_write(W / (a.name + '-private-failure-ui.xml'), ET.tostring(m.tree(), encoding='utf-8', xml_declaration=True))
        except Exception:
            pass
    stage('failed-before-finally')
finally:
    if can_restore:
        try:
            restore_documents_visibility()
        except BaseException as failure:
            P['documentsVisibilityRestoreFailureType'] = type(failure).__name__
            P['passed'] = False
        try:
            dismiss_owned_document_picker()
        except BaseException as failure:
            P['ownedPickerDismissalFailureType'] = type(failure).__name__
            P['passed'] = False
        try:
            m.force_stop()
            shell('am', 'force-stop', TEST)
            # Discover after partial save/share failures, but delete only exact new owned entries.
            if gallery_before is not None:
                own_gallery |= gallery() - gallery_before
            for uri in own_gallery:
                gallery_owned(uri, allow_pending=True)
                m.adb('exec-out', 'su', '0', 'content', 'delete', '--uri', uri)
            if cache_before is not None:
                own_cache |= listing(m.PKG, CACHE) - cache_before
            for name in own_cache:
                assert re.fullmatch(r'runmoni-card-[0-9a-f-]{36}\.png', name)
                shell('run-as', m.PKG, 'rm', CACHE + '/' + name)
            if receiver_before is not None:
                own_receiver |= listing(TEST, RECEIVER) - receiver_before
            for name in own_receiver:
                assert re.fullmatch(r'received-[0-9a-f-]{36}\.(png|json|part)', name)
                shell('run-as', TEST, 'rm', RECEIVER + '/' + name)
            P['ownGalleryOutputsRemoved'] = gallery() == gallery_before
            P['ownShareCacheOutputsRemoved'] = listing(m.PKG, CACHE) == cache_before
            P['ownReceiverOutputsRemoved'] = listing(TEST, RECEIVER) == receiver_before
            m.private_write(W / (a.name + '-private-output-inventory-after.json'),
                            (json.dumps({'shareCacheFiles': sorted(listing(m.PKG, CACHE)),
                                         'receiverFiles': sorted(listing(TEST, RECEIVER)),
                                         'galleryUris': sorted(gallery())}) + '\n').encode())
        except BaseException as failure:
            P['outputCleanupFailureType'] = type(failure).__name__
            P['passed'] = False
        if fixture_attempted:
            try:
                instrumentation('cleanupOwnedFixture')
                P['nonceFixtureCleanupSucceeded'] = True
            except BaseException as failure:
                P['fixtureCleanupFailureType'] = type(failure).__name__
                P['passed'] = False
        try:
            m.force_stop()
            shell('am', 'force-stop', TEST)
            P['databaseExactlyRestored'] = m.canonical(m.snapshot('after-cleanup')) == m.canonical(baseline)
        except BaseException as failure:
            P['databaseObservationFailureType'] = type(failure).__name__
            P['passed'] = False
        # Restore whole XML after fixture cleanup/startup so zero-point ledger IDs also disappear.
        try:
            m.force_stop()
            m.write_prefs(old)
            P['preferencesRestoredExactly'] = m.prefs() == old
            P['profileRestoredExactly'] = env.local_profile(m) == env.profile_from_xml(old)
            env.restore_account(m, old_account)
            current = env.account_snapshot(m)
            P['accountPreferencesRestoredExactly'] = current == old_account
        except BaseException as failure:
            P['preferenceRestoreFailureType'] = type(failure).__name__
            P['passed'] = False
        # Appearance is restored independently even if another XML restoration
        # failed. Original absence is restored as absence, never a saved default.
        try:
            m.force_stop()
            assert old_appearance is not None, 'Actual pre-mutation appearance backup exists'
            P['appearancePreferencesRestoredExactly'] = env.restore_preference_file(
                m, 'runmoni_appearance.xml', old_appearance)
            assert P['appearancePreferencesRestoredExactly'] is True
        except BaseException as failure:
            P['appearancePreferencesRestoredExactly'] = False
            P['appearanceRestoreFailureType'] = type(failure).__name__
            P['passed'] = False
        try:
            m.force_stop()
            assert old_fixture_preferences is not None
            P['newOwnedFixturePreferenceFilesRemoved'] = env.remove_new_owned_fixtures(m, old_fixture_preferences)
            P['ownedFixturePreferencesRestoredExactly'] = env.owned_fixture_preferences(m) == old_fixture_preferences
            assert P['ownedFixturePreferencesRestoredExactly'] is True
        except BaseException as failure:
            P['ownedFixturePreferencesRestoredExactly'] = False
            P['ownedFixturePreferenceRestoreFailureType'] = type(failure).__name__
            P['passed'] = False
        try:
            equal, observed, failures = env.restore_environment(m, before)
            P['controlledEnvironmentExactlyRestored'], P['restorationCommandFailures'] = equal, failures
            m.private_write(W / (a.name + '-private-environment-after.json'), (json.dumps(observed) + '\n').encode())
        except BaseException as failure:
            P['environmentRestoreFailureType'] = type(failure).__name__
            P['passed'] = False
        try:
            m.force_stop()
            P['allOwnedPreferencesStillExactAfterEnvironmentRestoration'] = (
                env.preference_file_snapshot(m, 'runmoni.xml') == {'present': True, 'raw': old} and
                env.account_snapshot(m) == old_account and
                env.preference_file_snapshot(m, 'runmoni_appearance.xml') == old_appearance and
                env.owned_fixture_preferences(m) == old_fixture_preferences)
            assert P['allOwnedPreferencesStillExactAfterEnvironmentRestoration'] is True
        except BaseException as failure:
            P['allOwnedPreferencesStillExactAfterEnvironmentRestoration'] = False
            P['finalPreferenceObservationFailureType'] = type(failure).__name__
            P['passed'] = False
    if binary is not None:
        try:
            P['sourceMapsAfterRestoration'] = env.source_maps(binary)
            P['binaryEvidenceUnchanged'] = sha((E / a.binary).read_bytes()) == P['binaryReferenceSha256']
        except BaseException as failure:
            P['postRestorationInputObservationFailureType'] = type(failure).__name__
            P['passed'] = False
    try:
        current_scope, current_scope_sha = validate_scope(a.binary, env)
        P['formalScopeEvidenceUnchanged'] = current_scope == _scope and current_scope_sha == _scope_sha
    except BaseException as failure:
        P['formalScopeEvidenceUnchanged'] = False
        P['formalScopeEvidenceObservationFailureType'] = type(failure).__name__
        P['passed'] = False
    P['driverSourceUnchanged'] = sha(Path(__file__).read_bytes()) == P['executedDriverSha256']
    P['observerHelperSourceUnchanged'] = sha(hpath.read_bytes()) == P['observerHelperSha256']
    P['environmentHelperSourceUnchanged'] = sha(envpath.read_bytes()) == P['environmentHelperSha256']
    P['scopeValidatorSourceUnchanged'] = sha(_SCOPE_VALIDATOR_PATH.read_bytes()) == P['scopeValidatorSha256']
    P['itemPlanUnchanged'] = sha(ITEM_PLAN.read_bytes()) == P['itemPlanSha256']
    try:
        P['installationEvidenceUnchanged'] = sha(INSTALL_PATH.read_bytes()) == P.get('installationReferenceSha256')
    except BaseException as failure:
        P['installationEvidenceUnchanged'] = False
        P['installationEvidenceObservationFailureType'] = type(failure).__name__
        P['passed'] = False
    required_restoration = ('nonceFixtureCleanupSucceeded', 'ownGalleryOutputsRemoved', 'ownShareCacheOutputsRemoved',
                            'ownReceiverOutputsRemoved', 'databaseExactlyRestored', 'preferencesRestoredExactly',
                            'profileRestoredExactly', 'accountPreferencesRestoredExactly', 'appearancePreferencesRestoredExactly',
                            'ownedFixturePreferencesRestoredExactly', 'allOwnedPreferencesStillExactAfterEnvironmentRestoration',
                            'controlledEnvironmentExactlyRestored',
                            'binaryEvidenceUnchanged', 'driverSourceUnchanged', 'observerHelperSourceUnchanged',
                            'environmentHelperSourceUnchanged', 'scopeValidatorSourceUnchanged', 'formalScopeEvidenceUnchanged', 'itemPlanUnchanged',
                            'installationEvidenceUnchanged')
    P['passed'] = bool(P['passed'] and all(P.get(key) is True for key in required_restoration))
    P['completed'], P['finishedAtUtc'] = True, env.now()
    stage('complete-passed' if P['passed'] else 'complete-failed')
raise SystemExit(0 if P['passed'] else 1)
