#!/usr/bin/env python3
"""ROOT ONLY: restore interrupted source7 attempt from its actual durable backups.
No product results are accepted, no old proof/log is overwritten, no DB copyback.
"""
from pathlib import Path
import base64,hashlib,importlib.util,json,os
R=Path('/workspace/runmoni');W=R/'work/expressive-refresh';E=R/'docs/app-review/2026-11-17-expressive-refresh'
spec=importlib.util.spec_from_file_location('scoped_review_recovery',W/'android-review.py');d=importlib.util.module_from_spec(spec);spec.loader.exec_module(d)
def ref(p):return {'file':p.name,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()}
oldp=E/'android-review2.json';old=json.loads(oldp.read_text());out=E/'android-review2-recovery1.json';assert not out.exists();assert old['completed'] is False
assert ref(W/'android-review.py')['sha256']==old['executedDriverSha256']
for p in Path('/proc').iterdir():
 if not p.name.isdigit():continue
 try:
  cmd=(p/'cmdline').read_bytes()
  assert not (b'python\x00work/expressive-refresh/android-review.py\x00' in cmd), 'Original driver still live'
 except (FileNotFoundError,ProcessLookupError,PermissionError):pass
own=json.loads((W/'owned-avd3.json').read_text());pid=own['pid'];assert Path(f'/proc/{pid}/stat').read_text().split(') ',1)[1].split()[19]==own['procStartTicks']
assert b'Runmoni_Cloud_29' in Path(f'/proc/{pid}/cmdline').read_bytes()
def backup(key):
 r=old[key];p=W/r['file'];assert ref(p)['sha256']==r['sha256'];return p.read_bytes()
main={'present':old['mainPreferencesOriginallyPresent'],'raw':backup('privateXmlBackup')}
account={'present':old['accountPreferencesOriginallyPresent'],'raw':backup('privateAccountBackup')}
appearance={'present':old['appearancePreferencesOriginallyPresent'],'raw':backup('privateAppearanceBackup')}
fixtures={k:base64.b64decode(v,validate=True) for k,v in json.loads(backup('privateOwnedFixtureBackup')).items()}
environment=json.loads(backup('privateEnvironmentBackup'));data=json.loads(backup('privateDataBefore'))
p={'version':'0.9.0-dev','versionCode':32,'completed':False,'passed':False,'scope':'Scoped restoration only after absent driver; no product execution success/count accepted','actualPassedProductMethods':0,'interruptedAttempt':ref(oldp),'originalAttemptRemainsIncomplete':True,'driverExitCodeKnown':False,'interruptionCauseEstablished':False,'observedLiveOwnedAvdPid':pid,'binaryReference':old['binaryReference'],'binaryReferenceSha256':old['binaryReferenceSha256'],'startedAtUtc':d.now(),'restorationCommandFailures':[],'executedRecoveryDriver':ref(Path(__file__))}
d.atomic_json(out,p);m=d.helper('review2-recovery1');assert m.adb('emu','avd','name').decode().splitlines()[0]=='Runmoni_Cloud_29';assert m.dump() is None
m.force_stop();m.sh('am','force-stop',m.PKG+'.test')
try:
 for name,value,field in [('runmoni.xml',main,'preferencesRestoredExactly'),('runmoni-community-account.xml',account,'communityAccountPreferencesRestoredExactly'),('runmoni_appearance.xml',appearance,'appearancePreferencesRestoredExactly')]:
  try:p[field]=d.restore_preference_file(m,name,value);assert p[field]
  except BaseException as ex:p[field]=False;p['restorationCommandFailures'].append({'operation':name,'failureType':type(ex).__name__})
 p['profileRestoredExactly']=d.local_profile(m)==d.profile_from_xml(main['raw'])
 try:p['removedOnlyNewOwnedFixturePreferences']=d.remove_new_owned_fixtures(m,fixtures);p['ownedFixturePreferencesCleaned']=True
 except BaseException as ex:p['ownedFixturePreferencesCleaned']=False;p['restorationCommandFailures'].append({'operation':'fixture preferences','failureType':type(ex).__name__})
 after=m.snapshot('after');d.exclusive_bytes(W/'review2-recovery1-private-data-after.json',(json.dumps(after,sort_keys=True)+'\n').encode(),private=True)
 p['originalDatabaseRowsPreservedExactly']=m.canonical(after)==m.canonical(data)
finally:
 equal,after,failures=d.restore_environment(m,environment);p['controlledSettingsPermissionsPowerRestoredExactly']=equal;p['restorationCommandFailures']+=failures
 d.exclusive_bytes(W/'review2-recovery1-private-environment-after.json',(json.dumps(after,indent=2)+'\n').encode(),private=True)
 m.force_stop()
 p['allPreferencesStillExactAfterEnvironmentRestoration']=(d.preference_file_snapshot(m,'runmoni.xml')==main and d.account_snapshot(m)==account and d.preference_file_snapshot(m,'runmoni_appearance.xml')==appearance and d.owned_fixture_preferences(m)==fixtures)
 p['sourceMapsAfterRestoration']=d.source_maps(json.loads((E/p['binaryReference']).read_text()))
 fields=['preferencesRestoredExactly','profileRestoredExactly','communityAccountPreferencesRestoredExactly','appearancePreferencesRestoredExactly','ownedFixturePreferencesCleaned','originalDatabaseRowsPreservedExactly','controlledSettingsPermissionsPowerRestoredExactly','allPreferencesStillExactAfterEnvironmentRestoration']
 p['passed']=all(p.get(k) is True for k in fields) and not p['restorationCommandFailures'];p['completed']=True;p['finishedAtUtc']=d.now();d.atomic_json(out,p)
 print(json.dumps({k:p[k] for k in ['completed','passed',*fields]},ensure_ascii=False),flush=True)
assert p['passed']
