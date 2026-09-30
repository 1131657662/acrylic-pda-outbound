param([Parameter(Mandatory=$true)][ValidateRange(1,999)][int]$Run, [string]$Root = 'D:\acrylic-pda-outbound')
# Falsification harness for the M2b-2b manual-confirmation guard.
# It byte-removes ONLY the reviewed-candidate digest comparison from TemporaryLinkOperation.java,
# runs the isolated link IT (which must then fail its rejection case), and restores the file by bytes.
$ErrorActionPreference = 'Stop'
$root = $Root
$source = Join-Path $root 'server\src\main\java\com\acrylic\outbound\temporary\command\TemporaryLinkOperation.java'
$guard = @'
        if(!reviewed.snapshot().path("contextSha256").equals(command.expected().path("candidateContextSha256"))
            || !reviewed.snapshot().path("total").asText().equals("1") || !reviewed.heads().getFirst().id().equals(head.id()))
            throw CommandErrors.stale();
'@
$reduced = @'
        if(!reviewed.snapshot().path("total").asText().equals("1") || !reviewed.heads().getFirst().id().equals(head.id()))
            throw CommandErrors.stale();
'@
$backup = Join-Path $root ".local\link-counterproof-$Run"
if (Test-Path -LiteralPath $backup) { throw "Counter-proof attempt already exists: $backup" }
New-Item -ItemType Directory -Path $backup | Out-Null
$original = [IO.File]::ReadAllText($source)
if (-not $original.Contains($guard)) { throw 'Expected guard text is absent; refusing to patch' }
[IO.File]::WriteAllBytes((Join-Path $backup 'TemporaryLinkOperation.java.original'),[IO.File]::ReadAllBytes($source))
$originalSha = (Get-FileHash -LiteralPath (Join-Path $backup 'TemporaryLinkOperation.java.original') -Algorithm SHA256).Hash
[IO.File]::WriteAllText($source,$original.Replace($guard,$reduced))
[IO.File]::WriteAllBytes((Join-Path $backup 'TemporaryLinkOperation.java.reduced'),[IO.File]::ReadAllBytes($source))
$reducedSha = (Get-FileHash -LiteralPath (Join-Path $backup 'TemporaryLinkOperation.java.reduced') -Algorithm SHA256).Hash
[IO.File]::WriteAllText((Join-Path $backup 'hashes.txt'),"original=$originalSha`nreduced=$reducedSha`n")
$exit = 1
try {
    & (Join-Path $root 'tools\e2a-cross-source-verify.ps1') -Run $Run -Link
    $exit = $LASTEXITCODE
} finally {
    [IO.File]::WriteAllBytes($source,[IO.File]::ReadAllBytes((Join-Path $backup 'TemporaryLinkOperation.java.original')))
    $restoredSha = (Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash
    [IO.File]::WriteAllText((Join-Path $backup 'hashes.txt'),"original=$originalSha`nreduced=$reducedSha`nrestored=$restoredSha`ncounterproofExit=$exit`n")
    if ($restoredSha -ne $originalSha) { throw "Restore mismatch: $restoredSha" }
}
Write-Output "counterproofExit=$exit; original/restored SHA256=$originalSha; reduced SHA256=$reducedSha"
exit $exit
