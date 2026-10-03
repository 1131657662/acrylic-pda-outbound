param([Parameter(Mandatory=$true)][ValidateRange(1,999)][int]$Run)
$ErrorActionPreference = 'Stop'
$root = 'D:\acrylic-pda-outbound'
$attempt = Join-Path $root ".local\e2a-cross-20260925-$Run"
$database = "acrylic_e2a_cross_20260925_$Run"
$dbUser = "e2a_cross_20260925_$Run"
$mysqlBin = 'C:\Program Files\MySQL\MySQL Server 8.0\bin'
$port = 19330

function Assert-Environment {
    if ((Get-Location).Path -ne (Resolve-Path -LiteralPath $root).Path) { throw 'Unexpected workspace' }
    if ((Get-PSDrive D).Free -lt 20GB) { throw 'D: free space below 20 GB' }
    if (Test-Path -LiteralPath $attempt) { throw 'Attempt already exists; preserve it and choose a new run' }
    $local = Get-Item -LiteralPath (Join-Path $root '.local')
    if ($local.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Refusing redirected local directory' }
    foreach ($number in @(19330,19434,19435,19436)) {
        if (Get-NetTCPConnection -State Listen -LocalPort $number -ErrorAction SilentlyContinue) {
            throw "Port $number is already occupied"
        }
    }
}

function New-PrivateAttempt {
    New-Item -ItemType Directory -Path $attempt | Out-Null
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent().Name
    $acl = Get-Acl -LiteralPath $attempt
    $acl.SetAccessRuleProtection($true,$false)
    $acl.SetAccessRule([Security.AccessControl.FileSystemAccessRule]::new(
        $identity,'FullControl','ContainerInherit,ObjectInherit','None','Allow'))
    Set-Acl -LiteralPath $attempt -AclObject $acl
    New-Item -ItemType Directory -Path (Join-Path $attempt 'private'),(Join-Path $attempt 'storage') | Out-Null
}

function Write-PrivateConfiguration {
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    $random = [byte[]]::new(32)
    $rng.GetBytes($random)
    $script:dbPassword = (($random | ForEach-Object { $_.ToString('x2') }) -join '')
    $rng.GetBytes($random)
    $rootPassword = (($random | ForEach-Object { $_.ToString('x2') }) -join '')
    $rng.Dispose()
    $sql = "ALTER USER 'root'@'localhost' IDENTIFIED BY '$rootPassword';`n"
    $sql += "CREATE DATABASE $database;`nCREATE USER '$dbUser'@'127.0.0.1' IDENTIFIED BY '$dbPassword';`n"
    $sql += "GRANT ALL ON $database.* TO '$dbUser'@'127.0.0.1';`nGRANT SHUTDOWN ON *.* TO '$dbUser'@'127.0.0.1';`n"
    [IO.File]::WriteAllText((Join-Path $attempt 'private/bootstrap.sql'),$sql)
    $client = "[client]`nuser=$dbUser`npassword=$dbPassword`nhost=127.0.0.1`nport=$port`nprotocol=tcp`n"
    [IO.File]::WriteAllText((Join-Path $attempt 'private/client.cnf'),$client)
}

function Start-Database {
    $args = @('--no-defaults','--initialize-insecure',"--datadir=$attempt/data",'--console')
    $init = Start-Process -FilePath (Join-Path $mysqlBin 'mysqld.exe') -ArgumentList $args -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput (Join-Path $attempt 'private/initialize.out.log') `
        -RedirectStandardError (Join-Path $attempt 'private/initialize.err.log')
    if (-not $init.WaitForExit(60000) -or -not (Test-Path -LiteralPath (Join-Path $attempt 'data/ibdata1'))) {
        throw 'MySQL initialization failed; preserve attempt'
    }
    $args = @('--no-defaults',"--datadir=$attempt/data","--port=$port",'--bind-address=127.0.0.1',
        '--mysqlx=OFF','--skip-name-resolve','--skip-grant-tables=false','--log-bin-trust-function-creators=ON',
        "--init-file=$($attempt.Replace('\','/'))/private/bootstrap.sql",'--console')
    $script:mysqlProcess = Start-Process -FilePath (Join-Path $mysqlBin 'mysqld.exe') -ArgumentList $args -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput (Join-Path $attempt 'private/server.out.log') `
        -RedirectStandardError (Join-Path $attempt 'private/server.err.log')
    [IO.File]::WriteAllText((Join-Path $attempt 'private/launch-pid.txt'),[string]$script:mysqlProcess.Id)
    for ($i=0; $i -lt 60; $i++) {
        if (Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue) { return }
        if ($script:mysqlProcess.HasExited) { throw 'MySQL exited during startup' }
        Start-Sleep -Milliseconds 500
    }
    throw 'Isolated database did not start'
}

function Read-Identity {
    $sql = 'SELECT @@datadir,@@port,DATABASE(),CURRENT_USER(),@@log_bin,@@log_bin_trust_function_creators'
    $row = & (Join-Path $mysqlBin 'mysql.exe') "--defaults-file=$attempt/private/client.cnf" `
        --batch --raw --skip-column-names $database --execute=$sql 2> (Join-Path $attempt 'private/identity.err.log')
    if ($LASTEXITCODE -ne 0) { throw 'Cannot authenticate isolated database identity' }
    $values = ([string]$row).Split("`t")
    if ($values.Length -ne 6 -or $values[0].Replace('\','/').TrimEnd('/') -ine "$($attempt.Replace('\','/'))/data" `
        -or $values[1] -ne "$port" -or $values[2] -ne $database -or $values[3] -ne "$dbUser@127.0.0.1" `
        -or $values[4] -ne '1' -or $values[5] -ne '1') { throw 'Connected identity mismatch' }
    return [string]$row
}

function Run-Probe {
    $env:E2A_CROSS_VERIFY = 'true'
    $env:E2A_CROSS_RUN = [string]$Run
    $env:E2A_CROSS_PASSWORD = $script:dbPassword
    try {
        $log = Join-Path $attempt 'integration.log'
        $command = 'gradlew.bat -p server integrationTest --tests *E2aHandoverPdaManifestIT --no-daemon --rerun-tasks > "' + $log + '" 2>&1'
        & cmd.exe /c $command
        if ($LASTEXITCODE -ne 0) { throw 'Integration test failed; inspect retained log' }
        $xml = Join-Path $root 'server/build/test-results/integrationTest/TEST-com.acrylic.outbound.e2averify.E2aHandoverPdaManifestIT.xml'
        Copy-Item -LiteralPath $xml -Destination (Join-Path $attempt 'integration-result.xml')
        [xml]$report = Get-Content -Raw -LiteralPath $xml
        if ([int]$report.testsuite.tests -ne 1 -or [int]$report.testsuite.failures -ne 0 `
            -or [int]$report.testsuite.errors -ne 0 -or [int]$report.testsuite.skipped -ne 0) {
            throw 'Integration result is not 1/0/0/0'
        }
        foreach ($marker in @('test-started.marker','test-completed.marker')) {
            if (-not (Test-Path -LiteralPath (Join-Path $attempt $marker))) { throw "Missing $marker" }
        }
    } finally {
        Remove-Item Env:E2A_CROSS_VERIFY,Env:E2A_CROSS_RUN,Env:E2A_CROSS_PASSWORD -ErrorAction SilentlyContinue
    }
}

function Stop-Database {
    [IO.File]::WriteAllText((Join-Path $attempt 'shutdown-identity.tsv'),(Read-Identity))
    & (Join-Path $mysqlBin 'mysqladmin.exe') "--defaults-file=$attempt/private/client.cnf" shutdown `
        *> (Join-Path $attempt 'private/shutdown.log')
    if ($LASTEXITCODE -ne 0) { throw 'Authenticated shutdown failed' }
    for ($i=0; $i -lt 40; $i++) {
        if (-not (Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue)) { return }
        Start-Sleep -Milliseconds 250
    }
    throw 'Isolated database remains online'
}

Assert-Environment
New-PrivateAttempt
[IO.File]::WriteAllText((Join-Path $attempt 'disk-before.txt'),[string](Get-PSDrive D).Free)
Write-PrivateConfiguration
Start-Database
$authenticated = $false
try {
    [IO.File]::WriteAllText((Join-Path $attempt 'connected-identity.tsv'),(Read-Identity))
    $authenticated = $true
    Run-Probe
} finally {
    if ($authenticated) { Stop-Database }
    [IO.File]::WriteAllText((Join-Path $attempt 'disk-after.txt'),[string](Get-PSDrive D).Free)
}
Write-Output "Attempt=$attempt; result=1/0/0/0; MySQL stopped"
