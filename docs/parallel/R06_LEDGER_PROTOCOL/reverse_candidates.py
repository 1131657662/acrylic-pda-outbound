"""Rebuild formal-to-TEMP candidates from independent raw relations, never query DTOs."""
from datetime import datetime, timedelta, timezone
from ledger_primitives import canonical, parse, raw_digest, require

LOCAL = timezone(timedelta(hours=8))


def instant(value):
    result = datetime.fromisoformat(value.replace("Z", "+00:00"))
    require(result.tzinfo is not None, "REVERSE_TIME_ZONE")
    return result


def indexed(rows, field="id"):
    result = {row[field]: row for row in rows}
    require(len(result) == len(rows), "REVERSE_DUPLICATE_FACT")
    return result


def revision(facts, order):
    rows = [r for r in facts["revisions"] if r["order_id"] == order["id"] and r["revision"] == order["current_revision"]]
    require(len(rows) == 1, "REVERSE_REVISION_FACT")
    return rows[0]


def products(facts, revision_id):
    return {line["product_id"] for line in facts["lines"] if line["revision_id"] == revision_id}


def time_range(order):
    day = instant(order["created_at"]).astimezone(LOCAL).date()
    first, last = day - timedelta(days=7), day + timedelta(days=7)
    start = datetime.combine(first, datetime.min.time(), LOCAL)
    return {"fromDate": str(first), "throughDate": str(last)}, start, start + timedelta(days=15)


def candidate(facts, source, query):
    scopes, payloads, mappings, orders = query["joins"]
    scope, payload = scopes.get(source["scope_id"]), payloads.get(source["id"])
    if scope is None or payload is None or source["status"] not in ("SUCCEEDED", "REVIEW_REQUIRED"):
        return None
    if scope["warehouse_id"] != query["warehouse"] or scope["creator_device_id"] is None:
        return None
    mapping = mappings.get(scope["id"])
    if mapping is None or mapping["warehouse_id"] != scope["warehouse_id"]:
        return None
    order = orders.get(mapping["canonical_order_id"])
    if order is None or order["warehouse_id"] != scope["warehouse_id"] or order["source"] != "PDA_TEMP":
        return None
    received = instant(payload["received_at"])
    if not query["start"] <= received < query["end"]:
        return None
    plan = revision(facts, order)
    same = plan["vehicle_no"] == query["vehicle"]
    overlap = bool(products(facts, plan["id"]) & query["products"])
    if not same and not overlap:
        return None
    return {"submissionId": source["id"], "orderId": order["id"], "orderNo": order["order_no"],
            "status": source["status"], "vehicleNo": plan["vehicle_no"], "containerNo": plan["container_no"],
            "plannedTotal": plan["planned_total"], "actualCount": payload["expected_count"],
            "receivedAt": payload["received_at"], "sameVehicle": same, "overlappingProducts": overlap}


def expected_page(facts, order, request):
    date_range, start, end = time_range(order)
    plan = revision(facts, order)
    mappings = [m for m in facts["mappings"] if m["mapping_version"] == "1"]
    joins = (indexed(facts["scopes"]), indexed(facts["payloads"], "submission_id"),
             indexed(mappings, "scope_id"), indexed(facts["orders"]))
    query = {"warehouse": order["warehouse_id"], "vehicle": plan["vehicle_no"], "joins": joins,
             "products": products(facts, plan["id"]), "start": start, "end": end}
    matches = [item for row in facts["submissions"] if (item := candidate(facts, row, query)) is not None]
    matches.sort(key=lambda row: (row["status"] == "SUCCEEDED", instant(row["receivedAt"]), row["submissionId"]), reverse=True)
    requested = request["page"]
    require(type(requested) is int and 1 <= requested <= 1000000, "REVERSE_PAGE")
    page = min(requested, max(1, (len(matches) + 19) // 20))
    return date_range, {"items": matches[(page - 1) * 20:page * 20], "total": len(matches), "page": page, "pageSize": 20}


def successful_witness(vector):
    facts, body = vector["facts"], parse(vector["payloadUtf8"])
    require(canonical(body).decode() == vector["payloadUtf8"], "REVERSE_BODY_BYTES")
    source = indexed(facts["submissions"])[body["submissionId"]]
    payload = indexed(facts["payloads"], "submission_id")[source["id"]]
    require(source["status"] == "SUCCEEDED" and body["source"]["kind"] == "TEMP", "REVERSE_SUCCESS_WITNESS")
    require(payload["payload_sha256"] == raw_digest(canonical(body)) and
            payload["expected_count"] == body["expectedCount"], "REVERSE_PAYLOAD_FACTS")
    require(sum(row["submission_id"] == source["id"] for row in facts["results"]) == 1, "REVERSE_RESULT_WITNESS")


def verify_reverse_candidates(vector):
    facts, request, response, actor = (vector[key] for key in ("facts", "request", "response", "actor"))
    require(actor["status"] == "ACTIVE" and "order:read" in actor["permissions"] and
            request["warehouseId"] in actor["warehouses"], "REVERSE_ACTOR_AUTHORITY")
    order = indexed(facts["orders"])[request["orderId"]]
    require(order["warehouse_id"] == request["warehouseId"] and order["source"] == "WEB", "REVERSE_TARGET")
    require(canonical(vector["stateBefore"]) == canonical(vector["stateAfter"]), "REVERSE_QUERY_MUTATED_BUSINESS")
    require(all(facts[key] == vector["stateBefore"][key] for key in facts), "REVERSE_FACT_SNAPSHOT")
    successful_witness(vector)
    date_range, page = expected_page(facts, order, request)
    work = any(row["order_id"] == order["id"] for name in ("assignments", "pcWork", "results") for row in facts[name])
    work = work or any(row["source_order_id"] == order["id"] for row in facts["formalScopes"])
    instant(response["checkedAt"])
    expected = {"orderId": order["id"], "revision": int(order["current_revision"]), "orderVersion": order["row_version"],
                "range": date_range, "checkedAt": response["checkedAt"], "candidates": page, "hasFormalWork": work,
                "advisoryOnly": True, "releaseAllowed": False}
    require(canonical(response) == canonical(expected), "REVERSE_RESPONSE_FACTS")
    require(vector["foreignDenied"].get("code") == "WAREHOUSE_NOT_FOUND", "REVERSE_FOREIGN_AUTHORITY")
    return expected
