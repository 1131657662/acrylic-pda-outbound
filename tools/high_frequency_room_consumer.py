"""Independently consume stopped high-frequency Android Room snapshots."""

import argparse
import hashlib
import json
import sqlite3
import tarfile
import tempfile
from pathlib import Path


DATABASE = Path("databases/outbound-pda.db")
GUARDED_TABLES = (
    "temporary_handover_confirmation",
    "temporary_handover_activation",
    "temporary_recovered_confirmation",
    "temporary_submission",
)
EXPECTED = {
    "scan": {"drafts": 1, "captures": 2, "scans": 1, "guards": 1},
    "save-scan": {"drafts": 2, "captures": 4, "scans": 2, "guards": 2},
    "preparation": {},
    "home": {},
}
SENSITIVE_FIELDS = {"ackBodyJson", "ackJson", "bodyJson", "receiptJson", "signingKeyJson"}


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def extract_database(archive_path: Path, directory: Path) -> Path:
    with tarfile.open(archive_path) as archive:
        members = archive.getmembers()
        for member in members:
            parts = Path(member.name).parts
            if Path(member.name).is_absolute() or ".." in parts:
                raise ValueError(f"unsafe archive member: {member.name}")
        archive.extractall(directory, filter="data")
    database = directory / DATABASE
    if not database.is_file():
        raise ValueError(f"missing Room database: {DATABASE}")
    return database


def table_rows(database: sqlite3.Connection, table: str) -> list[sqlite3.Row]:
    return database.execute(f'SELECT * FROM "{table}"').fetchall()


def serializable_value(value: object, field: str | None = None) -> object:
    if isinstance(value, bytes):
        return {"bytes": len(value), "sha256": hashlib.sha256(value).hexdigest()}
    if field in SENSITIVE_FIELDS and isinstance(value, str):
        return {"chars": len(value), "sha256": hashlib.sha256(value.encode("utf-8")).hexdigest()}
    return value


def row_dict(row: sqlite3.Row) -> dict:
    return {key: serializable_value(row[key], key) for key in row.keys()}


def collect_snapshot(database: sqlite3.Connection) -> dict:
    database.row_factory = sqlite3.Row
    tables = {
        name: table_rows(database, name)
        for name in ("temporary_draft", "temporary_capture", "temporary_scan",
                     "temporary_guard", "temporary_event", "temporary_submission",
                     "temporary_preparation", "temporary_request", "temporary_part_chunk",
                     *GUARDED_TABLES)
    }
    return {
        "integrity": [row[0] for row in database.execute("PRAGMA integrity_check")],
        "foreignKeys": [list(row) for row in database.execute("PRAGMA foreign_key_check")],
        "roomVersion": database.execute("PRAGMA user_version").fetchone()[0],
        "counts": {name: len(rows) for name, rows in tables.items()},
        "states": [row["state"] for row in tables["temporary_capture"]],
        "scanStatuses": [row["status"] for row in tables["temporary_scan"]],
        "draftStates": [row["state"] for row in tables["temporary_draft"]],
        "captures": [row_dict(row) for row in tables["temporary_capture"]],
        "scans": [row_dict(row) for row in tables["temporary_scan"]],
        "guards": [row_dict(row) for row in tables["temporary_guard"]],
        "events": [row_dict(row) for row in tables["temporary_event"]],
        "preparations": [row_dict(row) for row in tables["temporary_preparation"]],
        "requests": [row_dict(row) for row in tables["temporary_request"]],
        "partChunks": [row_dict(row) for row in tables["temporary_part_chunk"]],
    }


def assert_common(snapshot: dict) -> None:
    if snapshot["integrity"] != ["ok"] or snapshot["foreignKeys"]:
        raise AssertionError("Room integrity or foreign-key check failed")
    if snapshot["roomVersion"] <= 0:
        raise AssertionError("Room schema version is empty")
    if any(state != "SCANNING" for state in snapshot["draftStates"]):
        raise AssertionError("high-frequency drafts are not still scanning")
    if any(snapshot["counts"][table] for table in GUARDED_TABLES):
        raise AssertionError("cancelled handover/submission chain wrote a row")


def assert_capture_shape(snapshot: dict, expected: dict) -> None:
    counts = snapshot["counts"]
    table_names = {"drafts": "temporary_draft", "captures": "temporary_capture",
                   "scans": "temporary_scan", "guards": "temporary_guard"}
    for name, value in expected.items():
        actual = counts[table_names[name]]
        if actual != value:
            raise AssertionError(f"{name}: expected {value}, got {actual}")
    if snapshot["states"].count("COMMITTED") != expected["scans"]:
        raise AssertionError("each saved scan must have one committed capture")
    if snapshot["states"].count("INVALID") != expected["scans"]:
        raise AssertionError("each duplicate input must have one invalid capture")
    if snapshot["scanStatuses"] != ["ACTIVE"] * expected["scans"]:
        raise AssertionError("saved scans are not all active")
    if len({row["barcode"] for row in snapshot["guards"]}) != expected["guards"]:
        raise AssertionError("barcode guard count is not unique")


def assert_relations(snapshot: dict) -> None:
    committed = {row["id"] for row in snapshot["captures"] if row["state"] == "COMMITTED"}
    invalid = [row for row in snapshot["captures"] if row["state"] == "INVALID"]
    scan_ids = {row["captureId"] for row in snapshot["scans"]}
    if committed != scan_ids:
        raise AssertionError("saved scan ids do not match committed captures")
    if any(row["id"] in scan_ids or not row["failureAckAt"] for row in invalid):
        raise AssertionError("duplicate capture was not acknowledged as invalid")
    if len({row["captureId"] for row in snapshot["guards"]}) != len(snapshot["guards"]):
        raise AssertionError("barcode guards point at duplicate captures")
    sequences = [row["deviceCaptureSeq"] for row in snapshot["captures"]]
    if sequences != sorted(sequences) or sequences != list(range(1, len(sequences) + 1)):
        raise AssertionError("device capture sequence is not contiguous")


def assert_preparation_shape(snapshot: dict) -> None:
    counts = snapshot["counts"]
    expected = {
        "temporary_preparation": 1,
        "temporary_request": 1,
        "temporary_part_chunk": 5,
    }
    for table, value in expected.items():
        if counts[table] != value:
            raise AssertionError(f"{table}: expected {value}, got {counts[table]}")
    for table in ("temporary_draft", "temporary_capture", "temporary_scan",
                  "temporary_guard", "temporary_event", "temporary_submission"):
        if counts[table] != 0:
            raise AssertionError(f"preparation/home snapshot unexpectedly contains {table}")
    preparation = snapshot["preparations"][0]
    if preparation["inventoryRows"] != 2 or preparation["availabilityRows"] != 2:
        raise AssertionError("preparation inventory facts are incomplete")
    for key in ("authorizationId", "requestKey", "bundleId", "manifestSha256", "ackJson"):
        if not preparation[key]:
            raise AssertionError(f"preparation field is empty: {key}")
    request = snapshot["requests"][0]
    for key in ("key", "bodyJson", "signingKeyJson", "receiptJson"):
        if not request[key]:
            raise AssertionError(f"preparation request field is empty: {key}")
    chunk_names = {row["part"] for row in snapshot["partChunks"]}
    if chunk_names != {"inventory", "mapping", "policy", "rules", "availability"}:
        raise AssertionError("preparation part names are incomplete")


def consume(archive_path: Path, kind: str) -> dict:
    if kind not in EXPECTED:
        raise ValueError(f"unknown snapshot kind: {kind}")
    before = digest(archive_path)
    temp_root = archive_path.parent / "python-room-tmp"
    temp_root.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="high-frequency-room-", dir=temp_root) as directory:
        database_path = extract_database(archive_path, Path(directory))
        database = sqlite3.connect(f"file:{database_path}?mode=ro", uri=True)
        try:
            snapshot = collect_snapshot(database)
        finally:
            database.close()
    assert_common(snapshot)
    if kind in ("scan", "save-scan"):
        assert_capture_shape(snapshot, EXPECTED[kind])
        assert_relations(snapshot)
    else:
        assert_preparation_shape(snapshot)
    after = digest(archive_path)
    if before != after:
        raise AssertionError("input tar changed during read-only consumption")
    return {"archive": str(archive_path), "kind": kind, "inputSha256": before,
            "inputUnchanged": True, **snapshot}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("archive", type=Path)
    parser.add_argument("--kind", choices=sorted(EXPECTED), required=True)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    result = consume(args.archive, args.kind)
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({"kind": args.kind, "inputSha256": result["inputSha256"],
                      "counts": result["counts"], "inputUnchanged": True}, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
