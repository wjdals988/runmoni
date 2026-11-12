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
import json
import os
import re
import shlex
import time
import xml.etree.ElementTree as ET

R = Path('/workspace/runmoni')
W = R / 'work/ui-studio'
E = R / 'docs/app-review/2026-11-11-studio-ui'
os.umask(0o077)
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--name', required=True)
parser.add_argument('--nonce', required=True)
parser.add_argument('--binary', default='binary-build3.json')
a = parser.parse_args()
assert re.fullmatch(r'live[1-9]\d*', a.name)
assert re.fullmatch(r'[a-z0-9]{8,32}', a.nonce)
assert re.fullmatch(r'binary-build[1-9]\d*\.json', a.binary)


def module(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


hpath = R / 'work/running-experience/actual-ui-helper6.py'
envpath = W / 'android-review3.py'
m = module('actual_run_share_main_observer', hpath)
env = module('run_share_review_environment', envpath)
m.W, m.E, m.NAME, m.VERSION, m.CODE = W, E, a.name, '0.4.3-dev', 25
sha = env.sha
out = E / (a.name + '.json')
assert not out.exists()
TEST = m.PKG + '.test'
PHOTO = 'RunmoniReview-' + a.nonce + '.png'
RECORD = 'runmoni-share-review-' + a.nonce
MARKER = 'cache/run-share-review/main-fixture-' + a.nonce + '.json'
CACHE = 'cache/exports/run-shares'
RECEIVER = 'cache/run-share-receiver'
P = {'name': a.name, 'nonce': a.nonce, 'version': '0.4.3-dev', 'versionCode': 25,
     'api': 29, 'avd': 'Runmoni_Cloud_29', 'acceleration': 'TCG',
     'binaryReference': a.binary, 'sourceKind': 'actual Android MainActivity',
     'passed': False, 'completed': False, 'captures': [], 'actualTaps': [],
     'startedAtUtc': env.now(), 'systemUiWaitActions': [], 'accessibilityRetries': 0,
     'syntheticGps': False, 'GPSInjectedByThisDriver': False,
     'originalGpsFixRestoredByThisDriver': False, 'fixtureTrackPointCount': 0,
     'syntheticCompletedRun': True, 'syntheticSourcePhoto': True,
     'productionWritesVerified': False, 'newAuthenticationRequestedByDriver': False,
     'physicalDeviceVerified': False, 'googleLoginVerified': False,
     'signedReleaseRuntimeVerified': False, 'android31RuntimeVerified': False,
     'socialAppDeliveryVerified': False, 'appTotalNetworkTrafficMeasured': False,
     'rootPrivacyReviewRequiredBeforePublication': True,
     'requiredIndependentRootPixelReview': True}
m.P = P
can_restore = fixture_attempted = False
old = before = baseline = binary = old_account = None
cache_before = receiver_before = gallery_before = None
marker = None
own_gallery = set()
own_cache = set()
own_receiver = set()
for filename, raw in ((a.name + '-executed-driver.py', Path(__file__).read_bytes()),
                      (a.name + '-actual-ui-helper.py', hpath.read_bytes()),
                      (a.name + '-environment-helper.py', envpath.read_bytes())):
    env.exclusive_bytes(E / filename, raw)
P.update(executedDriver=a.name + '-executed-driver.py', executedDriverSha256=sha(Path(__file__).read_bytes()),
         observerHelper=a.name + '-actual-ui-helper.py', observerHelperSha256=sha(hpath.read_bytes()),
         environmentHelper=a.name + '-environment-helper.py', environmentHelperSha256=sha(envpath.read_bytes()))


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
    directions = (False, True) if label in ('배경', '기록', '미니미', '사진 추가', '사진 바꾸기') else (True, False)
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


def caption_field_echoes(expected):
    root = m.tree()
    return any(node.get('package') == m.PKG and node.get('class') == 'android.widget.EditText'
               and node.get('focused') == 'true' and node.get('text') == expected for node in root.iter('node'))


def ready():
    def settled():
        root = m.tree()
        return bool(app_nodes(root, '기록 공유') and len(click_targets(root, '이미지 저장')) == 1 and len(click_targets(root, '공유하기')) == 1)
    return m.wait(settled, 'Actual export controls enabled after image render', 120)


def capture(suffix):
    m.save_png(a.name + '-main-' + suffix + '.png', 'main')


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
    before = env.environment_snapshot(m)
    old = m.prefs()
    ET.fromstring(old)
    baseline = m.snapshot('before')
    assert all(row['finishedAtMillis'] > 0 for row in baseline['runs'])
    assert not any(row['id'] == RECORD for row in baseline['runs'])
    for suffix, raw in (('before.xml', old), ('account-before.xml', old_account['raw']),
                        ('environment-before.json', (json.dumps(before) + '\n').encode())):
        m.private_write(W / (a.name + '-private-' + suffix), raw)
    P.update(originalPreferencesSha256=sha(old), originalDatabaseRowsSha256=m.canonical(baseline),
             originalAccountPreferenceExists=old_account['present'], originalAccountPreferenceSha256=sha(old_account['raw']))
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
    profile['name'] = 'ShareRunner25'
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
    stage('actual-studio-home-and-closet')
    def avatar_pixels_ready():
        root = m.tree()
        retry = app_nodes(root, '미니미 다시 불러오기', True)
        assert not retry, 'Actual Main avatar exposes retry; do not capture it as sprite-ready'
        return not (app_nodes(root, '미니미 준비 중', True) or app_nodes(root, '준비 중'))
    P['originalHistoryExcludedFromExtraScreenshots'] = True
    if not baseline['runs']:
        m.wait(avatar_pixels_ready, 'Placed actual Main home avatar leaves loading state', 120)
        capture('studio-home')
        P['homeCaptureContainsOnlyNonceReviewRun'] = True
    else:
        P['homeCaptureOmittedForOriginalHistoryPrivacy'] = True
    m.tap_tab('나')
    tap_reveal('미니미 꾸미기')
    m.wait(lambda: bool(app_nodes(m.tree(), '옷장')), 'Actual standalone closet visible', 90)
    m.wait(avatar_pixels_ready, 'Placed actual Main closet avatar leaves loading state', 120)
    root = m.tree()
    assert not (app_nodes(root, '마일리지') or app_nodes(root, '포인트')), 'Do not capture original wallet values'
    capture('studio-closet')
    fulltap('닫기')
    m.wait(lambda: bool(app_nodes(m.tree(), '나')), 'Actual profile navigation restored after closet')
    expected_capture_count = 4 + (1 if P.get('homeCaptureOmittedForOriginalHistoryPrivacy') else 2)
    stage('actual-result-and-default-card')
    own_detail()
    fulltap('공유 이미지 만들기', True)
    ready()
    P['defaultPreviewDescription'] = preview_description()
    assert '1:1' in P['defaultPreviewDescription'], 'Actual new editor uses the square default'
    capture('default-card')
    stage('actual-photo-picker-ratio-theme-caption-and-avatar')
    tap_reveal('9:16')
    tap_reveal('검정')
    reveal('사진 추가')
    pick_own_photo()
    P['photoStoryPreviewDescription'] = preview_description()
    capture('photo-story')
    tap_reveal('기록')
    tap_reveal('상단')
    reveal('한 줄 기록')
    root = m.tree()
    fields = [n for n in root.iter('node') if n.get('package') == m.PKG and n.get('class') == 'android.widget.EditText']
    assert len(fields) == 1
    m.tap_node(fields[0], root)
    m.wait(lambda: bool(re.search(r'\bmInputShown=true\b|\bmIsInputViewShown=true\b', m.sh('dumpsys', 'input_method'))),
           'Actual Main caption IME is shown', 30)
    P['actualCaptionKeyboardObserved'] = True
    root = m.tree()
    back = click_targets(root, '러닝 기록으로 돌아가기', True)
    assert len(back) == 1, 'Actual Main Back remains visible while the caption IME is shown'
    bounds = list(m.rect(back[0]))
    windows = m.sh('dumpsys', 'window', 'windows')
    m.private_write(W / (a.name + '-private-main-caption-ime-windows.txt'), windows.encode())
    blocks = re.split(r'(?m)^  Window #\d+ ', windows)
    main_blocks = [block for block in blocks if re.search(
        r'^Window\{[^\n}]+ com\.runmoni\.app/com\.runmoni\.app\.MainActivity\}:', block)]
    assert len(main_blocks) == 1, 'Exactly one actual Main window supplies IME-safe visible bounds'
    frame = re.search(r'\bvisible=\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]', main_blocks[0])
    assert frame, 'Actual Main WindowManager visible frame must be observed'
    visible = list(map(int, frame.groups()))
    density_text = m.sh('wm', 'density')
    density = re.search(r'Override density:\s*(\d+)', density_text) or re.search(r'Physical density:\s*(\d+)', density_text)
    assert density, 'Actual physical or overridden density must be observed'
    minimum = 48 * int(density.group(1)) / 160
    assert bounds[2] - bounds[0] + 1 >= minimum and bounds[3] - bounds[1] + 1 >= minimum
    width, height = m.dimensions()
    assert 0 <= visible[0] < visible[2] <= width and 0 <= visible[1] < visible[3] < height
    assert visible[0] <= bounds[0] < bounds[2] <= visible[2] and visible[1] <= bounds[1] < bounds[3] <= visible[3], \
        'Actual Main Back stays entirely outside status/navigation bars and IME'
    P['actualCaptionKeyboardBackBounds'] = bounds
    P['actualCaptionKeyboardWindowVisibleFrame'] = visible
    P['actualCaptionKeyboardBackFullyOnDisplay'] = True
    P['actualCaptionKeyboardBackInsideVisibleWindow'] = True
    m.private_write(W / (a.name + '-private-main-caption-ime.png'), m.adb('exec-out', 'screencap', '-p'))
    m.sh('input', 'text', 'Morning%sMiles')
    m.wait(lambda: caption_field_echoes('Morning Miles'), 'Real shell keys retained in the focused Main caption while IME is shown', 40)
    P['actualCaptionRealShellKeysObservedBeforeImeDismissal'] = True
    m.private_write(W / (a.name + '-private-main-caption-ime-typed.png'), m.adb('exec-out', 'screencap', '-p'))
    m.sh('input', 'keyevent', '4')
    m.wait(lambda: 'Morning Miles' in preview_description(), 'Actual caption present in preview semantics')
    tap_reveal('미니미')
    tap_reveal('왼쪽')
    tap_reveal('미니미 위로 이동', True)
    ready()
    P['editedPreviewDescription'] = preview_description()
    P['actualMiniMiMovementActionObserved'] = True
    capture('mini-editor')
    stage('save-gallery-and-real-separate-uid-chooser-recipient')
    fulltap('이미지 저장')
    P['galleryAcknowledgmentScrollActions'] = 0
    for _ in range(10):
        if app_nodes(m.tree(), '사진 앱에 저장했어요.'):
            break
        scroll_controls(False)
        P['galleryAcknowledgmentScrollActions'] += 1
    else:
        raise AssertionError('Actual gallery-save acknowledgment must be visible after bounded upward scroll')
    P['actualGallerySaveAcknowledgmentObserved'] = True
    own_gallery = gallery() - gallery_before
    assert len(own_gallery) == 1
    uri = next(iter(own_gallery))
    m.private_write(W / (a.name + '-private-own-gallery-uri.txt'), (uri + '\n').encode())
    display_name = gallery_owned(uri)
    gallery_png = m.adb('exec-out', 'su', '0', 'content', 'read', '--uri', uri)
    assert m.dimensions(gallery_png) == (1080, 1920)
    env.exclusive_bytes(E / (a.name + '-gallery-export.png'), gallery_png)
    P['galleryExport'] = {'file': a.name + '-gallery-export.png', 'displayName': display_name,
                           'sha256': sha(gallery_png), 'width': 1080, 'height': 1920, 'sourceKind': 'actual saved MediaStore PNG, not a screenshot'}
    before_send = listing(TEST, RECEIVER)
    assert before_send == receiver_before
    fulltap('공유하기')
    m.wait(lambda: bool(m.nodes(m.tree(), 'Runmoni 공유 검토')), 'Actual Android chooser offers test receiver', 60)
    fulltap('Runmoni 공유 검토', system=True)
    m.wait(lambda: bool(m.nodes(m.tree(), 'runmoni-share-receiver-complete', True)), 'Actual separate UID receiver read completed', 90)
    own_receiver = listing(TEST, RECEIVER) - receiver_before
    receipts = [name for name in own_receiver if re.fullmatch(r'received-[0-9a-f-]{36}\.json', name)]
    assert len(receipts) == 1
    receipt = json.loads(private_read(TEST, RECEIVER + '/' + receipts[0]))
    m.private_write(W / (a.name + '-private-receiver-receipt.json'), (json.dumps(receipt) + '\n').encode())
    required = ('passed', 'differentLinuxUid', 'clipDataMatchesSingleStream', 'readPermissionGranted',
                'writePermissionDenied', 'siblingReadPermissionDenied', 'siblingOpenDeniedBySecurityException',
                'prefixAndPersistableGrantAbsent', 'sourceUriCanonical', 'actualReadSucceeded')
    assert all(receipt.get(key) is True for key in required)
    assert receipt['receiverLinuxUid'] != receipt['targetLinuxUid'] and receipt['receiverPackage'] == TEST
    png_name = receipt['pngFile']
    assert re.fullmatch(r'received-[0-9a-f-]{36}\.png', png_name)
    assert own_receiver == {receipts[0], png_name}
    received_png = private_read(TEST, RECEIVER + '/' + png_name)
    assert sha(received_png) == receipt['sha256'] == sha(gallery_png)
    own_cache = listing(m.PKG, CACHE) - cache_before
    assert len(own_cache) == 1
    cache_name = next(iter(own_cache))
    assert re.fullmatch(r'runmoni-card-[0-9a-f-]{36}\.png', cache_name)
    assert sha(private_read(m.PKG, CACHE + '/' + cache_name)) == sha(received_png)
    assert receipt['sourceUri'] == 'content://com.runmoni.app.files/exports/run-shares/' + cache_name
    env.exclusive_bytes(E / (a.name + '-receiver-export.png'), received_png)
    P['receiver'] = {key: value for key, value in receipt.items() if key != 'sourceUri'}
    P['receiver'].update(file=a.name + '-receiver-export.png', sourceKind='actual READ-granted separate-UID receiver PNG, not a screenshot')
    P['galleryCacheReceiverPngBytesExactlyEqual'] = True
    P['shareCacheOwnFilename'] = cache_name
    stage('return-back-reopen-observation')
    m.sh('input', 'keyevent', '4')
    m.wait(lambda: m.focus('main'), 'Back from test receiver returns to actual Main')
    ready()
    P['previewAfterChooserReturn'] = preview_description()
    fulltap('러닝 기록으로 돌아가기', True)
    m.wait(lambda: bool(app_nodes(m.tree(), '러닝 기록')), 'Editor Back returns to canonical result')
    P['resultReturnedAfterEditorBack'] = True
    fulltap('공유 이미지 만들기', True)
    ready()
    P['previewAfterActualReopen'] = preview_description()
    P['draftReopenObservationMeaning'] = 'Actual accessibility description after Back and reopening; retention is not inferred from taps'
    assert P['previewAfterActualReopen'] == P['editedPreviewDescription']
    fulltap('러닝 기록으로 돌아가기', True)
    m.wait(lambda: bool(app_nodes(m.tree(), '러닝 기록')), 'Back to record before map review')
    fulltap('뒤로', True)
    m.wait(lambda: bool(app_nodes(m.tree(), '나')), 'Root navigation restored before map review')
    m.tap_tab('탐색')
    m.wait(lambda: bool(app_nodes(m.tree(), '근처 러너') and click_targets(m.tree(), '지도 조작')), 'Actual clean discovery map and outside action bar', 90)
    root = m.tree()
    maps = [n for n in root.iter('node') if n.get('package') == m.PKG and n.get('content-desc', '').startswith(('지도.', '세계 지도.'))]
    options = click_targets(root, '지도 조작')
    assert len(maps) == len(options) == 1
    map_bounds, action_bounds = list(m.rect(maps[0])), list(m.rect(options[0]))
    assert action_bounds[1] >= map_bounds[3] - 1, 'Map action stays outside map pixels'
    P['actualMapBounds'], P['outsideMapActionBounds'] = map_bounds, action_bounds
    P['mapProviderChangedOrNewProviderAuthenticated'] = False
    P['mapHttpStatusAndTileContentMeasuredByDriver'] = False
    capture('map-clean')
    assert len(P['captures']) == expected_capture_count and all(row['kind'] == 'main' and row['syntheticGps'] is False for row in P['captures'])
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
        try:
            equal, observed, failures = env.restore_environment(m, before)
            P['controlledEnvironmentExactlyRestored'], P['restorationCommandFailures'] = equal, failures
            m.private_write(W / (a.name + '-private-environment-after.json'), (json.dumps(observed) + '\n').encode())
        except BaseException as failure:
            P['environmentRestoreFailureType'] = type(failure).__name__
            P['passed'] = False
    if binary is not None:
        try:
            P['sourceMapsAfterRestoration'] = env.source_maps(binary)
            P['binaryEvidenceUnchanged'] = sha((E / a.binary).read_bytes()) == P['binaryReferenceSha256']
        except BaseException as failure:
            P['postRestorationInputObservationFailureType'] = type(failure).__name__
            P['passed'] = False
    P['driverSourceUnchanged'] = sha(Path(__file__).read_bytes()) == P['executedDriverSha256']
    P['observerHelperSourceUnchanged'] = sha(hpath.read_bytes()) == P['observerHelperSha256']
    P['environmentHelperSourceUnchanged'] = sha(envpath.read_bytes()) == P['environmentHelperSha256']
    required_restoration = ('nonceFixtureCleanupSucceeded', 'ownGalleryOutputsRemoved', 'ownShareCacheOutputsRemoved',
                            'ownReceiverOutputsRemoved', 'databaseExactlyRestored', 'preferencesRestoredExactly',
                            'profileRestoredExactly', 'accountPreferencesRestoredExactly', 'controlledEnvironmentExactlyRestored',
                            'binaryEvidenceUnchanged', 'driverSourceUnchanged', 'observerHelperSourceUnchanged', 'environmentHelperSourceUnchanged')
    P['passed'] = bool(P['passed'] and all(P.get(key) is True for key in required_restoration))
    P['completed'], P['finishedAtUtc'] = True, env.now()
    stage('complete-passed' if P['passed'] else 'complete-failed')
raise SystemExit(0 if P['passed'] else 1)
