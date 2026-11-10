from pathlib import Path
import importlib.util,json,os,hashlib,datetime,time,xml.etree.ElementTree as ET,subprocess,shlex
from zoneinfo import ZoneInfo
R=Path.cwd();W=R/'work/running-experience';E=R/'docs/app-review/2026-11-10-running-experience'
spec=importlib.util.spec_from_file_location('live_helper',W/'actual-ui-helper3.py');m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
spec=importlib.util.spec_from_file_location('env_helper',W/'android-review.py');env=importlib.util.module_from_spec(spec);spec.loader.exec_module(env)
m.W=W;m.E=E;m.NAME='live3';m.P={'captures':[],'systemUiWaitActions':[],'accessibilityRetries':0};P=m.P
P.update(version='0.3.9-dev',versionCode=21,passed=False,api=29,avd='Runmoni_Cloud_29',syntheticGps=True,physicalDeviceVerified=False,googleLoginVerified=False,android31RuntimeVerified=False,dozeVerified=False,startedAtUtc=env.now())
out=E/'live3.json';assert not out.exists();can=False;oldprefs=None;beforeenv=None;widgetbefore=None;oldwidget=None
os.umask(0o077)
base_focus=m.focus

def reviewed_focus(kind):
 if kind!='launcher':return base_focus(kind)
 window=m.sh('dumpsys','window','displays')
 current=next((line for line in window.splitlines() if 'mCurrentFocus=' in line),'')
 return 'com.android.launcher3' in current
m.focus=reviewed_focus

def launcher_png(name):
 assert reviewed_focus('launcher')
 activities=m.sh('dumpsys','activity','activities')
 assert any('mResumedActivity' in line and 'com.android.launcher3' in line for line in activities.splitlines())
 m.save_png(name,'launcher',focus_required=True)
 P['captures'][-1].update(sourceKind='actual Android Launcher3 AppWidget RemoteViews',launcherResumedActivityVerified=True)

def stage(name):
 P['stage']=name;env.atomic_json(out,P);print(name,flush=True)
def raw_widget():
 return m.adb('exec-out','run-as',m.PKG,'cat','shared_prefs/runmoni_widget.xml',allow_failure=True)
def widget_ids():
 import re
 text=m.sh('dumpsys','appwidget').split('Widgets:',1)[1].split('Hosts:',1)[0]
 ids=[];own=[]
 for block in re.split(r'(?=  \[\d+\] id=)',text):
  found=re.search(r' id=(\d+)',block)
  if found:
   ids.append(int(found[1]))
   if 'com.runmoni.app/' in block:own.append(int(found[1]))
 return {'all':sorted(ids),'runmoni':sorted(own)}

def find_widget(resource):
 m.sh('cmd','statusbar','collapse');m.wake();m.sh('input','keyevent','3')
 for direction in ([1]*3+[-1]*6):
  root=m.tree()
  found=[n for n in root.iter('node') if n.get('resource-id')=='com.runmoni.app:id/'+resource]
  if found:
   assert len(found)==1 and len(widget_ids()['runmoni'])==1
   return root,found[0]
  w,h=m.dimensions();x1,x2=(w*4//5,w//5) if direction==1 else (w//5,w*4//5)
  m.sh('input','swipe',str(x1),str(h//2),str(x2),str(h//2),'350')
 raise AssertionError('Only review Runmoni widget control not found: '+resource)

def click_widget(resource):
 root,node=find_widget(resource);bounds=list(m.rect(node));m.tap_node(node,root,climb=False)
 P.setdefault('actualWidgetTaps',[]).append({'resource':resource,'bounds':bounds})

def remove_widget():
 if widget_ids()==widgetbefore:return
 current=widget_ids();assert len(current['runmoni'])==1 and set(widgetbefore['all']).issubset(current['all'])
 root,node=find_widget('run_widget_root');l,t,r,b=m.rect(node);w,h=m.dimensions()
 m.sh('input','draganddrop',str((l+r)//2),str((t+b)//2),str(w//2+10),'55','1500')
 m.wait(lambda:widget_ids()==widgetbefore,'Original widget IDs after actual Remove animation',45)
 P['launcherWidgetLifecycle'].update(newReviewWidgetRemoved=True,originalRunmoniWidgetIdsPreserved=True,originalOtherWidgetIdsPreserved=True,afterRemovalIds=widget_ids(),removalMethod='Actual Launcher3 drag onto observed Remove target; wait for service IDs')

try:
 stage('guards')
 assert m.adb('emu','avd','name').decode().splitlines()[0]=='Runmoni_Cloud_29'
 assert m.dump() is None
 assert 'boolean FIREBASE_ENABLED = false;' in (R/'app/build/generated/source/buildConfig/debug/com/runmoni/app/BuildConfig.java').read_text()
 binary=json.loads((E/'binary-build7.json').read_text())
 for pkg,b in zip([m.PKG,m.PKG+'.test'],binary['binaries']):
  path=m.sh('pm','path',pkg).removeprefix('package:');assert m.sh('sha256sum',path).split()[0]==b['sha256']
 source=Path(__file__).read_bytes();env.exclusive_bytes(E/'live3-executed-driver.py',source);P['executedDriverSha256']=env.sha(source)
 helper=(W/'actual-ui-helper3.py').read_bytes();env.exclusive_bytes(E/'live3-actual-ui-helper.py',helper);P['helperSha256']=env.sha(helper)
 beforeenv=env.environment_snapshot(m);m.force_stop();oldprefs=m.prefs();m.private_write(W/'live3-private-before.xml',oldprefs)
 oldwidget=raw_widget();m.private_write(W/'live3-private-widget.xml',oldwidget)
 m.baseline=m.snapshot('baseline');assert all(x['finishedAtMillis']>0 for x in m.baseline['runs'])
 m.device_zone=ZoneInfo(m.sh('getprop','persist.sys.timezone') or 'UTC')
 P['databaseBefore']={'runs':len(m.baseline['runs']),'points':len(m.baseline['points']),'sha256':m.canonical(m.baseline)}
 widgetbefore=widget_ids();(W/'live3-private-widget-host.json').write_text(json.dumps(widgetbefore));assert not widgetbefore['runmoni']
 fixture=ET.fromstring(oldprefs)
 for kind,key,value in [('string','communityBackend','firebase'),('string','appEntryChoiceV1','guest'),('boolean','communityGuest','true'),('boolean','voiceGuidance','false'),('boolean','autoPause','false'),('boolean','publicLocation','false')]:m.set_pref(fixture,kind,key,value)
 profile=next((n for n in fixture if n.get('name')=='localProfile'),None)
 if profile is not None:
  anonymous=json.loads(profile.text);anonymous['name']='검토 러너';profile.text=json.dumps(anonymous,ensure_ascii=False)
  P['profileNameTemporarilyAnonymized']=True
 m.write_prefs(ET.tostring(fixture,encoding='utf-8',xml_declaration=True));can=True
 for key in ['window_animation_scale','transition_animation_scale','animator_duration_scale']:m.sh('settings','put','global',key,'0')
 for perm in ['android.permission.ACCESS_FINE_LOCATION','android.permission.ACCESS_COARSE_LOCATION']:m.sh('pm','grant',m.PKG,perm)
 m.sh('settings','put','secure','location_mode','3');env.viewport(m,False)
 m.launch();m.tap_tab('홈');time.sleep(10)
 if not m.baseline['runs']:
  m.save_png('live3-main-home.png','main')
 else:
  m.private_write(W/'live3-private-original-history-home.png',m.adb('exec-out','screencap','-p'))
  P['homePublication']='Existing history kept private; use separately labeled synthetic ComponentActivity home fixture.'
 m.tap_tab('러닝');m.reveal('러닝 옵션');m.tap('러닝 옵션');m.reveal('홈 화면에 러닝 위젯 추가');m.tap('홈 화면에 러닝 위젯 추가')
 stage('pin-prompt-open')
 root=m.tree();buttons=m.nodes(root,'ADD AUTOMATICALLY')
 assert len(buttons)==1 and buttons[0].get('package')=='com.android.launcher3'
 assert any('런모니' in (n.get('text','')+n.get('content-desc','')) for n in root.iter('node'))
 m.save_png('live3-pin-prompt.png','launcher',focus_required=False)
 m.tap_node(buttons[0],root,climb=False)
 m.wait(lambda:len(widget_ids()['runmoni'])==1,'Actual pin-created Runmoni widget',60)
 afterPin=widget_ids();newIds=set(afterPin['all'])-set(widgetbefore['all']);assert len(newIds)==1
 P['launcherWidgetLifecycle']={'beforeIds':widgetbefore,'afterPinIds':afterPin,'newReviewWidgetId':next(iter(newIds)),'actualOsPinAction':'ADD AUTOMATICALLY','originalWidgetIdsPreserved':set(widgetbefore['all']).issubset(afterPin['all'])}
 stage('pin-confirmed-by-root')
 m.start_run();m.inject(8)
 m.capture('main-running-map','main','러닝 기록 중')
 P['running']=m.running_settled()
 find_widget('run_widget_control')
 # The root inspects the widget screenshot and its actual control labels.
 launcher_png('live3-launcher-running.png')
 click_widget('run_widget_control');P['widgetPause']=m.paused_settled();stage('actual-widget-paused')
 launcher_png('live3-launcher-paused.png')
 click_widget('run_widget_control');P['widgetResume']=m.running_settled();stage('actual-widget-resumed')
 beforeOff=m.dump();m.sh('input','keyevent','223');m.wait(m.asleep,'Owned AVD screen asleep')
 m.inject(4);afterOff=m.running_settled()
 assert afterOff['pointCount']>beforeOff['pointCount'] and afterOff['distanceMeters']>beforeOff['distanceMeters'] and afterOff['activeDurationMillis']>beforeOff['activeDurationMillis']
 P['screenOffBackgroundSyntheticGps']={'before':beforeOff,'after':afterOff,'outdoorOrDozeClaimed':False};m.wake()
 m.open_shade();m.notification_control('일시정지','PAUSED');m.paused_settled();m.save_png('live3-systemui-paused.png','system')
 m.notification_control('재개','RUNNING');m.running_settled();m.sh('cmd','statusbar','collapse');m.sh('input','keyevent','3')
 click_widget('run_widget_finish');m.wait(lambda:bool(m.nodes(m.tree(),'오늘의 러닝을 마칠까요?')),'Widget finish opens Main confirmation')
 m.save_png('live3-finish-confirm.png','main');m.tap('취소');assert m.dump()['phase']=='RUNNING'
 click_widget('run_widget_finish');m.tap('저장하고 마치기')
 m.wait(lambda:bool(m.nodes(m.tree(),'러닝 기록')),'Actual independent record page')
 root=m.tree();assert not any(m.nodes(root,x) for x in ['홈','탐색','크루','나'])
 m.wait(lambda:not any('배경 지도를 불러오는 중' in n.get('content-desc','') for n in m.tree().iter('node')), 'Visible map request settled',60)
 m.save_png('live3-main-record-map.png','main');stage('actual-record-page')
 # Retain only cache counts/status, never raw original GPS/prefs.
 cache=m.sh('run-as',m.PKG,'ls','-1','cache/osm-visible-tiles',allow_failure=True)
 P['mapTileCacheFileCount']=len([x for x in cache.splitlines() if x.endswith('.png')])
 root=m.tree();P['mapErrorVisible']=bool(m.nodes(root,'지도 연결 실패'));assert P['mapTileCacheFileCount']>0 and not P['mapErrorVisible'], 'Native HTTPS basemap must load in current visible viewport'
 P['nativeHttpsMapCacheAndRenderingVerified']=True
 m.reveal('GPX 경로 내보내기');m.save_png('live3-main-record-footer.png','main')
 m.tap('기록 삭제');m.tap('삭제');m.wait(lambda:bool(m.nodes(m.tree(),'홈')) and bool(m.nodes(m.tree(),'나')),'Own detail closed after actual deletion')
 stage('record-deleted-in-app')
 # A second zero-distance review run checks a cached launcher action after a
 # real process force-stop. Do not call a PendingIntent factory as click proof.
 m.start_run();find_widget('run_widget_control')
 click_widget('run_widget_control');m.paused_settled();m.wait(lambda:bool(m.nodes(m.tree(),'재개')),'Second widget paused control rendered',30);m.force_stop()
 root=m.tree();P['cachedOldWidgetResumeVisibleAfterForceStop']=bool(m.nodes(root,'재개'))
 if P['cachedOldWidgetResumeVisibleAfterForceStop']:
  m.tap('재개');P['cachedOldWidgetResumeActuallyTapped']=True;time.sleep(2)
 else:
  P['cachedOldWidgetResumeActuallyTapped']=False
  P['forceStopTapLimitation']='Android did not retain a clickable Resume; no factory invocation substituted.'
 assert m.dump() is None
 P['oldWidgetResumeAfterForceStopDidNotStartRecorder']=True
 m.cleanup_own_runs()
 stage('old-widget-action-rejected-and-own-recovery-cleaned')
 remove_widget();stage('review-widget-removed')
 m.force_stop();after=m.snapshot('after');assert m.canonical(after)==m.canonical(m.baseline)
 P['databaseAfter']={'runs':len(after['runs']),'points':len(after['points']),'sha256':m.canonical(after)}
 P['passed']=True
except BaseException as e:
 P['failureType']=type(e).__name__;P['failure']=str(e) if isinstance(e,AssertionError) else 'Review operation failed; inspect private log'
 try:m.private_write(W/'live3-private-failure.png',m.adb('exec-out','screencap','-p'))
 except Exception:pass
finally:
 stage('restoring')
 if can:
  try:
   m.cleanup_own_runs()
   remove_widget()
   m.force_stop()
   after=m.snapshot('finally');P['databaseExactlyRestored']=m.canonical(after)==m.canonical(m.baseline)
   m.write_prefs(oldprefs);P['preferencesExactlyRestored']=m.prefs()==oldprefs
   # Cache restoration does not modify canonical records.
   if oldwidget:
    quoted=shlex.join(['run-as',m.PKG,'sh','-c','cat > shared_prefs/runmoni_widget.xml'])
    m.adb('shell','-T',quoted,data=oldwidget)
   else:m.sh('run-as',m.PKG,'rm','-f','shared_prefs/runmoni_widget.xml')
   P['widgetCacheExactlyRestored']=raw_widget()==oldwidget
  except BaseException as e:P['restoreFailureType']=type(e).__name__;P['passed']=False
 if beforeenv:
  try:
   equal,after,failures=env.restore_environment(m,beforeenv);P['controlledEnvironmentExactlyRestored']=equal;P['environmentRestoreFailures']=failures
  except BaseException as e:P['environmentRestoreFailureType']=type(e).__name__;P['passed']=False
 P['passed']=P['passed'] and P.get('launcherWidgetLifecycle',{}).get('newReviewWidgetRemoved',False) and all(P.get(x) for x in ['databaseExactlyRestored','preferencesExactlyRestored','widgetCacheExactlyRestored','controlledEnvironmentExactlyRestored'])
 P['completed']=True;P['finishedAtUtc']=env.now();stage('complete')
print(json.dumps({k:P.get(k) for k in ['passed','failureType','databaseExactlyRestored','preferencesExactlyRestored','controlledEnvironmentExactlyRestored']},ensure_ascii=False),flush=True)
raise SystemExit(0 if P['passed'] else 1)
