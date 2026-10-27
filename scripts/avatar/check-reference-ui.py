#!/usr/bin/env python3
"""Optional isolated Kotlin/Compose compile with pre-fetched, verified local tools.

No Gradle, installation, network, AVD, signing or APK generation. This does not
compile the complete application or substitute for an Android runtime test.
"""
import hashlib
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
BASE = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else ROOT / "work/avatar-reference-review"
for directory in (BASE / "kotlin", BASE / "compose"):
    sidecars = list(directory.glob("*.sha1"))
    assert sidecars, f"Missing verified compiler/Compose cache: {directory}"
    for checksum in sidecars:
        artifact = checksum.with_suffix("")
        assert hashlib.sha1(artifact.read_bytes()).hexdigest() == checksum.read_text().strip(), artifact
# Android API jar extracted from Google's platform-36_r02.zip. Check the API jar
# against the checksum-verified archive, not a file with the same name.
import zipfile
archive = BASE / "tools/platform-36_r02.zip"
assert hashlib.sha1(archive.read_bytes()).hexdigest() == "2c1a80dd4d9f7d0e6dd336ec603d9b5c55a6f576"
with zipfile.ZipFile(archive) as package:
    entry = next(n for n in package.namelist() if n.endswith("/android.jar"))
    assert package.read(entry) == (BASE / "tools/android-36.jar").read_bytes()
for artifact in (BASE / "compose").glob("*.aar"):
    # Extracted class jars must still match their verified AARs.
    name = artifact.stem.rsplit("-", 1)[0]
    with zipfile.ZipFile(artifact) as package:
        assert package.read("classes.jar") == (BASE / "compose" / (name + ".jar")).read_bytes()

source = ROOT / "app/src/main/java/com/runmoni/app"
small_note = next(line for line in (source / "ui/RunmoniApp.kt").read_text().splitlines() if line.startswith("internal fun SmallNote("))
note = BASE / "SmallNote.kt"
note.write_text("package com.runmoni.app.ui\nimport androidx.compose.runtime.Composable\nimport androidx.compose.material3.*\n@Composable\n" + small_note + "\n")
classpath = ":".join(str(p) for p in [BASE / "kotlin/kotlin-stdlib-2.0.21.jar", BASE / "tools/android-36.jar", *sorted((BASE / "compose").glob("*.jar"))])
subprocess.run([
    "java", "-cp", str(BASE / "kotlin/*"), "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
    "-no-stdlib", "-no-reflect", "-jvm-target", "17", "-classpath", classpath,
    "-Xplugin=" + str(BASE / "compose/kotlin-compose-compiler-plugin-embeddable-2.0.21.jar"),
    "-d", str(BASE / "kotlin-classes"),
    str(source / "avatar/GarmentPalette.java"), str(source / "avatar/AvatarReferenceReview.kt"),
    str(source / "ui/AvatarReferenceDialog.kt"), str(source / "ui/RunmoniTheme.kt"), str(note),
], check=True, cwd=ROOT)
print("Isolated Android API 36 / Compose source compile passed. NOT an APK or runtime test.")
