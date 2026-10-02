param([Parameter(Mandatory=$true)][ValidateRange(1,999)][int]$Run, [switch]$Android, [switch]$Abort, [switch]$PartialAbort, [switch]$Accounting, [switch]$Confirmation, [switch]$SuccessConsumer, [switch]$Archive, [switch]$Candidates, [switch]$DecisionProtocol, [switch]$ArchiveRecovery, [switch]$Export, [switch]$DraftEvidence, [switch]$UnsubmittedSource, [switch]$JobFence, [switch]$Link, [switch]$HandoverRollback, [string]$Root = 'D:\acrylic-pda-outbound')
$ErrorActionPreference = 'Stop'
$root = $Root
$attempt = Join-Path $root ".local\e2a-cross-20260925-$Run"
$database = "acrylic_e2a_cross_20260925_$Run"
$dbUser = "e2a_cross_20260925_$Run"
$mysqlBin = 'C:\Program Files\MySQL\MySQL Server 8.0\bin'
$port = 19330

function Require-FreePort([int]$Number) {
    if (Get-NetTCPConnection -State Listen -LocalPort $Number -ErrorAction SilentlyContinue) {
        throw "Port $Number is already occupied"
    }
}

function New-PrivateAttempt {
    $resolvedRoot = (Resolve-Path -LiteralPath $root).Path
    if ((Get-Location).Path -ne $resolvedRoot -or [IO.Path]::GetFullPath($attempt) -ne "$resolvedRoot\.local\e2a-cross-20260925-$Run") {
        throw 'Unexpected workspace or attempt path'
    }
    if (Test-Path -LiteralPath $attempt) { throw 'Attempt already exists; preserve it and allocate a new run' }
    $local = Get-Item -LiteralPath (Join-Path $root '.local')
    if ($local.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Refusing redirected local directory' }
    New-Item -ItemType Directory -Path $attempt | Out-Null
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent().Name
    $acl = Get-Acl -LiteralPath $attempt
    $acl.SetAccessRuleProtection($true,$false)
    $rule = [Security.AccessControl.FileSystemAccessRule]::new($identity,'FullControl','ContainerInherit,ObjectInherit','None','Allow')
    $acl.SetAccessRule($rule)
    Set-Acl -LiteralPath $attempt -AclObject $acl
    New-Item -ItemType Directory -Path (Join-Path $attempt 'private'),(Join-Path $attempt 'storage') | Out-Null
}

function Write-PrivateConfiguration {
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    $random = [byte[]]::new(32); $rng.GetBytes($random)
    $script:dbPassword = (($random | ForEach-Object { $_.ToString('x2') }) -join '')
    $rng.GetBytes($random); $rootPassword = (($random | ForEach-Object { $_.ToString('x2') }) -join ''); $rng.Dispose()
    $sql = "ALTER USER 'root'@'localhost' IDENTIFIED BY '$rootPassword';`n"
    $sql += "CREATE DATABASE $database;`nCREATE USER '$dbUser'@'127.0.0.1' IDENTIFIED BY '$dbPassword';`n"
    $sql += "GRANT ALL ON $database.* TO '$dbUser'@'127.0.0.1';`nGRANT SHUTDOWN ON *.* TO '$dbUser'@'127.0.0.1';`n"
    if ($Confirmation -or $JobFence) { $sql += "GRANT SELECT ON performance_schema.* TO '$dbUser'@'127.0.0.1';`n" }
    if ($ArchiveRecovery) { $sql += "CREATE DATABASE $($database)_restore;`nGRANT ALL ON $($database)_restore.* TO '$dbUser'@'127.0.0.1';`n" }
    [IO.File]::WriteAllText((Join-Path $attempt 'private\bootstrap.sql'),$sql)
    $client = "[client]`nuser=$dbUser`npassword=$dbPassword`nhost=127.0.0.1`nport=$port`nprotocol=tcp`n"
    [IO.File]::WriteAllText((Join-Path $attempt 'private\client.cnf'),$client)
}

function Initialize-MySql {
    $args = @('--no-defaults','--initialize-insecure',"--datadir=$attempt/data",'--console')
    $process = Start-Process -FilePath (Join-Path $mysqlBin 'mysqld.exe') -ArgumentList $args -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput (Join-Path $attempt 'private\initialize.out.log') `
        -RedirectStandardError (Join-Path $attempt 'private\initialize.err.log')
    if (-not $process.WaitForExit(60000)) { throw 'Initialization is still running; preserve process and inspect before proceeding' }
    # Windows PowerShell 5.1 leaves Start-Process ExitCode empty, so the durable datadir is the actual check.
    if (-not (Test-Path -LiteralPath (Join-Path $attempt 'data\ibdata1'))) { throw 'MySQL initialization failed; see protected logs' }
}

function Start-IsolatedMySql {
    Require-FreePort $port
    $args = @('--no-defaults',"--datadir=$attempt/data", "--port=$port",'--bind-address=127.0.0.1',
        '--mysqlx=OFF','--skip-name-resolve','--skip-grant-tables=false','--log-bin-trust-function-creators=ON',
        "--init-file=$($attempt.Replace('\','/'))/private/bootstrap.sql",'--console')
    $process = Start-Process -FilePath (Join-Path $mysqlBin 'mysqld.exe') -ArgumentList $args -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput (Join-Path $attempt 'private\server.out.log') `
        -RedirectStandardError (Join-Path $attempt 'private\server.err.log')
    [IO.File]::WriteAllText((Join-Path $attempt 'private\launch-pid.txt'),[string]$process.Id)
    for ($i=0; $i -lt 50; $i++) {
        if (Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue) { return }
        if ($process.HasExited) { throw 'MySQL exited during startup; see protected logs' }
        Start-Sleep -Milliseconds 500
    }
    throw 'MySQL listener did not start'
}

function Read-Identity {
    $sql = 'SELECT @@datadir,@@port,DATABASE(),CURRENT_USER(),@@log_bin,@@log_bin_trust_function_creators'
    $row = & (Join-Path $mysqlBin 'mysql.exe') "--defaults-file=$attempt/private/client.cnf" --batch --raw --skip-column-names $database --execute=$sql 2> (Join-Path $attempt 'private\identity.err.log')
    if ($LASTEXITCODE -ne 0) { throw 'Cannot authenticate isolated database identity' }
    $values = ([string]$row).Split("`t")
    if ($values.Length -ne 6 -or $values[0].Replace('\','/').TrimEnd('/') -ine "$($attempt.Replace('\','/'))/data" `
        -or $values[1] -ne "$port" -or $values[2] -ne $database -or $values[3] -ne "$dbUser@127.0.0.1" `
        -or $values[4] -ne '1' -or $values[5] -ne '1') { throw 'Connected identity mismatch' }
    return [string]$row
}

function Invoke-IsolatedChecks {
    $env:E2A_CROSS_VERIFY = 'true'; $env:E2A_CROSS_RUN = [string]$Run; $env:E2A_CROSS_PASSWORD = $dbPassword
    try {
        $testName = if ($JobFence) { 'E2aJobFenceHttpIT' } elseif ($HandoverRollback) { 'E2aHandoverRollbackIT' } elseif ($Link) { 'E2aTemporaryLinkIT' } elseif ($UnsubmittedSource) { 'E2aUnsubmittedHttpIT' } elseif ($DraftEvidence) { 'E2aDraftEvidenceIT' } elseif ($Export) { 'E2aTemporaryExportIT' } elseif ($ArchiveRecovery) { 'E2aTemporaryArchiveRecoveryIT' } elseif ($DecisionProtocol) { 'E2aTemporaryDecisionProtocolIT' } elseif ($Candidates) { 'E2aTemporaryCandidatesIT' } elseif ($Archive) { 'E2aTemporaryArchiveIT' } elseif ($SuccessConsumer) { 'E2aTemporarySuccessConsumerIT' } elseif ($Confirmation) { 'E2aTemporaryConfirmationIT' } elseif ($Accounting) { 'E2aTemporaryAccountingIT' } elseif ($PartialAbort) { 'E2aAndroidPartialAbortHttpIT' } elseif ($Abort) { 'E2aAndroidAbortHttpIT' } elseif ($Android) { 'E2aAndroidHttpIT' } else { 'E2aCrossSourceHttpIT' }
        $log = Join-Path $attempt 'integration.log'
        & cmd.exe /c "`"$(Join-Path $root 'gradlew.bat')`" -p server integrationTest --tests `"*$testName`" --no-daemon --rerun-tasks > `"$log`" 2>&1"
        $script:testExit = $LASTEXITCODE
        $xml = Join-Path $root "server\build\test-results\integrationTest\TEST-com.acrylic.outbound.e2averify.$testName.xml"
        if (Test-Path -LiteralPath (Join-Path $attempt 'test-started.marker')) {
            if (Test-Path -LiteralPath $xml) { Copy-Item -LiteralPath $xml -Destination (Join-Path $attempt 'integration-result.xml') }
        }
        if ($script:testExit -eq 0 -and -not (Test-Path -LiteralPath (Join-Path $attempt 'test-completed.marker'))) {
            $script:testExit = 2
            [IO.File]::WriteAllText((Join-Path $attempt 'verification-not-executed.txt'), 'Missing one-shot completion marker; a cached/skipped task is not verification.')
        }
        if ($script:testExit -eq 0 -and $JobFence) { Invoke-JobRegression }
    } finally {
        Remove-Item Env:E2A_CROSS_VERIFY,Env:E2A_CROSS_RUN,Env:E2A_CROSS_PASSWORD -ErrorAction SilentlyContinue
    }
}

function Invoke-JobRegression {
    $env:OUTBOUND_DB_URL = "jdbc:mysql://127.0.0.1:$port/$($database)?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
    $env:OUTBOUND_DB_USER = $dbUser; $env:OUTBOUND_DB_PASSWORD = $dbPassword; $env:OUTBOUND_MIGRATE = 'false'
    $env:OUTBOUND_STORAGE = Join-Path $attempt 'storage'
    $env:OUTBOUND_AUTH_SIGNING_PRIVATE_KEY = Join-Path $attempt 'private/server-private.der'
    $env:OUTBOUND_AUTH_SIGNING_PUBLIC_KEY = Join-Path $attempt 'private/server-public.der'
    try {
        & cmd.exe /c "`"$(Join-Path $root 'gradlew.bat')`" -p server integrationTest --tests `"*jobs.BackgroundJob*IT`" --no-daemon --rerun-tasks > `"$(Join-Path $attempt 'job-regression.log')`" 2>&1"
        $script:testExit = $LASTEXITCODE
        $reports = @(Get-ChildItem -LiteralPath (Join-Path $root 'server/build/test-results/integrationTest') -Filter 'TEST-com.acrylic.outbound.jobs.BackgroundJob*IT.xml')
        $count = 0
        foreach ($report in $reports) {
            Copy-Item -LiteralPath $report.FullName -Destination (Join-Path $attempt $report.Name)
            [xml]$parsed = Get-Content -Raw -LiteralPath $report.FullName
            $count += [int]$parsed.testsuite.tests
            if ([int]$parsed.testsuite.skipped -ne 0 -or [int]$parsed.testsuite.failures -ne 0 -or [int]$parsed.testsuite.errors -ne 0) { $script:testExit = 2 }
        }
        if ($count -lt 20 -or $reports.Count -ne 3) { $script:testExit = 2 }
        if ($script:testExit -eq 0) { [IO.File]::WriteAllText((Join-Path $attempt 'job-regression-completed.marker'), "$count actual job regression tests; no skips") }
    } finally {
        Remove-Item Env:OUTBOUND_DB_URL,Env:OUTBOUND_DB_USER,Env:OUTBOUND_DB_PASSWORD,Env:OUTBOUND_MIGRATE,Env:OUTBOUND_STORAGE,Env:OUTBOUND_AUTH_SIGNING_PRIVATE_KEY,Env:OUTBOUND_AUTH_SIGNING_PUBLIC_KEY -ErrorAction SilentlyContinue
    }
}

function Stop-IsolatedMySql {
    $identity = Read-Identity
    [IO.File]::WriteAllText((Join-Path $attempt 'shutdown-identity.tsv'),$identity)
    & (Join-Path $mysqlBin 'mysqladmin.exe') "--defaults-file=$attempt/private/client.cnf" shutdown *> (Join-Path $attempt 'private\shutdown.log')
    if ($LASTEXITCODE -ne 0) { throw 'Authenticated shutdown failed; preserve process for inspection' }
    for ($i=0; $i -lt 40; $i++) {
        if (-not (Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue)) { return }
        Start-Sleep -Milliseconds 250
    }
    throw 'Isolated listener remains after shutdown'
}

Require-FreePort $port
Require-FreePort 19434
Require-FreePort 19435
if ($Candidates) { Require-FreePort 19436 }
New-PrivateAttempt
Write-PrivateConfiguration
Initialize-MySql
$authenticated = $false; $testExit = 1
try {
    Start-IsolatedMySql
    $identity = Read-Identity; $authenticated = $true
    [IO.File]::WriteAllText((Join-Path $attempt 'connected-identity.tsv'),$identity)
    Invoke-IsolatedChecks
} finally {
    if ($authenticated) { Stop-IsolatedMySql }
}
Get-Content -LiteralPath (Join-Path $attempt 'integration.log') -Tail 45
Write-Output "Attempt=$attempt; testExit=$testExit; MySQL stopped through authenticated shutdown"
exit $testExit
