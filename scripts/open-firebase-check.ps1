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
$serial = 'emulator-5564'
function Invoke-ProjectAdb {
    $deviceArguments = @($args)
    $result = & $AdbPath -s $serial @deviceArguments
    if ($LASTEXITCODE -ne 0) { throw "ADB command failed: $($deviceArguments[0])." }
    $result
}
function Get-ProjectDefaultNetwork {
    $line = (Invoke-ProjectAdb @('shell','dumpsys','connectivity')) | Where-Object { $_ -match '^Active default network:' } | Select-Object -First 1
    if ($line -match '^Active default network:\s+(\d+)\s*$') { return $Matches[1] }
    return $null
}
# Intentionally visible: this helper is run by the user in an interactive PowerShell
# terminal when ready for their own Google login. It is not an overnight background task.
# -netspeed affects Ethernet/Cellular. Wi-Fi uses the guest's separate download setting.
# Neither setting guarantees PC-wide throughput or resolution of the prior slowdown.
$priorIngressLimit = $null
try {
    $process = Start-Process -WindowStyle Normal -FilePath $emulatorPath -ArgumentList @('-avd','Runmoni_API36','-port','5564','-no-audio','-no-snapshot','-gpu','swiftshader','-cores','2','-memory','1536','-netspeed','1024:1024','-netdelay','none','-timezone','Asia/Seoul') -PassThru
    Write-Output "Opened the project AVD for interactive login (PID $($process.Id)). No passwords or tokens are read."
    $booted = $false
    for ($index = 0; $index -lt 60; $index++) {
        $boot = & $AdbPath -s $serial shell getprop sys.boot_completed 2>$null
        if ($boot -match '^1') { $booted = $true; break }
        Start-Sleep -Seconds 1
    }
    if (!$booted) { throw 'AVD boot did not finish. Do not start another copy.' }
    $name = (Invoke-ProjectAdb @('emu','avd','name')) -join "`n"
    if ($name -notmatch '(?m)^Runmoni_API36\s*$') { throw 'Unexpected AVD. Installation stopped.' }
    # This dedicated test AVD may retain a large-font audit configuration. Always
    # present the human login session at its ordinary baseline; never clamp app text.
    Invoke-ProjectAdb @('shell','wm','size','reset') | Out-Null
    Invoke-ProjectAdb @('shell','wm','density','reset') | Out-Null
    Invoke-ProjectAdb @('shell','settings','put','system','font_scale','1.0') | Out-Null
    $fontScale = ((Invoke-ProjectAdb @('shell','settings','get','system','font_scale')) -join '').Trim()
    if ($fontScale -ne '1.0') { throw 'The dedicated AVD font baseline was not restored.' }
    Write-Output 'Dedicated AVD display baseline: native size/density, font scale 100%.'
    # Install while offline; preserve existing app records and never auto-uninstall.
    Invoke-ProjectAdb @('shell','svc','wifi','disable') | Out-Null
    Invoke-ProjectAdb @('shell','svc','data','disable') | Out-Null
    Invoke-ProjectAdb @('shell','cmd','connectivity','airplane-mode','enable') | Out-Null
    Invoke-ProjectAdb @('install','-r',$ApkPath) | Out-Null
    $previousIngress = ((Invoke-ProjectAdb @('shell','settings','get','global','ingress_rate_limit_bytes_per_second')) -join '').Trim()
    $previousIngressNumber = 0L
    if ($previousIngress -ne 'null' -and
        ($previousIngress -notmatch '^-?\d+$' -or ![long]::TryParse($previousIngress, [ref]$previousIngressNumber) -or
         $previousIngressNumber -lt -1 -or $previousIngressNumber -gt 4294967295)) {
        throw 'Unexpected saved guest download setting; it has not been changed.'
    }
    $priorIngressLimit = $previousIngress
    Invoke-ProjectAdb @('shell','settings','put','global','ingress_rate_limit_bytes_per_second','65536') | Out-Null
    $configured = ((Invoke-ProjectAdb @('shell','settings','get','global','ingress_rate_limit_bytes_per_second')) -join '').Trim()
    if ($configured -ne '65536') { throw 'Guest download limit was not configured; online check stopped.' }
    Invoke-ProjectAdb @('shell','cmd','connectivity','airplane-mode','disable') | Out-Null
    Invoke-ProjectAdb @('shell','svc','wifi','enable') | Out-Null
    $network = $null
    for ($index = 0; $index -lt 5; $index++) {
        Start-Sleep -Seconds 2
        $network = Get-ProjectDefaultNetwork
        if ($network) { break }
    }
    if (!$network) {
        # Fixed emulator AP only. No real Wi-Fi password or host networking changes.
        Invoke-ProjectAdb @('shell','cmd','wifi','connect-network','AndroidWifi','open') | Out-Null
        for ($index = 0; $index -lt 10; $index++) {
            Start-Sleep -Seconds 2
            $network = Get-ProjectDefaultNetwork
            if ($network) { break }
        }
    }
    if (!$network) { throw 'The project AVD has no default network. Google login has not started; inspect the virtual AndroidWifi connection first.' }
    Write-Output "Default network $network is present. Guest download setting: 65536 B/s; this does not verify HTTPS or Google sign-in."
    Invoke-ProjectAdb @('shell','am','start','-n','com.runmoni.app/.MainActivity') | Out-Null
    Write-Output 'In the AVD, open Runmoni settings and choose Google sign-in. Enter credentials only in the Google screen.'
    Write-Output 'Follow docs/FIREBASE_DEVICE_CHECK.md. Keep this terminal open until finished.'
    [void](Read-Host '검증을 마치면 이 터미널에서 Enter를 누르세요. 설정을 복원하고 전용 AVD를 종료합니다')
} finally {
    $currentName = (& $AdbPath -s $serial emu avd name 2>$null) -join "`n"
    if ($currentName -match '(?m)^Runmoni_API36\s*$') {
        & $AdbPath -s $serial shell svc wifi disable 2>$null | Out-Null
        & $AdbPath -s $serial shell svc data disable 2>$null | Out-Null
        & $AdbPath -s $serial shell cmd connectivity airplane-mode enable 2>$null | Out-Null
        if ($null -ne $priorIngressLimit) {
            if ($priorIngressLimit -eq 'null') { & $AdbPath -s $serial shell settings delete global ingress_rate_limit_bytes_per_second 2>$null | Out-Null }
            else { & $AdbPath -s $serial shell settings put global ingress_rate_limit_bytes_per_second $priorIngressLimit 2>$null | Out-Null }
            $restored = ((& $AdbPath -s $serial shell settings get global ingress_rate_limit_bytes_per_second 2>$null) -join '').Trim()
            if ($restored -ne $priorIngressLimit) { Write-Warning 'The original guest download setting could not be confirmed as restored.' }
        }
        # SettingsProvider persists asynchronously. Allow its queued write to run
        # and sync the guest before terminating the emulator process.
        Start-Sleep -Seconds 2
        & $AdbPath -s $serial shell sync 2>$null | Out-Null
        & $AdbPath -s $serial emu kill
        if ($LASTEXITCODE -ne 0) { Write-Warning 'Close the verified Runmoni_API36 window manually; emulator shutdown was not confirmed.' }
    } else {
        Write-Warning 'The project AVD is already closed or could not be identified. No other device was changed. If it was force-closed, inspect its saved download setting next time.'
    }
}
