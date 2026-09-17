param([string]$GradleExe, [string]$Sdk = "$env:LOCALAPPDATA/Android/Sdk")
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location $projectRoot
try {
    if (-not (Test-Path -LiteralPath 'local.properties')) {
        $sdkProperty = 'sdk.dir=' + $Sdk.Replace('\','/').Replace(':','\:')
        [System.IO.File]::WriteAllText((Join-Path $projectRoot 'local.properties'), $sdkProperty + "`n")
    }
    if (-not $GradleExe) {
        $cached = @(Get-ChildItem -Path "$env:USERPROFILE/.gradle/wrapper/dists/gradle-8.11.1-bin/*/gradle-8.11.1/bin/gradle.bat" -ErrorAction SilentlyContinue)
        $GradleExe = if ($cached.Count) { $cached[0].FullName } else { Join-Path $projectRoot 'gradlew.bat' }
    }
    & $GradleExe :demo:testDebugUnitTest :demo:assembleDebug :demo:lintDebug --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Build or verification failed; no APK was packaged.' }
    New-Item -ItemType Directory -Force 'demo-artifacts' | Out-Null
    Copy-Item -LiteralPath 'demo/build/outputs/apk/debug/demo-debug.apk' -Destination 'demo-artifacts/M71-Demo-0.1.19-debug.apk'
    $hash = (Get-FileHash 'demo-artifacts/M71-Demo-0.1.19-debug.apk' -Algorithm SHA256).Hash.ToLowerInvariant()
    [System.IO.File]::WriteAllText((Join-Path $projectRoot 'demo-artifacts/SHA256SUMS.txt'), "$hash  M71-Demo-0.1.19-debug.apk`n")
    $certificate = @(& (Join-Path $Sdk 'build-tools/36.1.0/apksigner.bat') verify --print-certs 'demo-artifacts/M71-Demo-0.1.19-debug.apk')
    if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed.' }
    $size = (Get-Item 'demo-artifacts/M71-Demo-0.1.19-debug.apk').Length
    $info = @('M71 Demo 0.1.19-demo', 'Package: com.acrylic.m71demo.debug',
        'Version code: 20', 'Minimum SDK: 28; Target SDK: 35; Compile SDK: 36', 'Room schema: 3',
        "APK bytes: $size", "APK SHA256: $hash", ($certificate | Where-Object { $_ -match 'SHA-256' }),
        'Purpose: isolated local validation only. M71 physical acceptance remains pending.')
    [System.IO.File]::WriteAllLines((Join-Path $projectRoot 'demo-artifacts/APK_INFO.txt'), $info)
} finally { Pop-Location }
