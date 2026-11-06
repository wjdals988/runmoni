#!/usr/bin/env python3
"""Root-only recovery helper; no boot, installation, authentication or direct DB writes.

Default mode only audits local private backups. After the root starts the owned
AVD, --inspect-device reads a quiescent private DB copy. Cleanup requires the
SHA256 of the single candidate ID from that inspection. Historical settings that
host5 never serialized remain explicitly unknown; recovery-entry settings are
saved privately before UI interaction and restored separately.
"""
import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import shutil
import sqlite3
import tempfile
import xml.etree.ElementTree as ET

R = next(p for p in Path(__file__).resolve().parents if (p / 'app/build.gradle.kts').exists())
W = R / 'work/background-running'
E = R / 'docs/app-review/2026-11-06-background-running'
NAME = 'host5-recovery1'


def guard(condition, label):
    if not condition:
        raise AssertionError(label)


def sha(data):
    return hashlib.sha256(data).hexdigest()


def canonical(data):
    return sha(json.dumps(data, sort_keys=True, separators=(',', ':'), ensure_ascii=False).encode())


def private_json(path, data):
    guard(not path.exists(), 'Recovery evidence must be fresh; preserve the prior attempt')
    with path.open('x') as output:
        json.dump(data, output, ensure_ascii=False, indent=2)
        output.write('\n')
    path.chmod(0o600)


def local_audit():
    backups = []
    for name in ['pre-install-private.xml', 'host5-private-before.xml']:
        path = W / name
        raw = path.read_bytes()
        guard(path.stat().st_mode & 0o777 == 0o600, 'Private XML must have0600 permissions')
        ET.fromstring(raw)
        backups.append({'file': name, 'sha256': sha(raw), 'bytes': len(raw), 'mode': '0600'})
    guard(backups[0]['sha256'] == backups[1]['sha256'], 'Pre-install and host5-before XML match exactly')
    source = W / 'host5-private-db-1-baseline'
    # Work only on a temporary copy: even SQLite WAL-index housekeeping cannot
    # alter the archived private baseline files.
    with tempfile.TemporaryDirectory(prefix='runmoni-host5-local-audit-') as temporary:
        directory = Path(temporary)
        for name in ['runmoni-runs.db', 'runmoni-runs.db-wal', 'runmoni-runs.db-shm']:
            path = source / name
            if path.exists():
                guard(path.stat().st_mode & 0o777 == 0o600, 'Private SQLite snapshots must have0600 permissions')
                shutil.copy2(path, directory / name)
        connection = sqlite3.connect((directory / 'runmoni-runs.db').as_uri() + '?mode=ro', uri=True)
        connection.row_factory = sqlite3.Row
        try:
            connection.execute('PRAGMA query_only=ON')
            baseline = {'runs': [dict(row) for row in connection.execute('SELECT * FROM runs ORDER BY id')],
                        'points': [dict(row) for row in connection.execute('SELECT * FROM track_points ORDER BY runId, sequence')]}
        finally:
            connection.close()
    old = json.loads((E / 'host4.json').read_text())['databaseBaseline']
    guard(len(baseline['runs']) == old['runCount'] and len(baseline['points']) == old['pointCount'] and
          canonical(baseline) == old['contentSha256'], 'host5 private baseline agrees with the previously restored baseline')
    guard(not baseline['runs'] and not baseline['points'], 'This helper is restricted to the verified empty host5 baseline')
    driver = W / 'host-review5.py'
    guard(sha(driver.read_bytes()) == sha((E / 'host5-executed-driver.py').read_bytes()), 'Use the exact archived host5 helper implementation')
    old_result = json.loads((E / 'host5.json').read_text()) if (E / 'host5.json').exists() else None
    guard(not old_result or not old_result.get('passed'), 'Do not reinterpret an already successful host5 record')
    return baseline, {'xmlBackups': backups, 'baseline': {'runCount': 0, 'pointCount': 0, 'contentSha256': canonical(baseline)},
                      'host5FinallyJsonExists': old_result is not None, 'host5FeatureVerificationComplete': False,
                      'historicalSettingsReferenceAvailable': False,
                      'historicalSettingsNotSerialized': ['animation scales', 'location mode', 'runtime permission grants and flags'],
                      'viewportNote': 'Host5 stopped before any native font/density mutation; this does not recover the lost settings snapshot.',
                      'pauseTapConcern': 'Generic click-ancestor climbing can target a notification container; no missed-tap/product cause was established.'}


def load_driver():
    spec = importlib.util.spec_from_file_location('interrupted_host5_helpers', W / 'host-review5.py')
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    module.E = W  #Any unexpected SystemUI/diagnostic images remain private WORK artifacts.
    module.snapshot_counter = 20000
    return module


def device_guard(module):
    guard(module.adb('emu', 'avd', 'name').decode().splitlines()[0] == 'Runmoni_Cloud_29', 'Owned cloud AVD only')
    guard(module.sh('getprop', 'ro.build.version.sdk') == '29' and module.sh('am', 'get-current-user') == '0', 'Owned API29 primary user')
    binary = json.loads((E / 'binary-build2.json').read_text())
    guard(len(binary['binaries']) == 2, 'Frozen app/test pair reference')
    for package, expected in zip([module.PKG, module.PKG + '.test'], binary['binaries']):
        path = module.sh('pm', 'path', package).removeprefix('package:')
        guard(path.startswith('/data/app/') and '\n' not in path, 'One installed base APK')
        guard(module.sh('sha256sum', path).split()[0] == expected['sha256'], 'Installed pair must still match host5 build2; do not reinstall for cleanup')
    guard(not module.pid() and module.dump() is None, 'Recovery inspection starts from a force-stopped app; never take over an active recorder')


def product_body_reveal(module, label):
    for _ in range(10):
        root = module.tree()
        if module.nodes(root, label):
            return
        scrolls = [node for node in root.iter('node') if node.get('scrollable') == 'true' and
                   node.get('package') == module.PKG and module.rect(node)[3] - module.rect(node)[1] > 80]
        guard(scrolls, 'No safe product scroll body; leave the review row for explicit cleanup')
        scrolls.sort(key=lambda node: module.rect(node)[3] - module.rect(node)[1], reverse=True)
        left, top, right, bottom = module.rect(scrolls[0])
        width, height = module.dimensions()
        guard(0 <= left < right <= width and 0 <= top < bottom <= height, 'Scroll inside the actual product body, never the dialog footer/OS navigation')
        x = left + max(8, (right - left) // 12)
        module.sh('input', 'swipe', str(x), str(bottom - 16), str(x), str(top + 16), '450')
    raise AssertionError('Own delete action remains unreachable; no direct database deletion is allowed')


def recover(args, baseline, audit):
    module = load_driver()
    mode = 'inspect' if args.cleanup_owned_run is None else 'cleanup'
    module.NAME = NAME + '-' + mode
    module.baseline = baseline
    device_guard(module)
    current = module.snapshot('quiescent-before')
    module.preserved(current)
    candidates = current['runs']
    guard(len(candidates) <= 1, 'More than one new row requires independent ownership review')
    candidate_hashes = [sha(row['id'].encode()) for row in candidates]
    result = dict(audit, scope='Interrupted host5 cleanup only; no feature-test success inferred',
                  candidateRunIdSha256=candidate_hashes, candidateCount=len(candidates), cleanupExecuted=False,
                  fullPreHost5SettingsRestoredExactly=False)
    private_json(W / (NAME + '-' + mode + '-private-candidate-manifest.json'), {'rows': candidates, 'points': current['points']})
    if args.cleanup_owned_run is None:
        print(json.dumps(result, ensure_ascii=False, indent=2))
        return
    guard(args.cleanup_owned_run == (candidate_hashes[0] if candidate_hashes else 'none'), 'Explicit candidate fingerprint must match the quiescent inspection')
    entry_settings = module.settings_snapshot()
    private_json(W / (NAME + '-private-settings-at-entry.json'), entry_settings)
    module.device_zone = module.ZoneInfo(module.sh('getprop', 'persist.sys.timezone') or 'UTC')
    module.creation_windows = [(row['startedAtMillis'], row['startedAtMillis']) for row in candidates]
    module.reveal = lambda label: product_body_reveal(module, label)
    restore = (W / 'pre-install-private.xml').read_bytes()
    try:
        # Use only a safe local review configuration while the existing product
        # UI finishes/deletes the explicitly reviewed row. No nickname, wallet,
        # ownership, auth preference or database content is rewritten here.
        fixture = ET.fromstring((W / 'host5-private-before.xml').read_bytes())
        for kind, key, value in [('string', 'communityBackend', 'firebase'), ('string', 'apiUrl', 'http://127.0.0.1:8787'),
                                 ('string', 'appEntryChoiceV1', 'guest'), ('boolean', 'communityGuest', 'true'),
                                 ('boolean', 'publicLocation', 'false'), ('boolean', 'voiceGuidance', 'false'), ('boolean', 'autoPause', 'false')]:
            module.set_pref(fixture, kind, key, value)
        module.write_prefs(ET.tostring(fixture, encoding='utf-8', xml_declaration=True))
        module.cleanup_own_runs()
        result.update(cleanupExecuted=True, ownReviewRowsRemoved=True, databaseAfterCleanup=module.P['databaseAfterCleanup'])
    except BaseException as failure:
        result.update(cleanupFailureType=type(failure).__name__, ownReviewRowsRemoved=False)
    finally:
        try:
            module.force_stop()
            module.write_prefs(restore)
            result['preInstallPreferencesRestoredExactly'] = module.prefs() == restore
        except BaseException as failure:
            result.update(preInstallPreferencesRestoredExactly=False, preferenceRestoreFailureType=type(failure).__name__)
        try:
            module.restore_settings(entry_settings)
            result['recoveryEntrySettingsRestoredExactly'] = True
        except BaseException as failure:
            result.update(recoveryEntrySettingsRestoredExactly=False, settingsRestoreFailureType=type(failure).__name__)
        try:
            module.force_stop()
            remaining = module.snapshot('quiescent-after')
            module.preserved(remaining)
            result['remainingReviewRunIdSha256'] = [sha(row['id'].encode()) for row in remaining['runs']]
            result['databaseBaselineRestoredExactly'] = canonical(remaining) == canonical(baseline)
        except BaseException as failure:
            result['finalDatabaseInspectionFailureType'] = type(failure).__name__
        result['databaseAndXmlCleanupSucceeded'] = bool(result.get('ownReviewRowsRemoved') and
            result.get('databaseBaselineRestoredExactly') and result.get('preInstallPreferencesRestoredExactly'))
        # Recovery-entry settings equality must not be substituted for the
        # pre-host5 snapshot that was lost with the interrupted process.
        private_json(W / (NAME + '-result.json'), result)
        print(json.dumps(result, ensure_ascii=False, indent=2))
    guard(result['databaseAndXmlCleanupSucceeded'], 'Cleanup incomplete; retain remaining review-row metadata and never restore an old DB over it')


def main():
    global NAME
    os.umask(0o077)
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--inspect-device', action='store_true', help='Root only, after owned AVD boot; no app launch')
    parser.add_argument('--cleanup-owned-run', metavar='ID_SHA256_OR_NONE', help='Root only; exact candidate fingerprint from inspection')
    parser.add_argument('--attempt-name', default=NAME, help='Fresh immutable WORK prefix, e.g.host5-recovery2 for a retry')
    args = parser.parse_args()
    guard(re.fullmatch(r'host5-recovery\d+', args.attempt_name) is not None, 'Restricted recovery-attempt prefix')
    NAME = args.attempt_name
    baseline, audit = local_audit()
    if not args.inspect_device and args.cleanup_owned_run is None:
        print(json.dumps(audit, ensure_ascii=False, indent=2))
        return
    recover(args, baseline, audit)


if __name__ == '__main__':
    main()
