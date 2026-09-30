#requires -Version 5.1
<#
.SYNOPSIS
  R08 专用库构造器：为 R08 的 8 类集成测试起一个**独立的空 MySQL**，跑指定 IT，报告结果，收尾停库。

.DESCRIPTION
  与 tools/e2a-provision-and-run-106.ps1 同一套已验证构造，只把"库坐标"参数化：
    - 端口默认 23308、库名默认 acrylic_r08_it
      （R08 的 8 个 IT 各自带环境守卫，只认 127.0.0.1:23308/acrylic_r08_it 或 *-preview；
       R08PreviewSeedIT 另需 acrylic_r08_preview —— 用 -DatabasePrefix 指定即可）。
    - attempt 目录独立序列 .local/r08-it-<Run>（默认 R08-1 起），**不与 e2a 的 attempt 号混用**。
  步骤（每步显式判退出码，失败即停并打印原始错误）：
    1. 停掉占用该端口的 mysqld / 属于本 attempt 目录的残留 mysqld
    2. mysqld --initialize-insecure 到 attempt 数据目录
    3. 起 mysqld（--log-bin-trust-function-creators=ON）
    4. 用**真实连接**（mysql -uroot 无密码 SELECT 1）等就绪，不用 mysqladmin ping
    5. 建库、建用户、授权
    6. 生成 64 位十六进制随机密码写入 attempt 目录，并导出运行环境
    7. gradlew -p server integrationTest --tests <glob> --rerun-tasks
  不传 -DatabasePrefix 时用的库名/用户名与 R08 守卫所要求的一致；**不改动任何 R08 测试守卫**。

.PARAMETER NonE2a
  R08 的 IT 是 @SpringBootTest + MockMvc（应用自己的数据源），所以必须走已验证的 -NonE2a 通道：
  导出 OUTBOUND_DB_URL/USER/PASSWORD + OUTBOUND_MIGRATE=true（应用自迁移到最新 schema）
  + OUTBOUND_AUTH_SIGNING_*（.local/it-keys/ 一次性测试密钥）+ OUTBOUND_STORAGE。

.PARAMETER RendererHome
  导出类 IT 会起渲染子进程，需要指向已构建的 report-rendering 安装目录（默认自动探测主树路径）。

.PARAMETER Browsers
  PDF 渲染所需的 Chromium 目录（默认自动探测 r08-export 工作树里的 playwright browsers）。

.EXAMPLE
  powershell -NoProfile -ExecutionPolicy Bypass -File tools/r08-provision-and-run.ps1 -Run 1 -Tests '*R08ExportIT'
#>
[CmdletBinding()]
param(
    [int]$Run = 1,
    [string]$Tests = '*R08ExportIT',
    [string]$Workspace = 'D:\acrylic-pda-outbound',
    [string]$MySqlBase = 'C:\Program Files\MySQL\MySQL Server 8.0',
    [int]$Port = 23308,
    [string]$DatabasePrefix = 'acrylic_r08_it',
    [string]$SigningDir = 'D:\acrylic-pda-outbound\.local\it-keys',
    [string]$RendererHome = 'D:\acrylic-pda-outbound\report-rendering\build\install\acrylic-report-rendering',
    [string]$Browsers = 'D:\acrylic-pda-worktrees\r08-export\report-rendering\build\browsers',
    # R08 ITs assert the TEST_SAMPLE provenance (synthetic sample data); the export entry only sets it when
    # outbound.exports.sample-data is on, exactly as the e2a export fixture passes it. Default on for this harness.
    [bool]$SampleData = $true,
    # R08 的环境守卫按"库名前缀"比对（R08CenterFixture:34 / R08ExportIT:27 要求 URL 以
    # jdbc:mysql://127.0.0.1:23308/acrylic_r08_it? 开头），所以默认**不加 attempt 后缀**，库名严格等于
    # -DatabasePrefix。每次运行会 DROP+CREATE 同名库，隔离由"独立 mysqld 实例 + 独立数据目录"保证。
    [bool]$SuffixDatabaseWithRun = $false,
    # R08 的每个类都必须跑在**全新库**上：R08CenterFixture.<clinit> 的守卫在库名恒定、且上一类已把库写成
    # 非初始状态时会抛 "R08 center tests require the dedicated integration database"（实测 R08-4 批量 29 项红）。
    # 传 -DatabaseSuffix <类名> 让库名带上后缀（仍以 acrylic_r08_it 开头，守卫按前缀比对可通过）。
    [string]$DatabaseSuffix = '',
    [switch]$KeepServerRunning
)

$ErrorActionPreference = 'Continue'
$utf8 = New-Object System.Text.UTF8Encoding($false)
$attempt = Join-Path $Workspace (".local/r08-it-$Run")
$data = Join-Path $attempt 'data'
$log = Join-Path $attempt 'mysqld.log'
$initLog = Join-Path $attempt 'initialize.log'
$passwordFile = Join-Path $attempt 'run-password.txt'
$runLog = Join-Path $attempt 'integration-test.log'
$database = if ($SuffixDatabaseWithRun) { "${DatabasePrefix}_$Run" } else { $DatabasePrefix }
if (-not [string]::IsNullOrWhiteSpace($DatabaseSuffix)) { $database = "${database}_$DatabaseSuffix" }
$user = if ($SuffixDatabaseWithRun) { "r08_it_$Run" } else { 'r08_it' }
if (-not [string]::IsNullOrWhiteSpace($DatabaseSuffix)) { $user = "${user}_$DatabaseSuffix" }
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
if (-not (Test-Path -LiteralPath $RendererHome)) { Write-Host "warning: renderer home not found: $RendererHome (导出类 IT 会失败)" -ForegroundColor Yellow }
if (-not (Test-Path -LiteralPath $Browsers)) { Write-Host "warning: browsers dir not found: $Browsers (PDF 渲染会失败)" -ForegroundColor Yellow }
New-Item -ItemType Directory -Force -Path $attempt, (Join-Path $attempt 'private'), (Join-Path $attempt 'storage') | Out-Null
if (Test-Path -LiteralPath (Join-Path $attempt 'test-started.marker')) {
    Fail 're-run guard' "attempt $Run is already used (test-started.marker exists). Use a new -Run number."
}

# ---------------------------------------------------------------- 1) free the port
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
$check = & $mysql --no-defaults '-h127.0.0.1' "-P$Port" "-u$user" "-p$password" $database -e 'SELECT @@port,DATABASE(),CURRENT_USER();' 2>&1
if ($LASTEXITCODE -ne 0) { Fail 'verify new user' ($check -join "`n") }
$check | ForEach-Object { Write-Host "  $_" }
$tables = @(& $mysql --no-defaults '-h127.0.0.1' "-P$Port" "-u$user" "-p$password" $database -N -B -e "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='$database';" 2>$null)
$tableCount = ($tables | Where-Object { $_ -match '^\d+$' } | Select-Object -Last 1)
if ($tableCount -ne '0') { Fail 'verify empty schema' "expected 0 tables, found '$tableCount' (raw: $($tables -join '|'))" }

# ---------------------------------------------------------------- 7) run the IT
Write-Host "== 7) run R08 integrationTest ($Tests) =="
# -NonE2a channel (already verified in the e2a harness): the R08 ITs are @SpringBootTest + MockMvc and resolve the
# application's own datasource, so the isolated database is exported and the app is allowed to migrate it.
$env:OUTBOUND_DB_URL = "jdbc:mysql://127.0.0.1:$Port/$database`?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
$env:OUTBOUND_DB_USER = $user
$env:OUTBOUND_DB_PASSWORD = $password
$env:OUTBOUND_MIGRATE = 'true'
$env:OUTBOUND_COOKIE_SECURE = 'false'
$env:OUTBOUND_AUTH_SIGNING_PRIVATE_KEY = Join-Path $SigningDir 'server-private.der'
$env:OUTBOUND_AUTH_SIGNING_PUBLIC_KEY = Join-Path $SigningDir 'server-public.der'
$env:OUTBOUND_STORAGE = Join-Path $attempt 'storage'
New-Item -ItemType Directory -Force -Path $env:OUTBOUND_STORAGE | Out-Null
if (-not (Test-Path -LiteralPath $env:OUTBOUND_AUTH_SIGNING_PRIVATE_KEY)) {
    Fail 'signing key' "missing $($env:OUTBOUND_AUTH_SIGNING_PRIVATE_KEY); generate with .local/it-keys/GenKeys.java"
}
Push-Location $Workspace
$runStartedAt = Get-Date
try {
    $testArgs = if ([string]::IsNullOrWhiteSpace($Tests) -or $Tests -in @('*','ALL')) { @() } else { @('--tests', $Tests) }
    # Export ITs start a renderer subprocess. `@SpringBootTest` does not read Gradle program arguments, and Gradle's
    # own -D goes to the *Gradle* JVM, not the test JVM - so the renderer is configured through Spring's relaxed
    # binding of environment variables (OUTBOUND_EXPORTS_RENDERER_HOME -> outbound.exports.renderer-home). Without it
    # the export POST answers 503 EXPORT_UNAVAILABLE (the documented R08 precondition, not a product defect).
    $env:OUTBOUND_EXPORTS_RENDERER_HOME = $RendererHome
    $env:OUTBOUND_EXPORTS_BROWSERS = $Browsers
    $env:OUTBOUND_EXPORTS_SAMPLE_DATA = if ($SampleData) { 'true' } else { 'false' }
    & (Join-Path $Workspace 'gradlew.bat') -p server integrationTest @testArgs --rerun-tasks --console=plain 2>&1 |
        Tee-Object -FilePath $runLog
    $gradleExit = $LASTEXITCODE
} finally {
    Pop-Location
}
Write-Host "gradle exit=$gradleExit"

# ---------------------------------------------------------------- results
$resultsDir = Join-Path $Workspace 'server/build/test-results/integrationTest'
$xml = @(Get-ChildItem $resultsDir -Filter 'TEST-*.xml' -ErrorAction SilentlyContinue |
    Where-Object { $_.LastWriteTime -ge $runStartedAt })
if ($xml.Count -eq 0) {
    Write-Host "RESULT r08-run=$Run NOT-EXECUTED (this run produced no XML under $resultsDir)"
    Write-Host "markers: started=$(Test-Path -LiteralPath (Join-Path $attempt 'test-started.marker')) attempt=$attempt"
    exit 2
}
$testCount=0;$failureCount=0;$errorCount=0;$skipCount=0
foreach ($x in $xml) {
    $d = [xml][System.IO.File]::ReadAllText($x.FullName, $utf8)
    $testCount += [int]$d.testsuite.tests; $failureCount += [int]$d.testsuite.failures
    $errorCount += [int]$d.testsuite.errors; $skipCount += [int]$d.testsuite.skipped
}
Write-Host ("RESULT r08-run={0} db={1} classes={2} tests={3} failures={4} errors={5} skipped={6}" -f $Run, $database, $xml.Count, $testCount, $failureCount, $errorCount, $skipCount)
$xml | ForEach-Object {
    $d = [xml][System.IO.File]::ReadAllText($_.FullName, $utf8)
    $d.testsuite.testcase | ForEach-Object {
        $state = if ($_.failure) { 'FAIL' } elseif ($_.error) { 'ERROR' } elseif ($_.skipped) { 'SKIP' } else { 'pass' }
        Write-Host ("  [{0}] {1}.{2}" -f $state, $d.testsuite.name, $_.name)
        if ($_.failure) { Write-Host ($_.failure.message -split "`n" | Select-Object -First 4 | Out-String) }
        if ($_.error) { Write-Host ($_.error.message -split "`n" | Select-Object -First 4 | Out-String) }
    }
}

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
} else {
    Write-Host "teardown skipped (-KeepServerRunning); instance is on 127.0.0.1:$Port"
}
if ($gradleExit -ne 0) { exit $gradleExit }

# 例行维护：只保留最近 50 个 attempt 的 datadir（MySQL 数据目录），删除更早的 datadir；
# 日志/marker/storage/签名材料一律保留（见 tools/cleanup-old-attempt-datadirs.ps1 的说明）。
$cleanup = Join-Path (Split-Path -Parent $PSCommandPath) 'cleanup-old-attempt-datadirs.ps1'
if (Test-Path -LiteralPath $cleanup) {
    & powershell -NoProfile -ExecutionPolicy Bypass -File $cleanup -Local (Join-Path $Workspace '.local') -Keep 50 -CleanupAfterRun
}