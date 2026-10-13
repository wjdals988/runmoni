param([switch]$Online)
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
if (!(Test-Path -LiteralPath '.secrets/signing.properties')) {
    throw 'Restore the retained Runmoni release key and ignored signing.properties. Do not generate a new key for an existing published app.'
}
$env:GRADLE_USER_HOME = Join-Path $PWD 'work/gradle-home'
$env:ANDROID_USER_HOME = Join-Path $PWD 'work/android-home'
$candidateRoots = @((Join-Path $env:GRADLE_USER_HOME 'wrapper/dists/gradle-8.13-bin'), (Join-Path $env:USERPROFILE '.gradle/wrapper/dists/gradle-8.13-bin'))
$gradleBinary = $candidateRoots | ForEach-Object { if (Test-Path -LiteralPath $_) { Get-ChildItem -Path (Join-Path $_ '*/gradle-8.13/bin/gradle.bat') -File -ErrorAction SilentlyContinue } } | Select-Object -First 1
if (!$gradleBinary) { throw 'Gradle 8.13 must already be installed/cached.' }
$buildArguments = @('--no-daemon', '--no-parallel', '--max-workers=1', '-Dorg.gradle.internal.instrumentation.agent=false', "-Duser.home=$(Join-Path $PWD 'work')", '--console=plain')
if (!$Online) { $buildArguments += '--offline' }
$buildArguments += @(':app:assembleRelease', ':app:testDebugUnitTest', ':app:lintRelease')
& $gradleBinary.FullName @buildArguments
if ($LASTEXITCODE -ne 0) { throw "Release build failed ($LASTEXITCODE)." }
