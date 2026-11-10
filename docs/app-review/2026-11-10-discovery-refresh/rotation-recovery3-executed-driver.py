"""Finish measured restoration after recovery2's accessibility-observer timeout.
No UI automation, framework restart, APK changes or database writes.
"""
from pathlib import Path
import importlib.util,json,os
R=Path('/workspace/runmoni');W=R/'work/discovery-refresh';E=R/'docs/app-review/2026-11-10-discovery-refresh';os.umask(0o077)
def load(name,path):
 s=importlib.util.spec_from_file_location(name,path);m=importlib.util.module_from_spec(s);s.loader.exec_module(m);return m
m=load('rotation_final_readonly',R/'work/running-experience/actual-ui-helper6.py');env=load('rotation_final_scope',W/'android-last.py');m.W=W;m.E=W;m.NAME='rotation-recovery3';sha=env.sha
raw=Path(__file__).read_bytes();env.exclusive_bytes(E/'rotation-recovery3-executed-driver.py',raw)
out=E/'rotation-recovery3.json';assert not out.exists()
p2raw=(E/'rotation-recovery2.json').read_bytes();p2=json.loads(p2raw)
assert p2['completed'] and not p2['passed'] and p2['frameworkRestarted'] and p2['onlyOwnedAttributeChanged'] and p2['otherFileBytesPreserved'] and p2['displayFileOwnerModePreserved']
assert p2['failureType']=='TimeoutExpired'
assert m.adb('emu','avd','name').decode().splitlines()[0]=='Runmoni_Cloud_29' and m.sh('getprop','ro.build.version.sdk')=='29'
assert m.dump() is None;m.force_stop()
original_env=json.loads((W/'landscape1-private-environment-before.json').read_text());original_xml=(W/'landscape1-private-before.xml').read_bytes()
current={'effective':env.fixed_rotation(m),'persistedPolicy':env.fixed_rotation_policy(m)};assert current==p2['target']=={'effective':False,'persistedPolicy':'default'}
equal,observed,failures=env.restore_environment(m,original_env);assert equal and not failures
assert m.prefs()==original_xml and env.local_profile(m)==env.profile_from_xml(original_xml)
db=m.snapshot('after-policy-reload');assert m.canonical(db)==p2['originalDatabaseRowsSha256']
braw=(E/'binary-build8.json').read_bytes();b=json.loads(braw);installed=[]
for package,item in zip([m.PKG,m.PKG+'.test'],b['binaries']):
 path=m.sh('pm','path',package).removeprefix('package:');assert path.startswith('/data/app/') and '\n' not in path
 digest=m.sh('sha256sum',path,timeout=60).split()[0];assert digest==item['sha256'];installed.append({'package':package,'sha256':digest})
p={'passed':True,'completed':True,'observedAtUtc':env.now(),'avd':'Runmoni_Cloud_29','api':29,'acceleration':'TCG','executedDriver':'rotation-recovery3-executed-driver.py','executedDriverSha256':sha(raw),'driverSourceUnchanged':sha(Path(__file__).read_bytes())==sha(raw),'priorAttempt':p2['priorAttempt'],'recoveryAttempt2':{'file':'rotation-recovery2.json','sha256':sha(p2raw),'wholeAttemptPassed':False,'reason':'Framework restarted and single policy byte restored; later accessibility observer timed out'},'before':p2['before'],'target':p2['target'],'after':current,'policyAndEffectiveRestoredExactly':True,'preferencesRestoredExactly':True,'profileRestoredExactly':True,'controlledEnvironmentExactlyRestored':True,'databaseExactlyRestored':True,'appOrTestApkChanged':False,'userDataReset':False,'networkOrFirewallModified':False,'allOsBytesExactlyRestoredClaimed':False,'onlyOwnedAttributeChanged':True,'otherFileBytesPreserved':True,'displayFileOwnerModePreserved':True,'frameworkRestarted':True,'frameworkPidBefore':p2['frameworkPidBefore'],'frameworkPidAfter':p2['frameworkPidAfter'],'binaryReference':'binary-build8.json','binaryReferenceSha256':sha(braw),'installed':installed,'sourceMapsBefore':env.source_maps(b),'sourceMapsAfter':env.source_maps(b)}
assert p['driverSourceUnchanged'];out.write_text(json.dumps(p,ensure_ascii=False,indent=2)+'\n')
alias=E/'landscape1-manual-restoration.json';assert not alias.exists();alias.write_bytes(out.read_bytes())
proof={key:p[key] for key in ['passed','completed','observedAtUtc','avd','api','acceleration','executedDriver','executedDriverSha256','driverSourceUnchanged','binaryReference','binaryReferenceSha256','installed','sourceMapsBefore','sourceMapsAfter','appOrTestApkChanged','userDataReset','networkOrFirewallModified']}
proof['rotationRestoration']={'file':alias.name,'sha256':sha(alias.read_bytes())};proof['currentRotation']=current
(E/'rotation-recovery3-installation.json').write_text(json.dumps(proof,ensure_ascii=False,indent=2)+'\n')
print('Original default/effective rotation, exact preferences/profile/owned settings/DB rows and installed APK pair verified.',flush=True)
