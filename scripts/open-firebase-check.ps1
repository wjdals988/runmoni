param([string]$ApkPath, [string]$AdbPath)
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
$projectRoot = $PWD.Path
$sdkRoot = Join-Path $env:LOCALAPPDATA 'Android/Sdk'
if (!$AdbPath) {
    $AdbPath = @((Join-Path $sdkRoot 'platform-tools/adb.exe'), (Join-Path $env:USERPROFILE 'Downloads/platform-tools-latest-windows/platform-tools/adb.exe')) | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
}
if (!$AdbPath) { throw 'ADB must already be installed. This script never downloads tools.' }
if (!$ApkPath) {
    $manifestPath = Join-Path $projectRoot 'outputs/latest-release.json'
    if (!(Test-Path -LiteralPath $manifestPath)) { throw 'Prepare the verified signed release and outputs/latest-release.json first.' }
    $manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
    $ApkPath = Join-Path $projectRoot "outputs/$($manifest.fileName)"
    if ((Get-FileHash -LiteralPath $ApkPath -Algorithm SHA256).Hash.ToLowerInvariant() -ne $manifest.sha256) { throw 'APK checksum mismatch.' }
}
if (!(Test-Path -LiteralPath $ApkPath)) { throw 'The signed APK is missing.' }
$env:ANDROID_AVD_HOME = Join-Path $projectRoot 'work/avd'
$env:ANDROID_USER_HOME = Join-Path $projectRoot 'work/android-home'
$env:ADB_VENDOR_KEYS = Join-Path $projectRoot 'work/android-home/adbkey'
$devices = (& $AdbPath devices) -join "`n"
if ($devices -match '(?m)^emulator-') { throw 'An AVD is already running. Use its window or close it before opening this login session.' }
$emulatorPath = Join-Path $sdkRoot 'emulator/emulator.exe'
if (!(Test-Path -LiteralPath $emulatorPath)) { throw 'The Android emulator must already be installed.' }
# Intentionally visible: the user explicitly requested a window for their own Google login.
# SDK help defines 1024:1024 in kbits/s (1,024 kbit/s each direction). This is not a
# guarantee that the previously reported PC network slowdown is resolved.
$process = Start-Process -WindowStyle Normal -FilePath $emulatorPath -ArgumentList @('-avd','Runmoni_API36','-port','5564','-no-audio','-no-snapshot','-gpu','swiftshader','-cores','2','-memory','1536','-netspeed','1024:1024','-netdelay','none','-timezone','Asia/Seoul') -PassThru
Write-Output "Opened the project AVD for interactive login (PID $($process.Id)). No passwords or tokens are read."
$booted = $false
for ($index = 0; $index -lt 60; $index++) {
    $boot = & $AdbPath -s emulator-5564 shell getprop sys.boot_completed 2>$null
    if ($boot -match '^1') { $booted = $true; break }
    Start-Sleep -Seconds 1
}
if (!$booted) { throw 'AVD boot did not finish. Inspect its visible window; do not start another copy.' }
$name = (& $AdbPath -s emulator-5564 emu avd name) -join "`n"
if ($name -notmatch '(?m)^Runmoni_API36\s*$') { throw 'Unexpected AVD. Installation stopped.' }
& $AdbPath -s emulator-5564 shell cmd connectivity airplane-mode disable
& $AdbPath -s emulator-5564 shell svc wifi disable
& $AdbPath -s emulator-5564 shell svc data enable
& $AdbPath -s emulator-5564 install -r $ApkPath
if ($LASTEXITCODE -ne 0) { throw 'Installation failed. Do not automatically uninstall an app containing records.' }
& $AdbPath -s emulator-5564 shell am start -n com.runmoni.app/.MainActivity
Write-Output 'In the AVD, open Runmoni settings and choose Google sign-in. Add the Google account yourself if prompted.'
Write-Output 'Follow docs/FIREBASE_DEVICE_CHECK.md. Close the AVD after the interactive check; it is not a background service.'
