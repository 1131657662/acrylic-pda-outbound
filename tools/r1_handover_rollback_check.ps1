#requires -Version 5.1
[CmdletBinding()]
param(
    [switch]$UnitOnly
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$gradle = Join-Path $root 'gradlew.bat'
$tests = @(
    'com.acrylic.outbound.recovery.RecoveryLegacyPathIsolationTest',
    'com.acrylic.outbound.e2averify.HandoverRollbackFactsTest'
)
if (-not $UnitOnly) { $tests += 'com.acrylic.outbound.e2averify.E2aHandoverRollbackIT' }

$arguments = @('-p', (Join-Path $root 'server'), 'test')
foreach ($test in $tests) { $arguments += @('--tests', $test) }
& $gradle @arguments
if ($LASTEXITCODE -ne 0) { throw "R1 verification failed with exit code $LASTEXITCODE" }
