# R09 C2 B1-3：V36 家族门控 IT 的隔离运行器（一次性 attempt，消费后不删不改）
#
# 六个 IT 各自要求：<PREFIX>_VERIFY=true、<PREFIX>_URL/_USER/_PASSWORD，且 @@datadir 必须等于
# <attempt>/data（attempt 是固定路径，IT 会写 test-started.marker ⇒ 一次运行即消费）。
# 用法：
#   powershell -NoProfile -File tools/r09-e2a-v36-run.ps1 -Item migration
#   powershell -NoProfile -File tools/r09-e2a-v36-run.ps1 -Item devicekey
#   powershell -NoProfile -File tools/r09-e2a-v36-run.ps1 -Item migration -Shutdown   # 关掉残留实例
param(
    [Parameter(Mandatory = $true)][ValidateSet('migration', 'devicekey', 'pairedkey', 'stopwriter', 'auditprefix', 'auditarchive')]
    [string]$Item,
    [int]$Run = 700,
    [switch]$Shutdown,
    [switch]$Reuse
)

$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent (Split-Path -Parent $PSCommandPath)
$Port = 19330
$Bin = 'C:\Program Files\MySQL\MySQL Server 8.0\bin'
$Mysqld = Join-Path $Bin 'mysqld.exe'
$Mysql = Join-Path $Bin 'mysql.exe'
$MysqlAdmin = Join-Path $Bin 'mysqladmin.exe'

# 每个 IT 的常量（与源码一一对应，见记录 S27）
$table = @{
    migration    = @{ gate = 'E2A_V36_VERIFY'; prefix = 'E2A_V36'; cls = 'E2aV36MigrationIT'; db = 'acrylic_outbound_e2a_v36_20260924d'; user = 'e2a_v36_20260924d'; attempt = '.local/e2a-v36-attempt4' }
    devicekey    = @{ gate = 'E2A_DEVICE_KEY_VERIFY'; prefix = 'E2A_DEVICE_KEY'; cls = 'E2aV36DeviceKeyIT'; db = 'acrylic_outbound_e2a_key_20260924c'; user = 'e2a_key_20260924c'; attempt = '.local/e2a-v36-key-attempt3' }
    pairedkey    = @{ gate = 'E2A_KEY_VERIFY'; prefix = 'E2A_KEY'; cls = 'E2aV36PairedKeyIT'; db = 'acrylic_outbound_e2a_key_20260924a'; user = 'e2a_key_20260924a'; attempt = '.local/e2a-v36-key-attempt1' }
    stopwriter   = @{ gate = 'E2A_STOP_VERIFY'; prefix = 'E2A_STOP'; cls = 'E2aV36StopWriterIT'; db = 'acrylic_outbound_e2a_stop_20260924a'; user = 'e2a_stop_20260924a'; attempt = '.local/e2a-v36-stop-attempt1' }
    auditprefix  = @{ gate = 'E2A_AUDIT_PREFIX_VERIFY'; prefix = 'E2A_AUDIT_PREFIX'; cls = 'E2aV36AuditPrefixIT'; db = 'acrylic_outbound_e2a_audit_20260924e'; user = 'e2a_audit_20260924e'; attempt = '.local/e2a-v36-audit-attempt5' }
    auditarchive = @{ gate = 'E2A_AUDIT_ARCHIVE_VERIFY'; prefix = 'E2A_AUDIT_ARCHIVE'; cls = 'E2aV36AuditArchiveIT'; db = 'acrylic_outbound_e2a_audit_20260924f'; user = 'e2a_audit_20260924f'; attempt = '.local/e2a-v36-audit-attempt6' }
}
$c = $table[$Item]
$attempt = Join-Path $Root $c.attempt
$data = Join-Path $attempt 'data'
$schema = $c.db
$user = $c.user
$password = -join (1..64 | ForEach-Object { '0123456789abcdef'[(Get-Random -Maximum 16)] })

# A consumed datadir is reused with the password it was created with (the ITs read it from the env).
New-Item -ItemType Directory -Force -Path $attempt | Out-Null
$pwFile = Join-Path $attempt 'password.txt'
if ($Reuse -and (Test-Path $pwFile)) { $password = ([IO.File]::ReadAllText($pwFile)).Trim() }
else { [IO.File]::WriteAllText($pwFile, $password, (New-Object Text.UTF8Encoding($false))) }
$adminCnf = Join-Path $attempt 'admin.cnf'
function Invoke-MysqlAdmin([string[]]$Extra) {
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try { & $MysqlAdmin "--defaults-extra-file=$adminCnf" @Extra 2>&1 | Out-Null } finally { $ErrorActionPreference = $previous }
    return $LASTEXITCODE
}

if ($Shutdown) {
    if (Test-Path $adminCnf) { $code = Invoke-MysqlAdmin @('--protocol=tcp', 'shutdown'); "shutdown exit=$code" }
    else { 'no admin.cnf; nothing to shut down' }
    return
}

if (-not (Test-Path $Mysqld)) { throw "mysqld not found: $Mysqld" }
if (Test-Path (Join-Path $attempt 'test-started.marker')) {
    throw "attempt already consumed (test-started.marker exists): $attempt -- one-shot ITs are never re-run by deleting evidence"
}
if (@(Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue | Where-Object { $_.LocalPort -eq $Port }).Count) {
    throw "port $Port is occupied; release the instance first"
}

New-Item -ItemType Directory -Force -Path $attempt | Out-Null
if (-not $Reuse) {
    if (Test-Path $data) { throw "datadir already exists (refusing to reuse): $data" }
    New-Item -ItemType Directory -Force -Path $data | Out-Null
    & $Mysqld --no-defaults "--datadir=$data" '--initialize-insecure' "--log-error=$(Join-Path $attempt 'initialize.log')" | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "mysqld --initialize-insecure failed (see $attempt/initialize.log)" }
}
New-Item -ItemType Directory -Force -Path (Join-Path $attempt 'storage') | Out-Null

# The audit ITs assert @@log_bin = 0; the migration/key/stop ITs need binlog on.
$bootstrap = Join-Path $attempt 'bootstrap.sql'
$suffixes = @()
$sql = "CREATE DATABASE IF NOT EXISTS $schema;`n" +
    "CREATE USER IF NOT EXISTS '$user'@'127.0.0.1' IDENTIFIED BY '$password';`n" +
    "ALTER USER '$user'@'127.0.0.1' IDENTIFIED BY '$password';`n" +
"GRANT ALL ON $schema.* TO '$user'@'127.0.0.1';`n" +
    "GRANT PROCESS ON *.* TO '$user'@'127.0.0.1';`n" +
    "GRANT SHUTDOWN ON *.* TO '$user'@'127.0.0.1';`n" +
    "GRANT ALL ON *.* TO '$user'@'127.0.0.1' WITH GRANT OPTION;`n" +
    "CREATE USER 'root'@'127.0.0.1' IDENTIFIED BY '';`n" +
    "GRANT ALL ON *.* TO 'root'@'127.0.0.1' WITH GRANT OPTION;`n" +
    "FLUSH PRIVILEGES;`n"
[IO.File]::WriteAllText($bootstrap, $sql, (New-Object Text.UTF8Encoding($false)))
# The audit ITs assert @@log_bin = 0; the migration/key/stop ITs need binlog on.
if ($Item -in @('auditprefix', 'auditarchive')) { $logArgs = @('--skip-log-bin') } else { $logArgs = @('--log-bin-trust-function-creators=ON') }
$serverArgs = @('--no-defaults', "--datadir=$data", "--port=$Port",
    '--bind-address=127.0.0.1', '--mysqlx=OFF', '--skip-name-resolve', '--skip-grant-tables=false') + $logArgs + @(
    "--log-error=$attempt/server.log", "--init-file=$($bootstrap.Replace('\', '/'))")

Start-Process -FilePath $Mysqld -ArgumentList $serverArgs -WindowStyle Hidden `
    -RedirectStandardOutput (Join-Path $attempt 'server.stdout.log') -RedirectStandardError (Join-Path $attempt 'server.stderr.log') | Out-Null
$ready = $false
foreach ($i in 1..60) {
    Start-Sleep -Milliseconds 500
    if (@(Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue | Where-Object { $_.LocalPort -eq $Port }).Count) { $ready = $true; break }
}
if (-not $ready) { throw "mysqld did not start (see $(Join-Path $attempt 'server.log'))" }

[IO.File]::WriteAllText($adminCnf, "[client]`nhost=127.0.0.1`nport=$Port`nuser=root`nprotocol=tcp`n",
    (New-Object Text.UTF8Encoding($false)))

# The ITs resolve their attempt paths through E2aWorktree.ROOT, so the tree under test must be named.
$env:E2A_CROSS_ROOT = $Root
Set-Item -Path "env:$($c.gate)" -Value 'true'
Set-Item -Path "env:$($c.prefix)_URL" -Value ("jdbc:mysql://127.0.0.1:$Port/$schema" + '?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC')
Set-Item -Path "env:$($c.prefix)_USER" -Value $user
Set-Item -Path "env:$($c.prefix)_PASSWORD" -Value $password
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.11'

$logDir = Join-Path $Root ".local/r09-c2-b1-3-s27-$Item"
New-Item -ItemType Directory -Force -Path $logDir | Out-Null
$gradlePreference = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
& (Join-Path $Root 'gradlew.bat') -p server recoveryControlClasspath integrationTest --rerun-tasks --no-daemon `
    --console=plain --tests "com.acrylic.outbound.e2averify.$($c.cls)" *> (Join-Path $logDir 'gradle.log')
$testExit = $LASTEXITCODE; $ErrorActionPreference = $gradlePreference

$results = Join-Path $Root 'server/build/test-results/integrationTest'
$xml = Get-ChildItem $results -Filter 'TEST-*.xml' -ErrorAction SilentlyContinue | Select-Object -First 1
$stats = 'no-xml'
if ($xml) {
    Copy-Item $xml.FullName $logDir -Force
    [xml]$d = Get-Content $xml.FullName -Raw
    $stats = "tests=$($d.testsuite.tests) fail=$($d.testsuite.failures) err=$($d.testsuite.errors) skip=$($d.testsuite.skipped) time=$($d.testsuite.time)"
    $failed = $d.testsuite.testcase | Where-Object { $_.failure } | Select-Object -First 1
    if ($failed) { $stats = $stats + ' :: ' + (($failed.failure.message -split "`n")[0]) }
}
"item=$Item class=$($c.cls) testExit=$testExit $stats"
"attempt=$attempt listening=" + @(Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue | Where-Object { $_.LocalPort -eq $Port }).Count
