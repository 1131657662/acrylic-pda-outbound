$ErrorActionPreference = 'Stop'

$reproduciblePaths = @(
    'D:\acrylic-pda-outbound\.local\pda-download-avd',
    'D:\acrylic-pda-outbound\.local\pda-upgrade-avd',
    'D:\acrylic-pda-outbound\.local\pda-browser-install-avd',
    'D:\acrylic-pda-outbound\.local\server-runtime',
    'D:\acrylic-snapshots\r06-e2a-migration-full-20260926-2215\workspace\build',
    'D:\acrylic-snapshots\r06-e2a-migration-full-20260926-2215\workspace\server\build',
    'D:\acrylic-snapshots\r06-e2a-migration-full-20260926-2215\workspace\report-rendering\build',
    'D:\acrylic-entry-verify\server\build',
    'D:\acrylic-entry-verify\report-rendering\build'
)

$activeEmulators = Get-Process -Name emulator,emulator64-crash-service,qemu-system-x86_64 -ErrorAction SilentlyContinue
if ($activeEmulators) {
    $names = ($activeEmulators | Select-Object -ExpandProperty ProcessName) -join ', '
    throw "Refusing cleanup while emulator processes are active: $names"
}

function Get-ByteCount([string] $path) {
    if (-not (Test-Path -LiteralPath $path)) { return [int64] 0 }
    $item = Get-Item -LiteralPath $path -Force
    if (-not $item.PSIsContainer) { return [int64] $item.Length }
    $sum = (Get-ChildItem -LiteralPath $path -Force -Recurse -File -ErrorAction SilentlyContinue |
        Measure-Object -Property Length -Sum).Sum
    if ($null -eq $sum) { return [int64] 0 }
    return [int64] $sum
}

$before = @{}
foreach ($path in $reproduciblePaths) {
    $before[$path] = Get-ByteCount $path
}

foreach ($path in $reproduciblePaths) {
    if (Test-Path -LiteralPath $path) {
        Remove-Item -LiteralPath $path -Recurse -Force
    }
}

$rows = foreach ($path in $reproduciblePaths) {
    [pscustomobject]@{
        Path = $path
        BeforeBytes = $before[$path]
        RemainingBytes = Get-ByteCount $path
    }
}
$rows | ConvertTo-Json -Compress
