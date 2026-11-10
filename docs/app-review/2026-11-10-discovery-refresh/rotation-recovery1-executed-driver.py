from pathlib import Path
import importlib.util,json,hashlib,time,datetime,os
R=Path('/workspace/runmoni');W=R/'work/discovery-refresh';E=R/'docs/app-review/2026-11-10-discovery-refresh'
os.umask(0o077)
spec=importlib.util.spec_from_file_location('rotation_recovery_environment',W/'android-last.py');env=importlib.util.module_from_spec(spec);spec.loader.exec_module(env)
m=env.helper('rotation-recovery1');sha=env.sha
out=E/'landscape1-manual-restoration.json';assert not out.exists()
driver=Path(__file__).read_bytes();env.exclusive_bytes(E/'rotation-recovery1-executed-driver.py',driver)
priorRaw=(E/'landscape1.json').read_bytes();prior=json.loads(priorRaw)
assert prior['completed'] and not prior['passed'] and prior['actualPassedProductMethods']==1
assert prior['fixedToUserRotation']['before'] is False and prior['fixedToUserRotation']['beforePolicy']=='default'
assert all(prior[k] for k in ['preferencesRestoredExactly','profileRestoredExactly','controlledSettingsPermissionsPowerRestoredExactly'])
assert m.adb('emu','avd','name').decode().splitlines()[0]=='Runmoni_Cloud_29' and m.sh('getprop','ro.build.version.sdk')=='29'
assert m.dump() is None and not m.pid()
ref=json.loads((E/'rotation-helper-build.json').read_text());jar=R/ref['jar']['hostFile'];assert sha(jar.read_bytes())==ref['jar']['sha256']
before={'effective':env.fixed_rotation(m),'persistedPolicy':env.fixed_rotation_policy(m)}
assert before=={'effective':False,'persistedPolicy':'disabled'}
originalEnvironment=json.loads((W/'landscape1-private-environment-before.json').read_text())
originalXml=(W/'landscape1-private-before.xml').read_bytes()
assert m.prefs()==originalXml
m.adb('push',str(jar),ref['jar']['deviceFile'])
assert m.sh('sha256sum',ref['jar']['deviceFile']).split()[0]==ref['jar']['sha256']
raw=env.shell(m,'env','CLASSPATH='+ref['jar']['deviceFile'],'app_process','/system/bin','com.runmoni.review.RotationPolicy')
observed=json.loads(raw);assert observed=={'displayId':0,'requestedFixedToUserRotationMode':0,'binderInvocationSucceeded':True}
(E/'rotation-recovery1-binder.txt').write_text(raw+'\n')
end=time.monotonic()+20
while time.monotonic()<end:
 after={'effective':env.fixed_rotation(m),'persistedPolicy':env.fixed_rotation_policy(m)}
 if after=={'effective':False,'persistedPolicy':'default'}:break
 time.sleep(.25)
assert after=={'effective':False,'persistedPolicy':'default'}
equal,current,failures=env.restore_environment(m,originalEnvironment)
assert equal and not failures and m.prefs()==originalXml
p={'passed':True,'completed':True,'executedDriver':'rotation-recovery1-executed-driver.py','executedDriverSha256':sha(driver),'driverSourceUnchanged':sha(Path(__file__).read_bytes())==sha(driver),'observedAtUtc':env.now(),'priorAttempt':{'file':'landscape1.json','sha256':sha(priorRaw),'wholeAttemptPassed':False,'productMethodsPassed':1},'reason':'API29 wm only exposes enabled/disabled; default argument had persisted disabled. Restore default through its explicit IWindowManager binder API.', 'before':before,'target':{'effective':False,'persistedPolicy':'default'},'after':after,'policyAndEffectiveRestoredExactly':True,'preferencesRestoredExactly':True,'profileRestoredExactly':env.local_profile(m)==env.profile_from_xml(originalXml),'controlledEnvironmentExactlyRestored':True,'appOrTestApkChanged':False,'userDataReset':False,'allOsBytesExactlyRestoredClaimed':False,'helperBuild':{'file':'rotation-helper-build.json','sha256':sha((E/'rotation-helper-build.json').read_bytes())},'deviceJarSha256':ref['jar']['sha256'],'raw':{'file':'rotation-recovery1-binder.txt','sha256':sha((E/'rotation-recovery1-binder.txt').read_bytes())}}
assert p['profileRestoredExactly'];out.write_text(json.dumps(p,ensure_ascii=False,indent=2)+'\n')
print('Original default rotation policy/effective state and owned settings restored; prior failed whole attempt retained.',flush=True)
