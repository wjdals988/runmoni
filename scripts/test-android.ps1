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
# Native 320dp/font2 dialog cases run separately with scripts/test-design.ps1.
$result = & $AdbPath -s $Serial shell am instrument -w -r -e notClass 'com.runmoni.app.ui.RunmoniDialogDesignTest,com.runmoni.app.ui.AppInformationNativeTest' com.runmoni.app.test/androidx.test.runner.AndroidJUnitRunner
$result | Write-Output
# am instrument may exit 0 even when JUnit fails; inspect its actual completion report.
$report = $result -join "`n"
$declared = [regex]::Match($report, 'numtests=(\d+)')
if (!$declared.Success -or $report -notmatch "OK \($($declared.Groups[1].Value) tests\)" -or $report -match 'INSTRUMENTATION_STATUS_CODE: -[234]') {
    throw 'Not every discovered default Android test passed. Native large-font cases are separate.'
}
