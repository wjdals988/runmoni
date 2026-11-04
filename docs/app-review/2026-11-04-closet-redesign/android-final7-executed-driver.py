import datetime,hashlib,json,re,subprocess,time,xml.etree.ElementTree as ET
from pathlib import Path
R=Path('/workspace/runmoni');W=R/'work/closet-redesign';E=R/'docs/app-review/2026-11-04-closet-redesign'
ADB=[str(R/'work/cloud-android/sdk/platform-tools/adb'),'-s','emulator-5556']
ATTEMPT='android-final7';PRIVATE=W/(ATTEMPT+'-private-before.xml');PROOF=E/(ATTEMPT+'.json')
assert not PRIVATE.exists() and not PROOF.exists()
def call(*args,timeout=45):return subprocess.check_output(ADB+list(args),timeout=timeout)
def sh(*args,timeout=45):return call('shell',*args,timeout=timeout).decode().strip()
def guard(value,message):
 if not value:raise AssertionError(message)
def no_bak():
 names=call('exec-out','run-as','com.runmoni.app','ls','-1','shared_prefs').decode().splitlines()
 guard('runmoni.xml' in names and 'runmoni.xml.bak' not in names,'Stable current preferences required')
def read():return call('exec-out','run-as','com.runmoni.app','cat','shared_prefs/runmoni.xml')
def profile(raw):
 root=ET.fromstring(raw);return json.loads(next(n for n in root if n.get('name')=='localProfile').text)
def write(raw):
 no_bak();p=subprocess.run(ADB+['shell','-T','run-as','com.runmoni.app','dd','of=shared_prefs/runmoni.xml'],input=raw,capture_output=True,timeout=45)
 guard(p.returncode==0 and read()==raw,'Exact private preference write');sh('sync');no_bak()
subprocess.run(['python3',str(R/'scripts/cloud/avatar-review.py'),'status'],check=True)
guard(sh('getprop','qemu.hw.mainkeys')=='0','Software navigation bar required')
config=(R/'app/build/generated/source/buildConfig/debug/com/runmoni/app/BuildConfig.java').read_text()
guard('boolean FIREBASE_ENABLED = false;' in config,'Unconfigured debug only')
build=json.loads((E/'binary-build7.json').read_text());installed=[]
for package,binary in zip(['com.runmoni.app','com.runmoni.app.test'],build['binaries']):
 path=sh('pm','path',package);guard(path.startswith('package:/data/app/') and '\n' not in path,'Single installed package')
 digest=sh('sha256sum',path.removeprefix('package:'),timeout=60).split()[0]
 guard(digest==binary['sha256'],'Installed '+package+' matches build7')
 installed.append({'package':package,'sha256':digest,'matchesBuild7':True})
(E/'installed-build7.json').write_text(json.dumps({'installed':installed,'version':'0.3.4-dev','versionCode':16,'softwareNavigation':True},indent=2)+'\n')
sh('am','force-stop','com.runmoni.app');no_bak();before=read();original=profile(before)
PRIVATE.write_bytes(before);PRIVATE.chmod(0o600)
settings={k:sh('settings','get','global',k) for k in ['window_animation_scale','transition_animation_scale','animator_duration_scale']}
font=sh('settings','get','system','font_scale');density=sh('wm','density')
guard(font in ('1','1.0') and 'Override' not in density,'Begin in actual default density/font1')
root=ET.fromstring(before)
for kind,name,value in [('string','communityBackend','firebase'),('string','apiUrl','http://127.0.0.1:8787'),('boolean','communityGuest','true'),('boolean','publicLocation','false')]:
 old=next((n for n in root if n.get('name')==name),None)
 if old is not None:root.remove(old)
 n=ET.SubElement(root,kind,{'name':name})
 if kind=='boolean':n.set('value',value)
 else:n.text=value
proof={'attempt':ATTEMPT,'startedAtUtc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'binaryReference':'binary-build7.json','installedReference':'installed-build7.json','fixtureBackend':'firebase','firebaseConfigured':False,'softwareNavigationRequired':True,'baselineAndroidReference':'android-final6.json','baselineBinaryReference':'binary-build6.json','groups':[],'passed':False,'physicalDeviceVerified':False,'googleLoginVerified':False,'productionWritesVerified':False}
proof['systemUiHandling']={'exactSystemUiAnrWaitOnly':True,'actions':[]}
def clear_known_system_popup():
 sh('rm','-f','/data/local/tmp/closet-preflight.xml')
 result=sh('uiautomator','dump','/data/local/tmp/closet-preflight.xml',timeout=60)
 guard('dumped to:' in result.lower(),'Fresh preflight accessibility tree')
 tree=ET.fromstring(call('exec-out','cat','/data/local/tmp/closet-preflight.xml'))
 if not any(re.fullmatch(r"System UI (?:isn't|isn’t) responding",n.get('text','')) for n in tree.iter('node')):return
 waits=[n for n in tree.iter('node') if n.get('text')=='Wait'];guard(len(waits)==1,'One exact platform Wait action')
 name=ATTEMPT+'-system-ui-dialog-'+str(len(proof['systemUiHandling']['actions'])+1)+'.png'
 (E/name).write_bytes(call('exec-out','screencap','-p'))
 x,y,r,b=map(int,re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',waits[0].get('bounds')).groups())
 sh('input','tap',str((x+r)//2),str((y+b)//2));time.sleep(1)
 proof['systemUiHandling']['actions'].append({'action':'Wait','dialog':'System UI not responding','screenshot':name})
error=None;restore_error=None
try:
 write(ET.tostring(root,encoding='utf-8',xml_declaration=True))
 sh('run-as','com.runmoni.app','rm','-f','cache/design-review/native-hierarchy-back-focus.txt','cache/design-review/closet-navigation-window-bounds.txt')
 for k in settings:sh('settings','put','global',k,'0')
 def run_group(name,expected,classes):
  clear_known_system_popup()
  output=E/(ATTEMPT+'-'+name+'.txt');started=time.monotonic()
  with output.open('w') as log:
   p=subprocess.run(ADB+['shell','am','instrument','-w','-e','class',','.join(classes),'com.runmoni.app.test/androidx.test.runner.AndroidJUnitRunner'],stdout=log,stderr=subprocess.STDOUT,timeout=1500)
  raw=output.read_text();match=re.search(r'OK \((\d+) tests?\)',raw)
  passed=p.returncode==0 and match is not None and int(match.group(1))==expected and 'FAILURES!!!' not in raw and 'Process crashed' not in raw and 'INSTRUMENTATION_FAILED' not in raw
  proof['groups'].append({'name':name,'expected':expected,'passed':passed,'actualPassedTests':int(match.group(1)) if match else None,'output':output.name,'elapsedSeconds':round(time.monotonic()-started,2)})
  print(json.dumps(proof['groups'][-1]),flush=True)
  guard(passed,'Instrumentation group failed: '+name)
  guard(profile(read())==original,'Stored local profile preserved after '+name)
 sh('am','force-stop','com.runmoni.app');sh('wm','density','240');sh('settings','put','system','font_scale','2.0');time.sleep(2)
 run_group('native-320-font2',8,['com.runmoni.app.ui.RunmoniDialogDesignTest','com.runmoni.app.ui.ClosetNavigationNativeUiTest','com.runmoni.app.ui.HierarchyClosetLargeTextUxTest','com.runmoni.app.ui.RunningKitColorsNativeUiTest','com.runmoni.app.ui.ConsumerClosetTest'])
 sh('am','force-stop','com.runmoni.app');sh('wm','density','reset');sh('settings','put','system','font_scale',font);time.sleep(2)
 focus=call('exec-out','run-as','com.runmoni.app','cat','cache/design-review/native-hierarchy-back-focus.txt').decode()
 guard(focus=='appWindowFocusedBeforeNativeBack=true\n','Actual fixture focus before native key')
 (E/(ATTEMPT+'-native-back-focus.txt')).write_text(focus);proof['fixtureFocusVerifiedBeforeNativeBack']=True
 # Independently require a real nonzero navigation inset in every recorded touch case.
 bounds=call('exec-out','run-as','com.runmoni.app','cat','cache/design-review/closet-navigation-window-bounds.txt').decode()
 baseline_bounds=(E/'android-final6-navigation-bounds.txt').read_text().splitlines()[:2]
 guard(len(baseline_bounds)==2 and all('bottomInsetPx=48' in line for line in baseline_bounds),'Baseline6 default viewport bounds')
 bounds='Baseline6 default viewport (successful closet group):\n'+'\n'.join(baseline_bounds)+'\nFinal7 native320/font2:\n'+bounds
 entries=re.findall(r'bottomInsetPx=(\d+)',bounds);guard(entries and all(int(x)>0 for x in entries),'Actual software navigation inset is positive')
 (E/(ATTEMPT+'-navigation-bounds.txt')).write_text(bounds)
 with (W/(ATTEMPT+'-host-main.log')).open('w') as log:
  p=subprocess.run(['python3',str(E/'host-wardrobe-driver.py')],stdout=log,stderr=subprocess.STDOUT,timeout=1800)
 guard(p.returncode==0 and json.loads((E/'main-final2.json').read_text())['passed'],'Real Main external input flow passed')
 proof['hostMainReference']='main-final2.json';proof['passed']=True
except Exception as exc:
 error=exc;proof['failureType']=type(exc).__name__;proof['failure']=str(exc)
 try:
  name=ATTEMPT+'-failure-screen.png';(E/name).write_bytes(call('exec-out','screencap','-p'));proof['failureScreenshot']=name
 except Exception:proof['failureScreenshotUnavailable']=True
finally:
 try:
  sh('am','force-stop','com.runmoni.app');write(before);guard(read()==before and profile(read())==original,'Whole pre-attempt preferences restored')
  proof['currentPreAttemptPreferencesRestoredExactly']=True
 except Exception as exc:restore_error=exc;proof['currentPreAttemptPreferencesRestoredExactly']=False;proof['restorationFailureType']=type(exc).__name__
 sh('wm','density','reset');sh('settings','put','system','font_scale',font)
 for k,v in settings.items():sh('settings','delete','global',k) if v=='null' else sh('settings','put','global',k,v)
 proof['settingsRestoredExactly']=all(sh('settings','get','global',k)==v for k,v in settings.items()) and sh('settings','get','system','font_scale')==font and sh('wm','density')==density
 proof['driverSha256']=hashlib.sha256(Path(__file__).read_bytes()).hexdigest();proof['finishedAtUtc']=datetime.datetime.now(datetime.timezone.utc).isoformat()
 proof['passed']=proof['passed'] and restore_error is None and proof['settingsRestoredExactly'];PROOF.write_text(json.dumps(proof,ensure_ascii=False,indent=2)+'\n')
 print(json.dumps({'passed':proof['passed'],'restored':proof.get('currentPreAttemptPreferencesRestoredExactly'),'failure':proof.get('failure')},ensure_ascii=False),flush=True)
if not proof['passed']:raise SystemExit(1)
