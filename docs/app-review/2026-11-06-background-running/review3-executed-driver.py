"""Owned API29 AVD only. Preserve private XML; no authentication/production writes."""
import datetime,hashlib,json,re,subprocess,time,xml.etree.ElementTree as ET,io
from pathlib import Path
from PIL import Image
R=next(p for p in Path(__file__).resolve().parents if (p/'app/build.gradle.kts').exists());W=R/'work/background-running';E=Path(__file__).resolve().parent
A=[str(R/'work/cloud-android/sdk/platform-tools/adb'),'-s','emulator-5556'];NAME='review3';OUT=E/(NAME+'.json');assert not OUT.exists()
def call(*args,timeout=60):return subprocess.check_output(A+list(args),timeout=timeout)
def sh(*args,timeout=60):return call('shell',*args,timeout=timeout).decode().strip()
def guard(x,label):
 if not x:raise AssertionError(label)
def prefs():return call('exec-out','run-as','com.runmoni.app','cat','shared_prefs/runmoni.xml')
def stable():guard('runmoni.xml.bak' not in call('exec-out','run-as','com.runmoni.app','ls','-1','shared_prefs').decode().splitlines(),'No pending preference backup')
def write(raw):
 stable();p=subprocess.run(A+['shell','-T','run-as','com.runmoni.app','dd','of=shared_prefs/runmoni.xml'],input=raw,capture_output=True,timeout=60)
 guard(p.returncode==0 and prefs()==raw,'Exact private preference write');sh('sync');stable()
def decode(raw):return json.loads(next(n.text for n in ET.fromstring(raw) if n.get('name')=='localProfile'))
def profile():return decode(prefs())
def rect(n):return tuple(map(int,re.fullmatch(r'\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]',n.get('bounds','')).groups()))
def visible(n):
 x,y,r,b=rect(n);return r-x>=10 and b-y>=10 and 24<=(y+b)/2<805 and 10<(x+r)/2<470
def shot():
 raw=call('exec-out','screencap','-p');guard(raw.startswith(b'\x89PNG') and len(raw)<2000000,'Bounded native Android PNG');return raw
proof={'installedReference':NAME+'-installed.json','version':'0.3.7-dev','versionCode':19,'startedAtUtc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'avd':'Runmoni_Cloud_29','api':29,'acceleration':'TCG','binaryReference':'binary-build2.json','groups':[],'captures':[],'systemUiWaitActions':[],'accessibilityRetries':0,'passed':False,'physicalDeviceVerified':False,'googleLoginVerified':False,'productionWritesVerified':False,'android31RuntimeVerified':False}
def tree():
 for attempt in range(8):
  try:
   sh('rm','-f','/data/local/tmp/startup-review.xml');result=sh('uiautomator','dump','/data/local/tmp/startup-review.xml')
   guard('dumped to:' in result.lower(),'Fresh accessibility dump');t=ET.fromstring(call('exec-out','cat','/data/local/tmp/startup-review.xml'));guard(bool(list(t)),'Nonempty accessibility root')
   if any(re.fullmatch("System UI (?:isn't|isn’t) responding",n.get('text','')) for n in t.iter('node')):
    waits=[n for n in t.iter('node') if n.get('text')=='Wait'];guard(len(waits)==1,'Exact system Wait only');name=NAME+'-system-ui-'+str(len(proof['systemUiWaitActions'])+1)+'.png';(E/name).write_bytes(shot());x,y,r,b=rect(waits[0]);sh('input','tap',str((x+r)//2),str((y+b)//2));proof['systemUiWaitActions'].append({'dialog':'System UI not responding','action':'Wait','file':name});continue
   return t
  except (AssertionError,ET.ParseError,subprocess.CalledProcessError,subprocess.TimeoutExpired):
   proof['accessibilityRetries']+=1
   if attempt==7:raise
   time.sleep(.4)
def nodes(t,label,desc=False):return [n for n in t.iter('node') if n.get('content-desc' if desc else 'text')==label and visible(n)]
def wait(fn,label,seconds=120):
 end=time.monotonic()+seconds
 while time.monotonic()<end:
  result=fn()
  if result is not None and result is not False:return result
  time.sleep(.3)
 raise AssertionError(label)
def tap(label,desc=False):
 t=wait(lambda:(v if nodes(v,label,desc) else None) if (v:=tree()) is not None else None,'Visible action '+label);n=nodes(t,label,desc)[-1];parents={c:p for p in t.iter() for c in p};p=n
 while p.get('clickable')!='true' and p in parents:p=parents[p]
 x,y,r,b=rect(p);sh('input','tap',str((x+r)//2),str((y+b)//2));time.sleep(.3)
def reveal(label):
 for _ in range(8):
  if nodes(tree(),label):return
  sh('input','swipe','240','730','240','350','350')
 raise AssertionError('Reveal '+label)
def focused():return any('mCurrentFocus=' in x and 'com.runmoni.app/com.runmoni.app.MainActivity' in x for x in sh('dumpsys','window','displays').splitlines())
def capture(name,anchor):
 wait(lambda:bool(nodes(tree(),anchor)),'Expected page '+anchor)
 def ready():
  t=tree();return not any(n.get('text')=='준비 중' or n.get('content-desc')=='미니미 준비 중' for n in t.iter('node'))
 wait(ready,'Artwork ready');guard(focused(),'Real Main window focused')
 activity=sh('dumpsys','activity','activities');guard(any('mResumedActivity' in x and 'com.runmoni.app/.MainActivity' in x for x in activity.splitlines()),'Real Main resumed')
 raw=shot();p=E/(NAME+'-'+name+'.png');guard(not p.exists(),'Immutable capture');p.write_bytes(raw)
 proof['captures'].append({'file':p.name,'sha256':hashlib.sha256(raw).hexdigest(),'bytes':len(raw),'width':int.from_bytes(raw[16:20],'big'),'height':int.from_bytes(raw[20:24],'big'),'activity':'com.runmoni.app/.MainActivity','mainWindowFocused':True,'kind':'real MainActivity UI, offline review profile'})
 print(json.dumps({'capture':name}),flush=True)
subprocess.run(['python3',str(R/'scripts/cloud/avatar-review.py'),'status'],check=True)
guard('boolean FIREBASE_ENABLED = false;' in (R/'app/build/generated/source/buildConfig/debug/com/runmoni/app/BuildConfig.java').read_text(),'Unconfigured debug only')
binary=json.loads((E/'binary-build2.json').read_text());installed=[]
for package,expected in zip(['com.runmoni.app','com.runmoni.app.test'],binary['binaries']):
 path=sh('pm','path',package).removeprefix('package:');guard(path.startswith('/data/app/') and '\n' not in path,'One installed APK');digest=sh('sha256sum',path,timeout=180).split()[0];guard(digest==expected['sha256'],'Installed binary matches verified build');installed.append({'package':package,'sha256':digest})
package_info=sh('dumpsys','package','com.runmoni.app');guard(re.search(r'versionName=0\.3\.7-dev(?:\s|$)',package_info) is not None and re.search(r'versionCode=19(?:\s|$)',package_info) is not None,'Actual installed package version19')
(E/(NAME+'-installed.json')).write_text(json.dumps({'installed':installed,'version':'0.3.7-dev','versionCode':19},indent=2)+'\n')
sh('am','force-stop','com.runmoni.app');stable();before=prefs();backup=W/(NAME+'-private-before.xml');guard(not backup.exists(),'Fresh private backup');backup.write_bytes(before);backup.chmod(0o600);original=decode(before);restore=(W/'pre-install-private.xml').read_bytes();ET.fromstring(restore)
settings={k:sh('settings','get','global',k) for k in ['window_animation_scale','transition_animation_scale','animator_duration_scale']};font=sh('settings','get','system','font_scale');density=sh('wm','density');guard(font in ('1','1.0') and 'Override' not in density,'Default viewport at start')
root=ET.fromstring(before)
for kind,key,value in [('string','communityBackend','firebase'),('string','apiUrl','http://127.0.0.1:8787'),('boolean','communityGuest','true'),('boolean','publicLocation','false'),('boolean','voiceGuidance','false'),('boolean','autoPause','false'),('string','appEntryChoiceV1','guest')]:
 old=next((n for n in root if n.get('name')==key),None)
 if old is not None:root.remove(old)
 n=ET.SubElement(root,kind,{'name':key})
 if kind=='boolean':n.set('value',value)
 else:n.text=value
safe_raw=ET.tostring(root,encoding='utf-8',xml_declaration=True)
try:
 write(safe_raw)
 sh('run-as','com.runmoni.app','rm','-f','cache/design-review/entry-window-bounds.txt')
 for key in settings:sh('settings','put','global',key,'0')
 def group(name,count,classes):
  tree();out=E/(NAME+'-'+name+'.txt');start=time.monotonic()
  with out.open('w') as log:p=subprocess.run(A+['shell','am','instrument','-w','-e','class',','.join(classes),'com.runmoni.app.test/androidx.test.runner.AndroidJUnitRunner'],stdout=log,stderr=subprocess.STDOUT,timeout=1500)
  raw=out.read_text();m=re.search(r'OK \((\d+) tests?\)',raw);passed=p.returncode==0 and m is not None and int(m.group(1))==count and not any(x in raw for x in ['FAILURES!!!','Process crashed','INSTRUMENTATION_FAILED'])
  proof['groups'].append({'name':name,'expected':count,'passed':passed,'actual':int(m.group(1)) if m else None,'output':out.name,'elapsedSeconds':round(time.monotonic()-start,2)});print(json.dumps(proof['groups'][-1]),flush=True);guard(passed,'Group failed: '+name);guard(profile()==original,'Stored profile preserved after '+name)
 group('speed-recovery-data',6,['com.runmoni.app.data.RunSpeedPersistenceTest'])
 group('recording-regression',9,['com.runmoni.app.data.RunReliabilityTest','com.runmoni.app.data.RunPersistenceTest'])
 group('notification-factory',6,['com.runmoni.app.tracking.RunNotificationsTest'])
 group('notification-ui',6,['com.runmoni.app.ui.RunNotificationUiTest'])
 group('entry-startup',6,['com.runmoni.app.ui.AppEntryUiTest','com.runmoni.app.ui.StartupUiTest'])
 for name in ['run-notification-blocked-320-200-fixture','run-speed-360-100-fixture','run-speed-320-200-fixture']:
  raw=call('exec-out','run-as','com.runmoni.app','cat','cache/design-review/'+name+'.png')
  guard(raw.startswith(b'\x89PNG'),'Actual UI fixture image')
  file=E/(NAME+'-'+name+'.png');file.write_bytes(raw)
  proof.setdefault('uiFixtures',[]).append({'file':file.name,'sha256':hashlib.sha256(raw).hexdigest(),'kind':'ComponentActivity Compose-target viewport/font fixture; no actual GPS or Android notification interaction'})
 proof['passed']=True
except Exception as exc:
 proof['failureType']=type(exc).__name__;proof['failure']=str(exc)
 try:(E/(NAME+'-failure.png')).write_bytes(shot())
 except Exception:pass
finally:
 sh('am','force-stop','com.runmoni.app')
 try:write(restore);proof['preInstallPreferencesRestoredExactly']=prefs()==restore
 except Exception as exc:proof['preInstallPreferencesRestoredExactly']=False;proof['restoreErrorType']=type(exc).__name__
 sh('wm','density','reset');sh('settings','put','system','font_scale',font)
 for key,value in settings.items():sh('settings','delete','global',key) if value=='null' else sh('settings','put','global',key,value)
 proof['settingsRestoredExactly']=all(sh('settings','get','global',k)==v for k,v in settings.items()) and sh('settings','get','system','font_scale')==font and sh('wm','density')==density
 proof['passed']=proof['passed'] and proof['preInstallPreferencesRestoredExactly'] and proof['settingsRestoredExactly'];proof['driverSha256']=hashlib.sha256(Path(__file__).read_bytes()).hexdigest();proof['finishedAtUtc']=datetime.datetime.now(datetime.timezone.utc).isoformat();OUT.write_text(json.dumps(proof,ensure_ascii=False,indent=2)+'\n');print(json.dumps({'passed':proof['passed'],'restored':proof['preInstallPreferencesRestoredExactly'],'failure':proof.get('failure')}),flush=True)
if not proof['passed']:raise SystemExit(1)
