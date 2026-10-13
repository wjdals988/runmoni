param([string]$Serial, [string]$AdbPath = 'adb')
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
if (!$Serial) { throw 'Specify -Serial for the dedicated test device. The GPS/audio cases use synthetic locations and cache WAV fixtures.' }
Write-Host 'Start the loopback API with RUNMONI_PILOT_INVITE=runmoni-local-test and a separate validation DB before this suite.'
& $AdbPath -s $Serial reverse tcp:8787 tcp:8787
if ($LASTEXITCODE -ne 0) { throw 'ADB reverse failed.' }
& $AdbPath -s $Serial install -r app/build/outputs/apk/debug/app-debug.apk
if ($LASTEXITCODE -ne 0) { throw 'App installation failed.' }
& $AdbPath -s $Serial install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
if ($LASTEXITCODE -ne 0) { throw 'Instrumentation APK installation failed.' }
$result = & $AdbPath -s $Serial shell am instrument -w -r com.runmoni.app.test/androidx.test.runner.AndroidJUnitRunner
$result | Write-Output
# am instrument may exit 0 even when JUnit fails; inspect its actual completion report.
if (($result -join "`n") -notmatch 'OK \(21 tests\)') { throw 'The full 21-case Android suite did not pass.' }
