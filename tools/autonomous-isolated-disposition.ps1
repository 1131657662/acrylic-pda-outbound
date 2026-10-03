param(
    [Parameter(Mandatory=$true)][string]$OfflineEvidence,
    [ValidateSet('E2aPhysicalDispositionStoreIT','E2aPhysicalDispositionWebHttpIT','E2aTemporaryCloseDispositionIT')]
    [string]$Test = 'E2aPhysicalDispositionStoreIT'
)
$ErrorActionPreference = 'Stop'
$workspace = Split-Path -Parent $PSScriptRoot
Set-Location $workspace
$offline = Get-Content (Join-Path $OfflineEvidence 'result.json') -Raw | ConvertFrom-Json
if ($offline.exit -ne 0 -or $offline.failures -ne 0 -or $offline.errors -ne 0 -or $offline.tests -lt 2900) {
    throw 'Full offline regression must pass first.'
}
if ((Get-PSDrive D).Free -lt 20GB) { throw 'Disk below threshold.' }
if (@(Get-NetTCPConnection -State Listen | Where-Object LocalPort -in @(19330,19434)).Count) { throw 'Ports occupied.' }
if (Get-CimInstance Win32_Process -Filter "Name='java.exe'" |
    Where-Object { $_.CommandLine -match 'GradleWrapperMain|Gradle Test Executor' }) { throw 'Gradle already active.' }
$numbers = foreach ($tree in (git worktree list --porcelain | Where-Object { $_ -like 'worktree *' })) {
    Get-ChildItem (Join-Path $tree.Substring(9) '.local') -Directory -Filter 'e2a-cross-20260925-*' -ErrorAction SilentlyContinue |
        ForEach-Object { if ($_.Name -match '^e2a-cross-20260925-(\d+)$') { [int]$Matches[1] } }
}
$run = 1 + ($numbers | Measure-Object -Maximum).Maximum
if ($run -ge 1000) { throw 'Run exceeds existing profile range.' }
$before = Join-Path $workspace ('.local/autonomous-20261003/disposition-before-' + $run)
$attempt = Join-Path $workspace ('.local/e2a-cross-20260925-' + $run)
New-Item -ItemType Directory -Path (Join-Path $before 'before') | Out-Null
@{nextRun=$run;offline=$OfflineEvidence;test=$Test} | ConvertTo-Json | Set-Content (Join-Path $before 'before/allocation.json')
Copy-Item -LiteralPath '.local/autonomous-20261001/handover-list/provision.ps1' -Destination (Join-Path $before 'provision.ps1')
& pwsh -NoProfile -File (Join-Path $before 'provision.ps1') -Run $run
if ($LASTEXITCODE -ne 0) { throw 'Provision failed; preserve attempt.' }
$result = @{run=$run;test=$Test;exit=$null;tests=0;failures=0;errors=0;skipped=0;complete=$false}
try {
    $env:E2A_CROSS_VERIFY = 'true'; $env:E2A_CROSS_ROOT = $workspace; $env:E2A_CROSS_RUN = [string]$run
    $env:E2A_CROSS_PASSWORD = (Get-Content -LiteralPath (Join-Path $attempt 'run-password.txt') -Raw).Trim()
    $env:OUTBOUND_DB_RESET = 'false'; $env:OUTBOUND_DB_REPAIR = 'false'; $env:E2A_MIGRATION_RESET = 'false'
    $env:OUTBOUND_COOKIE_SECURE = 'false'
    $started = Get-Date
    $testArguments = @('-p','server','integrationTest','--tests',('*' + $Test),'--offline','--rerun-tasks','--console=plain','-x',':report-rendering:prepareFont')
    $ErrorActionPreference = 'Continue'
    & ./gradlew.bat @testArguments *> (Join-Path $attempt 'integration-test.log')
    $result.exit = $LASTEXITCODE
    $ErrorActionPreference = 'Stop'
    $xmlPath = 'server/build/test-results/integrationTest/TEST-com.acrylic.outbound.e2averify.' + $Test + '.xml'
    if ((Test-Path -LiteralPath $xmlPath) -and (Get-Item -LiteralPath $xmlPath).LastWriteTime -ge $started) {
        Copy-Item -LiteralPath $xmlPath -Destination (Join-Path $attempt 'integration-result.xml')
        $suite = ([xml](Get-Content -LiteralPath $xmlPath -Raw)).testsuite
        foreach ($field in @('tests','failures','errors','skipped')) { $result[$field] = [int]$suite.$field }
    }
    $result.complete = $result.exit -eq 0 -and $result.tests -eq 1 -and $result.failures -eq 0 -and $result.errors -eq 0 -and $result.skipped -eq 0
} finally {
    $result | ConvertTo-Json | Set-Content (Join-Path $attempt 'result.json')
    & pwsh -NoProfile -File .local/autonomous-20261001/handover-sql-rollback/preserve-stop.ps1 -Run $run
    if ($LASTEXITCODE -ne 0) { throw 'Dump/shutdown failed.' }
}
$result | ConvertTo-Json | Write-Host
if (-not $result.complete) { exit 1 }
