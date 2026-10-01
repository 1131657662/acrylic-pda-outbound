$ErrorActionPreference = 'Stop'
$root = 'D:\acrylic-pda-outbound'
$evidence = Join-Path $root '.local\autonomous-20261001\p08-a-805'
$log = Join-Path $evidence 'server-test-regression.log'
$command = 'gradlew.bat -p server test --no-daemon --console=plain > "' + $log + '" 2>&1'
Push-Location $root
try {
    & cmd.exe /c $command
    $exitCode = $LASTEXITCODE
} finally {
    Pop-Location
}
$reports = @(Get-ChildItem -LiteralPath (Join-Path $root 'server\build\test-results\test') -Filter 'TEST-*.xml')
$total = 0
$failures = 0
$errors = 0
$skipped = 0
foreach ($report in $reports) {
    [xml]$xml = Get-Content -Raw -LiteralPath $report.FullName
    $suite = $xml.testsuite
    $total += [int]$suite.tests
    $failures += [int]$suite.failures
    $errors += [int]$suite.errors
    $skipped += [int]$suite.skipped
}
$summary = "reports=$($reports.Count) tests=$total failures=$failures errors=$errors skipped=$skipped exit=$exitCode"
Set-Content -LiteralPath (Join-Path $evidence 'server-test-regression-summary.txt') -Value $summary -Encoding utf8
if ($exitCode -ne 0 -or $failures -ne 0 -or $errors -ne 0) {
    Get-Content -Tail 160 -LiteralPath $log
    throw "server test regression failed: $summary"
}
Write-Output $summary
