#requires -Version 5.1
[CmdletBinding()]
param(
    [int]$Run = 857,
    [string]$Workspace = 'D:\acrylic-pda-outbound'
)
$ErrorActionPreference = 'Stop'
$runner = Join-Path $Workspace 'tools/e2a-provision-and-run-106.ps1'
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $runner -Run $Run -Tests '*E2aPhysicalDispositionStoreIT' -Workspace $Workspace -SkipFontPreparation
exit $LASTEXITCODE
