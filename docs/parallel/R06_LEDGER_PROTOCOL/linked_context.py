"""Independent context for one controlled TEMP link and its first-round confirmation."""
from datetime import datetime
from ledger_primitives import canonical, digest, parse, raw_digest, require
from decision_facts import CONTEXT_COLUMNS, EXPECTED_FIELDS, ABSENT_FIELDS
from decision_products import candidate_snapshot, products
from candidate_facts import matching_orders


def context_from(facts, body, kind):
    result = {key: facts[table][column] for table, fields in CONTEXT_COLUMNS.items() for key, column in fields.items()}
    result["planSha256"] = raw_digest(canonical(body["temporaryOrder"]))
    require(body["planSha256"] == result["planSha256"], "LINK_PLAN_HASH")
    require(result["receivedPayloadSha256"] == raw_digest(canonical(body)), "LINK_BODY_HASH")
    require(result["declaredUploadSha256"] in (None, result["receivedPayloadSha256"]), "LINK_UPLOAD_HASH")
    require(result["workRound"] == result["scopeRound"] == result["slotRound"] == "1", "LINK_UNSUPPORTED_ROUND")
    require(result["status"] == "REVIEW_REQUIRED", "LINK_SOURCE_STATUS")
    require(result["mappingVersion"] == ("1" if kind == "LINK" else "2"), "LINK_UNSUPPORTED_MAPPING")
    for name, value in {"submissionId": result["currentSubmissionId"], "scopeId": result["slotScopeId"],
                        "canonicalOrderId": result["mappingOrderId"]}.items():
        require(result[name] == value, "LINK_SOURCE_CONTEXT")
    require(result["slotSubmissionId"] == result["submissionId"], "LINK_SLOT_CONTEXT")
    require(body["source"] == {"kind": "TEMP", "creatorDeviceId": result["creatorDeviceId"],
                                "clientOrderId": result["clientOrderId"]}, "LINK_BODY_SOURCE")
    for name in ("submissionId", "workRound", "authorizationId", "freezeDeviceId", "sourceRecoveryEpoch", "authorizationEpoch"):
        require(body[name] == result[name], "LINK_BODY_CONTEXT")
    mapping = facts["mapping"]
    require(mapping["scope_id"] == result["scopeId"] and mapping["warehouse_id"] == result["warehouseId"] and
            mapping["mapping_version"] == result["mappingVersion"], "LINK_MAPPING_CONTEXT")
    allowed = ("INITIAL_ACCEPTANCE", "MIGRATED_BASELINE") if kind == "LINK" else ("CONTROLLED_LINK",)
    require(mapping["origin"] in allowed, "LINK_MAPPING_ORIGIN")
    return result


def actor_from(vector, kind):
    facts = vector["facts"]
    actor, session = facts["actor"], facts["pdaSession"]
    permission = "temporary_order:link" if kind == "LINK" else "temporary_order:confirm"
    require(actor["status"] == "ACTIVE" and permission in actor["permissions"] and "pda:login" in actor["permissions"], "LINK_ACTOR_AUTHORITY")
    require(facts["scope"]["warehouse_id"] in actor["warehouses"] and
            session["warehouse_id"] == facts["scope"]["warehouse_id"], "LINK_ACTOR_WAREHOUSE")
    require(session["user_id"] == actor["id"] and session["user_auth_version"] == actor["auth_version"] and
            session["device_version"] == session["row_version"], "LINK_SESSION_VERSION")
    require(session["device_status"] == "ACTIVE" and session["revoked_at"] is None, "LINK_SESSION_STATUS")
    finished = datetime.fromisoformat(vector["finishedAt"])
    require(all(datetime.fromisoformat(session[key]) > finished for key in ("access_expires_at", "expires_at")), "LINK_SESSION_EXPIRED")
    return {"principalId": actor["id"], "userAuthVersion": actor["auth_version"], "deviceId": session["device_id"],
            "sessionId": session["id"], "deviceVersion": session["device_version"], "permissionSnapshot": {
                "requiredPermissions": [permission], "effectivePermissions": sorted(set(actor["permissions"])),
                "warehouseIds": sorted(set(actor["warehouses"])), "roleIds": sorted(set(actor["roles"]))}}


def reviewed_target(facts, body, kind):
    _, _, matches = matching_orders(body, facts)
    require(len(matches) == 1, "LINK_CANDIDATE_COUNT")
    target = matches[0]
    require(target["status"] == "READY", "LINK_TARGET_STATUS")
    require(target["inventory_snapshot_id"] == body["inventorySnapshotId"], "LINK_TARGET_INVENTORY")
    planned = [{key: row[key] for key in ("deliveryCode", "productId", "model", "name", "plannedCount")} for row in products(body)]
    require(sorted(map(canonical, target["lines"])) == sorted(map(canonical, planned)), "LINK_TARGET_PLAN")
    require(not any(target[field] for field in ("hasAssignment", "hasPcWork", "hasResult")), "LINK_TARGET_WORK")
    require(target["hasSubmission"] is (kind == "NORMAL_CONFIRM"), "LINK_TARGET_SUBMISSION")
    if kind == "NORMAL_CONFIRM":
        require(target["id"] == facts["scope"]["canonical_order_id"], "LINK_TARGET_MAPPING")
    else:
        require(target["id"] != facts["scope"]["canonical_order_id"], "LINK_TARGET_IS_SOURCE")
    return target


def inputs(vector):
    facts = vector["facts"]
    body, command = parse(vector["payloadUtf8"]), parse(vector["commandUtf8"])
    require(canonical(body).decode() == vector["payloadUtf8"], "LINK_BODY_NONCANONICAL")
    kind = command["commandType"]
    require(kind in ("LINK", "NORMAL_CONFIRM"), "LINK_COMMAND_KIND")
    context, actor = context_from(facts, body, kind), actor_from(vector, kind)
    candidates = candidate_snapshot(body, facts)
    target = reviewed_target(facts, body, kind)
    expected = {name: context[name] for name in EXPECTED_FIELDS}
    expected.update(dict.fromkeys(ABSENT_FIELDS))
    expected.update(userAuthVersion=actor["userAuthVersion"], candidateContextSha256=candidates["contextSha256"])
    require(canonical(expected) == canonical(command["expected"]), "LINK_COMMAND_EXPECTED")
    for name in ("warehouseId", "submissionId", "workRound", "authorizationId", "freezeDeviceId", "sourceRecoveryEpoch"):
        require(command[name] == context[name], "LINK_COMMAND_CONTEXT")
    require(command["source"] == body["source"] and type(command["commandVersion"]) is int and command["commandVersion"] == 1, "LINK_COMMAND_SOURCE")
    require(command["transportRecoveryEpoch"] == facts["server"]["recovery_epoch"], "LINK_RECOVERY_EPOCH")
    require(digest(command, "command") == vector["record"]["command_sha256"], "LINK_COMMAND_HASH")
    require(all(row["shortCount"] == "0" for row in products(body)), "LINK_SHORT_UNSUPPORTED")
    return body, command, context, actor, candidates, target
