"""Reconstruct the complete candidate set from table facts, independently of Java queries."""
import json
from datetime import datetime, timedelta, timezone
from ledger_primitives import raw_digest, require

HEAD_FIELDS = ("id orderNo status revision rowVersion revisionId vehicleNo containerNo createdAt updatedAt "
               "inventorySnapshotId mappingRevision hasAssignment hasPcWork hasSubmission hasResult").split()


def matching_orders(body, facts):
    received = datetime.fromisoformat(facts["payload"]["received_at"]).astimezone(timezone(timedelta(hours=8))).date()
    first, last = received - timedelta(days=7), received + timedelta(days=7)
    products = {p["productId"] for p in body["temporaryOrder"]["lines"]}
    matches = []
    for order in facts["formalOrders"]:
        if order["warehouse_id"] != facts["scope"]["warehouse_id"] or order["source"] != "WEB":
            continue
        day = datetime.fromisoformat(order["created_at"]).astimezone(timezone(timedelta(hours=8))).date()
        signal = order["vehicle_no"] == body["temporaryOrder"]["vehicleNo"] or products.intersection(order["products"])
        if first <= day <= last and signal:
            matches.append(order)
    matches.sort(key=lambda row: (datetime.fromisoformat(row["created_at"]), row["id"]), reverse=True)
    require(len({row["id"] for row in matches}) == len(matches), "DUPLICATE_CANDIDATE")
    return first, last, matches


def head(row):
    value = {"id": row["id"], "orderNo": row["order_no"], "status": row["status"],
             "revision": int(row["current_revision"]), "rowVersion": row["row_version"], "revisionId": row["revision_id"],
             "vehicleNo": row["vehicle_no"], "containerNo": row["container_no"], "createdAt": row["created_at"],
             "updatedAt": row["updated_at"], "inventorySnapshotId": row["inventory_snapshot_id"],
             "mappingRevision": row["mapping_revision"]}
    for field in HEAD_FIELDS[-4:]:
        require(type(row[field]) is bool, "CANDIDATE_FACT_TYPE")
        value[field] = row[field]
    return value


def fingerprint(orders):
    # The public candidate fingerprint hashes Jackson record field order, not canonical JSON order.
    raw = b"".join(json.dumps(head(row), ensure_ascii=False, separators=(",", ":")).encode("utf-8") + b"\n" for row in orders)
    return raw_digest(raw)
