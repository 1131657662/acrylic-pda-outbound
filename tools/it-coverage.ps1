#requires -Version 5.1
<#
.SYNOPSIS
  汇总"哪些 IT 已经在哪些 attempt 上跑过、结果如何"——从 JUnit XML 反推，取代人工统计。

.DESCRIPTION
  验证历史散落在两处 XML：
    - 批量入口产物：`<ReportRoot>/<batch>/xml/run-<attempt>-<Class>/TEST-*.xml`（每类一目录）；
    - 最近一次运行产物：`<Workspace>/server/build/test-results/integrationTest/TEST-*.xml`。
  attempt 目录里的 integration-test.log **不保留** RESULT/[pass] 行（Tee 流式写入时丢失），
  所以本工具只认 XML（tests/failures/errors/skipped + 套件名），并打印 attempt markers 作为旁证。

  只读：不写 attempt 目录、不改仓库状态。失败条目照原样列出（不隐藏）。

.PARAMETER Workspace
  运行树（默认 e2a 守卫星树），用于取最近一次 XML 与 attempt markers。

.PARAMETER ReportRoot
  批量报告根（默认主树 `.local/it-batches`）。

.PARAMETER Csv
  可选：把汇总写成 CSV。

.EXAMPLE
  powershell -NoProfile -ExecutionPolicy Bypass -File tools/it-coverage.ps1
#>
[CmdletBinding()]
param(
    [string]$Workspace = 'D:\acrylic-pda-worktrees\r06-e2a-migration',
    [string]$ReportRoot = 'D:\acrylic-pda-outbound\.local\it-batches',
    [string]$Csv = ''
)

$ErrorActionPreference = 'Continue'
$utf8 = New-Object System.Text.UTF8Encoding($false)
$rows = New-Object System.Collections.Generic.List[object]

function Add-Xml([string]$path, [string]$attempt, [string]$source) {
    try { $doc = [xml][System.IO.File]::ReadAllText($path, $utf8) } catch { return }
    foreach ($suite in @($doc.testsuite)) {
        if ($null -eq $suite) { continue }
        $tests = [int]$suite.tests; $f = [int]$suite.failures; $e = [int]$suite.errors; $s = [int]$suite.skipped
        $state = if ($f + $e -gt 0) { 'FAIL' } elseif ($tests -eq 0) { 'NO-TESTS' } elseif ($s -eq $tests) { 'SKIP' } else { 'pass' }
        $rows.Add([pscustomobject]@{
            Attempt = $attempt; Class = [string]$suite.name; Tests = $tests
            Failures = $f; Errors = $e; Skipped = $s; State = $state; Source = $source
        })
    }
}

if (Test-Path -LiteralPath $ReportRoot) {
    foreach ($batch in Get-ChildItem -LiteralPath $ReportRoot -Directory -ErrorAction SilentlyContinue) {
        $xmlRoot = Join-Path $batch.FullName 'xml'
        if (-not (Test-Path -LiteralPath $xmlRoot)) { continue }
        foreach ($runDir in Get-ChildItem -LiteralPath $xmlRoot -Directory -ErrorAction SilentlyContinue) {
            $attempt = ''
            if ($runDir.Name -match '^run-(\d+)-') { $attempt = $Matches[1] }
            foreach ($x in Get-ChildItem -LiteralPath $runDir.FullName -Filter 'TEST-*.xml' -ErrorAction SilentlyContinue) {
                Add-Xml $x.FullName $attempt $batch.Name
            }
        }
    }
}

$latest = Join-Path $Workspace 'server\build\test-results\integrationTest'
if (Test-Path -LiteralPath $latest) {
    foreach ($x in Get-ChildItem -LiteralPath $latest -Filter 'TEST-*.xml' -ErrorAction SilentlyContinue) {
        Add-Xml $x.FullName '(latest)' 'build/test-results'
    }
}

if ($rows.Count -eq 0) { Write-Host "no JUnit XML found under $ReportRoot or $latest (run a batch first)"; exit 1 }

$byClass = $rows | Group-Object Class | Sort-Object Name
Write-Host "== XML suites: $($rows.Count) ; distinct IT classes: $($byClass.Count) =="
$byClass = $rows | Group-Object Class | Sort-Object Name
Write-Host "== XML suites: $($rows.Count) ; distinct IT classes: $($byClass.Count) =="
function Latest-Of($group) {
    $group | Sort-Object { if ($_.Attempt -eq '(latest)') { [int]::MaxValue } elseif ($_.Attempt -match '^\d+$') { [int]$_.Attempt } else { -1 } } | Select-Object -Last 1
}
foreach ($g in $byClass) {
    $newest = Latest-Of $g.Group
    $reds = @($g.Group | Where-Object { $_.State -in @('FAIL', 'ERROR') }).Count
    $tests = ($g.Group | Measure-Object -Property Tests -Sum).Sum
    $flag = if ($newest.State -in @('FAIL', 'ERROR')) { "LATEST-RED(attempt $($newest.Attempt))" }
            elseif ($newest.State -eq 'SKIP') { 'skip(env guard?)' }
            elseif ($newest.State -eq 'NO-TESTS') { 'no-tests' } else { 'pass' }
    $hist = if ($reds -gt 0) { " [历史红 $reds 次, 已复跑]" } else { '' }
    Write-Host ("  {0,-60} suites={1,-3} tests={2,-4} {3}{4}" -f $g.Name, $g.Group.Count, $tests, $flag, $hist)
}
$bad = $rows | Where-Object { $_.State -in @('FAIL', 'ERROR') }
Write-Host "== 历史红条目: $($bad.Count)（逐条保留，不隐藏；当前判定看上表 LATEST）=="
foreach ($b in $bad) { Write-Host ("  [{0}] attempt={1} {2} tests={3} failures={4} errors={5}" -f $b.State, $b.Attempt, $b.Class, $b.Tests, $b.Failures, $b.Errors) }
$latestRed = @()
foreach ($g in $byClass) { $n = Latest-Of $g.Group; if ($n.State -in @('FAIL', 'ERROR')) { $latestRed += "$($g.Name) @ attempt $($n.Attempt)" } }
Write-Host "== 当前仍为红（最新一次即红）: $($latestRed.Count) =="
foreach ($l in $latestRed) { Write-Host "  $l" }
Write-Host @"
== 环境守卫族（只在专用库/账号下才会真的执行；见 TEST_INFRA_SELFCHECK_2026-09-28.md）==
  R08 族           23308 / acrylic_r08_it (+ _preview)；渲染需 OUTBOUND_EXPORTS_RENDERER_HOME/_BROWSERS
  IdentityLock 族  19321 / acrylic_outbound_l2_20260921 且账号同名
  ledgerverify 族  ledgerverify 专用库
"@

if (-not [string]::IsNullOrWhiteSpace($Csv)) {
    $rows | Export-Csv -LiteralPath $Csv -NoTypeInformation -Encoding UTF8
    Write-Host "csv written: $Csv"
}
