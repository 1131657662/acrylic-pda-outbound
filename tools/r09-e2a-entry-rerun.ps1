#requires -Version 5.1
<#
Runs the E2a cross-source entry IT against the tree under test.

The E2a harness is a one-shot isolated installation: this script provisions a fresh MySQL on port
19330 under <root>/.local/e2a-cross-20260925-<Run>/data, creates the schema and the 64-hex account
the profile demands, points the harness at <root> (E2A_CROSS_ROOT) and runs the gated IT. Every
failure round and the datadir are preserved; the instance is only stopped explicitly.
#>
[CmdletBinding()]
param(
    [ValidateRange(1, 999)][int]$Run = 601,
    [string]$Root = 'D:/acrylic-pda-outbound',
    [string]$TestClass = 'com.acrylic.outbound.e2averify.E2aRecoveryAdmissionEntryIT',
    [switch]$Shutdown,
    [switch]$Reuse
)
$ErrorActionPreference = 'Stop'
$port = 19330
$mysqlBase = 'C:/Program Files/MySQL/MySQL Server 8.0'
$mysql = "$mysqlBase/bin/mysql.exe"
$mysqld = "$mysqlBase/bin/mysqld.exe"
$mysqladmin = "$mysqlBase/bin/mysqladmin.exe"
$attempt = Join-Path $Root ".local/e2a-cross-20260925-$Run"
$schema = "acrylic_e2a_cross_20260925_$Run"
$user = "e2a_cross_20260925_$Run"
$utf8 = [Text.UTF8Encoding]::new($false)

if ((Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path -ne (Resolve-Path -LiteralPath $Root).Path) {
    throw 'The E2a rerun must be driven from the tree under test'
}
foreach ($key in 'OUTBOUND_DB_RESET', 'OUTBOUND_DB_REPAIR', 'E2A_MIGRATION_RESET') {
    if ([Environment]::GetEnvironmentVariable($key) -eq 'true') { throw "Forbidden environment option: $key" }
}
if ($Shutdown) {
    if (-not (Test-Path -LiteralPath (Join-Path $attempt 'private/admin.cnf'))) { throw 'No attempt to shut down' }
    $ErrorActionPreference = 'Continue'
    & $mysqladmin "--defaults-file=$attempt/private/admin.cnf" shutdown *> "$attempt/shutdown.log"
    $code = $LASTEXITCODE
    $ErrorActionPreference = 'Stop'
    "shutdown exit=$code listening=$(@(Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue | Where-Object { $_.LocalPort -eq $port }).Count)"
    exit 0
}
$listening = @(Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue | Where-Object { $_.LocalPort -eq $port }).Count
if ($Reuse) {
    if (-not (Test-Path -LiteralPath (Join-Path $attempt 'private/password.txt'))) { throw 'Nothing to reuse in that attempt' }
    if ($listening -eq 0) { throw 'The attempt database is not running; provision a new run instead' }
    $password = (Get-Content (Join-Path $attempt 'private/password.txt') -Raw).Trim()
    "reusing run=$Run schema=$schema state=ready"
} else {
    if ($listening) { throw "Port $port is occupied; refusing to stop another process" }
    if (Test-Path -LiteralPath $attempt) { throw "Attempt $attempt exists; retain it and allocate a new run" }
    if (-not (Test-Path -LiteralPath $mysqlBase)) { throw 'MySQL 8.0 is not installed where this harness expects it' }

New-Item -ItemType Directory -Path $attempt, (Join-Path $attempt 'private'), (Join-Path $attempt 'storage') | Out-Null
$data = Join-Path $attempt 'data'
$password = -join (1..64 | ForEach-Object { '{0:x}' -f (Get-Random -Minimum 0 -Maximum 16) })
[IO.File]::WriteAllText((Join-Path $attempt 'private/password.txt'), $password, $utf8)
# RecoveryProjectionDatabases demands exactly these preallocated (and empty) negative-case schemas;
# _restore is the archive ITs' companion schema.
$suffixes = @('_copy', '_conflict', '_missing', '_gap', '_overflow', '_rollback', '_instance', '_skip', '_mapping',
    '_restore')
$bootstrap = "CREATE DATABASE $schema;`n" +
    "CREATE USER '$user'@'127.0.0.1' IDENTIFIED BY '$password';`n" +
    "GRANT ALL ON $schema.* TO '$user'@'127.0.0.1';`n" +
    # The entry checks read information_schema.INNODB_TRX, which needs the PROCESS privilege.
    "GRANT PROCESS ON *.* TO '$user'@'127.0.0.1';`n" +
    # --skip-name-resolve means root@localhost cannot authenticate over TCP, so the harness needs its
    # own loopback root account for administration and shutdown.
    "CREATE USER 'root'@'127.0.0.1' IDENTIFIED BY '';`n" +
    "GRANT ALL ON *.* TO 'root'@'127.0.0.1' WITH GRANT OPTION;`n" +
    "GRANT SHUTDOWN ON *.* TO '$user'@'127.0.0.1';`n" +
    # Some projection checks build their schema names at runtime; on this one-shot isolated instance
    # the harness account gets the global grant so those cases can create what they need.
    "GRANT ALL ON *.* TO '$user'@'127.0.0.1' WITH GRANT OPTION;`n" +
    # Several E2a ITs work on companion schemas (closed projection copies/instances/conflicts, archive
    # restore). Their names are built at runtime, so the harness provisions the known family up front.
    (($suffixes | ForEach-Object {
        "CREATE DATABASE ${schema}$_;`nGRANT ALL ON ${schema}$_.* TO '$user'@'127.0.0.1';`n" }) -join '') +
    "FLUSH PRIVILEGES;`n"
[IO.File]::WriteAllText((Join-Path $attempt 'private/bootstrap.sql'), $bootstrap, $utf8)
[IO.File]::WriteAllText((Join-Path $attempt 'private/admin.cnf'),
    "[client]`nhost=127.0.0.1`nport=$port`nuser=root`nprotocol=tcp`n", $utf8)

& $mysqld '--no-defaults' '--initialize-insecure' "--datadir=$data" '--mysqlx=OFF' `
    "--log-error=$attempt/initialize.log" *> "$attempt/initialize.stdout.log"
if ($LASTEXITCODE -ne 0) { throw 'MySQL initialization failed; see the retained attempt directory' }

# Binlog stays enabled (the cross-source profile refuses a server without it) and trigger creation
# needs trust_function_creators, exactly as the reference E2a harness starts its instance.
$serverArgs = @('--no-defaults', "--datadir=$data", "--port=$port",
    '--bind-address=127.0.0.1', '--mysqlx=OFF', '--skip-name-resolve', '--skip-grant-tables=false',
    '--log-bin-trust-function-creators=ON', "--log-error=$attempt/server.log",
    "--init-file=$($attempt.Replace('\', '/'))/private/bootstrap.sql")
Start-Process -FilePath $mysqld -ArgumentList $serverArgs -WindowStyle Hidden `
    -RedirectStandardOutput "$attempt/server.stdout.log" -RedirectStandardError "$attempt/server.stderr.log" | Out-Null
$ready = $false
foreach ($attemptNumber in 1..60) {
    Start-Sleep -Milliseconds 500
    if (@(Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue | Where-Object { $_.LocalPort -eq $port }).Count) {
        # A native command that reports "not ready yet" on stderr must not terminate the wait.
        $ErrorActionPreference = 'Continue'
        & $mysqladmin "--defaults-file=$attempt/private/admin.cnf" ping *> $null
        $pinged = $LASTEXITCODE
        $ErrorActionPreference = 'Stop'
        if ($pinged -eq 0) { $ready = $true; break }
    }
}
    if (-not $ready) { throw 'MySQL did not become ready; see the retained attempt directory' }
    "provisioned run=$Run schema=$schema user=$user state=ready"
}

$env:E2A_CROSS_VERIFY = 'true'
$env:E2A_CROSS_RUN = "$Run"
$env:E2A_CROSS_PASSWORD = $password
$env:E2A_CROSS_ROOT = $Root
$env:B11_EFFECT_EVIDENCE_DIR = $attempt
# C1MainProfile starts the application without datasource arguments, so the harness supplies them the
# way the reference run did: through the environment (explicit --spring.datasource.* args still win).
$env:SPRING_DATASOURCE_URL = "jdbc:mysql://127.0.0.1:$port/$schema" +
    '?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC'
$env:SPRING_DATASOURCE_USERNAME = $user
$env:SPRING_DATASOURCE_PASSWORD = $password
# Some E2a ITs start the application without renderer arguments; the harness supplies the install
# distribution that lives in the tree under test (explicit --outbound.exports.* args still win).
# The reference runner supplies the remaining harness environment: the attempt storage root, the
# explicit "no migration" switch, and the auth signing keys the setup writes into <attempt>/private.
$env:OUTBOUND_MIGRATE = 'false'
$env:OUTBOUND_STORAGE = Join-Path $attempt 'storage'
$env:OUTBOUND_AUTH_SIGNING_PRIVATE_KEY = Join-Path $attempt 'private/server-private.der'
$env:OUTBOUND_AUTH_SIGNING_PUBLIC_KEY = Join-Path $attempt 'private/server-public.der'
$env:OUTBOUND_EXPORTS_RENDERER_HOME = Join-Path $Root 'report-rendering/build/install/acrylic-report-rendering'
# PDF rendering needs a Playwright browser tree. The tree under test may not have one (it is a
# network download: gradlew :report-rendering:installChromium); the E2a ITs themselves hard-code the
# r08-export worktree's copy, so the harness prefers its own and falls back to that same path.
$localBrowsers = Join-Path $Root 'report-rendering/build/browsers'
$env:OUTBOUND_EXPORTS_BROWSERS = if (Test-Path $localBrowsers) { $localBrowsers }
    else { 'D:/acrylic-pda-worktrees/r08-export/report-rendering/build/browsers' }
$arguments = @('-p', 'server', 'recoveryControlClasspath', 'integrationTest', '--rerun-tasks', '--no-daemon',
    '--console=plain', '--tests', $TestClass)
$ErrorActionPreference = 'Continue'
& (Join-Path $Root 'gradlew.bat') @arguments *> "$attempt/gradle.log"
$testExit = $LASTEXITCODE
$ErrorActionPreference = 'Stop'
[IO.File]::WriteAllText((Join-Path $attempt 'exit.txt'), "testExit=$testExit`n", $utf8)
$results = Join-Path $Root 'server/build/test-results/integrationTest'
if (Test-Path $results) { Copy-Item (Join-Path $results 'TEST-*.xml') $attempt -ErrorAction SilentlyContinue }
if (Test-Path $results) {
    foreach ($file in Get-ChildItem $results -Filter 'TEST-*.xml') {
        [xml]$xml = Get-Content $file.FullName -Raw
        $failures = [int]$xml.testsuite.failures + [int]$xml.testsuite.errors
        if ($failures -gt 0) {
            $case = $xml.testsuite.testcase | Where-Object { $_.failure } | Select-Object -First 1
            if ($case) {
                "FAILED $($xml.testsuite.name): $($case.name)"
                $case.failure.message -split "`n" | Select-Object -First 6
            }
        }
    }
}
$listening = @(Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue |
    Where-Object { $_.LocalPort -eq $port }).Count
"attempt=$attempt testExit=$testExit listening=$listening"
