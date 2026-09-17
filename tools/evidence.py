"""Read immutable Demo evidence; standard library only, never modifies input."""
import hashlib
import json
from collections import Counter
from decimal import Decimal
from pathlib import Path


def rows(path):
    with Path(path).open(encoding="utf-8") as source:
        return [json.loads(line) for line in source if line.strip()]


def digest(path):
    with Path(path).open("rb") as source:
        return hashlib.file_digest(source, "sha256").hexdigest()


def unique(items, fields, errors, label):
    values = [tuple(item[key] for key in fields) for item in items]
    if len(values) != len(set(values)):
        errors.append(f"{label}: duplicate {fields}")


def read_export(directory):
    root = Path(directory)
    manifest = json.loads((root / "manifest.json").read_text(encoding="utf-8"))
    if manifest.get("purpose") != "DEMO_EVIDENCE_NOT_CLOUD_SUBMISSION":
        raise ValueError("Not a recognized Demo evidence export")
    files = {name: rows(root / f"{name}.ndjson") for name in ("captures", "scans", "audit")}
    return manifest, files


def check_capture(manifest, captures, errors):
    seq = [c["orderScanSeq"] for c in captures]
    if seq != list(range(1, manifest["captureHighWatermark"] + 1)):
        errors.append("capture order has missing, duplicate or reordered sequence")
    if len(captures) != manifest["captureCount"]:
        errors.append("capture count does not match manifest")
    unique(captures, ["captureId"], errors, "capture")
    unique(captures, ["originDeviceId", "deviceCaptureSeq"], errors, "capture")
    for capture in captures:
        if capture["orderId"] != manifest["orderId"]:
            errors.append("capture assigned to wrong order")
        if capture["state"] not in {"COMMITTED", "INVALID", "BUSINESS_REJECTED", "DISCARDED"}:
            errors.append(f"unresolved capture: {capture['captureId']}")


def check_scans(scans, captures, audit, errors):
    by_id = {c["captureId"]: c for c in captures}
    confirmations = {}
    for event in audit:
        if event["type"] == "CONFIRM_REQUESTED":
            confirmations.setdefault(event["captureId"], []).append(event)
    saved = {(a["originDeviceId"], a["localSeq"], a["captureId"])
             for a in audit if a["type"] == "SAVED"}
    seq = [s["orderScanSeq"] for s in scans]
    if seq != sorted(seq):
        errors.append("scan export not in capture order")
    unique(scans, ["orderScanSeq"], errors, "scan")
    unique(scans, ["originDeviceId", "deviceCaptureSeq"], errors, "scan")
    unique([s for s in scans if s["status"] == "ACTIVE"], ["barcode"], errors, "active")
    for scan in scans:
        capture = by_id.get(scan["captureId"], {})
        fields = ["barcode", "orderScanSeq", "originDeviceId", "deviceCaptureSeq", "expectedDispatchCycle"]
        if any(scan[key] != capture.get(key) for key in fields) or capture.get("state") != "COMMITTED":
            errors.append("scan and durable capture differ")
        check_confirmation(scan, capture, confirmations.get(scan["captureId"], []), errors)
        if capture.get("confirmedAtNs") is None or scan["expectedDispatchCycle"] is None:
            errors.append("saved without durable confirmation or cycle")
        if (scan["originDeviceId"], scan["localSeq"], scan["captureId"]) not in saved:
            errors.append("saved scan lacks matching audit")
        if Decimal(scan["rawWeight"]) * 1000 != int(scan["weightGrams"]):
            errors.append("exact source weight differs from grams")
    if {s["captureId"] for s in scans} != {c["captureId"] for c in captures if c["state"] == "COMMITTED"}:
        errors.append("committed capture / scan set mismatch")


def check_confirmation(scan, capture, audit, errors):
    policy = capture.get("confirmationPolicy", "MANUAL")
    if policy not in {"MANUAL", "AUTO_AFTER_VALIDATION"}:
        errors.append("unknown confirmation policy")
    if scan.get("confirmationPolicy", "MANUAL") != policy or scan["inputSource"] != capture.get("inputSource"):
        errors.append("scan confirmation policy / source differs from capture")
    confirms = [a for a in audit if a["captureId"] == scan["captureId"] and a["type"] == "CONFIRM_REQUESTED"
                and a["originDeviceId"] == scan["originDeviceId"] and a["localSeq"] < scan["localSeq"]]
    if len(confirms) != 1 or scan.get("confirmedAtNs") != capture.get("confirmedAtNs"):
        errors.append("saved scan lacks unique matching confirmation intent")
    if policy == "AUTO_AFTER_VALIDATION":
        source = capture.get("inputSource")
        if source not in {"BROADCAST", "TEST_INJECTION"}:
            errors.append("automatic save has invalid input source")
        if len(confirms) != 1 or confirms[0].get("reason") != f"{policy}:{source}":
            errors.append("automatic save lacks policy audit")


def check_totals(manifest, scans, errors):
    active = [s for s in scans if s["status"] == "ACTIVE"]
    if len(active) != manifest["savedCount"] or sum(int(s["weightGrams"]) for s in active) != int(manifest["weightGrams"]):
        errors.append("effective totals differ from manifest")
    for line in manifest["lines"]:
        matched = [s for s in active if s["deliveryCode"] == line["deliveryCode"]]
        if len(matched) != line["actualCount"] or sum(int(s["weightGrams"]) for s in matched) != int(line["weightGrams"]):
            errors.append(f"product total mismatch: {line['deliveryCode']}")


def check_expected(expected_path, captures, scans, errors):
    expected = rows(expected_path)
    if len(expected) != len(captures) or len(expected) != len(scans):
        errors.append("external expected count mismatch")
    for wanted, capture, scan in zip(expected, captures, scans):
        for field in ("orderScanSeq", "barcode"):
            if wanted[field] != capture[field] or wanted[field] != scan[field]:
                errors.append(f"external expected mismatch: {field}")
        if int(wanted["weightGrams"]) != int(scan["weightGrams"]) or wanted["deliveryCode"] != scan["deliveryCode"]:
            errors.append("external expected product / weight mismatch")


def verify(directory, expected_path=None):
    root = Path(directory)
    manifest, files = read_export(root)
    errors = []
    for name in ("captures.ndjson", "scans.ndjson", "audit.ndjson"):
        if manifest.get("files", {}).get(name) != digest(root / name):
            errors.append(f"SHA256 mismatch: {name}")
    check_capture(manifest, files["captures"], errors)
    check_scans(files["scans"], files["captures"], files["audit"], errors)
    check_totals(manifest, files["scans"], errors)
    unique(files["audit"], ["originDeviceId", "localSeq"], errors, "audit")
    if expected_path:
        check_expected(expected_path, files["captures"], files["scans"], errors)
    return {"orderId": manifest["orderId"], "integrityPassed": not errors,
            "externalSequenceCompared": bool(expected_path), "hardwarePassed": False,
            "writerFullObserved": manifest.get("sqliteConnection") == "PRIMARY_WRITER_IN_TRANSACTION"
            and manifest.get("sqliteSynchronous") == 2 and manifest.get("sqliteJournalMode") == "wal",
            "captureStates": dict(Counter(c["state"] for c in files["captures"])),
            "inputSources": dict(Counter(c["inputSource"] for c in files["captures"])),
            "effectiveCount": manifest["savedCount"], "weightGrams": manifest["weightGrams"],
            "errors": sorted(set(errors))}
