"""Scoped widget environment functions; import performs no Android operation.

Retains only reviewed backup/restore/source-map/view functions from the previous
collector. No old phase counts, binary references, version, proof or success is
imported. The new driver pins this exact helper source via a Root-supplied SHA.
"""
import datetime
import hashlib
import json
import os
from pathlib import Path
import re
import shlex
import time
import xml.etree.ElementTree as ET

R = Path('/workspace/runmoni')
W = R / 'work/companion-redesign'
E = R / 'docs/app-review/2026-11-15-companion'
MAPS = ('productSources', 'testSources', 'resources', 'rendererAssets', 'approvedSourceCopies', 'configurationSources')
CONTROLLED_SETTINGS = {
    'system': ('font_scale', 'accelerometer_rotation', 'user_rotation'),
    'global': ('window_animation_scale', 'transition_animation_scale', 'animator_duration_scale', 'display_size_forced'),
    'secure': ('display_density_forced', 'show_ime_with_hard_keyboard', 'ui_night_mode', 'location_mode', 'location_providers_allowed'),
}

def guard(value, label):
    if not value:
        raise AssertionError(label)

def sha(raw):
    return hashlib.sha256(raw).hexdigest()

def now():
    return datetime.datetime.now(datetime.timezone.utc).isoformat()

def exclusive_bytes(path, raw, private=False):
    with path.open('xb') as output:
        output.write(raw)
        output.flush()
        os.fsync(output.fileno())
    if private:
        path.chmod(0o600)
        guard(path.stat().st_mode & 0o777 == 0o600, 'Private backup permission is exactly0600')
    descriptor = os.open(path.parent, os.O_RDONLY | os.O_DIRECTORY)
    try:
        os.fsync(descriptor)
    finally:
        os.close(descriptor)

def atomic_json(path, data):
    temporary = path.with_suffix(path.suffix + '.tmp')
    with temporary.open('w') as output:
        json.dump(data, output, ensure_ascii=False, indent=2)
        output.write('\n')
        output.flush()
        os.fsync(output.fileno())
    os.replace(temporary, path)
    descriptor = os.open(path.parent, os.O_RDONLY | os.O_DIRECTORY)
    try:
        os.fsync(descriptor)
    finally:
        os.close(descriptor)

def shell(module, *words):
    #adb shell joins arguments remotely; quote every value, including settings
    #strings, instead of treating JSON escaping as shell escaping.
    return module.adb('shell', shlex.join([str(word) for word in words])).decode('utf-8', 'replace').strip()

def settings_map(module, namespace):
    guard(namespace in CONTROLLED_SETTINGS, 'Only owned settings namespaces are collected')
    #List is used only to distinguish an absent key from a stored literal null.
    #Unrelated values are never persisted, logged, compared, or rolled back.
    raw = module.adb('shell', 'settings', 'list', namespace).decode('utf-8', 'strict')
    observed_keys = [line.partition('=')[0] for line in raw.splitlines() if '=' in line]
    entries = {}
    for key in CONTROLLED_SETTINGS[namespace]:
        guard(observed_keys.count(key) <= 1, 'Owned settings keys are unambiguous')
        present = key in observed_keys
        value = None
        if present:
            value = module.adb('shell', 'settings', 'get', namespace, key).decode('utf-8', 'strict')
            value = value.removesuffix('\r\n') if value.endswith('\r\n') else value.removesuffix('\n')
        entries[key] = {'present': present, 'value': value}
    return entries

def environment_restoration_scope():
    return {'controlledSettingsOnly': True, 'fullOsSettingsRestorationClaimed': False,
            'settingsKeys': {namespace: list(keys) for namespace, keys in CONTROLLED_SETTINGS.items()},
            'noUnrelatedSettingsRolledBack': True, 'unrelatedOsAutomaticChangesComparedOrReverted': False,
            'windowManager': ['size', 'density'], 'uiModeManagerNightMode': True, 'allOurPackageRuntimePermissions': 'com.runmoni.app',
            'runtimePermissionGrantAndFlags': True, 'power': 'Observed Awake/Asleep wakefulness only'}

def permissions(module):
    raw = module.sh('dumpsys', 'package', module.PKG)
    values = {}
    for key, granted, flags in re.findall(r'^\s+([A-Za-z0-9_.]+): granted=(true|false), flags=\[([^\]]*)\]', raw, re.M):
        state = {'granted': granted == 'true', 'flags': sorted(flag.strip() for flag in flags.split('|') if flag.strip())}
        guard(key not in values or values[key] == state, 'Runtime permission state must be unambiguous')
        values[key] = state
    guard(values, 'Actual package runtime permissions were observed')
    return values

def observed_night_mode(module):
    match = re.search(r'Night mode:\s*(auto|no|yes)\b', module.sh('cmd', 'uimode', 'night'))
    guard(match is not None, 'Actual UiModeManager night mode is observable')
    return match.group(1)

def environment_snapshot(module):
    power = module.sh('dumpsys', 'power')
    wakefulness = re.search(r'mWakefulness=([A-Za-z]+)\b', power)
    guard(wakefulness is not None and wakefulness.group(1) in ['Awake', 'Asleep'],
          'Only an observed stable Awake/Asleep state can be restored exactly')
    return {'environmentRestorationScope': environment_restoration_scope(),
            'settings': {namespace: settings_map(module, namespace) for namespace in CONTROLLED_SETTINGS},
            'density': module.sh('wm', 'density'), 'size': module.sh('wm', 'size'),
            'nightMode': observed_night_mode(module),
            'permissions': permissions(module), 'powerAsleep': wakefulness.group(1) == 'Asleep',
            'powerWakefulness': wakefulness.group(1)}

def comparable_environment(value):
    #Only explicit owned settings, WM overrides, this package's runtime grants/
    #flags, and screen wakefulness are compared. Other OS state is out of scope.
    return {key: value[key] for key in ['settings', 'density', 'size', 'nightMode', 'permissions', 'powerAsleep', 'powerWakefulness']}

def restore_environment(module, before):
    failures = []
    def attempt(label, action):
        try:
            return action()
        except Exception as failure:
            failures.append({'operation': label, 'failureType': type(failure).__name__})
            return None
    if attempt('read current night mode', lambda: observed_night_mode(module)) != before['nightMode']:
        attempt('restore UiModeManager night mode', lambda: shell(module, 'cmd', 'uimode', 'night', before['nightMode']))
    for command, label in [('size', 'size'), ('density', 'density')]:
        pattern = r'Override ' + command + r':\s*([^\n]+)'
        match = re.search(pattern, before[label])
        attempt(label, lambda command=command, match=match: module.sh('wm', command, match.group(1).strip() if match else 'reset'))
    for namespace, keys in CONTROLLED_SETTINGS.items():
        desired = before['settings'][namespace]
        guard(set(desired) == set(keys), 'Restoration snapshot contains exactly owned setting keys')
        current = attempt('read current settings ' + namespace, lambda namespace=namespace: settings_map(module, namespace))
        if current is None:
            current = {}
        for key in keys:
            state = desired[key]
            if current.get(key) != state:
                command = ['settings', 'put', namespace, key, state['value']] if state['present'] else ['settings', 'delete', namespace, key]
                attempt('restore owned setting', lambda command=command: shell(module, *command))
    current_permissions = attempt('read current permission grants', lambda: permissions(module)) or {}
    flag_names = {'USER_SET': 'user-set', 'USER_FIXED': 'user-fixed', 'REVOKE_ON_UPGRADE': 'revoke-on-upgrade',
                  'REVIEW_REQUIRED': 'review-required', 'REVOKED_COMPAT': 'revoked-compat'}
    for key, desired in before['permissions'].items():
        current = current_permissions.get(key)
        if current is None or current['granted'] != desired['granted']:
            attempt('restore permission grant', lambda key=key, desired=desired: shell(module, 'pm', 'grant' if desired['granted'] else 'revoke', module.PKG, key))
    restored_permissions = attempt('read permission flags after grant restoration', lambda: permissions(module)) or {}
    for key, desired in before['permissions'].items():
        current = restored_permissions.get(key)
        actual_flags = set(current['flags']) if current else set()
        desired_flags = set(desired['flags'])
        for flag, argument in flag_names.items():
            if (flag in actual_flags) != (flag in desired_flags):
                attempt('restore permission flag', lambda key=key, flag=flag, argument=argument, desired_flags=desired_flags:
                        shell(module, 'pm', 'set-permission-flags' if flag in desired_flags else 'clear-permission-flags', module.PKG, key, argument))
    attempt('restore screen power', lambda: module.sh('input', 'keyevent', '223' if before['powerAsleep'] else '224'))
    deadline = time.monotonic() + 5
    while time.monotonic() < deadline:
        raw = attempt('wait for restored screen power', lambda: module.sh('dumpsys', 'power'))
        if raw is not None and re.search(r'mWakefulness=' + before['powerWakefulness'] + r'\b', raw):
            break
        time.sleep(.2)
    after = environment_snapshot(module)
    return comparable_environment(after) == comparable_environment(before), after, failures

def source_maps(binary):
    result = {}
    for section in MAPS:
        expected = binary.get(section)
        guard(isinstance(expected, dict) and expected, 'All six frozen source maps are required')
        mismatches = []
        for relative, digest in expected.items():
            path = R / relative
            guard(not Path(relative).is_absolute() and '..' not in Path(relative).parts and '.secrets' not in path.resolve().parts and
                  path.resolve().is_relative_to(R), 'Source evidence stays within non-secret repository files')
            if not path.is_file() or sha(path.read_bytes()) != digest:
                mismatches.append(relative)
        result[section] = {'fileCount': len(expected), 'matches': not mismatches, 'mismatches': mismatches}
    guard(all(value['matches'] for value in result.values()), 'Current sources/resources must equal all six build maps')
    return result

def profile_from_xml(raw):
    root = ET.fromstring(raw)
    node = next((node for node in root if node.get('name') == 'localProfile'), None)
    return None if node is None else json.loads(node.text)

def local_profile(module):
    return profile_from_xml(module.prefs())

def fixed_rotation(module):
    observed = re.findall(r'mFixedToUserRotation=(true|false)', module.sh('dumpsys', 'window'))
    guard(len(observed) == 1, 'Exactly one owned display fixed-to-user rotation state')
    return observed[0] == 'true'

def fixed_rotation_policy(module):
    # Preserve default/disabled/enabled rather than collapsing default toFalse.
    raw = shell(module, 'su', '0', 'sh', '-c',
                'if [ -e /data/system/display_settings.xml ]; then cat /data/system/display_settings.xml; else printf MISSING; fi')
    if raw == 'MISSING':
        return 'default'
    root = ET.fromstring(raw)
    displays = root.findall('display')
    guard(len(displays) <= 1, 'Only the single owned AVD display policy may be changed')
    values = {None: 'default', '0': 'default', '1': 'disabled', '2': 'enabled'}
    value = displays[0].get('fixedToUserRotation') if displays else None
    guard(value in values, 'Known actual fixed-to-user rotation tri-state policy')
    return values[value]

def viewport(module, native):
    landscape = native == 'landscape'
    # A force-stopped app leaves Launcher, whose portrait-only activity can
    # ignore user_rotation. Start only our safe local fixture MainActivity
    # before requesting landscape, then verify real display dimensions.
    if landscape:
        module.sh('wm', 'set-fix-to-user-rotation', 'enabled')
        guard(fixed_rotation(module) is True, 'Actual OS fixed-to-user rotation enabled for native landscape')
        module.launch()
    module.sh('settings','put','system','accelerometer_rotation','0')
    module.sh('settings','put','system','user_rotation','1' if landscape else '0')
    module.wait(lambda: (module.dimensions()[0] > module.dimensions()[1]) == landscape, 'Actual product group orientation')
    module.sh('wm', 'density', 'reset')
    module.sh('settings', 'put', 'system', 'font_scale', '1.0')
    if native:
        width, height = module.dimensions()
        short = min(width,height) if landscape else width
        density = round(short * 160 / 320)
        guard(density > 0 and abs(short * 160 / density - 320) <= 1, 'Physical display can represent actual320dp')
        module.sh('wm', 'density', str(density))
        module.sh('settings', 'put', 'system', 'font_scale', '2.0')
        module.sh('settings', 'put', 'secure', 'show_ime_with_hard_keyboard', '1')
    module.tree()  #Working fresh observer, never the standard idle-wait dump.
    result = {'density': module.sh('wm', 'density'), 'fontScale': module.sh('settings', 'get', 'system', 'font_scale'),
              'native320Font2RequiredByClass': bool(native and not landscape)}
    if landscape:
        width,height = module.dimensions()
        guard(width > height and abs(height * 160 / density - 320) <= 1, 'Actual native short side320dp in landscape')
        result.update(nativeLandscape320ShortSideRequiredByClass=True,widthPx=width,heightPx=height,
                      effectiveDensityDpi=density,shortSideDp=height*160/density,nativeLandscape=True)
    return result
