#requires -Version 5.1
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$arguments = @('-p', (Join-Path $root 'server'), 'test', '--no-daemon')
& (Join-Path $root 'gradlew.bat') @arguments
if ($LASTEXITCODE -ne 0) { throw "Server regression failed with exit code $LASTEXITCODE" }
