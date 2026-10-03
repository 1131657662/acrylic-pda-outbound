param([Parameter(Mandatory = $true)][ValidateRange(1, 99)][int]$Attempt)

$ErrorActionPreference = 'Stop'
$root = 'D:\acrylic-pda-outbound'
$evidence = Join-Path $root ".local\autonomous-20261003\r07-p08-b-full-regression-$Attempt"

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

function Invoke-Regression {
    $log = Join-Path $evidence 'server-test.log'
    & cmd.exe /c "`"$root\gradlew.bat`" -p server test --no-daemon --rerun-tasks > `"$log`" 2>&1"
    if ($LASTEXITCODE -ne 0) {
        throw "Full server test failed with exit $LASTEXITCODE"
    }
}

function Assert-Reports {
    $reportDirectory = Join-Path $root 'server\build\test-results\test'
    $reports = @(Get-ChildItem -LiteralPath $reportDirectory -Filter 'TEST-*.xml')
    if ($reports.Count -eq 0) {
        throw 'No JUnit reports produced'
    }
    $tests = 0
    $failures = 0
    $errors = 0
    $skipped = 0
    foreach ($report in $reports) {
        Copy-Item -LiteralPath $report.FullName -Destination $evidence
        [xml]$parsed = Get-Content -Raw -LiteralPath $report.FullName
        $tests += [int]$parsed.testsuite.tests
        $failures += [int]$parsed.testsuite.failures
        $errors += [int]$parsed.testsuite.errors
        $skipped += [int]$parsed.testsuite.skipped
    }
    if ($tests -lt 2993 -or $failures -ne 0 -or $errors -ne 0) {
        throw "Unexpected aggregate tests=$tests failures=$failures errors=$errors skipped=$skipped"
    }
    [IO.File]::WriteAllText((Join-Path $evidence 'summary.txt'), "tests=$tests failures=$failures errors=$errors skipped=$skipped")
    Get-ChildItem -LiteralPath $reportDirectory -Filter 'TEST-*.xml' | Select-String -Pattern '<skipped' |
        ForEach-Object { $_.Path + ':' + $_.LineNumber + ':' + $_.Line } |
        Set-Content -LiteralPath (Join-Path $evidence 'skipped.txt') -Encoding utf8
}

Require-Workspace
New-EvidenceDirectory
try {
    Invoke-Regression
    Assert-Reports
} finally {
    [IO.File]::WriteAllText((Join-Path $evidence 'disk-after.txt'), [string](Get-PSDrive -Name D).Free)
}

Write-Output "Evidence=$evidence"
