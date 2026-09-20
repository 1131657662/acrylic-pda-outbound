param(
    [string]$GradleExecutable = (Join-Path $PSScriptRoot '../gradlew.bat'),
    [Parameter(ValueFromRemainingArguments = $true)][string[]]$GradleArguments = @('test')
)
$ErrorActionPreference = 'Stop'
$taskRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$scratchDir = Join-Path $taskRoot '.local/r08/tmp'
New-Item -ItemType Directory -Force $scratchDir | Out-Null
$env:GRADLE_USER_HOME = Join-Path $taskRoot '.local/r08/gradle'
$env:TEMP = $scratchDir
$env:TMP = $scratchDir
$env:PLAYWRIGHT_BROWSERS_PATH = Join-Path $PSScriptRoot 'build/browsers'
& $GradleExecutable --no-daemon -p $PSScriptRoot @GradleArguments
exit $LASTEXITCODE
