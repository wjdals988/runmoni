#!/usr/bin/env python3
"""Root-only preparation: two real Launcher3 idle-widget captures on the owned AVD.

No installation, authentication, run/service start, notification controls,
canonical DB writes, HTTP requests, proxy changes or emulator lifecycle calls.
Application automatic update traffic is NOT measured as zero. This driver
temporarily revokes preview-location permission to suppress home weather.

Execute only after Root independently reviews this prepared source and the
binary3/source3 instrumentation restoration. Every failure still enters the
owned-widget/cache/preferences/environment cleanup; no DB is copied back.
"""
from pathlib import Path
import argparse
import hashlib
import importlib.util
import json
import os
import re
import shlex
import time
import xml.etree.ElementTree as ET

R = Path('/workspace/runmoni')
W = R / 'work/widget-wardrobe'
E = R / 'docs/app-review/2026-11-10-widget-wardrobe'
H = R / 'work/running-experience/actual-ui-helper6.py'
ENV = W / 'android-review.py'
EXPECTED_OBSERVER_SHA256 = '1cc713c08cf5c2e12b83c0fcb0a879ae92922ff0cfe318388e6b9fef796c84d2'
EXPECTED_ENVIRONMENT_SHA256 = 'd834959bc4352b6a9de28e9407f49f994b90b835783d1e98d6a2ed360c0db139'
EXPECTED_BINARY_SHA256 = 'a80b4c667b6bfe67e9ceaf70d4368398eb65023f0f1458075e69951dec218bee'
PKG = 'com.runmoni.app'
PROVIDER = PKG + '/.widget.RunWidgetProvider'
CACHE = 'shared_prefs/runmoni_widget.xml'


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def load_module(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--name', required=True, help='Fresh widget1, widget2, ...; never reuse a proof name')
    args = parser.parse_args()
    assert re.fullmatch(r'widget[1-9]\d*', args.name)
    os.umask(0o077)
    out = E / (args.name + '.json')
    assert not out.exists()
    # Guard source bytes before import: these helper modules have guarded entry
    # points, but an edited helper still requires independent Root review.
    assert sha(H.read_bytes()) == EXPECTED_OBSERVER_SHA256
    assert sha(ENV.read_bytes()) == EXPECTED_ENVIRONMENT_SHA256
    env = load_module('owned_launcher_environment', ENV)
    m = load_module('owned_launcher_live_observer', H)
    m.W, m.E, m.NAME, m.VERSION, m.CODE = W, E, args.name, '0.4.1-dev', 23
    proof = {
        'name': args.name, 'version': '0.4.1-dev', 'versionCode': 23,
        'api': 29, 'avd': 'Runmoni_Cloud_29', 'acceleration': 'TCG',
        'binaryReference': 'binary-build4.json', 'passed': False, 'completed': False,
        'startedAtUtc': env.now(), 'captures': [], 'systemUiWaitActions': [],
        'accessibilityRetries': 0, 'actualTaps': [], 'launcherWidgetLifecycles': [],
        'driverWidgetUpdateBroadcastsSent': 0,
        'twoIndependentOsPinPlacements': True, 'sameWidgetResizingVerified': False,
        'sourceKind': 'actual Android Launcher3 AppWidget RemoteViews',
        'databaseWritesByReview': False, 'runCreatedByReview': False,
        'notificationControlsExecuted': False, 'authenticationExecuted': False,
        'foregroundServiceStartedByReview': False,
        'httpRequestsMadeByDriver': 0, 'networkOrProxyConfigurationChanged': False,
        'appNetworkTrafficMeasured': False, 'appTotalNetworkZeroClaimed': False,
        'automaticAppUpdateFeedTrafficMayOccur': True,
        'physicalDeviceVerified': False, 'googleLoginVerified': False,
        'productionWritesVerified': False, 'android31RuntimeVerified': False,
        'signedReleaseRuntimeVerified': False, 'outdoorGpsVerified': False,
        'dozeVerified': False, 'oemBatteryPolicyVerified': False,
        'syntheticGps': False, 'locationInjected': False,
        'factoryGlyphInkVerificationIsSeparate': True,
        'glyphInkMeasuredByThisDriver': False, 'rootPixelReviewRequiredBeforePublication': True,
        'rootPrivacyReviewRequiredBeforePublication': True, 'userVisualApproval': False,
        'otherLauncherFixtureItemsMayAppear': True,
        'otherLauncherAccountsOrPersonalContentVerifiedAbsentByDriver': False,
        'launcherHomePageIndexRestorationClaimed': False,
        'wholeLauncherDatabaseOrAllOsBytesRestorationClaimed': False,
    }
    m.P = proof
    before_env = old_prefs = old_profile = baseline = before_widgets = old_widget = None
    binary = None
    can_restore = False
    pin_requested = False
    review_id = None
    current_lifecycle = None
    last_main_pid = None
    original_focus = m.focus
    original_capture = m.save_png

    def stage(label):
        proof['stage'], proof['updatedAtUtc'] = label, env.now()
        env.atomic_json(out, proof)
        print(label, flush=True)

    driver = Path(__file__).read_bytes()
    for filename, raw in [(args.name + '-executed-driver.py', driver),
                          (args.name + '-actual-ui-helper.py', H.read_bytes()),
                          (args.name + '-environment-helper.py', ENV.read_bytes())]:
        env.exclusive_bytes(E / filename, raw)
    proof.update(executedDriver=args.name + '-executed-driver.py', executedDriverSha256=sha(driver),
                 observerHelper=args.name + '-actual-ui-helper.py', observerHelperSha256=sha(H.read_bytes()),
                 environmentHelper=args.name + '-environment-helper.py', environmentHelperSha256=sha(ENV.read_bytes()))

    def private(name, raw):
        path = W / (args.name + '-private-' + name)
        m.private_write(path, raw)
        assert path.stat().st_mode & 0o777 == 0o600
        return {'file': path.name, 'sha256': sha(raw), 'mode': '0600'}

    def observer_capture(name, kind, focus_required=True):
        if kind == 'system-ui-anr':
            assert focus_required is False
            info = private(name, m.adb('exec-out', 'screencap', '-p'))
            proof.setdefault('privateObserverCaptures', []).append({'requestedDiagnosticName': name, **info})
            return
        return original_capture(name, kind, focus_required)

    def reviewed_focus(kind):
        if kind != 'launcher':
            return original_focus(kind)
        raw = m.sh('dumpsys', 'window', 'displays')
        focus = next((line for line in raw.splitlines() if 'mCurrentFocus=' in line), '')
        return 'com.android.launcher3/' in focus or 'com.android.launcher3}' in focus

    m.focus, m.save_png = reviewed_focus, observer_capture

    def assert_idle():
        assert m.dump() is None, 'Refuse an active recorder; this driver never stops or controls a run'
        services = m.sh('dumpsys', 'activity', 'services', PKG)
        assert not re.search(r'\bisForeground=true\b', services), 'No foreground service in the reviewed app'
        assert not re.search(r'ServiceRecord[^\n]*\.tracking\.RunTrackingService', services), 'No recorder ServiceRecord'
        proof.setdefault('idleServiceObservations', []).append({'observedAtUtc': env.now(),
            'recorderDumpAbsent': True, 'foregroundServiceInReviewedAppObserved': False,
            'recorderServiceRecordObserved': False, 'runningFgsExecutionVerified': False})
        return True

    def cache_state():
        names = m.adb('exec-out', 'run-as', PKG, 'ls', '-1', 'shared_prefs').decode().splitlines()
        assert 'runmoni_widget.xml.bak' not in names, 'No pending widget preference backup'
        present = 'runmoni_widget.xml' in names
        raw = m.adb('exec-out', 'run-as', PKG, 'cat', CACHE) if present else b''
        # Back up existing cache as opaque bytes, including an already malformed file.
        # A review fixture must not silently repair or discard the original.
        try:
            well_formed = bool(present and ET.fromstring(raw).tag == 'map')
        except ET.ParseError:
            well_formed = False
        return {'present': present, 'raw': raw, 'wellFormedXml': well_formed}

    def write_widget_cache(raw, present):
        assert not m.pid(), 'Only change the separate widget cache while the app is stopped'
        if present:
            command = shlex.join(['run-as', PKG, 'sh', '-c', 'cat > ' + CACHE])
            m.adb('shell', '-T', command, data=raw)
        else:
            m.sh('run-as', PKG, 'rm', '-f', CACHE)
        m.sh('sync')
        observed = cache_state()
        assert observed['present'] is present and observed['raw'] == raw

    def widget_ids():
        text = m.sh('dumpsys', 'appwidget')
        assert 'Widgets:' in text and 'Hosts:' in text, 'Recognized AppWidgetService dump schema'
        text = text.split('Widgets:', 1)[1].split('Hosts:', 1)[0]
        all_ids, own = [], []
        for block in re.split(r'(?=  \[\d+\] id=)', text):
            found = re.search(r' id=(\d+)', block)
            if found:
                value = int(found[1]); all_ids.append(value)
                if 'com.runmoni.app/' in block:
                    own.append(value)
        assert len(all_ids) == len(set(all_ids)), 'Unambiguous widget service IDs'
        return {'all': sorted(all_ids), 'runmoni': sorted(own)}

    def launcher_resumed():
        raw = m.sh('dumpsys', 'activity', 'activities')
        return any('mResumedActivity' in line and 'com.android.launcher3/' in line for line in raw.splitlines())

    def find_widget():
        m.sh('cmd', 'statusbar', 'collapse'); m.wake(); m.sh('input', 'keyevent', '3')
        directions = iter([1] * 3 + [-1] * 6)
        def scan():
            assert reviewed_focus('launcher') and launcher_resumed(), 'Actual Launcher3 focused/resumed'
            root = m.tree()
            found = [node for node in root.iter('node') if node.get('resource-id') == PKG + ':id/run_widget_root']
            if found:
                current = widget_ids()
                assert len(found) == 1 and current['runmoni'] == [review_id], 'Only the new review widget is operated on'
                return root, found[0]
            direction = next(directions, None)
            assert direction is not None, 'Review widget must be reachable within the bounded launcher page scan'
            width, height = m.dimensions()
            x1, x2 = (width * 4 // 5, width // 5) if direction == 1 else (width // 5, width * 4 // 5)
            m.sh('input', 'swipe', str(x1), str(height // 2), str(x2), str(height // 2), '350')
            return None
        return m.wait(scan, 'Only owned review widget visible on Launcher3', 60)

    def click_app(label):
        m.reveal(label)
        root = m.tree()
        nodes = [node for node in m.nodes(root, label) if node.get('package') == PKG]
        assert len(nodes) == 1, 'Unambiguous exact app action: ' + label
        m.tap_node(nodes[0], root)
        proof['actualTaps'].append({'label': label, 'bounds': list(m.rect(nodes[0])), 'package': PKG})

    def only_review_id():
        current = widget_ids()
        assert current['runmoni'] == [review_id]
        assert current['all'] == sorted(before_widgets['all'] + [review_id])
        return current

    def reveal_options():
        for _ in range(10):
            root = m.tree()
            choices = [(label, node) for label in ('러닝 옵션', '러닝 옵션 접기')
                       for node in m.nodes(root, label) if node.get('package') == PKG]
            if choices:
                assert len(choices) == 1
                label, node = choices[0]
                if label == '러닝 옵션':
                    m.tap_node(node, root)
                    proof['actualTaps'].append({'label': label, 'bounds': list(m.rect(node)), 'package': PKG})
                return
            width, height = m.dimensions()
            m.sh('input', 'swipe', str(width // 2), str(height * 7 // 10), str(width // 2), str(height * 35 // 100), '350')
        raise AssertionError('Actual running options must be reachable by scrolling')

    def pin_widget(font_scale):
        nonlocal review_id, pin_requested, current_lifecycle, last_main_pid
        assert widget_ids() == before_widgets
        assert_idle()
        m.force_stop()
        m.sh('settings', 'put', 'system', 'font_scale', str(font_scale))
        m.wait(lambda: abs(float(m.sh('settings', 'get', 'system', 'font_scale')) - font_scale) < .001,
               'Actual system font_scale set to ' + str(font_scale), 20)
        # A new Main process loads the current system font resources. Each OS
        # pin supplies the platform's own update lifecycle for a fresh widget.
        # No driver APPWIDGET_UPDATE/other private broadcast is sent.
        m.launch(); assert_idle()
        this_pid = m.pid()
        assert this_pid and this_pid != last_main_pid, 'Each placement starts a fresh actual Main process'
        last_main_pid = this_pid
        m.tap_tab('러닝'); reveal_options(); click_app('홈 화면에 러닝 위젯 추가')
        root = m.tree()
        buttons = m.nodes(root, 'ADD AUTOMATICALLY')
        assert len(buttons) == 1 and buttons[0].get('package') == 'com.android.launcher3'
        assert any('런모니' in (node.get('text', '') + node.get('content-desc', '')) for node in root.iter('node'))
        private('pin-prompt-font' + str(int(font_scale)) + '.xml', ET.tostring(root, encoding='utf-8'))
        current_lifecycle = {'systemFontScale': font_scale, 'beforeIds': widget_ids(),
                             'newReviewWidgetRemoved': False, 'freshMainProcessObserved': True,
                             'updateSource': 'Actual OS pin lifecycle; no driver widget broadcast',
                             'onUpdateCallbackTraceMeasured': False}
        proof['launcherWidgetLifecycles'].append(current_lifecycle)
        proof['actualTaps'].append({'label': 'ADD AUTOMATICALLY', 'package': 'com.android.launcher3',
                                   'bounds': list(m.rect(buttons[0])), 'systemFontScale': font_scale})
        pin_requested = True
        m.tap_node(buttons[0], root, climb=False)
        m.wait(lambda: len(widget_ids()['runmoni']) == 1, 'Actual OS pin created one Runmoni widget', 60)
        after_pin = widget_ids()
        new_ids = set(after_pin['all']) - set(before_widgets['all'])
        assert len(new_ids) == 1 and after_pin['runmoni'] == [next(iter(new_ids))]
        review_id = next(iter(new_ids))
        prior_ids = [item.get('newReviewWidgetId') for item in proof['launcherWidgetLifecycles'][:-1]]
        assert review_id not in prior_ids, 'Independent OS placements have distinct newly allocated IDs'
        only_review_id()
        current_lifecycle.update(afterPinIds=after_pin, newReviewWidgetId=review_id,
                                 actualOsPinAction='ADD AUTOMATICALLY', originalWidgetIdsPreserved=True)
        def rendered():
            root, widget = find_widget()
            children = list(widget.iter('node'))
            opens = [node for node in children if node.get('resource-id') == PKG + ':id/run_widget_open']
            desc = widget.get('content-desc', '')
            return (root, widget) if len(opens) == 1 and opens[0].get('clickable') == 'true' and '러닝 시간 —' in desc else None
        return m.wait(rendered, 'Fresh OS-pinned idle widget with an actual enabled app action', 60)

    def observe_widget(root, widget, scale):
        assert_idle(); only_review_id()
        width, height = m.dimensions()
        root_bounds = m.rect(widget)
        assert 0 <= root_bounds[0] < root_bounds[2] <= width and 0 <= root_bounds[1] < root_bounds[3] <= height
        observed = []
        for node in widget.iter('node'):
            resource = node.get('resource-id', '')
            if not resource.startswith(PKG + ':id/'):
                continue
            bounds = m.rect(node)
            if bounds[2] <= bounds[0] or bounds[3] <= bounds[1]:
                continue
            assert root_bounds[0] <= bounds[0] < bounds[2] <= root_bounds[2]
            assert root_bounds[1] <= bounds[1] < bounds[3] <= root_bounds[3]
            assert resource not in (PKG + ':id/run_widget_control', PKG + ':id/run_widget_finish'), 'Idle widget exposes no service controls'
            text = node.get('text', '')
            if resource.endswith('/run_widget_title'):
                assert text in ('런모니', '러닝 시작 준비')
            if resource.endswith('/run_widget_footer'):
                assert text in ('러닝 시작 준비', '앱에서 러닝을 시작하세요')
            observed.append({'resource': resource, 'class': node.get('class'), 'text': text,
                             'bounds': list(bounds), 'clickable': node.get('clickable') == 'true'})
        opens = [node for node in widget.iter('node') if node.get('resource-id') == PKG + ':id/run_widget_open']
        assert len(opens) == 1 and opens[0].get('enabled') == 'true' and opens[0].get('clickable') == 'true'
        desc = widget.get('content-desc', '')
        assert all(value in desc for value in ('런모니', '러닝 시작 준비', '— km', '러닝 시간 —', '현재 속도 —'))
        current_cache = cache_state()
        assert current_cache['present'] and not any(n.get('name') == 'metricsV1' for n in ET.fromstring(current_cache['raw']))
        return {'systemFontScale': scale, 'actualDisplayPixels': [width, height], 'rootWidgetBounds': list(root_bounds),
                'visibleNodes': observed, 'rootDescriptionHasOnlyIdleMetrics': True,
                'allObservedWidgetChildrenInsideRootAndDisplay': True,
                'glyphInkMeasured': False, 'textAdvanceOrMaxLinesMeasuredFromAccessibility': False,
                'fontSizeMeasuredFromAccessibility': False, 'serviceControlsAbsent': True,
                'widgetCacheMetricsAbsent': True}

    def capture_idle(scale):
        root, widget = pin_widget(scale)
        observed = observe_widget(root, widget, scale)
        assert reviewed_focus('launcher') and launcher_resumed()
        name = args.name + ('-idle-font1.png' if scale == 1.0 else '-idle-font2.png')
        m.save_png(name, 'launcher', focus_required=True)
        assert reviewed_focus('launcher') and launcher_resumed(), 'Launcher remained resumed across raw capture'
        proof['captures'][-1].update(independentOsPinPlacement=len(proof['launcherWidgetLifecycles']),
                                     sameWidgetResizingClaimed=False, sourceKind='actual Android Launcher3 AppWidget RemoteViews',
                                     source='Launcher', launcherResumedActivityVerified=True,
                                     syntheticGps=False, idleWidget=True, systemFontScale=scale,
                                     reviewWidgetId=review_id, observedBounds=observed,
                                     originalWidgetMetricsNotDisplayed=True,
                                     otherLauncherFixtureItemsMayAppear=True,
                                     rootPrivacyReviewRequiredBeforePublication=True)

    def restore_font():
        value = before_env['settings']['system']['font_scale']
        if value['present']:
            env.shell(m, 'settings', 'put', 'system', 'font_scale', value['value'])
        else:
            env.shell(m, 'settings', 'delete', 'system', 'font_scale')
        assert env.settings_map(m, 'system')['font_scale'] == value
        proof['originalFontSettingRestoredBeforeWidgetRemoval'] = True

    def remove_widget():
        nonlocal review_id
        current = widget_ids()
        if current == before_widgets:
            if current_lifecycle is not None:
                current_lifecycle['currentIdsEqualOriginal'] = True
            return
        assert pin_requested and not before_widgets['runmoni']
        own = current['runmoni']
        assert len(own) == 1 and own[0] not in before_widgets['all'] and set(before_widgets['all']).issubset(current['all'])
        if review_id is None:
            review_id = own[0]
        assert own == [review_id]
        _, widget = find_widget()
        left, top, right, bottom = m.rect(widget); width, height = m.dimensions()
        # Reuse the previously reviewed Launcher3 gesture: down on our widget
        # only, then drag into its top Remove region. We do not claim an
        # accessibility dump observed the transient drag target mid-gesture.
        target = (width // 2 + 10, 55)
        assert 0 <= target[0] < width and 0 < target[1] < height
        m.sh('input', 'draganddrop', str((left + right) // 2), str((top + bottom) // 2), str(target[0]), str(target[1]), '1500')
        m.wait(lambda: widget_ids() == before_widgets, 'Original widget service IDs after owned Launcher drag Remove', 60)
        assert current_lifecycle is not None
        current_lifecycle.update(newReviewWidgetRemoved=True,
            newReviewWidgetId=review_id, removalMethod='Actual Launcher3 drag from owned widget into top Remove region',
            removalTargetPixels=list(target), transientRemoveTargetObservedByAccessibility=False,
            originalRunmoniWidgetIdsPreserved=True, originalOtherWidgetIdsPreserved=True, afterRemovalIds=widget_ids())

    try:
        stage('guards')
        assert m.adb('emu', 'avd', 'name').decode().splitlines()[0] == 'Runmoni_Cloud_29'
        assert m.sh('getprop', 'ro.build.version.sdk') == '29'
        assert not m.pid() and assert_idle(), 'No app process/recorder before backup'
        binary_raw = (E / proof['binaryReference']).read_bytes()
        assert sha(binary_raw) == EXPECTED_BINARY_SHA256
        binary = json.loads(binary_raw)
        assert binary['version'] == '0.4.1-dev' and binary['versionCode'] == 23 and binary['buildSucceeded']
        assert binary['signing'] == 'local TEST debug, not retained release signing'
        proof['binaryReferenceSha256'] = sha(binary_raw)
        proof['sourceMapsBefore'] = env.source_maps(binary)
        proof['installed'] = []
        assert len(binary['binaries']) == 2
        for package, expected in zip([PKG, PKG + '.test'], binary['binaries']):
            paths = m.sh('pm', 'path', package).splitlines()
            assert len(paths) == 1 and paths[0].startswith('package:')
            digest = m.sh('sha256sum', paths[0].removeprefix('package:')).split()[0]
            assert digest == expected['sha256']
            proof['installed'].append({'package': package, 'sha256': digest})
        cfg = (R / 'app/build/generated/source/buildConfig/debug/com/runmoni/app/BuildConfig.java').read_text()
        assert 'boolean FIREBASE_ENABLED = false;' in cfg
        before_env = env.environment_snapshot(m)
        proof['privateEnvironmentBefore'] = private('environment-before.json', (json.dumps(before_env) + '\n').encode())
        old_prefs = m.prefs(); ET.fromstring(old_prefs)
        old_profile = env.profile_from_xml(old_prefs)
        proof['privatePreferencesBefore'] = private('preferences-before.xml', old_prefs)
        old_widget = cache_state()
        proof['privateWidgetCacheBefore'] = private('widget-cache-before.xml', old_widget['raw'])
        proof['originalWidgetCacheExists'] = old_widget['present']
        proof['originalWidgetCacheWellFormedXml'] = old_widget['wellFormedXml']
        proof['originalCacheHandledAsOpaqueBytes'] = True
        proof['originalWidgetCacheSha256'] = sha(old_widget['raw'])
        proof['originalPreferencesSha256'] = sha(old_prefs)
        baseline = m.snapshot('before')
        assert all(row['finishedAtMillis'] > 0 for row in baseline['runs']), 'Refuse unfinished/recoverable runs'
        proof['originalDatabaseRowsSha256'] = m.canonical(baseline)
        before_widgets = widget_ids()
        proof['privateWidgetIdsBefore'] = private('widget-ids-before.json', (json.dumps(before_widgets) + '\n').encode())
        assert not before_widgets['runmoni'], 'Never replace or remove an existing Runmoni widget'
        proof['launcherWidgetIdsBefore'] = before_widgets
        can_restore = True
        fixture = ET.fromstring(old_prefs)
        for kind, key, value in [('string', 'communityBackend', 'firebase'), ('string', 'appEntryChoiceV1', 'guest'),
                                 ('boolean', 'communityGuest', 'true'), ('boolean', 'publicLocation', 'false'),
                                 ('boolean', 'voiceGuidance', 'false'), ('boolean', 'autoPause', 'false')]:
            m.set_pref(fixture, kind, key, value)
        local = next((node for node in fixture if node.get('name') == 'localProfile'), None)
        if local is not None:
            profile = json.loads(local.text); profile['name'] = '검토 러너'; local.text = json.dumps(profile, ensure_ascii=False)
        m.write_prefs(ET.tostring(fixture, encoding='utf-8', xml_declaration=True))
        write_widget_cache(b'<?xml version="1.0" encoding="utf-8" standalone="yes" ?>\n<map />\n', True)
        proof['temporaryWidgetCacheContainsNoMetrics'] = True
        for permission in ['android.permission.ACCESS_FINE_LOCATION', 'android.permission.ACCESS_COARSE_LOCATION']:
            m.sh('pm', 'revoke', PKG, permission)
        proof['previewLocationPermissionsTemporarilyRevoked'] = True
        env.viewport(m, False)
        for key in ['window_animation_scale', 'transition_animation_scale', 'animator_duration_scale']:
            m.sh('settings', 'put', 'global', key, '0')
        for scale in (1.0, 2.0):
            stage('new-idle-os-pin-font' + str(int(scale)))
            capture_idle(scale)
            assert current_lifecycle is not None
            current_lifecycle['captureCompleted'] = True
            # Return the original font before the familiar Launcher3 Remove
            # gesture; this does not assert live resizing of that widget.
            restore_font()
            remove_widget()
            assert widget_ids() == before_widgets
            assert current_lifecycle.get('newReviewWidgetRemoved') is True
            m.force_stop()
            assert_idle()
            review_id, pin_requested, current_lifecycle = None, False, None
        assert len(proof['launcherWidgetLifecycles']) == 2
        assert all(item['newReviewWidgetRemoved'] for item in proof['launcherWidgetLifecycles'])
        assert len(proof['captures']) == 2 and all(item['kind'] == 'launcher' for item in proof['captures'])
        assert_idle()
        proof['sourceMapsAfter'] = env.source_maps(binary)
        proof['passed'] = True
        stage('captures-passed-awaiting-restoration')
    except BaseException as failure:
        proof['passed'] = False
        proof['failureType'] = type(failure).__name__
        proof['failure'] = str(failure) if isinstance(failure, AssertionError) else 'Inspect private local diagnostics'
        if can_restore:
            try: private('failure.png', m.adb('exec-out', 'screencap', '-p'))
            except Exception: pass
        stage('failed-before-finally')
    finally:
        def attempt(label, action):
            result = {'step': label, 'attempted': True, 'passed': False, 'startedAtUtc': env.now()}
            proof.setdefault('restorationAttempts', []).append(result)
            try:
                action(); result['passed'] = True
            except BaseException as failure:
                result['failureType'] = type(failure).__name__
                proof['passed'] = False
            finally:
                result['finishedAtUtc'] = env.now(); stage('restoring-' + label)
        def restore_database_observation():
            assert not m.pid()
            after = m.snapshot('after')
            proof['databaseExactlyRestored'] = m.canonical(after) == m.canonical(baseline)
            assert proof['databaseExactlyRestored'], 'Original DB rows must stay exact; no DB is copied back'
        def restore_preferences():
            assert not m.pid()
            m.write_prefs(old_prefs)
            proof['preferencesRestoredExactly'] = m.prefs() == old_prefs
            proof['profileRestoredExactly'] = env.local_profile(m) == old_profile
            assert proof['preferencesRestoredExactly'] and proof['profileRestoredExactly']
        def restore_cache():
            assert widget_ids() == before_widgets, 'Do not expose original private metrics beneath a surviving review widget'
            write_widget_cache(old_widget['raw'], old_widget['present'])
            observed = cache_state()
            proof['widgetCacheExistsExactlyRestored'] = observed['present'] is old_widget['present']
            proof['widgetCacheExactlyRestored'] = observed['raw'] == old_widget['raw']
            assert proof['widgetCacheExistsExactlyRestored'] and proof['widgetCacheExactlyRestored']
        def verify_ids():
            proof['launcherWidgetIdsExactlyRestored'] = widget_ids() == before_widgets
            assert proof['launcherWidgetIdsExactlyRestored']
        def restore_environment():
            equal, observed, failures = env.restore_environment(m, before_env)
            proof['controlledEnvironmentExactlyRestored'] = equal and not failures
            proof['restorationCommandFailures'] = failures
            proof['privateEnvironmentAfter'] = private('environment-after.json', (json.dumps(observed) + '\n').encode())
            assert proof['controlledEnvironmentExactlyRestored']
        if can_restore:
            attempt('original-font-before-removal', restore_font)
            attempt('remove-only-new-widget', remove_widget)
            attempt('force-stop-after-removal', m.force_stop)
            attempt('verify-original-db-rows', restore_database_observation)
            attempt('restore-original-preferences', restore_preferences)
            attempt('restore-original-widget-cache', restore_cache)
            attempt('verify-original-widget-ids', verify_ids)
        if before_env is not None and can_restore:
            attempt('restore-controlled-environment', restore_environment)
        if binary is not None:
            try: proof['sourceMapsAfterRestoration'] = env.source_maps(binary)
            except BaseException as failure:
                proof['sourceMapRestorationObservationFailureType'] = type(failure).__name__; proof['passed'] = False
        proof['driverSourceUnchanged'] = sha(Path(__file__).read_bytes()) == proof['executedDriverSha256']
        proof['observerHelperSourceUnchanged'] = sha(H.read_bytes()) == proof['observerHelperSha256']
        proof['environmentHelperSourceUnchanged'] = sha(ENV.read_bytes()) == proof['environmentHelperSha256']
        required = ['databaseExactlyRestored', 'preferencesRestoredExactly', 'profileRestoredExactly',
                    'widgetCacheExactlyRestored', 'widgetCacheExistsExactlyRestored', 'launcherWidgetIdsExactlyRestored',
                    'controlledEnvironmentExactlyRestored', 'driverSourceUnchanged', 'observerHelperSourceUnchanged',
                    'environmentHelperSourceUnchanged', 'originalFontSettingRestoredBeforeWidgetRemoval']
        proof['passed'] = bool(proof['passed'] and all(proof.get(key) is True for key in required)
                               and len(proof['launcherWidgetLifecycles']) == 2
                               and all(item.get('newReviewWidgetRemoved') for item in proof['launcherWidgetLifecycles'])
                               and all(step['passed'] for step in proof.get('restorationAttempts', [])))
        proof['completed'], proof['finishedAtUtc'] = True, env.now()
        stage('complete-passed' if proof['passed'] else 'complete-failed')
    return 0 if proof['passed'] else 1


if __name__ == '__main__':
    raise SystemExit(main())
