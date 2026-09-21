"""Synthetic server facts, specified independently of the Java receipt outputs."""
from copy import deepcopy
from ledger_primitives import identifier, raw_digest

ABSENT_EVIDENCE = (
    "candidateContextSha256", "evidenceSetId", "evidenceSetSha256", "verificationId",
    "verificationVersion", "verificationAlgorithmVersion", "dispositionManifestSha256",
    "dispositionManifestVersion", "retainedManifestSha256", "retainedManifestVersion",
)


def source_facts(mode):
    facts = {
        "scopeId": identifier(10), "warehouseId": identifier(11),
        "creatorDeviceId": identifier(12), "clientOrderId": identifier(13),
        "submissionId": identifier(14), "workRound": "2", "status": "ABORTED",
        "authorizationId": identifier(15), "authorizationEpoch": "3",
        "authorizationUserVersion": "4", "authorizationDeviceVersion": "5",
        "freezeDeviceId": identifier(12), "originalOperatorId": identifier(16),
        "sourceRecoveryEpoch": "6", "canonicalOrderId": None,
        "mappingId": identifier(17), "mappingVersion": "0", "mappingOrderId": None,
        "currentSubmissionId": identifier(14), "scopeRound": "2", "decisionVersion": "7",
        "slotVersion": None, "slotScopeId": None, "slotSubmissionId": None,
        "slotRound": None, "targetRevision": None, "receivedPayloadSha256": None,
        "declaredUploadSha256": None, "planSha256": None,
    }
    if mode != "no-body":
        facts["declaredUploadSha256"] = raw_digest(b"synthetic declared payload")
    if mode == "received":
        complete_source(facts)
    return facts


def complete_source(facts):
    facts.update({
        "canonicalOrderId": identifier(18), "mappingOrderId": identifier(18),
        "mappingVersion": "1", "slotVersion": "8", "slotScopeId": identifier(10),
        "slotSubmissionId": identifier(14), "slotRound": "2", "targetRevision": "9",
        "receivedPayloadSha256": facts["declaredUploadSha256"],
        "planSha256": raw_digest(b"synthetic trusted plan"),
    })


def actor_facts(pda, kind):
    required = "submission:review" if kind == "SHORT_APPROVE" else "temporary_order:confirm"
    return {
        "principalId": identifier(20), "userAuthVersion": "10",
        "deviceId": identifier(21) if pda else None,
        "sessionId": identifier(22) if pda else None,
        "deviceVersion": "11" if pda else None,
        "permissionSnapshot": {
            "requiredPermissions": [required],
            "effectivePermissions": ["submission:review", "temporary_order:confirm"],
            "warehouseIds": [identifier(11)], "roleIds": [identifier(23), identifier(24)],
        },
    }


def command_facts(context, actor, kind):
    names = (
        "canonicalOrderId", "mappingVersion", "currentSubmissionId", "scopeRound",
        "slotVersion", "decisionVersion", "receivedPayloadSha256", "declaredUploadSha256",
        "planSha256", "targetRevision",
    )
    expected = {name: context[name] for name in names}
    expected.update(dict.fromkeys(ABSENT_EVIDENCE))
    expected["userAuthVersion"] = actor["userAuthVersion"]
    command = {
        "commandVersion": 1, "commandType": kind, "commandId": identifier(30),
        "warehouseId": identifier(11),
        "source": {"kind": "TEMP", "creatorDeviceId": identifier(12), "clientOrderId": identifier(13)},
        "submissionId": identifier(14), "workRound": "2", "authorizationId": identifier(15),
        "freezeDeviceId": identifier(12), "sourceRecoveryEpoch": "6", "transportRecoveryEpoch": "12",
        "expected": expected, "reason": "短装合成理由\n\\n\u0001 😀" if kind == "SHORT_APPROVE" else "",
    }
    return command


def fixture(mode="no-body", pda=False, kind="NORMAL_CONFIRM"):
    context = source_facts(mode)
    actor = actor_facts(pda, kind)
    return {
        "id": f"{mode}-{'pda' if pda else 'web'}-{kind.lower()}",
        "command": command_facts(context, actor, kind), "context": context, "actor": actor,
        "requestId": identifier(31), "receiptId": identifier(32), "eventId": identifier(33),
        "recordedAt": "2026-09-21T08:09:10.123Z",
    }


def fixtures():
    return [fixture(), fixture("declared", True), fixture("received"),
            fixture("received", True, "SHORT_APPROVE")]


def replace(value, path, replacement):
    changed = deepcopy(value)
    target = changed
    parts = path.split(".")
    for part in parts[:-1]:
        target = target[part]
    target[parts[-1]] = replacement
    return changed
