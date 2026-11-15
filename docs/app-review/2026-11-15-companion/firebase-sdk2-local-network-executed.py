from pathlib import Path
import subprocess, json, hashlib, datetime, os
R=Path('/workspace/runmoni'); W=R/'work/companion-redesign'; E=R/'docs/app-review/2026-11-15-companion'
os.umask(0o077)
A=[str(R/'work/cloud-android/sdk/platform-tools/adb'),'-s','emulator-5556','shell','settings']
keys=['global_http_proxy_host','global_http_proxy_port','global_http_proxy_exclusion_list','global_proxy_pac_url','http_proxy']
def get():return {k:subprocess.check_output(A+['get','global',k],timeout=20).decode().strip() for k in keys}
def write(k,v):subprocess.run(A+(['delete','global',k] if v=='null' else ['put','global',k,v]),check=True,capture_output=True,timeout=20)
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
out=E/'firebase-sdk2-local-network-scope.json'; assert not out.exists()
old=get(); p=W/'firebase-sdk2-proxy-before-private.json'; assert not p.exists();p.write_text(json.dumps(old,indent=2)+'\n')
proof={'startedAtUtc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'scope':'Owned API29 local demo Auth/Firestore only; no production requests intended','privateBeforeSha256':sha(p),'productionMapTestRunInsideScope':False,'appSourceOrTlsChanged':False,'completed':False,'proxyRestoredExactly':False}
executed=E/'firebase-sdk2-local-network-executed.py';assert not executed.exists();executed.write_bytes(Path(__file__).read_bytes());proof.update(executedDriver=executed.name,executedDriverSha256=sha(executed))
out.write_text(json.dumps(proof,indent=2)+'\n')
try:
 write('http_proxy',':0')
 proof['localFixtureProxyTemporarilyDisabled']=True
 out.write_text(json.dumps(proof,indent=2)+'\n')
 result=subprocess.run(['python3',str(W/'android-review.py'),'--phase','firebase-sdk','--demo-emulators-ready','--binary','binary-build4.json','--name','firebase-sdk2'],cwd=R)
 proof['phaseExitCode']=result.returncode
finally:
 for k in keys:write(k,old[k])
 after=get(); proof['proxyRestoredExactly']=after==old
 proof['completed']=True;proof['finishedAtUtc']=datetime.datetime.now(datetime.timezone.utc).isoformat()
 out.write_text(json.dumps(proof,indent=2)+'\n')
 assert after==old,'Restore owned proxy settings exactly before any other network review'
raise SystemExit(proof.get('phaseExitCode',1))
