param([Parameter(Mandatory)][ValidatePattern('^[a-zA-Z0-9._-]+$')][string]$Release)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$destination = Join-Path $root "outputs/r02/$Release"
if (Test-Path $destination) { throw 'Release directory exists; choose a new immutable name.' }
$jar = Join-Path $root 'server/build/libs/acrylic-outbound-server-0.1.0.jar'
$web = Join-Path $root 'web/dist'
$apk = Join-Path $root 'android/app/build/outputs/apk/staging/app-staging.apk'
foreach ($path in @($jar, "$web/index.html", $apk)) {
    if (-not (Test-Path $path)) { throw "Missing verified artifact: $path" }
}
New-Item -ItemType Directory -Path "$destination/deploy" -Force | Out-Null
Copy-Item $jar "$destination/server.jar"
Copy-Item $web "$destination/web" -Recurse
Get-ChildItem "$root/deploy/test" -File | ForEach-Object {
    $content = [IO.File]::ReadAllText($_.FullName).Replace("`r`n", "`n")
    [IO.File]::WriteAllText("$destination/deploy/$($_.Name)", $content, [Text.UTF8Encoding]::new($false))
}
$sources = @{}
Push-Location $root
try {
    $sourcePaths = @(& git -c core.quotepath=false ls-files --cached --others --exclude-standard)
    foreach ($path in $sourcePaths) {
        if (Test-Path -LiteralPath $path -PathType Leaf) {
            $sources[$path] = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()
        }
    }
    $manifest = [ordered]@{
        release = $Release; createdAt = [DateTime]::UtcNow.ToString('o')
        gitHead = (& git rev-parse HEAD); gitStatus = @(& git status --short)
        contract = '0.14.0'; flyway = 17; pda = '0.7.0-pairing-test'; room = 6
        environment = 'independent-test'; url = 'https://outbound-test.nbsmzwl.cn'
        sourceHashes = $sources
        serverSha256 = (Get-FileHash $jar).Hash.ToLowerInvariant()
        apkSha256 = (Get-FileHash $apk).Hash.ToLowerInvariant()
    }
    $manifest | ConvertTo-Json -Depth 5 | Set-Content "$destination/manifest.json" -Encoding utf8
    & tar -czf "$destination.tar.gz" -C $destination server.jar web deploy manifest.json
    if ($LASTEXITCODE) { throw 'Archive failed' }
    Copy-Item $apk "$destination.apk"
    Get-FileHash "$destination.tar.gz", "$destination.apk" | Select-Object Path, Hash
} finally { Pop-Location }
