# R09 C2 B1-3 A0.1: run the remaining gated ITs, one class per one-shot attempt.
#
# The `E2A_CROSS_VERIFY=true` gated E2a IT classes already carry 24 verified classes; the rest were
# never attempted. Per the user ruling of 2026-09-29 and checklist A0.1 this driver runs them one at a
# time: each class gets exactly one attempt (attempts are consumed and never re-run), and the isolated
# MySQL instance is stopped before the next class starts.
#
# This driver owns the MySQL lifecycle in-process (provision, start, run gradle, stop). It does NOT
# nest a second powershell.exe for the shutdown: that nested call hung and was killed, so the driver
# stops mysqld itself with the attempt's own admin.cnf. All evidence stays under
# `.local/e2a-cross-20260925-<run>/`; nothing here changes product or test code or any default switch.
#
# Usage:
#   powershell -NoProfile -File tools/r09-a01-remaining-it-run.ps1
#   powershell -NoProfile -File tools/r09-a01-remaining-it-run.ps1 -FromIndex 6
#   powershell -NoProfile -File tools/r09-a01-remaining-it-run.ps1 -Only E2aAndroidHttpIT
param(
    [string]$Root = 'D:/acrylic-pda-outbound',
    [int]$StartRun = 712,
    [int]$FromIndex = 1,
    [string[]]$Only = @()
)

$ErrorActionPreference = 'Stop'
$port = 19330
$mysqlBase = 'C:/Program Files/MySQL/MySQL Server 8.0'
$mysql = "$mysqlBase/bin/mysql.exe"
$mysqld = "$mysqlBase/bin/mysqld.exe"
$mysqladmin = "$mysqlBase/bin/mysqladmin.exe"
$logDir = Join-Path $Root '.local/r09-a01-20260930'
New-Item -ItemType Directory -Force -Path $logDir | Out-Null
$progress = Join-Path $logDir 'progress.txt'
$utf8 = New-Object Text.UTF8Encoding($false)

function Write-Line([string]$text) {
    $line = ('{0} {1}' -f (Get-Date).ToString('HH:mm:ss'), $text)
    [IO.File]::AppendAllText($progress, $line + "`n", $utf8)
    Write-Output $line
}
function Get-Listener([int]$p) {
    $n = 0
    Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue | Where-Object { $_.LocalPort -eq $p } |
        ForEach-Object { $n++ }
    return $n
}

# E2a_CROSS_VERIFY gated classes that are NOT part of the existing baseline (run = StartRun + index - 1).
$classes = @(
    'E2aAndroidAbortHttpIT',
    'E2aAndroidHttpIT',
    'E2aAndroidPartialAbortHttpIT',
    'E2aChainedCorrectionExportIT',
    'E2aCorrectionWebRouteIT',
    'E2aDraftEvidenceIT',
    'E2aFormalRegisteredCounterexamplesIT',
    'E2aFormalRegisteredSourceIT',
    'E2aHandoverFormalIT',
    'E2aHeaderCorrectionExportIT',
    'E2aPartialCorrectionExportIT',
    'E2aServeBindingForAndroidIT',
    'E2aTemporaryArchiveRecoveryIT',
    'E2aTemporaryArchiveSwitchIT',
    'E2aTemporaryCommandBrowserIT',
    'E2aTemporaryExportRenderIT',
    'E2aTemporaryLinkConfirmIT',
    'E2aUnsubmittedHttpIT'
)
if ($Only.Count -gt 0) { $classes = @($classes | Where-Object { $Only -contains $_ }) }

if ((Get-Listener $port) -gt 0) { throw 'Port 19330 is occupied; stop the previous attempt before starting' }

$results = @()
try {
    for ($i = 0; $i -lt $classes.Count; $i++) {
        $index = $i + 1
        if ($index -lt $FromIndex) { continue }
        $class = $classes[$i]
        $run = $StartRun + $i
        $attempt = Join-Path $Root ".local/e2a-cross-20260925-$run"
        $schema = "acrylic_e2a_cross_20260925_$run"
        $user = "e2a_cross_20260925_$run"
        if (Test-Path -LiteralPath (Join-Path $attempt 'test-started.marker')) {
            throw "attempt $attempt already consumed; allocate another run number instead of re-running"
        }
        if (Test-Path -LiteralPath $attempt) { throw "attempt $attempt exists; retain it and allocate another run" }

        Write-Line "START [$index/$($classes.Count)] run=$run class=$class"
        $started = Get-Date
        $runnerLog = Join-Path $logDir ("run{0}-{1}.log" -f $run, $class)
        $log = New-Object Text.StringBuilder
        [void]$log.AppendLine("run=$run class=$class started=$($started.ToString('o'))")
        $serverProcess = $null

        # ---------------------------------------------------------------- provision (runner parity)
        New-Item -ItemType Directory -Path $attempt, (Join-Path $attempt 'private'), (Join-Path $attempt 'storage') | Out-Null
        $data = Join-Path $attempt 'data'
        $password = -join (1..64 | ForEach-Object { '{0:x}' -f (Get-Random -Minimum 0 -Maximum 16) })
        [IO.File]::WriteAllText((Join-Path $attempt 'private/password.txt'), $password, $utf8)
        # RecoveryProjectionDatabases demands exactly these preallocated (and empty) negative-case schemas;
        # _restore is the archive ITs' companion schema.
        $suffixes = @('_copy', '_conflict', '_missing', '_gap', '_overflow', '_rollback', '_instance', '_skip', '_mapping', '_restore')
        $bootstrap = "CREATE DATABASE $schema;`n" +
            "CREATE USER '$user'@'127.0.0.1' IDENTIFIED BY '$password';`n" +
            "GRANT ALL ON $schema.* TO '$user'@'127.0.0.1';`n" +
            "GRANT PROCESS ON *.* TO '$user'@'127.0.0.1';`n" +
            "CREATE USER 'root'@'127.0.0.1' IDENTIFIED BY '';`n" +
            "GRANT ALL ON *.* TO 'root'@'127.0.0.1' WITH GRANT OPTION;`n" +
            "GRANT SHUTDOWN ON *.* TO '$user'@'127.0.0.1';`n" +
            "GRANT ALL ON *.* TO '$user'@'127.0.0.1' WITH GRANT OPTION;`n" +
            (($suffixes | ForEach-Object {
                "CREATE DATABASE ${schema}$_;`nGRANT ALL ON ${schema}$_.* TO '$user'@'127.0.0.1';`n" }) -join '') +
            "FLUSH PRIVILEGES;`n"
        [IO.File]::WriteAllText((Join-Path $attempt 'private/bootstrap.sql'), $bootstrap, $utf8)
        [IO.File]::WriteAllText((Join-Path $attempt 'private/admin.cnf'),
            "[client]`nhost=127.0.0.1`nport=$port`nuser=root`nprotocol=tcp`n", $utf8)

        $ErrorActionPreference = 'Continue'
        & $mysqld '--no-defaults' '--initialize-insecure' "--datadir=$data" '--mysqlx=OFF' `
            "--log-error=$attempt/initialize.log" *> "$attempt/initialize.stdout.log"
        $initExit = $LASTEXITCODE
        $ErrorActionPreference = 'Stop'
        if ($initExit -ne 0) { throw "MySQL initialization failed (exit $initExit); see the retained attempt directory" }

        $serverArgs = @('--no-defaults', "--datadir=$data", "--port=$port",
            '--bind-address=127.0.0.1', '--mysqlx=OFF', '--skip-name-resolve', '--skip-grant-tables=false',
            '--log-bin-trust-function-creators=ON', "--log-error=$attempt/server.log",
            "--init-file=$($attempt.Replace('\', '/'))/private/bootstrap.sql")
        $serverProcess = Start-Process -FilePath $mysqld -ArgumentList $serverArgs -WindowStyle Hidden -PassThru `
            -RedirectStandardOutput "$attempt/server.stdout.log" -RedirectStandardError "$attempt/server.stderr.log"
        $ready = $false
        foreach ($n in 1..60) {
            Start-Sleep -Milliseconds 500
            if ((Get-Listener $port) -gt 0) {
                $ErrorActionPreference = 'Continue'
                & $mysqladmin "--defaults-file=$attempt/private/admin.cnf" ping *> $null
                $pinged = $LASTEXITCODE
                $ErrorActionPreference = 'Stop'
                if ($pinged -eq 0) { $ready = $true; break }
            }
        }
        if (-not $ready) { throw 'MySQL did not become ready; see the retained attempt directory' }

        # ---------------------------------------------------------------- environment (runner parity)
        $env:E2A_CROSS_VERIFY = 'true'
        $env:E2A_CROSS_RUN = "$run"
        $env:E2A_CROSS_PASSWORD = $password
        $env:E2A_CROSS_ROOT = $Root
        $env:B11_EFFECT_EVIDENCE_DIR = $attempt
        $env:SPRING_DATASOURCE_URL = "jdbc:mysql://127.0.0.1:$port/$schema" + '?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC'
        $env:SPRING_DATASOURCE_USERNAME = $user
        $env:SPRING_DATASOURCE_PASSWORD = $password
        $env:OUTBOUND_DB_URL = $env:SPRING_DATASOURCE_URL
        $env:OUTBOUND_DB_USER = $user
        $env:OUTBOUND_DB_PASSWORD = $password
        $env:SERVER_SERVLET_SESSION_COOKIE_SECURE = 'false'
        $env:OUTBOUND_MIGRATE = 'false'
        $env:OUTBOUND_STORAGE = Join-Path $attempt 'storage'
        $env:OUTBOUND_AUTH_SIGNING_PRIVATE_KEY = Join-Path $attempt 'private/server-private.der'
        $env:OUTBOUND_AUTH_SIGNING_PUBLIC_KEY = Join-Path $attempt 'private/server-public.der'
        $env:OUTBOUND_SIGNING_PRIVATE_KEY = $env:OUTBOUND_AUTH_SIGNING_PRIVATE_KEY
        $env:OUTBOUND_SIGNING_PUBLIC_KEY = $env:OUTBOUND_AUTH_SIGNING_PUBLIC_KEY
        $env:OUTBOUND_EXPORTS_RENDERER_HOME = Join-Path $Root 'report-rendering/build/install/acrylic-report-rendering'
        $localBrowsers = Join-Path $Root 'report-rendering/build/browsers'
        $env:OUTBOUND_EXPORTS_BROWSERS = if (Test-Path $localBrowsers) { $localBrowsers }
            else { 'D:/acrylic-pda-worktrees/r08-export/report-rendering/build/browsers' }
        $env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.11'

        # ---------------------------------------------------------------- run one class
        $arguments = @('-p', 'server', 'recoveryControlClasspath', 'integrationTest', '--rerun-tasks', '--no-daemon',
            '--console=plain', '--tests', "com.acrylic.outbound.e2averify.$class")
        ($arguments -join ' ') | Set-Content -LiteralPath (Join-Path $attempt 'command.txt') -Encoding UTF8
        $ErrorActionPreference = 'Continue'
        & (Join-Path $Root 'gradlew.bat') @arguments *> "$attempt/gradle.log"
        $runnerExit = $LASTEXITCODE
        $ErrorActionPreference = 'Stop'
        [IO.File]::WriteAllText((Join-Path $attempt 'exit.txt'), "testExit=$runnerExit`n", $utf8)
        $resultsDir = Join-Path $Root 'server/build/test-results/integrationTest'
        if (Test-Path $resultsDir) { Copy-Item (Join-Path $resultsDir 'TEST-*.xml') $attempt -ErrorAction SilentlyContinue }

        # ---------------------------------------------------------------- stop the instance
        $shutdownExit = -1
        if ($serverProcess -and -not $serverProcess.HasExited) {
            $ErrorActionPreference = 'Continue'
            & $mysqladmin "--defaults-file=$attempt/private/admin.cnf" shutdown *> "$attempt/shutdown.log"
            $shutdownExit = $LASTEXITCODE
            $ErrorActionPreference = 'Stop'
            if (-not $serverProcess.WaitForExit(30000)) { $serverProcess.Kill() }
        } else { $shutdownExit = 0 }
        $wait = 0
        while ((Get-Listener $port) -gt 0 -and $wait -lt 60) { Start-Sleep -Milliseconds 500; $wait++ }
        $elapsed = [int]((Get-Date) - $started).TotalSeconds

        # Numbers come from the TEST-*.xml the runner copied into the attempt; a skipped or absent target
        # class counts as "not attempted" and is never reported as a pass.
        $tests = 0; $failures = 0; $errors = 0; $skipped = 0; $targetSeen = $false; $detail = ''
        foreach ($file in Get-ChildItem $attempt -Filter 'TEST-*.xml' -ErrorAction SilentlyContinue) {
            [xml]$doc = Get-Content -LiteralPath $file.FullName -Raw
            $tests += [int]$doc.testsuite.tests; $failures += [int]$doc.testsuite.failures
            $errors += [int]$doc.testsuite.errors; $skipped += [int]$doc.testsuite.skipped
            if ($doc.testsuite.name -eq "com.acrylic.outbound.e2averify.$class") {
                $targetSeen = $true
                $case = $doc.testsuite.testcase | Where-Object { $_.failure } | Select-Object -First 1
                if ($case) { $detail = (($case.failure.message -split "`n") | Select-Object -First 1) }
                if ([int]$doc.testsuite.skipped -gt 0) { $detail = 'SKIPPED/untried: ' + $detail }
            }
        }
        if (-not $targetSeen) { $detail = 'target class produced no XML: not attempted' }
        $released = ((Get-Listener $port) -eq 0)
        [void]$log.AppendLine("testExit=$runnerExit shutdownExit=$shutdownExit released=$released listenerAfter=$(Get-Listener $port)")
        [IO.File]::WriteAllText($runnerLog, $log.ToString(), $utf8)
        $results += [pscustomobject][ordered]@{
            index = $index; run = $run; class = $class; runnerExit = $runnerExit; shutdownExit = $shutdownExit
            tests = $tests; failures = $failures; errors = $errors; skipped = $skipped
            seconds = $elapsed; targetSeen = $targetSeen; portReleased = $released; detail = $detail
        }
        Write-Line ('DONE  [{0}/{1}] run={2} class={3} runnerExit={4} tests={5} fail={6} err={7} skip={8} {9}s released={10} {11}' -f `
            $index, $classes.Count, $run, $class, $runnerExit, $tests, $failures, $errors, $skipped, $elapsed, $released, $detail)
    }
} finally {
    # A killed driver must not leave the isolated instance listening.
    if ((Get-Listener $port) -gt 0) {
        $lastAttempt = Join-Path $Root ".local/e2a-cross-20260925-$($StartRun + $classes.Count - 1)"
        $cfg = Join-Path $lastAttempt 'private/admin.cnf'
        if (Test-Path -LiteralPath $cfg) {
            $ErrorActionPreference = 'Continue'
            & $mysqladmin "--defaults-file=$cfg" shutdown *>> (Join-Path $logDir 'finally-shutdown.log')
            $ErrorActionPreference = 'Stop'
        }
        Write-Line ('FINALLY listener still up on {0}; attempted admin shutdown' -f $port)
    }
}

$results | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $logDir 'a01-summary.json') -Encoding UTF8
$results | Format-Table -AutoSize | Out-String -Width 400 | Set-Content -LiteralPath (Join-Path $logDir 'a01-summary.txt') -Encoding UTF8
$green = @($results | Where-Object { $_.runnerExit -eq 0 -and $_.failures -eq 0 -and $_.errors -eq 0 -and $_.skipped -eq 0 -and $_.targetSeen })
$red = @($results | Where-Object { -not ($_.runnerExit -eq 0 -and $_.failures -eq 0 -and $_.errors -eq 0 -and $_.skipped -eq 0 -and $_.targetSeen) })
Write-Line ('SUMMARY attempted={0} green={1} notGreen={2} [{3}]' -f $results.Count, $green.Count, $red.Count,
    (($red | ForEach-Object { $_.class }) -join ', '))
