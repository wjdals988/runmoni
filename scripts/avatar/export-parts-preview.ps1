param(
    [string]$JavaHome = '',
    [string]$OutputDirectory = 'docs/design/avatars/v0.4.0/host-preview'
)

$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$supportRoot = Join-Path $PSScriptRoot 'host-preview'
$cacheRoot = Join-Path $projectRoot 'work/gradle-home/caches/modules-2/files-2.1'
$assetRoot = Join-Path $projectRoot 'app/src/main/assets'
$manifestPath = Join-Path $assetRoot 'avatars/v0.4.0/manifest.json'
if (!(Test-Path -LiteralPath $manifestPath -PathType Leaf)) { throw "Missing local production manifest: $manifestPath" }

# All dependencies are read from this project's existing cache. Never install/download/start Gradle or AVD.
function Get-CachedJar([string]$Group, [string]$Artifact, [string]$Version) {
    $directory = Join-Path $cacheRoot "$Group/$Artifact/$Version"
    if (!(Test-Path -LiteralPath $directory -PathType Container)) { throw "Offline dependency missing: $Group/$Artifact/$Version" }
    $found = @(Get-ChildItem -LiteralPath $directory -Recurse -File -Filter "$Artifact-$Version.jar" | Sort-Object FullName)
    if ($found.Count -ne 1) { throw "Expected one cached $Artifact-$Version.jar, found $($found.Count); resolve cache explicitly." }
    return $found[0].FullName
}

$jdkCandidates = @(
    $JavaHome,
    'C:/Program Files/Java/jdk-21',
    'C:/Program Files/Eclipse Adoptium/jdk-21.0.7.6-hotspot',
    $env:JAVA_HOME,
    'C:/Program Files/Android/Android Studio/jbr'
)
$selectedJdk = $jdkCandidates | Where-Object {
    $_ -and (Test-Path -LiteralPath (Join-Path $_ 'bin/javac.exe') -PathType Leaf) -and
    (Test-Path -LiteralPath (Join-Path $_ 'bin/java.exe') -PathType Leaf)
} | Select-Object -First 1
if (!$selectedJdk) { throw 'A cached JDK 21 is required. Supply -JavaHome; this script does not install a JDK.' }
if ($JavaHome -and ([IO.Path]::GetFullPath($selectedJdk) -ne [IO.Path]::GetFullPath($JavaHome))) {
    throw "The requested -JavaHome has no java.exe/javac.exe: $JavaHome"
}
$releasePath = Join-Path $selectedJdk 'release'
if (!(Test-Path -LiteralPath $releasePath -PathType Leaf) -or
    !([IO.File]::ReadAllText($releasePath) -match 'JAVA_VERSION="21(?:\.|"|-)')) {
    throw "Expected JDK 21 from local release metadata: $selectedJdk"
}
$java = Join-Path $selectedJdk 'bin/java.exe'
$javac = Join-Path $selectedJdk 'bin/javac.exe'
$compiler = Get-CachedJar 'org.jetbrains.kotlin' 'kotlin-compiler-embeddable' '2.0.21'
$stdlib = Get-CachedJar 'org.jetbrains.kotlin' 'kotlin-stdlib' '2.0.21'
$annotations = Get-CachedJar 'org.jetbrains' 'annotations' '13.0'
$compilerJars = @(
    $compiler, $stdlib, $annotations,
    (Get-CachedJar 'org.jetbrains.kotlin' 'kotlin-script-runtime' '2.0.21'),
    (Get-CachedJar 'org.jetbrains.kotlin' 'kotlin-reflect' '1.6.10'),
    (Get-CachedJar 'org.jetbrains.kotlin' 'kotlin-daemon-embeddable' '2.0.21'),
    (Get-CachedJar 'org.jetbrains.intellij.deps' 'trove4j' '1.0.20200330'),
    (Get-CachedJar 'org.jetbrains.kotlinx' 'kotlinx-coroutines-core-jvm' '1.6.4')
)
$buildDirectory = Join-Path $projectRoot ("work/avatar-host-preview/" + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [Guid]::NewGuid().ToString('N').Substring(0, 8))
$classes = Join-Path $buildDirectory 'classes'
New-Item -ItemType Directory -Force -Path $classes | Out-Null
if (![IO.Path]::IsPathRooted($OutputDirectory)) { $OutputDirectory = Join-Path $projectRoot $OutputDirectory }
$OutputDirectory = [IO.Path]::GetFullPath($OutputDirectory)

# Extract the exact current standalone model declaration; compile the actual catalogue and rendering files unchanged.
$models = Join-Path $projectRoot 'app/src/main/java/com/runmoni/app/domain/Models.kt'
$declaration = [regex]::Match([IO.File]::ReadAllText($models), '(?ms)^data class AvatarStyle\(.*?^\)')
if (!$declaration.Success) { throw 'Cannot extract the current AvatarStyle declaration; update this host wrapper before continuing.' }
$generatedModel = Join-Path $buildDirectory 'AvatarStyle.kt'
$utf8 = New-Object System.Text.UTF8Encoding($false)
[IO.File]::WriteAllText($generatedModel, "package com.runmoni.app.domain`n`n$($declaration.Value)`n", $utf8)
$repositorySource = Join-Path $projectRoot 'app/src/main/java/com/runmoni/app/avatar/AvatarArtRepository.kt'
$rendererSource = Join-Path $projectRoot 'app/src/main/java/com/runmoni/app/avatar/AvatarSpriteRenderer.kt'
$catalogueSource = Join-Path $projectRoot 'app/src/main/java/com/runmoni/app/domain/AvatarCatalogue.kt'
$javaSources = @(Get-ChildItem -LiteralPath $supportRoot -Recurse -File -Filter '*.java' | Sort-Object FullName | Select-Object -ExpandProperty FullName)
if ($javaSources.Count -eq 0) { throw 'Host compatibility Java sources are missing.' }

$runId = [Guid]::NewGuid().ToString('N')
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
function Write-PreviewStatus([string]$Status, [string]$Message) {
    $state = [ordered]@{
        runId = $runId; status = $Status; updatedAtUtc = [DateTime]::UtcNow.ToString('o')
        surface = 'HOST Java2D compatibility preview; NOT Android capture'; message = $Message
    }
    [IO.File]::WriteAllText((Join-Path $OutputDirectory 'run-status.json'), ($state | ConvertTo-Json), $utf8)
}
Write-PreviewStatus 'running' 'Partial files and any older checks.json are diagnostic only until this run passes.'
try {

Write-Host '1/3 Compile host adapters (JDK 21, local only).'
& $javac '-J-Xmx384m' '--release' '21' '-encoding' 'UTF-8' '-d' $classes @javaSources
if ($LASTEXITCODE -ne 0) { throw "Host javac failed ($LASTEXITCODE). No app source was modified." }
Write-Host '2/3 Compile unchanged production Kotlin repository, renderer, catalogue and extracted model.'
$compilerClasspath = $compilerJars -join [IO.Path]::PathSeparator
$runtimeClasspath = @($classes, $stdlib, $annotations) -join [IO.Path]::PathSeparator
$kotlinArguments = @(
    '-Xmx640m', '-Djava.awt.headless=true', '-cp', $compilerClasspath,
    'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-no-stdlib', '-no-reflect',
    '-jvm-target', '21', '-classpath', $runtimeClasspath, '-d', $classes,
    $generatedModel, $catalogueSource, $repositorySource, $rendererSource,
    (Join-Path $supportRoot 'HostPreview.kt')
)
& $java @kotlinArguments
if ($LASTEXITCODE -ne 0) { throw "Host Kotlin compilation failed ($LASTEXITCODE). Preview export was not run." }
Write-Host '3/3 Export HOST Java2D PNGs/GIFs/contact sheets and bounded host checks.'
& $java '-Xmx640m' '-Djava.awt.headless=true' '-cp' $runtimeClasspath 'com.runmoni.app.avatar.HostPreviewKt' $assetRoot $OutputDirectory
if ($LASTEXITCODE -ne 0) { throw "Host preview assertion/export failed ($LASTEXITCODE). Partial files are diagnostic only; do not treat them as a passed preview." }

$trackedSources = @($models, $catalogueSource, $repositorySource, $rendererSource, $manifestPath, $PSCommandPath)
$trackedSources += @(Get-ChildItem -LiteralPath $supportRoot -Recurse -File | Sort-Object FullName | Select-Object -ExpandProperty FullName)
$provenance = [ordered]@{
    surface = 'HOST Java2D compatibility preview; NOT Android capture'
    exportedAtUtc = [DateTime]::UtcNow.ToString('o')
    javaHome = $selectedJdk
    kotlinCompiler = '2.0.21'
    buildDirectory = $buildDirectory
    sourceSha256 = @($trackedSources | ForEach-Object {
        [ordered]@{ path = $_; sha256 = (Get-FileHash -LiteralPath $_ -Algorithm SHA256).Hash.ToLowerInvariant() }
    })
    artworkSha256 = @(Get-ChildItem -LiteralPath (Split-Path $manifestPath -Parent) -Recurse -File -Filter '*.png' | Sort-Object FullName | ForEach-Object {
        [ordered]@{ path = $_.FullName; sha256 = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant() }
    })
}
[IO.File]::WriteAllText((Join-Path $OutputDirectory 'source-provenance.json'), ($provenance | ConvertTo-Json -Depth 8), $utf8)
Write-PreviewStatus 'passed' 'Host checks completed. Android rasterization/playback/device behavior remains unverified.'
Write-Host "Host preview complete: $OutputDirectory/index.html"
Write-Host 'No Android/Compose/device result is implied. No AVD, network, installation or Gradle task was used.'
} catch {
    Write-PreviewStatus 'failed' $_.Exception.Message
    throw
}
