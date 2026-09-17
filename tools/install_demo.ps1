param([string]$Serial, [string]$Adb = "$env:LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe")
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$apk = Join-Path $projectRoot 'demo-artifacts/M71-Demo-0.1.19-debug.apk'
if (-not (Test-Path -LiteralPath $Adb)) { throw 'ADB not found. Pass -Adb with the SDK platform-tools path.' }
if (-not (Test-Path -LiteralPath $apk)) { throw 'Demo APK not found; build and package it first.' }
if (-not $Serial) {
    $devices = @(& $Adb devices | Select-String '^([^\s]+)\s+device$' | ForEach-Object { $_.Matches[0].Groups[1].Value })
    if ($devices.Count -ne 1) { throw 'Connect and authorize one device, or pass -Serial explicitly.' }
    $Serial = $devices[0]
}
& $Adb -s $Serial install -r $apk
if ($LASTEXITCODE -ne 0) { throw 'Installation failed. Do not uninstall or erase existing evidence.' }
& $Adb -s $Serial shell am start -n com.acrylic.m71demo.debug/com.acrylic.m71demo.ui.MainActivity
if ($LASTEXITCODE -ne 0) { throw 'Could not start the Demo.' }
