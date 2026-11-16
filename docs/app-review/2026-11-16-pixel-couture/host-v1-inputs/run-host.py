"""Compile a source snapshot of the actual pure pixel classes using cached tools."""
from pathlib import Path
import argparse, hashlib, json, os, re, subprocess, shutil
R=Path('/workspace/runmoni');W=R/'work/pixel-couture';E=R/'docs/app-review/2026-11-16-pixel-couture'
K=R/'work/avatar-reference-review/kotlin';J=R/'work/cloud-android/jdk-21.0.13+9/bin'
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
p=argparse.ArgumentParser();p.add_argument('--round',required=True);a=p.parse_args();assert re.fullmatch(r'v[1-9]\d*',a.round)
build=W/a.round;build.mkdir(exist_ok=False);classes=build/'classes';classes.mkdir();snap=build/'source';snap.mkdir()
domain=R/'app/src/main/java/com/runmoni/app/domain';avatar=R/'app/src/main/java/com/runmoni/app/avatar'
originals=[domain/'Models.kt',domain/'RunningKitColors.kt',domain/'AvatarCatalogue.kt',*[avatar/(n+'.java')for n in ['GarmentPalette','WardrobeClothing','SockClothing']],*[avatar/(n+'.kt')for n in ['AvatarEquipmentLayers','AvatarCostumeLayers','AvatarHeadwearLayers']],W/'PreviewPixelCouture.java',Path(__file__)]
frozen={str(f.relative_to(R)):sha(f)for f in originals}
for f in originals:shutil.copyfile(f,snap/f.name)
model=(snap/'Models.kt').read_text();gender=re.search(r'(?ms)^enum class AvatarGender\(.*?^\}',model).group();style=re.search(r'(?ms)^data class AvatarStyle\(.*?^\)',model).group()
generated=snap/'AvatarModel.kt';generated.write_text('package com.runmoni.app.domain\n'+gender+'\n'+style+'\n')
names=['kotlin-compiler-embeddable-2.0.21.jar','kotlin-stdlib-2.0.21.jar','kotlin-script-runtime-2.0.21.jar','kotlin-reflect-1.6.10.jar','kotlin-daemon-embeddable-2.0.21.jar','trove4j-1.0.20200330.jar','kotlinx-coroutines-core-jvm-1.6.4.jar','annotations-13.0.jar']
cp=os.pathsep.join(map(str,[classes,K/'kotlin-stdlib-2.0.21.jar',K/'annotations-13.0.jar']));log=build/'compile.txt'
def run(cmd):
 with log.open('a')as out:r=subprocess.run(list(map(str,cmd)),cwd=R,stdout=out,stderr=subprocess.STDOUT)
 if r.returncode:raise RuntimeError('Host compile failed: '+str(log))
run([J/'javac','-J-Xmx384m','--release','21','-d',classes,*[snap/(n+'.java')for n in ['GarmentPalette','WardrobeClothing','SockClothing']]])
run([J/'java','-Xmx512m','-cp',os.pathsep.join(str(K/n)for n in names),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-jvm-target','21','-classpath',cp,'-d',classes,generated,*[snap/n for n in ['RunningKitColors.kt','AvatarCatalogue.kt','AvatarEquipmentLayers.kt','AvatarCostumeLayers.kt','AvatarHeadwearLayers.kt']]])
run([J/'javac','-J-Xmx384m','--release','21','-classpath',cp,'-d',classes,snap/'PreviewPixelCouture.java'])
result=subprocess.run([str(J/'java'),'-Xmx512m','-Djava.awt.headless=true','-cp',cp,'PreviewPixelCouture',str(R),str(E),a.round],cwd=R,capture_output=True,text=True);(build/'host-output.txt').write_text(result.stdout+result.stderr)
if result.returncode:raise RuntimeError('Host checks failed: '+str(build/'host-output.txt'))
report=json.loads(result.stdout);report.update(round=a.round,sourceKind='HOST JVM production pixel code snapshot; NOT Android screenshot',sourceHashes=frozen,workingSourcesStillMatch=all(sha(f)==frozen[str(f.relative_to(R))]for f in originals),imageHashes={f.name:sha(f)for f in E.glob('host-*-'+a.round+'.png')})
assert report['workingSourcesStillMatch'],'Working files changed during host compilation; no final source claim'
target=E/('host-checks-'+a.round+'.json')
with target.open('x')as out:json.dump(report,out,ensure_ascii=False,indent=2);out.write('\n')
print(json.dumps(report,ensure_ascii=False))
