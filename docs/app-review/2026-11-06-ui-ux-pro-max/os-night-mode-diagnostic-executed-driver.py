from pathlib import Path
import datetime, hashlib, importlib.util, json, subprocess

R=Path('/workspace/runmoni');W=R/'work/ui-ux-pro-max';E=R/'docs/app-review/2026-11-06-ui-ux-pro-max'
spec=importlib.util.spec_from_file_location('night_environment_helper',W/'android-review.py')
a=importlib.util.module_from_spec(spec);spec.loader.exec_module(a)
m=a.helper('os-night-diagnostic')
assert m.adb('emu','avd','name').decode().splitlines()[0]=='Runmoni_Cloud_29'
assert m.dump() is None and not m.pid()
before=a.environment_snapshot(m)
private=W/'os-night-diagnostic-private-before.json';assert not private.exists();private.write_text(json.dumps(before,indent=2)+'\n');private.chmod(0o600)
prefs_sha=hashlib.sha256(m.prefs()).hexdigest()
proof={'startedAtUtc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'scope':'Owned API29 AOSP emulator only; temporary OS night mode diagnostic','productTestCount':0,'productOrTestApkChanged':False,'preferencesWritten':False,'photosCollected':0,'passed':False,'environmentRestorationScope':a.environment_restoration_scope()}
def mode():
 return {'getter':m.sh('cmd','uimode','night'),'observed':a.observed_night_mode(m),
         'dumpsysModeLines':[s for s in m.sh('dumpsys','uimode').splitlines() if 'NightMode' in s or 'mUiMode' in s]}
def root_set(value):
 p=subprocess.run(m.A+['shell','su','0','cmd','uimode','night',value],capture_output=True,timeout=30)
 return {'returnCode':p.returncode,'output':p.stdout.decode().strip()}
try:
 proof['beforeMode']=mode()
 m.sh('cmd','uimode','night','yes')
 proof['ordinaryShellAfterYes']=mode()
 proof['temporaryRootCommand']=root_set('yes')
 proof['temporaryRootAfterYes']=mode()
 proof['actualRootModeYesObserved']=proof['temporaryRootAfterYes']['observed']=='yes'
finally:
 if a.observed_night_mode(m)!=before['nightMode']:
  proof['rootRestoreCommand']=root_set(before['nightMode'])
 equal,after,failures=a.restore_environment(m,before)
 private_after=W/'os-night-diagnostic-private-after.json';assert not private_after.exists();private_after.write_text(json.dumps(after,indent=2)+'\n');private_after.chmod(0o600)
 proof['controlledSettingsPermissionsPowerRestoredExactly']=equal
 proof['restoreCommandFailures']=failures
 proof['preferencesPreservedExactly']=hashlib.sha256(m.prefs()).hexdigest()==prefs_sha
 proof['afterMode']=mode()
 proof['passed']=bool(proof.get('actualRootModeYesObserved') and equal and proof['preferencesPreservedExactly'])
 proof['finishedAtUtc']=datetime.datetime.now(datetime.timezone.utc).isoformat()
 proof['limitations']=['OS mode diagnostic only; no product screen, touch, authentication, or background running test is counted.','su0 is one temporary emulator command; adbd mode, package permissions, system policy resources and app data were not changed.','Original private environment snapshots stay in ignored WORK. Only public OS mode lines are retained here.']
 out=E/'os-night-mode-diagnostic.json';assert not out.exists();out.write_text(json.dumps(proof,ensure_ascii=False,indent=2)+'\n')
 source=E/'os-night-mode-diagnostic-executed-driver.py';assert not source.exists();source.write_bytes(Path(__file__).read_bytes())
 print(json.dumps({'modeDiagnosticPassed':proof['passed'],'ordinaryMode':proof.get('ordinaryShellAfterYes',{}).get('observed'),'rootMode':proof.get('temporaryRootAfterYes',{}).get('observed'),'controlledEnvironmentRestored':equal,'wholeXmlPreserved':proof['preferencesPreservedExactly']},ensure_ascii=False),flush=True)
assert proof['passed']
