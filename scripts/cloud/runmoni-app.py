#!/usr/bin/env python3
"""Build and inspect the real Runmoni debug app on the dedicated cloud AVD.

Uses a local TEST key, never the release key. No publishing, uninstalling, data
reset, source/lockfile rewrites or changes to the production application ID.
"""
import argparse
import os
from pathlib import Path
import subprocess

ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'work/cloud-android'
SDK=BASE/'sdk'
JDK=BASE/'jdk-21.0.13+9'
ENV=os.environ|{'JAVA_HOME':str(JDK),'GRADLE_USER_HOME':str(BASE/'gradle-home'),
    'ANDROID_HOME':str(SDK),'ANDROID_USER_HOME':str(BASE/'user')}
ENV.pop('ANDROID_SDK_ROOT', None)

def run(command,**kwargs):
    return subprocess.run([str(c) for c in command],cwd=ROOT,env=ENV,check=True,**kwargs)

parser=argparse.ArgumentParser()
parser.add_argument('action',choices=['build','install','launch'])
parser.add_argument('--online',action='store_true',help='Only for initial/missing dependency caches')
parser.add_argument('--tests',action='store_true',help='Build: run JVM tests. Install: also install the test APK')
parser.add_argument('--lint',action='store_true')
parser.add_argument('--android-tests',action='store_true',help='Build instrumentation APK; does not execute it')
args=parser.parse_args()
if args.action=='build':
    # The shared AVD helper refuses to operate on a PID with different ownership.
    pidfile=BASE/'emulator.pid'
    if pidfile.exists():
        proc=Path('/proc')/pidfile.read_text().strip()/'cmdline'
        if proc.exists() and b'Runmoni_Cloud_29' in proc.read_bytes():
            raise RuntimeError('Stop the cloud emulator before building (sequential work rule)')
    key=BASE/'review-app/review-debug.keystore'
    if not key.exists():
        key.parent.mkdir(parents=True,exist_ok=True)
        run([JDK/'bin/keytool','-genkeypair','-keystore',key,'-storepass','android','-keypass','android',
             '-alias','androiddebugkey','-dname','CN=Android Debug,O=Android,C=US','-keyalg','RSA','-keysize','2048','-validity','3650','-noprompt'])
    init=BASE/'full-debug.init.gradle'
    init.write_text("allprojects { plugins.withId('com.android.application') {\n"
                   "android.buildToolsVersion = '36.0.0'\n"
                   "android.signingConfigs.debug.storeFile = new File('"+str(key)+"')\n} }\n")
    cmd=[BASE/'gradle-8.13/bin/gradle','-p',ROOT,'-I',init,'--no-daemon','--no-parallel','--max-workers=1']
    if not args.online:cmd+=['--offline']
    else:
        cmd+=['-Dhttps.proxyHost=proxy','-Dhttps.proxyPort=8080','-Dhttp.proxyHost=proxy','-Dhttp.proxyPort=8080',
              '-Djavax.net.ssl.trustStore='+str(BASE/'proxy-truststore'),'-Djavax.net.ssl.trustStorePassword=changeit',
              '-Dorg.gradle.internal.http.maxConnections=1']
    cmd+=[':app:assembleDebug']
    if args.tests:cmd+=[':app:testDebugUnitTest']
    if args.android_tests:cmd+=[':app:assembleDebugAndroidTest']
    if args.lint:cmd+=[':app:lintDebug']
    run(cmd)
else:
    # Require the owned, booted cloud AVD; never send commands to a PC/device.
    run(['python3',ROOT/'scripts/cloud/avatar-review.py','status'])
    adb=[SDK/'platform-tools/adb','-s','emulator-5556']
    if args.action=='install':
        run(adb+['install','-r',ROOT/'app/build/outputs/apk/debug/app-debug.apk'])
        if args.tests:run(adb+['install','-r',ROOT/'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'])
    else:
        run(adb+['shell','am','start','-W','-n','com.runmoni.app/.MainActivity'])
        # am's wait result alone is insufficient under slow TCG; inspect resumed
        # Activity, actual screenshot and crash/ANR logs as documented.
