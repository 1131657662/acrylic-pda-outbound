"""Check controlled mapping and accounting effects from table rows, never receipt DTOs."""
from ledger_primitives import canonical, require
from decision_products import decimal


def mapping_effect(vector, values):
    body, command, context, _, _, target = values
    state = vector["after"]
    require(canonical(state) == canonical(vector["afterReplay"]), "LINK_REPLAY_EFFECT")
    maps = state["mappings"]
    require(len({m["mapping_version"] for m in maps}) == len(maps), "LINK_MAPPING_DUPLICATE")
    require(all(m["scope_id"] == context["scopeId"] and m["warehouse_id"] == context["warehouseId"] for m in maps), "LINK_MAPPING_SCOPE")
    require(all(m["mapping_version"] in ("0", "1", "2") for m in maps), "LINK_HISTORY_UNSUPPORTED")
    current = [m for m in maps if m["mapping_version"] == "2"]
    require(len(current) == 1 and current[0]["origin"] == "CONTROLLED_LINK" and
            current[0]["canonical_order_id"] == target["id"], "LINK_INSTALLED_MAPPING")
    initial = [m for m in maps if m["mapping_version"] == "1"]
    require(len(initial) == 1 and initial[0]["canonical_order_id"] == state["originalOrder"]["id"], "LINK_HISTORY_SOURCE")
    require(state["originalOrder"]["status"] == "LINKED" and state["originalOrder"]["current_result_version"] is None, "LINK_ORIGINAL_EFFECT")
    require(vector["facts"]["mapping"] in maps, "LINK_HISTORY_REWRITTEN")
    require(state["scope"]["mapping_version"] == "2" and state["scope"]["canonical_order_id"] == target["id"] and
            state["scope"]["id"] == context["scopeId"], "LINK_SCOPE_EFFECT")
    require(state["slot"]["scope_id"] == context["scopeId"] and state["slot"]["order_id"] == target["id"] and
            state["slot"]["current_submission_id"] == body["submissionId"] and state["slot"]["work_round"] == "1", "LINK_SLOT_EFFECT")
    # LINK moves the slot; confirmation advances it again when marking it completed.
    require(state["slot"]["slot_version"] == str(decimal(context["slotVersion"]) + 1), "LINK_SLOT_VERSION")
    require(state["decision"]["decision_version"] == str(decimal(context["decisionVersion"]) + 1), "LINK_DECISION_EFFECT")
    return current[0]


def verify_effects(vector, values):
    body, command, _, _, _, target = values
    state, active = vector["after"], [s for s in body["scans"] if s["status"] == "ACTIVE"]
    linked = command["commandType"] == "LINK"
    require(state["receiptCount"] == ("1" if linked else "2"), "LINK_RECEIPT_COUNT")
    require(state["submission"] == {"id": body["submissionId"], "status": "REVIEW_REQUIRED" if linked else "SUCCEEDED"}, "LINK_SUBMISSION_EFFECT")
    require(state["targetOrder"] == {"id": target["id"], "status": "READY" if linked else "COMPLETED",
                                     "current_result_version": None if linked else "1"}, "LINK_TARGET_EFFECT")
    require(state["slot"]["phase"] == ("FROZEN" if linked else "COMPLETED"), "LINK_SLOT_PHASE")
    require(len(state["commitments"]) == len(active), "LINK_COMMITMENT_COUNT")
    require({r["bale_id"] for r in state["commitments"]} == {s["baleId"] for s in active}, "LINK_COMMITMENT_BALES")
    require(all(r["order_id"] == target["id"] and r["submission_id"] == body["submissionId"] and
                r["state"] == ("REVIEW_RESERVED" if linked else "DISPATCHED") for r in state["commitments"]), "LINK_COMMITMENT_OWNER")
    if linked:
        require(not state["results"] and not state["dispatches"] and not state["ledger"], "LINK_PREMATURE_ACCOUNTING")
    else:
        accounting(state, body, target)


def accounting(state, body, target):
    active = {s["baleId"]: s for s in body["scans"] if s["status"] == "ACTIVE"}
    require(state["results"] == [{"order_id": target["id"], "submission_id": body["submissionId"], "result_version": "1",
                                   "actual_count": body["expectedCount"], "weight_g": body["expectedWeightGrams"]}], "LINK_RESULT_FACTS")
    dispatches = state["dispatches"]
    require(len(dispatches) == len(active) and {r["bale_id"] for r in dispatches} == set(active), "LINK_DISPATCH_COUNT")
    for row in dispatches:
        require(row["order_id"] == target["id"] and row["submission_id"] == body["submissionId"] and
                row["weight_g"] == active[row["bale_id"]]["weightGrams"], "LINK_DISPATCH_FACTS")
    expected = [{"dispatch_id": r["id"], "bale_id": r["bale_id"], "quantity_delta": "-1",
                 "weight_delta_g": str(-decimal(r["weight_g"]))} for r in dispatches] if body["autoDeductInventory"] else []
    actual = [{k: v for k, v in row.items() if k != "id"} for row in state["ledger"]]
    require(sorted(map(canonical, actual)) == sorted(map(canonical, expected)), "LINK_LEDGER_FACTS")
