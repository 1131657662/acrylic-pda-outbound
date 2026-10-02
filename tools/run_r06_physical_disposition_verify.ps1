$ErrorActionPreference = 'Stop'
$gradle = Join-Path (Get-Location) 'gradlew.bat'
& $gradle -p server test `
    --tests com.acrylic.outbound.temporary.evidence.SourceEvidenceIntakeReadServiceTest `
    --tests com.acrylic.outbound.temporary.evidence.SourceEvidenceIntakeBusinessFactsTest `
    -x :report-rendering:prepareFont `
    --console=plain
if ($LASTEXITCODE -ne 0) { throw "R06 physical disposition verification failed ($LASTEXITCODE)" }
