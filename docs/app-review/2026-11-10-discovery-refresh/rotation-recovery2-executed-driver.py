"""Recover the one owned persisted rotation policy on API29, then reload its framework.
No APK/app/database edits, user-data reset, external connection or host OS change.
"""
from pathlib import Path
import importlib.util,json,time,os,xml.etree.ElementTree as ET,re
R=Path('/workspace/runmoni');W=R/'work/discovery-refresh';E=R/'docs/app-review/2026-11-10-discovery-refresh';os.umask(0o077)
def load(name,path):
 spec=importlib.util.spec_from_file_location(name,path);m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m);return m
m=load('rotation_readonly_android_helpers',R/'work/running-experience/actual-ui-helper6.py');env=load('rotation_scope_helpers',W/'android-last.py')
m.W=W;m.E=W;m.NAME='rotation-recovery2';sha=env.sha
out=E/'rotation-recovery2.json';assert not out.exists()
source=Path(__file__).read_bytes();env.exclusive_bytes(E/'rotation-recovery2-executed-driver.py',source)
p={'passed':False,'completed':False,'executedDriver':'rotation-recovery2-executed-driver.py','executedDriverSha256':sha(source),'startedAtUtc':env.now(),'appOrTestApkChanged':False,'userDataReset':False,'networkOrFirewallModified':False,'allOsBytesExactlyRestoredClaimed':False}
need_start=False;original_env=None;original_xml=None;baseline=None
fp='/data/system/display_settings.xml';tmp=fp+'.runmoni-recovery'
def stage(name):p['stage']=name;p['updatedAtUtc']=env.now();env.atomic_json(out,p);print(name,flush=True)
def root(*args,**kwargs):return m.adb('shell','-T','su','0',*args,**kwargs)
def read_policy_file():return root('cat',fp)
try:
 stage('guards')
 assert m.adb('emu','avd','name').decode().splitlines()[0]=='Runmoni_Cloud_29' and m.sh('getprop','ro.build.version.sdk')=='29'
 assert m.dump() is None and not m.pid()
 prior_raw=(E/'landscape1.json').read_bytes();prior=json.loads(prior_raw)
 assert prior['completed'] and not prior['passed'] and prior['actualPassedProductMethods']==1
 assert prior['fixedToUserRotation']['before'] is False and prior['fixedToUserRotation']['beforePolicy']=='default'
 assert all(prior[k] for k in ['preferencesRestoredExactly','profileRestoredExactly','controlledSettingsPermissionsPowerRestoredExactly'])
 original_env=json.loads((W/'landscape1-private-environment-before.json').read_text());original_xml=(W/'landscape1-private-before.xml').read_bytes();assert m.prefs()==original_xml
 before={'effective':env.fixed_rotation(m),'persistedPolicy':env.fixed_rotation_policy(m)};assert before=={'effective':False,'persistedPolicy':'disabled'}
 p.update(priorAttempt={'file':'landscape1.json','sha256':sha(prior_raw),'wholeAttemptPassed':False,'productMethodsPassed':1},before=before,target={'effective':False,'persistedPolicy':'default'},originalPreferencesSha256=sha(original_xml))
 baseline=m.snapshot('before-framework');p['originalDatabaseRowsSha256']=m.canonical(baseline)
 raw=read_policy_file();assert len(raw)<16384
 parsed=ET.fromstring(raw);displays=parsed.findall('display');assert len(displays)==1 and displays[0].get('fixedToUserRotation')=='1'
 stat=root('stat','-c','%u:%g:%a',fp).decode().strip();assert re.fullmatch(r'\d+:\d+:[0-7]{3,4}',stat)
 m.private_write(W/'rotation-recovery2-private-display-before.xml',raw)
 p['beforeDisplayFileSha256']=sha(raw);p['beforeDisplayFileStat']=stat
 server=m.sh('pidof','system_server');assert re.fullmatch(r'\d+',server)
 p['frameworkPidBefore']=server
 stage('stopping-owned-android-framework')
 need_start=True;root('stop')
 end=time.monotonic()+45
 while time.monotonic()<end:
  if not m.sh('pidof','system_server',allow_failure=True):break
  time.sleep(.25)
 assert not m.sh('pidof','system_server',allow_failure=True)
 # Stop writers before changing the single owned attribute. Preserve all other bytes.
 frozen=read_policy_file();node=ET.fromstring(frozen);d=node.findall('display');assert len(d)==1 and d[0].get('fixedToUserRotation')=='1'
 assert frozen.count(b'fixedToUserRotation="1"')==1
 restored=frozen.replace(b'fixedToUserRotation="1"',b'fixedToUserRotation="0"',1)
 check=ET.fromstring(restored);assert check.findall('display')[0].get('fixedToUserRotation')=='0'
 before_others=dict(d[0].attrib);after_others=dict(check.findall('display')[0].attrib)
 before_others.pop('fixedToUserRotation');after_others.pop('fixedToUserRotation');assert before_others==after_others
 root('dd','of='+tmp,data=restored);owner,group,mode=stat.split(':');root('chown',owner+':'+group,tmp);root('chmod',mode,tmp);root('mv',tmp,fp)
 assert read_policy_file()==restored and root('stat','-c','%u:%g:%a',fp).decode().strip()==stat
 p.update(onlyOwnedAttributeChanged=True,otherFileBytesPreserved=True,displayFileOwnerModePreserved=True,restoredDisplayFileSha256=sha(restored))
 stage('restarting-framework-to-load-original-default')
 root('setprop','sys.boot_completed','0');root('start');need_start=False
 end=time.monotonic()+600
 while time.monotonic()<end:
  current=m.sh('pidof','system_server',allow_failure=True)
  if current and current!=server and m.sh('getprop','sys.boot_completed')=='1':break
  time.sleep(2)
 assert current and current!=server and m.sh('getprop','sys.boot_completed')=='1'
 p['frameworkPidAfter']=current;p['frameworkRestarted']=True
 m.wake();m.sh('input','keyevent','3');m.wait(lambda:any(n.get('package')=='com.android.launcher3' for n in m.tree().iter('node')),'Actual Launcher after policy reload',180)
 stage('verifying-original-data-and-owned-settings')
 assert m.dump() is None;m.force_stop()
 after={'effective':env.fixed_rotation(m),'persistedPolicy':env.fixed_rotation_policy(m)};assert after=={'effective':False,'persistedPolicy':'default'}
 equal,current_env,failures=env.restore_environment(m,original_env);assert equal and not failures
 assert m.prefs()==original_xml
 afterdb=m.snapshot('after-framework');assert m.canonical(afterdb)==m.canonical(baseline)
 p.update(after=after,policyAndEffectiveRestoredExactly=True,preferencesRestoredExactly=True,profileRestoredExactly=env.local_profile(m)==env.profile_from_xml(original_xml),controlledEnvironmentExactlyRestored=True,databaseExactlyRestored=True)
 assert p['profileRestoredExactly'];p['passed']=True
except BaseException as failure:
 p['failureType']=type(failure).__name__;p['failure']=str(failure) if isinstance(failure,AssertionError) else 'Inspect preserved local recovery log';stage('failed')
finally:
 if need_start:
  try:root('start');p['frameworkStartAttemptedInFinally']=True
  except BaseException as failure:p['frameworkStartFailureType']=type(failure).__name__
 p['driverSourceUnchanged']=sha(Path(__file__).read_bytes())==p['executedDriverSha256'];p['passed']=bool(p['passed'] and p['driverSourceUnchanged'])
 p['completed']=True;p['finishedAtUtc']=env.now();stage('complete-passed' if p['passed'] else 'complete-failed')
 if p['passed']:
  target=E/'landscape1-manual-restoration.json';assert not target.exists();target.write_text(json.dumps(p,ensure_ascii=False,indent=2)+'\n')
raise SystemExit(0 if p['passed'] else 1)
