"""Consume actual single-candidate LINK -> mapping2 NORMAL_CONFIRM evidence independently."""
from ledger_primitives import canonical, digest, parse, raw_digest, require
from receipt_expectations import CAPABILITIES
from decision_products import decimal, products
from decision_signature import verify_signed
from decision_success import success_receipt
from linked_context import inputs
from linked_effects import mapping_effect, verify_effects


def business_decision(vector, keys, values):
    body, command, context, _, candidates, target = values
    mapping = mapping_effect(vector, values)
    verify_effects(vector, values)
    if command["commandType"] == "LINK":
        require(isinstance(command["reason"], str) and command["reason"].strip(), "LINK_MANUAL_REASON")
        require(vector.get("successHttpUtf8") is None, "LINK_UNEXPECTED_SUCCESS")
        receipt = {"applyState": "APPLIED", "targetOrderId": target["id"], "targetOrderNo": target["order_no"],
                   "targetRevision": target["current_revision"], "targetRowVersion": target["row_version"],
                   "sourceCanonicalOrderId": context["canonicalOrderId"], "sourceMappingVersion": "1",
                   "candidateContextSha256": candidates["contextSha256"], "mappingId": mapping["id"], "mappingVersion": "2"}
    else:
        receipt = success_receipt(vector, body, keys)
    require(vector.get("approval") is None, "LINK_UNEXPECTED_APPROVAL")
    return {"kind": command["commandType"], "shortShipmentReason": "", "shortApprovalReceiptId": None,
            "shortApprovalSha256": None, "submissionReceipt": receipt, "products": products(body), "candidates": candidates}


def verify_linked_decision(vector, keys):
    raw, record = vector["responseUtf8"].encode("utf-8", errors="strict"), vector["record"]
    require(raw_digest(raw) == record["original_response_sha256"], "ORIGINAL_BYTES_CHANGED")
    unsigned = verify_signed(parse(raw), keys, "acrylic-r06-command-receipt-v1")
    values = inputs(vector)
    _, command, context, actor, _, _ = values
    expected = {"receiptVersion": 1, "receiptId": record["id"], "commandId": command["commandId"],
                "routeScope": "r06.command.v1", "commandType": command["commandType"], "commandSha256": digest(command, "command"),
                "status": "RECORDED", "businessDecision": business_decision(vector, keys, values),
                "recordedAt": record["recorded_at"], "eventId": record["event_id"],
                "resultingDecisionVersion": str(decimal(context["decisionVersion"]) + 1),
                "originalContext": context, "actor": actor, "expected": command["expected"], "capabilities": dict(CAPABILITIES)}
    require(canonical(unsigned) == canonical(expected), "LINK_DECISION_FACTS")
    require(digest(unsigned, "receipt") == record["unsigned_receipt_sha256"], "RECEIPT_DOMAIN")
    return unsigned


def verify_linked_detail(vector, keys):
    detail = vector["detail"]
    require(set(detail) == {"originalCommandReceiptUtf8", "originalResponseSha256", "currentEffect"}, "DETAIL_SHAPE")
    require(detail["originalCommandReceiptUtf8"] == vector["responseUtf8"] and
            detail["originalResponseSha256"] == vector["record"]["original_response_sha256"], "DETAIL_ORIGINAL_CHANGED")
    return verify_linked_decision(vector, keys)


def verify_linked_chain(bundle):
    link, confirm = bundle["cases"]["link"], bundle["cases"]["confirm"]
    first = verify_linked_detail(link, bundle["keys"])
    last = verify_linked_detail(confirm, bundle["keys"])
    require(first["commandType"] == "LINK" and last["commandType"] == "NORMAL_CONFIRM", "LINK_CHAIN_KINDS")
    require(link["payloadUtf8"] == confirm["payloadUtf8"], "LINK_CHAIN_BODY")
    require(first["resultingDecisionVersion"] == last["originalContext"]["decisionVersion"], "LINK_CHAIN_DECISION")
    installed = first["businessDecision"]["submissionReceipt"]
    for name, field in (("mappingId", "mappingId"), ("mappingVersion", "mappingVersion"), ("canonicalOrderId", "targetOrderId")):
        require(last["originalContext"][name] == installed[field], "LINK_CHAIN_MAPPING")
    require(link["after"]["mappings"] == confirm["after"]["mappings"], "LINK_CHAIN_HISTORY")
    return last
