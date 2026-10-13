param([switch]$Online, [switch]$IncludeDeviceTests)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
Set-Location $projectRoot
$env:GRADLE_USER_HOME = Join-Path $projectRoot 'work/gradle-home'
$env:ANDROID_USER_HOME = Join-Path $projectRoot 'work/android-home'
New-Item -ItemType Directory -Force -Path $env:GRADLE_USER_HOME,$env:ANDROID_USER_HOME | Out-Null
# Never let the wrapper silently download a distribution during an offline run.
$candidateRoots = @((Join-Path $env:GRADLE_USER_HOME 'wrapper/dists/gradle-8.13-bin'), (Join-Path $env:USERPROFILE '.gradle/wrapper/dists/gradle-8.13-bin'))
$gradleBinary = $candidateRoots | ForEach-Object {
    if (Test-Path -LiteralPath $_) { Get-ChildItem -Path (Join-Path $_ '*/gradle-8.13/bin/gradle.bat') -File -ErrorAction SilentlyContinue }
} | Select-Object -First 1
if (!$gradleBinary) { throw 'Gradle 8.13 is not installed. Install/cache it before building; this script will not download it implicitly.' }
$buildArguments = @('--no-daemon', '--no-parallel', '--max-workers=1', '-Dorg.gradle.internal.instrumentation.agent=false', "-Duser.home=$(Join-Path $projectRoot 'work')", '--console=plain')
if (!$Online) { $buildArguments += '--offline' }
$buildArguments += @(':app:assembleDebug', ':app:testDebugUnitTest', ':app:lintDebug')
if ($IncludeDeviceTests) { $buildArguments += ':app:assembleDebugAndroidTest' }
& $gradleBinary.FullName @buildArguments
if ($LASTEXITCODE -ne 0) { throw "Build failed ($LASTEXITCODE). See Gradle output. Use -Online only when downloads are acceptable." }
