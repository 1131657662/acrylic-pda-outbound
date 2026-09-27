#requires -Version 5.1
<#
.SYNOPSIS
  One-shot provisioning + run of the M2b-4b real-HTTP verification (e2a attempt 106).

.DESCRIPTION
  Steps, each with an explicit exit-code check (fail fast, raw error printed):
    1. kill every mysqld owning/writing port 19330
    2. mysqld --initialize-insecure into the attempt datadir
    3. start mysqld on 127.0.0.1:19330 with --log-bin-trust-function-creators=ON (no --skip-networking)
    4. wait for readiness by a REAL connection: mysql -uroot with no password must answer SELECT 1
    5. create the empty database, the user and its grants
    6. generate a 64-hex password, store it in the attempt directory, export it for the run
    7. run integrationTest --tests '*E2aTemporaryLinkConfirmIT' in the guarded worktree

  Readiness never uses `mysqladmin ping`: that command exits 0 even when the server answers
  ER_ACCESS_DENIED, which previously produced a false "ready".

  Re-run policy: idempotent while the attempt is unused. If the harness one-shot marker
  (test-started.marker) already exists, the script refuses to run and tells you to use a new number.

.EXAMPLE
  powershell -NoProfile -ExecutionPolicy Bypass -File tools/e2a-provision-and-run-106.ps1
#>
[CmdletBinding()]
param(
    [int]$Run = 106,
    [string]$Tests = '*E2aTemporaryLinkConfirmIT',
    [string]$Workspace = 'D:\acrylic-pda-worktrees\r06-e2a-migration',
    [string]$MySqlBase = 'C:\Program Files\MySQL\MySQL Server 8.0',
    [int]$Port = 19330,
    [switch]$ProvisionOnly,
    # Non-e2a ITs (@SpringBootTest + MockMvc) resolve the app's own datasource instead of E2aCrossSourceProfile, so they
    # need the isolated database exported and the app allowed to migrate it. Off by default: the verified e2a path keeps
    # its manual per-IT migration targets untouched.
    [switch]$NonE2a,
    # Throwaway offline signing key pair for -NonE2a (gitignored; never production material).
    [string]$SigningDir = 'D:\acrylic-pda-outbound\.local\it-keys',
    # Reusable constructor (R02): by default the isolated MySQL is stopped again once the run is reported, so one
    # command means "start empty DB -> run the chosen gated IT (which starts the real server and drives real HTTP)
    # -> report -> stop". Pass -KeepServerRunning to inspect the instance afterwards.
    [switch]$KeepServerRunning
)

$ErrorActionPreference = 'Continue'
$utf8 = New-Object System.Text.UTF8Encoding($false)
$attempt = Join-Path $Workspace (".local/e2a-cross-20260925-$Run")
$data = Join-Path $attempt 'data'
$log = Join-Path $attempt 'mysqld.log'
$initLog = Join-Path $attempt 'initialize.log'
$passwordFile = Join-Path $attempt 'run-password.txt'
$runLog = Join-Path $attempt 'integration-test.log'
$database = "acrylic_e2a_cross_20260925_$Run"
$user = "e2a_cross_20260925_$Run"
$mysqld = Join-Path $MySqlBase 'bin/mysqld.exe'
$mysql = Join-Path $MySqlBase 'bin/mysql.exe'

function Fail([string]$step, [string]$detail) {
    Write-Host "FAILED at: $step" -ForegroundColor Red
    if ($detail) { Write-Host $detail -ForegroundColor Red }
    exit 1
}
function Assert-Zero([string]$step) {
    if ($LASTEXITCODE -ne 0) { Fail $step "exit code $LASTEXITCODE" }
}
function Show-Log([string]$path, [int]$lines = 12) {
    if (Test-Path -LiteralPath $path) {
        Write-Host "--- $path (last $lines) ---"
        [System.IO.File]::ReadAllText($path, $utf8) -split "`n" | Select-Object -Last $lines | ForEach-Object { Write-Host $_ }
    }
}

if (-not (Test-Path -LiteralPath $mysqld)) { Fail 'locate mysqld' "not found: $mysqld" }
New-Item -ItemType Directory -Force -Path $attempt, (Join-Path $attempt 'private'), (Join-Path $attempt 'storage') | Out-Null
if (Test-Path -LiteralPath (Join-Path $attempt 'test-started.marker')) {
    Fail 're-run guard' "attempt $Run is already used (test-started.marker exists). Use a new -Run number."
}

# ---------------------------------------------------------------- 1) kill port 19330 owners
Write-Host "== 1) stop any mysqld on port $Port =="
$owners = @(Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue | Where-Object { $_.LocalPort -eq $Port } | Select-Object -ExpandProperty OwningProcess -Unique)
foreach ($owner in $owners) {
    $process = Get-Process -Id $owner -ErrorAction SilentlyContinue
    Write-Host "port owner pid=$owner name=$($process.ProcessName)"
    if ($process -and $process.ProcessName -eq 'mysqld') { Stop-Process -Id $owner -Force -ErrorAction SilentlyContinue }
}
Get-CimInstance Win32_Process -Filter "Name='mysqld.exe'" -ErrorAction SilentlyContinue |
    Where-Object { $_.CommandLine -and $_.CommandLine -match [regex]::Escape($attempt) } |
    ForEach-Object { Write-Host "stopping stray pid=$($_.ProcessId)"; Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
$freed = $false
foreach ($i in 1..40) {
    Start-Sleep -Milliseconds 500
    if (@(Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue | Where-Object { $_.LocalPort -eq $Port }).Count -eq 0) { $freed = $true; break }
}
if (-not $freed) { Fail 'free port' "port $Port is still listening" }

# ---------------------------------------------------------------- 2) initialize datadir
Write-Host "== 2) initialize datadir =="
if (-not (Test-Path -LiteralPath $data)) {
    & $mysqld --no-defaults --initialize-insecure "--basedir=$MySqlBase" "--datadir=$data" "--log-error=$initLog" 2>&1 |
        Tee-Object -FilePath (Join-Path $attempt 'initialize.stdout.log')
    if ($LASTEXITCODE -ne 0) { Show-Log $initLog 15; Fail 'mysqld --initialize-insecure' "exit code $LASTEXITCODE" }
    Write-Host "datadir initialized: $data"
} else {
    Write-Host "datadir already present: $data (reusing)"
}
if (-not (Test-Path -LiteralPath (Join-Path $data 'mysql'))) { Fail 'datadir check' "no mysql schema directory under $data" }

# ---------------------------------------------------------------- 3) start server
Write-Host "== 3) start mysqld on 127.0.0.1:$Port =="
Start-Process -FilePath $mysqld -WindowStyle Hidden -ArgumentList @(
    '--no-defaults', "--basedir=`"$MySqlBase`"", "--datadir=`"$data`"", '--bind-address=127.0.0.1',
    "--port=$Port", "--log-error=`"$log`"", '--mysqlx=OFF', '--log-bin-trust-function-creators=ON') | Out-Null

# ---------------------------------------------------------------- 4) readiness by a real connection
Write-Host "== 4) wait for a real root connection (no password) =="
$ready = $false
foreach ($i in 1..90) {
    Start-Sleep -Seconds 1
    $probe = & $mysql --no-defaults '-h127.0.0.1' "-P$Port" -uroot --connect-timeout=3 -e 'SELECT 1' 2>&1
    if ($LASTEXITCODE -eq 0) { $ready = $true; break }
}
if (-not $ready) { Show-Log $log 15; Fail 'server readiness' "root with no password cannot run SELECT 1 (last probe: $probe)" }
Write-Host "server ready"

# ---------------------------------------------------------------- 5) password + grants
Write-Host "== 5) create database, user and grants =="
$password = -join (1..64 | ForEach-Object { '0123456789abcdef'[(Get-Random -Maximum 16)] })
$sql = @"
DROP DATABASE IF EXISTS $database;
CREATE DATABASE $database CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs;
CREATE USER IF NOT EXISTS '$user'@'127.0.0.1' IDENTIFIED BY '$password';
ALTER USER '$user'@'127.0.0.1' IDENTIFIED BY '$password';
GRANT ALL PRIVILEGES ON $database.* TO '$user'@'127.0.0.1';
FLUSH PRIVILEGES;
"@
$sql | & $mysql --no-defaults '-h127.0.0.1' "-P$Port" -uroot
Assert-Zero 'database/user provisioning'

# ---------------------------------------------------------------- 6) persist password, verify as the new user
[System.IO.File]::WriteAllText($passwordFile, $password + "`n", $utf8)
Write-Host "password written to $passwordFile (not printed)"
$check = & $mysql --no-defaults '-h127.0.0.1' "-P$Port" "-u$user" "-p$password" $database -e 'SELECT @@port,@@log_bin,@@log_bin_trust_function_creators,DATABASE(),CURRENT_USER();' 2>&1
if ($LASTEXITCODE -ne 0) { Fail 'verify new user' ($check -join "`n") }
$check | ForEach-Object { Write-Host "  $_" }
$tables = @(& $mysql --no-defaults '-h127.0.0.1' "-P$Port" "-u$user" "-p$password" $database -N -B -e "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='$database';" 2>$null)
if ($LASTEXITCODE -ne 0) { Fail 'verify empty schema' 'table count query failed' }
$tableCount = ($tables | Where-Object { $_ -match '^\d+$' } | Select-Object -Last 1)
if ($tableCount -ne '0') { Fail 'verify empty schema' "expected 0 tables, found '$tableCount' (raw: $($tables -join '|'))" }
if ($ProvisionOnly) { Write-Host "provision-only: server left running on $Port"; exit 0 }

# ---------------------------------------------------------------- 7) run the real-HTTP IT
Write-Host "== 7) run E2aTemporaryLinkConfirmIT =="
$env:E2A_CROSS_VERIFY = 'true'
$env:E2A_CROSS_RUN = "$Run"
$env:E2A_CROSS_PASSWORD = $password
# A3/CSRF root cause (2026-09-27): application.yml defaults server.servlet.session.cookie.secure to true
# (`${OUTBOUND_COOKIE_SECURE:true}`). This harness serves PLAIN HTTP, and a Secure cookie is not sent back over http,
# so every request arrived without its session: /auth/csrf issued a token for a session the next POST could not
# present, the CSRF filter rejected it and the Web login answered 403 ACCESS_DENIED (attempts 150, 194-196, 237).
# Production keeps the secure default; the isolated harness opts out explicitly for the lifetime of this process.
$env:OUTBOUND_COOKIE_SECURE = 'false'
if ($NonE2a) {
    # See the -NonE2a switch: hand the isolated database to the application context itself.
    $env:OUTBOUND_DB_URL = "jdbc:mysql://127.0.0.1:$Port/$database"
    $env:OUTBOUND_DB_USER = $user
    $env:OUTBOUND_DB_PASSWORD = $password
    $env:OUTBOUND_MIGRATE = 'true'
    # Non-e2a ITs never call E2aCrossSourceHttpSetup.signing, so they need the offline signing key pair the e2a path
    # generates per attempt. A throwaway pair lives in the gitignored .local/it-keys (never committed, never production).
    $env:OUTBOUND_AUTH_SIGNING_PRIVATE_KEY = Join-Path $SigningDir 'server-private.der'
    $env:OUTBOUND_AUTH_SIGNING_PUBLIC_KEY = Join-Path $SigningDir 'server-public.der'
    # File-writing ITs (archive, exports, evidence) need a storage root per attempt; the e2a helper passes it explicitly
    # and non-e2a ITs would otherwise fall back to ./private-files relative to the gradle working directory.
    $env:OUTBOUND_STORAGE = Join-Path $attempt 'storage'
    New-Item -ItemType Directory -Force -Path $env:OUTBOUND_STORAGE | Out-Null
    if (-not (Test-Path -LiteralPath $env:OUTBOUND_AUTH_SIGNING_PRIVATE_KEY)) {
        Fail 'signing key' "missing $($env:OUTBOUND_AUTH_SIGNING_PRIVATE_KEY); generate with .local/it-keys/GenKeys.java"
    }
}
Push-Location $Workspace
$runStartedAt = Get-Date
try {
    # Full-suite mode: an empty/-ALL $Tests runs every IT. Passing a glob through PowerShell is unsafe
    # (it glob-expands and Gradle then sees a bogus filter such as ".git"), so omit --tests entirely.
    $testArgs = if ([string]::IsNullOrWhiteSpace($Tests) -or $Tests -in @('*','ALL')) { @() } else { @('--tests', $Tests) }
    # --rerun-tasks is required: a repeated run with the SAME --tests filter is UP-TO-DATE for Gradle, so the task is
    # skipped and the attempt silently produces no XML at all (observed 2026-09-27, attempt 186). With the filter in
    # place only the named tests execute, so forcing the task cannot widen the run.
    & (Join-Path $Workspace 'gradlew.bat') -p server integrationTest @testArgs --rerun-tasks --console=plain 2>&1 |
        Tee-Object -FilePath $runLog
    $gradleExit = $LASTEXITCODE
} finally {
    Pop-Location
}
Write-Host "gradle exit=$gradleExit"

# ---------------------------------------------------------------- results
$resultsDir = Join-Path $Workspace 'server/build/test-results/integrationTest'
# Aggregate every XML produced by THIS run; if this run produced none, say so — never print a previous run's file.
$xml = @(Get-ChildItem $resultsDir -Filter 'TEST-*.xml' -ErrorAction SilentlyContinue |
    Where-Object { $_.LastWriteTime -ge $runStartedAt })
if ($xml.Count -eq 0) {
    Write-Host "RESULT attempt=$Run NOT-EXECUTED (this run produced no XML under $resultsDir)"
    $started = Test-Path -LiteralPath (Join-Path $attempt 'test-started.marker')
    Write-Host "markers: started=$started completed=False attempt=$attempt"
    exit 2
}
# Counters must NOT be called $tests/$Test etc.: PowerShell variable names are case-insensitive, and -Tests is a
# [string] parameter, so a $tests counter silently degrades to string concatenation and the RESULT line reported
# "01" for one class / "0111" for three (verified 2026-09-27). The *Count names cannot collide with any parameter.
$testCount=0;$failureCount=0;$errorCount=0;$skipCount=0
foreach ($x in $xml) {
    $d = [xml][System.IO.File]::ReadAllText($x.FullName, (New-Object System.Text.UTF8Encoding($false)))
    $testCount += [int]$d.testsuite.tests; $failureCount += [int]$d.testsuite.failures
    $errorCount += [int]$d.testsuite.errors; $skipCount += [int]$d.testsuite.skipped
}
Write-Host ("RESULT attempt={0} classes={1} tests={2} failures={3} errors={4} skipped={5}" -f $Run, $xml.Count, $testCount, $failureCount, $errorCount, $skipCount)
$xml | ForEach-Object {
    $d = [xml][System.IO.File]::ReadAllText($_.FullName, (New-Object System.Text.UTF8Encoding($false)))
    $d.testsuite.testcase | ForEach-Object {
        $state = if ($_.failure) { 'FAIL' } elseif ($_.error) { 'ERROR' } elseif ($_.skipped) { 'SKIP' } else { 'pass' }
        Write-Host ("  [{0}] {1}.{2}" -f $state, $d.testsuite.name, $_.name)
        if ($_.failure) { Write-Host ($_.failure.message -split "`n" | Select-Object -First 3 | Out-String) }
        if ($_.error) { Write-Host ($_.error.message -split "`n" | Select-Object -First 3 | Out-String) }
    }
}
$started = Test-Path -LiteralPath (Join-Path $attempt 'test-started.marker')
$completed = Test-Path -LiteralPath (Join-Path $attempt 'test-completed.marker')
Write-Host "markers: started=$started completed=$completed attempt=$attempt"

# Teardown is part of the constructor contract: the caller gets the instance back the way it was found.
if (-not $KeepServerRunning) {
    Get-CimInstance Win32_Process -Filter "Name='mysqld.exe'" -ErrorAction SilentlyContinue |
        Where-Object { $_.CommandLine -and $_.CommandLine -match [regex]::Escape($attempt) } |
        ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
    $stopped = $false
    foreach ($i in 1..40) {
        Start-Sleep -Milliseconds 500
        if (@(Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue | Where-Object { $_.LocalPort -eq $Port }).Count -eq 0) { $stopped = $true; break }
    }
    Write-Host ("teardown: port {0} free = {1}" -f $Port, $stopped)
    if (-not $stopped) { Write-Host "teardown warning: port $Port still listening" }
} else {
    Write-Host "teardown skipped (-KeepServerRunning); instance is on 127.0.0.1:$Port"
}
if ($gradleExit -ne 0) { exit $gradleExit }
