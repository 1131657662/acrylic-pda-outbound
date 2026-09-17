param([Parameter(Mandatory=$true)][string]$Serial,
    [string]$Adb = "$env:LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe")
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$destination = Join-Path $projectRoot ('demo-artifacts/runs/' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
New-Item -ItemType Directory -Path $destination | Out-Null
# First stop the software load and select Export in the app; immutable export files are safe to pull.
& $Adb -s $Serial pull '/sdcard/Android/data/com.acrylic.m71demo.debug/files/evidence' $destination
if ($LASTEXITCODE -ne 0) { throw 'Pull failed; preserve device data and inspect the ADB error.' }
& $Adb -s $Serial shell getprop ro.build.fingerprint | Set-Content -Encoding UTF8 (Join-Path $destination 'device-build.txt')
& $Adb -s $Serial shell dumpsys meminfo com.acrylic.m71demo.debug | Set-Content -Encoding UTF8 (Join-Path $destination 'meminfo.txt')
Write-Output "Evidence copied to $destination"
