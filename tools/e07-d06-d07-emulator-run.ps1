#requires -Version 5.1
[CmdletBinding()]
param(
    [ValidateRange(1,9999)][int]$Run = 1601,
    [int]$MysqlPort = 19361,
    [int]$HttpPort = 19461,
    [int]$WebPort = 5174,
    [int]$EmulatorPort = 5568,
    [string]$Avd = 'Pixel_6',
    [string]$Root = 'D:\acrylic-pda-outbound'
)
$ErrorActionPreference = 'Stop'
$attempt = Join-Path $Root ".local\e2a-cross-20260925-$Run"
$evidence = Join-Path $Root ".local\goal-20261011\fault-restart-consumer-$Run"
$input = Join-Path $evidence 'private\input.json'
$adb = 'C:\Users\qq113\AppData\Local\Android\Sdk\platform-tools\adb.exe'
$emulator = 'C:\Users\qq113\AppData\Local\Android\Sdk\emulator\emulator.exe'
$java = 'C:\Program Files\Java\jdk-21.0.11\bin\java.exe'
$node = 'F:\Program Files\nodejs\node.exe'
$python = 'C:\Python314\python.exe'
$serial = "emulator-$EmulatorPort"
$emulatorHome = Join-Path $evidence 'emulator-home'
$emulatorData = Join-Path $emulatorHome "$Avd.avd"
$avdRoot = if ($env:ANDROID_AVD_HOME) { $env:ANDROID_AVD_HOME } else { Join-Path $env:USERPROFILE '.android\avd' }
$avdData = Join-Path $avdRoot "$Avd.avd"
$database = "acrylic_e2a_cross_20260925_$Run"
$mysqlBin = 'C:\Program Files\MySQL\MySQL Server 8.0\bin'
$serverProcess = $null; $webProcess = $null; $emulatorProcess = $null; $exit = 1; $stage = 'preflight'

function Require-Zero([string]$name) { if ($LASTEXITCODE -ne 0) { throw "$name failed with exit $LASTEXITCODE" } }
function Run-Logged([string]$file, [string[]]$arguments, [string]$log) {
    $previousErrorActionPreference = $ErrorActionPreference
    $code = 1
    try {
        $ErrorActionPreference = 'Continue'
        & $file @arguments *> $log
        $code = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    [IO.File]::WriteAllText("$log.exit",[string]$code)
    if ($code -ne 0) { throw "command failed ($code): $file; see $log" }
}
function Wait-Http([string]$url) {
    foreach ($i in 1..90) { try { Invoke-WebRequest -UseBasicParsing -Uri $url -TimeoutSec 2 | Out-Null; return } catch { Start-Sleep -Seconds 1 } }
    throw "HTTP not ready: $url"
}
function Require-Free([int]$port) { if (Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue) { throw "port occupied: $port" } }
function Require-NoAvdProcess([string]$name) {
    $active = @(Get-CimInstance Win32_Process | Where-Object {
        $_.Name -in @('emulator.exe','qemu-system-x86_64.exe') -and $_.CommandLine -match "-avd\s+$([regex]::Escape($name))(\s|$)"
    })
    if ($active.Count -gt 0) { throw "AVD already active: $name" }
}
function Prepare-EmulatorAvd([string]$source, [string]$targetHome, [string]$name) {
    if (-not (Test-Path -LiteralPath $source -PathType Container)) { throw "AVD data source missing: $source" }
    $target = Join-Path $targetHome "$name.avd"
    New-Item -ItemType Directory -Force -Path $targetHome | Out-Null
    if (Test-Path -LiteralPath $target) { throw "isolated AVD target already exists: $target" }
    New-Item -ItemType Directory -Force -Path $target | Out-Null
    $excluded = @('multiinstance.lock','hardware-qemu.ini.lock','tmpAdbCmds','snapshots')
    foreach ($item in Get-ChildItem -LiteralPath $source -Force) {
        if ($excluded -notcontains $item.Name) { Copy-Item -LiteralPath $item.FullName -Destination $target -Recurse -Force }
    }
    $ini = Join-Path (Split-Path -Parent $source) "$name.ini"
    if (-not (Test-Path -LiteralPath $ini -PathType Leaf)) { throw "AVD index missing: $ini" }
    $targetIni = Join-Path $targetHome "$name.ini"
    Copy-Item -LiteralPath $ini -Destination $targetIni -Force
    $iniText = [IO.File]::ReadAllText($targetIni)
    $iniText = [regex]::Replace($iniText, '(?m)^path=.*$', "path=$target")
    $iniText = [regex]::Replace($iniText, '(?m)^path\.rel=.*$', "path.rel=$target")
    [IO.File]::WriteAllText($targetIni, $iniText, [Text.Encoding]::UTF8)
    $images = @(Get-ChildItem -LiteralPath $target -File | Where-Object Name -in @('userdata-qemu.img.qcow2','encryptionkey.img.qcow2','cache.img.qcow2','sdcard.img.qcow2'))
    if (-not ($images.Name -contains 'userdata-qemu.img.qcow2')) { throw 'isolated AVD writable userdata image missing' }
    $manifest = [pscustomobject]@{ source = $source; target = $target; fileCount = @(Get-ChildItem -LiteralPath $target -Recurse -Force -File).Count; bytes = (Get-ChildItem -LiteralPath $target -Recurse -Force -File | Measure-Object Length -Sum).Sum; writable = @($images | ForEach-Object { [pscustomobject]@{ name = $_.Name; sourceSha256 = (Get-FileHash -LiteralPath (Join-Path $source $_.Name) -Algorithm SHA256).Hash; targetSha256 = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash } }) }
    $manifest | ConvertTo-Json -Depth 5 | Set-Content -Encoding utf8 (Join-Path $evidence 'emulator-data-manifest.json')
}
function Verify-EmulatorSource([string]$manifestPath) {
    if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) { return }
    $manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
    $checks = @($manifest.writable | ForEach-Object {
        $now = (Get-FileHash -LiteralPath (Join-Path $manifest.source $_.name) -Algorithm SHA256).Hash
        [pscustomobject]@{ name = $_.name; before = $_.sourceSha256; after = $now; unchanged = ($_.sourceSha256 -eq $now) }
    })
    $checks | ConvertTo-Json -Depth 4 | Set-Content -Encoding utf8 (Join-Path $evidence 'emulator-source-after.json')
    if (@($checks | Where-Object { -not $_.unchanged }).Count -gt 0) { throw 'default AVD source changed during isolated run' }
}
function Require-IsolatedEmulator([string]$data) {
    $userdata = Join-Path $data 'userdata-qemu.img.qcow2'
    if (-not (Test-Path -LiteralPath $userdata -PathType Leaf)) { throw "isolated userdata image missing: $userdata" }
    $bound = @(Get-CimInstance Win32_Process | Where-Object {
        $_.Name -in @('emulator.exe','qemu-system-x86_64.exe') -and $_.CommandLine -like "*$data*"
    })
    if ($bound.Count -eq 0) { throw "emulator process is not bound to isolated datadir: $data" }
}
function Save-Environment([string]$name) {
    $rows = Get-ChildItem Env: | Where-Object Name -match '^(OUTBOUND_|FAULT_RESTART_|VITE_FAULT_WITHDRAWAL_ENABLED$|JAVA_HOME$|TEMP$|TMP$|ANDROID|GRADLE)' |
        Sort-Object Name | ForEach-Object { [pscustomobject]@{ name = $_.Name; value = if ($_.Name -match 'PASSWORD|TOKEN|SECRET|PRIVATE') { '<redacted>' } else { $_.Value } } }
    $rows | ConvertTo-Json -Depth 3 | Set-Content -Encoding utf8 (Join-Path $evidence "$name.json")
}
function Save-Processes([string]$name) {
    [pscustomobject]@{ processes = @(Get-CimInstance Win32_Process | Where-Object Name -match '^(mysqld|java|node|adb|emulator)(\.exe)?$' | Select-Object Name,ProcessId,CommandLine); listeners = @(Get-NetTCPConnection -State Listen | Select-Object LocalAddress,LocalPort,OwningProcess) } |
        ConvertTo-Json -Depth 5 | Set-Content -Encoding utf8 (Join-Path $evidence "$name.json")
}
function Stop-ProcessSafe($process) { if ($process -and -not $process.HasExited) { Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue } }
function Run-Instrumentation([string]$method, [string]$log) {
    Run-Logged $adb @('-s',$serial,'shell','am','instrument','-w','-r','-e','faultRestart','true','-e','class',"com.acrylic.outbound.pda.FaultRestartDeviceTest#$method",'com.acrylic.outbound.pda.debug.test/androidx.test.runner.AndroidJUnitRunner') $log
    if (-not (Select-String -LiteralPath $log -Pattern 'OK \(1 test\)' -Quiet)) { throw "instrumentation did not report OK: $method" }
}
function Stop-MySql {
    $cnf = Join-Path $attempt 'client.cnf'; if (-not (Test-Path $cnf)) { return }
    $admin = Join-Path $attempt 'admin.cnf'
    if (-not (Test-Path $admin)) { "[client]`nhost=127.0.0.1`nport=$MysqlPort`nuser=root`nprotocol=tcp`n" | Set-Content -Encoding ascii $admin }
    & "$mysqlBin\mysql.exe" "--defaults-file=$admin" --execute="GRANT SHUTDOWN ON *.* TO 'e2a_cross_20260925_$Run'@'127.0.0.1'; FLUSH PRIVILEGES;" *> (Join-Path $evidence 'grant-shutdown.log'); Require-Zero 'grant shutdown'
    $dump = Join-Path $evidence 'final-database.sql'; & "$mysqlBin\mysqldump.exe" "--defaults-file=$cnf" --single-transaction --no-tablespaces --routines --triggers --events "--result-file=$dump" $database 2> (Join-Path $evidence 'database-dump.log'); Require-Zero 'database dump'
    & "$mysqlBin\mysqladmin.exe" "--defaults-file=$cnf" shutdown *> (Join-Path $evidence 'authenticated-shutdown.log'); Require-Zero 'authenticated MySQL shutdown'
    foreach ($i in 1..40) { if (-not (Get-NetTCPConnection -State Listen -LocalPort $MysqlPort -ErrorAction SilentlyContinue)) { return }; Start-Sleep -Milliseconds 250 }
    throw "MySQL listener remains on $MysqlPort"
}

Set-Location $Root
foreach ($path in @($attempt,$evidence)) { if (Test-Path -LiteralPath $path) { throw "one-shot path already exists: $path" } }
foreach ($port in @($MysqlPort,$HttpPort,$WebPort,$EmulatorPort)) { Require-Free $port }
Require-NoAvdProcess $Avd
foreach ($path in @($adb,$emulator,$java,$node,$python)) { if (-not (Test-Path $path)) { throw "required executable missing: $path" } }
New-Item -ItemType Directory -Force -Path (Join-Path $evidence 'private'),(Join-Path $evidence 'xml'),(Join-Path $evidence 'apk'),$emulatorHome | Out-Null
$env:TEMP = Join-Path $Root '.local\tmp'; $env:TMP = $env:TEMP; $env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.11'
$env:FAULT_RESTART_CONSUMER_INPUT = $input; $env:FAULT_RESTART_WEB_RESULT = Join-Path $evidence 'private\web-result.json'; $env:VITE_FAULT_WITHDRAWAL_ENABLED = 'true'
Save-Environment 'environment-before'; Save-Processes 'processes-before'; git rev-parse HEAD | Set-Content -Encoding ascii (Join-Path $evidence 'head.txt')
try {
    $stage = 'mysql'; Run-Logged 'powershell.exe' @('-NoProfile','-File',(Join-Path $Root 'tools\e2a-provision-and-run-106.ps1'),'-Run',$Run,'-Port',$MysqlPort,'-ProvisionOnly','-Workspace',$Root) (Join-Path $evidence 'provision.log')
    $password = (Get-Content -Raw (Join-Path $attempt 'run-password.txt')).Trim()
    "[client]`nhost=127.0.0.1`nport=$MysqlPort`nuser=e2a_cross_20260925_$Run`npassword=$password`nprotocol=tcp`n" | Set-Content -Encoding ascii (Join-Path $attempt 'client.cnf')
    $env:OUTBOUND_DB_URL = "jdbc:mysql://127.0.0.1:$MysqlPort/$database"; $env:OUTBOUND_DB_USER = "e2a_cross_20260925_$Run"; $env:OUTBOUND_DB_PASSWORD = $password
    $env:OUTBOUND_MIGRATE = 'true'; $env:OUTBOUND_STORAGE = Join-Path $attempt 'storage'; $env:OUTBOUND_COOKIE_SECURE = 'false'; $env:OUTBOUND_AUTH_SIGNING_PRIVATE_KEY = Join-Path $Root '.local\it-keys\server-private.der'; $env:OUTBOUND_AUTH_SIGNING_PUBLIC_KEY = Join-Path $Root '.local\it-keys\server-public.der'
    $stage = 'fixture'; New-Item -ItemType File -Force -Path (Join-Path $attempt 'test-started.marker') | Out-Null
    Run-Logged (Join-Path $Root 'gradlew.bat') @('-p','server','integrationTest','--tests','*FaultRestartConsumerFixtureIT','--rerun-tasks','--offline','--console=plain','-x',':report-rendering:prepareFont','--no-daemon') (Join-Path $evidence 'fixture.log')
    if (-not (Test-Path $input)) { throw 'consumer fixture did not produce input' }
    Copy-Item (Get-ChildItem server/build/test-results/integrationTest/TEST-*FaultRestartConsumerFixtureIT.xml | Select-Object -Last 1).FullName (Join-Path $evidence 'xml\fixture.xml')
    Run-Logged $python @((Join-Path $Root 'tools\fault-restart-input-consumer.py'),$input,(Join-Path $evidence 'private\python-result.json')) (Join-Path $evidence 'python-consumer.log')
    $stage = 'build'; Run-Logged (Join-Path $Root 'gradlew.bat') @('-p','server','bootJar','--rerun-tasks','--offline','--console=plain','-x',':report-rendering:prepareFont','--no-daemon') (Join-Path $evidence 'server-build.log')
    Run-Logged (Join-Path $Root 'gradlew.bat') @('-p','android',':app:assembleDebug',':app:assembleDebugAndroidTest','--rerun-tasks','--offline','--no-daemon') (Join-Path $evidence 'android-build.log')
    Copy-Item android/app/build/outputs/apk/debug/app-debug.apk (Join-Path $evidence 'apk\app-debug.apk'); Copy-Item android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk (Join-Path $evidence 'apk\app-debug-androidTest.apk')
    $stage = 'service'; $env:OUTBOUND_MIGRATE = 'false'; $serverProcess = Start-Process $java -WindowStyle Hidden -PassThru -ArgumentList @('-Djava.io.tmpdir='+$env:TEMP,'-jar',(Join-Path $Root 'server/build/libs/acrylic-outbound-server-0.1.0.jar'),"--server.port=$HttpPort",'--outbound.recall.fault-withdrawal-enabled=true') -RedirectStandardOutput (Join-Path $evidence 'service.log') -RedirectStandardError (Join-Path $evidence 'service-error.log'); Wait-Http "http://127.0.0.1:$HttpPort/api/v1/auth/csrf"
    $env:OUTBOUND_API_PROXY = "http://127.0.0.1:$HttpPort"; $env:OUTBOUND_WEB_URL = "http://127.0.0.1:$WebPort"; $webProcess = Start-Process $node -WorkingDirectory (Join-Path $Root 'web') -WindowStyle Hidden -PassThru -ArgumentList @('node_modules/vite/bin/vite.js','--host','127.0.0.1','--port',$WebPort) -RedirectStandardOutput (Join-Path $evidence 'vite.log') -RedirectStandardError (Join-Path $evidence 'vite-error.log'); Wait-Http "http://127.0.0.1:$WebPort/login"
    $stage = 'emulator'; Prepare-EmulatorAvd $avdData $emulatorHome $Avd; $env:ANDROID_AVD_HOME = $emulatorHome; $emulatorProcess = Start-Process $emulator -WindowStyle Hidden -PassThru -ArgumentList @('-avd',$Avd,'-datadir',$emulatorData,'-port',$EmulatorPort,'-no-snapshot-load','-no-snapshot-save','-no-boot-anim','-gpu','swiftshader_indirect') -RedirectStandardOutput (Join-Path $evidence 'emulator.log') -RedirectStandardError (Join-Path $evidence 'emulator-error.log'); Run-Logged $adb @('-s',$serial,'wait-for-device') (Join-Path $evidence 'adb-wait.log')
    foreach ($i in 1..180) { $boot = (& $adb -s $serial shell getprop sys.boot_completed 2>$null).Trim(); if ($boot -eq '1') { break }; Start-Sleep -Seconds 1 }; if ($boot -ne '1') { throw 'emulator boot timeout' }; Require-IsolatedEmulator $emulatorData
    Run-Logged $adb @('-s',$serial,'reverse','tcp:19434',"tcp:$HttpPort") (Join-Path $evidence 'adb-reverse.log'); Run-Logged $adb @('-s',$serial,'install','-r',(Join-Path $evidence 'apk\app-debug.apk')) (Join-Path $evidence 'adb-install.log'); Run-Logged $adb @('-s',$serial,'install','-r',(Join-Path $evidence 'apk\app-debug-androidTest.apk')) (Join-Path $evidence 'adb-test-install.log'); Run-Logged $adb @('-s',$serial,'shell','pm','clear','com.acrylic.outbound.pda.debug') (Join-Path $evidence 'adb-clear-app-data.log')
    Run-Logged $adb @('-s',$serial,'push',$input,'/data/local/tmp/fault-restart-input.json') (Join-Path $evidence 'adb-push.log'); Run-Logged $adb @('-s',$serial,'shell','run-as','com.acrylic.outbound.pda.debug','mkdir','-p','files/fault-restart/private') (Join-Path $evidence 'adb-fixture-mkdir.log'); Run-Logged $adb @('-s',$serial,'shell','run-as','com.acrylic.outbound.pda.debug','cp','/data/local/tmp/fault-restart-input.json','files/fault-restart/private/input.json') (Join-Path $evidence 'adb-fixture-copy.log')
    $stage = 'android'; $methods = @('newDeviceClaimsDownloadsScansFreezesAndVerifiesOwnSuccess','forceStoppedProcessRetainsVerifiedResultAndOriginalFrozenBytes','scanningDeviceConsumesSignedWithdrawalAndRetainsItsLocalOrder','retainedWithdrawalReplaysIntoStoppedStateAfterNewProductProcess'); $index = 0
    foreach ($method in $methods) { $index++; if ($index -eq 2) { & $adb -s $serial shell am force-stop com.acrylic.outbound.pda.debug; Require-Zero 'force-stop product' }; Run-Instrumentation $method (Join-Path $evidence "android-$index-$method.log") }
    Run-Logged $adb @('-s',$serial,'logcat','-d') (Join-Path $evidence 'logcat.txt'); Run-Logged $adb @('-s',$serial,'shell','getprop') (Join-Path $evidence 'getprop.txt'); Run-Logged $adb @('-s',$serial,'exec-out','run-as','com.acrylic.outbound.pda.debug','cat','files/fault-restart/result.json') (Join-Path $evidence 'private\android-result.json'); Run-Logged $adb @('-s',$serial,'exec-out','run-as','com.acrylic.outbound.pda.debug','cat','files/fault-restart/restart.json') (Join-Path $evidence 'private\android-restart.json'); Run-Logged $adb @('-s',$serial,'exec-out','run-as','com.acrylic.outbound.pda.debug','cat','files/fault-restart/revocation.json') (Join-Path $evidence 'private\android-revocation.json'); Run-Logged $adb @('-s',$serial,'exec-out','run-as','com.acrylic.outbound.pda.debug','cat','files/fault-restart/revocation-restart.json') (Join-Path $evidence 'private\android-revocation-restart.json')
    $stage = 'chrome'; Push-Location (Join-Path $Root 'web'); try { Run-Logged $node @('node_modules/@playwright/test/cli.js','test','tests/fault-restart.spec.ts','--reporter=json','--trace=on',"--output=$evidence/playwright") (Join-Path $evidence 'playwright.json') } finally { Pop-Location }; $stage = 'complete'; $exit = 0
} catch { $_ | Out-String | Set-Content -Encoding utf8 (Join-Path $evidence 'error-detail.txt'); [IO.File]::WriteAllText((Join-Path $evidence 'error.txt'),"stage=$stage`n$($_.Exception.Message)`n") } finally {
    Stop-ProcessSafe $webProcess; Stop-ProcessSafe $serverProcess
    if ($emulatorProcess -and -not $emulatorProcess.HasExited) { try { Run-Logged $adb @('-s',$serial,'emu','kill') (Join-Path $evidence 'emulator-stop.log') } catch { $_.Exception.Message | Set-Content -Encoding utf8 (Join-Path $evidence 'emulator-stop-error.txt') }; Start-Sleep -Seconds 3; Stop-ProcessSafe $emulatorProcess }
    try { Verify-EmulatorSource (Join-Path $evidence 'emulator-data-manifest.json') } catch { $_.Exception.Message | Set-Content -Encoding utf8 (Join-Path $evidence 'emulator-source-after-error.txt'); $exit = 1 }
    try { Stop-MySql } catch { $_.Exception.Message | Set-Content -Encoding utf8 (Join-Path $evidence 'shutdown-error.txt') }
    Save-Environment 'environment-after'; Save-Processes 'processes-after'; [pscustomobject]@{ run=$Run; stage=$stage; exit=$exit; serial=$serial; mysqlPort=$MysqlPort; httpPort=$HttpPort; webPort=$WebPort; evidence=$evidence } | ConvertTo-Json | Set-Content -Encoding utf8 (Join-Path $evidence 'result.json')
}
exit $exit
