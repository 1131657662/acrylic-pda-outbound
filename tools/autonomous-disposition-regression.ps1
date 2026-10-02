param(
    [Parameter(Mandatory=$true)][string]$EvidenceName,
    [string]$Tests = '*TemporaryProtocolTest',
    [switch]$Full
)
$ErrorActionPreference = 'Stop'
$workspace = Split-Path -Parent $PSScriptRoot
$evidence = Join-Path $workspace ('.local/autonomous-20261002/' + $EvidenceName)
if (Test-Path -LiteralPath $evidence) { throw 'Evidence directory already exists; choose a new name.' }
if ((Get-PSDrive D).Free -lt 20GB) { throw 'Disk below authorized 20GB threshold.' }
$running = Get-CimInstance Win32_Process -Filter "Name='java.exe'" |
    Where-Object { $_.CommandLine -match 'GradleWrapperMain|Gradle Test Executor' }
if ($running) { throw 'Another Gradle run is active.' }
New-Item -ItemType Directory -Path $evidence | Out-Null
git -C $workspace rev-parse HEAD | Set-Content (Join-Path $evidence 'head.txt')
git -C $workspace status --short --untracked-files=all | Set-Content (Join-Path $evidence 'worktree.txt')
$argsForTest = @('-p', 'server', 'test', '--rerun-tasks', '--console=plain', '-x', ':report-rendering:prepareFont')
if (-not $Full) { $argsForTest += @('--tests', $Tests) }
Push-Location $workspace
try {
    $ErrorActionPreference = 'Continue'
    & (Join-Path $workspace 'gradlew.bat') @argsForTest 2>&1 |
        Tee-Object -FilePath (Join-Path $evidence 'gradle.log')
    $resultCode = $LASTEXITCODE
    $ErrorActionPreference = 'Stop'
    Copy-Item -LiteralPath (Join-Path $workspace 'server/build/test-results/test') -Destination $evidence -Recurse
    $totals = @{ tests=0; failures=0; errors=0; skipped=0; exit=$resultCode }
    foreach ($file in Get-ChildItem (Join-Path $evidence 'test') -Filter 'TEST-*.xml') {
        $suite = ([xml](Get-Content -LiteralPath $file.FullName -Raw)).testsuite
        foreach ($key in @('tests','failures','errors','skipped')) { $totals[$key] += [int]$suite.$key }
    }
    $totals | ConvertTo-Json | Set-Content (Join-Path $evidence 'result.json')
    $totals | ConvertTo-Json | Write-Host
} finally { Pop-Location }
exit $resultCode
