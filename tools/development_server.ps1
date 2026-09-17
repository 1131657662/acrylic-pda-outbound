param([ValidateSet('Verify','Build','Bootstrap','Run')][string]$Task = 'Verify')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$settingsPath = Join-Path $projectRoot '.local/development.json'
if (-not (Test-Path -LiteralPath $settingsPath)) { throw 'Run python tools/start_development_db.py first.' }
$settings = Get-Content -Raw -LiteralPath $settingsPath | ConvertFrom-Json
$env:OUTBOUND_DB_USER = 'outbound_local'
$env:OUTBOUND_DB_PASSWORD = $settings.dbPassword
$databaseName = if ($Task -eq 'Verify') { 'acrylic_outbound_test' } else { 'acrylic_outbound_dev' }
$env:OUTBOUND_DB_URL = "jdbc:mysql://127.0.0.1:$($settings.dbPort)/${databaseName}?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&rewriteBatchedStatements=true"
$env:OUTBOUND_STORAGE = Join-Path $projectRoot ".local/private-$databaseName"
$env:OUTBOUND_COOKIE_SECURE = 'false'
$env:OUTBOUND_MIGRATE = 'true'
$signingDirectory = Join-Path $projectRoot '.local/pda-signing'
& java (Join-Path $PSScriptRoot 'CreateDevelopmentSigningKey.java') $signingDirectory
if ($LASTEXITCODE -ne 0) { throw 'Development signing key initialization failed' }
$env:OUTBOUND_AUTH_SIGNING_PRIVATE_KEY = Join-Path $signingDirectory 'private.pk8'
$env:OUTBOUND_AUTH_SIGNING_PUBLIC_KEY = Join-Path $signingDirectory 'public.der'
$cachedGradle = Get-ChildItem (Join-Path $env:USERPROFILE '.gradle/wrapper/dists/gradle-8.11.1-bin') -Filter gradle.bat -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1
$gradleCommand = if ($cachedGradle) { $cachedGradle.FullName } else { Join-Path $projectRoot 'gradlew.bat' }
$serverDir = Join-Path $projectRoot 'server'
switch ($Task) {
    'Verify' { & $gradleCommand -p $serverDir test integrationTest bootJar --console=plain }
    'Build' { & $gradleCommand -p $serverDir test bootJar --console=plain }
    'Bootstrap' {
        $env:OUTBOUND_BOOTSTRAP_USERNAME = $settings.adminUsername
        $env:OUTBOUND_BOOTSTRAP_PASSWORD = $settings.adminPassword
        & java '-Duser.timezone=UTC' -jar "$serverDir/build/libs/acrylic-outbound-server-0.1.0.jar" '--spring.main.web-application-type=none' '--outbound.bootstrap.enabled=true'
    }
    'Run' {
        $runtimeDirectory = Join-Path $projectRoot '.local/server-runtime'
        New-Item -ItemType Directory -Path $runtimeDirectory -Force | Out-Null
        $runtimeJar = Join-Path $runtimeDirectory ("acrylic-outbound-" + [guid]::NewGuid().ToString() + '.jar')
        Copy-Item -LiteralPath "$serverDir/build/libs/acrylic-outbound-server-0.1.0.jar" -Destination $runtimeJar
        & java '-Duser.timezone=UTC' -jar $runtimeJar
    }
}
if ($LASTEXITCODE -ne 0) { throw "Server task $Task failed ($LASTEXITCODE)" }
