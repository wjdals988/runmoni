"""Restore one owned API29 rotation policy; no product test or app/data reset.

The actual shell default command persisted DISABLED. Stop only the owned AVD's
Android framework before changing its one attribute1→0, preserve all other bytes
and owner/mode, then reload and verify the original whole app/data/owned state.
"""
from pathlib import Path
import base64, importlib.util, json, os, re, shlex, time, xml.etree.ElementTree as ET

R=Path('/workspace/runmoni');W=R/'work/expressive-refresh';E=R/'docs/app-review/2026-11-17-expressive-refresh'
os.umask(0o077)
def load(name,path):
    spec=importlib.util.spec_from_file_location(name,path);value=importlib.util.module_from_spec(spec);spec.loader.exec_module(value);return value
m=load('owned_rotation_app_observer',W/'actual-ui-helper.py')
env=load('owned_rotation_restore_environment',W/'android-review.py')
m.W=W;m.E=W;m.NAME='landscape2-rotation-recovery2';m.VERSION='0.9.0-dev';m.CODE=32
m.OUT=W/'landscape2-rotation-recovery2-private-observer.json'
sha=env.sha
out=E/'landscape2-rotation-recovery2.json';assert not out.exists()
source=Path(__file__).read_bytes();env.exclusive_bytes(E/'landscape2-rotation-recovery2-executed-driver.py',source)
p={'version':'0.9.0-dev','versionCode':32,'passed':False,'completed':False,
   'sourceKind':'restoration-only owned API29 rotation','avd':'Runmoni_Cloud_29','api':29,
   'executedDriver':'landscape2-rotation-recovery2-executed-driver.py','executedDriverSha256':sha(source),
   'startedAtUtc':env.now(),'appOrTestApkChanged':False,'userDataReset':False,
   'networkOrFirewallModified':False,'allOsBytesExactlyRestoredClaimed':False,
   'restorationOnly':True,'productMethodsCountedByRecovery':0,
   'originalFailedParentOverwritten':False,'productInstrumentationInvoked':False}
need_start=False;original_env=None;expected_prefs=None;expected_fixtures=None;baseline=None
fp='/data/system/display_settings.xml';tmp=fp+'.runmoni-recovery'
def stage(name):p['stage']=name;p['updatedAtUtc']=env.now();env.atomic_json(out,p);print(name,flush=True)
def root(*args,**kwargs):return m.adb('shell','-T','su','0',*args,**kwargs)
def read_policy_file():return root('cat',fp)
def require_unused_temp():
    script='if [ -e /data/system/display_settings.xml.runmoni-recovery ] || [ -L /data/system/display_settings.xml.runmoni-recovery ]; then printf PRESENT; else printf ABSENT; fi'
    result=root('sh','-c',shlex.quote(script))
    assert result==b'ABSENT','Never overwrite an existing recovery temporary file'
def private_original(prior,key):
    entry=prior[key];path=W/entry['file'];raw=path.read_bytes()
    assert path.stat().st_mode&0o777==0o600 and sha(raw)==entry['sha256'];return raw
def all_preferences():return {name:env.preference_file_snapshot(m,name) for name in env.PREFERENCE_FILES}
def installed(binary):
    result=[]
    for package,entry in zip((m.PKG,m.PKG+'.test'),binary['binaries']):
        path=m.sh('pm','path',package).removeprefix('package:')
        assert path.startswith('/data/app/') and '\n' not in path
        digest=m.sh('sha256sum',path,timeout=120).split()[0];assert digest==entry['sha256']
        result.append({'package':package,'sha256':digest})
    return result
try:
    stage('guards-and-original-evidence')
    failed_path=E/'landscape2-rotation-recovery.json'
    assert sha(failed_path.read_bytes())=='3307d720b75db4c2b4caa505478631a039435446168e080c8fc23208f188d19f'
    failed=json.loads(failed_path.read_text())
    assert failed['completed'] is True and failed['passed'] is False and failed['failureType']=='RuntimeError'
    assert not any(key in failed for key in ('recoveryTempOriginallyAbsent','frameworkPidBefore','frameworkPidAfter','onlyOwnedAttributeChanged'))
    p['priorFailedRecoveryPreflight']={'file':failed_path.name,'sha256':sha(failed_path.read_bytes()),
        'frameworkStopOrRotationWriteReached':False,'productMethodsCounted':0}
    assert json.loads((W/'formal-followups11-result.json').read_text())['completed'] is True
    owner=json.loads((W/'owned-avd7.json').read_text())
    assert owner['avd']=='Runmoni_Cloud_29' and owner['serial']=='emulator-5556'
    assert m.A[-2:]==['-s','emulator-5556']
    owned=Path(f"/proc/{owner['pid']}")
    assert owned.is_dir() and (owned/'stat').read_text().split(') ',1)[1].split()[19]==owner['procStartTicks']
    assert b'Runmoni_Cloud_29' in (owned/'cmdline').read_bytes()
    assert m.adb('emu','avd','name').decode().splitlines()[0]=='Runmoni_Cloud_29'
    assert m.sh('getprop','ro.build.version.sdk')=='29' and m.sh('am','get-current-user')=='0'
    assert m.sh('getprop','sys.boot_completed')=='1' and m.dump() is None and not m.pid()
    p['ownedAvd']={'pid':owner['pid'],'procStartTicks':owner['procStartTicks'],'serial':owner['serial']}
    prior_raw=(E/'landscape2.json').read_bytes();assert sha(prior_raw)=='3c475c9a4ead2ff45fb93909da43741a4add12a5e83275440f2aeb817ada960a'
    prior=json.loads(prior_raw)
    assert prior['completed'] is True and prior['passed'] is False and prior['actualPassedProductMethods']==1
    assert prior['fixedToUserRotation']=={'before':False,'beforePolicy':'default','forcedEnabledForLandscape':True,
        'restoredExactly':False,'restorationScope':'Effective value and default/disabled/enabled policy only; full display-settings file bytes are not claimed identical',
        'after':False,'afterPolicy':'disabled'}
    assert len(prior['groups'])==1 and prior['groups'][0]['passed'] is True
    assert not prior['restorationCommandFailures']
    for key in ('driverSourceUnchanged','observerHelperSourceUnchanged','binaryEvidenceUnchanged',
                'preferencesRestoredExactly','profileRestoredExactly','communityAccountPreferencesRestoredExactly',
                'appearancePreferencesRestoredExactly','allPreferencesStillExactAfterEnvironmentRestoration',
                'originalDatabaseRowsPreservedExactly','ownedFixturePreferencesCleaned','controlledSettingsPermissionsPowerRestoredExactly'):
        assert prior[key] is True,key
    binary_raw=(E/'binary-build11.json').read_bytes();assert sha(binary_raw)=='13b25e0a2706183c96bff43f0a05fe9548211e308a22c194cb3086bc3cc2dba3'
    binary=json.loads(binary_raw);p['binaryReference']={'file':'binary-build11.json','sha256':sha(binary_raw)}
    p['sourceMapsBefore']=env.source_maps(binary);p['installedBefore']=installed(binary)
    p['environmentHelper']={'file':prior['executedDriver'],'sha256':prior['executedDriverSha256']}
    p['observerHelper']={'file':prior['observerHelper'],'sha256':prior['observerHelperSha256']}
    assert sha((E/p['environmentHelper']['file']).read_bytes())==p['environmentHelper']['sha256']
    assert sha((E/p['observerHelper']['file']).read_bytes())==p['observerHelper']['sha256']
    assert sha((W/'android-review.py').read_bytes())==prior['executedDriverSha256']
    assert sha((W/'actual-ui-helper.py').read_bytes())==prior['observerHelperSha256']
    original_env=json.loads(private_original(prior,'privateEnvironmentBackup'))
    assert prior['mainPreferencesOriginallyPresent'] is True
    expected_prefs={'runmoni.xml':{'present':prior['mainPreferencesOriginallyPresent'],'raw':private_original(prior,'privateXmlBackup')},
        'runmoni-community-account.xml':{'present':prior['accountPreferencesOriginallyPresent'],'raw':private_original(prior,'privateAccountBackup')},
        'runmoni_appearance.xml':{'present':prior['appearancePreferencesOriginallyPresent'],'raw':private_original(prior,'privateAppearanceBackup')}}
    expected_fixtures={name:base64.b64decode(value,validate=True) for name,value in json.loads(private_original(prior,'privateOwnedFixtureBackup')).items()}
    original_data=json.loads(private_original(prior,'privateDataBefore'))
    assert all_preferences()==expected_prefs and env.owned_fixture_preferences(m)==expected_fixtures
    baseline=m.snapshot('before-framework');assert m.canonical(baseline)==m.canonical(original_data)
    p['originalDatabaseRowsSha256']=m.canonical(original_data)
    before={'effective':env.fixed_rotation(m),'persistedPolicy':env.fixed_rotation_policy(m)}
    assert before=={'effective':False,'persistedPolicy':'disabled'}
    p.update(priorAttempt={'file':'landscape2.json','sha256':sha(prior_raw),'wholeAttemptPassed':False,'productMethodsPassed':1},
        before=before,target={'effective':False,'persistedPolicy':'default'},
        originalPreferencePresenceAndHashes={name:{'present':value['present'],'sha256':sha(value['raw'])} for name,value in expected_prefs.items()})
    raw=read_policy_file();assert len(raw)<16384
    parsed=ET.fromstring(raw);displays=parsed.findall('display');assert len(displays)==1 and displays[0].get('fixedToUserRotation')=='1'
    stat=root('stat','-c','%u:%g:%a',fp).decode().strip();assert re.fullmatch(r'\d+:\d+:[0-7]{3,4}',stat)
    m.private_write(W/'landscape2-rotation-recovery2-private-display-before.xml',raw)
    p['beforeDisplayFileSha256']=sha(raw);p['beforeDisplayFileStat']=stat
    require_unused_temp();p['recoveryTempOriginallyAbsent']=True
    server=m.sh('pidof','system_server');assert re.fullmatch(r'\d+',server);p['frameworkPidBefore']=server
    stage('stopping-owned-android-framework')
    need_start=True;root('stop');end=time.monotonic()+45
    while time.monotonic()<end:
        if not m.sh('pidof','system_server',allow_failure=True):break
        time.sleep(.25)
    assert not m.sh('pidof','system_server',allow_failure=True)
    frozen_stat=root('stat','-c','%u:%g:%a',fp).decode().strip()
    assert frozen_stat==stat,'Abort if owner/mode changed while stopping framework'
    p['frozenDisplayFileStat']=frozen_stat
    frozen=read_policy_file();node=ET.fromstring(frozen);displays=node.findall('display')
    assert len(displays)==1 and displays[0].get('fixedToUserRotation')=='1'
    assert frozen.count(b'fixedToUserRotation="1"')==1
    restored=frozen.replace(b'fixedToUserRotation="1"',b'fixedToUserRotation="0"',1)
    check=ET.fromstring(restored);assert check.findall('display')[0].get('fixedToUserRotation')=='0'
    before_others=dict(displays[0].attrib);after_others=dict(check.findall('display')[0].attrib)
    before_others.pop('fixedToUserRotation');after_others.pop('fixedToUserRotation');assert before_others==after_others
    require_unused_temp();p['recoveryTempStillAbsentBeforeWrite']=True
    root('dd','of='+tmp,data=restored);uid,gid,mode=stat.split(':');root('chown',uid+':'+gid,tmp);root('chmod',mode,tmp);root('mv',tmp,fp)
    assert read_policy_file()==restored and root('stat','-c','%u:%g:%a',fp).decode().strip()==stat
    p.update(onlyOwnedAttributeChanged=True,otherFileBytesPreserved=True,displayFileOwnerModePreserved=True,
        frozenDisplayFileSha256=sha(frozen),restoredDisplayFileSha256=sha(restored))
    stage('restarting-framework-to-load-original-default')
    root('setprop','sys.boot_completed','0');root('start');need_start=False;end=time.monotonic()+600
    while time.monotonic()<end:
        current=m.sh('pidof','system_server',allow_failure=True)
        if current and current!=server and m.sh('getprop','sys.boot_completed')=='1':break
        time.sleep(2)
    assert current and current!=server and m.sh('getprop','sys.boot_completed')=='1'
    p.update(frameworkPidAfter=current,frameworkRestarted=True,launcherAccessibilityVerifiedByThisRecovery=False)
    stage('verifying-all-original-app-data-and-owned-settings')
    assert m.dump() is None;m.force_stop()
    equal,current_env,failures=env.restore_environment(m,original_env)
    p['restorationCommandFailures']=failures;assert equal and not failures
    after={'effective':env.fixed_rotation(m),'persistedPolicy':env.fixed_rotation_policy(m)}
    assert after=={'effective':False,'persistedPolicy':'default'}
    assert all_preferences()==expected_prefs and env.owned_fixture_preferences(m)==expected_fixtures
    afterdb=m.snapshot('after-framework');assert m.canonical(afterdb)==m.canonical(original_data)==m.canonical(baseline)
    p.update(after=after,policyAndEffectiveRestoredExactly=True,preferencesRestoredExactly=True,
        profileRestoredExactly=env.local_profile(m)==env.profile_from_xml(expected_prefs['runmoni.xml']['raw']),
        communityAccountPreferencesRestoredExactly=True,appearancePreferencesRestoredExactly=True,
        allPreferencesStillExactAfterEnvironmentRestoration=True,ownedFixturePreferencesRestoredExactly=True,
        controlledEnvironmentExactlyRestored=True,databaseExactlyRestored=True,originalDatabaseRowsAndBackupOriginsPreservedExactly=True)
    assert p['profileRestoredExactly'];p['installedAfter']=installed(binary)
    assert p['installedAfter']==p['installedBefore'];p['sourceMapsAfterRestoration']=env.source_maps(binary)
    assert sha((E/'binary-build11.json').read_bytes())==p['binaryReference']['sha256']
    assert sha((E/'landscape2.json').read_bytes())==p['priorAttempt']['sha256']
    p['originalFailedParentStillImmutable']=True;p['binaryEvidenceUnchanged']=True;p['passed']=True
except BaseException as failure:
    p['failureType']=type(failure).__name__;p['failure']=str(failure) if isinstance(failure,AssertionError) else 'Inspect preserved local recovery log';stage('failed')
finally:
    if need_start:
        try:root('start');p['frameworkStartAttemptedInFinally']=True
        except BaseException as failure:p['frameworkStartFailureType']=type(failure).__name__
    p['driverSourceUnchanged']=sha(Path(__file__).read_bytes())==p['executedDriverSha256']
    p['passed']=bool(p['passed'] and p['driverSourceUnchanged']);p['completed']=True;p['finishedAtUtc']=env.now()
    stage('complete-passed' if p['passed'] else 'complete-failed')
raise SystemExit(0 if p['passed'] else 1)
