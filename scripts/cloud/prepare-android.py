#!/usr/bin/env python3
"""Pinned, checksum-verified cloud test tools. Does not start an emulator.

Only writes work/cloud-android. Existing archives are verified and reused. The
application source, dependency declarations, production signing and data stay intact.
"""
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import tarfile
import zipfile

ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'work/cloud-android'
DOWNLOADS=BASE/'downloads'
SDK=BASE/'sdk'
DOWNLOADS.mkdir(parents=True,exist_ok=True)
SDK.mkdir(exist_ok=True)
GOOGLE='https://dl.google.com/android/repository/'
ITEMS=[
 ('platform-tools_r37.0.1-linux.zip','sha1','477254aa5f903c15cf51001717bdf347fb6b53e0',GOOGLE+'platform-tools_r37.0.1-linux.zip','platform-tools'),
 ('build-tools_r36_linux.zip','sha1','b0b6376977657e8ad9b969bacf4093601da2c6fb',GOOGLE+'build-tools_r36_linux.zip','build-tools/36.0.0'),
 ('emulator-linux_x64-16632760.zip','sha1','a7e7c486f392cbf1a7502441b079b3f35493f134',GOOGLE+'emulator-linux_x64-16632760.zip','emulator'),
 ('platform-36_r02.zip','sha1','2c1a80dd4d9f7d0e6dd336ec603d9b5c55a6f576',GOOGLE+'platform-36_r02.zip','platforms/android-36'),
 ('x86_64-29_r08-linux.zip','sha1','e4b798d6fcddff90d528d74ef22ce3dd4a2ca798',GOOGLE+'sys-img/android/x86_64-29_r08-linux.zip','system-images/android-29/default/x86_64'),
 ('jdk.tar.gz','sha256','d7f5d5cf865a2902b70ff549b97586a7d3f234e016f041ab447ea740c7d66d1c','https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.13%2B9/OpenJDK21U-jdk_x64_linux_hotspot_21.0.13_9.tar.gz','jdk-21.0.13+9'),
 ('gradle-8.13-bin.zip','sha256','20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78','https://services.gradle.org/distributions/gradle-8.13-bin.zip','gradle-8.13'),
]
proof=[]
for name,algorithm,expected,url,target_name in ITEMS:
    archive=DOWNLOADS/name
    previous=ROOT/'work/avatar-reference-review/tools'/name
    if not archive.exists() and previous.exists(): shutil.copy2(previous,archive)
    if not archive.exists():
        temporary=archive.with_suffix(archive.suffix+'.partial')
        subprocess.run(['curl','--fail','--silent','--show-error','--location','--max-time','300',url,'-o',str(temporary)],check=True)
        with temporary.open('rb') as stream: actual=hashlib.file_digest(stream,algorithm).hexdigest()
        if actual!=expected: raise RuntimeError(f'Checksum failed: {name}; refusing to use artifact')
        temporary.rename(archive)
    with archive.open('rb') as stream: actual=hashlib.file_digest(stream,algorithm).hexdigest()
    if actual!=expected: raise RuntimeError(f'Cached checksum failed: {name}; refusing to use artifact')
    destination=(BASE if target_name.startswith(('jdk-','gradle-')) else SDK)/target_name
    if not destination.exists():
        if name.endswith('.tar.gz'):
            with tarfile.open(archive) as package: package.extractall(BASE,filter='data')
        else:
            unpack=BASE/'unpack'/name
            unpack.mkdir(parents=True,exist_ok=True)
            with zipfile.ZipFile(archive) as package:
                for item in package.infolist():
                    package.extract(item,unpack)
                    permissions=item.external_attr>>16
                    if permissions: (unpack/item.filename).chmod(permissions)
            top=list(unpack.iterdir())
            if len(top)!=1: raise RuntimeError(f'Unexpected archive layout: {name}')
            destination.parent.mkdir(parents=True,exist_ok=True)
            shutil.move(str(top[0]),str(destination))
    proof.append({'archive':name,'url':url,'algorithm':algorithm,'digest':expected,'installed':str(destination.relative_to(ROOT))})
    print(name+': verified / installed',flush=True)

trust=BASE/'proxy-truststore'
if not trust.exists():
    jdk=BASE/'jdk-21.0.13+9'
    shutil.copy2(jdk/'lib/security/cacerts',trust)
    # Add only the managed machine's existing public CA certificates; retain TLS
    # validation and all official roots. This is not a credential store export.
    for cert in sorted(Path('/usr/local/share/ca-certificates').glob('*.crt')):
        subprocess.run([str(jdk/'bin/keytool'),'-importcert','-noprompt','-trustcacerts','-keystore',str(trust),'-storepass','changeit','-alias','runtime-'+cert.stem,'-file',str(cert)],check=True)
(BASE/'prepared-tools.json').write_text(json.dumps(proof,indent=2)+'\n')
print('Tools prepared; no emulator started. First build may need avatar-review.py build --online.')
