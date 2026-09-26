"""Candidate read context reconstructed from original payload and raw DB facts."""
import base64
import json
from decision_facts import context_from
from decision_products import products
from ledger_primitives import raw_digest, require


def read_context(body, facts):
    source = context_from(facts, body)
    result = {"source": body["source"], "submissionId": source["submissionId"],
              "payloadSha256": source["receivedPayloadSha256"], "planSha256": source["planSha256"],
              "workRound": source["workRound"], "currentSubmissionId": source["currentSubmissionId"],
              "current": True, "status": source["status"], "orderRevision": body["orderRevision"],
              "vehicleNo": body["temporaryOrder"]["vehicleNo"], "containerNo": body["temporaryOrder"]["containerNo"],
              "products": products(body), "inventorySnapshotId": body["inventorySnapshotId"],
              "sourceRecoveryEpoch": source["sourceRecoveryEpoch"], "serverRecoveryEpoch": facts["server"]["recovery_epoch"],
              "serviceMode": facts["server"]["service_mode"], "reviewerPermissionVersion": facts["actor"]["auth_version"],
              "receivedAt": facts["payload"]["received_at"], "closedAt": body["closedAt"],
              "canonicalOrderId": source["canonicalOrderId"]}
    return result


def context_hash(warehouse, context, dates):
    # JsonText hashes insertion-order JSON; products in the review DTO use this field order.
    ordered = dict(context)
    fields = "deliveryCode productId model name plannedCount actualCount weightGrams shortCount".split()
    ordered["products"] = [{key: row[key] for key in fields} for row in context["products"]]
    data = [warehouse, ordered, dates]
    return raw_digest(json.dumps(data, ensure_ascii=False, separators=(",", ":")).encode("utf-8"))


def check_cursor(page, context, fingerprint):
    if not page["hasMore"]:
        require(page["nextCursor"] is None, "CANDIDATE_CURSOR")
        return
    value = page["nextCursor"]
    require(isinstance(value, str) and 0 < len(value) <= 400, "CANDIDATE_CURSOR")
    decoded = base64.b64decode(value + "=" * (-len(value) % 4), altchars=b"-_", validate=True).decode("ascii")
    last = page["items"][-1]["order"]
    require(decoded == "|".join((context, fingerprint, last["createdAt"], last["id"])), "CANDIDATE_CURSOR_ANCHOR")
