<#
.SYNOPSIS
  Build, verify and package a PDA APK for one channel.

.DESCRIPTION
  Gates (same as before): Android host unit tests and lint must pass before an APK is packaged.
  The update-channel public key is injected so the built app can actually verify update manifests;
  the signing identity comes from android/app/build.gradle.kts (signingConfigs, release buildType).

  ASCII-only on purpose: Windows PowerShell 5.1 reads BOM-less scripts with the ANSI code page, so
  non-ASCII literals inside this file would be corrupted. Pass non-ASCII values as arguments instead
  (command-line arguments are UTF-16 and safe).

.EXAMPLE
  powershell -NoProfile -ExecutionPolicy Bypass -File tools/build_pda.ps1 -BuildType release
#>
param(
    [string]$GradleExe,
    [string]$Sdk = "$env:LOCALAPPDATA/Android/Sdk",
    [string]$ServerUrl = '',
    [string]$EnvironmentName = 'unset',
    [ValidateSet('debug', 'staging', 'release')]
    [string]$BuildType = 'debug',
    [string]$UpdatePublicKeyFile = ''
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
foreach ($fixtureName in @('pda-contract-fixture.json', 'pda-submission-fixture.json', 'pda-recall-fixture.json', 'pda-continuation-fixture.json', 'pda-disposition-fixture.json')) {
    if (-not (Test-Path -LiteralPath (Join-Path $projectRoot "server/build/$fixtureName"))) {
        throw 'Run tools/development_server.ps1 -Task Verify first. Android tests require signed server fixtures.'
    }
}
if (-not $UpdatePublicKeyFile) {
    $UpdatePublicKeyFile = Join-Path $projectRoot '.local/pda-update-signing/update-public-key.base64'
}
$updatePublicKey = ''
if (Test-Path -LiteralPath $UpdatePublicKeyFile) {
    $updatePublicKey = (Get-Content -LiteralPath $UpdatePublicKeyFile -Raw).Trim()
} elseif ($BuildType -eq 'release') {
    throw "Update public key not found: $UpdatePublicKeyFile. A release APK without it cannot check for updates."
} else {
    Write-Warning "Update public key not found: $UpdatePublicKeyFile. The update page will fail in this build."
}
$previousSdk = $env:ANDROID_HOME
Push-Location $projectRoot
try {
    $env:ANDROID_HOME = $Sdk
    if (-not $GradleExe) {
        $cached = @(Get-ChildItem -Path "$env:USERPROFILE/.gradle/wrapper/dists/gradle-8.11.1-bin/*/gradle-8.11.1/bin/gradle.bat" -ErrorAction SilentlyContinue)
        $GradleExe = if ($cached.Count) { $cached[0].FullName } else { Join-Path $projectRoot 'gradlew.bat' }
    }
    $assembleTask = if ($BuildType -eq 'debug') { ':app:assembleDebug' } else { ":app:assemble$($BuildType.Substring(0,1).ToUpper() + $BuildType.Substring(1))" }
    & $GradleExe -p android :app:testDebugUnitTest :app:lintDebug $assembleTask `
        "-PpdaServerUrl=$ServerUrl" "-PpdaEnvironment=$EnvironmentName" "-PpdaUpdatePublicKey=$updatePublicKey" --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'PDA build or verification failed; no APK was packaged.' }

    $source = Join-Path $projectRoot "android/app/build/outputs/apk/$BuildType/app-$BuildType.apk"
    if (-not (Test-Path -LiteralPath $source)) { throw "Expected APK not found: $source" }
    $signer = Join-Path $Sdk 'build-tools/36.1.0/apksigner.bat'
    $aapt2 = Join-Path $Sdk 'build-tools/36.1.0/aapt2.exe'
    $certificate = @(& $signer verify --print-certs $source)
    if ($LASTEXITCODE -ne 0) { throw 'PDA APK signature verification failed.' }
    $badging = @(& $aapt2 dump badging $source)
    if ($LASTEXITCODE -ne 0) { throw 'Could not read APK badging.' }
    $packageLine = ($badging | Where-Object { $_ -match '^package:' } | Select-Object -First 1)
    $versionCode = if ($packageLine -match "versionCode='([^']+)'") { $Matches[1] } else { 'unknown' }
    $versionName = if ($packageLine -match "versionName='([^']+)'") { $Matches[1] } else { 'unknown' }
    $packageName = if ($packageLine -match "name='([^']+)'") { $Matches[1] } else { 'unknown' }
    $certLine = ($certificate | Where-Object { $_ -match 'certificate SHA-256 digest' } | Select-Object -First 1)

    $output = Join-Path $projectRoot 'outputs/pda'
    New-Item -ItemType Directory -Force -Path $output | Out-Null
    $name = if ($versionName -like "*$BuildType") { "Acrylic-PDA-$versionName.apk" } else { "Acrylic-PDA-$versionName-$BuildType.apk" }
    Copy-Item -LiteralPath $source -Destination (Join-Path $output $name) -Force
    $hash = (Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash.ToLowerInvariant()
    [System.IO.File]::WriteAllText((Join-Path $output 'SHA256SUMS.txt'), "$hash  $name`n")
    $info = @("Acrylic Outbound PDA $versionName ($BuildType channel)",
        "Package: $packageName", "Version code: $versionCode; Version name: $versionName",
        'Minimum SDK: 28; Target SDK: 35; Compile SDK: 36',
        "Configured service: $ServerUrl", "Environment: $EnvironmentName",
        "Update channel: $BuildType", "Update public key injected: $([bool]$updatePublicKey)",
        "APK bytes: $((Get-Item -LiteralPath $source).Length)", "APK SHA256: $hash",
        $certLine,
        'Signed with the R06 production signing identity; see docs/PDA_SIGNING_GUIDE.md.',
        'Implemented: verified activation, local scanning, formal freeze/upload, signed receipts, controlled recall, continuation, temporary draft freeze/submission with evidence upload and recovery.',
        'Only a verified successful cloud receipt is dispatch clearance. Local or pending records are not clearance.',
        'Temporary confirmation, archive, link and physical disposition remain closed; no production deployment or M71 physical acceptance has been performed.')
    [System.IO.File]::WriteAllLines((Join-Path $output 'APK_INFO.txt'), $info)
    Write-Output "Verified PDA APK: $output/$name"
    Write-Output "Signer: $certLine"
} finally {
    $env:ANDROID_HOME = $previousSdk
    Pop-Location
}
