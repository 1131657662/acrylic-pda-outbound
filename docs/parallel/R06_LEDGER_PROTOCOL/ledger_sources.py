"""Bind all compiled production bytes to an explicitly reviewed source manifest."""
import json
from pathlib import Path
from ledger_primitives import ROOT, raw_digest, require

BASE_SOURCES = (
    "common/BusinessException.java", "common/JsonText.java", "devices/DeviceCrypto.java",
    "inventory/ImportFiles.java", "pda/OfflineSigner.java", "submissions/SubmissionJson.java",
)
COMMAND_SOURCES = (
    "CommandContext.java", "CommandErrors.java", "CommandInput.java", "CommandReceiptCodec.java",
    "CommandReceiptShape.java", "CommandSignature.java", "CommandWrite.java",
)


def production_sources(repository):
    package = repository / "server/src/main/java/com/acrylic/outbound"
    sources = [package / name for name in BASE_SOURCES]
    sources.extend(sorted((package / "temporary/protocol").glob("*.java")))
    sources.extend(package / "temporary/command" / name for name in COMMAND_SOURCES)
    return sources


def validate_sources(repository):
    manifest_path = ROOT / "source_manifest.json"
    require(manifest_path.is_file(), "SOURCE_NOT_FROZEN")
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    require(len(manifest["productionCommit"]) == 40, "SOURCE_COMMIT_REQUIRED")
    sources = production_sources(repository)
    actual = {}
    for source in sources:
        raw = source.read_bytes().replace(b"\r\n", b"\n")
        actual[source.relative_to(repository).as_posix()] = raw_digest(raw)
    require(actual == manifest["lfSha256"], "PRODUCTION_SOURCE_CHANGED")
    return sources
