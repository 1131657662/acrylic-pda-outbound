param([switch]$ProbeIdempotence)
$ErrorActionPreference = 'Stop'
$root = 'D:\acrylic-pda-outbound'
$run = 803
$attempt = Join-Path $root ".local\e2a-cross-20260925-$run"
$evidence = Join-Path $root ".local\autonomous-20261001\p08-a-$run"
$database = "acrylic_e2a_cross_20260925_$run"
$dbUser = "e2a_cross_20260925_$run"
$mysqlBin = 'C:\Program Files\MySQL\MySQL Server 8.0\bin'
$port = 19330

function Require-FreePort([int]$Number) {
    if (Get-NetTCPConnection -State Listen -LocalPort $Number -ErrorAction SilentlyContinue) { throw "Port $Number is already occupied" }
}
function Capture-DeviceSnapshot([string]$Path) {
    $adb = Get-Command adb -ErrorAction SilentlyContinue
    if ($null -eq $adb) { Set-Content -LiteralPath $Path -Value 'adb=UNAVAILABLE' -Encoding utf8; return }
    & $adb.Source devices -l *> $Path
    if ($LASTEXITCODE -ne 0) { throw 'adb devices failed' }
}
function Record-Disk([string]$Label) {
    $free = (Get-PSDrive -Name D).Free
    Add-Content -LiteralPath (Join-Path $evidence 'disk.tsv') -Value ($Label + [char]9 + $free)
    if ($free -lt 20GB) { throw "D: free space below 20 GB at $Label" }
}
function New-Attempt() {
    $resolvedRoot = (Resolve-Path -LiteralPath $root).Path
    if ((Get-Location).Path -ne $resolvedRoot) { throw 'Unexpected workspace' }
    if (Test-Path -LiteralPath $attempt) { throw 'Attempt already exists; preserve it and allocate a new run' }
    New-Item -ItemType Directory -Path $attempt,(Join-Path $attempt 'private'),(Join-Path $attempt 'storage') | Out-Null
    New-Item -ItemType File -Path (Join-Path $evidence 'attempt-created.marker') | Out-Null
}
function Write-PrivateConfiguration() {
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    $bytes = [byte[]]::new(32); $rng.GetBytes($bytes)
    $script:dbPassword = (($bytes | ForEach-Object { $_.ToString('x2') }) -join '')
    $rng.GetBytes($bytes); $rootPassword = (($bytes | ForEach-Object { $_.ToString('x2') }) -join ''); $rng.Dispose()
    $nl = [Environment]::NewLine
    $sql = "ALTER USER 'root'@'localhost' IDENTIFIED BY '$rootPassword';$nl"
    $sql += "CREATE DATABASE $database;$nl"
    $sql += "CREATE USER '$dbUser'@'127.0.0.1' IDENTIFIED BY '$dbPassword';$nl"
    $sql += "GRANT ALL ON $database.* TO '$dbUser'@'127.0.0.1';$nl"
    $sql += "GRANT SHUTDOWN ON *.* TO '$dbUser'@'127.0.0.1';$nl"
    [IO.File]::WriteAllText((Join-Path $attempt 'private\bootstrap.sql'),$sql)
    $client = "[client]$nl" + "user=$dbUser$nl" + "password=$dbPassword$nl" + "host=127.0.0.1$nl" + "port=$port$nl" + "protocol=tcp$nl"
    [IO.File]::WriteAllText((Join-Path $attempt 'private\client.cnf'),$client)
}
function Initialize-MySql() {
    $args = @('--no-defaults','--initialize-insecure',"--datadir=$attempt/data",'--console')
    $out = Join-Path $attempt 'private\initialize.out.log'; $err = Join-Path $attempt 'private\initialize.err.log'
    $process = Start-Process -FilePath (Join-Path $mysqlBin 'mysqld.exe') -ArgumentList $args -WindowStyle Hidden -PassThru -RedirectStandardOutput $out -RedirectStandardError $err
    if (-not $process.WaitForExit(60000)) { throw 'MySQL initialization still running' }
    if (-not (Test-Path -LiteralPath (Join-Path $attempt 'data\ibdata1'))) { throw 'MySQL initialization failed' }
}
function Start-IsolatedMySql() {
    Require-FreePort $port
    $args = @('--no-defaults',"--datadir=$attempt/data", "--port=$port",'--bind-address=127.0.0.1','--mysqlx=OFF','--skip-name-resolve','--skip-grant-tables=false','--log-bin-trust-function-creators=ON',"--init-file=$($attempt.Replace('\','/'))/private/bootstrap.sql",'--console')
    $out = Join-Path $attempt 'private\server.out.log'; $err = Join-Path $attempt 'private\server.err.log'
    $script:mysqlProcess = Start-Process -FilePath (Join-Path $mysqlBin 'mysqld.exe') -ArgumentList $args -WindowStyle Hidden -PassThru -RedirectStandardOutput $out -RedirectStandardError $err
    [IO.File]::WriteAllText((Join-Path $attempt 'private\launch-pid.txt'),[string]$script:mysqlProcess.Id)
    for ($i=0; $i -lt 60; $i++) {
        if (Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue) { return }
        if ($script:mysqlProcess.HasExited) { throw 'MySQL exited during startup' }
        Start-Sleep -Milliseconds 500
    }
    throw 'MySQL listener did not start'
}
function Read-Identity() {
    $sql = 'SELECT @@datadir,@@port,DATABASE(),CURRENT_USER(),@@log_bin,@@log_bin_trust_function_creators'
    $row = & (Join-Path $mysqlBin 'mysql.exe') "--defaults-file=$attempt/private/client.cnf" --batch --raw --skip-column-names $database --execute=$sql 2> (Join-Path $attempt 'private\identity.err.log')
    if ($LASTEXITCODE -ne 0) { throw 'Cannot authenticate isolated database identity' }
    $values = ([string]$row).Split([char]9)
    $expectedDir = $attempt.Replace('\','/').TrimEnd('/') + '/data'
    if ($values.Length -ne 6 -or $values[0].Replace('\','/').TrimEnd('/') -ine $expectedDir -or $values[1] -ne "$port" -or $values[2] -ne $database -or $values[3] -ne "$dbUser@127.0.0.1" -or $values[4] -ne '1' -or $values[5] -ne '1') { throw 'Connected identity mismatch' }
    return [string]$row
}
function Run-IntegrationTest() {
    $env:E2A_CROSS_VERIFY = 'true'; $env:E2A_CROSS_RUN = [string]$run; $env:E2A_CROSS_PASSWORD = $script:dbPassword
    try {
        Push-Location $root
        try { & cmd.exe /c 'gradlew.bat -p server integrationTest --tests *E2aCorrectionAppendOnlyIT --no-daemon --rerun-tasks > .local\autonomous-20261001\p08-a-803\integration.log 2>&1'; $script:testExit = $LASTEXITCODE }
        finally { Pop-Location }
        $xml = Join-Path $root 'server\build\test-results\integrationTest\TEST-com.acrylic.outbound.e2averify.E2aCorrectionAppendOnlyIT.xml'
        if (Test-Path -LiteralPath $xml) { Copy-Item -LiteralPath $xml -Destination (Join-Path $evidence 'integration-result.xml') }
        if ($script:testExit -ne 0) { throw "integration test failed with exit $script:testExit" }
        if (-not (Test-Path -LiteralPath (Join-Path $attempt 'test-started.marker'))) { throw 'Missing test-started.marker' }
        if (-not (Test-Path -LiteralPath (Join-Path $attempt 'test-completed.marker'))) { throw 'Missing test-completed.marker' }
        if (-not (Test-Path -LiteralPath (Join-Path $evidence 'integration-result.xml'))) { throw 'Missing integration XML' }
        [xml]$report = Get-Content -Raw -LiteralPath (Join-Path $evidence 'integration-result.xml')
        if ([int]$report.testsuite.tests -ne 1 -or [int]$report.testsuite.failures -ne 0 -or [int]$report.testsuite.errors -ne 0 -or [int]$report.testsuite.skipped -ne 0) { throw 'Integration XML is not 1/0/0/0' }
        [IO.File]::WriteAllText((Join-Path $evidence 'assertions.txt'),'tests=1 failures=0 errors=0 skipped=0; started/completed markers present')
    } finally {
        Remove-Item Env:E2A_CROSS_VERIFY,Env:E2A_CROSS_RUN,Env:E2A_CROSS_PASSWORD -ErrorAction SilentlyContinue
    }
}
function Stop-IsolatedMySql() {
    if ($null -eq $script:mysqlProcess) { return }
    $identity = Read-Identity
    [IO.File]::WriteAllText((Join-Path $evidence 'shutdown-identity.tsv'),$identity)
    & (Join-Path $mysqlBin 'mysqladmin.exe') "--defaults-file=$attempt/private\client.cnf" shutdown *> (Join-Path $attempt 'private\shutdown.log')
    if ($LASTEXITCODE -ne 0) { throw 'Authenticated shutdown failed' }
    for ($i=0; $i -lt 40; $i++) {
        if (-not (Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue)) { $script:mysqlProcess = $null; return }
        Start-Sleep -Milliseconds 250
    }
    throw 'Isolated listener remains after shutdown'
}

New-Item -ItemType Directory -Force -Path $evidence | Out-Null
if ($ProbeIdempotence) {
    $second = Join-Path $evidence 'idempotence-second.log'
    & powershell.exe -NoProfile -File $PSCommandPath *> $second
    $secondExit = $LASTEXITCODE
    if ($secondExit -eq 0 -or (Get-Content -Raw -LiteralPath $second) -notmatch 'Attempt already exists') { throw 'Second invocation was not rejected by one-shot guard' }
    [IO.File]::WriteAllText((Join-Path $evidence 'idempotence-verified.marker'),'second invocation rejected before any test or database mutation')
    exit 0
}
if (Test-Path -LiteralPath $attempt) { throw 'Attempt already exists; preserve it and allocate a new run' }
Capture-DeviceSnapshot (Join-Path $evidence 'adb-before.txt')
Record-Disk 'before'
New-Attempt
Write-PrivateConfiguration
Initialize-MySql
$authenticated = $false
try {
    Start-IsolatedMySql
    $identity = Read-Identity; $authenticated = $true
    [IO.File]::WriteAllText((Join-Path $evidence 'connected-identity.tsv'),$identity)
    Run-IntegrationTest
} finally {
    if ($authenticated) { Stop-IsolatedMySql }
    Capture-DeviceSnapshot (Join-Path $evidence 'adb-after.txt')
    Record-Disk 'after'
}
$before = Get-Content -Raw -LiteralPath (Join-Path $evidence 'adb-before.txt')
$after = Get-Content -Raw -LiteralPath (Join-Path $evidence 'adb-after.txt')
if ($before -match '22D2910042' -and $before -ne $after) { throw 'Real-device snapshot changed' }
& powershell.exe -NoProfile -File $PSCommandPath -ProbeIdempotence
if ($LASTEXITCODE -ne 0) { throw 'Idempotence probe failed' }
Write-Output "Attempt=$attempt; evidence=$evidence; testExit=0; MySQL stopped; idempotence guard verified"

