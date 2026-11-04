"""Owned debug AVD review through real Android input; no instrumentation/test clock."""
import colorsys,datetime,hashlib,io,json,re,subprocess,time,xml.etree.ElementTree as ET
from pathlib import Path
from PIL import Image
R=next(p for p in Path(__file__).resolve().parents if (p/'app/build.gradle.kts').exists());W=R/'work/kit-colors';E=R/'docs/app-review/2026-11-04-kit-colors'
ADB=[str(R/'work/cloud-android/sdk/platform-tools/adb'),'-s','emulator-5556']
NAME='host-main-final';PRIVATE=W/(NAME+'-private-profile-before.json');PROOF=E/(NAME+'.json')
assert not PRIVATE.exists() and not PROOF.exists(),'Use a new review attempt name'
subprocess.run(['python3',str(R/'scripts/cloud/avatar-review.py'),'status'],check=True)
def adb(*args,timeout=40):return subprocess.check_output(ADB+list(args),timeout=timeout)
def shell(*args,timeout=40):return adb('shell',*args,timeout=timeout).decode().strip()
def guard(value,message):
 if not value:raise AssertionError(message)
def profile():
 for attempt in range(20):
  try:
   root=ET.fromstring(adb('exec-out','run-as','com.runmoni.app','cat','shared_prefs/runmoni.xml'))
   field=next(n for n in root if n.get('name')=='localProfile')
   result=json.loads(field.text);guard(isinstance(result.get('avatar'),dict),'Profile avatar readable');return result
  except (ET.ParseError,json.JSONDecodeError,StopIteration,subprocess.CalledProcessError):
   if attempt==19:raise
   time.sleep(.15)
def rect(n):
 a=re.fullmatch(r'\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]',n.get('bounds',''))
 return tuple(map(int,a.groups())) if a else (0,0,0,0)
def visible(n):
 x,y,r,b=rect(n);return r-x>=12 and b-y>=12 and 20<=(x+r)/2<=460 and 24<=(y+b)/2<=842
def tree():
 for attempt in range(5):
  try:
   shell('rm','-f','/data/local/tmp/runmoni-kit-review.xml')
   output=shell('uiautomator','dump','/data/local/tmp/runmoni-kit-review.xml',timeout=45)
   if 'dumped to:' not in output.lower():raise ValueError('Fresh accessibility root not ready')
   root=ET.fromstring(adb('exec-out','cat','/data/local/tmp/runmoni-kit-review.xml'))
   if not list(root):raise ValueError('Empty accessibility root')
   return root
  except (ET.ParseError,subprocess.CalledProcessError,subprocess.TimeoutExpired,ValueError):
   if attempt==4:raise
   time.sleep(.4)
def texts(root,label):return [n for n in root.iter('node') if n.get('text')==label and visible(n)]
def wait(check,label,seconds=90):
 end=time.monotonic()+seconds
 while time.monotonic()<end:
  result=check()
  if result is not None and result is not False:return result
  time.sleep(.3)
 raise AssertionError(label)
def wait_text(label):return wait(lambda:bool(texts(tree(),label)),'Visible '+label)
def tap(label):
 root=wait(lambda:(t if texts(t,label) else None) if (t:=tree()) is not None else None,'Target '+label)
 n=texts(root,label)[-1];parents={c:p for p in root.iter() for c in p};target=n;p=n
 while p in parents:
  p=parents[p]
  if p.get('clickable')=='true' or p.get('checkable')=='true':
   guard(p.get('enabled')=='true','Action enabled '+label);target=p;break
 x,y,r,b=rect(target)
 # If selected navigation metadata omits its action, use its 48dp icon area.
 cy=(y+b)//2-36 if target is n and label in ('홈','나') else (y+b)//2
 shell('input','tap',str((x+r)//2),str(cy));time.sleep(.4)
categories=['헤어','모자','얼굴','상의','하의','세트','양말','신발','액세서리'];active=3
regions={'top':'상의','bottom':'하의','shoes':'신발'};labels=['원본','민트','코랄','블루','퍼플','골드']
def tab(region):
 global active
 label=regions[region];wanted=categories.index(label);heading='기본 '+label
 root=tree()
 if texts(root,heading):active=wanted;return
 for _ in range(3):
  nodes=texts(root,label)
  if nodes:break
  anchors=[n for n in root.iter('node') if n.get('text') in categories and visible(n)]
  guard(bool(anchors),'Visible category strip')
  x,y,r,b=rect(anchors[0]);cy=(y+b)//2
  points=('40',str(cy),'440',str(cy)) if wanted<active else ('440',str(cy),'40',str(cy))
  shell('input','swipe',*points,'350');root=tree()
 guard(bool(texts(root,label)),'Category revealed '+label);tap(label);wait_text(heading);active=wanted
 operations.append({'phase':phase,'action':'category','region':region,'visibleContentConfirmed':True})
def color(region,value):
 tab(region);wait_text('색상은 기본형에 적용돼요');tap(labels[value])
 wait(lambda:profile()['avatar']['runningKitColors'][region]==value,'Committed '+region)
 operations.append({'phase':phase,'action':'fabricColor','region':region,'value':value,'committed':True})
def gender(value):
 tap('남자' if value=='male' else '여자')
 wait(lambda:profile()['avatar'].get('gender')==value,'Committed gender')
 operations.append({'phase':phase,'action':'gender','value':value,'committed':True})
def shot():
 raw=adb('exec-out','screencap','-p');guard(raw.startswith(b'\x89PNG') and len(raw)<4*1024*1024,'Bounded Android PNG');return raw
def ready_tree():
 t=tree()
 pending=any(n.get('text')=='준비 중' or n.get('content-desc')=='미니미 준비 중' for n in t.iter('node'))
 failed=any(n.get('content-desc')=='미니미 다시 불러오기' for n in t.iter('node'))
 guard(not failed,'Avatar load did not fail')
 return None if pending else t
def capture(name,hue=None):
 end=time.monotonic()+90
 while time.monotonic()<end:
  t=ready_tree()
  if t is None:continue
  raw=shot();im=Image.open(io.BytesIO(raw)).convert('RGB');count=None
  if hue:
   nodes=[n for n in t.iter('node') if n.get('content-desc')=='나의 러닝 미니미' and visible(n)]
   if not nodes:continue
   node=max(nodes,key=lambda n:rect(n)[3]-rect(n)[1]);x,y,r,b=rect(node);w=r-x;h=b-y;count=0
   for py in range(y+int(h*.4),y+int(h*.68)):
    for px in range(x+int(w*.28),x+int(w*.82)):
     hh,s,v=colorsys.rgb_to_hsv(*(c/255 for c in im.getpixel((px,py))))
     if hue[0]<=hh*360<=hue[1] and s>.3 and v>.45:count+=1
   if count<12:continue
  out=E/(NAME+'-'+name+'.png');guard(not out.exists(),'Capture name is immutable');out.write_bytes(raw)
  captures.append({'file':out.name,'sha256':hashlib.sha256(raw).hexdigest(),'bytes':len(raw),'width':im.width,'height':im.height,'topHueRange':hue,'actualMatchingTopPixels':count})
  print(json.dumps({'captured':name,'matchingTopPixels':count}),flush=True);return
 raise AssertionError('Actual rendered avatar '+name)
def invariant(a,b):
 keep=lambda p:{'name':p.get('name'),'mileage':p.get('mileage'),'owned':sorted(p.get('owned',[])),
  'equipment':{k:v for k,v in p['avatar'].items() if k not in ('gender','runningKitColors')}}
 return keep(a)==keep(b)
def launch():shell('am','start','-n','com.runmoni.app/.MainActivity');time.sleep(2)
def closet():tap('나');wait_text('미니미 꾸미기');tap('미니미 꾸미기');wait_text('기본 러닝복 색상')
def home():tap('닫기');tap('홈');wait_text('러닝 시작하기')
settings={k:shell('settings','get','global',k) for k in ['window_animation_scale','transition_animation_scale','animator_duration_scale']}
guard(shell('settings','get','system','font_scale') in ('1','1.0'),'Actual font1');guard('Override' not in shell('wm','density'),'Actual480px/density160')
pkg=shell('dumpsys','package','com.runmoni.app');guard('versionCode=15 ' in pkg and 'versionName=0.3.3-dev' in pkg,'Installed0.3.3/code15')
before=profile();PRIVATE.write_text(json.dumps(before,ensure_ascii=False));PRIVATE.chmod(0o600)
guard(before['avatar'].get('gender') in ('male','female'),'Known baseline gender')
guard(all(type(v) is int and v in range(6) for v in before['avatar']['runningKitColors'].values()),'Known baseline colors')
phase='verification';operations=[];captures=[];proof={'startedAtUtc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'verificationKind':'host-driven real Android input; no instrumentation or Compose test clock','avd':'Runmoni_Cloud_29','androidApi':29,'acceleration':'TCG','version':'0.3.3-dev','versionCode':15,'appSha256':'2b4b1f6509599b8aebdfb253fc746621070e10d218f8718fa422c8f973c3714c','installedBinaryReference':'final5-installed-verification.json','physicalDeviceVerified':False,'googleLoginVerified':False,'productionFirebaseWritesVerified':False,'originalPreFinal4CrashProfileRestorationVerified':False,'operations':operations,'captures':captures,'passed':False}
error=None;restore_error=None
try:
 for key in settings:shell('settings','put','global',key,'0')
 shell('am','force-stop','com.runmoni.app');launch();wait_text('러닝 시작하기');closet();gender('male')
 for region in regions:color(region,0)
 for region,value in [('top',1),('bottom',2),('shoes',3)]:color(region,value)
 guard(invariant(before,profile()),'Existing profile fields preserved after male colors')
 capture('male-mint-coral-blue-closet',(135,195));home();capture('male-mint-coral-blue-home')
 closet();gender('female')
 for region,value in [('top',4),('bottom',3),('shoes',5)]:color(region,value)
 guard(invariant(before,profile()),'Existing profile fields preserved after female colors')
 capture('female-purple-blue-gold-closet',(250,295));home();capture('female-purple-blue-gold-home')
 old_pid=shell('pidof','com.runmoni.app');guard(old_pid.isdigit(),'One running debug process')
 shell('am','force-stop','com.runmoni.app')
 stopped=subprocess.run(ADB+['shell','pidof','com.runmoni.app'],capture_output=True,timeout=20)
 guard(stopped.returncode!=0 and not stopped.stdout.strip(),'Old process absent after force-stop')
 launch();wait_text('러닝 시작하기')
 new_pid=shell('pidof','com.runmoni.app');guard(new_pid.isdigit() and new_pid!=old_pid,'Actual process PID changed')
 guard(profile()['avatar']['gender']=='female' and profile()['avatar']['runningKitColors']=={'top':4,'bottom':3,'shoes':5},'Persisted colors after process restart')
 guard(invariant(before,profile()),'Other profile fields preserved after process restart')
 closet();capture('female-colors-after-process-relaunch',(250,295))
 proof['processRelaunch']={'beforePid':int(old_pid),'afterPid':int(new_pid),'oldPidAbsentAfterForceStop':True,'newProcessConfirmed':True,'femaleColorsPreserved':True,'actualTopHueConfirmed':True}
 proof['passed']=True
except Exception as exc:
 error=exc;proof['failureType']=type(exc).__name__;proof['failure']=str(exc)
finally:
 phase='restore'
 try:
  # Restore only the changed avatar fields through the same production UI.
  root=tree()
  if not texts(root,'기본 러닝복 색상'):closet()
  gender(before['avatar']['gender'])
  for region in regions:color(region,before['avatar']['runningKitColors'][region])
  after=profile();guard(invariant(before,after) and after['avatar']==before['avatar'],'Current pre-attempt profile restored')
  proof['currentPreAttemptProfileRestored']=True
  home()
 except Exception as exc:restore_error=exc;proof['currentPreAttemptProfileRestored']=False;proof['restorationFailureType']=type(exc).__name__
 for key,value in settings.items():
  shell('settings','delete','global',key) if value=='null' else shell('settings','put','global',key,value)
 proof['restoredSettings']={k:shell('settings','get','global',k) for k in settings}
 proof['driverSha256']=hashlib.sha256(Path(__file__).read_bytes()).hexdigest()
 proof['finishedAtUtc']=datetime.datetime.now(datetime.timezone.utc).isoformat();proof['passed']=proof['passed'] and restore_error is None
 PROOF.write_text(json.dumps(proof,ensure_ascii=False,indent=2)+'\n')
 print(json.dumps({'passed':proof['passed'],'captures':len(captures),'profileRestored':proof.get('currentPreAttemptProfileRestored'),'failure':proof.get('failure')},ensure_ascii=False),flush=True)
if error or restore_error:raise SystemExit(1)
