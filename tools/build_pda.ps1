param([string]$GradleExe, [string]$Sdk = "$env:LOCALAPPDATA/Android/Sdk", [string]$ServerUrl = '', [string]$EnvironmentName = '未配置服务环境')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
foreach ($fixtureName in @('pda-contract-fixture.json', 'pda-submission-fixture.json', 'pda-recall-fixture.json', 'pda-continuation-fixture.json', 'pda-disposition-fixture.json')) {
    if (-not (Test-Path -LiteralPath (Join-Path $projectRoot "server/build/$fixtureName"))) {
        throw 'Run tools/development_server.ps1 -Task Verify first. Android tests require signed server fixtures.'
    }
}
$previousSdk = $env:ANDROID_HOME
Push-Location $projectRoot
try {
    $env:ANDROID_HOME = $Sdk
    if (-not $GradleExe) {
        $cached = @(Get-ChildItem -Path "$env:USERPROFILE/.gradle/wrapper/dists/gradle-8.11.1-bin/*/gradle-8.11.1/bin/gradle.bat" -ErrorAction SilentlyContinue)
        $GradleExe = if ($cached.Count) { $cached[0].FullName } else { Join-Path $projectRoot 'gradlew.bat' }
    }
    & $GradleExe -p android :app:testDebugUnitTest :app:assembleDebug :app:lintDebug "-PpdaServerUrl=$ServerUrl" "-PpdaEnvironment=$EnvironmentName" --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'PDA build or verification failed; no APK was packaged.' }
    $source = Join-Path $projectRoot 'android/app/build/outputs/apk/debug/app-debug.apk'
    $signer = Join-Path $Sdk 'build-tools/36.1.0/apksigner.bat'
    $certificate = @(& $signer verify --print-certs $source)
    if ($LASTEXITCODE -ne 0) { throw 'PDA APK signature verification failed.' }
    $output = Join-Path $projectRoot 'outputs/pda'
    New-Item -ItemType Directory -Force -Path $output | Out-Null
    $name = 'Acrylic-PDA-0.7.0-debug.apk'
    Copy-Item -LiteralPath $source -Destination (Join-Path $output $name)
    $hash = (Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash.ToLowerInvariant()
    [System.IO.File]::WriteAllText((Join-Path $output 'SHA256SUMS.txt'), "$hash  $name`n")
    $info = @('Acrylic Outbound PDA 0.7.0-pairing', 'Package: com.acrylic.outbound.pda.debug',
        'Version code: 7; Room schema: 6; existing database and preferences remain readable', 'Minimum SDK: 28; Target SDK: 35; Compile SDK: 36',
        "Configured service: $ServerUrl", "Environment: $EnvironmentName",
        "APK bytes: $((Get-Item -LiteralPath $source).Length)", "APK SHA256: $hash",
        ($certificate | Where-Object { $_ -match 'SHA-256' }),
        'Debug development build. Separate from the accepted M71 Demo.',
        'Short-code pairing requires administrator approval and confirmation on the original PDA. Private-key proof and encrypted original requests support recovery without replacing an existing identity.',
        'Implemented: verified activation, local scanning, formal freeze/upload, signed receipts, controlled recall and same-person/same-device continuation with retained bales and independently approved release/quarantine evidence.',
        'Backend supports global dispatch guards, short-shipment approval and optional automatic inventory accounting.',
        'Physical evidence and activation retain stock; complete replacement submission atomically releases approved bales, quarantines isolated bales and transfers retained reservations. Device transfer, quarantine removal, temporary orders, corrections, general renewal, incremental sync and documents remain pending.',
        'Only a verified successful cloud receipt is dispatch clearance. Local or pending records are not clearance.',
        'No production deployment or M71 physical acceptance of this app has been performed.')
    [System.IO.File]::WriteAllLines((Join-Path $output 'APK_INFO.txt'), $info)
    Write-Output "Verified PDA APK: $output/$name"
} finally {
    $env:ANDROID_HOME = $previousSdk
    Pop-Location
}
