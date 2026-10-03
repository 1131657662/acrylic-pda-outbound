param([Parameter(Mandatory = $true)][ValidateRange(1, 99)][int]$Attempt)

$ErrorActionPreference = 'Stop'
$root = 'D:\acrylic-pda-outbound'
$evidence = Join-Path $root ".local\autonomous-20261003\r07-p08-b-targeted-$Attempt"
$tests = @(
    'com.acrylic.outbound.correction.CorrectionOccupancyRestorationTest',
    'com.acrylic.outbound.correction.CorrectionCommandConfigurationTest',
    'com.acrylic.outbound.correction.CorrectionAttachmentConfigurationTest',
    'com.acrylic.outbound.correction.CorrectionCycleReopeningTest',
    'com.acrylic.outbound.correction.CorrectionStoreCycleReopeningTest'
)

function Require-Workspace {
    $resolvedRoot = (Resolve-Path -LiteralPath $root).Path
    if ((Get-Location).Path -ne $resolvedRoot) {
        throw 'Unexpected workspace'
    }
    if ((Get-PSDrive -Name D).Free -lt 20GB) {
        throw 'D: free space is below the 20 GB stop threshold'
    }
}

function New-EvidenceDirectory {
    if (Test-Path -LiteralPath $evidence) {
        throw 'Attempt evidence already exists and will be preserved'
    }
    New-Item -ItemType Directory -Path $evidence | Out-Null
    [IO.File]::WriteAllText((Join-Path $evidence 'disk-before.txt'), [string](Get-PSDrive -Name D).Free)
}

function Invoke-TargetedTests {
    $arguments = @('-p', 'server', 'test', '--no-daemon', '--rerun-tasks')
    foreach ($test in $tests) {
        $arguments += '--tests'
        $arguments += $test
    }
    $log = Join-Path $evidence 'targeted.log'
    & cmd.exe /c "`"$root\gradlew.bat`" $($arguments -join ' ') > `"$log`" 2>&1"
    if ($LASTEXITCODE -ne 0) {
        throw "Targeted Gradle test failed with exit $LASTEXITCODE"
    }
}

function Assert-Reports {
    $reports = @()
    foreach ($test in $tests) {
        $report = Join-Path $root "server\build\test-results\test\TEST-$test.xml"
        if (-not (Test-Path -LiteralPath $report)) {
            throw "Missing JUnit report for $test"
        }
        Copy-Item -LiteralPath $report -Destination $evidence
        [xml]$parsed = Get-Content -Raw -LiteralPath $report
        if ([int]$parsed.testsuite.tests -lt 1 -or [int]$parsed.testsuite.failures -ne 0 -or
            [int]$parsed.testsuite.errors -ne 0 -or [int]$parsed.testsuite.skipped -ne 0) {
            throw "JUnit report is not clean for $test"
        }
        $reports += $parsed.testsuite
    }
    $total = ($reports | Measure-Object -Property tests -Sum).Sum
    [IO.File]::WriteAllText((Join-Path $evidence 'summary.txt'), "tests=$total failures=0 errors=0 skipped=0")
}

Require-Workspace
New-EvidenceDirectory
try {
    Invoke-TargetedTests
    Assert-Reports
} finally {
    [IO.File]::WriteAllText((Join-Path $evidence 'disk-after.txt'), [string](Get-PSDrive -Name D).Free)
}

Write-Output "Evidence=$evidence"
