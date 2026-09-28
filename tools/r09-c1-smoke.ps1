#requires -Version 7.0
<# Full Boot startup smoke only. Never runs C1 IT or reuses a prior smoke directory. #>
[CmdletBinding()]
param([ValidateRange(2, 9999)][int]$SmokeRun = 2)

$ErrorActionPreference = 'Stop'
$repo = 'D:/acrylic-pda-outbound'
if ((Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path -ne (Resolve-Path -LiteralPath $repo).Path) {
    throw 'C1 smoke requires the main workspace'
}
$smoke = Join-Path $repo ".local/r09-c1-smoke-20260928-$SmokeRun"
$schema = "acrylic_r09_c1_smoke_20260928_$SmokeRun"
$dbUser = "r09_c1_smoke_$SmokeRun"
$mysqlBase = 'C:/Program Files/MySQL/MySQL Server 8.0'
$mysql = Join-Path $mysqlBase 'bin/mysql.exe'
$mysqld = Join-Path $mysqlBase 'bin/mysqld.exe'
$mysqladmin = Join-Path $mysqlBase 'bin/mysqladmin.exe'
$utf8 = [Text.UTF8Encoding]::new($false)
# Native arguments are complete quoted strings. Never use bare -h127.0.0.1 in PowerShell.
$rootArgs = @('--no-defaults', '--host=127.0.0.1', '--port=19330', '--user=root')
if (Test-Path -LiteralPath $smoke) { throw 'Smoke directory already exists; retain it and stop' }
if (@(Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue | Where-Object { $_.LocalPort -in 19330,19437 }).Count) {
    throw 'Smoke ports occupied; refusing to stop another process'
}
foreach ($key in 'OUTBOUND_DB_RESET','OUTBOUND_DB_REPAIR','E2A_MIGRATION_RESET') {
    if ([Environment]::GetEnvironmentVariable($key) -eq 'true') { throw "Forbidden environment option: $key" }
}
New-Item -ItemType Directory -Path $smoke,(Join-Path $smoke 'storage') | Out-Null
$data = Join-Path $smoke 'data'
$initArgs = @('--no-defaults', '--initialize-insecure', "--basedir=$mysqlBase", "--datadir=$data", "--log-error=$smoke/initialize.log")
& $mysqld @initArgs *> "$smoke/initialize.stdout.log"
if ($LASTEXITCODE -ne 0) { throw 'Smoke MySQL initialization failed; see retained initialize logs' }
$serverArgs = @('--no-defaults', "--basedir=`"$mysqlBase`"", "--datadir=`"$data`"", '--bind-address=127.0.0.1',
    '--port=19330', "--log-error=`"$smoke/mysqld.log`"", '--mysqlx=OFF', '--log-bin-trust-function-creators=ON')
$server = Start-Process -FilePath $mysqld -WindowStyle Hidden -PassThru -ArgumentList $serverArgs
$smokeExit = 1
$javaStarted = $false
try {
    $ready = $false
    $readinessArgs = $rootArgs + @('--connect-timeout=1', '-N', '-e', 'SELECT 1')
    foreach ($tick in 1..30) {
        Start-Sleep -Seconds 1
        & $mysql @readinessArgs *> "$smoke/readiness.log"
        if ($LASTEXITCODE -eq 0) { $ready = $true; break }
    }
    if (-not $ready) { throw 'Smoke MySQL not ready; see retained logs' }
    $passwordBytes = [byte[]]::new(32)
    [Security.Cryptography.RandomNumberGenerator]::Fill($passwordBytes)
    $smokePassword = [Convert]::ToHexString($passwordBytes).ToLowerInvariant()
    [IO.File]::WriteAllText("$smoke/run-password.txt", $smokePassword, $utf8)
    $sql = @"
CREATE DATABASE $schema CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs;
CREATE USER '$dbUser'@'127.0.0.1' IDENTIFIED BY '$smokePassword';
GRANT ALL PRIVILEGES ON $schema.* TO '$dbUser'@'127.0.0.1';
"@
    [IO.File]::WriteAllText("$smoke/provision.sql", $sql, $utf8)
    $provision = '"{0}" --no-defaults --host="127.0.0.1" --port=19330 --user=root < "{1}/provision.sql"' -f $mysql,$smoke
    & cmd.exe '/c' $provision *> "$smoke/provision.log"
    if ($LASTEXITCODE -ne 0) { throw 'Smoke provision failed; see retained provision log' }
    $env:OUTBOUND_DB_URL = "jdbc:mysql://127.0.0.1:19330/$schema"
    $env:OUTBOUND_DB_USER = $dbUser
    $env:OUTBOUND_DB_PASSWORD = $smokePassword
    $env:OUTBOUND_MIGRATE = 'false'
    $env:OUTBOUND_STORAGE = "$smoke/storage"
    $env:OUTBOUND_AUTH_SIGNING_PRIVATE_KEY = "$repo/.local/it-keys/server-private.der"
    $env:OUTBOUND_AUTH_SIGNING_PUBLIC_KEY = "$repo/.local/it-keys/server-public.der"
    $classpath = ([IO.File]::ReadAllText("$repo/server/build/recovery-control-classpath.json") | ConvertFrom-Json) -join ';'
    $arguments = @('-Dfile.encoding=UTF-8','-Duser.timezone=UTC',"-Dc1.smoke.run=$SmokeRun",'-cp',
        ('"' + $classpath.Replace('\','/') + '"'),'com.acrylic.c1probe.C1StartupSmoke') -join "`n"
    [IO.File]::WriteAllText("$smoke/java-args.txt", $arguments, $utf8)
    Push-Location "$repo/server"
    try {
        $javaStarted = $true
        & java "@$smoke/java-args.txt" *> "$smoke/application.log"
        $smokeExit = $LASTEXITCODE
    } finally { Pop-Location }
} finally {
    $owned = @(Get-CimInstance Win32_Process -Filter "Name='mysqld.exe'" | Where-Object {
        $_.CommandLine -and $_.CommandLine.Contains($data)
    })
    $listeners = @(Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue | Where-Object { $_.LocalPort -eq 19330 })
    if ($listeners.Count -and $listeners[0].OwningProcess -in $owned.ProcessId) {
        & $mysqladmin @rootArgs 'shutdown' *> "$smoke/shutdown.log"
        if ($LASTEXITCODE -ne 0) { $smokeExit = 1; Write-Output 'Smoke MySQL shutdown failed; inspect isolated instance' }
    } elseif ($listeners.Count) { $smokeExit = 1; Write-Output 'Instance identity mismatch; refusing shutdown' }
    [IO.File]::WriteAllText("$smoke/runner-result.txt", "javaStarted=$javaStarted`njavaExit=$smokeExit`n", $utf8)
}
Write-Output "Smoke exit=$smokeExit"
if (Test-Path -LiteralPath "$smoke/application.log") { Get-Content -LiteralPath "$smoke/application.log" -Tail 70 }
exit $smokeExit
