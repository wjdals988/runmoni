"""Owned API29 AVD only. Preserve private XML; no authentication/production writes."""
import datetime,hashlib,json,re,subprocess,time,xml.etree.ElementTree as ET,io
from pathlib import Path
from PIL import Image
R=next(p for p in Path(__file__).resolve().parents if (p/'app/build.gradle.kts').exists());W=R/'work/startup-polish';E=Path(__file__).resolve().parent
A=[str(R/'work/cloud-android/sdk/platform-tools/adb'),'-s','emulator-5556'];NAME='review5';OUT=E/(NAME+'.json');assert not OUT.exists()
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
proof={'installedReference':NAME+'-installed.json','version':'0.3.5-dev','versionCode':17,'startedAtUtc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'avd':'Runmoni_Cloud_29','api':29,'acceleration':'TCG','binaryReference':'binary-build4.json','groups':[],'captures':[],'systemUiWaitActions':[],'accessibilityRetries':0,'passed':False,'physicalDeviceVerified':False,'googleLoginVerified':False,'productionWritesVerified':False,'android31RuntimeVerified':False}
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
  if result:return result
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
binary=json.loads((E/'binary-build4.json').read_text());installed=[]
for package,expected in zip(['com.runmoni.app','com.runmoni.app.test'],binary['binaries']):
 path=sh('pm','path',package).removeprefix('package:');guard(path.startswith('/data/app/') and '\n' not in path,'One installed APK');digest=sh('sha256sum',path,timeout=180).split()[0];guard(digest==expected['sha256'],'Installed binary matches verified build');installed.append({'package':package,'sha256':digest})
package_info=sh('dumpsys','package','com.runmoni.app');guard(re.search(r'versionName=0\.3\.5-dev(?:\s|$)',package_info) is not None and re.search(r'versionCode=17(?:\s|$)',package_info) is not None,'Actual installed package version17')
(E/(NAME+'-installed.json')).write_text(json.dumps({'installed':installed,'version':'0.3.5-dev','versionCode':17},indent=2)+'\n')
sh('am','force-stop','com.runmoni.app');stable();before=prefs();backup=W/(NAME+'-private-before.xml');guard(not backup.exists(),'Fresh private backup');backup.write_bytes(before);backup.chmod(0o600);original=decode(before);restore=(W/'pre-install-private.xml').read_bytes();ET.fromstring(restore)
settings={k:sh('settings','get','global',k) for k in ['window_animation_scale','transition_animation_scale','animator_duration_scale']};font=sh('settings','get','system','font_scale');density=sh('wm','density');guard(font in ('1','1.0') and 'Override' not in density,'Default viewport at start')
root=ET.fromstring(before)
for kind,key,value in [('string','communityBackend','firebase'),('string','apiUrl','http://127.0.0.1:8787'),('boolean','communityGuest','true'),('boolean','publicLocation','false')]:
 old=next((n for n in root if n.get('name')==key),None)
 if old is not None:root.remove(old)
 n=ET.SubElement(root,kind,{'name':key})
 if kind=='boolean':n.set('value',value)
 else:n.text=value
safe_raw=ET.tostring(root,encoding='utf-8',xml_declaration=True)
try:
 write(safe_raw)
 for key in settings:sh('settings','put','global',key,'0')
 def group(name,count,classes):
  tree();out=E/(NAME+'-'+name+'.txt');start=time.monotonic()
  with out.open('w') as log:p=subprocess.run(A+['shell','am','instrument','-w','-e','class',','.join(classes),'com.runmoni.app.test/androidx.test.runner.AndroidJUnitRunner'],stdout=log,stderr=subprocess.STDOUT,timeout=1500)
  raw=out.read_text();m=re.search(r'OK \((\d+) tests?\)',raw);passed=p.returncode==0 and m is not None and int(m.group(1))==count and not any(x in raw for x in ['FAILURES!!!','Process crashed','INSTRUMENTATION_FAILED'])
  proof['groups'].append({'name':name,'expected':count,'passed':passed,'actual':int(m.group(1)) if m else None,'output':out.name,'elapsedSeconds':round(time.monotonic()-start,2)});print(json.dumps(proof['groups'][-1]),flush=True);guard(passed,'Group failed: '+name);guard(profile()==original,'Stored profile preserved after '+name)
 prior=json.loads((E/'review4.json').read_text())
 guard(prior['preInstallPreferencesRestoredExactly'] and prior['settingsRestoredExactly'] and prior['binaryReference']=='binary-build4.json','Same-binary restored prior')
 prior_installed=json.loads((E/prior['installedReference']).read_text());guard(prior_installed['installed']==installed,'Exact same installed app/test SHA')
 for index,old in enumerate(prior['groups']):
  raw=(E/old['output']).read_text();guard(old['passed'] and old['actual']==old['expected'] and re.search(r'OK \('+str(old['actual'])+r' tests?\)',raw) is not None and 'FAILURES!!!' not in raw,'Adopt only actual passed raw groups')
  proof['groups'].append(dict(old,sourceReview='review3.json' if index<3 else 'review4.json'))
 proof['priorExecutionReuseNote']='All22 completed methods on exact same installed app/test SHA:13 from review3 and9 from review4. Only actual Main/splash capture rerun here; failed loading group and failed host command excluded.'
 bounds=(E/'review4-navigation-bounds.txt').read_bytes();(E/(NAME+'-navigation-bounds.txt')).write_bytes(bounds);proof['navigationEvidenceSourceReview']='review4.json'
 for suffix in ['chat-keyboard-geometry.txt','chat-ime-system-200.png']:(E/(NAME+'-'+suffix)).write_bytes((E/('review4-'+suffix)).read_bytes())
 proof['nativeFixtureSourceReview']='review4.json'
 # Seed only a local anonymous review profile; exact original XML is restored below.
 fixture=ET.fromstring(safe_raw);field=next(n for n in fixture if n.get('name')=='localProfile');p=json.loads(field.text);p['name']='런모니 러너'
 for key in ['hair','top','bottom','shoes','accessory','eyewear','wrist','waist','hydration','face','socks','headwear','ear','costume']:p['avatar'][key]=0
 p['avatar']['gender']='male';p['avatar']['runningKitColors']={'top':0,'bottom':0,'shoes':0};field.text=json.dumps(p,ensure_ascii=False);write(ET.tostring(fixture,encoding='utf-8',xml_declaration=True))
 for key,value in settings.items():
  sh('settings','delete','global',key) if value=='null' else sh('settings','put','global',key,value)
 sh('input','keyevent','3');tree()
 start=time.monotonic();launch_log=(E/(NAME+'-launch-command.txt')).open('w');launch=subprocess.Popen(A+['shell','am','start','-a','android.intent.action.MAIN','-c','android.intent.category.LAUNCHER','-f','0x10008000','-n','com.runmoni.app/.MainActivity'],stdout=launch_log,stderr=subprocess.STDOUT)
 samples=[]
 for i in range(30):
  raw=shot();file=E/(NAME+'-launch-sample-'+str(i)+'.png');file.write_bytes(raw);im=Image.open(io.BytesIO(raw)).convert('RGB');count=sum(1 for y in range(375,475) for x in range(190,290) if max(abs(im.getpixel((x,y))[c]-v) for c,v in enumerate((36,92,69)))<8)
  canvas=all(max(abs(im.getpixel(pt)[c]-v) for c,v in enumerate((247,247,242)))<8 for pt in [(120,427),(360,427),(240,250),(240,600)])
  samples.append({'file':file.name,'sha256':hashlib.sha256(raw).hexdigest(),'hostCaptureOffsetMs':round((time.monotonic()-start)*1000),'centerLauncherGreenPixels':count,'surroundingCanvasMatches':canvas,'splashCandidate':count>1000 and canvas})
  if count>1000 and canvas:break
  if time.monotonic()-start>20:break
  time.sleep(.15)
 guard(launch.wait(timeout=60)==0,'Launcher command return code');launch_log.close();guard(not any(x in (E/(NAME+'-launch-command.txt')).read_text() for x in ['Exception','Error:']),'Launcher command accepted');proof['launchSamples']=samples;proof['launchSampleNote']='Raw Android captures; host offsets include ADB overhead and are not total startup performance. Candidate requires visual inspection. API31 exit120ms is configuration, not measured here.'
 for key in settings:sh('settings','put','global',key,'0')
 tap('나');tap('미니미 꾸미기');tap('블루 긴팔');wait(lambda:profile()['avatar']['top']==2,'Actual equipment stored');capture('closet','옷장')
 tap('닫기');tap('홈');capture('home','러닝 시작하기');tap('나');capture('profile','프로필')
 tap('계정 및 러닝 설정',True);capture('settings','설정');reveal('앱 정보 · 업데이트');tap('앱 정보 · 업데이트');capture('app-info','앱 정보');tap('닫기')
 pid=sh('pidof','com.runmoni.app');sh('input','keyevent','3');sh('am','start','-a','android.intent.action.MAIN','-c','android.intent.category.LAUNCHER','-n','com.runmoni.app/.MainActivity');wait(lambda:bool(nodes(tree(),'프로필')),'Profile destination preserved on hot resume');guard(sh('pidof','com.runmoni.app')==pid and focused(),'Same real Main process resumes');capture('resumed-profile','프로필');proof['hotResume']={'samePid':True,'destinationPreserved':True,'mainFocused':True}
 guard(profile()['mileage']==original['mileage'] and set(original['owned'])<=set(profile()['owned']),'Original points/ownership preserved');proof['passed']=True
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
