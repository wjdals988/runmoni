#!/usr/bin/env python3
"""Root-run only: actual MainActivity/SystemUI + synthetic GPS on the owned API29 AVD.

No installation, authentication, DB reset or direct DB edits. SQLite reads only
private copies made while the app is force-stopped. Failed cleanup is reported;
the original database is never copied back to conceal a remaining review run.
"""
import datetime
import hashlib
import json
import os
import re
import sqlite3
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path
from zoneinfo import ZoneInfo

R = next(p for p in Path(__file__).resolve().parents if (p / 'app/build.gradle.kts').exists())
W = R / 'work/background-running'
E = R / 'docs/app-review/2026-11-06-background-running'
A = [str(R / 'work/cloud-android/sdk/platform-tools/adb'), '-s', 'emulator-5556']
PKG, MAIN, SERVICE = 'com.runmoni.app', 'com.runmoni.app/.MainActivity', 'com.runmoni.app/.tracking.RunTrackingService'
NAME, VERSION, CODE = 'host5', '0.3.7-dev', 19
OUT = E / (NAME + '.json')
P = {'version': VERSION, 'versionCode': CODE, 'avd': 'Runmoni_Cloud_29', 'serial': 'emulator-5556',
     'api': 29, 'acceleration': 'TCG', 'binaryReference': 'binary-build2.json', 'passed': False,
     'startedAtUtc': datetime.datetime.now(datetime.timezone.utc).isoformat(), 'captures': [], 'checks': [],
     'systemUiWaitActions': [], 'accessibilityRetries': 0, 'physicalDeviceVerified': False,
     'outdoorGpsVerified': False, 'dozeVerified': False, 'oemBatteryPolicyVerified': False,
     'android31RuntimeVerified': False, 'googleLoginVerified': False, 'productionWritesVerified': False}
snapshot_counter, creation_windows = 0, []
baseline, original_settings, restore_xml = None, None, None
geo_step = 0
device_zone = None


def guard(value, label):
    if not value:
        raise AssertionError(label)


def adb(*args, data=None, timeout=45, allow_failure=False):
    result = subprocess.run(A + list(args), input=data, capture_output=True, timeout=timeout)
    if not allow_failure and result.returncode:
        raise RuntimeError('ADB operation failed: ' + args[0])
    return result.stdout


def sh(*args, **kwargs):
    return adb('shell', *args, **kwargs).decode('utf-8', 'replace').strip()


def private_write(path, data):
    guard(not path.exists(), 'Private evidence path must be fresh')
    with path.open('xb') as output:
        output.write(data)
    path.chmod(0o600)


def force_stop():
    sh('am', 'force-stop', PKG)
    wait(lambda: not pid(), 'App is force-stopped', 15)


def pid():
    return sh('pidof', PKG, allow_failure=True)


def wait(fn, label, seconds=75):
    end = time.monotonic() + seconds
    while time.monotonic() < end:
        value = fn()
        if value is not None and value is not False:
            return value
        time.sleep(.35)
    raise AssertionError(label)


def prefs():
    return adb('exec-out', 'run-as', PKG, 'cat', 'shared_prefs/runmoni.xml')


def write_prefs(raw):
    ET.fromstring(raw)
    names = adb('exec-out', 'run-as', PKG, 'ls', '-1', 'shared_prefs').decode().splitlines()
    guard('runmoni.xml.bak' not in names, 'No pending preference backup')
    adb('shell', '-T', 'run-as', PKG, 'dd', 'of=shared_prefs/runmoni.xml', data=raw)
    sh('sync')
    guard(prefs() == raw, 'Exact whole-XML preference write')


def set_pref(root, kind, key, value):
    old = next((n for n in root if n.get('name') == key), None)
    if old is not None:
        root.remove(old)
    node = ET.SubElement(root, kind, {'name': key})
    if kind == 'boolean':
        node.set('value', value)
    else:
        node.text = value


def permission_state():
    package = sh('dumpsys', 'package', PKG)
    result = {}
    for permission in ['android.permission.ACCESS_COARSE_LOCATION', 'android.permission.ACCESS_FINE_LOCATION']:
        matches = re.findall(re.escape(permission) + r': granted=(true|false), flags=\[([^\]]*)\]', package)
        guard(len(matches) == 1, 'One current-user runtime permission state')
        result[permission] = {'granted': matches[0][0] == 'true', 'flags': matches[0][1].strip()}
    return result


def settings_snapshot():
    return {'font': sh('settings', 'get', 'system', 'font_scale'), 'density': sh('wm', 'density'),
            'animations': {k: sh('settings', 'get', 'global', k) for k in
                           ['window_animation_scale', 'transition_animation_scale', 'animator_duration_scale']},
            'locationMode': sh('settings', 'get', 'secure', 'location_mode'), 'permissions': permission_state(),
            'screenAsleep': asleep()}


def restore_setting(namespace, key, value):
    sh('settings', 'delete', namespace, key) if value == 'null' else sh('settings', 'put', namespace, key, value)


def restore_viewport(settings):
    override = re.search(r'Override density:\s*(\d+)', settings['density'])
    sh('wm', 'density', override.group(1) if override else 'reset')
    restore_setting('system', 'font_scale', settings['font'])


def restore_settings(settings):
    failures = []
    def attempt(label, action):
        try:
            action()
        except Exception as failure:
            failures.append({'setting': label, 'failureType': type(failure).__name__})
    override = re.search(r'Override density:\s*(\d+)', settings['density'])
    attempt('density', lambda: sh('wm', 'density', override.group(1) if override else 'reset'))
    attempt('fontScale', lambda: restore_setting('system', 'font_scale', settings['font']))
    for key, value in settings['animations'].items():
        attempt(key, lambda key=key, value=value: restore_setting('global', key, value))
    attempt('locationMode', lambda: restore_setting('secure', 'location_mode', settings['locationMode']))
    for permission, state in reversed(list(settings['permissions'].items())):
        attempt(permission, lambda permission=permission, state=state: sh('pm', 'grant' if state['granted'] else 'revoke', PKG, permission))
    attempt('screenPower', lambda: sh('input', 'keyevent', '223' if settings['screenAsleep'] else '224'))
    if failures:
        P['settingsRestoreCommandFailures'] = failures
    after = settings_snapshot()
    guard(after == settings, 'Viewport, animations, location mode, permission grants/flags and power state restored')


def asleep():
    return re.search(r'mWakefulness=Asleep\b', sh('dumpsys', 'power')) is not None


def dimensions(raw=None):
    raw = raw or adb('exec-out', 'screencap', '-p')
    guard(raw.startswith(b'\x89PNG\r\n\x1a\n') and len(raw) < 3_000_000, 'Bounded actual Android PNG')
    return int.from_bytes(raw[16:20], 'big'), int.from_bytes(raw[20:24], 'big')


def rect(node):
    match = re.fullmatch(r'\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]', node.get('bounds', ''))
    guard(match is not None, 'Actual accessibility bounds')
    return tuple(map(int, match.groups()))


def tree():
    for attempt in range(7):
        try:
            path = '/data/local/tmp/runmoni-background-host.xml'
            actual_path = '/data/local/tmp/local/tmp/runmoni-background-host.xml'
            sh('mkdir', '-p', '/data/local/tmp/local/tmp')
            sh('rm', '-f', path, actual_path)
            # The platform launcher sets ANDROID_DATA=/data/local/tmp. Both paths are fresh.
            sh('ln', '-s', actual_path, path)
            result = sh('uiautomator', 'runtest', '/data/local/tmp/runmoni-live-dump.jar', '-c', 'com.runmoni.review.LiveDump#testDumpLiveHierarchy', '-e', 'disable_ime', 'false', timeout=30)
            guard('dumped to:' in result.lower() and 'FAILURES!!!' not in result and re.search(r'OK \(1 tests?\)', result) is not None, 'Fresh live accessibility observer dump')
            P['observerDumps'] = P.get('observerDumps', 0) + 1
            root = ET.fromstring(adb('exec-out', 'cat', path))
            guard(bool(list(root)), 'Nonempty accessibility root')
            if any(re.fullmatch("System UI (?:isn't|isn’t) responding", n.get('text', '')) for n in root.iter('node')):
                targets = [n for n in root.iter('node') if n.get('text') == 'Wait']
                guard(len(targets) == 1, 'Only the exact System UI Wait action is allowed')
                name = NAME + '-system-ui-wait-' + str(len(P['systemUiWaitActions']) + 1) + '.png'
                save_png(name, 'system-ui-anr', focus_required=False)
                tap_node(targets[0], root, climb=False)
                P['systemUiWaitActions'].append({'action': 'Wait', 'dialog': 'System UI not responding', 'file': name})
                continue
            return root
        except (AssertionError, ET.ParseError, RuntimeError, subprocess.TimeoutExpired):
            P['accessibilityRetries'] += 1
            if attempt == 6:
                raise
            time.sleep(.5)
    raise AssertionError('Fresh accessibility tree unavailable')


def nodes(root, label, desc=False):
    key = 'content-desc' if desc else 'text'
    return [n for n in root.iter('node') if n.get(key) == label and rect(n)[2] > rect(n)[0] and rect(n)[3] > rect(n)[1]]


def tap_node(node, root, climb=True):
    parents = {child: parent for parent in root.iter() for child in parent}
    target = node
    if climb:
        while target.get('clickable') != 'true' and target in parents:
            target = parents[target]
        if target.tag != 'node':
            target = node
    left, top, right, bottom = rect(target)
    width, height = dimensions()
    guard(0 <= left < right <= width and 0 <= top < bottom <= height, 'Tap target fully on the actual display')
    guard(node.get('enabled', 'true') == 'true', 'Target enabled')
    sh('input', 'tap', str((left + right) // 2), str((top + bottom) // 2))
    time.sleep(.3)


def tap(label, desc=False, system=False):
    def find():
        root = tree()
        hits = nodes(root, label, desc)
        if system:
            hits = [n for n in hits if n.get('package') == 'com.android.systemui']
        return (root, hits) if hits else None
    root, hits = wait(find, 'Visible exact action: ' + label)
    guard(len(hits) == 1, 'Unambiguous exact action: ' + label)
    if system:
        P.setdefault('systemActionTaps', []).append({'label': label, 'nodeBounds': list(rect(hits[0])), 'clickable': hits[0].get('clickable')})
        print(json.dumps({'systemAction':label,'nodeBounds':rect(hits[0]),'clickable':hits[0].get('clickable')}),flush=True)
    tap_node(hits[0], root)


def tap_tab(label):
    root = tree()
    _, height = dimensions()
    hits = [n for n in nodes(root, label) if rect(n)[1] > height * .7]
    guard(len(hits) == 1, 'One actual bottom navigation label: ' + label)
    tap_node(hits[0], root)


def reveal(label):
    for _ in range(10):
        if nodes(tree(), label):
            return
        width, height = dimensions()
        sh('input', 'swipe', str(width // 2), str(height * 7 // 10), str(width // 2), str(height * 35 // 100), '350')
    raise AssertionError('Action not reachable by scrolling: ' + label)


def focus(kind):
    window = sh('dumpsys', 'window', 'displays')
    current = next((line for line in window.splitlines() if 'mCurrentFocus=' in line), '')
    if kind == 'main':
        return 'com.runmoni.app/com.runmoni.app.MainActivity' in current
    return 'com.android.systemui' in current or 'StatusBar' in current


def save_png(name, kind, focus_required=True):
    if focus_required:
        guard(focus(kind), 'Expected real foreground window for capture')
        if kind == 'main':
            activities = sh('dumpsys', 'activity', 'activities')
            guard(any('mResumedActivity' in line and MAIN in line for line in activities.splitlines()), 'Actual MainActivity resumed')
    raw = adb('exec-out', 'screencap', '-p')
    width, height = dimensions(raw)
    target = E / name
    guard(not target.exists(), 'Capture evidence is immutable')
    target.write_bytes(raw)
    print(json.dumps({'capture':name,'kind':kind}),flush=True)
    P['captures'].append({'file': name, 'sha256': hashlib.sha256(raw).hexdigest(), 'bytes': len(raw),
                          'width': width, 'height': height, 'kind': kind,
                          'sourceKind': {'main': 'actual Android MainActivity', 'system': 'actual Android SystemUI',
                                         'system-ui-anr': 'actual SystemUI ANR dialog matched by UIAutomator'}.get(kind, 'raw Android screenshot; foreground kind unverified'),
                          'expectedWindowFocused': focus_required, 'syntheticGps': True})


def capture(suffix, kind, anchor):
    wait(lambda: bool(nodes(tree(), anchor)), 'Expected capture page: ' + anchor)
    save_png(NAME + '-' + suffix + '.png', kind)


def dump():
    raw = sh('dumpsys', 'activity', 'service', SERVICE)
    first = re.search(r'Runmoni recorder phase=(\w+) foreground=(true|false) locationSubscribed=(true|false) wakeLockHeld=(true|false)', raw)
    if first is None:
        return None
    second = re.search(r'distanceMeters=([\d.Ee+-]+) activeDurationMillis=(\d+) pointCount=(\d+) speedMetersPerSecond=(null|[\d.Ee+-]+)', raw)
    guard(second is not None, 'Both coordinate-free service dump lines present')
    return {'phase': first[1], 'foreground': first[2] == 'true', 'locationSubscribed': first[3] == 'true',
            'wakeLockHeld': first[4] == 'true', 'distanceMeters': float(second[1]), 'activeDurationMillis': int(second[2]),
            'pointCount': int(second[3]), 'speedMetersPerSecond': None if second[4] == 'null' else float(second[4])}


def phase(expected):
    return wait(lambda: value if (value := dump()) and value['phase'] == expected else None, 'Recorder phase ' + expected)


def running_settled():
    return wait(lambda: value if (value := dump()) and value['phase'] == 'RUNNING' and value['foreground'] and
                value['locationSubscribed'] and value['wakeLockHeld'] else None, 'Running recorder resources settled', 15)


def paused_settled():
    return wait(lambda: value if (value := dump()) and value['phase'] == 'PAUSED' and
                not value['locationSubscribed'] and not value['wakeLockHeld'] and value['speedMetersPerSecond'] is None else None,
                'Manual pause completed checkpoint and released recording resources', 10)


def notification_control(label, expected):
    for attempt in range(3):
        tap(label, system=True)
        deadline = time.monotonic() + 15
        while time.monotonic() < deadline:
            value = dump()
            if value and value['phase'] == expected:
                P.setdefault('notificationAcknowledgements', []).append({'label':label,'tapAttempt':attempt+1,'phase':expected})
                return
            time.sleep(.5)
        print(json.dumps({'notificationNotAcknowledged':label,'attempt':attempt+1,'state':dump()}),flush=True)
    raise AssertionError('Actual notification action was not acknowledged: '+label)


def inject(count):
    global geo_step
    for _ in range(count):
        geo_step += 1
        # Approx.2.6m east per1.3s at Seoul; synthetic GPS, never outdoor evidence.
        response = adb('emu', 'geo', 'fix', f'{126.978 + geo_step * .00003:.6f}', '37.566500').decode()
        guard('OK' in response, 'Owned emulator accepted a synthetic fix')
        time.sleep(1.3)


def wake():
    sh('input', 'keyevent', '224')
    sh('wm', 'dismiss-keyguard')
    wait(lambda: not asleep(), 'Screen awake', 15)


def launch():
    sh('cmd', 'statusbar', 'collapse')
    wake()
    result = sh('am', 'start', '-a', 'android.intent.action.MAIN', '-c', 'android.intent.category.LAUNCHER', '-n', MAIN)
    guard('Error:' not in result and 'Exception' not in result, 'Actual MainActivity launch accepted')
    wait(lambda: focus('main'), 'Actual MainActivity focused')


def open_shade():
    wake()
    sh('cmd', 'statusbar', 'expand-notifications')
    wait(lambda: focus('system'), 'Actual SystemUI shade focused')
    root = tree()
    titles = nodes(root, '러닝 중') + nodes(root, '러닝 · 일시정지')
    guard(len(titles) == 1, 'One recorder notification visible in actual shade')
    if not any(nodes(root, label) for label in ['일시정지', '재개', '종료']):
        parents = {child: parent for parent in root.iter() for child in parent}
        container = titles[0]
        while container in parents:
            container = parents[container]
            expand = [n for n in container.iter('node') if n.get('package') == 'com.android.systemui' and
                      (n.get('content-desc') in ['Expand notification', '알림 펼치기'] or n.get('resource-id', '').endswith('/expand_button'))]
            if len(expand) == 1:
                tap_node(expand[0], root, climb=False)
                break


def start_run():
    print(json.dumps({'stage':'start-run'}),flush=True)
    adb('emu', 'geo', 'fix', f'{126.978 + geo_step * .00003:.6f}', '37.566500')
    launch()
    tap_tab('러닝')
    wait(lambda: bool(nodes(tree(), '러닝 시작')), 'Idle running screen')
    started = int(sh('date', '+%s')) * 1000 - 2000
    tap('러닝 시작')
    running = running_settled()
    creation_windows.append((started, int(sh('date', '+%s')) * 1000 + 3000))
    guard(running['foreground'], 'Recorder promoted to foreground')
    return running


def canonical(snapshot):
    return hashlib.sha256(json.dumps(snapshot, sort_keys=True, separators=(',', ':'), ensure_ascii=False).encode()).hexdigest()


def snapshot(label):
    global snapshot_counter
    guard(not pid(), 'SQLite copies require a force-stopped app')
    snapshot_counter += 1
    directory = W / f'{NAME}-private-db-{snapshot_counter}-{label}'
    directory.mkdir(mode=0o700, exist_ok=False)
    names = adb('exec-out', 'run-as', PKG, 'ls', '-1', 'databases', allow_failure=True).decode().splitlines()
    if 'runmoni-runs.db' not in names:
        return {'runs': [], 'points': []}
    for name in ['runmoni-runs.db', 'runmoni-runs.db-wal']:
        if name in names:
            private_write(directory / name, adb('exec-out', 'run-as', PKG, 'cat', 'databases/' + name))
    db = sqlite3.connect((directory / 'runmoni-runs.db').as_uri() + '?mode=ro', uri=True)
    db.row_factory = sqlite3.Row
    try:
        db.execute('PRAGMA query_only=ON')
        guard(db.execute('PRAGMA quick_check').fetchone()[0] == 'ok', 'Private SQLite copy integrity')
        return {'runs': [dict(row) for row in db.execute('SELECT * FROM runs ORDER BY id')],
                'points': [dict(row) for row in db.execute('SELECT * FROM track_points ORDER BY runId, sequence')]}
    finally:
        db.close()


def preserved(snapshot_value):
    ids = {row['id'] for row in baseline['runs']}
    original_rows = {'runs': [row for row in snapshot_value['runs'] if row['id'] in ids],
                     'points': [row for row in snapshot_value['points'] if row['runId'] in ids]}
    guard(canonical(original_rows) == canonical(baseline), 'All original rows and original points preserved exactly')


def owned_rows(snapshot_value):
    preserved(snapshot_value)
    ids = {row['id'] for row in baseline['runs']}
    rows = [row for row in snapshot_value['runs'] if row['id'] not in ids]
    for row in rows:
        guard(any(low <= row['startedAtMillis'] <= high for low, high in creation_windows), 'Only review-created runs may be cleaned up')
    return rows


def own_summary(row, snapshot_value):
    points = [point for point in snapshot_value['points'] if point['runId'] == row['id']]
    def separation(a, b):
        radians = math.pi / 180
        value = math.sin((b['latitude']-a['latitude'])*radians/2)**2 + math.cos(a['latitude']*radians)*math.cos(b['latitude']*radians)*math.sin((b['longitude']-a['longitude'])*radians/2)**2
        value = max(0.0, min(1.0, value))
        return 6371000 * 2 * math.atan2(math.sqrt(value), math.sqrt(1-value))
    pairs = list(zip(points, points[1:]))
    within = sum(separation(a, b) for a, b in pairs if a['segment'] == b['segment'])
    excluded = [separation(a, b) for a, b in pairs if a['segment'] != b['segment']]
    guard(abs(within-row['distanceMeters']) < .01, 'Saved distance equals geometry within segments; boundaries never bridge')
    return {'testRunId': row['id'], 'pointCount': len(points), 'segmentCount': len({p['segment'] for p in points}),
            'distanceMeters': row['distanceMeters'], 'activeDurationMillis': row['activeDurationMillis'],
            'finished': row['finishedAtMillis'] > 0, 'interrupted': bool(row['interrupted']),
            'pointSequencesContiguous': [p['sequence'] for p in points] == list(range(len(points))),
            'distanceWithinSegmentsMeters': within, 'excludedSegmentBoundaryCount': len(excluded),
            'distanceMatchesSegments': True}


def duration(millis):
    value = max(0, millis) // 1000
    return f'{value // 3600}:{value // 60 % 60:02}:{value % 60:02}' if value >= 3600 else f'{value // 60:02}:{value % 60:02}'


def signature(row):
    when = datetime.datetime.fromtimestamp(row['startedAtMillis'] / 1000, device_zone)
    label = f'{when.month}월 {when.day}일 ({"월화수목금토일"[when.weekday()]}) {when:%H:%M}'
    return label, f'{row["distanceMeters"] / 1000:.2f} km', duration(row['activeDurationMillis'])


def delete_own_ui(row):
    own = signature(row)
    guard(all(signature(old) != own for old in baseline['runs']), 'Own row has an unambiguous UI signature')
    launch()
    tap_tab('나')
    for _ in range(12):
        root = tree()
        candidates = []
        for node in root.iter('node'):
            texts = '\n'.join(n.get('text', '') for n in node.iter('node'))
            if node.get('clickable') == 'true' and all(part in texts for part in own):
                candidates.append(node)
        # Choose only a record row, never the whole page: retain the smallest matching clickable area.
        if candidates:
            candidates.sort(key=lambda n: (rect(n)[2] - rect(n)[0]) * (rect(n)[3] - rect(n)[1]))
            guard(len(candidates) == 1 or rect(candidates[0]) != rect(candidates[1]), 'Unique own record target')
            tap_node(candidates[0], root, climb=False)
            break
        width, height = dimensions()
        sh('input', 'swipe', str(width // 2), str(height * 4 // 5), str(width // 2), str(height * 2 // 5), '350')
    else:
        raise AssertionError('Own record was not reachable; no other record was deleted')
    wait(lambda: bool(nodes(tree(), '나의 러닝 기록')), 'Own record detail')
    detail = '\n'.join(n.get('text', '') for n in tree().iter('node'))
    guard(all(part in detail for part in own), 'Own detail date/distance/time match before delete')
    reveal('기록 삭제')
    tap('기록 삭제')
    wait(lambda: bool(nodes(tree(), '이 기록을 삭제할까요?')), 'Own record delete confirmation')
    tap('삭제')
    wait(lambda: not nodes(tree(), '이 기록을 삭제할까요?') and not nodes(tree(), '나의 러닝 기록'), 'Own record deletion completed')


def cleanup_own_runs():
    print(json.dumps({'stage':'cleanup-owned-review-runs'}),flush=True)
    force_stop()
    current = snapshot('cleanup-check')
    for row in owned_rows(current):
        if not row['finishedAtMillis']:
            launch()
            tap_tab('러닝')
            wait(lambda: bool(nodes(tree(), '러닝 이어가기')), 'Own unfinished run recovery screen')
            tap('러닝 마치기')
            tap('저장하고 마치기')
            wait(lambda: bool(nodes(tree(), '나의 러닝 기록')), 'Own recovery saved without new GPS')
            force_stop()
            current = snapshot('cleanup-finished')
            row = next(item for item in owned_rows(current) if item['id'] == row['id'])
        delete_own_ui(row)
        force_stop()
    final = snapshot('after-ui-cleanup')
    guard(not owned_rows(final) and canonical(final) == canonical(baseline), 'Only review runs removed; complete baseline content hash unchanged')
    P['databaseAfterCleanup'] = {'runCount': len(final['runs']), 'pointCount': len(final['points']), 'contentSha256': canonical(final)}


def native_large_text():
    sh('wm', 'density', '240')  #480px wide owned AVD =>320dp; verify, never assume.
    sh('settings', 'put', 'system', 'font_scale', '2.0')
    wait(lambda: focus('main'), 'Native large-text MainActivity')
    root = tree()
    width, height = dimensions()
    guard(width == 480 and 'Override density: 240' in sh('wm', 'density'), 'Actual320dp viewport')
    nav = [rect(n) for n in root.iter('node') if n.get('package') == 'com.android.systemui' and
           re.search(r'navigation_bar|navigation_inflater|nav_buttons', n.get('resource-id', '')) and
           rect(n)[2] - rect(n)[0] >= width * .8 and rect(n)[1] > height * .75]
    check = {'widthDp': 320, 'fontScale': 2, 'measurement': 'UIAutomator exposed text and clickable-button bounds; not Compose unclipped bounds', 'verified': False}
    nav_top = min(bounds[1] for bounds in nav) if nav else None
    if nav:
        check['navigationSource'] = 'Actual SystemUI accessibility navigation bounds'
    else:
        windows = sh('dumpsys', 'window', 'displays')
        frames = list(re.finditer(r'DisplayFrames w=(\d+) h=(\d+) r=(\d+)', windows))
        matching = [(index, match) for index, match in enumerate(frames)
                    if int(match[1]) == width and int(match[2]) == height and int(match[3]) == 0]
        if len(matching) == 1:
            index, frame = matching[0]
            block = windows[frame.end():frames[index + 1].start() if index + 1 < len(frames) else len(windows)]
            stable = re.search(r'\bmStable=\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]', block)
            if stable:
                left, top, right, bottom = map(int, stable.groups())
                guard(left == 0 and right == width and 0 <= top < bottom < height,
                      'Actual portrait WindowManager stable frame bounds agree with the PNG')
                nav_top = bottom
                check['navigationSource'] = 'Actual WindowManager DisplayFrames.mStable bottom (stable content boundary)'
                check['windowManagerFrame'] = {'width': width, 'height': height, 'rotation': 0, 'stable': [left, top, right, bottom]}
                name = NAME + '-native-window-manager-frames.txt'
                with (E / name).open('x') as output:
                    output.write(frame.group(0) + '\n' + stable.group(0) + '\n')
                check['windowManagerRawExcerpt'] = name
    controls = []
    parents = {child: parent for parent in root.iter() for child in parent}
    for label in ['일시정지', '러닝 마치기']:
        hits = nodes(root, label)
        guard(len(hits) == 1, 'Large-text running action is present')
        bounds = rect(hits[0])
        guard(0 <= bounds[0] < bounds[2] <= width and 0 <= bounds[1] < bounds[3] <= height, 'Large-text action text is within the physical display')
        button = hits[0]
        while button.get('clickable') != 'true' and button in parents:
            button = parents[button]
        guard(button.tag == 'node' and button.get('clickable') == 'true', 'Actual clickable ancestor for each running action')
        button_bounds = rect(button)
        guard(0 <= button_bounds[0] < button_bounds[2] <= width and 0 <= button_bounds[1] < button_bounds[3] <= height,
              'Full exposed clickable bounds lie within the physical display')
        width_dp = (button_bounds[2] - button_bounds[0]) * 160 / 240
        height_dp = (button_bounds[3] - button_bounds[1]) * 160 / 240
        guard(width_dp >= 48 and height_dp >= 48, 'Exposed running control has at least48dp width and height')
        controls.append({'label': label, 'textBounds': bounds, 'buttonBounds': button_bounds,
                         'exposedWidthDp': width_dp, 'exposedHeightDp': height_dp})
    check['controls'] = controls
    if nav_top is not None:
        for control in controls:
            guard(control['textBounds'][3] <= nav_top and control['buttonBounds'][3] <= nav_top,
                  'Full exposed running controls lie above actual OS navigation/stable boundary')
        check.update(verified=True, osNavigationTop=nav_top)
    else:
        check['unverifiedReason'] = 'Neither SystemUI navigation bounds nor a matching portrait WindowManager stable frame was available'
    P['nativeLargeText'] = check
    capture('main-native-320dp-font2', 'main', '러닝 기록 중')
    restore_viewport(original_settings)
    wait(lambda: focus('main'), 'Original viewport restored')


def scenario_background():
    print(json.dumps({'stage':'background-scenario'}),flush=True)
    start_run()
    inject(3)
    first = phase('RUNNING')
    guard(first['pointCount'] >= 2, 'Initial accepted GPS points')
    capture('main-running', 'main', '러닝 기록 중')
    sh('input', 'keyevent', '3')
    sh('input', 'keyevent', '223')
    wait(asleep, 'Actual screen asleep', 15)
    before = phase('RUNNING')
    inject(6)
    after = phase('RUNNING')
    guard(asleep(), 'Screen stayed asleep throughout the background fixes')
    guard(after['foreground'] and after['locationSubscribed'] and after['wakeLockHeld'], 'Background FGS, location subscription and bounded wake lock')
    guard(after['pointCount'] > before['pointCount'] and after['distanceMeters'] > before['distanceMeters'] and
          after['activeDurationMillis'] > before['activeDurationMillis'] and after['speedMetersPerSecond'] is not None and after['speedMetersPerSecond'] > 0,
          'Points, distance, active time and route speed advance while asleep')
    P['checks'].append({'name': 'home-and-screen-off', 'before': before, 'after': after, 'powerAsleep': True})
    open_shade()
    capture('systemui-running', 'system', '러닝 중')
    notification_control('일시정지', 'PAUSED')
    paused = paused_settled()
    guard(not paused['locationSubscribed'] and not paused['wakeLockHeld'] and paused['speedMetersPerSecond'] is None, 'Manual pause releases recording resources and clears speed')
    inject(3)
    still = phase('PAUSED')
    guard(all(still[k] == paused[k] for k in ['pointCount', 'distanceMeters', 'activeDurationMillis']), 'Manual pause does not add fixes, distance or active time')
    open_shade()
    capture('systemui-paused', 'system', '러닝 · 일시정지')
    notification_control('재개', 'RUNNING')
    running_settled()
    inject(1)
    resumed = running_settled()
    guard(resumed['pointCount'] > paused['pointCount'], 'Native points continue after actual resume')
    inject(2)
    P['checks'].append({'name': 'actual-notification-pause-resume', 'paused': paused, 'afterPauseFixes': still, 'resumed': phase('RUNNING')})
    sh('cmd', 'statusbar', 'collapse')
    launch()
    tap('계정 및 러닝 설정', desc=True)
    wait(lambda: bool(nodes(tree(), '설정')), 'Settings was open before notification content tap')
    open_shade()
    tap('러닝 중', system=True)
    wait(lambda: focus('main') and bool(nodes(tree(), '러닝 기록 중')) and not nodes(tree(), '설정'), 'Notification content returns from settings to running')
    P['checks'].append({'name': 'actual-notification-content-tap', 'returnedFromSettingsToRunning': True})
    native_large_text()
    open_shade()
    tap('종료', system=True)
    capture('main-finish-confirmation', 'main', '오늘의 러닝을 마칠까요?')
    tap('취소')
    guard(phase('RUNNING')['foreground'], 'Cancelling finish keeps the actual run active')
    open_shade()
    tap('종료', system=True)
    tap('저장하고 마치기')
    capture('main-saved', 'main', '나의 러닝 기록')
    force_stop()
    saved = snapshot('first-saved')
    rows = owned_rows(saved)
    guard(len(rows) == 1 and rows[0]['finishedAtMillis'] > 0, 'Exactly one review run finished')
    summary = own_summary(rows[0], saved)
    guard(summary['pointCount'] >= after['pointCount'] and summary['segmentCount'] >= 2 and summary['pointSequencesContiguous'], 'Saved route preserves background points and resume segment')
    P['firstSavedRun'] = summary
    cleanup_own_runs()


def scenario_force_stop():
    print(json.dumps({'stage':'force-stop-recovery-scenario'}),flush=True)
    start_run()
    inject(3)
    sh('input', 'keyevent', '3')
    inject(3)
    running = phase('RUNNING')
    old_pid = pid()
    force_stop()
    time.sleep(2)
    guard(not pid() and dump() is None, 'Force-stop does not auto-restart START_NOT_STICKY recording')
    committed = snapshot('force-stop-committed')
    rows = owned_rows(committed)
    guard(len(rows) == 1 and rows[0]['finishedAtMillis'] == 0, 'One committed unfinished review run')
    row = rows[0]
    committed_points = [p for p in committed['points'] if p['runId'] == row['id']]
    launch()
    guard(pid() and pid() != old_pid, 'Actual new process for recovery')
    tap_tab('러닝')
    capture('main-recovery', 'main', '러닝 이어가기')
    guard(dump() is None, 'Recovery does not resume GPS or a foreground recorder automatically')
    tap('러닝 마치기')
    tap('저장하고 마치기')
    capture('main-recovery-saved', 'main', '나의 러닝 기록')
    force_stop()
    final = snapshot('force-stop-final')
    actual = next(item for item in owned_rows(final) if item['id'] == row['id'])
    guard(actual['finishedAtMillis'] > 0 and bool(actual['interrupted']), 'Recovered run is saved as interrupted')
    guard(actual['distanceMeters'] == row['distanceMeters'] and actual['activeDurationMillis'] == row['activeDurationMillis'] and
          [p for p in final['points'] if p['runId'] == row['id']] == committed_points,
          'Recovery finish adds no new points, distance or unobserved active time')
    P['checks'].append({'name': 'force-stop-and-new-process-recovery', 'beforeStop': running,
                        'automaticRestartObserved': False, 'newProcessVerified': True,
                        'committed': own_summary(row, committed), 'saved': own_summary(actual, final)})
    cleanup_own_runs()


def main():
    global baseline, original_settings, restore_xml, device_zone
    can_mutate = False
    os.umask(0o077)
    guard(not OUT.exists(), 'host5 evidence is immutable; choose a new driver name for another execution')
    W.mkdir(exist_ok=True)
    W.chmod(0o700)
    E.mkdir(parents=True, exist_ok=True)
    source = Path(__file__).read_bytes()
    executed = E / (NAME + '-executed-driver.py')
    guard(not executed.exists(), 'Executed driver evidence is immutable')
    executed.write_bytes(source)
    P['executedDriver'] = executed.name
    P['executedDriverSha256'] = hashlib.sha256(source).hexdigest()
    try:
        guard(adb('emu', 'avd', 'name').decode().splitlines()[0] == 'Runmoni_Cloud_29', 'Only the owned cloud AVD')
        guard(sh('getprop', 'ro.build.version.sdk') == '29' and sh('am', 'get-current-user') == '0', 'Owned API29 primary user')
        build_config = (R / 'app/build/generated/source/buildConfig/debug/com/runmoni/app/BuildConfig.java').read_text()
        guard(re.search(r'boolean FIREBASE_ENABLED\s*=\s*false\s*;', build_config) is not None, 'Only Firebase-disabled local debug')
        observer = json.loads((E / 'observer-build.json').read_text())
        guard(sh('sha256sum', '/data/local/tmp/runmoni-live-dump.jar').split()[0] == observer['jarSha256'], 'Actual uploaded shell observer SHA')
        P['observer'] = {'jarSha256': observer['jarSha256'], 'sourceSha256': observer['sourceSha256'], 'idleWaitMillis': 0,
                         'disableIme': False, 'packagedInProductApk': False, 'countsAsProductInstrumentationTests': False, 'pathCompatibility': 'Fresh dangling client link to SDK ANDROID_DATA-relative output; both old files removed before every observation.'}
        binary = json.loads((E / 'binary-build2.json').read_text())
        guard(binary['buildSucceeded'] and binary['version'] == VERSION and binary['versionCode'] == CODE, 'Expected0.3.7/code19 build evidence')
        guard(len(binary['binaries']) == 2, 'Both app and test APK build references are required')
        installed = []
        for package, expected in zip([PKG, PKG + '.test'], binary['binaries']):
            path = sh('pm', 'path', package).removeprefix('package:')
            guard(path.startswith('/data/app/') and '\n' not in path, 'Single installed base APK')
            digest = sh('sha256sum', path, timeout=60).split()[0]
            guard(digest == expected['sha256'], 'Installed app/test SHA matches build2')
            installed.append({'package': package, 'sha256': digest})
        package = sh('dumpsys', 'package', PKG)
        guard(re.search(r'versionName=' + re.escape(VERSION) + r'(?:\s|$)', package) is not None and
              re.search(r'versionCode=19(?:\s|$)', package) is not None, 'Actual installed0.3.7-dev/code19')
        P['installed'] = installed
        guard(dump() is None, 'Do not take over an existing recorder service')
        can_mutate = True
        force_stop()
        before = prefs()
        ET.fromstring(before)
        private_write(W / (NAME + '-private-before.xml'), before)
        restore_path = W / 'pre-install-private.xml'
        guard(restore_path.exists() and restore_path.stat().st_mode & 0o777 == 0o600, 'Private pre-install XML backup exists with0600')
        restore_xml = restore_path.read_bytes()
        ET.fromstring(restore_xml)
        P['privateBeforeSha256'] = hashlib.sha256(before).hexdigest()
        P['preInstallPrivateSha256'] = hashlib.sha256(restore_xml).hexdigest()
        original_settings = settings_snapshot()
        device_zone = ZoneInfo(sh('getprop', 'persist.sys.timezone') or 'UTC')
        baseline = snapshot('baseline')
        guard(all(row['finishedAtMillis'] > 0 for row in baseline['runs']), 'No existing unfinished user run may be taken over')
        P['databaseBaseline'] = {'runCount': len(baseline['runs']), 'pointCount': len(baseline['points']), 'contentSha256': canonical(baseline)}
        fixture = ET.fromstring(before)
        for kind, key, value in [('string', 'communityBackend', 'firebase'), ('string', 'apiUrl', 'http://127.0.0.1:8787'),
                                 ('string', 'appEntryChoiceV1', 'guest'), ('boolean', 'communityGuest', 'true'),
                                 ('boolean', 'voiceGuidance', 'false'), ('boolean', 'autoPause', 'false'), ('boolean', 'publicLocation', 'false')]:
            set_pref(fixture, kind, key, value)
        profile = next((n for n in fixture if n.get('name') == 'localProfile'), None)
        if profile is not None:
            original_profile = json.loads(profile.text)
            anonymous = dict(original_profile, name='배경 검토 러너')
            guard(anonymous.get('mileage') == original_profile.get('mileage') and anonymous.get('owned') == original_profile.get('owned'), 'Anonymous fixture preserves points and ownership')
            profile.text = json.dumps(anonymous, ensure_ascii=False)
        write_prefs(ET.tostring(fixture, encoding='utf-8', xml_declaration=True))
        for permission in original_settings['permissions']:
            sh('pm', 'grant', PKG, permission)
        sh('settings', 'put', 'secure', 'location_mode', '3')
        for key in original_settings['animations']:
            sh('settings', 'put', 'global', key, '0')
        scenario_background()
        scenario_force_stop()
        P['passed'] = True
    except BaseException as failure:
        P['failureType'] = type(failure).__name__
        P['failure'] = str(failure) if isinstance(failure, AssertionError) else 'Review operation failed; inspect the private work log without publishing personal data'
        try:
            if can_mutate:
                # A failed cleanup can be on the historical-record list. Keep
                # that raw image private instead of publishing original rows.
                raw = adb('exec-out', 'screencap', '-p')
                dimensions(raw)
                name = NAME + '-private-failure.png'
                private_write(W / name, raw)
                P['privateFailureScreenshot'] = {'file': name, 'kind': 'unknown', 'sourceKind': 'raw Android screenshot; foreground unverified', 'keptOutsideGit': True}
        except Exception:
            pass
    finally:
        try:
            if baseline is not None:
                cleanup_own_runs()
                P['ownReviewRunsRemoved'] = True
        except BaseException as failure:
            P['passed'] = False
            P['ownReviewRunsRemoved'] = False
            P['cleanupFailureType'] = type(failure).__name__
            try:
                force_stop()
                remaining = snapshot('cleanup-failed')
                P['remainingReviewRunIds'] = [row['id'] for row in owned_rows(remaining)]
            except Exception:
                P['remainingReviewRunIdsUnverified'] = True
        try:
            if can_mutate:
                force_stop()
            if can_mutate and restore_xml is not None:
                write_prefs(restore_xml)
                P['preInstallPreferencesRestoredExactly'] = prefs() == restore_xml
        except BaseException as failure:
            P['preInstallPreferencesRestoredExactly'] = False
            P['preferenceRestoreFailureType'] = type(failure).__name__
        try:
            if original_settings is not None:
                restore_settings(original_settings)
                P['settingsRestoredExactly'] = True
        except BaseException as failure:
            P['settingsRestoredExactly'] = False
            P['settingsRestoreFailureType'] = type(failure).__name__
        P['passed'] = bool(P['passed'] and P.get('ownReviewRunsRemoved') and P.get('preInstallPreferencesRestoredExactly') and P.get('settingsRestoredExactly'))
        P['driverSourceUnchanged'] = hashlib.sha256(Path(__file__).read_bytes()).hexdigest() == P['executedDriverSha256']
        P['passed'] = P['passed'] and P['driverSourceUnchanged']
        P['finishedAtUtc'] = datetime.datetime.now(datetime.timezone.utc).isoformat()
        with OUT.open('x') as output:
            output.write(json.dumps(P, ensure_ascii=False, indent=2) + '\n')
        print(json.dumps({'passed': P['passed'], 'ownReviewRunsRemoved': P.get('ownReviewRunsRemoved'),
                          'preferencesRestored': P.get('preInstallPreferencesRestoredExactly'), 'settingsRestored': P.get('settingsRestoredExactly'),
                          'failureType': P.get('failureType')}, ensure_ascii=False), flush=True)
    return 0 if P['passed'] else 1


if __name__ == '__main__':
    raise SystemExit(main())
