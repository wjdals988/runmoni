#!/usr/bin/env python3
"""루트 전용 실제 MainActivity UI + BG2 관찰 드라이버.

실행은 루트가 한다. 설치/AVD 시작/서버 생성/인증/직접 DB 쓰기는 없다.
기존 host-review5의 두 BG 시나리오·SQLite 사본·구간 기하 검증을 재사용한다.
모든 성공은 실제 관찰 뒤 기록하며 시각적 승인과 실기기/Google 검증은 생성하지 않는다.
"""
import argparse
import datetime
import hashlib
import importlib.util
import json
import math
import os
from pathlib import Path
import re
import struct
import time
import xml.etree.ElementTree as ET
from zoneinfo import ZoneInfo
import zlib

R = next(p for p in Path(__file__).resolve().parents if (p / 'app/build.gradle.kts').exists())
W = R / 'work/ui-ux-pro-max'
E = R / 'docs/app-review/2026-11-06-ui-ux-pro-max'
BG = R / 'work/background-running/host-review5.py'
ANDROID = W / 'android-review.py'
VERSION, CODE = '0.3.7-dev', 19
CONDITIONS = {'nativeFont2': ['largeText'], 'smallLandscape': ['landscape'],
              'systemDarkLightTheme': ['systemDark'], 'imeAndModal': ['ime', 'modal']}


def guard(value, label):
    if not value:
        raise AssertionError(label)


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def now():
    return datetime.datetime.now(datetime.timezone.utc).isoformat()


def load_module(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)  # Both helpers guard their executable main.
    return module


def exclusive(path, raw, private=False):
    with path.open('xb') as output:
        output.write(raw)
        output.flush()
        os.fsync(output.fileno())
    if private:
        path.chmod(0o600)


def atomic(path, value):
    temporary = path.with_suffix(path.suffix + '.tmp')
    with temporary.open('w') as output:
        json.dump(value, output, ensure_ascii=False, indent=2)
        output.write('\n')
        output.flush()
        os.fsync(output.fileno())
    os.replace(temporary, path)
    fd = os.open(path.parent, os.O_RDONLY | os.O_DIRECTORY)
    try:
        os.fsync(fd)
    finally:
        os.close(fd)


def image_pixel(raw, x, y):
    """Read an actual8-bit Android RGB/RGBA PNG; unknown format blocks review."""
    guard(raw.startswith(b'\x89PNG\r\n\x1a\n'), 'Real PNG signature')
    cursor, payload, header = 8, bytearray(), None
    while cursor < len(raw):
        size = int.from_bytes(raw[cursor:cursor + 4], 'big')
        kind, data = raw[cursor + 4:cursor + 8], raw[cursor + 8:cursor + 8 + size]
        if kind == b'IHDR':
            header = struct.unpack('>IIBBBBB', data)
        if kind == b'IDAT':
            payload.extend(data)
        cursor += size + 12
    guard(header is not None, 'PNG IHDR exists')
    width, height, depth, color, compression, filtering, interlace = header
    guard(depth == 8 and color in (2, 6) and compression == filtering == interlace == 0,
          'Only actual noninterlaced8-bit RGB/RGBA screencap is supported')
    guard(0 <= x < width and 0 <= y < height, 'Observed pixel is in image')
    channels = 3 if color == 2 else 4
    stride, decoded = width * channels, zlib.decompress(payload)
    guard(len(decoded) == (stride + 1) * height, 'Exact PNG scanline bytes')
    previous = bytearray(stride)
    for row in range(y + 1):
        offset = row * (stride + 1)
        method, current = decoded[offset], bytearray(decoded[offset + 1:offset + 1 + stride])
        guard(method in range(5), 'Known PNG row filter')
        for i in range(stride):
            left = current[i - channels] if i >= channels else 0
            up, upper_left = previous[i], previous[i - channels] if i >= channels else 0
            if method == 1:
                predictor = left
            elif method == 2:
                predictor = up
            elif method == 3:
                predictor = (left + up) // 2
            elif method == 4:
                p = left + up - upper_left
                candidates = (abs(p - left), abs(p - up), abs(p - upper_left))
                predictor = (left, up, upper_left)[candidates.index(min(candidates))]
            else:
                predictor = 0
            current[i] = (current[i] + predictor) & 255
        previous = current
    return list(previous[x * channels:x * channels + 3])


def run(args):
    os.umask(0o077)
    guard(re.fullmatch(r'host-review\d+', args.name) is not None, 'Fresh restricted immutable attempt name')
    W.mkdir(parents=True, exist_ok=True)
    E.mkdir(parents=True, exist_ok=True)
    out = E / (args.name + '.json')
    guard(not out.exists(), 'Existing host proof must never be overwritten')
    raw_source = Path(__file__).read_bytes()
    executed = E / (args.name + '-executed-driver.py')
    exclusive(executed, raw_source)
    m = load_module('ui_host_bg2', BG)
    a = load_module('ui_host_preservation', ANDROID)
    m.NAME, m.W, m.E, m.OUT, m.math = args.name, W, E, out, math
    p = m.P
    p.update(name=args.name, version=VERSION, versionCode=CODE, binaryReference='binary-build2.json',
             startedAtUtc=now(), phase='created', passed=False, completed=False,
             executedDriver=executed.name, executedDriverSha256=sha(raw_source),
             helpers={}, uiChecks={'passed': False, 'visualInspectionPassed': False, 'captures': {}, 'conditions': {}},
             userVisualApproval=False, signedReleaseRuntimeVerified=False,
             fixtureScope='Actual Firebase-disabled debug MainActivity; no fake server/CRUD; anonymous local name only')
    for label, path in [('background', BG), ('preservation', ANDROID)]:
        retained = E / (args.name + '-executed-' + label + '-helper.py')
        raw = path.read_bytes()
        exclusive(retained, raw)
        p['helpers'][label] = {'file': str(path.relative_to(R)), 'executedCopy': retained.name, 'sha256': sha(raw)}
    before_environment, restore_xml, binary, can_mutate = None, None, None, False
    condition_records = {}

    def checkpoint(phase):
        p['phase'], p['updatedAtUtc'] = phase, now()
        atomic(out, p)
        print(json.dumps({'phase': phase, 'passed': p['passed']}, ensure_ascii=False), flush=True)

    def condition(name, details, passed=False):
        guard(name in CONDITIONS, 'Known publication UI condition')
        record = condition_records.setdefault(name, {'condition': name, 'passed': False,
            'visualInspectionPassed': False, 'captureKeys': CONDITIONS[name], 'steps': []})
        record['steps'].append(details)
        record['passed'] = bool(passed)
        record['updatedAtUtc'] = now()
        path = E / (args.name + '-ui-' + name + '.json')
        atomic(path, record)
        p['uiChecks']['conditions'][name] = {'passed': record['passed'], 'captureKeys': CONDITIONS[name],
            'evidenceReference': path.name, 'evidenceSha256': sha(path.read_bytes()),
            'visualInspectionPassed': False}
        checkpoint('ui-condition-' + name)

    def save_png(name, kind, focus_required=True):
        if focus_required:
            guard(m.focus(kind), 'Expected real focused window for capture')
            if kind == 'main':
                guard(any('mResumedActivity' in line and m.MAIN in line for line in
                          m.sh('dumpsys', 'activity', 'activities').splitlines()), 'Actual MainActivity is resumed')
        raw = m.adb('exec-out', 'screencap', '-p')
        width, height = m.dimensions(raw)
        exclusive(E / name, raw)
        p['captures'].append({'file': name, 'sha256': sha(raw), 'bytes': len(raw), 'width': width, 'height': height,
            'kind': kind, 'sourceKind': {'main': 'actual Android MainActivity', 'system': 'actual Android SystemUI',
            'system-ui-anr': 'actual SystemUI ANR dialog matched by UIAutomator'}.get(kind, 'Unknown; not publication eligible'),
            'expectedWindowFocused': focus_required, 'syntheticGps': m.geo_step > 0,
            'firebaseConfigured': False, 'signedReleaseRuntimeVerified': False})
        checkpoint('capture-' + Path(name).stem)

    m.save_png = save_png

    def ui_capture(key, anchor=None):
        if anchor:
            m.wait(lambda: bool(m.nodes(m.tree(), anchor)), 'Actual UI anchor: ' + anchor)
        if key in {'home', 'profile', 'closet', 'largeText', 'landscape', 'systemDark'}:
            def artwork_ready():
                root = m.tree()
                return not any(n.get('text') == '준비 중' or n.get('content-desc') == '미니미 준비 중' for n in root.iter('node'))
            m.wait(artwork_ready, 'Actual approved avatar first frame is ready before capture', 120)
        guard(key not in p['uiChecks']['captures'], 'Each selected UI capture key is unique')
        save_png(args.name + '-ui-' + key + '.png', 'main')
        p['uiChecks']['captures'][key] = p['captures'][-1]
        checkpoint('ui-capture-selected-' + key)

    def system_tap(label, desc=False):
        def find():
            root = m.tree()
            hits = [n for n in m.nodes(root, label, desc) if n.get('package') == 'com.android.systemui']
            return (root, hits) if hits else None
        root, hits = m.wait(find, 'Original exact SystemUI label: ' + label)
        guard(len(hits) == 1, 'One exact SystemUI node; ancestor tap is forbidden')
        node, bounds = hits[0], list(m.rect(hits[0]))
        target = {'label': label, 'matchedAttribute': 'content-desc' if desc else 'text',
            'package': node.get('package'), 'class': node.get('class'), 'resourceId': node.get('resource-id'),
            'bounds': bounds, 'clickable': node.get('clickable'), 'climb': False,
            'resolvedXY': [(bounds[0] + bounds[2]) // 2, (bounds[1] + bounds[3]) // 2],
            'tapAttempt': 1, 'maximumAttempts': 1, 'acknowledged': False}
        p.setdefault('systemActionTaps', []).append(target)
        checkpoint('system-original-target-before-tap')
        m.tap_node(node, root, climb=False)
        target['tapDispatched'] = True
        checkpoint('system-original-target-tapped')
        return target

    original_tap = m.tap

    def tap(label, desc=False, system=False):
        if system:
            return system_tap(label, desc)
        return original_tap(label, desc, system=False)

    def notification_control(label, expected):
        for attempt in range(1, 4):
            target = system_tap(label)
            target.update(tapAttempt=attempt, maximumAttempts=3, expectedPhase=expected)
            checkpoint('notification-awaiting-ack')
            deadline = time.monotonic() + 15
            while time.monotonic() < deadline:
                value = m.dump()
                if value and value['phase'] == expected:
                    target.update(acknowledged=True, observedPhase=expected)
                    p.setdefault('notificationAcknowledgements', []).append(
                        {'label': label, 'tapAttempt': attempt, 'maximumAttempts': 3, 'phase': expected,
                         'class': target['class'], 'resourceId': target['resourceId'], 'bounds': target['bounds'],
                         'resolvedXY': target['resolvedXY'], 'climb': False})
                    checkpoint('notification-acknowledged')
                    return
                time.sleep(.5)
            target['lastObservedPhase'] = (m.dump() or {}).get('phase')
            checkpoint('notification-not-acknowledged')
        raise AssertionError('Exact SystemUI action did not acknowledge within3 taps: ' + label)

    m.tap, m.notification_control = tap, notification_control

    def stable_frame(root=None):
        width, height = m.dimensions()
        raw = m.sh('dumpsys', 'window', 'displays')
        matches = list(re.finditer(r'DisplayFrames w=(\d+) h=(\d+) r=(\d+)', raw))
        candidates = []
        for index, match in enumerate(matches):
            if (int(match[1]), int(match[2])) != (width, height):
                continue
            block = raw[match.end():matches[index + 1].start() if index + 1 < len(matches) else len(raw)]
            stable = re.search(r'\bmStable=\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]', block)
            if stable:
                bounds = list(map(int, stable.groups()))
                if 0 <= bounds[0] < bounds[2] <= width and 0 <= bounds[1] < bounds[3] <= height:
                    candidates.append({'width': width, 'height': height, 'rotation': int(match[3]), 'bounds': bounds,
                        'measurement': 'Actual WindowManager DisplayFrames.mStable',
                        'rawExcerpt': match.group(0) + '\n' + stable.group(0)})
        guard(len(candidates) == 1, 'One actual matching OS stable frame; unknown OS boundaries block review')
        frame = candidates[0]
        guard(frame['bounds'] != [0, 0, width, height], 'Observed OS stable content excludes a system edge')
        return frame

    def density():
        value = m.sh('wm', 'density')
        match = re.search(r'Override density:\s*(\d+)', value) or re.search(r'Physical density:\s*(\d+)', value)
        guard(match is not None, 'Actual effective display density')
        return int(match[1])

    def action_geometry(label, root=None, desc=False):
        root = root if root is not None else m.tree()
        hits = [n for n in m.nodes(root, label, desc) if n.get('package') == m.PKG]
        guard(len(hits) == 1, 'One actual app action label: ' + label)
        node, target = hits[0], hits[0]
        parents = {child: parent for parent in root.iter() for child in parent}
        while target.get('clickable') != 'true' and target in parents:
            target = parents[target]
        guard(target.tag == 'node' and target.get('clickable') == 'true' and target.get('package') == m.PKG,
              'App action has its own actual clickable target: ' + label)
        frame, text_bounds, button = stable_frame(root), list(m.rect(node)), list(m.rect(target))
        left, top, right, bottom = frame['bounds']
        for bounds in (text_bounds, button):
            guard(left <= bounds[0] < bounds[2] <= right and top <= bounds[1] < bounds[3] <= bottom,
                  'Full exposed app action is above/inside actual OS stable boundary: ' + label)
        dpi = density()
        width_dp, height_dp = (button[2] - button[0]) * 160 / dpi, (button[3] - button[1]) * 160 / dpi
        guard(width_dp >= 48 and height_dp >= 48, 'Actual exposed app action is at least48dp: ' + label)
        return {'label': label, 'textBounds': text_bounds, 'buttonBounds': button,
            'exposedWidthDp': width_dp, 'exposedHeightDp': height_dp, 'osStableFrame': frame,
            'measurementLimit': 'Accessibility exposed geometry; visual text clipping requires separate root image inspection'}

    def reveal_action(label, side=False, maximum=18):
        for attempt in range(maximum):
            root = m.tree()
            if m.nodes(root, label):
                try:
                    return action_geometry(label, root)
                except AssertionError:
                    pass
            frame, dpi = stable_frame(root), density()
            left, top, right, bottom = frame['bounds']
            regions = [list(m.rect(n)) for n in root.iter('node') if n.get('package') == m.PKG
                       and n.get('scrollable') == 'true' and m.rect(n)[3] - m.rect(n)[1] >= round(80 * dpi / 160)
                       and left <= m.rect(n)[0] < m.rect(n)[2] <= right and top <= m.rect(n)[1] < m.rect(n)[3] <= bottom]
            if side:
                regions = [b for b in regions if (b[0] + b[2]) / 2 > (left + right) / 2]
            guard(regions, 'Actual scrollable product body required; never swipe a fixed footer or outside a dialog')
            body = max(regions, key=lambda b: (b[3] - b[1], b[2] - b[0]))
            x = (body[0] + body[2]) // 2
            inset = max(8, round(8 * dpi / 160))
            start, end = body[3] - inset, body[1] + inset
            guard(start > end, 'Actual product body has a scroll gesture region')
            p.setdefault('uiScrollGestures', []).append({'label':label,'bodyBounds':body,'resolvedFrom':[x,start],'resolvedTo':[x,end]})
            checkpoint('ui-scroll-inside-product-body')
            m.sh('input', 'swipe', str(x), str(start), str(x), str(end), '350')
        raise AssertionError('A complete48dp item/action remains unreachable aboveOS navigation: ' + label)

    def open_closet():
        m.tap_tab('나')
        m.reveal('미니미 꾸미기')
        m.tap('미니미 꾸미기')
        m.wait(lambda: bool(m.nodes(m.tree(), '옷장')), 'Actual closet route')

    def close_page(anchor):
        m.tap('닫기')
        m.wait(lambda: not m.nodes(m.tree(), anchor), 'Actual route closed: ' + anchor)

    def open_settings():
        m.tap('계정 및 러닝 설정', desc=True)
        m.wait(lambda: bool(m.nodes(m.tree(), '설정')), 'Actual settings route')

    def open_about():
        m.tap('v' + VERSION)
        m.wait(lambda: bool(m.nodes(m.tree(), '앱 정보')), 'Actual app information dialog')

    def normal_viewport():
        a.shell(m, 'settings', 'put', 'system', 'accelerometer_rotation', '0')
        a.shell(m, 'settings', 'put', 'system', 'user_rotation', '0')
        m.sh('wm', 'density', 'reset')
        m.sh('settings', 'put', 'system', 'font_scale', '1.0')
        m.sh('cmd', 'uimode', 'night', 'no')
        m.wait(lambda: m.dimensions()[0] < m.dimensions()[1], 'Actual normal portrait restored')

    def native_viewport(landscape=False):
        m.sh('wm', 'density', '240')
        m.sh('settings', 'put', 'system', 'font_scale', '2.0')
        a.shell(m, 'settings', 'put', 'system', 'accelerometer_rotation', '0')
        a.shell(m, 'settings', 'put', 'system', 'user_rotation', '1' if landscape else '0')
        m.wait(lambda: (m.dimensions()[0] > m.dimensions()[1]) == landscape, 'Actual requested rotation')
        width, height = m.dimensions()
        guard(density() == 240 and float(m.sh('settings', 'get', 'system', 'font_scale')) == 2,
              'Actual native font2 and240dpi')
        guard(min(width, height) == 480 and abs(min(width, height) * 160 / density() - 320) < .01,
              'Actual native short side is320dp; no Compose fixture override')
        return {'widthPx': width, 'heightPx': height, 'widthDp': width * 160 / density(),
                'heightDp': height * 160 / density(), 'fontScale': 2, 'densityDpi': density(),
                'nativeConfiguration': True, 'osStableFrame': stable_frame()}

    def shoes_tab():
        categories = ['헤어', '모자', '얼굴', '상의', '하의', '세트', '양말', '신발', '액세서리']
        for _ in range(10):
            root = m.tree()
            targets = m.nodes(root, '신발')
            if targets:
                target = min(targets, key=lambda n: m.rect(n)[1])
                m.tap_node(target, root)
                return
            visible = [n for label in categories for n in m.nodes(root, label)]
            guard(visible, 'Visible actual closet category row')
            top = min(m.rect(n)[1] for n in visible)
            row = [n for n in visible if abs(m.rect(n)[1] - top) <= 4]
            y = sum(m.rect(n)[1] + m.rect(n)[3] for n in row) // (2 * len(row))
            width, _ = m.dimensions()
            m.sh('input', 'swipe', str(width - 24), str(y), str(width // 2), str(y), '400')
        raise AssertionError('Actual shoe category could not be reached by category-row scrolling')

    def closet_bounds(landscape):
        config = native_viewport(landscape)
        m.wait(lambda: bool(m.nodes(m.tree(), '옷장')), 'Closet survives native configuration change')
        controls = [action_geometry('닫기'), action_geometry('남자'), action_geometry('여자')]
        shoes_tab()
        controls.append(reveal_action('골드 러닝화', side=landscape))
        controls.append(reveal_action('골드', side=landscape))
        root, frame = m.tree(), stable_frame()
        regions = [list(m.rect(n)) for n in root.iter('node') if n.get('package') == m.PKG and n.get('scrollable') == 'true']
        guard(regions, 'Actual closet exposes its scrollable item/category region')
        left, top, right, bottom = frame['bounds']
        guard(all(left <= b[0] < b[2] <= right and top <= b[1] < b[3] <= bottom for b in regions),
              'Full exposed closet scroll regions remain inside actual OS content')
        key = 'landscape' if landscape else 'largeText'
        ui_capture(key)
        return {'configuration': config, 'controls': controls, 'scrollRegionBounds': regions,
                'actionsAndLastListItemAboveOs': True, 'storedProfileChangedByThisCheck': False}

    def ime_modal_checks():
        normal_viewport()
        m.launch()
        open_settings()
        root = m.tree()
        fields = [n for n in root.iter('node') if n.get('package') == m.PKG and n.get('class') == 'android.widget.EditText']
        guard(len(fields) == 1, 'One real visible settings EditText')
        before_text = fields[0].get('text', '')
        guard(before_text == 'UI 검토 러너', 'Only anonymous fixture text can be typed/captured')
        m.tap_node(fields[0], root, climb=False)
        m.wait(lambda: re.search(r'mInputShown=true|mIsInputViewShown=true', m.sh('dumpsys', 'input_method')) is not None,
               'Actual IME is shown for settings EditText')
        m.sh('input', 'keyevent', '123')
        m.sh('input', 'text', '_UI')
        def edited():
            root = m.tree()
            return root if any(n.get('class') == 'android.widget.EditText' and n.get('text') == before_text + '_UI'
                               for n in root.iter('node')) else None
        m.wait(edited, 'Actual unsaved EditText accepts text')
        ui_capture('ime', '설정')
        m.sh('input', 'keyevent', '4')
        m.wait(lambda: re.search(r'mInputShown=false', m.sh('dumpsys', 'input_method')) is not None,
               'Actual IME was hidden by Back')
        guard(bool(edited()) and bool(m.nodes(m.tree(), '설정')), 'Keyboard hiding keeps the settings route and typed draft')
        save_png(args.name + '-ui-ime-hidden-draft-preserved.png', 'main')
        name_action = action_geometry('이름 저장')
        close_page('설정')  # Never press 이름 저장; original profile remains untouched.
        open_about()
        root = m.tree()
        scrolls = [list(m.rect(n)) for n in root.iter('node') if n.get('package') == m.PKG and n.get('scrollable') == 'true']
        guard(scrolls, 'Actual FormDialog has an exposed scroll region')
        raw_xml = W / (args.name + '-private-about-before-scroll.xml')
        exclusive(raw_xml, ET.tostring(root), private=True)
        before_nodes = {(n.get('text'), n.get('bounds')) for n in root.iter('node') if n.get('package') == m.PKG}
        report_action = reveal_action('문제 신고')
        after_nodes = {(n.get('text'), n.get('bounds')) for n in m.tree().iter('node') if n.get('package') == m.PKG}
        guard(before_nodes != after_nodes, 'Actual FormDialog content bounds changed after reaching its lower support action')
        ui_capture('modal')
        close_action = reveal_action('닫기')
        close_page('앱 정보')
        open_about()
        root, frame = m.tree(), stable_frame()
        title = m.nodes(root, '앱 정보')
        guard(len(title) == 1, 'One actual modal title')
        title_bounds = m.rect(title[0])
        surfaces = [list(m.rect(n)) for n in root.iter('node') if n.get('package') == m.PKG
                    and m.rect(n)[0] > frame['bounds'][0] + 2 and m.rect(n)[2] < frame['bounds'][2] - 2
                    and m.rect(n)[0] <= title_bounds[0] and m.rect(n)[2] >= title_bounds[2]
                    and m.rect(n)[1] <= title_bounds[1] and m.rect(n)[3] >= title_bounds[3]]
        guard(surfaces, 'Actual inset dialog bounds establish a safe outside-tap region')
        surface = max(surfaces, key=lambda b: (b[2] - b[0]) * (b[3] - b[1]))
        x, y = frame['bounds'][0] + 2, (surface[1] + surface[3]) // 2
        guard(x < surface[0] and frame['bounds'][1] < y < frame['bounds'][3], 'Tap is outside actual dialog and inside OS content')
        m.sh('input', 'tap', str(x), str(y))
        m.wait(lambda: not m.nodes(m.tree(), '앱 정보'), 'Actual FormDialog dismisses on outside tap')
        condition('imeAndModal', {'actualImeShownAndHidden': True, 'draftPreservedAfterHide': True,
            'draftCommitted': False, 'nameSaveActionAfterHide': name_action, 'modalScrollRegion': scrolls,
            'modalSupportAction': report_action, 'modalContentGeometryChangedAfterScroll': True, 'modalCloseAction': close_action,
            'outsideTap': {'dialogBounds': surface, 'resolvedXY': [x, y], 'dismissed': True}}, passed=True)

    def ui_before_background():
        normal_viewport()
        m.launch()
        m.tap_tab('홈')
        ui_capture('home', '러닝 시작하기')
        for tab, key, anchor in [('탐색', 'explore', '탐색'), ('러닝', 'running', '러닝 시작'),
                                  ('크루', 'crew', '크루 만들기'), ('나', 'profile', '미니미 꾸미기')]:
            m.tap_tab(tab)
            ui_capture(key, anchor)
        open_settings()
        ui_capture('settings', '러너 이름')
        close_page('설정')
        open_closet()
        ui_capture('closet', '옷장')
        close_page('옷장')
        open_about()
        ui_capture('about', '앱 정보')
        close_page('앱 정보')
        checkpoint('all-eight-actual-main-pages-captured-before-bg')
        open_closet()
        details = closet_bounds(False)
        condition('nativeFont2', details, passed=False)  # Running action proof follows during BG2.
        details = closet_bounds(True)
        p['landscape'] = dict(details['configuration'], verified=True, actionsAndListAboveOs=True)
        condition('smallLandscape', details, passed=True)
        close_page('옷장')
        normal_viewport()
        m.launch()
        m.tap_tab('홈')
        m.sh('cmd', 'uimode', 'night', 'yes')
        m.wait(lambda: re.search(r'mNightMode=2\b', m.sh('dumpsys', 'uimode')) is not None,
               'Actual OS night mode yes acknowledged')
        ui_capture('systemDark', '러닝 시작하기')
        record = p['uiChecks']['captures']['systemDark']
        raw = (E / record['file']).read_bytes()
        pixel_xy = [record['width'] - 6, record['height'] // 2]
        rgb = image_pixel(raw, *pixel_xy)
        guard(rgb == [245, 247, 246], 'Actual app side canvas stays approved light cream while OS night=yes')
        p['systemDark'] = {'verified': True, 'nightMode': 'yes', 'actualOsAcknowledged': True,
            'sampleXY': pixel_xy, 'sampleRGB': rgb, 'appTheme': 'existing light theme',
            'statusAndNavigationIconsCaptured': True, 'iconContrastVisualInspectionPassed': False}
        condition('systemDarkLightTheme', dict(p['systemDark']), passed=True)
        ime_modal_checks()
        normal_viewport()
        m.launch()
        m.tap_tab('홈')
        checkpoint('ui-checks-complete-normal-portrait-before-bg2')

    def native_running():
        config = native_viewport(False)
        controls = [action_geometry('일시정지'), action_geometry('러닝 마치기')]
        p['nativeLargeText'] = {'widthDp': 320, 'fontScale': 2, 'verified': True,
            'configuration': config, 'controls': controls, 'measurement': 'Actual native configuration and exposed OS-safe clickable bounds'}
        m.capture('main-native-320dp-font2', 'main', '러닝 기록 중')
        condition('nativeFont2', {'runningActions': controls, 'configuration': config,
            'firstResumeFixOrderAssumed': False}, passed=True)
        landscape_config = native_viewport(True)
        landscape_controls = [action_geometry('일시정지'), action_geometry('러닝 마치기')]
        landscape_root = m.tree()
        readable_regions = [list(m.rect(n)) for n in landscape_root.iter('node') if n.get('package') == m.PKG
                            and n.get('scrollable') == 'true' and m.rect(n)[3] > m.rect(n)[1]]
        guard(readable_regions, 'Actual active landscape retains an accessible scrollable body')
        save_png(args.name + '-main-running-landscape-font2.png', 'main')
        landscape_capture = p['captures'][-1]
        p['runningLandscape'] = {'verified':True, 'configuration':landscape_config, 'controls':landscape_controls,
                                  'bodyRegions':readable_regions, 'capture':landscape_capture,
                                  'measurementLimit':'Exposed controls and body geometry; root visual inspection remains separate'}
        p['landscape']['runningControlsVerified'] = True
        original_landscape = json.loads((E / (args.name + '-ui-smallLandscape.json')).read_text())
        condition('smallLandscape', {'closet':original_landscape, 'running':p['runningLandscape']}, passed=True)
        normal_viewport()
        m.wait(lambda: m.focus('main'), 'Normal portrait restored after active native-font2 check')

    m.native_large_text = native_running
    original_snapshot = m.snapshot

    def snapshot(label):
        value = original_snapshot(label)
        p.setdefault('databaseCheckpoints', []).append({'label': label, 'runCount': len(value['runs']),
            'pointCount': len(value['points']), 'contentSha256': m.canonical(value)})
        checkpoint('database-copy-' + label)
        return value

    m.snapshot = snapshot
    checkpoint('created-before-device-mutation')
    try:
        binary_raw = (E / 'binary-build2.json').read_bytes()
        binary = json.loads(binary_raw)
        guard(binary.get('buildSucceeded') is True and binary.get('version') == VERSION and binary.get('versionCode') == CODE,
              'Frozen final0.3.7-dev/code19 build1 evidence')
        p['binaryReferenceSha256'], p['sourceMapsBefore'] = sha(binary_raw), a.source_maps(binary)
        guard(m.adb('emu', 'avd', 'name').decode().splitlines()[0] == 'Runmoni_Cloud_29', 'Owned AVD only')
        guard(m.sh('getprop', 'ro.build.version.sdk') == '29' and m.sh('am', 'get-current-user') == '0', 'Owned API29 user0')
        build_config = (R / 'app/build/generated/source/buildConfig/debug/com/runmoni/app/BuildConfig.java').read_text()
        guard(re.search(r'boolean FIREBASE_ENABLED\s*=\s*false\s*;', build_config) is not None,
              'Only unconfigured debug; Google/production writes must remain unavailable')
        observer_path = R / 'docs/app-review/2026-11-06-background-running/observer-build.json'
        observer = json.loads(observer_path.read_text())
        guard(m.sh('sha256sum', '/data/local/tmp/runmoni-live-dump.jar').split()[0] == observer['jarSha256'],
              'Actual uploaded observer jar SHA')
        p['observer'] = {'jarSha256': observer['jarSha256'], 'sourceSha256': observer['sourceSha256'],
            'idleWaitMillis': 0, 'disableIme': False, 'packagedInProductApk': False,
            'countsAsProductInstrumentationTests': False, 'buildReference': str(observer_path.relative_to(R))}
        guard(len(binary.get('binaries', [])) == 2, 'Frozen app/test pair required')
        installed = []
        for package, expected in zip([m.PKG, m.PKG + '.test'], binary['binaries']):
            path = m.sh('pm', 'path', package).removeprefix('package:')
            guard(path.startswith('/data/app/') and '\n' not in path, 'Single installed base APK')
            digest = m.sh('sha256sum', path, timeout=60).split()[0]
            guard(digest == expected['sha256'], 'Installed pair matches final build2 exactly')
            installed.append({'package': package, 'sha256': digest})
        p['installed'] = installed
        package = m.sh('dumpsys', 'package', m.PKG)
        guard(re.search(r'versionName=' + re.escape(VERSION) + r'(?:\s|$)', package) is not None and
              re.search(r'versionCode=19(?:\s|$)', package) is not None, 'Actual installed version/code')
        guard(m.dump() is None, 'Never take over an existing recorder service')
        before_environment = a.environment_snapshot(m)
        p['environmentRestorationScope'] = a.environment_restoration_scope()
        env_path = W / (args.name + '-private-environment-before.json')
        exclusive(env_path, (json.dumps(before_environment, ensure_ascii=False, indent=2) + '\n').encode(), private=True)
        p['privateEnvironmentBackup'] = {'file': env_path.name, 'sha256': sha(env_path.read_bytes()), 'mode': '0600'}
        checkpoint('original-environment-durable-before-any-mutation')
        can_mutate = True
        m.force_stop()
        before = m.prefs()
        ET.fromstring(before)
        before_path = W / (args.name + '-private-before.xml')
        exclusive(before_path, before, private=True)
        restore_path = W / 'pre-install-private.xml'
        guard(restore_path.exists() and restore_path.stat().st_mode & 0o777 == 0o600,
              'Existing pre-install whole-XML backup must be0600')
        restore_xml = restore_path.read_bytes()
        ET.fromstring(restore_xml)
        p['privateBeforeSha256'], p['preInstallPrivateSha256'] = sha(before), sha(restore_xml)
        m.original_settings = m.settings_snapshot()
        m.device_zone = ZoneInfo(m.sh('getprop', 'persist.sys.timezone') or 'UTC')
        m.baseline = m.snapshot('baseline')
        guard(all(row['finishedAtMillis'] > 0 for row in m.baseline['runs']), 'Existing unfinished user run blocks review')
        p['databaseBaseline'] = {'runCount': len(m.baseline['runs']), 'pointCount': len(m.baseline['points']),
            'contentSha256': m.canonical(m.baseline)}
        fixture = ET.fromstring(before)
        for kind, key, value in [('string', 'communityBackend', 'firebase'), ('string', 'apiUrl', 'http://127.0.0.1:8787'),
                                 ('string', 'appEntryChoiceV1', 'guest'), ('boolean', 'communityGuest', 'true'),
                                 ('boolean', 'voiceGuidance', 'false'), ('boolean', 'autoPause', 'false'), ('boolean', 'publicLocation', 'false')]:
            m.set_pref(fixture, kind, key, value)
        profile = next((n for n in fixture if n.get('name') == 'localProfile'), None)
        guard(profile is not None, 'Existing local profile required; unknown field absence blocks anonymous screenshot')
        original_profile = json.loads(profile.text)
        anonymous = dict(original_profile, name='UI 검토 러너')
        guard({k: v for k, v in anonymous.items() if k != 'name'} == {k: v for k, v in original_profile.items() if k != 'name'},
              'Only anonymous display name may change; points/owned/all other fields preserved')
        profile.text = json.dumps(anonymous, ensure_ascii=False)
        m.write_prefs(ET.tostring(fixture, encoding='utf-8', xml_declaration=True))
        for permission in m.original_settings['permissions']:
            m.sh('pm', 'grant', m.PKG, permission)
        m.sh('settings', 'put', 'secure', 'location_mode', '3')
        for key in m.original_settings['animations']:
            m.sh('settings', 'put', 'global', key, '0')
        m.wake()
        checkpoint('anonymous-local-fixture-ready-points-and-owned-preserved')
        ui_before_background()
        guard(a.local_profile(m) == anonymous, 'UI navigation/IME/closet inspection never changed stored profile')
        checkpoint('bg2-background-scenario-starting')
        m.scenario_background()
        checkpoint('bg2-background-scenario-finished')
        for target in p.get('systemActionTaps', []):
            if target['label'] == '러닝 중':
                target.update(acknowledged=True, acknowledgement='Observed settings-to-running MainActivity route in BG2 check')
            elif target['label'] == '종료':
                target.update(acknowledged=True, acknowledgement='Observed finish confirmation/cancel/save path in BG2')
        checkpoint('bg2-force-stop-scenario-starting')
        m.scenario_force_stop()
        checkpoint('bg2-force-stop-scenario-finished')
        p['sourceMapsAfter'] = a.source_maps(binary)
        guard(sha((E / 'binary-build2.json').read_bytes()) == p['binaryReferenceSha256'], 'Frozen build evidence unchanged')
        ui = p['uiChecks']
        guard(set(ui['captures']) == {'home', 'explore', 'running', 'crew', 'profile', 'settings', 'closet', 'about',
                                      'largeText', 'landscape', 'systemDark', 'ime', 'modal'}, 'All13 actual UI captures required')
        guard(set(ui['conditions']) == set(CONDITIONS) and all(c.get('passed') is True for c in ui['conditions'].values()),
              'Every actual UI condition must pass its own measurement')
        ui['passed'] = True
        p['passed'] = True
        checkpoint('all-observed-checks-finished-awaiting-restoration-and-root-visual-review')
    except BaseException as failure:
        p['passed'] = False
        p['failureType'] = type(failure).__name__
        p['failure'] = str(failure) if isinstance(failure, AssertionError) else 'Review operation failed; inspect private diagnostics without publishing personal data'
        checkpoint('failed-before-finally')
        if can_mutate:
            try:
                raw = m.adb('exec-out', 'screencap', '-p')
                m.dimensions(raw)
                failure_path = W / (args.name + '-private-failure.png')
                exclusive(failure_path, raw, private=True)
                p['privateFailureScreenshot'] = {'file': failure_path.name, 'keptOutsideGit': True}
            except Exception:
                pass
    finally:
        if can_mutate:
            try:
                if m.baseline is not None:
                    normal_viewport()
                    m.cleanup_own_runs()
                    p['ownReviewRunsRemoved'] = True
                checkpoint('own-review-run-cleanup-attempted')
            except BaseException as failure:
                p.update(passed=False, ownReviewRunsRemoved=False, cleanupFailureType=type(failure).__name__)
                try:
                    m.force_stop()
                    remaining = m.snapshot('cleanup-failed')
                    p['remainingReviewRunIds'] = [row['id'] for row in m.owned_rows(remaining)]
                except Exception:
                    p['remainingReviewRunIdsUnverified'] = True
                checkpoint('own-review-run-cleanup-failed')
            try:
                m.force_stop()
                if restore_xml is not None:
                    m.write_prefs(restore_xml)
                    p['preInstallPreferencesRestoredExactly'] = m.prefs() == restore_xml
                checkpoint('pre-install-whole-xml-restoration-attempted')
            except BaseException as failure:
                p.update(preInstallPreferencesRestoredExactly=False, preferenceRestoreFailureType=type(failure).__name__)
                checkpoint('pre-install-whole-xml-restoration-failed')
            try:
                if before_environment is not None:
                    equal, after, failures = a.restore_environment(m, before_environment)
                    path = W / (args.name + '-private-environment-after.json')
                    exclusive(path, (json.dumps(after, ensure_ascii=False, indent=2) + '\n').encode(), private=True)
                    p.update(settingsRestoredExactly=equal, controlledSettingsPermissionsPowerRestoredExactly=equal, settingsRestoreCommandFailures=failures)
                checkpoint('controlled-settings-permissions-power-restoration-attempted')
            except BaseException as failure:
                p.update(settingsRestoredExactly=False, settingsRestoreFailureType=type(failure).__name__)
                checkpoint('controlled-settings-restoration-failed')
        else:
            p['restorationNotApplicableBeforeMutation'] = True
        try:
            if binary is not None:
                p['sourceMapsFinal'] = a.source_maps(binary)
            p['helperSourcesUnchanged'] = all(sha((R / item['file']).read_bytes()) == item['sha256'] for item in p['helpers'].values())
        except BaseException as failure:
            p.update(passed=False, finalSourceFailureType=type(failure).__name__)
        p['driverSourceUnchanged'] = sha(Path(__file__).read_bytes()) == p['executedDriverSha256']
        p['passed'] = bool(p['passed'] and p.get('ownReviewRunsRemoved') and p.get('preInstallPreferencesRestoredExactly') and
                          p.get('settingsRestoredExactly') and p.get('helperSourcesUnchanged') and p['driverSourceUnchanged'])
        p['completed'], p['finishedAtUtc'] = True, now()
        checkpoint('complete-passed-awaiting-root-visual-review' if p['passed'] else 'complete-failed')
    return 0 if p['passed'] else 1


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--name', default='host-review1', help='Fresh immutable execution name')
    raise SystemExit(run(parser.parse_args()))
