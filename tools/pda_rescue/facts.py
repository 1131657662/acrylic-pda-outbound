"""Semantic checks and human-readable projections use decimal integers only."""
import hashlib
import re
from collections import defaultdict

from .integrity import parse

SCAN_FIELDS = ("orderScanSeq", "captureId", "barcode", "deliveryCode", "model", "name",
               "rawWeight", "weightGrams", "status", "savedAt")
SCAN_HEADERS = ("原扫码序号", "采集ID", "条码", "产品代码", "型号", "品名", "原重量", "整数克", "状态", "保存时间", "原处置")
PRODUCT_HEADERS = ("产品代码", "计划包数", "有效包数", "撤销包数", "处置包数", "差额", "有效整数克")


def integer(row, field):
    text = row[field]
    if not isinstance(text, str) or not re.fullmatch(r"0|[1-9][0-9]{0,18}", text) or int(text) > 2**63 - 1:
        raise ValueError(f"Invalid decimal integer: {field}")
    return int(text)


def records(data: bytes) -> list[dict]:
    rows = [parse(line) for line in data.splitlines() if line]
    if len(rows) > 100_000:
        raise ValueError("Too many records")
    if any(not isinstance(row, dict) for row in rows):
        raise ValueError("Invalid record")
    return rows


def unique(rows, field):
    values = [row[field] for row in rows]
    if any(not isinstance(value, str) or not value.strip() for value in values) or len(set(values)) != len(values):
        raise ValueError("Duplicate or missing original ID")


def ordered(rows, field):
    values = [integer(row, field) for row in rows]
    if any(value < 1 for value in values) or any(a >= b for a, b in zip(values, values[1:])):
        raise ValueError("Original sequence is not strictly ordered")


def excluded(scans, dispositions):
    unique(dispositions, "captureId"); unique(dispositions, "evidenceId")
    by_id = {row["captureId"]: row for row in scans}
    for row in dispositions:
        if row["action"] not in {"RELEASE", "QUARANTINE"} or by_id[row["captureId"]]["status"] != "ACTIVE":
            raise ValueError("Disposition does not refer to original active scan")
    return {row["captureId"] for row in dispositions}


def summary(rows):
    captures, scans, events, dispositions = rows
    unique(captures, "id"); unique(scans, "captureId"); unique(events, "id")
    ordered(captures, "orderScanSeq"); ordered(scans, "orderScanSeq"); ordered(events, "orderPosition")
    by_id = {row["id"]: row for row in captures}
    for scan in scans:
        if scan["orderScanSeq"] != by_id[scan["captureId"]]["orderScanSeq"] or scan["status"] not in {"ACTIVE", "VOID"}:
            raise ValueError("Scan does not match original capture or status")
        integer(scan, "weightGrams")
    disposed = excluded(scans, dispositions)
    active = [row for row in scans if row["status"] == "ACTIVE" and row["captureId"] not in disposed]
    return {key: str(value) for key, value in {
        "captures": len(captures), "scans": len(scans), "events": len(events), "active": len(active),
        "void": sum(row["status"] == "VOID" for row in scans), "disposed": len(disposed),
        "pending": sum(row["state"] in {"RECEIVED", "WAITING_CONFIRM", "CONFIRM_REQUESTED"} for row in captures),
        "failed": sum(row["state"] in {"INVALID", "BUSINESS_REJECTED"} for row in captures),
        "weightGrams": sum(integer(row, "weightGrams") for row in active)}.items()}


def cell(value: str) -> str:
    escaped = "".join({"\\": "\\\\", "\t": "\\t", "\n": "\\n", "\r": "\\r"}.get(
        char, f"\\u{ord(char):04x}" if ord(char) < 32 or ord(char) == 127 else char) for char in value)
    return "'" + escaped if value.lstrip()[:1] in {"=", "+", "-", "@"} else escaped


def tsv(headings, rows) -> bytes:
    return "".join("\t".join(cell(value) for value in row) + "\n" for row in [headings, *rows]).encode("utf-8")


def products(lines, scans, dispositions):
    unique(lines, "deliveryCode")
    codes = {row["deliveryCode"] for row in lines}
    if any(row["deliveryCode"] not in codes for row in scans):
        raise ValueError("Scan product absent from original plan")
    disposed = excluded(scans, dispositions)
    by_code = defaultdict(list)
    for row in scans:
        by_code[row["deliveryCode"]].append(row)
    result = []
    for line in lines:
        rows = by_code[line["deliveryCode"]]
        active = [row for row in rows if row["status"] == "ACTIVE" and row["captureId"] not in disposed]
        result.append([line["deliveryCode"], line["plannedCount"], str(len(active)), str(sum(row["status"] == "VOID" for row in rows)),
                       str(sum(row["captureId"] in disposed for row in rows)),
                       str(integer(line, "plannedCount") - len(active)), str(sum(integer(row, "weightGrams") for row in active))])
    return result


def verify_facts(files: dict, manifest: dict) -> dict:
    order = parse(files["order.json"])
    if order["source"] not in {"FORMAL", "TEMP", "INDEPENDENT"} or order["timeTrust"] != "UNCONFIRMED_EXPORT_TIME":
        raise ValueError("Unknown source or export time trust")
    for field in ("orderId", "localOrderId", "deviceId", "operatorId", "warehouseId", "state"):
        if not isinstance(order[field], str) or not order[field].strip():
            raise ValueError("Missing original scope")
    captures, scans, events, lines = [records(files[name + ".ndjson"]) for name in ("captures", "scans", "events", "lines")]
    dispositions = records(files["dispositions.ndjson"])
    actions = {row["captureId"]: row["action"] for row in dispositions}
    if summary((captures, scans, events, dispositions)) != manifest["summary"]:
        raise ValueError("Manifest semantic counts mismatch")
    if files["scan-details.tsv"] != tsv(SCAN_HEADERS, [[row[field] for field in SCAN_FIELDS] + [actions.get(row["captureId"], "")] for row in scans]):
        raise ValueError("Human scan table differs from original facts")
    if files["product-summary.tsv"] != tsv(PRODUCT_HEADERS, products(lines, scans, dispositions)):
        raise ValueError("Human product table differs from original facts")
    verify_frozen(files)
    return {"order": order, "captures": captures, "scans": scans, "events": events, "lines": lines, "dispositions": dispositions}


def verify_frozen(files):
    if "submission.json" in files:
        info = parse(files["submission-info.json"])
        if hashlib.sha256(files["submission.json"]).hexdigest() != info["sha256"]:
            raise ValueError("Frozen original payload digest mismatch")
        if parse(files["submission.json"])["submissionId"] != info["id"]:
            raise ValueError("Frozen original submission ID mismatch")
    elif "submission-info.json" in files or "receipt.json" in files:
        raise ValueError("Submission evidence without original frozen payload")
