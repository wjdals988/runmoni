from pathlib import Path
import subprocess,time,json,hashlib,importlib.util
R=Path('/workspace/runmoni');W=R/'work/ui-ux-pro-max';A=[str(R/'work/cloud-android/sdk/platform-tools/adb'),'-s','emulator-5556']
def call(*args):return subprocess.run(A+list(args),capture_output=True,timeout=20)
end=time.monotonic()+600
while time.monotonic()<end:
 try:
  p=call('shell','getprop','sys.boot_completed')
  if p.returncode==0 and p.stdout.strip()==b'1':break
 except subprocess.TimeoutExpired:pass
 time.sleep(3)
else:raise AssertionError('Owned AVD boot timeout')
spec=importlib.util.spec_from_file_location('recovery_observer',R/'work/background-running/host-review5.py');m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m);m.E=W;m.NAME='ui-install4-boot'
assert m.adb('emu','avd','name').decode().splitlines()[0]=='Runmoni_Cloud_29'
m.adb('push',str(R/'work/background-running/runmoni-live-dump.jar'),'/data/local/tmp/runmoni-live-dump.jar')
m.wake();m.sh('input','keyevent','3')
end=time.monotonic()+240
while time.monotonic()<end:
 root=m.tree()
 if any(n.get('package')=='com.android.launcher3' for n in root.iter('node')):break
 time.sleep(2)
else:raise AssertionError('Actual launcher not observed before cleanup')
p=W/'install4-boot.json';assert not p.exists();p.write_text(json.dumps({'avd':'Runmoni_Cloud_29','api':29,'acceleration':'TCG','bootCompleted':True,'launcherObserved':True,'apkInstalledOrDataCleared':False,'systemUiWaitActions':m.P['systemUiWaitActions']},indent=2)+'\n')
print('Owned API29 AVD ready; installed app/test and user data retained.')
