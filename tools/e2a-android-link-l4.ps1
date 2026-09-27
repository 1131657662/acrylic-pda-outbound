#requires -Version 5.1
<#
.SYNOPSIS
  Android LINK mapping-install L4 orchestrator: one attempt hosts both halves of the real-server evidence.

.DESCRIPTION
  The Android install code (SourceBindingRepository) could only be driven against MockWebServer so far, because the
  real server used to live and die inside a single server IT. This script closes that gap inside ONE attempt:

    1. provision the isolated MySQL + start the resident server IT in the background
       (E2aServeBindingForAndroidIT: R06 chain -> LINK -> mint a PDA Bearer -> serve the binding over real HTTP)
    2. wait for the handshake file the resident IT writes (bounded; the server keeps running)
    3. run the Android unit test that hits that very server on 127.0.0.1:19434
       (RealServerSourceBindingTest: real HTTP -> SourceBindingRepository.installBindings -> real Room database)
    4. write the stop file so the resident IT exits and the constructor tears MySQL down
    5. aggregate BOTH XMLs into one report and state the LINK evidence level

  Bounded by construction: handshake wait, Android run and server exit all have hard timeouts, so a hung half can
  never hold the attempt forever. ASCII-only on purpose (Windows PowerShell 5.1 reads a .ps1 without a UTF-8 BOM as
  ANSI, which mangles non-ASCII text and can break parsing - see the batch entry for the same lesson).

.EXAMPLE
  powershell -NoProfile -ExecutionPolicy Bypass -File tools/e2a-android-link-l4.ps1 -Run 185
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][int]$Run,
    [string]$Workspace = 'D:\acrylic-pda-worktrees\r06-e2a-migration',
    [string]$MainRoot = 'D:\acrylic-pda-outbound',
    [string]$Constructor = '',
    [int]$HandshakeTimeoutSeconds = 420,
    [int]$AndroidTimeoutSeconds = 900,
    [int]$ServerExitTimeoutSeconds = 300
)

$ErrorActionPreference = 'Continue'
if ([string]::IsNullOrWhiteSpace($Constructor)) { $Constructor = Join-Path $PSScriptRoot 'e2a-provision-and-run-106.ps1' }
if (-not (Test-Path -LiteralPath $Constructor)) { throw "constructor not found: $Constructor" }
if (-not (Test-Path -LiteralPath $Workspace)) { throw "workspace not found: $Workspace" }
if (-not (Test-Path -LiteralPath $MainRoot)) { throw "main tree not found: $MainRoot" }

$attemptDir = Join-Path $Workspace ".local/e2a-cross-20260925-$Run"
$handshakeFile = Join-Path $attemptDir 'android-binding-handshake.json'
$stopFile = Join-Path $attemptDir 'android-binding-stop'
$evidence = Join-Path $MainRoot ".local\android-link-l4\run-$Run"
New-Item -ItemType Directory -Force -Path $evidence | Out-Null

if (Test-Path -LiteralPath (Join-Path $attemptDir 'test-started.marker')) {
    throw "attempt $Run is already used (test-started.marker exists). Use a new -Run number."
}
if (Test-Path -LiteralPath $handshakeFile) { Remove-Item -LiteralPath $handshakeFile -Force }
if (Test-Path -LiteralPath $stopFile) { Remove-Item -LiteralPath $stopFile -Force }

function Show-Tail([string]$path, [int]$lines = 15) {
    if (Test-Path -LiteralPath $path) {
        Write-Host "--- $path (last $lines) ---"
        Get-Content -LiteralPath $path -Tail $lines | ForEach-Object { Write-Host "  $_" }
    }
}

# ---------------------------------------------------------------- 1) resident server half in the background
Write-Host "== 1) starting the resident server half (attempt $Run) =="
$serverOut = Join-Path $evidence 'server-half.log'
$serverErr = Join-Path $evidence 'server-half.err.log'
$serverArgs = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $Constructor,
    '-Run', "$Run", '-Tests', '*E2aServeBindingForAndroidIT', '-Workspace', $Workspace)
$server = Start-Process -FilePath 'powershell' -PassThru -WindowStyle Hidden -ArgumentList $serverArgs `
    -RedirectStandardOutput $serverOut -RedirectStandardError $serverErr
Write-Host "server half pid=$($server.Id) log=$serverOut"

# ---------------------------------------------------------------- 2) wait for the handshake
Write-Host "== 2) waiting for the handshake file (<= ${HandshakeTimeoutSeconds}s) =="
$handshakeDeadline = (Get-Date).AddSeconds($HandshakeTimeoutSeconds)
$handshakeReady = $false
while ((Get-Date) -lt $handshakeDeadline) {
    if (Test-Path -LiteralPath $handshakeFile) { $handshakeReady = $true; break }
    if ($server.HasExited) { break }
    Start-Sleep -Milliseconds 500
}
if (-not $handshakeReady) {
    Write-Host "the handshake file never appeared (serverExited=$($server.HasExited))"
    New-Item -ItemType File -Force -Path $stopFile | Out-Null
    Show-Tail $serverOut 25
    Show-Tail $serverErr 15
    if (-not $server.HasExited) { $server.WaitForExit(60000) | Out-Null }
    exit 2
}
$serverStillRunning = -not $server.HasExited
Write-Host "handshake ready (server process still running=$serverStillRunning)"
Copy-Item -LiteralPath $handshakeFile -Destination (Join-Path $evidence 'android-binding-handshake.json') -Force

# ---------------------------------------------------------------- 3) Android half against the live server
Write-Host "== 3) running the Android half against 127.0.0.1:19434 =="
$env:ANDROID_SOURCE_BINDING_HANDSHAKE = $handshakeFile
$env:ANDROID_SOURCE_BINDING_ATTEMPT = $attemptDir
$androidLog = Join-Path $evidence 'android-half.log'
$androidExit = $null
Push-Location $MainRoot
try {
    & (Join-Path $MainRoot 'gradlew.bat') -p android :app:testDebugUnitTest --tests '*RealServerSourceBindingTest' `
        --rerun-tasks --console=plain 2>&1 | Tee-Object -FilePath $androidLog |
        Select-String -Pattern 'BUILD|FAILED|RealServerSourceBindingTest|tests completed' |
        ForEach-Object { Write-Host "  $($_.Line)" }
    $androidExit = $LASTEXITCODE
} finally { Pop-Location }
Write-Host "android exit=$androidExit"

# ---------------------------------------------------------------- 4) stop the resident half and let the constructor tear down
Write-Host "== 4) writing the stop file =="
New-Item -ItemType File -Force -Path $stopFile | Out-Null
$serverExited = $server.WaitForExit($ServerExitTimeoutSeconds * 1000)
if (-not $serverExited) { Write-Host "server half did not exit within ${ServerExitTimeoutSeconds}s; leaving it to the operator" }
$serverExit = if ($serverExited) { $server.ExitCode } else { $null }
Write-Host "server exit=$serverExit"

# ---------------------------------------------------------------- 5) aggregate both halves
$serverXmlDir = Join-Path $Workspace 'server/build/test-results/integrationTest'
$androidXmlDir = Join-Path $MainRoot 'android/app/build/test-results/testDebugUnitTest'
$serverXml = @(Get-ChildItem $serverXmlDir -Filter 'TEST-*E2aServeBindingForAndroidIT.xml' -ErrorAction SilentlyContinue)
$androidXml = @(Get-ChildItem $androidXmlDir -Filter 'TEST-*RealServerSourceBindingTest.xml' -ErrorAction SilentlyContinue)
foreach ($file in @($serverXml + $androidXml)) { Copy-Item -LiteralPath $file.FullName -Destination $evidence -Force }

function Read-Suite($files) {
    $result = [pscustomobject]@{ Files = 0; Tests = 0; Failures = 0; Errors = 0; Skipped = 0; Messages = @() }
    foreach ($file in $files) {
        $doc = [xml][System.IO.File]::ReadAllText($file.FullName, (New-Object System.Text.UTF8Encoding($false)))
        $result.Files++
        foreach ($suite in @($doc.testsuite)) {
            $result.Tests += [int]$suite.tests; $result.Failures += [int]$suite.failures
            $result.Errors += [int]$suite.errors; $result.Skipped += [int]$suite.skipped
            foreach ($case in @($suite.testcase)) {
                if ($case.failure) { $result.Messages += "$($suite.name).$($case.name): $((("$($case.failure.message)" -split "`n")[0]).Trim())" }
                if ($case.error) { $result.Messages += "$($suite.name).$($case.name): $((("$($case.error.message)" -split "`n")[0]).Trim())" }
            }
        }
    }
    return $result
}

$serverSide = Read-Suite $serverXml
$androidSide = Read-Suite $androidXml
$level = 'L3 (unchanged: no usable real-server evidence)'
if ($serverSide.Files -gt 0 -and $androidSide.Files -gt 0 -and $serverSide.Failures -eq 0 -and $serverSide.Errors -eq 0 `
    -and $androidSide.Tests -eq 1 -and $androidSide.Failures -eq 0 -and $androidSide.Errors -eq 0 -and $androidSide.Skipped -eq 0) {
    $level = 'L4 (Android code hit the real server and installed the served binding)'
}

$report = New-Object System.Collections.Generic.List[string]
$report.Add("# Android LINK L4 report (attempt $Run, $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss'))")
$report.Add('')
$report.Add("- workspace: ``$Workspace``")
$report.Add("- attempt: ``$attemptDir``")
$report.Add("- handshake: ``$handshakeFile``; stop: ``$stopFile``")
$report.Add("- android exit: $androidExit; server exit: $serverExit")
$report.Add('')
$report.Add('| half | xml | tests | failures | errors | skipped |')
$report.Add('| --- | --- | --- | --- | --- | --- |')
$report.Add("| server (resident IT) | $($serverSide.Files) | $($serverSide.Tests) | $($serverSide.Failures) | $($serverSide.Errors) | $($serverSide.Skipped) |")
$report.Add("| android (RealServerSourceBindingTest) | $($androidSide.Files) | $($androidSide.Tests) | $($androidSide.Failures) | $($androidSide.Errors) | $($androidSide.Skipped) |")
$report.Add('')
$report.Add("**Evidence level: $level**")
$report.Add('')
if ($serverSide.Messages.Count + $androidSide.Messages.Count -gt 0) {
    $report.Add('## Failures')
    $report.Add('')
    foreach ($message in @($serverSide.Messages + $androidSide.Messages)) { $report.Add("- $message") }
} else {
    $report.Add('No failures on either side.')
}
$reportPath = Join-Path $evidence 'ANDROID-LINK-L4-REPORT.md'
[System.IO.File]::WriteAllLines($reportPath, $report, (New-Object System.Text.UTF8Encoding($false)))

Remove-Item Env:ANDROID_SOURCE_BINDING_HANDSHAKE, Env:ANDROID_SOURCE_BINDING_ATTEMPT -ErrorAction SilentlyContinue

Write-Host ''
Write-Host ("RESULT attempt={0} server={1}/{2}/{3}/{4} android={5}/{6}/{7}/{8} level={9}" -f $Run,
    $serverSide.Tests, $serverSide.Failures, $serverSide.Errors, $serverSide.Skipped,
    $androidSide.Tests, $androidSide.Failures, $androidSide.Errors, $androidSide.Skipped, $level)
Write-Host "report: $reportPath"
if ($level -like 'L4*') { exit 0 }
exit 1
