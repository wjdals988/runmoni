"""Read-only exact-input audit with one explicitly reviewed pure-renderer boundary.

Importing this file performs no Android/network/Git action or evidence mutation.
"""
from pathlib import Path
import hashlib
import importlib.util
import json
import re

R = Path('/workspace/runmoni')
W = R / 'work/expressive-refresh'
E = R / 'docs/app-review/2026-11-17-expressive-refresh'
MAPS = ('productSources', 'testSources', 'resources', 'rendererAssets',
        'approvedSourceCopies', 'configurationSources')
FINAL = 'binary-build11.json'
FINAL_SHA = '13b25e0a2706183c96bff43f0a05fe9548211e308a22c194cb3086bc3cc2dba3'
EARLIER = 'binary-build9.json'
EARLIER_SHA = '000b6f323ec4cc1911a90addafc60b6cb57643882457e87238d03b0883607766'
CHANGED = ['app/src/main/java/com/runmoni/app/ui/RunShareScreen.kt',
           'app/src/main/java/com/runmoni/app/ui/RunmoniModal.kt',
           'app/src/main/java/com/runmoni/app/ui/RunmoniWindowPolicy.kt']
LANDSCAPE_SHA = '3c475c9a4ead2ff45fb93909da43741a4add12a5e83275440f2aeb817ada960a'
RECOVERY_DRIVER_SHA = '19ff1a2893ff6689dd773b35924101d3dad4b69c1a731929daefeca9f132694e'
ROTATION_EXCEPTION = {
    'proof': 'landscape2', 'field': 'fixedToUserRotation.restoredExactly',
    'observed': False, 'originalParentSha256': LANDSCAPE_SHA,
    'supplementalRestoration': 'landscape2-rotation-recovery2.json',
    'reason': 'API29 shell default persisted disabled; passed whole product method retained only with separately executed restoration to original default/effective false. Original parent remains false; recovery adds zero product methods.'}
PURE = {'atelier-materials': ['com.runmoni.app.avatar.AtelierWardrobeTest',
                            'com.runmoni.app.avatar.WardrobeRendererTest',
                            'com.runmoni.app.avatar.ExpandedWardrobeTest'],
        'atelier-expansion': ['com.runmoni.app.ui.AtelierExpansionRenderingTest']}
RESTORED = ('driverSourceUnchanged', 'observerHelperSourceUnchanged', 'binaryEvidenceUnchanged',
            'preferencesRestoredExactly', 'profileRestoredExactly',
            'communityAccountPreferencesRestoredExactly', 'appearancePreferencesRestoredExactly',
            'allPreferencesStillExactAfterEnvironmentRestoration', 'originalDatabaseRowsPreservedExactly',
            'ownedFixturePreferencesCleaned', 'controlledSettingsPermissionsPowerRestoredExactly')


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def reference(parent, ref):
    assert isinstance(ref, dict) and isinstance(ref.get('file'), str)
    assert re.fullmatch('[0-9a-f]{64}', ref.get('sha256', ''))
    p = Path(ref['file'])
    p = (p if p.is_absolute() else parent / p).resolve(strict=True)
    assert p.is_file() and p.is_relative_to(R)
    assert not any(part == '.secrets' or 'private' in part.lower() for part in p.parts)
    assert sha(p) == ref['sha256'], ('Changed evidence', p.name)
    return p


def binary(path):
    d = json.loads(path.read_text())
    assert (d['version'], d['versionCode']) == ('0.9.0-dev', 32)
    assert d['buildSucceeded'] is True and d['jvmReportsFresh'] is True
    f = reference(path.parent, {'file': d['sourceFreezeReference'], 'sha256': d['sourceFreezeSha256']})
    frozen = json.loads(f.read_text())
    assert (frozen['version'], frozen['versionCode']) == ('0.9.0-dev', 32)
    assert frozen['sourceMaps'] == {key: d[key] for key in MAPS}
    return d


def observed_maps(value, d):
    assert set(value) == set(MAPS)
    for key in MAPS:
        assert value[key] == d[key] or value[key] == {
            'fileCount': len(d[key]), 'matches': True, 'mismatches': []}


def method_ids(proof, group):
    ids = [method for method in proof['plannedMethodIdsFromCurrentSource'] if any(
        method == selector or ('#' not in selector and method.startswith(selector+'#'))
        for selector in group['classes'])]
    assert len(ids) == group['expected'] and len(ids) == len(set(ids))
    return sorted(ids)


def successful_raw(parent, group):
    raw = reference(parent, {'file': group['raw'], 'sha256': group['rawSha256']})
    text = raw.read_text(errors='replace')
    found = re.search(r'OK \((\d+) tests?\)', text)
    assert found and int(found[1]) == group['actual']
    assert not any(token in text for token in ['FAILURES!!!', 'Process crashed', 'INSTRUMENTATION_FAILED'])
    assert re.search(r'INSTRUMENTATION_STATUS_CODE:\s*-[34]\b', text) is None



def audit_rotation_recovery(scope, scope_path, proofs, final):
    assert scope['restorationExceptions'] == [ROTATION_EXCEPTION]
    refs = scope['supplementalRestorationReferences']
    assert len(refs) == 1 and refs[0]['file'] == 'landscape2-rotation-recovery2.json'
    path = reference(scope_path.parent, refs[0])
    recovery = json.loads(path.read_text())
    assert (recovery['version'], recovery['versionCode']) == ('0.9.0-dev', 32)
    assert recovery['completed'] is True and recovery['passed'] is True
    assert recovery['sourceKind'] == 'restoration-only owned API29 rotation'
    assert recovery['avd'] == 'Runmoni_Cloud_29' and recovery['api'] == 29
    assert recovery['ownedAvd']['serial'] == 'emulator-5556'
    assert recovery['productMethodsCountedByRecovery'] == 0
    for field in ('restorationOnly', 'onlyOwnedAttributeChanged', 'otherFileBytesPreserved',
                  'displayFileOwnerModePreserved', 'frameworkRestarted',
                  'policyAndEffectiveRestoredExactly', 'preferencesRestoredExactly',
                  'profileRestoredExactly', 'communityAccountPreferencesRestoredExactly',
                  'appearancePreferencesRestoredExactly', 'allPreferencesStillExactAfterEnvironmentRestoration',
                  'ownedFixturePreferencesRestoredExactly', 'controlledEnvironmentExactlyRestored',
                  'databaseExactlyRestored', 'originalDatabaseRowsAndBackupOriginsPreservedExactly',
                  'originalFailedParentStillImmutable', 'binaryEvidenceUnchanged', 'driverSourceUnchanged',
                  'recoveryTempOriginallyAbsent', 'recoveryTempStillAbsentBeforeWrite'):
        assert recovery[field] is True, ('Rotation recovery incomplete', field)
    for field in ('appOrTestApkChanged', 'userDataReset', 'networkOrFirewallModified',
                  'allOsBytesExactlyRestoredClaimed', 'originalFailedParentOverwritten',
                  'productInstrumentationInvoked'):
        assert recovery[field] is False
    assert not recovery['restorationCommandFailures']
    assert recovery['before'] == {'effective': False, 'persistedPolicy': 'disabled'}
    assert recovery['target'] == recovery['after'] == {'effective': False, 'persistedPolicy': 'default'}
    failed_ref = recovery['priorFailedRecoveryPreflight']
    assert failed_ref == {'file': 'landscape2-rotation-recovery.json',
        'sha256': '3307d720b75db4c2b4caa505478631a039435446168e080c8fc23208f188d19f',
        'frameworkStopOrRotationWriteReached': False, 'productMethodsCounted': 0}
    failed_path = reference(path.parent, failed_ref)
    failed = json.loads(failed_path.read_text())
    assert failed['completed'] is True and failed['passed'] is False and failed['failureType'] == 'RuntimeError'
    assert not any(key in failed for key in ('recoveryTempOriginallyAbsent', 'frameworkPidBefore', 'frameworkPidAfter', 'onlyOwnedAttributeChanged'))
    assert recovery['beforeDisplayFileStat'] == recovery['frozenDisplayFileStat']
    assert re.fullmatch(r'\d+:\d+:[0-7]{3,4}', recovery['beforeDisplayFileStat'])
    assert recovery['priorAttempt'] == {'file': 'landscape2.json', 'sha256': LANDSCAPE_SHA,
                                       'wholeAttemptPassed': False, 'productMethodsPassed': 1}
    assert reference(path.parent, recovery['priorAttempt']) == proofs['landscape2'][0]
    assert sha(proofs['landscape2'][0]) == LANDSCAPE_SHA
    assert recovery['binaryReference'] == {'file': FINAL, 'sha256': FINAL_SHA}
    reference(path.parent, recovery['binaryReference'])
    assert recovery['executedDriverSha256'] == RECOVERY_DRIVER_SHA
    reference(path.parent, {'file': recovery['executedDriver'], 'sha256': RECOVERY_DRIVER_SHA})
    for field in ('environmentHelper', 'observerHelper'):
        reference(path.parent, recovery[field])
    prior = proofs['landscape2'][1]
    assert recovery['environmentHelper'] == {'file': prior['executedDriver'], 'sha256': prior['executedDriverSha256']}
    assert recovery['observerHelper'] == {'file': prior['observerHelper'], 'sha256': prior['observerHelperSha256']}
    expected_installed = [{'package': package, 'sha256': entry['sha256']} for package, entry in
                          zip(('com.runmoni.app', 'com.runmoni.app.test'), final['binaries'])]
    assert recovery['installedBefore'] == recovery['installedAfter'] == expected_installed
    for field in ('sourceMapsBefore', 'sourceMapsAfterRestoration'):
        observed_maps(recovery[field], final)
    assert set(recovery['originalPreferencePresenceAndHashes']) == {
        'runmoni.xml', 'runmoni-community-account.xml', 'runmoni_appearance.xml'}
    for value in recovery['originalPreferencePresenceAndHashes'].values():
        assert isinstance(value['present'], bool) and re.fullmatch('[0-9a-f]{64}', value['sha256'])
    assert re.fullmatch('[0-9a-f]{64}', recovery['originalDatabaseRowsSha256'])
    assert prior['fixedToUserRotation'] == {
        'before': False, 'beforePolicy': 'default', 'forcedEnabledForLandscape': True,
        'restoredExactly': False,
        'restorationScope': 'Effective value and default/disabled/enabled policy only; full display-settings file bytes are not claimed identical',
        'after': False, 'afterPolicy': 'disabled'}
    assert prior['passed'] is False and prior['actualPassedProductMethods'] == 1
    assert len(prior['groups']) == 1 and prior['groups'][0]['passed'] is True
    return recovery


def audit_scope(scope, scope_path, binary_name, require_root_acceptance=True):
    assert scope['schemaVersion'] == 2
    assert isinstance(scope['scopeAcceptedByRoot'], bool)
    if require_root_acceptance:
        assert scope['scopeAcceptedByRoot'] is True
    assert binary_name == FINAL and scope['finalLocalBinary'] == FINAL
    assert scope['finalLocalBinarySha256'] == FINAL_SHA
    final_path = reference(scope_path.parent, {'file': FINAL, 'sha256': FINAL_SHA})
    final = binary(final_path)
    spec = importlib.util.spec_from_file_location('scope11_readonly_environment', W / 'android-review.py')
    env = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(env)
    env.source_maps(final)
    catalogue = env.test_catalogue()
    current = {cls+'#'+method for cls, methods in catalogue.items() for method in methods}
    refs = scope['proofReferences']
    assert isinstance(refs, dict) and refs
    proofs = {}
    for key, ref in refs.items():
        p = reference(scope_path.parent, ref)
        proofs[key] = (p, json.loads(p.read_text()))

    audit_rotation_recovery(scope, scope_path, proofs, final)
    allowed_earlier = {}
    if scope['earlierInputResultsReused'] is True:
        assert len(scope['equivalenceReviews']) == 1
        ep = reference(scope_path.parent, scope['equivalenceReviews'][0])
        eq = json.loads(ep.read_text())
        assert eq['schemaVersion'] == 1 and (eq['sourceInput'], eq['finalInput']) == (9, 11)
        assert eq['acceptedForSourceEquivalenceByRoot'] is True
        assert eq['finalScopeAcceptanceStillRequired'] is True and eq['sameApkEntireScopeClaimed'] is False
        assert eq['sourceFailedGroupPartialMethodsRetained'] == eq['otherEarlierInputPassedUiPlaybackMethodsRetained'] == 0
        old_path = reference(ep.parent, eq['sourceBinary'])
        assert old_path.name == EARLIER and sha(old_path) == EARLIER_SHA
        assert reference(ep.parent, eq['finalBinary']) == final_path
        old = binary(old_path)
        assert reference(ep.parent, eq['sourceFreeze']) == E / old['sourceFreezeReference']
        assert reference(ep.parent, eq['finalFreeze']) == E / final['sourceFreezeReference']
        changed = {key: sorted(p for p in set(old[key]) | set(final[key])
                              if old[key].get(p) != final[key].get(p)) for key in MAPS}
        assert changed == eq['changedPathsByMap']
        assert changed['productSources'] == eq['allowedChangedProductPaths'] == CHANGED
        assert all(not changed[key] for key in MAPS[1:])
        assert eq['allOtherSourcePathsAndHashesUnchanged'] is True
        assert eq['unchangedProductDependencyEnvelope'] == {
            p: h for p, h in old['productSources'].items() if p not in CHANGED}
        assert eq['unchangedNonProductMaps'] == {key: len(old[key]) for key in MAPS[1:]}
        parent = reference(ep.parent, eq['sourceParent'])
        assert parent == proofs['android-review8'][0]
        pd = proofs['android-review8'][1]
        assert pd['passed'] is False and eq['sourceParentPassed'] is False
        assert pd['actualPassedProductMethods'] == eq['sourceParentWholePassedMethods'] == 84
        assert len(eq['retainedGroups']) == 2 and {g['group'] for g in eq['retainedGroups']} == set(PURE)
        retained = []
        for row in eq['retainedGroups']:
            assert row['proof'] == 'android-review8' and row['wholeGroupActuallyPassed'] is True
            group = next(g for g in pd['groups'] if g['name'] == row['group'])
            assert group['classes'] == PURE[row['group']]
            expected = sorted(c+'#'+method for c in PURE[row['group']] for method in catalogue[c])
            assert row['methodIds'] == expected == method_ids(pd, group)
            assert reference(ep.parent, row['raw']) == E / group['raw']
            allowed_earlier[(row['proof'], row['group'])] = expected
            retained += expected
        assert len(retained) == len(set(retained)) == eq['retainedDistinctMethods'] == 14
    else:
        assert scope['earlierInputResultsReused'] is False and scope['equivalenceReviews'] == []

    rows = scope['includedGroups']
    selected = {(row['proof'], row['group']) for row in rows}
    assert rows and len(rows) == len(selected)
    excluded = {(row['proof'], row['group']): row for row in scope['excludedGroups']}
    assert len(excluded) == len(scope['excludedGroups']) and not selected.intersection(excluded)
    for key, (path, proof) in proofs.items():
        assert proof['completed'] is True and (proof['version'], proof['versionCode']) == ('0.9.0-dev', 32)
        assert proof['actualPassedDiagnosticMethods'] == 0
        bp = reference(path.parent, {'file': proof['binaryReference'], 'sha256': proof['binaryReferenceSha256']})
        data = binary(bp)
        assert (bp.name, sha(bp)) in [(FINAL, FINAL_SHA), (EARLIER, EARLIER_SHA)]
        for field in ('sourceMapsBefore', 'sourceMapsAfter', 'sourceMapsAfterRestoration'):
            observed_maps(proof[field], data)
        assert all(proof[field] is True for field in RESTORED), ('Incomplete restoration/input', key)
        assert not proof['restorationCommandFailures']
        for field in ('executedDriver', 'observerHelper'):
            reference(path.parent, {'file': proof[field], 'sha256': proof[field+'Sha256']})
        assert proof['installed'] == [{'package': package, 'sha256': b['sha256']} for package, b in
            zip(('com.runmoni.app', 'com.runmoni.app.test'), data['binaries'])]
        bad = [g for g in proof['groups'] if not g['passed']]
        if key == 'landscape2':
            assert sha(path) == LANDSCAPE_SHA and proof['passed'] is False and not bad
        else:
            assert (proof['passed'] is True) == (not bad)
        assert sum(g.get('actual', 0) or 0 for g in proof['groups'] if g['passed']) == proof['actualPassedProductMethods']
        for group in bad:
            reason = excluded[(key, group['name'])]['reason']
            assert isinstance(reason, str) and reason.strip()
            raw = reference(path.parent, {'file': group['raw'], 'sha256': group['rawSha256']})
            assert any(t in raw.read_text(errors='replace') for t in ['FAILURES!!!', 'Process crashed', 'INSTRUMENTATION_FAILED'])
        if proof['reviewPhase'] == 'landscape':
            if key == 'landscape2':
                assert proof['fixedToUserRotation']['restoredExactly'] is False
            else:
                assert proof['fixedToUserRotation']['restoredExactly'] is True

    methods, fresh, retained, used_earlier = [], [], [], set()
    for row in rows:
        path, proof = proofs[row['proof']]
        matches = [g for g in proof['groups'] if g['name'] == row['group']]
        assert len(matches) == 1
        group = matches[0]
        assert group['status'] == 'passed' and group['passed'] is True
        assert group['actual'] == group['expected'] > 0 and group['countsAsProductTest'] and not group['diagnosticOnly']
        ids = method_ids(proof, group)
        assert row['methodIds'] == ids and set(ids) <= current
        successful_raw(path.parent, group)
        if proof['binaryReference'] == FINAL:
            fresh += ids
        else:
            key = (row['proof'], row['group'])
            assert key in allowed_earlier and ids == allowed_earlier[key]
            retained += ids
            used_earlier.add(key)
        methods += ids
    assert used_earlier == set(allowed_earlier)
    assert len(methods) == len(set(methods)) == scope['distinctPassedMethods'] > 0
    assert len(retained) == scope['equivalenceRetainedMethodCount']
    assert len(fresh) == scope['freshFinalInputMethodCount']
    planned = [method for phase in ('default', 'native', 'native-sdk', 'native-online', 'landscape')
               for method in env.phase_groups(phase)[1]]
    assert len(planned) == len(set(planned)) == 117
    assert set(methods) == set(planned), 'Every method in the actual planned117 scope must be present once'
    if retained:
        assert len(retained) == 14 and len(fresh) == 103
    assert scope['sameBinaryEntireScopeClaimed'] is (not bool(retained))
    assert scope['restorationExceptions'] == [ROTATION_EXCEPTION]
    assert len(scope['supplementalRestorationReferences']) == 1
    return scope


def validate_scope(binary_name, scope_path=E / 'android-scope-summary.json'):
    raw = scope_path.read_bytes()
    scope = json.loads(raw)
    audit_scope(scope, scope_path, binary_name, require_root_acceptance=True)
    return scope, hashlib.sha256(raw).hexdigest()
