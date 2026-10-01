$ErrorActionPreference = 'Stop'
$root = 'D:\acrylic-pda-outbound'
$log = Join-Path $root 'build/r07_p08_offline_check.log'
Push-Location $root
try {
    & cmd.exe /c 'gradlew.bat -p server testClasses --no-daemon --console=plain > build/r07_p08_offline_check.log 2>&1'
    $code = $LASTEXITCODE
} finally {
    Pop-Location
}
if ($code -ne 0) {
    Get-Content $log -Tail 120
    throw "Gradle testClasses failed with exit $code"
}
$parseFailures = @()
foreach ($fileInfo in Get-ChildItem (Join-Path $root 'tools') -Filter '*.ps1') {
    $tokens = $null
    $errors = $null
    $null = [System.Management.Automation.Language.Parser]::ParseFile($fileInfo.FullName, [ref]$tokens, [ref]$errors)
    if ($errors.Count -gt 0) {
        $parseFailures += [pscustomobject]@{ File = $fileInfo.FullName; Errors = $errors.Count }
    }
}
if ($parseFailures.Count -gt 0) {
    $parseFailures | Format-Table -AutoSize
    throw 'PowerShell parseErrors detected'
}
$migration = Get-Content -Raw (Join-Path $root 'server/src/main/resources/db/migration/V51__correction_append_only_guards.sql')
if (($migration -notmatch 'DELIMITER \$\$') -or ($migration -notmatch 'DELIMITER ;') -or ($migration -notmatch '<=>')) {
    throw 'V51 delimiter/null-safe comparison checks failed'
}
Write-Output 'compile=PASS parseErrors=0 migration-static-check=PASS'
