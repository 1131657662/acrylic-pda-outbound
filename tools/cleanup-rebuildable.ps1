$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$rebuildable = @(
    (Join-Path $root 'server/build'),
    (Join-Path $root 'report-rendering/build'),
    (Join-Path $root 'web/test-results')
)

foreach ($path in $rebuildable) {
    if (Test-Path -LiteralPath $path) {
        Remove-Item -LiteralPath $path -Recurse -Force
    }
}

$apkDir = Join-Path $root 'outputs/pda'
if (Test-Path -LiteralPath $apkDir) {
    Get-ChildItem -LiteralPath $apkDir -Filter '*.apk' -File |
        Where-Object { $_.Name -ne 'Acrylic-PDA-0.16.0-r06-release.apk' } |
        Remove-Item -Force
}

Write-Output 'Removed rebuildable build/test output and superseded APKs.'
