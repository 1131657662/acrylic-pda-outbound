param(
    [string]$Root = 'D:\acrylic-pda-outbound',
    [Parameter(Mandatory = $true)][string]$EvidenceRoot,
    [int]$Port = 19443,
    [int]$TimeWaitLimit = 400,
    [int]$PacingSeconds = 5,
    [int]$SqlPortWarmupSeconds = 30,
    [string]$ClassDirBase = '',
    [string[]]$Only = @(),
    [switch]$Pinned
)
# R09 recovery-chain IT batch runner: one class per invocation, own attempt directory, one aggregate result.
#
# -Pinned targets the instance/schema the test-side guards currently pin (19442 / b12_adv_20260929), resuming
# the preserved datadir when it is down. Without -Pinned it provisions a brand-new isolated instance, which the
# recovery IT guards do NOT yet accept: that hard-coded pin is registered as infrastructure debt.
# The runner refuses (fail closed) rather than reporting host resource exhaustion as a product failure.
$ErrorActionPreference = 'Stop'
$bin = 'C:\Program Files\MySQL\MySQL Server 8.0\bin'
$runnerBuild = Join-Path $Root 'server/build/test-results/integrationTest'

function Write-Evidence([string]$name, [string]$text) {
    Set-Content -LiteralPath (Join-Path $EvidenceRoot $name) -Value $text -Encoding UTF8
}
function Get-TimeWaitCount { return @(netstat -ano | Select-String -Pattern 'TIME_WAIT').Count }
function Get-ListenerCount([int]$p) {
    return @(netstat -ano | Select-String -Pattern (':' + $p + '\s') | Select-String 'LISTENING').Count
}
function New-SecretHex {
    # Windows PowerShell 5.1 / .NET Framework has neither RandomNumberGenerator.GetBytes(int)
    # nor Convert.ToHexString; build both from instance APIs.
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

# ---------------------------------------------------------------- preflight
if (Test-Path -LiteralPath $EvidenceRoot) { throw "Evidence root exists; preserve it: $EvidenceRoot" }
if ((Get-ListenerCount $Port) -gt 0 -and -not $Pinned) { throw "Port $Port already listening" }
$timeWaitBefore = Get-TimeWaitCount
if ($timeWaitBefore -gt $TimeWaitLimit) {
    throw "Host has $timeWaitBefore TIME_WAIT sockets (> $TimeWaitLimit): wait for decay before a full batch, or the run will fail for host reasons, not product reasons."
}
New-Item -ItemType Directory -Path $EvidenceRoot | Out-Null
if (-not $ClassDirBase) { $ClassDirBase = $EvidenceRoot }
$dynamic = (netsh int ipv4 show dynamicport tcp | Select-String 'Number of Ports')

if ($Pinned) {
    $Port = 19442
    $schema = 'b12_adv_20260929'
    $account = $schema
    $instance = Join-Path $Root '.local/mysql19442-b12-adversarial'
    if (-not (Test-Path -LiteralPath (Join-Path $instance 'data/mysql'))) { throw "Pinned datadir missing: $instance" }
    $settings = Get-Content -LiteralPath (Join-Path $instance 'settings.json') -Raw | ConvertFrom-Json
    $password = $settings.password
    $started = $null
    if ((Get-ListenerCount $Port) -eq 0) {
        $started = Start-Isolated (Join-Path $instance 'data') (Join-Path $instance 'mysqld.log') $null `
            (Join-Path $instance 'private/batch-server.out') (Join-Path $instance 'private/batch-server.err')
        $started.Id | Set-Content -LiteralPath (Join-Path $EvidenceRoot 'pinned-server-pid.txt') -Encoding UTF8
    }
} else {
    $instance = Join-Path $Root ('.local/mysql' + $Port + '-r09batch')
    $schema = 'r09_batch_' + (Get-Date -Format 'yyyyMMdd')
    $account = $schema
    if (Test-Path -LiteralPath $instance) { throw "Instance directory exists; preserve it: $instance" }
    New-Item -ItemType Directory -Path (Join-Path $instance 'data'), (Join-Path $instance 'private') | Out-Null
    $sid = [Security.Principal.WindowsIdentity]::GetCurrent().User.Value
    & icacls $instance /inheritance:r /grant:r "*${sid}:(OI)(CI)F" '*S-1-5-18:(OI)(CI)F' | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Private ACL failed' }
    $encoding = [Text.UTF8Encoding]::new($false)
    $password = New-SecretHex
    $rootPassword = New-SecretHex
    @{ user = $account; password = $password; port = $Port; datadir = "$instance/data" } |
        ConvertTo-Json | Set-Content -LiteralPath (Join-Path $instance 'settings.json') -Encoding UTF8
    $sql = "ALTER USER 'root'@'localhost' IDENTIFIED BY '$rootPassword';`nCREATE DATABASE $schema;`n" +
        "CREATE USER '$account'@'127.0.0.1' IDENTIFIED BY '$password';`nGRANT ALL ON $schema.* TO '$account'@'127.0.0.1';`n"
    [IO.File]::WriteAllText((Join-Path $instance 'private/init.sql'), $sql, $encoding)
    [IO.File]::WriteAllText((Join-Path $instance 'private/admin.cnf'),
        "[client]`nuser=root`npassword=$rootPassword`nhost=127.0.0.1`nport=$Port`nprotocol=tcp`n", $encoding)
    $init = Start-Process "$bin/mysqld.exe" -WindowStyle Hidden -PassThru -ArgumentList @(
        '--no-defaults', '--initialize-insecure', "--datadir=$instance/data", '--mysqlx=OFF') `
        -RedirectStandardOutput (Join-Path $instance 'private/initialize.out') -RedirectStandardError (Join-Path $instance 'private/initialize.err')
    if (-not $init.WaitForExit(45000)) { throw 'Initialization not complete; preserve process and logs' }
    # Windows PowerShell can leave ExitCode null on a PassThru process object: prove success by the
    # initialized system schema rather than trusting a possibly-null exit code.
    $initCode = $null
    try { $initCode = $init.ExitCode } catch { $initCode = $null }
    if ($null -ne $initCode -and $initCode -ne 0) { throw "Initialization failed (exit $initCode); inspect retained logs" }
    if (-not (Test-Path -LiteralPath (Join-Path $instance 'data/mysql'))) {
        throw 'Initialization produced no system schema; inspect retained logs'
    }
    Start-Isolated (Join-Path $instance 'data') (Join-Path $instance 'mysqld.log') (Join-Path $instance 'private/init.sql') `
        (Join-Path $instance 'private/server.out') (Join-Path $instance 'private/server.err') | Out-Null
}
$identity = & "$bin/mysql.exe" "--defaults-file=$instance/private/admin.cnf" -N -e 'SELECT @@port, @@datadir, @@version;' 2>&1
Write-Evidence 'preflight.txt' @"
pinned=$Pinned
port=$Port
schema=$account
instance=$instance
timeWaitBefore=$timeWaitBefore (limit $TimeWaitLimit)
$dynamic
"@
Write-Evidence 'instance-identity.txt' ($identity -join ' | ')

# ---------------------------------------------------------------- batch
$classes = @(
    'RecoveryEffectBatchIT', 'RecoveryEffectCommitIT', 'RecoveryEffectCursorIT', 'RecoveryEffectSqlIT',
    'RecoveryEffectAdversarialBatchIT', 'RecoveryEffectAdversarialCallableIT', 'RecoveryEffectAdversarialCleanupIT',
    'RecoveryEffectAdversarialDefaultOffIT', 'RecoveryEffectAdversarialDriverIT', 'RecoveryEffectAdversarialFenceIT',
    'RecoveryEffectAdversarialHandlesIT', 'RecoveryEffectAdversarialLobIT', 'RecoveryEffectAdversarialMetadataIT',
    'RecoveryEffectAdversarialPoolIT', 'RecoveryEffectAdversarialSqlSurfaceIT', 'RecoveryEffectAdversarialViewIT',
    'RecoveryFenceAdversarialDurableIT', 'RecoveryFenceConnectionIT', 'RecoveryFenceCrossProcessIT',
    'RecoveryFenceIntegrityIT', 'RecoveryFenceStartupIT', 'RecoveryFenceUnknownIT',
    'G0K1RealFileLockIT', 'G0K1WriterLeaseIdentityRealIT'
)
$notAttempted = @(
    @{ name = 'C1InitialNormalHttpIT'; reason = 'needs a real HTTP/Session stack and its own environment' },
    @{ name = 'G0K1RealJdbcBoundaryIT'; reason = 'its fail-closed guard pins the JDBC URL to that round schema/port 19438' }
)
if ($Only.Count -gt 0) { $classes = $Only }
$env:GIT_OPTIONAL_LOCKS = '0'
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.11'
$env:OUTBOUND_DB_URL = "jdbc:mysql://127.0.0.1:$Port/${schema}?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8"
$env:OUTBOUND_DB_USER = $account
$env:OUTBOUND_DB_PASSWORD = $password
$rows = @()
$index = 0
foreach ($class in $classes) {
    $index++
    $fqcn = 'com.acrylic.outbound.recovery.' + $class
    # The recovery IT guards require a direct child of the pinned base whose name matches run<N>-<slug>.
    $target = Join-Path $ClassDirBase ('run{0}-batch-{1}' -f (100 + $index), $class.ToLower())
    New-Item -ItemType Directory -Path $target | Out-Null
    $env:B11_EFFECT_EVIDENCE_DIR = $target
    $env:B12_FENCE_EVIDENCE_DIR = $target
    $env:G0K1_EVIDENCE_DIR = $target
    $arguments = @('-p', 'server', 'recoveryControlClasspath', 'integrationTest', '--rerun-tasks', '--no-daemon',
        '--console=plain', '--tests', $fqcn)
    ($arguments -join ' ') | Set-Content -LiteralPath (Join-Path $target 'command.txt') -Encoding UTF8
    $twBefore = Get-TimeWaitCount
    $started = Get-Date
    $ErrorActionPreference = 'Continue'
    & (Join-Path $Root 'gradlew.bat') @arguments *> (Join-Path $target 'gradle.log')
    $code = $LASTEXITCODE
    $ErrorActionPreference = 'Stop'
    $elapsed = [int]((Get-Date) - $started).TotalSeconds
    $twAfter = Get-TimeWaitCount
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
    $rows += [pscustomobject][ordered]@{ index = $index; class = $class; exit = $code; tests = $tests; failures = $failures;
        errors = $errors; skipped = $skipped; seconds = $elapsed; timeWaitBefore = $twBefore; timeWaitAfter = $twAfter }
    Write-Output ('[{0:d2}/{1}] {2} exit={3} tests={4} failures={5} errors={6} skipped={7} {8}s tw={9}->{10}' -f
        $index, $classes.Count, $class, $code, $tests, $failures, $errors, $skipped, $elapsed, $twBefore, $twAfter)
    Start-Sleep -Seconds $PacingSeconds
}

# ---------------------------------------------------------------- aggregate and shutdown
$totalTests = ($rows | Measure-Object -Property tests -Sum).Sum
$totalFailures = ($rows | Measure-Object -Property failures -Sum).Sum
$totalErrors = ($rows | Measure-Object -Property errors -Sum).Sum
$totalSkipped = ($rows | Measure-Object -Property skipped -Sum).Sum
$failedClasses = @($rows | Where-Object { $_.exit -ne 0 })
$summary = [ordered]@{
    time = (Get-Date).ToString('o'); pinned = [bool]$Pinned; port = $Port; schema = $schema; account = $account
    instance = $instance; classesAttempted = $classes.Count; totalTests = $totalTests
    totalFailures = $totalFailures; totalErrors = $totalErrors; totalSkipped = $totalSkipped
    timeWaitLimit = $TimeWaitLimit; timeWaitBefore = $timeWaitBefore; timeWaitAfter = (Get-TimeWaitCount)
    failedClasses = @($failedClasses | ForEach-Object { $_.class }); notAttempted = $notAttempted; rows = $rows
}
$summary | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $EvidenceRoot 'batch-summary.json') -Encoding UTF8
$rows | Format-Table -AutoSize | Out-String -Width 400 | Set-Content -LiteralPath (Join-Path $EvidenceRoot 'batch-summary.txt') -Encoding UTF8
$shutdown = & "$bin/mysqladmin.exe" "--defaults-file=$instance/private/admin.cnf" shutdown 2>&1
Start-Sleep -Seconds 3
Write-Evidence 'db-shutdown.txt' ("exit=$LASTEXITCODE`n" + ($shutdown -join ' ') + "`nport_listening_after=" + (Get-ListenerCount $Port))
Write-Output ('BATCH pinned={0} classes={1} tests={2} failures={3} errors={4} skipped={5} failedClasses={6} portReleased={7}' -f
    [bool]$Pinned, $classes.Count, $totalTests, $totalFailures, $totalErrors, $totalSkipped, $failedClasses.Count,
    ((Get-ListenerCount $Port) -eq 0))
if ($failedClasses.Count -gt 0) { exit 1 }
exit 0
