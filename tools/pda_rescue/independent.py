"""Device-attested independent facts remain pending authoritative/manual reconciliation."""
from datetime import datetime, timedelta
import hashlib
import re

from .facts import integer, records, unique, ordered
from .independent_sku import catalog
from .integrity import parse

FILES = {"independent-sku.bin", "independent-source.json", "independent-voids.ndjson", "independent-state.json"}
SECRETS = {"password", "accesstoken", "refreshtoken", "offlinetoken", "privatekey", "rescuepassword", "signedcredential", "localsessionid"}


def public(value, depth=0):
    if depth > 32:
        raise ValueError("Independent public evidence too deep")
    if isinstance(value, dict):
        for key, item in value.items():
            if key.lower() in SECRETS:
                raise ValueError("Operation credential cannot be exported")
            public(item, depth + 1)
    elif isinstance(value, list):
        for item in value:
            public(item, depth + 1)


def same(left, right, fields):
    if any(left[key] != right[key] for key in fields):
        raise ValueError("Independent original scope or fact mismatch")


def at(value, credential):
    stamp = datetime.fromisoformat(value)
    if not datetime.fromisoformat(credential["issuedAt"]) <= stamp < datetime.fromisoformat(credential["expiresAt"]):
        raise ValueError("Independent fact outside original claimed window")
    return stamp


def anchored(row, anchor, credential):
    elapsed = integer(row, "elapsed") - integer(anchor, "elapsed")
    if row["boot"] != anchor["boot"] or elapsed < 0 or abs(integer(row, "wall") - integer(anchor, "wall") - elapsed) > 120000:
        raise ValueError("Independent original time anchor mismatch")
    return at((datetime.fromisoformat(anchor["server"]) + timedelta(milliseconds=elapsed)).isoformat(), credential)


def source_facts(files, order, manifest):
    source = parse(files["independent-source.json"]); public(source)
    if source["purpose"] != "INDEPENDENT_READONLY_SOURCE_V1":
        raise ValueError("Unknown independent source")
    task, credential = source["task"], source["credential"]
    window = datetime.fromisoformat(credential["expiresAt"]) - datetime.fromisoformat(credential["issuedAt"])
    if not timedelta() < window <= timedelta(hours=48):
        raise ValueError("Independent claimed credential window exceeds limit")
    same(task, order, ("operatorId", "deviceId", "warehouseId", "vehicleNo"))
    same(task, credential, ("credentialId", "operatorId", "deviceId", "warehouseId"))
    if task["id"] != order["localOrderId"] or order["orderId"] != task["id"] or task["recoveryEpoch"] != credential["serverRecoveryEpoch"]:
        raise ValueError("Independent task identity mismatch")
    if credential["deviceKeySha256"] != manifest["deviceKeySha256"] or not re.fullmatch("[0-9a-f]{64}", credential["credentialSha256"]):
        raise ValueError("Independent credential/device digest mismatch")
    if order.get("pendingReconciliation") is not True or order.get("serverAuthorizationVerified") is not False:
        raise ValueError("Independent facts cannot claim authorization or completion")
    original = files["independent-sku.bin"]; imported, decision = source["import"], source["decision"]
    if hashlib.sha256(original).hexdigest() != task["skuSha256"] or imported["id"] != task["importId"] or imported["sha256"] != task["skuSha256"]:
        raise ValueError("Independent fixed original SKU mismatch")
    if decision["importId"] != imported["id"] or decision["outcome"] != "ACTIVATED":
        raise ValueError("Independent source was not activated")
    sku, products, duplicates = catalog(original)
    if duplicates and decision["duplicatesConfirmed"] != "1":
        raise ValueError("Independent identical rows lack explicit confirmation")
    return source, sku, products


def scans_and_inputs(facts, source, sku):
    task, credential, anchor = source["task"], source["credential"], source["timeAnchor"]
    captures, scans = facts["captures"], facts["scans"]
    by_id = {row["id"]: row for row in captures}
    if [integer(r, "orderScanSeq") for r in captures] != list(range(1, len(captures) + 1)):
        raise ValueError("Independent input sequence gap")
    ordered(captures, "deviceSequence")
    for row in captures:
        if row["taskId"] != task["id"] or row["deviceId"] != task["deviceId"] or row["sequence"] != row["orderScanSeq"]:
            raise ValueError("Independent input scope mismatch")
        if row["deviceCaptureSeq"] != row["deviceSequence"] or row["state"] not in {
                "RECEIVED", "WAITING_CONFIRM", "CONFIRM_REQUESTED", "COMMITTED", "INVALID", "BUSINESS_REJECTED", "DISCARDED"}:
            raise ValueError("Independent input state or device sequence mismatch")
        if row["rawInput"].strip(" \r\n\t").upper() != row["barcode"] or row["inputSource"] not in {"MANUAL", "BROADCAST"}:
            raise ValueError("Independent input changed")
        if anchored(row, anchor, credential) != at(row["capturedTrustedAt"], credential):
            raise ValueError("Independent capture time mismatch")
    for scan in scans:
        capture = by_id[scan["captureId"]]
        same(scan, sku[scan["barcode"]], tuple(sku[scan["barcode"]]))
        same(scan, capture, ("taskId", "sequence", "barcode"))
        if capture["state"] != "COMMITTED" or capture["confirmedAt"] is None:
            raise ValueError("Independent scan lacks confirmed original input")
        if at(scan["savedAt"], credential) < at(capture["confirmedAt"], credential):
            raise ValueError("Independent scan precedes original confirmation")
    if {r["id"] for r in captures if r["state"] == "COMMITTED"} != {r["captureId"] for r in scans}:
        raise ValueError("Independent committed input lacks its original scan")


def plans_and_state(files, facts, source, products):
    task = source["task"]; state = parse(files["independent-state.json"])
    if state["taskId"] != task["id"] or state["state"] != facts["order"]["state"] or facts["dispositions"] or state["state"] not in {"PLANNED", "SCANNING"}:
        raise ValueError("Independent local state mismatch")
    for line in facts["lines"]:
        if line["taskId"] != task["id"] or (line["model"], line["name"]) != products[line["deliveryCode"]] or integer(line, "plannedCount") < 1:
            raise ValueError("Independent fixed product plan mismatch")
    if sum(integer(r, "plannedCount") for r in facts["lines"]) != integer(task, "plannedTotal"):
        raise ValueError("Independent total plan mismatch")
    scans = facts["scans"]; voids = records(files["independent-voids.ndjson"]); unique(voids, "captureId")
    if any(sum(r["deliveryCode"] == line["deliveryCode"] and r["status"] == "ACTIVE" for r in scans) > integer(line, "plannedCount") for line in facts["lines"]):
        raise ValueError("Independent effective count exceeds fixed plan")
    if {r["captureId"] for r in voids} != {r["captureId"] for r in scans if r["status"] == "VOID"}:
        raise ValueError("Independent VOID original missing")
    for row in voids:
        if row["taskId"] != task["id"] or not row["reason"].strip() or len(row["reason"]) > 500:
            raise ValueError("Independent VOID scope or reason mismatch")
        sampled = anchored(row, source["timeAnchor"], source["credential"])
        if not timedelta() <= at(row["authorizedAt"], source["credential"]) - sampled <= timedelta(minutes=2):
            raise ValueError("Independent VOID original time mismatch")
    active = [row for row in scans if row["status"] == "ACTIVE"]
    if integer(state, "actualCount") != len(active) or integer(state, "weightGrams") != sum(integer(r, "weightGrams") for r in active):
        raise ValueError("Independent effective totals mismatch")
    if integer(state, "nextScanSeq") != len(facts["captures"]) + 1 or integer(state, "nextEventSeq") != len(facts["events"]) + 1:
        raise ValueError("Independent state high water mismatch")
    return state, voids


def audits(facts, source, voids):
    events = facts["events"]; task = source["task"]
    if [integer(r, "sequence") for r in events] != list(range(1, len(events) + 1)) or not events or events[0]["type"] != "CREATE_PLANNED":
        raise ValueError("Independent audit sequence gap or creation missing")
    allowed = {"CREATE_PLANNED", "INDEPENDENT_STARTED", "INDEPENDENT_RECEIVED", "INDEPENDENT_WAITING_CONFIRM",
               "INDEPENDENT_CONFIRM_REQUESTED", "INDEPENDENT_INVALID", "INDEPENDENT_SAVED", "INDEPENDENT_VOID",
               "INDEPENDENT_BUSINESS_REJECTED", "INDEPENDENT_FAILURE_ACKNOWLEDGED", "INDEPENDENT_DISCARDED"}
    for row in events:
        if row["taskId"] != task["id"] or row["sequence"] != row["orderPosition"] or row["type"] not in allowed:
            raise ValueError("Independent original audit scope mismatch")
        anchored(row, source["timeAnchor"], source["credential"]); public(parse(row["detail"].encode("utf-8")))
    expected = {"INDEPENDENT_RECEIVED": {r["id"] for r in facts["captures"]},
                "INDEPENDENT_SAVED": {r["captureId"] for r in facts["scans"]},
                "INDEPENDENT_VOID": {r["captureId"] for r in voids}}
    for kind, ids in expected.items():
        actual = [parse(r["detail"].encode("utf-8"))["captureId"] for r in events if r["type"] == kind]
        if len(actual) != len(ids) or set(actual) != ids:
            raise ValueError("Independent audit does not preserve original facts")
    if sum(r["type"] == "CREATE_PLANNED" for r in events) != 1 or sum(r["type"] == "INDEPENDENT_STARTED" for r in events) != (facts["order"]["state"] == "SCANNING"):
        raise ValueError("Independent task state lacks its original transition")
    if facts["order"]["state"] == "PLANNED" and facts["captures"]:
        raise ValueError("Independent planned task cannot contain captured work")


def verify(files, facts, manifest):
    order = facts["order"]; present = FILES.intersection(files)
    if order["source"] != "INDEPENDENT":
        if present:
            raise ValueError("Independent originals on another source")
        return
    if present != FILES or any(name in files for name in ("submission.json", "receipt.json", "submission-info.json", "source-evidence.ndjson")):
        raise ValueError("Independent original membership mismatch")
    source, sku, products = source_facts(files, order, manifest)
    task = source["task"]
    created = {key: task["created" + key[0].upper() + key[1:]] for key in ("wall", "elapsed", "boot")}
    if anchored(created, source["timeAnchor"], source["credential"]) != at(task["createdTrustedAt"], source["credential"]):
        raise ValueError("Independent creation time mismatch")
    scans_and_inputs(facts, source, sku)
    state, voids = plans_and_state(files, facts, source, products)
    audits(facts, source, voids)
    facts["independent"] = {"source": source, "state": state, "voids": voids,
                            "pendingReconciliation": True, "serverAuthorizationVerified": False}
