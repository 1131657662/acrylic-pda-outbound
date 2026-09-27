#requires -Version 5.1
<#
.SYNOPSIS
  Batch entry for the one-shot e2a integration tests: one class per attempt, aggregated into one readable report.

.DESCRIPTION
  E2aCrossSourceProfile is one-shot by design: an attempt directory + database is consumed by the first class that
  uses it ("One-shot attempt already used"), so a single gradle integrationTest invocation can never host more than
  one IT class. This script turns that constraint into a usable batch entry:

    for each named class -> allocate its own run number -> run the R02 constructor -> collect that run's TEST-*.xml
    then aggregate every collected XML (never just the first) into one report with failure classification.

  It changes no test harness and no business code, and it runs exactly the classes you name: the point is a
  trustworthy report for those classes, not "run all 173".

  NOTE (encoding): every tools/*.ps1 in this repository is deliberately ASCII-only. Windows PowerShell 5.1 reads a
  .ps1 without a UTF-8 BOM as ANSI, so non-ASCII text here would be mangled and can break parsing.
  NOTE (naming): counters must not be named $tests/$Tests etc. PowerShell variable names are case-insensitive and
  -Tests is a [string] parameter, so such a counter silently degrades to string concatenation (fixed in the
  constructor on 2026-09-27). All counters below use *Count names.

.EXAMPLE
  powershell -NoProfile -ExecutionPolicy Bypass -File tools/e2a-batch-it.ps1 `
      -Classes E2aTemporaryArchiveSwitchIT,E2aTemporaryLinkConfirmIT -StartRun 181
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string[]]$Classes,
    [Parameter(Mandatory=$true)][int]$StartRun,
    [string]$Workspace = 'D:\acrylic-pda-worktrees\r06-e2a-migration',
    # Do NOT default this to (Join-Path $PSScriptRoot ...): under Windows PowerShell 5.1 $PSScriptRoot is empty while
    # parameter defaults are evaluated, so the script dies before running. Resolved below instead.
    [string]$Constructor = '',
    [string]$ReportRoot = 'D:\acrylic-pda-outbound\.local\it-batches',
    [switch]$KeepServerRunning
)

if ([string]::IsNullOrWhiteSpace($Constructor)) {
    $Constructor = Join-Path $PSScriptRoot 'e2a-provision-and-run-106.ps1'
}
if (-not (Test-Path -LiteralPath $Constructor)) { throw "constructor not found: $Constructor" }

# `powershell -File <script> -Classes A,B,C` hands the whole comma list over as ONE string argument, while an
# in-session `& .\script.ps1 -Classes A,B,C` binds it as an array. Split every element on commas so both call
# styles work.
$Classes = @($Classes | ForEach-Object { "$_".Split(',') } |
    ForEach-Object { $_.Trim() } | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
if ($Classes.Count -eq 0) { throw 'no class names given' }
Write-Host "classes ($($Classes.Count)): $($Classes -join ', ')"

$ErrorActionPreference = 'Continue'
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$batchDir = Join-Path $ReportRoot ("batch-$stamp-start$StartRun")
$resultsDir = Join-Path $Workspace 'server/build/test-results/integrationTest'
New-Item -ItemType Directory -Force -Path $batchDir, (Join-Path $batchDir 'xml'), (Join-Path $batchDir 'logs') | Out-Null

function Get-FailureClass([string]$text) {
    if ($text -match 'One-shot attempt already used|Nonempty schema cannot be reseeded|Invalid run allocation|Missing isolated datadir|Datadir mismatch|Attempt escapes workspace|Reset/repair forbidden|Workspace mismatch|Schema mismatch|User mismatch|Port mismatch|Missing private credential') {
        return 'A harness one-shot/structure'
    }
    if ($text -match 'Could not resolve placeholder|Failed to load ApplicationContext|ApplicationContext failure threshold|Communications link failure|Connection refused|Unknown database|Access denied|No suitable driver|Missing mandatory environment|environment variable') {
        return 'B environment not configured/startup'
    }
    if ($text -match 'NumberFormatException') { return 'C known-issue pattern' }
    return 'D real bug (manual triage)'
}

function Test-ClassSourceExists([string]$className) {
    $roots = @((Join-Path $Workspace 'server/src/test/java'), 'D:\acrylic-pda-outbound\server\src\test\java')
    foreach ($root in $roots) {
        if ((Test-Path -LiteralPath $root) -and
            (Get-ChildItem -LiteralPath $root -Recurse -Filter "$className.java" -ErrorAction SilentlyContinue)) {
            return $true
        }
    }
    return $false
}

# ------------------------------------------------------------------ one attempt per class
$runRows = New-Object System.Collections.Generic.List[object]
$runNumber = $StartRun
foreach ($className in $Classes) {
    $className = "$className".Trim()
    if ([string]::IsNullOrWhiteSpace($className)) { continue }
    Write-Host "=== run $runNumber : $className ==="
    if (-not (Test-ClassSourceExists $className)) {
        Write-Host "WARN: $className.java not found in the test sources"
    }
    $log = Join-Path $batchDir ("logs/run-$runNumber-$className.log")
    $started = Get-Date
    $constructorArgs = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $Constructor,
        '-Run', "$runNumber", '-Tests', "*$className", '-Workspace', $Workspace)
    if ($KeepServerRunning) { $constructorArgs += '-KeepServerRunning' }
    & powershell @constructorArgs 2>&1 | Tee-Object -FilePath $log |
        Select-String -Pattern 'RESULT|markers|\[pass\]|\[FAIL\]|\[ERROR\]|\[SKIP\]|teardown' |
        ForEach-Object { Write-Host "  $($_.Line)" }
    $exitCode = $LASTEXITCODE
    $attemptDir = Join-Path $Workspace ".local/e2a-cross-20260925-$runNumber"
    $xmlDir = Join-Path $batchDir "xml/run-$runNumber-$className"
    New-Item -ItemType Directory -Force -Path $xmlDir | Out-Null
    Get-ChildItem $resultsDir -Filter 'TEST-*.xml' -ErrorAction SilentlyContinue |
        Where-Object { $_.LastWriteTime -ge $started } |
        ForEach-Object { Copy-Item $_.FullName $xmlDir -Force }
    $collected = @(Get-ChildItem $xmlDir -Filter 'TEST-*.xml' -ErrorAction SilentlyContinue)
    $runRows.Add([pscustomobject]@{
        Run = $runNumber; Class = $className; Exit = $exitCode; Attempt = $attemptDir
        XmlDir = $xmlDir; XmlCount = $collected.Count; Log = $log })
    $runNumber++
}

# ------------------------------------------------------------------ aggregate every collected XML
$report = New-Object System.Collections.Generic.List[string]
$totalTests = 0; $totalFailures = 0; $totalErrors = 0; $totalSkipped = 0
$failureRows = New-Object System.Collections.Generic.List[object]
$classRows = New-Object System.Collections.Generic.List[object]

foreach ($row in $runRows) {
    $classTests = 0; $classFailures = 0; $classErrors = 0; $classSkipped = 0
    $xmlFiles = @(Get-ChildItem $row.XmlDir -Filter 'TEST-*.xml' -ErrorAction SilentlyContinue)
    foreach ($file in $xmlFiles) {
        $doc = [xml][System.IO.File]::ReadAllText($file.FullName, (New-Object System.Text.UTF8Encoding($false)))
        foreach ($suite in @($doc.testsuite)) {
            $classTests += [int]$suite.tests; $classFailures += [int]$suite.failures
            $classErrors += [int]$suite.errors; $classSkipped += [int]$suite.skipped
            foreach ($case in @($suite.testcase)) {
                if ($case.failure -or $case.error) {
                    $detail = if ($case.failure) { $case.failure } else { $case.error }
                    $message = "$($detail.message)".Trim()
                    $stack = "$($detail.'#text')"
                    $where = (($stack -split "`n") | Where-Object { $_ -match '\.java:\d+' } | Select-Object -First 1)
                    $failureRows.Add([pscustomobject]@{
                        Run = $row.Run; Suite = "$($suite.name)"; Case = "$($case.name)"
                        Message = ($message -split "`n")[0]
                        Where = if ($where) { "$where".Trim() } else { '' }
                        Class = (Get-FailureClass "$message`n$stack") })
                }
            }
        }
    }
    if ($xmlFiles.Count -eq 0) {
        $failureRows.Add([pscustomobject]@{
            Run = $row.Run; Suite = $row.Class; Case = '(no XML produced)'
            Message = "this run produced no TEST-*.xml (constructor exit=$($row.Exit)); inspect $($row.Log)"
            Where = ''; Class = 'E not executed / no XML (entry layer)' })
    }
    $totalTests += $classTests; $totalFailures += $classFailures
    $totalErrors += $classErrors; $totalSkipped += $classSkipped
    $state = 'pass'
    if ($classErrors -gt 0) { $state = 'ERROR' }
    elseif ($classFailures -gt 0) { $state = 'FAIL' }
    elseif ($xmlFiles.Count -eq 0) { $state = 'NOT-EXECUTED' }
    elseif ($classTests -eq 0) { $state = 'NO-TESTS' }
    $classRows.Add([pscustomobject]@{
        Run = $row.Run; Class = $row.Class; ConstructorExit = $row.Exit
        XmlFiles = $xmlFiles.Count; Tests = $classTests; Failures = $classFailures
        Errors = $classErrors; Skipped = $classSkipped; State = $state })
}

# ------------------------------------------------------------------ report
$report.Add("# IT batch report ($stamp, runs $StartRun..$($runNumber - 1))")
$report.Add('')
$report.Add("- constructor: ``$Constructor``")
$report.Add("- workspace: ``$Workspace``")
$report.Add("- one attempt per class (own run number/DB/directory), so the one-shot guard is satisfied; all collected XMLs are aggregated.")
$report.Add('')
$report.Add('## Per-class result')
$report.Add('')
$report.Add('| run | class | xml | tests | failures | errors | skipped | state |')
$report.Add('| --- | --- | --- | --- | --- | --- | --- | --- |')
foreach ($c in $classRows) {
    $report.Add("| $($c.Run) | $($c.Class) | $($c.XmlFiles) | $($c.Tests) | $($c.Failures) | $($c.Errors) | $($c.Skipped) | **$($c.State)** |")
}
$report.Add('')
$entryIssues = @($failureRows | Where-Object { "$($_.Class)" -like 'E *' }).Count
$report.Add("**TOTAL: classes=$($classRows.Count) tests=$totalTests failures=$totalFailures errors=$totalErrors skipped=$totalSkipped entryIssues=$entryIssues**")
$report.Add('')
$report.Add('## Failures and classification')
$report.Add('')
if ($failureRows.Count -eq 0) {
    $report.Add('No failures.')
} else {
    $report.Add('| run | class.case | classification | message | location |')
    $report.Add('| --- | --- | --- | --- | --- |')
    foreach ($f in $failureRows) {
        $msg = $f.Message -replace '\|', '/' -replace "`r?`n", ' '
        $report.Add("| $($f.Run) | $($f.Suite).$($f.Case) | $($f.Class) | $msg | $($f.Where) |")
    }
    $report.Add('')
    $report.Add('### Classification counts')
    $report.Add('')
    foreach ($group in ($failureRows | Group-Object Class | Sort-Object Name)) {
        $report.Add("- **$($group.Name)**: $($group.Count)")
    }
}
$report.Add('')
$report.Add('## Evidence paths')
$report.Add('')
foreach ($row in $runRows) {
    $report.Add("- run $($row.Run) $($row.Class): attempt ``$($row.Attempt)``; xml ``$($row.XmlDir)``; log ``$($row.Log)``")
}
$reportPath = Join-Path $batchDir 'REPORT.md'
[System.IO.File]::WriteAllLines($reportPath, $report, (New-Object System.Text.UTF8Encoding($false)))

Write-Host ''
Write-Host "TOTAL classes=$($classRows.Count) tests=$totalTests failures=$totalFailures errors=$totalErrors skipped=$totalSkipped entryIssues=$entryIssues"
$classRows | Format-Table -AutoSize | Out-String -Width 160 | Write-Host
Write-Host "report: $reportPath"
if ($failureRows.Count -gt 0) { exit 1 }
exit 0
