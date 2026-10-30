#!/usr/bin/env python3
"""Run demo Firebase SDK/UI checks on the owned cloud AVD; never a real Google account or phone."""
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


def instrument(classes, output):
    result = subprocess.run(ADB + ['shell', 'am', 'instrument', '-w', '-r', '-e', 'class', classes,
        'com.runmoni.app.test/androidx.test.runner.AndroidJUnitRunner'], capture_output=True, timeout=300)
    raw = result.stdout.decode(errors='replace') + result.stderr.decode(errors='replace')
    output.write_text(raw)
    count = re.search(r'OK \((\d+) tests?\)', raw)
    if result.returncode or not count or 'FAILURES!!!' in raw:
        raise RuntimeError('Android test failed; inspect the demo-only instrumentation report.')
    elapsed = re.search(r'Time: ([0-9.]+)', raw)
    return {'tests': int(count[1]), 'failures': 0, 'seconds': float(elapsed[1]) if elapsed else None}


def captures(output, prefix):
    # Missing config and disconnect warning; do not present a ready-state fixture as live service proof.
    for name in ['device-checks-firebase-missing-fixture', 'firebase-anonymous-disconnect-confirmation']:
        raw = subprocess.check_output(ADB + ['exec-out', 'run-as', 'com.runmoni.app', 'cat', 'cache/design-review/' + name + '.png'], timeout=30)
        if not raw.startswith(b'\x89PNG') or len(raw) > 1048576:
            raise RuntimeError('Android screenshot is invalid.')
        (output / f'{prefix}-{name}.png').write_bytes(raw)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, default=ROOT / 'docs/app-review/2026-10-30-firebase')
    args = parser.parse_args()
    for name, expected in [('GCLOUD_PROJECT', 'demo-runmoni'), ('FIRESTORE_EMULATOR_HOST', '127.0.0.1:8080'),
                           ('FIREBASE_AUTH_EMULATOR_HOST', '127.0.0.1:9099')]:
        if os.environ.get(name) != expected:
            raise RuntimeError('Start this helper through the local demo Firebase emulators only.')
    subprocess.run(['python3', str(ROOT / 'scripts/cloud/avatar-review.py'), 'status'], check=True)
    subprocess.run(ADB + ['shell', 'run-as', 'com.runmoni.app', 'true'], check=True)
    args.out.mkdir(parents=True, exist_ok=True)
    font = shell('settings', 'get', 'system', 'font_scale')
    density = shell('wm', 'density')
    override = re.search(r'Override density: (\d+)', density)
    animationKeys = ['window_animation_scale', 'transition_animation_scale', 'animator_duration_scale']
    animations = {key: shell('settings', 'get', 'global', key) for key in animationKeys}
    proof = {'project': 'demo-runmoni', 'realAndroidSdk': True, 'physicalDeviceVerified': False,
             'realGoogleLoginVerified': False, 'productionFirebaseModified': False}
    try:
        for key in animationKeys:
            shell('settings', 'put', 'global', key, '0')
        print('Checking current room/summary rules before Android clients', flush=True)
        rules = subprocess.run(['npm', 'run', 'test:rules'], cwd=ROOT / 'firebase', capture_output=True, timeout=120)
        rule_log = rules.stdout.decode(errors='replace') + rules.stderr.decode(errors='replace')
        (args.out / 'rules-tests.txt').write_text(rule_log)
        counts = {key: int(re.search(r'ℹ '+key+r' (\d+)', rule_log)[1]) if re.search(r'ℹ '+key+r' (\d+)', rule_log) else None
                  for key in ['tests', 'pass', 'fail', 'cancelled', 'skipped']}
        proof['rules'] = counts
        print(json.dumps(counts), flush=True)
        if rules.returncode or not counts['tests'] or counts['fail'] != 0:
            raise RuntimeError('Demo Firestore rule tests failed.')
        print('Checking Android Firebase SDK against demo Auth/Firestore', flush=True)
        proof['sdk'] = instrument('com.runmoni.app.data.FirebaseCommunityTest', args.out / 'android-firebase-sdk.txt')
        print(json.dumps(proof['sdk']), flush=True)
        print('Checking native Android connection UI and unauthenticated HTTP fixtures', flush=True)
        proof['native'] = instrument('com.runmoni.app.ui.DeviceChecksTest,com.runmoni.app.data.RoomConnectionCheckTest', args.out / 'android-native.txt')
        captures(args.out, 'native')
        print(json.dumps(proof['native']), flush=True)
        shell('wm', 'density', '240')
        shell('settings', 'put', 'system', 'font_scale', '2.0')
        proof['width320Font2'] = instrument('com.runmoni.app.ui.DeviceChecksTest', args.out / 'android-320dp-font2.txt')
        captures(args.out, '320dp-font2')
        print(json.dumps(proof['width320Font2']), flush=True)
    finally:
        shell('wm', 'density', override[1] if override else 'reset')
        shell('settings', 'put', 'system', 'font_scale', font)
        for key, value in animations.items():
            shell('settings', 'put', 'global', key, value)
        proof['restoredSettings'] = {'fontScale': shell('settings', 'get', 'system', 'font_scale'), 'density': shell('wm', 'density')}
        (args.out / 'android-verification.json').write_text(json.dumps(proof, indent=2) + '\n')


if __name__ == '__main__':
    main()
