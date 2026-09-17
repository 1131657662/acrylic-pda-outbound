"""Preserve an already stopped application's DB/WAL and inspect its selected order offline."""
import gzip
import json
import shutil
import sqlite3
import subprocess
import tarfile
import tempfile
from contextlib import closing
from pathlib import Path
from demo_control import PACKAGE


def snapshot(device, target):
    command = device.prefix + ["exec-out", "run-as", PACKAGE, "tar", "-cf", "-",
                               "databases", "shared_prefs", "files"]
    # Stream the full DB; device-side tar -z was observed to end with a truncated gzip stream.
    with tempfile.TemporaryFile() as errors:
        with subprocess.Popen(command, stdout=subprocess.PIPE, stderr=errors) as process:
            with gzip.open(target, "xb", compresslevel=3) as output:
                shutil.copyfileobj(process.stdout, output, length=1024 * 1024)
            status = process.wait()
        if status:
            errors.seek(0)
            raise RuntimeError(f"Incomplete snapshot retained: {errors.read().decode(errors='replace')}")


def order_facts(archive, order_id):
    with tempfile.TemporaryDirectory(prefix="m71-fault-evidence-") as directory:
        with tarfile.open(archive) as source:
            source.extractall(directory, filter="data")
        with closing(sqlite3.connect(Path(directory) / "databases/m71-demo.db")) as db:
            db.row_factory = sqlite3.Row
            result = {"integrity": [row[0] for row in db.execute("pragma integrity_check")],
                      "foreignKeys": [list(row) for row in db.execute("pragma foreign_key_check")]}
            for table in ("local_order", "local_order_line", "local_input_capture", "local_scan", "local_barcode_guard", "local_operation_event", "local_submission"):
                column = "id" if table == "local_order" else "orderId"
                result[table] = [dict(row) for row in db.execute(f'SELECT * FROM "{table}" WHERE "{column}"=?', (order_id,))]
            return result


def verify_saved(facts, capture_id, expected_count=1):
    assert facts["integrity"] == ["ok"] and facts["foreignKeys"] == []
    scans = [r for r in facts["local_scan"] if r["id"] == capture_id]
    assert len(scans) == len(facts["local_scan"]) == expected_count
    assert len(facts["local_input_capture"]) == len(facts["local_barcode_guard"]) == expected_count
    assert facts["local_order"][0]["actualCount"] == expected_count
    if not expected_count:
        assert facts["local_order"][0]["weightGrams"] == 0
        assert not any(r["captureId"] == capture_id for r in facts["local_operation_event"])
        return
    scan = scans[0]
    capture = next(r for r in facts["local_input_capture"] if r["id"] == capture_id)
    assert capture["state"] == "COMMITTED"
    for key in ("originDeviceId", "deviceCaptureSeq", "orderScanSeq", "expectedDispatchCycle", "barcode", "confirmationPolicy"):
        assert capture[key] == scan[key]
    assert sum(r["scanId"] == capture_id for r in facts["local_barcode_guard"]) == 1
    audit = [r for r in facts["local_operation_event"] if r["captureId"] == capture_id]
    assert sum(r["type"] == "SAVED" for r in audit) == 1
    assert sum(r["type"] == "CONFIRM_REQUESTED" for r in audit) == 1
    active = [r for r in facts["local_scan"] if r["status"] == "ACTIVE"]
    assert facts["local_order"][0]["actualCount"] == len(active)
    assert facts["local_order"][0]["weightGrams"] == sum(r["weightGrams"] for r in active)


def verify_context_unchanged(before, after):
    fields = ("id", "orderId", "originDeviceId", "deviceCaptureSeq", "orderScanSeq", "rawInput", "barcode",
              "receivedAt", "receivedElapsedNs", "bootSessionId", "snapshotId", "expectedDispatchCycle",
              "sourceRecoveryEpoch", "authorizationId", "orderRevision", "assignmentEpoch", "confirmationPolicy")
    current = {r["id"]: r for r in after["local_input_capture"]}
    for row in before["local_input_capture"]:
        assert tuple(row[k] for k in fields) == tuple(current[row["id"]][k] for k in fields), "Recovery changed original input context"
