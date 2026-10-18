"""Cross-check recorded withdrawal, replacement work and accounting facts."""
from __future__ import annotations

import json
from uuid import UUID

from fault_recovery_evidence import require, table_rows


def identified(rows: list[dict], value: str) -> dict:
    matches = [row for row in rows if row["id"] == UUID(value).bytes]
    require(len(matches) == 1, "business.unique_id")
    return matches[0]


def check_assignments(facts: dict, scenario: dict) -> None:
    old, new = scenario["oldSubmission"], scenario["submission"]
    rows = table_rows(facts, "order_assignment")
    previous, current = identified(rows, old["assignmentId"]), identified(rows, new["assignmentId"])
    require(previous["status"] == b"REVOKED" and current["status"] == b"CLOSED", "business.assignments")
    require(previous["order_id"] == current["order_id"] == UUID(scenario["orderId"]).bytes,
            "business.assignment_order")
    require(previous["device_id"] != current["device_id"], "business.independent_device")
    require({row["id"] for row in old["captures"]}.isdisjoint(row["id"] for row in new["captures"]),
            "business.independent_captures")
    require(current["device_id"] == UUID(new["freezeDeviceId"]).bytes, "business.new_device")
    require(current["start_order_scan_seq"] == b"1", "business.start_from_one")
    changes = table_rows(facts, "order_change_request")
    change = identified(changes, scenario["withdrawalReceipt"]["changeRequestId"])
    require(change["assignment_id"] == previous["id"] and change["status"] == b"WITHDRAWN",
            "business.withdrawal")
    require(change["stop_receipt"] is None, "business.no_stop_receipt")
    require(json.loads(change["resolution_receipt"]) == scenario["withdrawalReceipt"],
            "business.original_withdrawal_receipt")


def check_submission(facts: dict, scenario: dict) -> None:
    expected, success = scenario["submission"], scenario["success"]
    rows = table_rows(facts, "submission")
    require(len(rows) == 1, "business.single_submission")
    row = identified(rows, expected["submissionId"])
    require(row["status"] == b"SUCCEEDED" and row["work_round"] == b"2", "business.success")
    require(row["assignment_id"] == UUID(expected["assignmentId"]).bytes, "business.submission_assignment")
    require(json.loads(row["receipt_text"]) == success, "business.original_success_receipt")
    payloads = table_rows(facts, "submission_payload")
    require(len(payloads) == 1 and payloads[0]["submission_id"] == row["id"], "business.payload_id")
    require(payloads[0]["payload_sha256"].decode("ascii") == success["payloadSha256"], "business.payload_sha")
    require(success["mayResumeScanning"] is False and success["stockReleased"] is False,
            "business.success_boundaries")


def check_dispatch(facts: dict, scenario: dict) -> None:
    scans = scenario["submission"]["scans"]
    dispatches = table_rows(facts, "dispatch_record")
    require(len(dispatches) == len(scans) > 0, "business.dispatch_count")
    for scan in scans:
        matches = [row for row in dispatches if row["capture_id"] == UUID(scan["captureId"]).bytes]
        require(len(matches) == 1, "business.capture_once")
        row = matches[0]
        require(row["submission_id"] == UUID(scenario["submission"]["submissionId"]).bytes,
                "business.dispatch_submission")
        require(row["bale_id"] == UUID(scan["baleId"]).bytes, "business.bale")
        require(row["order_scan_seq"] == scan["orderScanSeq"].encode("ascii"), "business.scan_order")
        require(int(row["weight_g"]) == int(scan["weightGrams"]), "business.weight")


def check_ledger(facts: dict, scenario: dict) -> None:
    dispatches = table_rows(facts, "dispatch_record")
    ledger = table_rows(facts, "inventory_ledger")
    require(len(ledger) == len(dispatches), "business.ledger_count")
    for dispatch in dispatches:
        matches = [row for row in ledger if row["dispatch_id"] == dispatch["id"]]
        require(len(matches) == 1, "business.ledger_once")
        row = matches[0]
        require(row["bale_id"] == dispatch["bale_id"] and row["quantity_delta"] == b"-1",
                "business.ledger_bale")
        require(int(row["weight_delta_g"]) == -int(dispatch["weight_g"]), "business.ledger_weight")
    require(-sum(int(row["weight_delta_g"]) for row in ledger) == int(scenario["success"]["actualWeightGrams"]),
            "business.total_weight")


def check_business(facts: dict, scenario: dict) -> None:
    check_assignments(facts, scenario)
    check_submission(facts, scenario)
    check_dispatch(facts, scenario)
    check_ledger(facts, scenario)
    result = table_rows(facts, "order_result_version")
    require(len(result) == 1 and result[0]["result_version"] == b"1", "business.result_once")
    require(result[0]["actual_count"] == str(len(scenario["submission"]["scans"])).encode("ascii"),
            "business.result_count")
    require(result[0]["submission_id"] == UUID(scenario["submission"]["submissionId"]).bytes,
            "business.result_submission")
    commitments = table_rows(facts, "bale_commitment")
    require(len(commitments) == len(scenario["submission"]["scans"]), "business.commitments_count")
    require(all(row["state"] == b"DISPATCHED" and row["submission_id"] == result[0]["submission_id"]
                for row in commitments), "business.commitments")
    for name in ("temporary_handover_confirmation", "temporary_handover_activation", "recovery_handover_confirmation"):
        require(not table_rows(facts, name), "business.cancelled_chain_empty")
