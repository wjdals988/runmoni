#!/usr/bin/env python3
"""Build/start/stop the isolated cloud avatar test APK. Never a Runmoni release.

Uses the already prepared verified SDK/JDK/Gradle in work/cloud-android. Does not
download, expose a port externally, touch a PC AVD, or read signing credentials.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import signal
import subprocess
import re
import time

ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT / "work/cloud-android"
APP = BASE / "review-app"
NAME = "Runmoni_Cloud_29"
SDK = BASE / "sdk"
JDK = BASE / "jdk-21.0.13+9"
ENV = os.environ | {"JAVA_HOME": str(JDK), "GRADLE_USER_HOME": str(BASE / "gradle-home"),
    "ANDROID_USER_HOME": str(BASE / "user"), "ANDROID_HOME": str(SDK),
    "ANDROID_AVD_HOME": str(BASE / "avds"), "ANDROID_EMULATOR_HOME": str(BASE / "user"),
    "ANDROID_I_WANT_MY_TCG": "yes"}

def run(args, **kwargs):
    return subprocess.run([str(a) for a in args], env=ENV, check=True, cwd=ROOT, **kwargs)

def stage():
    target = APP / "src/main/java"
    target.mkdir(parents=True, exist_ok=True)
    snapshots = {}
    for relative in ["avatar/GarmentPalette.java", "avatar/AvatarReferenceReview.kt", "ui/AvatarReferenceDialog.kt", "ui/RunmoniTheme.kt"]:
        source = ROOT / "app/src/main/java/com/runmoni/app" / relative
        dest = target / "com/runmoni/app" / relative
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source,dest)
        snapshots[str(source.relative_to(ROOT))] = hashlib.sha256(source.read_bytes()).hexdigest()
    small = next(line for line in (ROOT/"app/src/main/java/com/runmoni/app/ui/RunmoniApp.kt").read_text().splitlines() if line.startswith("internal fun SmallNote("))
    (target/"com/runmoni/app/ui/SmallNote.kt").write_text("package com.runmoni.app.ui\nimport androidx.compose.runtime.Composable\nimport androidx.compose.material3.*\n@Composable\n"+small+"\n")
    main=target/"com/runmoni/avatarreview/ReviewActivity.kt"
    main.parent.mkdir(parents=True,exist_ok=True)
    main.write_text('''package com.runmoni.avatarreview
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import com.runmoni.app.ui.AvatarReferenceDialog
import com.runmoni.app.ui.RunmoniTheme
class ReviewActivity : ComponentActivity() {
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  setContent { RunmoniTheme {
   Surface(Modifier.fillMaxSize()) { Box(Modifier.fillMaxSize()) {
    Text("캐릭터 검토 전용 테스트")
    AvatarReferenceDialog(onDismiss = { finish() }, onMotionFrameRendered = { Log.i("AvatarReviewFrame", "frame=$it") })
   } }
  } }
 }
}
''')
    (APP/"source-snapshot.json").write_text(json.dumps(snapshots,indent=2)+"\n")
    (APP/"settings.gradle.kts").write_text('''pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { google(); mavenCentral() } }
rootProject.name = "RunmoniAvatarReview"
''')
    (APP/"build.gradle.kts").write_text('''plugins {
 id("com.android.application") version "8.11.1"
 id("org.jetbrains.kotlin.android") version "2.0.21"
 id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
}
android {
 namespace = "com.runmoni.avatarreview"
 compileSdk = 36
 buildToolsVersion = "36.0.0"
 defaultConfig { applicationId = "com.runmoni.avatarreview"; minSdk = 29; targetSdk = 36; versionCode = 1; versionName = "review-local" }
 buildFeatures { compose = true }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
 sourceSets.getByName("main").assets.srcDir("'''+str(ROOT/'app/src/main/assets')+'''")
 signingConfigs.getByName("debug").storeFile = file("review-debug.keystore")
}
dependencies {
 implementation("androidx.activity:activity-compose:1.9.3")
 implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
 implementation("androidx.compose.ui:ui:1.7.0")
 implementation("androidx.compose.foundation:foundation:1.7.0")
 implementation("androidx.compose.material3:material3:1.3.0")
 implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
''')
    (APP/'gradle.properties').write_text('android.useAndroidX=true\norg.gradle.parallel=false\norg.gradle.workers.max=1\norg.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8\nkotlin.compiler.execution.strategy=in-process\n')
    (APP/'local.properties').write_text('sdk.dir='+str(SDK)+'\n')
    (APP/'src/main/AndroidManifest.xml').write_text('''<manifest xmlns:android="http://schemas.android.com/apk/res/android"><application android:label="Runmoni Avatar Review" android:theme="@android:style/Theme.Material.Light.NoActionBar" android:allowBackup="false"><activity android:name=".ReviewActivity" android:exported="true"><intent-filter><action android:name="android.intent.action.MAIN"/><category android:name="android.intent.category.LAUNCHER"/></intent-filter></activity></application></manifest>''')

def configure_avd():
    avd=BASE/'avds'/f'{NAME}.avd';avd.mkdir(parents=True,exist_ok=True)
    (avd.parent/f'{NAME}.ini').write_text(f'avd.ini.encoding=UTF-8\npath={avd}\ntarget=android-29\n')
    # Dedicated cloud image only; no snapshots/wipe of other AVDs or user data.
    (avd/'config.ini').write_text(f'''AvdId={NAME}
avd.ini.displayname=Runmoni Cloud API29
abi.type=x86_64
hw.cpu.arch=x86_64
hw.cpu.ncore=2
hw.ramSize=2048
hw.lcd.width=480
hw.lcd.height=854
hw.lcd.density=160
hw.gpu.enabled=yes
hw.gpu.mode=swiftshader
hw.keyboard=yes
hw.audioInput=no
hw.audioOutput=no
hw.camera.back=none
hw.camera.front=none
image.sysdir.1={SDK}/system-images/android-29/default/x86_64/
tag.id=default
tag.display=Default
target=android-29
PlayStore.enabled=false
disk.dataPartition.size=2G
showDeviceFrame=no
fastboot.forceColdBoot=yes
''')

def owned_process():
    file=BASE/'emulator.pid'
    if not file.exists(): return None
    pid=int(file.read_text())
    try:
        args=Path(f'/proc/{pid}/cmdline').read_bytes()
    except FileNotFoundError: return None
    if not args: return None
    if NAME.encode() not in args or str(SDK/'emulator').encode() not in args:
        raise RuntimeError('PID no longer belongs to this project emulator; refusing to act')
    return pid

def active_avds():
    found=[]
    for proc in Path('/proc').iterdir():
        if not proc.name.isdigit(): continue
        try: command=(proc/'cmdline').read_bytes().split(b'\0')
        except (FileNotFoundError,PermissionError,ProcessLookupError): continue
        if b'-avd' in command and command[command.index(b'-avd')+1]==NAME.encode() and str(SDK/'emulator').encode() in command[0]:
            found.append(int(proc.name))
    return found

def quarantine_stale_lock():
    lock=BASE/'avds'/f'{NAME}.avd/hardware-qemu.ini.lock'
    if not lock.exists(): return
    if active_avds(): raise RuntimeError('Active project AVD exists; refusing to touch its lock')
    raw=lock.read_bytes()
    if not re.fullmatch(rb'[0-9]+\x00?[\r\n ]*',raw): raise RuntimeError('Unknown AVD lock format; refusing to remove it')
    owner=raw.rstrip(b'\0\r\n ').decode()
    stat=Path('/proc')/owner/'stat'
    if stat.exists() and stat.read_text().split()[2]!='Z':
        raise RuntimeError('AVD lock owner still exists; refusing to remove it')
    backup=BASE/'stale-locks';backup.mkdir(exist_ok=True)
    lock.rename(backup/f'hardware-qemu.ini.lock.{owner}.{time.time_ns()}')
    print('Quarantined stale lock for terminated PID',owner,'; userdata untouched',flush=True)

parser=argparse.ArgumentParser()
parser.add_argument('action',choices=['build','start','stop','status'])
parser.add_argument('--online',action='store_true',help='Only when initial dependency installation is needed')
args=parser.parse_args()
if args.action=='build':
    if owned_process(): raise RuntimeError('Stop this emulator before building (sequential work rule)')
    stage()
    key=APP/'review-debug.keystore'
    if not key.exists():
        # Public Android debug credentials for a separate local test package only.
        run([JDK/'bin/keytool','-genkeypair','-keystore',key,'-storepass','android','-keypass','android','-alias','androiddebugkey','-dname','CN=Android Debug,O=Android,C=US','-keyalg','RSA','-keysize','2048','-validity','3650','-noprompt'])
    command=[BASE/'gradle-8.13/bin/gradle','-p',APP,'--no-daemon','--no-parallel','--max-workers=1']
    if not args.online: command+=['--offline']
    else:
        command += ['-Dhttps.proxyHost=proxy','-Dhttps.proxyPort=8080','-Dhttp.proxyHost=proxy','-Dhttp.proxyPort=8080',
                    '-Djavax.net.ssl.trustStore='+str(BASE/'proxy-truststore'),'-Djavax.net.ssl.trustStorePassword=changeit','-Dorg.gradle.internal.http.maxConnections=1']
    run(command+['assembleDebug'])
elif args.action=='start':
    if owned_process() or active_avds(): raise RuntimeError('This project emulator is already running')
    quarantine_stale_lock()
    configure_avd()
    (BASE/'user').mkdir(exist_ok=True)
    with (BASE/'emulator.log').open('w') as log:
        process=subprocess.Popen([str(SDK/'emulator/emulator'),'-avd',NAME,'-accel','off','-gpu','swiftshader','-no-window','-no-audio','-no-boot-anim','-no-snapshot','-no-metrics','-memory','2048','-cores','2','-port','5556','-feature','-Vulkan'],env=ENV,stdin=subprocess.DEVNULL,stdout=log,stderr=log,start_new_session=True)
    (BASE/'emulator.pid').write_text(str(process.pid)+'\n')
    print('Started cloud emulator PID',process.pid,'(boot completion not yet verified)')
elif args.action=='stop':
    pid=owned_process()
    if pid:
        os.kill(pid,signal.SIGTERM)
        deadline=time.monotonic()+20
        while owned_process() and time.monotonic()<deadline: time.sleep(.2)
        if owned_process(): raise RuntimeError('Emulator still stopping; do not build/start another instance')
        quarantine_stale_lock()
        print('Stopped',NAME,'PID',pid)
    else: print('No running emulator owned by this helper')
else:
    pid=owned_process()
    if not pid: raise RuntimeError('No running emulator owned by this helper')
    try:
        state=run([SDK/'platform-tools/adb','-s','emulator-5556','shell','getprop','sys.boot_completed'],capture_output=True,text=True,timeout=20)
    except (subprocess.CalledProcessError,subprocess.TimeoutExpired):
        print('Project emulator PID:',pid,'boot not ready; ADB is starting/offline',flush=True)
        raise SystemExit(1)
    print('Project emulator PID:',pid,'boot_completed:',state.stdout.strip(),flush=True)
    if state.stdout.strip()!='1': raise SystemExit(1)
