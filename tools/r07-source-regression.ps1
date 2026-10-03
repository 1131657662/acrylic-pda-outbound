param(
    [Parameter(Mandatory=$true)][string]$EvidenceName,
    [switch]$Full
)
$ErrorActionPreference = 'Stop'
$workspace = Split-Path -Parent $PSScriptRoot
Set-Location $workspace
$evidence = Join-Path $workspace ('.local/autonomous-20261003/' + $EvidenceName)
if (Test-Path -LiteralPath $evidence) { throw 'Fresh evidence directory required.' }
if ((Get-PSDrive D).Free -lt 20GB) { throw 'Disk below threshold.' }
if (Get-CimInstance Win32_Process -Filter "Name='java.exe'" |
    Where-Object { $_.CommandLine -match 'GradleWrapperMain|Gradle Test Executor' }) { throw 'Gradle already active.' }
New-Item -ItemType Directory -Path $evidence | Out-Null
git rev-parse HEAD | Set-Content (Join-Path $evidence 'head.txt')
git status --short | Set-Content (Join-Path $evidence 'worktree.txt')
$arguments = @('-p','server','test','--offline','--rerun-tasks','--console=plain','-x',':report-rendering:prepareFont')
if (-not $Full) { $arguments += @('--tests','com.acrylic.outbound.handover.*') }
$started = Get-Date
$ErrorActionPreference = 'Continue'
& ./gradlew.bat @arguments *> (Join-Path $evidence 'gradle.log')
$code = $LASTEXITCODE
$ErrorActionPreference = 'Stop'
$result = @{exit=$code;tests=0;failures=0;errors=0;skipped=0}
$reports = Get-ChildItem 'server/build/test-results/test' -Filter 'TEST-*.xml' |
    Where-Object { $_.LastWriteTime -ge $started }
foreach ($report in $reports) {
    Copy-Item -LiteralPath $report.FullName -Destination $evidence
    $suite = ([xml](Get-Content -LiteralPath $report.FullName -Raw)).testsuite
    foreach ($field in @('tests','failures','errors','skipped')) { $result[$field] += [int]$suite.$field }
}
$result | ConvertTo-Json | Set-Content (Join-Path $evidence 'result.json')
$result | ConvertTo-Json | Write-Output
if ($code -ne 0 -or $result.tests -lt 1) { exit 1 }
