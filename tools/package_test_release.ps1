param(
    [Parameter(Mandatory)][ValidatePattern('^[a-zA-Z0-9._-]+$')][string]$Release,
    [Parameter(Mandatory)][string]$BrowserRoot,
    [Parameter(Mandatory)][string]$BuildRecord,
    [string]$RendererRoot,
    [string]$OutputRoot,
    [string]$Python = 'python'
)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
if (-not $RendererRoot) { $RendererRoot = Join-Path $root 'report-rendering/build/install/acrylic-report-rendering' }
if (-not $OutputRoot) { $OutputRoot = Join-Path $root 'outputs/r02' }
& $Python (Join-Path $PSScriptRoot 'package_test_release.py') --root $root --release $Release --renderer $RendererRoot --browsers $BrowserRoot --output $OutputRoot --build-record $BuildRecord
if ($LASTEXITCODE -ne 0) { throw 'Immutable backend package generation failed; retain failed output for diagnosis.' }
