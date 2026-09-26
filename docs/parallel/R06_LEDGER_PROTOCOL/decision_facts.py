"""Construct expected context from separate raw table projections, not signed output."""
from ledger_primitives import canonical, digest, parse, raw_digest, require
from decision_products import candidate_snapshot

CONTEXT_COLUMNS = {
    "scope": {"scopeId": "id", "warehouseId": "warehouse_id", "creatorDeviceId": "creator_device_id",
              "clientOrderId": "client_order_id", "canonicalOrderId": "canonical_order_id",
              "mappingVersion": "mapping_version", "currentSubmissionId": "current_submission_id", "scopeRound": "work_round"},
    "submission": {"submissionId": "id", "workRound": "work_round", "status": "status",
                   "freezeDeviceId": "freeze_device_id", "originalOperatorId": "operator_id", "sourceRecoveryEpoch": "source_recovery_epoch"},
    "authorization": {"authorizationId": "id", "authorizationEpoch": "authorization_epoch",
                      "authorizationUserVersion": "user_auth_version", "authorizationDeviceVersion": "device_version"},
    "mapping": {"mappingId": "id", "mappingOrderId": "canonical_order_id"},
    "decision": {"decisionVersion": "decision_version"},
    "slot": {"slotVersion": "slot_version", "slotScopeId": "scope_id", "slotSubmissionId": "current_submission_id", "slotRound": "work_round"},
    "order": {"targetRevision": "current_revision"},
    "payload": {"receivedPayloadSha256": "payload_sha256"}, "upload": {"declaredUploadSha256": "payload_sha256"},
}
EXPECTED_FIELDS = ("canonicalOrderId mappingVersion currentSubmissionId scopeRound slotVersion decisionVersion "
                   "receivedPayloadSha256 declaredUploadSha256 planSha256 targetRevision").split()
ABSENT_FIELDS = ("evidenceSetId evidenceSetSha256 verificationId verificationVersion verificationAlgorithmVersion "
                 "dispositionManifestSha256 dispositionManifestVersion retainedManifestSha256 retainedManifestVersion").split()


def context_from(facts, body):
    result = {key: facts[table][column] for table, fields in CONTEXT_COLUMNS.items() for key, column in fields.items()}
    result["planSha256"] = raw_digest(canonical(body["temporaryOrder"]))
    require(body["planSha256"] == result["planSha256"], "PLAN_HASH")
    require(result["receivedPayloadSha256"] == raw_digest(canonical(body)), "BODY_HASH")
    require(result["declaredUploadSha256"] in (None, result["receivedPayloadSha256"]), "DECLARED_HASH")
    require(result["mappingVersion"] == result["workRound"] == result["scopeRound"] == "1", "UNSUPPORTED_SCOPE")
    require(result["status"] == "REVIEW_REQUIRED", "SOURCE_STATUS")
    for name, value in {"submissionId": result["currentSubmissionId"], "scopeId": result["slotScopeId"],
                        "workRound": result["slotRound"], "canonicalOrderId": result["mappingOrderId"]}.items():
        require(result[name] == value, "SOURCE_LINK")
    require(result["slotSubmissionId"] == result["submissionId"], "SLOT_LINK")
    require(body["source"] == {"kind": "TEMP", "creatorDeviceId": result["creatorDeviceId"],
                                "clientOrderId": result["clientOrderId"]}, "BODY_SOURCE")
    for name in ("submissionId", "workRound", "authorizationId", "freezeDeviceId", "sourceRecoveryEpoch", "authorizationEpoch"):
        require(body[name] == result[name], "BODY_CONTEXT")
    return result


def actor_from(facts, kind):
    actor = facts["actor"]
    require(actor["status"] == "ACTIVE", "ACTOR_STATUS")
    permission = "submission:review" if kind == "SHORT_APPROVE" else "temporary_order:confirm"
    require(permission in actor["permissions"] and facts["scope"]["warehouse_id"] in actor["warehouses"], "ACTOR_AUTHORITY")
    return {"principalId": actor["id"], "userAuthVersion": actor["auth_version"], "deviceId": None,
            "sessionId": None, "deviceVersion": None, "permissionSnapshot": {"requiredPermissions": [permission],
            "effectivePermissions": sorted(set(actor["permissions"])), "warehouseIds": sorted(set(actor["warehouses"])),
            "roleIds": sorted(set(actor["roles"]))}}


def inputs(vector):
    facts = vector["facts"]
    body, command = parse(vector["payloadUtf8"]), parse(vector["commandUtf8"])
    require(canonical(body).decode() == vector["payloadUtf8"], "ORIGINAL_BODY_NONCANONICAL")
    context = context_from(facts, body)
    kind = command["commandType"]
    require(kind in ("NORMAL_CONFIRM", "SHORT_APPROVE"), "COMMAND_KIND")
    actor = actor_from(facts, kind)
    expected = {name: context[name] for name in EXPECTED_FIELDS}
    expected.update(dict.fromkeys(ABSENT_FIELDS))
    candidates = candidate_snapshot(body, facts)
    expected.update(userAuthVersion=actor["userAuthVersion"], candidateContextSha256=candidates["contextSha256"])
    require(canonical(expected) == canonical(command["expected"]), "COMMAND_EXPECTED")
    for name in ("warehouseId", "submissionId", "workRound", "authorizationId", "freezeDeviceId", "sourceRecoveryEpoch"):
        require(command[name] == context[name], "COMMAND_CONTEXT")
    require(command["source"] == body["source"] and command["commandVersion"] == 1, "COMMAND_SOURCE")
    require(digest(command, "command") == vector["record"]["command_sha256"], "COMMAND_HASH")
    return body, command, context, actor, candidates
