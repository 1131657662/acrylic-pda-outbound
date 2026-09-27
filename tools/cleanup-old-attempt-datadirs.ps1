#requires -Version 5.1
<#
.SYNOPSIS
  例行维护：只删除旧 attempt 的 **datadir**（MySQL 数据目录），保留日志/marker/storage/签名材料。

.DESCRIPTION
  背景：每次 e2a / R08 attempt 的 MySQL 数据目录约 0.2-0.25 GB，批量回归会快速累积
  （2026-09-28 实测：501 个 attempt 的 datadir 合计 91.4 GB，一度把 D: 撑到 0 GB，
  导致 MySQL `--initialize` 失败、attempts 416-424 全部 NOT-EXECUTED）。

  本工具按"**保留最近 N 个 attempt 的 datadir**"这一策略清理，**只删 `<attempt>/data`**；
  以下一律保留（它们是证据，不是缓存）：
    - `integration-test.log`、`mysqld.log`、`initialize*.log`（运行与失败记录）
    - `test-started.marker` / `test-completed.marker` / 自定义 marker（发生过什么）
    - `storage/`（渲染产物、快照、文件类 IT 的输出）
    - `private/`（一次性测试签名材料；gitignored，仍保留）
    - `run-password.txt`、`provision.sql`（那次环境是怎么建的）

  只读以外的唯一动作：删除 `<attempt>/data` 目录。不动仓库、不动源码、不动任何 attempt 的非 datadir 内容。

.PARAMETER Local
  attempt 根目录（默认 e2a 守卫星树 `.local`）。

.PARAMETER Keep
  保留最近多少个 attempt 的 datadir（默认 50）。

.PARAMETER DryRun
  只报告将删除什么，不实际删除。

.PARAMETER CleanupAfterRun
  供 harness 调用的模式：清理时不打印完整清单，只回一行汇总。

.EXAMPLE
  powershell -NoProfile -ExecutionPolicy Bypass -File tools/cleanup-old-attempt-datadirs.ps1 -DryRun
.EXAMPLE
  powershell -NoProfile -ExecutionPolicy Bypass -File tools/cleanup-old-attempt-datadirs.ps1 -Keep 50
#>
[CmdletBinding()]
param(
    [string]$Local = 'D:\acrylic-pda-worktrees\r06-e2a-migration\.local',
    [int]$Keep = 50,
    [switch]$DryRun,
    [switch]$CleanupAfterRun
)

$ErrorActionPreference = 'Continue'

function SortKey([string]$name) {
    # e2a-cross-20260925-<n> / r08-it-<n> / e2a-v36*-attempt<n> / mysql19329[-attempt<n>]
    if ($name -match '-(\d+)$') { return [int]$Matches[1] }
    if ($name -match 'attempt(\d+)$') { return [int]$Matches[1] }
    return 0
}

if (-not (Test-Path -LiteralPath $Local)) {
    Write-Host "local root not found: $Local" -ForegroundColor Yellow
    exit 0
}

$attempts = @(
    Get-ChildItem -LiteralPath $Local -Directory -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -like 'e2a-cross-*' -or $_.Name -like 'r08-it-*' -or $_.Name -like 'e2a-v36*' -or $_.Name -like 'mysql19329*' } |
        ForEach-Object { [pscustomobject]@{ Dir = $_; Order = (SortKey $_.Name); Name = $_.Name } }
) | Sort-Object Order, Name

$datadirs = @($attempts | Where-Object { Test-Path -LiteralPath (Join-Path $_.Dir.FullName 'data') })
if ($datadirs.Count -le $Keep) {
    Write-Host ("cleanup: nothing to do (attempts with datadir={0}, keep={1})" -f $datadirs.Count, $Keep)
    exit 0
}

$remove = @($datadirs | Select-Object -First ($datadirs.Count - $Keep))
$keep = @($datadirs | Select-Object -Last $Keep)

$freedBytes = 0; $removed = 0; $failed = 0
foreach ($a in $remove) {
    $dd = Join-Path $a.Dir.FullName 'data'
    $sz = (Get-ChildItem -LiteralPath $dd -Recurse -File -ErrorAction SilentlyContinue | Measure-Object -Property Length -Sum).Sum
    if ($DryRun) {
        Write-Host ("  would remove {0} ({1} GB)" -f $a.Name, [math]::Round($sz / 1GB, 2))
        $freedBytes += $sz; $removed++
        continue
    }
    Remove-Item -LiteralPath $dd -Recurse -Force -ErrorAction SilentlyContinue
    if (Test-Path -LiteralPath $dd) { $failed++; Write-Host "  FAILED to remove $dd" -ForegroundColor Yellow }
    else { $freedBytes += $sz; $removed++ }
}

$verb = if ($DryRun) { 'would free' } else { 'freed' }
if ($CleanupAfterRun) {
    Write-Host ("cleanup: removed {0} datadir(s), {1} {2} GB (kept newest {3})" -f $removed, $verb, [math]::Round($freedBytes / 1GB, 2), $Keep)
} else {
    Write-Host ("cleanup: removed {0} datadir(s) {1} {2} GB; kept newest {3} datadir(s); failed={4}" -f `
        $removed, $verb, [math]::Round($freedBytes / 1GB, 2), $keep.Count, $failed)
    Write-Host "  保留（每 attempt）: integration-test.log / markers / storage/ / private/ / run-password.txt / provision.sql"
    if ($keep.Count -gt 0) {
        Write-Host ("  最新保留区间: {0} .. {1}" -f $keep[0].Name, $keep[-1].Name)
    }
}
