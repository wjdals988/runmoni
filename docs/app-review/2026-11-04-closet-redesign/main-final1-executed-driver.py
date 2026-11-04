"""Real MainActivity + Android input review on the owned AVD; never a test clock.

Backs up the current local XML privately BEFORE seeding an offline avatar fixture.
No authentication, production writes, profile export or secret values are logged.
"""
import colorsys, datetime, hashlib, io, json, re, subprocess, time
import xml.etree.ElementTree as ET
from pathlib import Path
from PIL import Image
R=next(p for p in Path(__file__).resolve().parents if (p/'app/build.gradle.kts').exists())
W=R/'work/closet-redesign'; E=Path(__file__).resolve().parent
ADB=[str(R/'work/cloud-android/sdk/platform-tools/adb'),'-s','emulator-5556']
NAME='main-final1'; PRIVATE=W/(NAME+'-private-prefs-before.xml'); PROOF=E/(NAME+'.json')
assert not PRIVATE.exists() and not PROOF.exists(),'Use a fresh immutable attempt name'
def adb(*args,timeout=45): return subprocess.check_output(ADB+list(args),timeout=timeout)
def shell(*args,timeout=45): return adb('shell',*args,timeout=timeout).decode().strip()
def guard(value,message):
 if not value: raise AssertionError(message)
def wait(fn,label,seconds=100):
 end=time.monotonic()+seconds
 while time.monotonic()<end:
  value=fn()
  if value is not None and value is not False:return value
  time.sleep(.3)
 raise AssertionError(label)
def prefs():return adb('exec-out','run-as','com.runmoni.app','cat','shared_prefs/runmoni.xml')
def no_preference_backup():
 names=adb('exec-out','run-as','com.runmoni.app','ls','-1','shared_prefs').decode().splitlines()
 guard('runmoni.xml' in names and 'runmoni.xml.bak' not in names,'Stable XML required; do not overwrite a pending SharedPreferences backup')
def decode(raw):
 root=ET.fromstring(raw);field=next(n for n in root if n.get('name')=='localProfile')
 value=json.loads(field.text);guard(isinstance(value.get('avatar'),dict),'Readable local avatar');return root,field,value
def profile():
 for attempt in range(20):
  try:return decode(prefs())[2]
  except (ET.ParseError,json.JSONDecodeError,StopIteration,subprocess.CalledProcessError):
   if attempt==19:raise
   time.sleep(.2)
def write_prefs(raw):
 no_preference_backup()
 result=subprocess.run(ADB+['shell','-T','run-as','com.runmoni.app','dd','of=shared_prefs/runmoni.xml'],input=raw,capture_output=True,timeout=45)
 guard(result.returncode==0,'Private fixture write succeeded')
 guard(prefs()==raw,'Exact private XML write verified');shell('sync')
 no_preference_backup()
def rect(n):
 m=re.fullmatch(r'\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]',n.get('bounds',''))
 return tuple(map(int,m.groups())) if m else (0,0,0,0)
def visible(n):
 x,y,r,b=rect(n);return r-x>=10 and b-y>=10 and 15<=(x+r)/2<=465 and 24<=(y+b)/2<=840
def tree():
 for attempt in range(5):
  try:
   shell('rm','-f','/data/local/tmp/runmoni-closet-review.xml')
   result=shell('uiautomator','dump','/data/local/tmp/runmoni-closet-review.xml')
   guard('dumped to:' in result.lower(),'Fresh accessibility tree')
   root=ET.fromstring(adb('exec-out','cat','/data/local/tmp/runmoni-closet-review.xml'))
   guard(bool(list(root)),'Nonempty accessibility root');return root
  except (AssertionError,ET.ParseError,subprocess.CalledProcessError,subprocess.TimeoutExpired):
   if attempt==4:raise
   time.sleep(.4)
def nodes(root,label):return [n for n in root.iter('node') if n.get('text')==label and visible(n)]
def tap(label,first=False):
 root=wait(lambda:(t if nodes(t,label) else None) if (t:=tree()) is not None else None,'Visible action '+label)
 candidates=nodes(root,label);n=min(candidates,key=lambda n:rect(n)[1]) if first else candidates[-1]
 parents={c:p for p in root.iter() for c in p};target=n;p=n
 while p in parents:
  p=parents[p]
  if p.get('clickable')=='true' or p.get('checkable')=='true':target=p;break
 x,y,r,b=rect(target)
 cy=(y+b)//2-36 if target is n and label in ('홈','나') else (y+b)//2
 shell('input','tap',str((x+r)//2),str(cy));time.sleep(.3)
def reveal(label,horizontal=False):
 for _ in range(7):
  t=tree()
  if nodes(t,label):return
  if horizontal:
   choices=[n for n in t.iter('node') if n.get('text') in categories and visible(n)]
   guard(bool(choices),'Category strip visible');cy=min(rect(n)[1] for n in choices)+20
   shell('input','swipe','440',str(cy),'40',str(cy),'320')
  else:shell('input','swipe','240','740','240','365','350')
 raise AssertionError('Reveal '+label)
categories=['헤어','모자','얼굴','상의','하의','세트','양말','신발','액세서리']
def tab(label):
 # Fixed tabs stay above the independently scrollable items.
 t=tree()
 if not nodes(t,label):
  # Return to the start when switching from shoes back to tops.
  if label in ('상의','하의'):
   options=[n for n in t.iter('node') if n.get('text') in categories and visible(n)]
   cy=min(rect(n)[1] for n in options)+20
   shell('input','swipe','40',str(cy),'440',str(cy),'320')
  reveal(label,horizontal=True)
 tap(label,first=True)
def item(slot,variant,label):
 tab({'top':'상의','bottom':'하의','shoes':'신발'}[slot]);reveal(label);tap(label)
 wait(lambda:profile()['avatar'].get(slot)==variant,'Committed item '+slot)
 guard(profile()['avatar']['runningKitColors'][slot]==0,'New item uses its own palette '+slot)
 operations.append({'action':'equipItem','slot':slot,'variant':variant,'committed':True,'ownPaletteRestored':True})
def color(region,value,label):
 tab({'top':'상의','bottom':'하의','shoes':'신발'}[region]);reveal(label);tap(label)
 wait(lambda:profile()['avatar']['runningKitColors'][region]==value,'Committed color '+region)
 operations.append({'action':'color','region':region,'value':value,'committed':True})
def launch():shell('am','start','-n','com.runmoni.app/.MainActivity');time.sleep(2)
def closet():tap('나');reveal('미니미 꾸미기');tap('미니미 꾸미기');wait(lambda:bool(nodes(tree(),'옷장')),'Closet title')
def home():tap('닫기');tap('홈');wait(lambda:bool(nodes(tree(),'러닝 시작하기')),'Home CTA')
def shot():
 raw=adb('exec-out','screencap','-p');guard(raw.startswith(b'\x89PNG') and len(raw)<4*1024*1024,'Bounded Android PNG');return raw
def capture(name,hue=None):
 activity=shell('dumpsys','activity','activities')
 guard(any('mResumedActivity' in line and 'com.runmoni.app/.MainActivity' in line for line in activity.splitlines()),'Real MainActivity must be resumed for every published screenshot')
 end=time.monotonic()+100
 while time.monotonic()<end:
  t=tree()
  guard(not any(n.get('content-desc')=='미니미 다시 불러오기' for n in t.iter('node')),'Avatar read did not fail')
  if any(n.get('text')=='준비 중' or n.get('content-desc')=='미니미 준비 중' for n in t.iter('node')):continue
  raw=shot();im=Image.open(io.BytesIO(raw)).convert('RGB');count=None
  if hue:
   avatars=[n for n in t.iter('node') if n.get('content-desc')=='나의 러닝 미니미' and visible(n)]
   if avatars:
    x,y,r,b=rect(max(avatars,key=lambda n:rect(n)[3]-rect(n)[1]))
   else:
    # Home's avatar is decorative (excluded from accessibility). Locate its
    # actual CTA and use the production font1 hero's96x128dp/16dp spacing.
    ctas=nodes(t,'러닝 시작하기')
    if not ctas:continue
    parents={c:p for p in t.iter() for c in p};cta=ctas[-1]
    while cta.get('clickable')!='true' and cta in parents:cta=parents[cta]
    left,top,right,bottom=rect(cta);x=right-96;r=right;y=top-16-128;b=top-16
   w=r-x;h=b-y;count=0
   for py in range(y+int(h*.4),y+int(h*.68)):
    for px in range(x+int(w*.28),x+int(w*.82)):
     hh,s,v=colorsys.rgb_to_hsv(*(c/255 for c in im.getpixel((px,py))))
     if hue[0]<=hh*360<=hue[1] and s>.3 and v>.45:count+=1
   if count<15:continue
  path=E/(NAME+'-'+name+'.png');guard(not path.exists(),'Immutable screenshot name');path.write_bytes(raw)
  captures.append({'file':path.name,'sha256':hashlib.sha256(raw).hexdigest(),'bytes':len(raw),'width':im.width,'height':im.height,'activity':'com.runmoni.app/.MainActivity','actualMatchingTopPixels':count,'hueRange':hue})
  print(json.dumps({'capture':name,'topPixels':count}),flush=True);return
 raise AssertionError('Actual rendered avatar '+name)
subprocess.run(['python3',str(R/'scripts/cloud/avatar-review.py'),'status'],check=True)
guard(shell('settings','get','system','font_scale') in ('1','1.0'),'Actual font1')
guard('Override' not in shell('wm','density'),'Actual default density')
pkg=shell('dumpsys','package','com.runmoni.app');guard('versionCode=16 ' in pkg and 'versionName=0.3.4-dev' in pkg,'Installed0.3.4/code16')
binary=json.loads((E/'binary-build5.json').read_text())
config=(R/'app/build/generated/source/buildConfig/debug/com/runmoni/app/BuildConfig.java').read_text()
guard(bool(re.search(r'boolean FIREBASE_ENABLED = false;',config)),'Only the unconfigured local debug binary is permitted')
installed=shell('pm','path','com.runmoni.app')
guard(installed.startswith('package:/data/app/') and installed.count('\n')==0,'One installed app APK')
installed_sha=shell('sha256sum',installed.removeprefix('package:'),timeout=60).split()[0]
guard(installed_sha==binary['binaries'][0]['sha256'],'Installed binary exactly matches the verified offline debug build')
settings={k:shell('settings','get','global',k) for k in ['window_animation_scale','transition_animation_scale','animator_duration_scale']}
shell('am','force-stop','com.runmoni.app');no_preference_backup();before_raw=prefs();root,field,before=decode(before_raw)
PRIVATE.write_bytes(before_raw);PRIVATE.chmod(0o600)
fixture=json.loads(json.dumps(before));fixture['name']='런모니 러너';a=fixture['avatar']
def fixture_preference(kind,name,value):
 entry=next((n for n in root if n.get('name')==name),None)
 if entry is not None:root.remove(entry)
 entry=ET.SubElement(root,kind,{'name':name})
 if kind=='boolean':entry.set('value',value)
 else:entry.text=value
fixture_preference('string','communityBackend','firebase')
fixture_preference('string','apiUrl','http://127.0.0.1:8787')
fixture_preference('boolean','communityGuest','true')
fixture_preference('boolean','publicLocation','false')
for key in ['hair','top','bottom','shoes','accessory','eyewear','wrist','waist','hydration','face','socks','headwear','ear','costume']:a[key]=0
a['gender']='male';a['runningKitColors']={'top':4,'bottom':2,'shoes':1};field.text=json.dumps(fixture,ensure_ascii=False)
operations=[];captures=[];proof={'version':'0.3.4-dev','versionCode':16,'verificationKind':'real MainActivity + external Android input, offline local avatar fixture','avd':'Runmoni_Cloud_29','androidApi':29,'acceleration':'TCG','startedAtUtc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'physicalDeviceVerified':False,'googleLoginVerified':False,'productionWritesVerified':False,'installedAppSha256':installed_sha,'binaryReference':'binary-build5.json','fixtureBackend':'firebase','firebaseConfigured':False,'operations':operations,'captures':captures,'passed':False}
error=None;restore_error=None
try:
 for key in settings:shell('settings','put','global',key,'0')
 write_prefs(ET.tostring(root,encoding='utf-8',xml_declaration=True));launch();closet()
 item('top',2,'블루 긴팔')
 guard(profile()['avatar']['runningKitColors']=={'top':0,'bottom':2,'shoes':1},'Only the newly selected top palette changed')
 item('bottom',1,'러닝 타이츠')
 guard(profile()['avatar']['runningKitColors']=={'top':0,'bottom':0,'shoes':1},'Only the newly selected bottom palette changed')
 item('shoes',2,'블루 러닝화');tab('상의')
 capture('male-blue-outfit-closet',(205,235));home();capture('male-blue-outfit-home',(205,235))
 closet();tap('여자');wait(lambda:profile()['avatar'].get('gender')=='female','Committed female base')
 item('top',3,'옐로 바람막이');color('top',4,'퍼플')
 tab('하의');tab('상의');tap('옐로 바람막이')
 guard(profile()['avatar']['runningKitColors']['top']==4,'Retapping the current item preserves its custom tint')
 operations.append({'action':'retapCurrentItem','slot':'top','customTintPreserved':True})
 capture('female-purple-outfit-closet',(250,295))
 shell('input','keyevent','4');wait(lambda:bool(nodes(tree(),'미니미 꾸미기')),'Actual Back returns to profile')
 guard('com.runmoni.app/.MainActivity' in shell('dumpsys','activity','activities'),'Back keeps MainActivity')
 operations.append({'action':'actualAndroidBack','returnedToProfile':True});tap('홈');capture('female-purple-outfit-home',(250,295))
 tap('나');capture('female-profile-sections')
 current=profile();guard(current.get('mileage')==before.get('mileage'),'Mileage preserved')
 guard(set(before.get('owned',[]))<=set(current.get('owned',[])),'Existing ownership preserved')
 guard(all(current['avatar'].get(k)==v for k,v in {'gender':'female','top':3,'bottom':1,'shoes':2}.items()),'Equipped fields persisted')
 old_pid=shell('pidof','com.runmoni.app');guard(old_pid.isdigit(),'Known old PID')
 shell('am','force-stop','com.runmoni.app')
 stopped=subprocess.run(ADB+['shell','pidof','com.runmoni.app'],capture_output=True,timeout=20)
 guard(stopped.returncode!=0 and not stopped.stdout.strip(),'Old process absent')
 launch();new_pid=shell('pidof','com.runmoni.app');guard(new_pid.isdigit() and new_pid!=old_pid,'New app process')
 guard(profile()==current,'Whole profile persisted through real process restart')
 closet();capture('female-outfit-after-restart',(250,295))
 proof['processRestart']={'oldPid':int(old_pid),'newPid':int(new_pid),'oldProcessAbsent':True,'wholeProfilePersisted':True}
 proof['passed']=True
except Exception as exc:error=exc;proof['failureType']=type(exc).__name__;proof['failure']=str(exc)
finally:
 try:
  shell('am','force-stop','com.runmoni.app');write_prefs(before_raw)
  guard(decode(prefs())[2]==before,'Current pre-attempt profile restored')
  # Leave the restored original process stopped; restoring a prior HTTP backend
  # must not start sync/network activity outside this offline fixture review.
  proof['currentPreAttemptXmlRestoredExactly']=True
 except Exception as exc:restore_error=exc;proof['currentPreAttemptXmlRestoredExactly']=False;proof['restoreFailureType']=type(exc).__name__
 for key,value in settings.items():
  shell('settings','delete','global',key) if value=='null' else shell('settings','put','global',key,value)
 proof['settingsRestored']={k:shell('settings','get','global',k) for k in settings}
 proof['settingsRestoredCorrectly']=proof['settingsRestored']==settings
 if not proof['settingsRestoredCorrectly']:restore_error=AssertionError('Animation settings were not restored')
 proof['driverSha256']=hashlib.sha256(Path(__file__).read_bytes()).hexdigest();proof['finishedAtUtc']=datetime.datetime.now(datetime.timezone.utc).isoformat()
 proof['passed']=proof['passed'] and restore_error is None;PROOF.write_text(json.dumps(proof,ensure_ascii=False,indent=2)+'\n')
 print(json.dumps({'passed':proof['passed'],'profileRestored':proof.get('currentPreAttemptXmlRestoredExactly'),'captureCount':len(captures),'failure':proof.get('failure')},ensure_ascii=False),flush=True)
if error or restore_error:raise SystemExit(1)
