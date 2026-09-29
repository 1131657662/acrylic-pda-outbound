param(
    [string]$Root = 'D:\acrylic-pda-outbound',
    [Parameter(Mandatory = $true)][string]$EvidenceRoot,
    [int]$Port = 19454,
    [int]$AppPort = 19458,
    [int]$Attempt = 1,
    [string]$Label = 's3-session',
    [int]$SqlPortWarmupSeconds = 30,
    [switch]$Shutdown
)
# R09 C2 B1-3 S3 runner: one isolated MySQL instance kept up across attempts, one evidence directory
# per attempt, one real-HTTP Spring Session root test class.
#
# -Shutdown stops the instance (normal mysqladmin shutdown) and prints the released-port fact.
# Without -Shutdown a missing instance is provisioned (fresh datadir, schema=account) and reused by
# later attempts on the same port, so an iteration only costs one Gradle IT run.
$ErrorActionPreference = 'Stop'
$bin = 'C:\Program Files\MySQL\MySQL Server 8.0\bin'
$instance = Join-Path $Root ('.local/mysql' + $Port + '-s3session')
$schema = 'r09_s3_session_' + $Port
$runnerBuild = Join-Path $Root 'server/build/test-results/integrationTest'
$testClass = 'com.acrylic.outbound.recovery.RecoveryRequestRootHttpIT'

function Get-ListenerCount([int]$p) {
    return @(netstat -ano | Select-String -Pattern (':' + $p + '\s') | Select-String 'LISTENING').Count
}
function New-SecretHex {
    $bytes = New-Object byte[] 32
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    $text = New-Object System.Text.StringBuilder
    foreach ($value in $bytes) { [void]$text.Append($value.ToString('x2')) }
    return $text.ToString()
}
function Start-Isolated([string]$dataDir, [string]$logError, [string]$initFile, [string]$outFile, [string]$errFile) {
    $arguments = @('--no-defaults', "--datadir=$dataDir", "--port=$Port", '--bind-address=127.0.0.1', '--mysqlx=OFF',
        '--skip-log-bin', "--log-error=$logError")
    if ($initFile) { $arguments += "--init-file=$initFile" }
    $process = Start-Process "$bin/mysqld.exe" -WindowStyle Hidden -PassThru -ArgumentList $arguments `
        -RedirectStandardOutput $outFile -RedirectStandardError $errFile
    $deadline = (Get-Date).AddSeconds($SqlPortWarmupSeconds)
    while ((Get-ListenerCount $Port) -eq 0 -and (Get-Date) -lt $deadline) { Start-Sleep -Milliseconds 500 }
    if ((Get-ListenerCount $Port) -eq 0) { throw "Isolated server did not start listening on $Port" }
    return $process
}

# ---------------------------------------------------------------- shutdown mode
if ($Shutdown) {
    if (-not (Test-Path -LiteralPath (Join-Path $instance 'private/admin.cnf'))) { throw "No instance to shut down: $instance" }
    $shutdown = & "$bin/mysqladmin.exe" "--defaults-file=$instance/private/admin.cnf" shutdown 2>&1
    Start-Sleep -Seconds 3
    Write-Output ("shutdown exit=$LASTEXITCODE listening=" + (Get-ListenerCount $Port) + " output=" + ($shutdown -join ' '))
    exit 0
}

# ---------------------------------------------------------------- instance
if (-not (Test-Path -LiteralPath (Join-Path $instance 'data/mysql'))) {
    if (Test-Path -LiteralPath $instance) { throw "Instance directory exists but is not initialized; preserve it: $instance" }
    New-Item -ItemType Directory -Path (Join-Path $instance 'data'), (Join-Path $instance 'private') | Out-Null
    $sid = [Security.Principal.WindowsIdentity]::GetCurrent().User.Value
    & icacls $instance /inheritance:r /grant:r "*${sid}:(OI)(CI)F" '*S-1-5-18:(OI)(CI)F' | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Private ACL failed' }
    $encoding = [Text.UTF8Encoding]::new($false)
    $password = New-SecretHex
    $rootPassword = New-SecretHex
    @{ user = $schema; password = $password; port = $Port; datadir = "$instance/data" } |
        ConvertTo-Json | Set-Content -LiteralPath (Join-Path $instance 'settings.json') -Encoding UTF8
    $sql = "ALTER USER 'root'@'localhost' IDENTIFIED BY '$rootPassword';`nCREATE DATABASE $schema;`n" +
        "CREATE USER '$schema'@'127.0.0.1' IDENTIFIED BY '$password';`nGRANT ALL ON $schema.* TO '$schema'@'127.0.0.1';`n"
    [IO.File]::WriteAllText((Join-Path $instance 'private/init.sql'), $sql, $encoding)
    [IO.File]::WriteAllText((Join-Path $instance 'private/admin.cnf'),
        "[client]`nuser=root`npassword=$rootPassword`nhost=127.0.0.1`nport=$Port`nprotocol=tcp`n", $encoding)
    $init = Start-Process "$bin/mysqld.exe" -WindowStyle Hidden -PassThru -ArgumentList @(
        '--no-defaults', '--initialize-insecure', "--datadir=$instance/data", '--mysqlx=OFF') `
        -RedirectStandardOutput (Join-Path $instance 'private/initialize.out') -RedirectStandardError (Join-Path $instance 'private/initialize.err')
    if (-not $init.WaitForExit(45000)) { throw 'Initialization not complete; preserve process and logs' }
    if (-not (Test-Path -LiteralPath (Join-Path $instance 'data/mysql'))) {
        throw 'Initialization produced no system schema; inspect retained logs'
    }
}
if ((Get-ListenerCount $Port) -eq 0) {
    Start-Isolated (Join-Path $instance 'data') (Join-Path $instance 'mysqld.log') (Join-Path $instance 'private/init.sql') `
        (Join-Path $instance 'private/server.out') (Join-Path $instance 'private/server.err') | Out-Null
}
$settings = Get-Content -LiteralPath (Join-Path $instance 'settings.json') -Raw | ConvertFrom-Json
$password = $settings.password

# ---------------------------------------------------------------- attempt
$target = Join-Path $EvidenceRoot ('run{0}-{1}' -f $Attempt, $Label)
if (Test-Path -LiteralPath $target) { throw "Attempt directory exists; preserve it: $target" }
New-Item -ItemType Directory -Path $target | Out-Null
$env:GIT_OPTIONAL_LOCKS = '0'
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.11'
$env:OUTBOUND_DB_URL = "jdbc:mysql://127.0.0.1:$Port/${schema}?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8"
$env:OUTBOUND_DB_USER = $schema
$env:OUTBOUND_DB_PASSWORD = $password
$env:B11_EFFECT_EVIDENCE_DIR = $target
$env:S3_HTTP_PORT = "$AppPort"
$arguments = @('-p', 'server', 'recoveryControlClasspath', 'integrationTest', '--rerun-tasks', '--no-daemon',
    '--console=plain', '--tests', $testClass)
($arguments -join ' ') | Set-Content -LiteralPath (Join-Path $target 'command.txt') -Encoding UTF8
@"
port=$Port
schema=$schema
instance=$instance
appPort=$AppPort
testClass=$testClass
"@ | Set-Content -LiteralPath (Join-Path $target 'coordinates.txt') -Encoding UTF8
$started = Get-Date
$ErrorActionPreference = 'Continue'
& (Join-Path $Root 'gradlew.bat') @arguments *> (Join-Path $target 'gradle.log')
$code = $LASTEXITCODE
$ErrorActionPreference = 'Stop'
Set-Content -LiteralPath (Join-Path $target 'exit.txt') -Value $code -Encoding UTF8
$xml = @(Get-ChildItem $runnerBuild -Filter 'TEST-*.xml' -ErrorAction SilentlyContinue |
    Where-Object { $_.LastWriteTime -ge $started })
foreach ($file in $xml) { Copy-Item -LiteralPath $file.FullName -Destination $target }
$tests = 0; $failures = 0; $errors = 0; $skipped = 0
foreach ($file in $xml) {
    [xml]$doc = Get-Content -LiteralPath $file.FullName -Raw
    $tests += [int]$doc.testsuite.tests; $failures += [int]$doc.testsuite.failures
    $errors += [int]$doc.testsuite.errors; $skipped += [int]$doc.testsuite.skipped
}
Get-ChildItem $target -File -Recurse | Get-FileHash -Algorithm SHA256 |
    Format-Table -AutoSize | Out-String -Width 400 | Set-Content -LiteralPath (Join-Path $target 'files.sha256') -Encoding UTF8
Write-Output ('[attempt {0}] exit={1} tests={2} failures={3} errors={4} skipped={5} seconds={6}' -f
    $Attempt, $code, $tests, $failures, $errors, $skipped, [int]((Get-Date) - $started).TotalSeconds)
Write-Output ('instance still listening on ' + $Port + ' = ' + ((Get-ListenerCount $Port) -gt 0) + '; use -Shutdown to release it')
if ($code -ne 0) { exit 1 }
exit 0
