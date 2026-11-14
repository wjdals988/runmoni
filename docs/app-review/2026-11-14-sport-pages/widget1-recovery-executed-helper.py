from pathlib import Path
import json,hashlib,datetime,importlib.util,re,sqlite3,shlex
R=Path('/workspace/runmoni');W=R/'work/sport-pages';E=R/'docs/app-review/2026-11-14-sport-pages'
sha=lambda raw:hashlib.sha256(raw).hexdigest()
def load(name,path):
 s=importlib.util.spec_from_file_location(name,path);m=importlib.util.module_from_spec(s);s.loader.exec_module(m);return m
original=E/'widget1.json';old=json.loads(original.read_text());assert old['completed'] is False and old['passed'] is False and old['stage']=='guards' and not old['captures'] and not old['launcherWidgetLifecycles']
out=E/'widget1-interruption-recovery.json';assert not out.exists()
env=load('widget_recovery_env',W/'android-review-input5.py');m=load('widget_recovery_observer',R/'work/running-experience/actual-ui-helper6.py');m.W=W;m.E=E;m.NAME='widget1-recovery';m.P={'systemUiWaitActions':[],'accessibilityRetries':0}
assert sha((W/'android-review-input5.py').read_bytes())==old['environmentHelperSha256'] and sha((R/'work/running-experience/actual-ui-helper6.py').read_bytes())==old['observerHelperSha256']
assert not m.pid() and m.dump() is None
bpath=E/'binary-build5.json';b=json.loads(bpath.read_text());assert sha(bpath.read_bytes())==old['requiredBinaryEvidenceSha256']
proof={'version':'0.6.0-dev','versionCode':28,'name':'widget1-interruption-recovery','startedAtUtc':env.now(),'kind':'owned interruption recovery only','completed':False,'passed':False,'restorationOnly':True,'widgetRuntimeVerified':False,'originalAttemptCompleted':False,'originalAttemptPassed':False,'originalAttemptNotRewritten':False,'originalAttempt':{'file':'widget1.json','sha256':sha(original.read_bytes())},'captures':[],'binaryReference':'binary-build5.json','binaryReferenceSha256':sha(bpath.read_bytes()),'sourceMapsBefore':env.source_maps(b),'actions':[],'newWidgetsRemoved':False,'unrelatedProcessTerminated':False,'productTestsCounted':0}
env.atomic_json(out,proof)
def ids():
 text=m.sh('dumpsys','appwidget');assert 'Widgets:' in text and 'Hosts:' in text;text=text.split('Widgets:',1)[1].split('Hosts:',1)[0];all_ids=[];own=[]
 for block in re.split(r'(?=  \[\d+\] id=)',text):
  f=re.search(r' id=(\d+)',block)
  if f:
   i=int(f[1]);all_ids.append(i)
   if 'com.runmoni.app/' in block:own.append(i)
 assert len(all_ids)==len(set(all_ids));return {'all':sorted(all_ids),'runmoni':sorted(own)}
before_ids=json.loads((W/'widget1-private-widget-ids-before.json').read_text());assert not before_ids['runmoni'];assert ids()==before_ids,'Unexpected widget IDs: refuse unknown removal'
proof['launcherWidgetIdsExactlyRestored']=True
before_env=json.loads((W/'widget1-private-environment-before.json').read_text());prefs=(W/'widget1-private-preferences-before.xml').read_bytes();cache=(W/'widget1-private-widget-cache-before.xml').read_bytes();assert cache,'Nonempty backup proves original cache existed; do not infer absence from an empty opaque file'
def read_cache():
 names=m.adb('exec-out','run-as',m.PKG,'ls','-1','shared_prefs').decode().splitlines();assert 'runmoni_widget.xml.bak' not in names
 return ('runmoni_widget.xml' in names,m.adb('exec-out','run-as',m.PKG,'cat','shared_prefs/runmoni_widget.xml') if 'runmoni_widget.xml' in names else b'')
proof['preferencesAlreadyExactBeforeRecovery']=m.prefs()==prefs;proof['widgetCacheAlreadyExactBeforeRecovery']=read_cache()==(True,cache)
if not proof['preferencesAlreadyExactBeforeRecovery']:m.write_prefs(prefs);proof['actions'].append('restore exact original main XML')
if not proof['widgetCacheAlreadyExactBeforeRecovery']:
 m.adb('shell','-T',shlex.join(['run-as',m.PKG,'sh','-c','cat > shared_prefs/runmoni_widget.xml']),data=cache);m.sh('sync');proof['actions'].append('restore exact original widget XML')
proof['preferencesRestoredExactly']=m.prefs()==prefs;proof['profileRestoredExactly']=env.local_profile(m)==env.profile_from_xml(prefs);proof['widgetCacheExactlyRestored']=read_cache()==(True,cache);proof['widgetCacheExistsExactlyRestored']=read_cache()[0] is True
# No DB is copied back. Read both before and current snapshots with query_only.
directory=W/'widget1-private-db-1-before';db=sqlite3.connect((directory/'runmoni-runs.db').as_uri()+'?mode=ro',uri=True);db.row_factory=sqlite3.Row
try:
 db.execute('PRAGMA query_only=ON');assert db.execute('PRAGMA quick_check').fetchone()[0]=='ok';baseline={'runs':[dict(x) for x in db.execute('SELECT * FROM runs ORDER BY id')],'points':[dict(x) for x in db.execute('SELECT * FROM track_points ORDER BY runId, sequence')]}
finally:db.close()
current=m.snapshot('after-recovery');proof['databaseExactlyRestored']=m.canonical(current)==m.canonical(baseline);proof['databaseCopiedBack']=False
account_before=(W/'live2-private-account-before.xml').read_bytes();account_now=m.adb('exec-out','run-as',m.PKG,'cat','shared_prefs/runmoni-community-account.xml');proof['accountPreferencesRestoredExactly']=account_now==account_before
observed_before=env.environment_snapshot(m);proof['controlledEnvironmentAlreadyExactBeforeRecovery']=observed_before==before_env
equal,after,failures=env.restore_environment(m,before_env);proof['controlledEnvironmentExactlyRestored']=equal and not failures;proof['restorationCommandFailures']=failures
proof['launcherWidgetIdsExactlyRestored']=ids()==before_ids;proof['sourceMapsAfterRestoration']=env.source_maps(b);proof['originalAttemptNotRewritten']=sha(original.read_bytes())==proof['originalAttempt']['sha256'];proof['binaryEvidenceUnchanged']=sha(bpath.read_bytes())==proof['binaryReferenceSha256']
proof['backupReferences']=[{'file':p.name,'sha256':sha(p.read_bytes()),'privateWorkspaceBackup':True} for p in [W/'widget1-private-environment-before.json',W/'widget1-private-preferences-before.xml',W/'widget1-private-widget-cache-before.xml',W/'widget1-private-widget-ids-before.json']]
keys=['preferencesRestoredExactly','profileRestoredExactly','widgetCacheExactlyRestored','widgetCacheExistsExactlyRestored','databaseExactlyRestored','accountPreferencesRestoredExactly','controlledEnvironmentExactlyRestored','launcherWidgetIdsExactlyRestored','originalAttemptNotRewritten','binaryEvidenceUnchanged']
assert all(proof[k] is True for k in keys),{k:proof[k] for k in keys}
proof.update(completed=True,passed=True,finishedAtUtc=env.now());env.atomic_json(out,proof);print('Interruption recovery completed: exact controlled state/data/cache/IDs, no widget runtime success claimed.',flush=True)
