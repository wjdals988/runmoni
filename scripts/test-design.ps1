param([string]$Serial = 'emulator-5564', [string]$AdbPath = 'adb')
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
$avdName = (& $AdbPath -s $Serial emu avd name) -join "`n"
if ($LASTEXITCODE -ne 0 -or $avdName -notmatch '(?m)^Runmoni_API36\s*$') {
    throw 'Use the dedicated Runmoni_API36 AVD only. This suite changes viewport/font settings.'
}
function Invoke-AdbChecked([string[]]$Arguments) {
    & $AdbPath -s $Serial @Arguments
    if ($LASTEXITCODE -ne 0) { throw "ADB step failed: $($Arguments[0])" }
}
function Invoke-UiPass([string]$Classes, [int]$Expected, [string]$LogPath) {
    $result = & $AdbPath -s $Serial shell am instrument -w -r -e class $Classes com.runmoni.app.test/androidx.test.runner.AndroidJUnitRunner | Tee-Object -FilePath $LogPath
    $result | Select-Object -Last 7 | Write-Output
    if ($LASTEXITCODE -ne 0 -or ($result -join "`n") -notmatch "OK \($Expected tests\)") {
        throw "UI pass did not report $Expected successful tests. See $LogPath"
    }
}
# No API server, download, Google sign-in or real GPS is part of these fixtures.
Invoke-AdbChecked @('shell', 'svc', 'wifi', 'disable')
Invoke-AdbChecked @('shell', 'svc', 'data', 'disable')
Invoke-AdbChecked @('shell', 'cmd', 'connectivity', 'airplane-mode', 'enable')
Invoke-AdbChecked @('install', '-r', 'app/build/outputs/apk/debug/app-debug.apk')
Invoke-AdbChecked @('install', '-r', 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')
$passErrors = [System.Collections.Generic.List[string]]::new()
try {
    Invoke-AdbChecked @('shell', 'wm', 'size', 'reset')
    Invoke-AdbChecked @('shell', 'settings', 'put', 'system', 'font_scale', '1.0')
    Invoke-AdbChecked @('shell', 'am', 'force-stop', 'com.runmoni.app')
    try { Invoke-UiPass 'com.runmoni.app.ui.RunmoniUiTest,com.runmoni.app.ui.RunmoniDesignTest,com.runmoni.app.ui.AvatarIntegrationTest,com.runmoni.app.ui.AvatarPlaybackTest,com.runmoni.app.ui.AppInformationTest,com.runmoni.app.ui.AppUpdateControllerTest' 33 'work/design-ui-final.log' }
    catch { $passErrors.Add($_.Exception.Message) }
    # 840px / 2.625 density = 320dp. A real system font change is required:
    # CompositionLocal overrides do not propagate to Android Dialog windows.
    Invoke-AdbChecked @('shell', 'wm', 'size', '840x1680')
    Invoke-AdbChecked @('shell', 'settings', 'put', 'system', 'font_scale', '2.0')
    Invoke-AdbChecked @('shell', 'settings', 'put', 'secure', 'show_ime_with_hard_keyboard', '1')
    Invoke-AdbChecked @('shell', 'am', 'force-stop', 'com.runmoni.app')
    try { Invoke-UiPass 'com.runmoni.app.ui.RunmoniDialogDesignTest,com.runmoni.app.ui.AppInformationNativeTest' 5 'work/design-ui-native-large-final.log' }
    catch { $passErrors.Add($_.Exception.Message) }
} finally {
    Invoke-AdbChecked @('shell', 'wm', 'size', 'reset')
    Invoke-AdbChecked @('shell', 'settings', 'put', 'system', 'font_scale', '1.0')
    Invoke-AdbChecked @('shell', 'settings', 'put', 'secure', 'show_ime_with_hard_keyboard', '0')
}
if ($passErrors.Count -gt 0) { throw ($passErrors -join "`n") }
Write-Output 'UI/controller fixtures: 38 passed. The caller must close the verified project AVD after review.'
