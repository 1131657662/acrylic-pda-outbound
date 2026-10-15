"""Read-only consumer for an E07 independent checkpoint.

The consumer never opens a database or network socket.  It validates the
recorded HTTP/fact vectors and the bytes preserved beside them, while taking
two manifests so a read-only run also proves that its input did not drift.
"""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
from typing import Any


REQUIRED = (
    "independent-checkpoint/scenario.json",
    "independent-checkpoint/intent.json",
    "final-facts.json",
    "checkpoints/before-association/rebuilt.json",
    "connected-identity.tsv",
    "shutdown-identity.tsv",
    "test-completed.marker",
)


def digest(path: Path) -> tuple[int, str]:
    value = hashlib.sha256()
    size = 0
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            size += len(block)
            value.update(block)
    return size, value.hexdigest()


def manifest(root: Path) -> list[dict[str, Any]]:
    entries = []
    for path in sorted(p for p in root.rglob("*") if p.is_file()):
        size, sha = digest(path)
        entries.append({"path": path.relative_to(root).as_posix(), "bytes": size, "sha256": sha})
    return entries


def read_json(root: Path, relative: str) -> Any:
    return json.loads((root / relative).read_text(encoding="utf-8"))


def check(condition: bool, failures: list[str], label: str) -> None:
    if not condition:
        failures.append(label)


def check_facts(facts: dict[str, Any], failures: list[str], label: str) -> dict[str, int]:
    tables = facts.get("tables", {})
    schemas = facts.get("schema", {})
    check(bool(tables), failures, f"{label}.tables.non_empty")
    check(set(tables) == set(schemas), failures, f"{label}.schema_keys")
    for name, table in tables.items():
        columns = table.get("columns", [])
        rows = table.get("rows", [])
        check(len(columns) == len(set(columns)), failures, f"{label}.table.{name}.unique_columns")
        check(all(len(row) == len(columns) for row in rows), failures, f"{label}.table.{name}.row_width")
    return {"tables": len(tables), "rows": sum(len(t.get("rows", [])) for t in tables.values())}


def check_fact_files(facts: dict[str, Any], storage: Path, failures: list[str]) -> int:
    label = "rebuilt" if "before-association" in storage.as_posix() else "final"
    entries = facts.get("files", {})
    for relative, expected in entries.items():
        path = storage / relative
        check(path.is_file(), failures, f"{label}.file.{relative}.exists")
        if path.is_file():
            size, sha = digest(path)
            check(size == expected.get("bytes"), failures, f"{label}.file.{relative}.bytes")
            check(sha == expected.get("sha256"), failures, f"{label}.file.{relative}.sha256")
    return len(entries)


def check_http(root: Path, failures: list[str]) -> dict[str, Any]:
    summary: dict[str, Any] = {}
    for status_path in sorted((root / "independent-checkpoint").glob("*.status")):
        value = status_path.read_text(encoding="utf-8").strip()
        check(value == "200", failures, f"http.{status_path.stem}.status")
        summary[status_path.stem] = {"status": value}
    for body_path in sorted((root / "independent-checkpoint").glob("*.body")):
        body = read_json(root, f"independent-checkpoint/{body_path.name}")
        check(body.get("code") == "OK", failures, f"http.{body_path.stem}.code")
        summary.setdefault(body_path.stem, {})["code"] = body.get("code")
    return summary


def check_identity(root: Path, failures: list[str]) -> dict[str, Any]:
    connected = (root / "connected-identity.tsv").read_text(encoding="utf-8").strip()
    shutdown = (root / "shutdown-identity.tsv").read_text(encoding="utf-8").strip()
    check(bool(connected), failures, "identity.connected_present")
    check(connected == shutdown, failures, "identity.shutdown_matches_connected")
    return {"connected": connected, "shutdown": shutdown}


def check_attachment(root: Path, attachment: dict[str, Any], failures: list[str]) -> None:
    attachment_path = root / "independent-checkpoint/attachment-original.bin"
    if not attachment_path.is_file():
        failures.append("attachment.original_present")
        return
    size, sha = digest(attachment_path)
    check(str(size) == str(attachment.get("sizeBytes")), failures, "attachment.bytes")
    check(sha == attachment.get("evidenceSha256"), failures, "attachment.sha256")


def check_inventory(root: Path, receipt: dict[str, Any], failures: list[str]) -> None:
    for name in ("initial-inventory", "full-candidates", "physical-inventory"):
        body = read_json(root, f"independent-checkpoint/{name}.body").get("data", {})
        check(bool(body.get("contextSha256")), failures, f"{name}.context_sha256_present")
        if name != "initial-inventory":
            check(body.get("contextSha256") == receipt.get("contextSha256"), failures, f"{name}.context_sha256")
        check(body.get("localDeletionAllowed") is False, failures, f"{name}.local_deletion_false")


def check_checkpoint_payloads(root: Path, context: dict[str, Any], failures: list[str]) -> None:
    scenario = context["scenario"]
    receipt = context["receipt"]
    receive = read_json(root, "independent-checkpoint/receive.body").get("data", {})
    check(receive.get("relayId") == scenario.get("relayId"), failures, "receive.relay_id")
    check(receive.get("archiveSha256") == receipt.get("archiveSha256"), failures, "receive.archive_sha256")
    check(receive.get("businessProcessed") is False, failures, "receive.business_processed_false")
    check(receive.get("localDeletionAllowed") is False, failures, "receive.local_deletion_false")
    attachment = read_json(root, "independent-checkpoint/attachment.body").get("data", {})
    check_attachment(root, attachment, failures)
    check_inventory(root, receipt, failures)
    association = read_json(root, "independent-checkpoint/association.body").get("data", {}).get("receipt", {})
    check(association.get("associationId") == receipt.get("associationId"), failures, "association.id")
    check(association.get("associationRecorded") is True, failures, "association.recorded")
    check(association.get("newCollectionAuthorized") is False, failures, "association.new_collection_false")
    check(association.get("releaseAllowed") is False, failures, "association.release_false")
    check(association.get("localDeletionAllowed") is False, failures, "association.local_deletion_false")


def validate(root: Path) -> tuple[dict[str, Any], list[str]]:
    failures: list[str] = []
    for relative in REQUIRED:
        check((root / relative).is_file(), failures, f"required.{relative}")
    scenario = read_json(root, REQUIRED[0])
    receipt = scenario.get("result", {}).get("receipt", {})
    final = read_json(root, "final-facts.json").get("facts", {})
    rebuilt = read_json(root, "checkpoints/before-association/rebuilt.json")
    final_counts = check_facts(final, failures, "final")
    rebuilt_counts = check_facts(rebuilt, failures, "rebuilt")
    check(final_counts["tables"] == rebuilt_counts["tables"], failures, "facts.table_count_preserved")
    check_fact_files(final, root / "final-storage", failures)
    check_fact_files(rebuilt, root / "checkpoints/before-association/final-storage", failures)
    http = check_http(root, failures)
    identities = check_identity(root, failures)
    check_checkpoint_payloads(root, {"scenario": scenario, "receipt": receipt}, failures)
    result = {
        "scenario": {key: scenario.get(key) for key in ("relayId", "targetId", "taskId", "warehouseId")},
        "receipt": {key: receipt.get(key) for key in ("associationId", "archiveSha256", "contextSha256", "pendingReconciliation", "releaseAllowed")},
        "facts": {"final": final_counts, "rebuilt": rebuilt_counts, "flywayRows": len(final.get("flywayHistory", []))},
        "http": http,
        "identity": identities,
        "failures": failures,
    }
    return result, failures


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    source = args.source.resolve()
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    before = manifest(source)
    result, failures = validate(source)
    after = manifest(source)
    unchanged = before == after
    check(unchanged, failures, "input_manifest_unchanged")
    result["input"] = {"root": str(source), "fileCount": len(before), "manifestBefore": before, "manifestAfter": after}
    result["scriptSha256"] = digest(Path(__file__).resolve())[1]
    result["readOnly"] = {"databaseConnection": False, "networkConnection": False, "inputUnchanged": unchanged}
    result["passed"] = not failures
    (output / "consumer-result.json").write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"passed": result["passed"], "failures": failures, "fileCount": len(before), "output": str(output)}, ensure_ascii=False))
    return 0 if result["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
