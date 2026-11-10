"""Root-owned actual MainActivity review. No authentication, run creation or DB writes."""
from pathlib import Path
import importlib.util, json, hashlib, datetime, subprocess, re, os, time, argparse
import xml.etree.ElementTree as ET
R=Path('/workspace/runmoni');W=R/'work/discovery-refresh';E=R/'docs/app-review/2026-11-10-discovery-refresh'
os.umask(0o077)
p=argparse.ArgumentParser();p.add_argument('--name',required=True);a=p.parse_args();assert re.fullmatch(r'live\d+',a.name)
def module(name,path):
 s=importlib.util.spec_from_file_location(name,path);m=importlib.util.module_from_spec(s);s.loader.exec_module(m);return m
hpath=R/'work/running-experience/actual-ui-helper6.py'
m=module('actual_android_helpers',hpath);env=module('discovery_review_environment',W/'android-last.py')
m.W=W;m.E=E;m.NAME=a.name;m.VERSION='0.4.0-dev';m.CODE=22
sha=env.sha;out=E/(a.name+'.json');assert not out.exists()
P={'name':a.name,'version':'0.4.0-dev','versionCode':22,'api':29,'avd':'Runmoni_Cloud_29','acceleration':'TCG','binaryReference':'binary-build8.json','passed':False,'completed':False,'captures':[],
 'startedAtUtc':env.now(),'physicalDeviceVerified':False,'googleLoginVerified':False,'productionWritesVerified':False,
 'android31RuntimeVerified':False,'signedReleaseRuntimeVerified':False,'outdoorGpsVerified':False,'dozeVerified':False,'oemBatteryPolicyVerified':False,'syntheticGps':True,
 'databaseWritesByReview':False,'sourceKind':'actual Android MainActivity','systemUiWaitActions':[],'accessibilityRetries':0}
m.P=P
can=False;old=None;before=None;base=None
raw=Path(__file__).read_bytes();env.exclusive_bytes(E/(a.name+'-executed-driver.py'),raw)
env.exclusive_bytes(E/(a.name+'-actual-ui-helper.py'),hpath.read_bytes())
envpath=W/'android-last.py';env.exclusive_bytes(E/(a.name+'-environment-helper.py'),envpath.read_bytes())
P.update(environmentHelper=a.name+'-environment-helper.py',environmentHelperSha256=sha(envpath.read_bytes()))
P.update(executedDriver=a.name+'-executed-driver.py',executedDriverSha256=sha(raw),observerHelper=a.name+'-actual-ui-helper.py',observerHelperSha256=sha(hpath.read_bytes()))
def stage(label):P['stage']=label;P['updatedAtUtc']=env.now();env.atomic_json(out,P);print(label,flush=True)
def exists(text,desc=False):return bool(m.nodes(m.tree(),text,desc))
def fulltap(text,desc=False):
 root=m.tree();hits=m.nodes(root,text,desc);assert len(hits)==1,(text,len(hits));m.tap_node(hits[0],root)
 P.setdefault('actualTaps',[]).append({'label':text,'description':desc,'bounds':list(m.rect(hits[0]))})
def capture(suffix):m.save_png(a.name+'-'+suffix+'.png','main')
def header_observation():
 root=m.tree();buttons=m.nodes(root,'러닝 시작하기');tops=[m.rect(n)[1] for n in buttons]
 if len(tops)!=1:return None
 limit=tops[0]
 def choose(pattern):return [n for n in root.iter('node') if n.get('package')==m.PKG and re.fullmatch(pattern,n.get('text','')) and m.rect(n)[3]<=limit]
 dates=choose(r'\d+월 \d+일 [월화수목금토일]요일');times=choose(r'\d{2}:\d{2}');temps=choose(r'-?\d+°C')
 if not(len(dates)==len(times)==len(temps)==1):return None
 return {'dateBounds':list(m.rect(dates[0])),'timeBounds':list(m.rect(times[0])),'temperatureBounds':list(m.rect(temps[0])),'runButtonBounds':list(m.rect(buttons[0]))}

try:
 stage('guards')
 assert m.adb('emu','avd','name').decode().splitlines()[0]=='Runmoni_Cloud_29'
 assert m.sh('getprop','ro.build.version.sdk')=='29'
 assert m.dump() is None
 assert not m.pid()
 braw=(E/P['binaryReference']).read_bytes();b=json.loads(braw);P['binaryReferenceSha256']=sha(braw)
 assert b['version']==P['version'] and b['versionCode']==22
 P['sourceMapsBefore']=env.source_maps(b);P['installed']=[]
 for package,ref in zip([m.PKG,m.PKG+'.test'],b['binaries']):
  path=m.sh('pm','path',package).removeprefix('package:');digest=m.sh('sha256sum',path).split()[0];assert digest==ref['sha256']
  P['installed'].append({'package':package,'sha256':digest})
 cfg=(R/'app/build/generated/source/buildConfig/debug/com/runmoni/app/BuildConfig.java').read_text();assert 'boolean FIREBASE_ENABLED = false;' in cfg
 before=env.environment_snapshot(m);m.private_write(W/(a.name+'-private-environment-before.json'),(json.dumps(before)+'\n').encode())
 old=m.prefs();ET.fromstring(old);m.private_write(W/(a.name+'-private-before.xml'),old)
 base=m.snapshot('before');assert all(row['finishedAtMillis']>0 for row in base['runs'])
 P['originalPreferencesSha256']=sha(old);P['originalDatabaseRowsSha256']=m.canonical(base)
 fixture=ET.fromstring(old)
 for kind,key,value in [('string','communityBackend','firebase'),('string','apiUrl','http://127.0.0.1:8787'),('string','appEntryChoiceV1','guest'),('boolean','communityGuest','true'),('boolean','publicLocation','false'),('boolean','voiceGuidance','false'),('boolean','autoPause','false')]:m.set_pref(fixture,kind,key,value)
 profile=next(n for n in fixture if n.get('name')=='localProfile');local=json.loads(profile.text);local['name']='검토 러너';profile.text=json.dumps(local,ensure_ascii=False)
 can=True;m.write_prefs(ET.tostring(fixture,encoding='utf-8',xml_declaration=True))
 env.viewport(m,False)
 for key in ['window_animation_scale','transition_animation_scale','animator_duration_scale']:m.sh('settings','put','global',key,'0')
 for permission in ['android.permission.ACCESS_FINE_LOCATION','android.permission.ACCESS_COARSE_LOCATION']:m.sh('pm','grant',m.PKG,permission)
 m.sh('settings','put','secure','location_mode','3')
 stage('home-weather-location-request')
 m.adb('emu','geo','fix','126.978','37.5665')
 m.launch();m.tap_tab('홈')
 for _ in range(3):m.adb('emu','geo','fix','126.978','37.5665');time.sleep(1)
 # The provider attribution is visible only after a parsed native forecast; it is not injected into AppState.
 def ready():
  root=m.tree()
  return any(n.get('text','').startswith('예보: MET Norway') for n in root.iter('node'))
 if not ready():
  # Current forecast content sits after the home hero. Scroll the page, not a map.
  m.sh('input','swipe','240','650','240','450','300')
 m.wait(ready,'Native MET forecast rendered after synthetic Seoul GPS',60)
 P['nativeWeatherDisplayed']=True;P['nativeWeatherHttpStatusMeasured']=False;P['weatherRequestCoordinatesIndependentlyLogged']=False
 if exists('날씨 다시 확인'):fulltap('날씨 다시 확인')
 for _ in range(3):m.adb('emu','geo','fix','126.978','37.5665');time.sleep(1)
 # Return to the hero so date/time/header temperature and the forecast are reviewed together.
 m.sh('input','swipe','240','350','240','700','300');time.sleep(1)
 P['headerDateTimeTemperatureBounds']=m.wait(header_observation,'Date time and current temperature all inside the home hero',60)
 P['homeDateTimeAndTemperatureObserved']=True
 capture('home-weather')
 stage('explore-map')
 m.tap_tab('탐색');m.wait(lambda:exists('근처 러너'),'Explore map screen')
 m.wait(lambda:exists('© OpenStreetMap contributors'),'Actual visible basemap attribution',60)
 # Cached or newly fetched native tiles are both real map display. A fresh tile HTTP status is not inferred.
 time.sleep(5)
 P['mapAttributionVisible']=True;P['mapFreshHttpStatusMeasured']=False
 w,h=m.dimensions();m.sh('input','swipe',str(w//2),str(h*4//10),str(w//3),str(h*5//10),'450')
 fulltap('지도 옵션',True);fulltap('지도 확대');time.sleep(10);capture('explore-map')
 assert not exists('크루 검색');P['exploreCrewPartyListAbsent']=True
 stage('unified-profile')
 fulltap('프로필 연결');m.wait(lambda:exists('닉네임'),'Shared profile editor opened from Explore')
 root=m.tree();fields=[n for n in root.iter('node') if n.get('class')=='android.widget.EditText' and n.get('package')==m.PKG]
 assert len(fields)==1
 m.tap_node(fields[0],root);m.sh('input','keyevent','123');m.sh('input','keyevent',*(['67']*len(local['name'])))
 m.sh('input','text','RunnerReview')
 m.wait(lambda:any(n.get('class')=='android.widget.EditText' and n.get('text')=='RunnerReview' for n in m.tree().iter('node')),'Actual editable nickname changed')
 fulltap('저장');m.wait(lambda:exists('근처 러너'),'Local save acknowledgement closes to Explore')
 saved=m.wait(lambda:env.local_profile(m) if (env.local_profile(m) or {}).get('name')=='RunnerReview' else None,'Nickname persisted in actual private preferences')
 assert all(saved.get(key)==value for key,value in local.items() if key!='name')
 P['localProfileSaveVerified']=True;P['localSavePreservedOtherProfileFields']=True
 m.tap_tab('나');m.reveal('프로필 편집');assert exists('RunnerReview')
 fulltap('프로필 편집');m.wait(lambda:exists('닉네임'),'Same editor opened from Me')
 capture('unified-profile')
 # Closing the reopened editor is an actual physical top X tap, without another Save/Connect request.
 fulltap('닫기',True);m.wait(lambda:exists('프로필 편집'),'Me destination preserved')
 P['sameProfileEditorFromExploreAndMe']=True
 stage('information-close-and-parent')
 fulltap('계정 및 러닝 설정',True);m.reveal('앱 정보 · 업데이트')
 info_before=m.nodes(m.tree(),'앱 정보 · 업데이트');assert len(info_before)==1
 info_bounds_before=list(m.rect(info_before[0]));P['settingsAboutRowBoundsBefore']=info_bounds_before
 fulltap('앱 정보 · 업데이트')
 m.wait(lambda:exists('앱 정보'),'App information opened')
 capture('app-information');fulltap('닫기',True)
 m.wait(lambda:exists('설정'),'Information X returns to Settings')
 info_after=m.nodes(m.tree(),'앱 정보 · 업데이트');assert len(info_after)==1
 info_bounds_after=list(m.rect(info_after[0]));P['settingsAboutRowBoundsAfter']=info_bounds_after
 assert all(abs(left-right)<=2 for left,right in zip(info_bounds_before,info_bounds_after))
 width,height=m.dimensions();assert 0<=info_bounds_after[0]<info_bounds_after[2]<=width and 0<=info_bounds_after[1]<info_bounds_after[3]<=height
 P['informationCloseReturnsToScrolledSettings']=True
 fulltap('닫기');m.wait(lambda:exists('프로필 편집'),'Settings close preserves Me tab')
 P['sourceMapsAfter']=env.source_maps(b)
 P['passed']=True;stage('actions-passed-awaiting-restoration')
except BaseException as failure:
 P['passed']=False;P['failureType']=type(failure).__name__;P['failure']=str(failure) if isinstance(failure,AssertionError) else 'Inspect retained local diagnostics'
 if can:
  try:m.private_write(W/(a.name+'-private-failure.png'),m.adb('exec-out','screencap','-p'))
  except Exception:pass
 stage('failed-before-finally')
finally:
 if can:
  quiesced=False
  try:m.force_stop();quiesced=True
  except BaseException as failure:P['forceStopFailureType']=type(failure).__name__;P['passed']=False
  if quiesced:
   try:
    after=m.snapshot('after');P['databaseExactlyRestored']=m.canonical(after)==m.canonical(base)
   except BaseException as failure:P['databaseObservationFailureType']=type(failure).__name__;P['passed']=False
   # A failed DB observation must never skip restoration of the original settings/name.
   try:
    m.write_prefs(old);P['preferencesRestoredExactly']=m.prefs()==old;P['profileRestoredExactly']=env.local_profile(m)==env.profile_from_xml(old)
   except BaseException as failure:P['preferenceRestoreFailureType']=type(failure).__name__;P['passed']=False
  try:
   equal,after,failures=env.restore_environment(m,before);P['controlledEnvironmentExactlyRestored']=equal;P['restorationCommandFailures']=failures
   m.private_write(W/(a.name+'-private-environment-after.json'),(json.dumps(after)+'\n').encode())
  except BaseException as failure:P['environmentRestoreFailureType']=type(failure).__name__;P['passed']=False
 P['driverSourceUnchanged']=sha(Path(__file__).read_bytes())==P['executedDriverSha256']
 P['observerHelperSourceUnchanged']=sha(hpath.read_bytes())==P['observerHelperSha256']
 P['environmentHelperSourceUnchanged']=sha(envpath.read_bytes())==P['environmentHelperSha256']
 P['passed']=bool(P['passed'] and all(P.get(k) for k in ['driverSourceUnchanged','observerHelperSourceUnchanged','environmentHelperSourceUnchanged','databaseExactlyRestored','preferencesRestoredExactly','profileRestoredExactly','controlledEnvironmentExactlyRestored']))
 P['completed']=True;P['finishedAtUtc']=env.now();stage('complete-passed' if P['passed'] else 'complete-failed')
raise SystemExit(0 if P['passed'] else 1)
