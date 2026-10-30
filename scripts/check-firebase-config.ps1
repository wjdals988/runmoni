$ErrorActionPreference = 'Stop'
$runmoniRoot = Split-Path $PSScriptRoot -Parent
$runmoniConfigPath = Join-Path $runmoniRoot '.secrets/firebase/google-services.json'
if (!(Test-Path -LiteralPath $runmoniConfigPath)) {
    Write-Output 'NOT_CONFIGURED: Save the Android google-services.json to .secrets/firebase/google-services.json.'
    exit 2
}
$runmoniConfig = Get-Content -LiteralPath $runmoniConfigPath -Raw | ConvertFrom-Json
if ($runmoniConfig.private_key -or $runmoniConfig.type -eq 'service_account') { throw 'Use Android configuration, never a service-account key.' }
if ($runmoniConfig.project_info.project_id -ne 'runmoni-3c08e') { throw 'Wrong project: expected runmoni-3c08e.' }
$runmoniClient = $runmoniConfig.client | Where-Object { $_.client_info.android_client_info.package_name -eq 'com.runmoni.app' } | Select-Object -First 1
if (!$runmoniClient) { throw 'Register Android package com.runmoni.app and download its configuration.' }
if (!$runmoniClient.api_key.current_key) { throw 'Android API configuration is incomplete.' }
$runmoniWebClient = $runmoniClient.oauth_client | Where-Object client_type -eq 3 | Select-Object -First 1
if (!$runmoniWebClient.client_id) { throw 'Enable Google sign-in, save, then download the updated Android configuration.' }
$runmoniApprovedSigning = Get-Content -LiteralPath (Join-Path $runmoniRoot 'config/android-release-signing.json') -Raw | ConvertFrom-Json
$runmoniReleaseOAuth = $runmoniClient.oauth_client | Where-Object { $_.client_type -eq 1 -and $_.android_info.package_name -eq 'com.runmoni.app' -and ($_.android_info.certificate_hash -replace ':','').ToLowerInvariant() -eq $runmoniApprovedSigning.certificateSha1 } | Select-Object -First 1
if (!$runmoniReleaseOAuth) { throw 'Register the current certificateSha1 from config/android-release-signing.json, then download the updated Android configuration.' }
[pscustomobject]@{
    ProjectId = $runmoniConfig.project_info.project_id
    AndroidPackage = $runmoniClient.client_info.android_client_info.package_name
    WebOAuthConfigured = $true
    ReleaseCertificateInAndroidOAuth = [bool]$runmoniReleaseOAuth
    Status = 'Configuration structurally valid; remote sign-in and Firestore still require verification.'
} | Format-List
