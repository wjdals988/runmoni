#!/usr/bin/env python3
"""Sequential demo SDK and real Android UI checks on the owned cloud AVD only."""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[2]
ADB = [str(ROOT / 'work/cloud-android/sdk/platform-tools/adb'), '-s', 'emulator-5556']


def shell(*args):
    return subprocess.check_output(ADB + ['shell', *args], timeout=45).decode().strip()


def instrument(classes, path):
    result = subprocess.run(ADB + ['shell', 'am', 'instrument', '-w', '-r', '-e', 'class', classes,
        'com.runmoni.app.test/androidx.test.runner.AndroidJUnitRunner'], capture_output=True, timeout=900)
    raw = result.stdout.decode(errors='replace') + result.stderr.decode(errors='replace')
    path.write_text(raw)
    count = re.search(r'OK \((\d+) tests?\)', raw)
    if result.returncode or not count or 'FAILURES!!!' in raw:
        raise RuntimeError('Inspect the demo-only Android report.')
    elapsed = re.search(r'Time: ([0-9.]+)', raw)
    return {'tests': int(count[1]), 'failures': 0, 'seconds': float(elapsed[1]) if elapsed else None}


def captures(directory, prefix, cache='design-review'):
    names = shell('run-as', 'com.runmoni.app', 'ls', 'cache/' + cache).splitlines()
    copied = []
    for name in names:
        if not re.fullmatch(r'[A-Za-z0-9_.-]+\.png', name):
            continue
        raw = subprocess.check_output(ADB + ['exec-out', 'run-as', 'com.runmoni.app', 'cat', 'cache/' + cache + '/' + name], timeout=30)
        if not raw.startswith(b'\x89PNG') or len(raw) > 4 * 1024 * 1024:
            raise RuntimeError('Invalid Android capture.')
        (directory / (prefix + '-' + name)).write_bytes(raw)
        copied.append(prefix + '-' + name)
    return copied


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, default=ROOT / 'work/crew-ux/android')
    parser.add_argument('--ui-only', action='store_true', help='Skip the separately verified demo SDK phase')
    selection = parser.add_mutually_exclusive_group()
    selection.add_argument('--focused', action='store_true', help='Rerun changed hierarchy cases and native large-text cases')
    selection.add_argument('--real-app', action='store_true', help='Requires owned localhost:8789 API and adb reverse; run MainActivity, hierarchy and native large text')
    parser.add_argument('--main-only', action='store_true', help='With --real-app, rerun MainActivity captures and native profile; hierarchy was checked separately')
    args = parser.parse_args()
    if args.main_only and not args.real_app:
        parser.error('--main-only requires --real-app')
    for key, expected in [('GCLOUD_PROJECT', 'demo-runmoni'), ('FIRESTORE_EMULATOR_HOST', '127.0.0.1:8080'),
                          ('FIREBASE_AUTH_EMULATOR_HOST', '127.0.0.1:9099')]:
        if not args.ui_only and os.environ.get(key) != expected:
            raise RuntimeError('Use local demo emulators; no production credentials.')
    subprocess.run(['python3', str(ROOT / 'scripts/cloud/avatar-review.py'), 'status'], check=True)
    args.out.mkdir(parents=True, exist_ok=True)
    density = shell('wm', 'density')
    override = re.search(r'Override density: (\d+)', density)
    font = shell('settings', 'get', 'system', 'font_scale')
    animations = {k: shell('settings', 'get', 'global', k) for k in
                  ['window_animation_scale', 'transition_animation_scale', 'animator_duration_scale']}
    proof = {'project': 'ui-fixtures' if args.ui_only else 'demo-runmoni', 'realAndroidSdk': True,
             'firebaseSdkExecutedThisRun': not args.ui_only, 'mainActivityLoopbackRequested': args.real_app,
             'physicalDeviceVerified': False,
             'realGoogleLoginVerified': False, 'productionFirebaseModified': False,
             'screenshotsRequireVisualInspection': True, 'phases': {}}
    failed = False

    def clear_captures(cache='design-review'):
        shell('run-as', 'com.runmoni.app', 'mkdir', '-p', 'cache/' + cache)
        names = [name for name in shell('run-as', 'com.runmoni.app', 'ls', 'cache/' + cache).splitlines()
                 if re.fullmatch(r'[A-Za-z0-9_.-]+\.png', name)]
        if names:
            shell('run-as', 'com.runmoni.app', 'rm', '-f', *['cache/' + cache + '/' + name for name in names])

    def phase(name, classes):
        nonlocal failed
        print('Checking ' + name, flush=True)
        try:
            proof['phases'][name] = instrument(classes, args.out / (name + '.txt'))
        except Exception as error:
            proof['phases'][name] = {'failed': True, 'errorType': type(error).__name__}
            failed = True
        print(json.dumps(proof['phases'][name]), flush=True)

    try:
        for key in animations:
            shell('settings', 'put', 'global', key, '0')
        if not args.ui_only:
            phase('firebase-sdk', 'com.runmoni.app.data.FirebaseCommunityTest')
        # Only automation PNGs in this owned debug application's cache are reset.
        clear_captures()
        native = [
            'com.runmoni.app.ui.HierarchyClosetUxTest', 'com.runmoni.app.ui.CommunityRoomsTest',
            'com.runmoni.app.ui.ConsumerClosetTest', 'com.runmoni.app.ui.ApprovedAvatarRenderingTest',
            'com.runmoni.app.data.AvatarGenderPersistenceTest', 'com.runmoni.app.ui.MiniMiLoadingTest',
            'com.runmoni.app.ui.RunmoniDesignTest']
        if args.real_app:
            clear_captures('room-review')
            native = ['com.runmoni.app.integration.CommunityApiFlowTest'] + ([] if args.main_only else [native[0]])
        phase('native-ui', ','.join(native[:2] if args.focused else native))
        proof['nativeCaptures'] = captures(args.out, 'native')
        if args.real_app:
            proof['mainActivityLoopbackCaptures'] = captures(args.out, 'mainactivity-loopback', 'room-review')
        shell('wm', 'density', '240')
        shell('settings', 'put', 'system', 'font_scale', '2.0')
        clear_captures()
        phase('native-320dp-font2', 'com.runmoni.app.ui.HierarchyClosetLargeTextUxTest' +
              ('' if args.real_app else ',com.runmoni.app.ui.ConsumerClosetTest'))
        proof['largeTextCaptures'] = captures(args.out, '320dp-font2')
    finally:
        shell('wm', 'density', override[1] if override else 'reset')
        if re.fullmatch(r'[0-9.]+', font):
            shell('settings', 'put', 'system', 'font_scale', font)
        else:
            shell('settings', 'delete', 'system', 'font_scale')
        for key, value in animations.items():
            shell('settings', 'delete', 'global', key) if value == 'null' else shell('settings', 'put', 'global', key, value)
        proof['restoredSettings'] = {'fontScale': shell('settings', 'get', 'system', 'font_scale'), 'density': shell('wm', 'density')}
        (args.out / 'android-verification.json').write_text(json.dumps(proof, indent=2) + '\n')
    return 1 if failed else 0


if __name__ == '__main__':
    raise SystemExit(main())
