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

  Counters here must not be named $tests/$Tests etc.: PowerShell variable names are case-insensitive and -Tests is a
  [string] parameter, so such a counter silently degrades to string concatenation (the bug fixed in the constructor
  on 2026-09-27). All counters below use *Count names that collide with nothing.

.EXAMPLE
  powershell -NoProfile -ExecutionPolicy Bypass -File tools/e2a-batch-it.ps1 `
      -Classes E2aTemporaryArchiveSwitchIT,E2aTemporaryLinkConfirmIT -StartRun 181
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string[]]$Classes,
    [Parameter(Mandatory=$true)][int]$StartRun,
    [string]$Workspace = 'D:\acrylic-pda-worktrees\r06-e2a-migration',
    [string]$Constructor = (Join-Path $PSScriptRoot 'e2a-provision-and-run-106.ps1'),
    [string]$ReportRoot = 'D:\acrylic-pda-outbound\.local\it-batches',
    [switch]$KeepServerRunning
)

$ErrorActionPreference = 'Continue'
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$batchDir = Join-Path $ReportRoot ("batch-$stamp-start$StartRun")
$resultsDir = Join-Path $Workspace 'server/build/test-results/integrationTest'
New-Item -ItemType Directory -Force -Path $batchDir, (Join-Path $batchDir 'xml'), (Join-Path $batchDir 'logs') | Out-Null

function Get-FailureClass([string]$text) {
    if ($text -match 'One-shot attempt already used|Nonempty schema cannot be reseeded|Invalid run allocation|Missing isolated datadir|Datadir mismatch|Attempt escapes workspace|Reset/repair forbidden|Workspace mismatch|Schema mismatch|User mismatch|Port mismatch|Missing private credential') {
        return 'A 夹具 one-shot/结构'
    }
    if ($text -match 'Could not resolve placeholder|Failed to load ApplicationContext|ApplicationContext failure threshold|Communications link failure|Connection refused|Unknown database|Access denied|No suitable driver|Missing mandatory environment|environment variable') {
        return 'B 环境未配置/启动失败'
    }
    if ($text -match 'NumberFormatException') { return 'C 已知问题模式（历史 flake 类）' }
    return 'D 真 bug（待人工判定）'
}

function Test-ClassSourceExists([string]$class) {
    foreach ($root in @((Join-Path $Workspace 'server/src/test/java'), 'D:\acrylic-pda-outbound\server\src\test\java')) {
        if ((Test-Path -LiteralPath $root) -and
            (Get-ChildItem -LiteralPath $root -Recurse -Filter "$class.java" -ErrorAction SilentlyContinue)) { return $true }
    }
    return $false
}

# ------------------------------------------------------------------ run one attempt per class
$runRows = New-Object System.Collections.Generic.List[object]
$runNumber = $StartRun
foreach ($class in $Classes) {
    $class = "$class".Trim()
    if ([string]::IsNullOrWhiteSpace($class)) { continue }
    Write-Host "=== run $runNumber : $class ===" -ForegroundColor Cyan
    if (-not (Test-ClassSourceExists $class)) { Write-Host "WARN: $class.java not found in the test sources" -ForegroundColor Yellow }
    $log = Join-Path $batchDir ("logs/run-$runNumber-$class.log")
    $started = Get-Date
    $constructorArgs = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $Constructor,
        '-Run', "$runNumber", '-Tests', "*$class", '-Workspace', $Workspace)
    if ($KeepServerRunning) { $constructorArgs += '-KeepServerRunning' }
    & powershell @constructorArgs 2>&1 | Tee-Object -FilePath $log |
        Select-String -Pattern 'RESULT|markers|\[pass\]|\[FAIL\]|\[ERROR\]|\[SKIP\]|teardown' |
        ForEach-Object { Write-Host "  $($_.Line)" }
    $exitCode = $LASTEXITCODE
    $attemptDir = Join-Path $Workspace ".local/e2a-cross-20260925-$runNumber"
    $xmlDir = Join-Path $batchDir "xml/run-$runNumber-$class"
    New-Item -ItemType Directory -Force -Path $xmlDir | Out-Null
    Get-ChildItem $resultsDir -Filter 'TEST-*.xml' -ErrorAction SilentlyContinue |
        Where-Object { $_.LastWriteTime -ge $started } |
        ForEach-Object { Copy-Item $_.FullName $xmlDir -Force }
    $collected = @(Get-ChildItem $xmlDir -Filter 'TEST-*.xml' -ErrorAction SilentlyContinue)
    $runRows.Add([pscustomobject]@{
        Run = $runNumber; Class = $class; Exit = $exitCode; Attempt = $attemptDir
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
                    $where = (("$($detail.'#text')" -split "`n") | Where-Object { $_ -match '\.java:\d+' } | Select-Object -First 1)
                    $failureRows.Add([pscustomobject]@{
                        Run = $row.Run; Suite = "$($suite.name)"; Case = "$($case.name)"
                        Message = ($message -split "`n")[0]
                        Where = if ($where) { "$where".Trim() } else { '' }
                        Class = (Get-FailureClass "$message`n$($detail.'#text')") })
                }
            }
        }
    }
    if ($xmlFiles.Count -eq 0) {
        $failureRows.Add([pscustomobject]@{
            Run = $row.Run; Suite = $row.Class; Case = '(no XML produced)'
            Message = "this run produced no TEST-*.xml (constructor exit=$($row.Exit)); inspect $($row.Log)"
            Where = ''; Class = 'E 未执行/未产出 XML（入口层）' })
    }
    $totalTests += $classTests; $totalFailures += $classFailures
    $totalErrors += $classErrors; $totalSkipped += $classSkipped
    $classRows.Add([pscustomobject]@{
        Run = $row.Run; Class = $row.Class; ConstructorExit = $row.Exit
        XmlFiles = $xmlFiles.Count; Tests = $classTests; Failures = $classFailures
        Errors = $classErrors; Skipped = $classSkipped
        State = if ($classErrors -gt 0) { 'ERROR' } elseif ($classFailures -gt 0) { 'FAIL' }
                 elseif ($xmlFiles.Count -eq 0) { 'NOT-EXECUTED' } elseif ($classTests -eq 0) { 'NO-TESTS' } else { 'pass' } })
}

# ------------------------------------------------------------------ report
$report.Add("# IT 分批报告（$stamp，runs $StartRun..$($runNumber - 1)）")
$report.Add('')
$report.Add("- 构造器：``$Constructor``")
$report.Add("- 工作树：``$Workspace``")
$report.Add("- 每类一个 attempt（独立 run 号/DB/目录），故 one-shot 守卫满足；XML 全部收集后聚合。")
$report.Add('')
$report.Add('## 逐类结果')
$report.Add('')
$report.Add('| run | class | xml | tests | failures | errors | skipped | state |')
$report.Add('| --- | --- | --- | --- | --- | --- | --- | --- |')
foreach ($c in $classRows) {
    $report.Add("| $($c.Run) | $($c.Class) | $($c.XmlFiles) | $($c.Tests) | $($c.Failures) | $($c.Errors) | $($c.Skipped) | **$($c.State)** |")
}
$report.Add('')
$report.Add("**合计：classes=$($classRows.Count) tests=$totalTests failures=$totalFailures errors=$totalErrors skipped=$totalSkipped**")
$report.Add('')
$report.Add('## 失败清单与分类')
$report.Add('')
if ($failureRows.Count -eq 0) {
    $report.Add('无失败。')
} else {
    $report.Add('| run | class/case | 分类 | 首行信息 | 位置 |')
    $report.Add('| --- | --- | --- | --- | --- |')
    foreach ($f in $failureRows) {
        $msg = $f.Message -replace '\|', '/' -replace "`r?`n", ' '
        $report.Add("| $($f.Run) | $($f.Suite).$($f.Case) | $($f.Class) | $msg | $($f.Where) |")
    }
    $report.Add('')
    $report.Add('### 分类统计')
    $report.Add('')
    foreach ($group in ($failureRows | Group-Object Class | Sort-Object Name)) {
        $report.Add("- **$($group.Name)**：$($group.Count) 项")
    }
}
$report.Add('')
$report.Add('## 证据路径')
$report.Add('')
foreach ($row in $runRows) {
    $report.Add("- run $($row.Run) $($row.Class)：attempt ``$($row.Attempt)``；XML ``$($row.XmlDir)``；日志 ``$($row.Log)``")
}
$reportPath = Join-Path $batchDir 'REPORT.md'
[System.IO.File]::WriteAllLines($reportPath, $report, (New-Object System.Text.UTF8Encoding($false)))

Write-Host ''
Write-Host "TOTAL classes=$($classRows.Count) tests=$totalTests failures=$totalFailures errors=$totalErrors skipped=$totalSkipped"
$classRows | Format-Table -AutoSize | Out-String -Width 160 | Write-Host
Write-Host "report: $reportPath"
if ($failureRows.Count -gt 0) { exit 1 }
exit 0
