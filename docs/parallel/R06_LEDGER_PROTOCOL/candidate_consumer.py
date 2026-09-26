"""Verify real paged candidate HTTP output against an independent database projection."""
from candidate_facts import head, matching_orders, fingerprint
from candidate_context import read_context, context_hash, check_cursor
from decision_products import products
from ledger_primitives import canonical, parse, require


def candidate(row, body):
    original = [{key: p[key] for key in ("deliveryCode", "productId", "model", "name", "plannedCount")} for p in products(body)]
    signals, blockers = [], []
    if row["vehicle_no"] == body["temporaryOrder"]["vehicleNo"]:
        signals.append("SAME_VEHICLE")
    if set(row["products"]).intersection(p["productId"] for p in original):
        signals.append("SHARED_PRODUCT")
    if row["status"] != "READY":
        blockers.append("TARGET_NOT_READY")
    for field, blocker in (("hasAssignment", "PDA_WORK_EXISTS"), ("hasPcWork", "PC_WORK_EXISTS"),
                           ("hasSubmission", "SUBMISSION_EXISTS"), ("hasResult", "RESULT_EXISTS")):
        if row[field]:
            blockers.append(blocker)
    if sorted(map(canonical, row["lines"])) != sorted(map(canonical, original)):
        blockers.append("PLAN_DIFFERS")
    if row["inventory_snapshot_id"] != body["inventorySnapshotId"]:
        blockers.append("INVENTORY_VERSION_DIFFERS")
    return {"order": head(row), "products": row["lines"], "signals": signals, "blockers": blockers, "linkAvailable": False}


def verify_pages(vector):
    body, facts = parse(vector["payloadUtf8"]), vector["facts"]
    first, last, orders = matching_orders(body, facts)
    expected = [candidate(row, body) for row in orders]
    dates = {"fromDate": str(first), "throughDate": str(last)}
    context = read_context(body, facts)
    expected_hash = context_hash(facts["scope"]["warehouse_id"], context, dates)
    seen = []
    for index, page in enumerate(vector["pages"]):
        require(page["range"] == dates, "CANDIDATE_RANGE")
        require(type(page["total"]) is int and page["total"] == len(expected), "CANDIDATE_TOTAL")
        require(page["candidateSetSha256"] == fingerprint(orders), "CANDIDATE_FINGERPRINT")
        require(page["writeAvailable"] is False and page["releaseAllowed"] is False, "CANDIDATE_CAPABILITIES")
        require(all(item["linkAvailable"] is False for item in page["items"]), "LINK_CAPABILITY")
        require(canonical(page["context"]) == canonical(context), "CANDIDATE_CONTEXT_FACTS")
        require(page["contextSha256"] == expected_hash, "CANDIDATE_CONTEXT_HASH")
        require(page["hasMore"] is (index < len(vector["pages"]) - 1), "CANDIDATE_PAGINATION")
        require(len(page["items"]) == min(5, len(expected) - index * 5), "CANDIDATE_PAGE_SIZE")
        check_cursor(page, expected_hash, fingerprint(orders)); seen.extend(page["items"])
    require(vector["pages"], "CANDIDATE_CONTEXT")
    require(canonical(seen) == canonical(expected), "CANDIDATE_FACTS")
    return expected
