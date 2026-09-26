"""Recorded first-round TEMP decisions; nonzero candidates never authorize normal confirmation."""
from ledger_primitives import canonical, digest, parse, raw_digest, require
from receipt_expectations import CAPABILITIES
from decision_facts import inputs
from decision_products import decimal, products
from decision_signature import verify_signed
from decision_success import success_receipt


def expected_decision(vector, trusted, values):
    body, command, context, actor, candidates = values
    rows = products(body)
    short = any(decimal(p["shortCount"]) > 0 for p in rows)
    reason = body.get("shortShipmentReason")
    require(reason is None or isinstance(reason, str), "SHORT_REASON_TYPE")
    require(not short or (isinstance(reason, str) and reason.strip()), "SHORT_REASON_REQUIRED")
    result = {"kind": command["commandType"], "shortShipmentReason": "" if reason is None else reason,
              "shortApprovalReceiptId": None, "shortApprovalSha256": None, "submissionReceipt": None,
              "products": rows, "candidates": candidates}
    if command["commandType"] == "SHORT_APPROVE":
        require(short and actor["principalId"] != context["originalOperatorId"], "INDEPENDENT_SHORT_APPROVAL")
        require(isinstance(command["reason"], str) and command["reason"].strip(), "APPROVAL_REASON")
        require(vector.get("approval") is None and vector.get("successHttpUtf8") is None, "APPROVAL_HAS_EFFECT")
        return result
    require(candidates["total"] == "0", "NONZERO_CANDIDATES_UNRESOLVED")
    if short:
        approval = bind_approval(vector, trusted, values)
        result.update(shortApprovalReceiptId=approval["receiptId"],
                      shortApprovalSha256=vector["approval"]["record"]["original_response_sha256"])
    else:
        require(vector.get("approval") is None, "UNEXPECTED_APPROVAL")
    result["submissionReceipt"] = success_receipt(vector, body, trusted)
    return result


def bind_approval(vector, keys, values):
    prior = vector.get("approval")
    require(isinstance(prior, dict) and prior.get("approval") is None, "APPROVAL_REQUIRED")
    require(parse(prior["commandUtf8"])["commandType"] == "SHORT_APPROVE", "APPROVAL_KIND")
    approval = verify_decision(prior, keys)
    body, command, context, actor, candidates = values
    require(prior["payloadUtf8"] == vector["payloadUtf8"], "APPROVAL_BODY")
    require(approval["resultingDecisionVersion"] == context["decisionVersion"], "APPROVAL_STALE")
    before = approval["originalContext"]
    require({k: v for k, v in before.items() if k != "decisionVersion"} ==
            {k: v for k, v in context.items() if k != "decisionVersion"}, "APPROVAL_CONTEXT")
    require(approval["businessDecision"]["candidates"] == candidates, "APPROVAL_CANDIDATES")
    current = vector["facts"]["approvalActor"]
    require(current["id"] == approval["actor"]["principalId"] and current["status"] == "ACTIVE" and
            current["auth_version"] == approval["actor"]["userAuthVersion"] and
            "submission:review" in current["permissions"] and context["warehouseId"] in current["warehouses"], "APPROVAL_AUTHORITY")
    return approval


def verify_decision(vector, keys):
    raw = vector["responseUtf8"].encode("utf-8", errors="strict")
    record = vector["record"]
    require(raw_digest(raw) == record["original_response_sha256"], "ORIGINAL_BYTES_CHANGED")
    unsigned = verify_signed(parse(raw), keys, "acrylic-r06-command-receipt-v1")
    values = inputs(vector)
    body, command, context, actor, candidates = values
    expected = {"receiptVersion": 1, "receiptId": record["id"], "commandId": command["commandId"],
                "routeScope": "r06.command.v1", "commandType": command["commandType"], "commandSha256": digest(command, "command"),
                "status": "RECORDED", "businessDecision": expected_decision(vector, keys, values),
                "recordedAt": record["recorded_at"], "eventId": record["event_id"],
                "resultingDecisionVersion": str(decimal(context["decisionVersion"]) + 1),
                "originalContext": context, "actor": actor, "expected": command["expected"], "capabilities": dict(CAPABILITIES)}
    require(canonical(unsigned) == canonical(expected), "DECISION_FACTS")
    require(digest(unsigned, "receipt") == record["unsigned_receipt_sha256"], "RECEIPT_DOMAIN")
    return unsigned


def verify_detail(vector, keys):
    detail = vector["detail"]
    require(set(detail) == {"originalCommandReceiptUtf8", "originalResponseSha256", "currentEffect"}, "DETAIL_SHAPE")
    require(detail["originalCommandReceiptUtf8"] == vector["responseUtf8"] and
            detail["originalResponseSha256"] == vector["record"]["original_response_sha256"], "DETAIL_ORIGINAL_CHANGED")
    return verify_decision(vector, keys)
