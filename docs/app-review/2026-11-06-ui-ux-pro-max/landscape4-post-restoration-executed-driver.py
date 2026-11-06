from pathlib import Path
import importlib.util,json,os,hashlib,time,subprocess,datetime,xml.etree.ElementTree as ET
R=Path('/workspace/runmoni');W=R/'work/ui-ux-pro-max';E=R/'docs/app-review/2026-11-06-ui-ux-pro-max'
spec=importlib.util.spec_from_file_location('restore_android',W/'android-review.py');a=importlib.util.module_from_spec(spec);spec.loader.exec_module(a)
m=a.helper('landscape4-restoration');out=E/'landscape4-post-restoration.json'
assert not out.exists()
proof={'name':'landscape4-post-restoration','parentReference':'landscape4.json','parentSha256':a.sha((E/'landscape4.json').read_bytes()),'startedAtUtc':a.now(),'passed':False,'completed':False,'actualAdditionalProductMethods':0,'parentPassedRewritten':False,'restorationTiming':'Later controlled recovery; original failed finally is preserved','originalDisplayFileBytesOrExistenceRestoredClaimed':False}
rawdriver=Path(__file__).read_bytes();a.exclusive_bytes(E/'landscape4-post-restoration-executed-driver.py',rawdriver);proof.update(executedDriver='landscape4-post-restoration-executed-driver.py',executedDriverSha256=a.sha(rawdriver))
def checkpoint(phase):
 proof['phase']=phase;a.atomic_json(out,proof);print(phase,flush=True)
checkpoint('created')
try:
 parent=json.loads((E/'landscape4.json').read_text());assert parent['actualPassedProductMethods']==2 and all(x['passed'] for x in parent['groups']) and not parent['passed']
 before=json.loads((W/parent['privateEnvironmentBackup']['file']).read_text());original=(W/parent['privateXmlBackup']['file']).read_bytes();binary=json.loads((E/'binary-build3.json').read_text())
 proof['sourceMapsBefore']=a.source_maps(binary)
 assert m.adb('emu','avd','name').decode().splitlines()[0]=='Runmoni_Cloud_29' and m.sh('getprop','ro.build.version.sdk')=='29'
 assert not m.pid() and m.dump() is None and m.prefs()==original
 equal,after,failures=a.restore_environment(m,before);assert equal and not failures
 checkpoint('original-controlled-environment-restored-before-policy-repair')
 policyfile='/data/system/display_settings.xml'
 before_xml=a.shell(m,'su','0','cat',policyfile).encode();root=ET.fromstring(before_xml);displays=root.findall('display');assert len(displays)==1 and displays[0].get('fixedToUserRotation')=='1'
 # Android10 wm shell default incorrectly maps to DISABLED. Remove only the
 # owned display fixedToUserRotation attribute; all other parsed XML remains.
 expected=ET.fromstring(before_xml);expected.find('display').attrib.pop('fixedToUserRotation')
 repaired=ET.tostring(expected,encoding='utf-8',xml_declaration=True)
 before_stat=a.shell(m,'su','0','stat','-c','%a %u %g',policyfile)
 a.exclusive_bytes(W/'landscape4-display-policy-before.xml',before_xml,private=True)
 a.exclusive_bytes(W/'landscape4-display-policy-repaired.xml',repaired,private=True)
 temp='/data/local/tmp/runmoni-owned-display-policy.xml'
 m.adb('push',str(W/'landscape4-display-policy-repaired.xml'),temp)
 a.shell(m,'su','0','sh','-c','cat '+temp+' > '+policyfile)
 a.shell(m,'su','0','sync');a.shell(m,'rm','-f',temp)
 assert a.shell(m,'su','0','stat','-c','%a %u %g',policyfile)==before_stat
 actual=ET.fromstring(a.shell(m,'su','0','cat',policyfile));assert ET.tostring(actual)==ET.tostring(expected)
 proof['displayPolicyRepair']={'onlyAttributeRemoved':'fixedToUserRotation','ownedDisplayName':displays[0].get('name'),'beforePolicy':'disabled','desiredPolicy':'default','allOtherParsedXmlPreserved':True,'ownerGroupModePreserved':True,'cause':'Android10 wm shell maps default to DISABLED; API29 public AOSP source and actual persisted state matched'}
 boot_before=m.sh('cat','/proc/sys/kernel/random/boot_id');m.adb('reboot');checkpoint('owned-avd-reboot-requested-to-reload-original-policy')
 deadline=time.monotonic()+420
 while time.monotonic()<deadline:
  try:
   boot_now=m.sh('cat','/proc/sys/kernel/random/boot_id',timeout=12)
   if boot_now!=boot_before and m.sh('getprop','sys.boot_completed',timeout=12)=='1':break
  except Exception:pass
  time.sleep(3)
 else:raise AssertionError('Owned AVD reboot incomplete')
 proof['ownedAvdRebootObserved']=True;proof['bootIdChanged']=True
 m.force_stop();assert m.prefs()==original
 equal,after,failures=a.restore_environment(m,before)
 a.exclusive_bytes(W/'landscape4-post-restoration-private-environment-after.json',(json.dumps(after,indent=2)+'\n').encode(),private=True)
 proof['privateEnvironmentBackup']=parent['privateEnvironmentBackup']
 proof['privateEnvironmentAfter']={'file':'landscape4-post-restoration-private-environment-after.json','sha256':a.sha((W/'landscape4-post-restoration-private-environment-after.json').read_bytes()),'mode':'0600'}
 proof['environmentRestorationScope']=a.environment_restoration_scope()
 proof['preferencesRestoredExactly']=m.prefs()==original
 proof['profileRestoredExactly']=a.profile_from_xml(m.prefs())==a.profile_from_xml(original)
 proof['controlledSettingsPermissionsPowerRestoredExactly']=equal and not failures
 proof['restorationCommandFailures']=failures
 proof['fixedToUserRotation']={'before':parent['fixedToUserRotation']['before'],'beforePolicy':parent['fixedToUserRotation']['beforePolicy'],'after':a.fixed_rotation(m),'afterPolicy':a.fixed_rotation_policy(m)}
 rotation=proof['fixedToUserRotation'];rotation['restoredExactly']=rotation['before']==rotation['after'] and rotation['beforePolicy']==rotation['afterPolicy']
 proof['sourceMapsAfter']=a.source_maps(binary)
 proof['driverSourceUnchanged']=a.sha(Path(__file__).read_bytes())==proof['executedDriverSha256']
 proof['passed']=all(proof[k] for k in ['ownedAvdRebootObserved','bootIdChanged','preferencesRestoredExactly','profileRestoredExactly','controlledSettingsPermissionsPowerRestoredExactly','driverSourceUnchanged']) and rotation['restoredExactly']
except BaseException as failure:
 proof['failureType']=type(failure).__name__;proof['failure']=str(failure) if isinstance(failure,AssertionError) else 'Recovery operation failed; inspect private WORK log'
finally:
 proof['completed']=True;proof['finishedAtUtc']=a.now();checkpoint('complete-passed' if proof['passed'] else 'complete-failed')
raise SystemExit(0 if proof['passed'] else 1)
