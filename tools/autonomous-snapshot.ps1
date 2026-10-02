param([Parameter(Mandatory=$true)][string]$Name, [switch]$ArchiveBoard)
$ErrorActionPreference = 'Stop'
$workspace = Split-Path -Parent $PSScriptRoot
$target = Join-Path $workspace ('.local/autonomous-20261002/' + $Name)
if (Test-Path -LiteralPath $target) { throw 'Snapshot already exists.' }
New-Item -ItemType Directory -Path $target | Out-Null
git -C $workspace status --short --branch --untracked-files=all | Set-Content (Join-Path $target 'status.txt')
git -C $workspace diff --binary | Set-Content (Join-Path $target 'tracked.patch')
git -C $workspace worktree list --porcelain | Set-Content (Join-Path $target 'worktrees.txt')
git -C $workspace rev-parse HEAD origin/codex/development | Set-Content (Join-Path $target 'heads.txt')
$dirty = @(git -C $workspace ls-files -m -o --exclude-standard)
$hashes = foreach ($path in $dirty) {
    $file = Join-Path $workspace $path
    if (Test-Path -LiteralPath $file -PathType Leaf) {
        [pscustomobject]@{path=$path;sha256=(Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash}
    }
}
$hashes | ConvertTo-Json | Set-Content (Join-Path $target 'dirty-hashes.json')
Get-PSDrive -PSProvider FileSystem | Select-Object Name,Free | ConvertTo-Json |
    Set-Content (Join-Path $target 'disks.json')
Get-NetTCPConnection -State Listen | Select-Object LocalAddress,LocalPort,OwningProcess | ConvertTo-Json |
    Set-Content (Join-Path $target 'listeners.json')
Get-CimInstance Win32_Process | Where-Object { $_.Name -match 'java|mysqld|pwsh|powershell|adb' } |
    Select-Object ProcessId,ParentProcessId,Name | ConvertTo-Json | Set-Content (Join-Path $target 'processes.json')
if ($ArchiveBoard) {
    $archive = Join-Path $workspace ('docs/history/ORCHESTRATION_STATE_before_' + $Name + '_2026-10-02.md')
    if (Test-Path -LiteralPath $archive) { throw 'Board archive already exists.' }
    Copy-Item -LiteralPath (Join-Path $workspace 'docs/ORCHESTRATION_STATUS.md') -Destination $archive
    $progressArchive = Join-Path $workspace ('docs/history/AUTONOMOUS_PROGRESS_2026-10-02_before_' + $Name + '.md')
    if (Test-Path -LiteralPath $progressArchive) { throw 'Progress archive already exists.' }
    Copy-Item -LiteralPath (Join-Path $workspace 'docs/history/AUTONOMOUS_PROGRESS_2026-10-02.md') -Destination $progressArchive
}
Write-Host ('Snapshot: ' + $target)
