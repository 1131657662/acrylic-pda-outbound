"""Independent receipt type/shape acceptance cases for the actual production validator."""
from copy import deepcopy
from ledger_primitives import identifier
from receipt_expectations import expected_receipt
from trusted_facts import fixture, fixtures, replace

INVALID_FIELDS = (
    ("receiptVersion", "1"), ("receiptVersion", 4294967297),
    ("receiptVersion", 1.5), ("receiptVersion", 1.0), ("receiptVersion", True),
    ("receiptId", 1), ("resultingDecisionVersion", 8),
    ("actor.principalId", 20), ("actor.userAuthVersion", 10),
    ("actor.deviceId", 21), ("actor.deviceVersion", 11),
    ("originalContext.mappingVersion", 0), ("originalContext.mappingVersion", "-1"),
    ("originalContext.decisionVersion", "-1"), ("originalContext.workRound", "0"),
    ("originalContext.receivedPayloadSha256", 123),
    ("originalContext.receivedPayloadSha256", "A" * 64),
    ("actor.permissionSnapshot.effectivePermissions", "temporary_order:confirm"),
    ("actor.permissionSnapshot.requiredPermissions", []),
    ("actor.permissionSnapshot.requiredPermissions", ["temporary_order:confirm", "temporary_order:confirm"]),
    ("actor.permissionSnapshot.roleIds", [identifier(24), identifier(23)]),
    ("actor.permissionSnapshot.warehouseIds", [11]),
    ("recordedAt", "2026-09-21T08:09:10.123456Z"),
    ("recordedAt", "2026-09-21T16:09:10.123+08:00"),
    ("capabilities.localDeletionAllowed", "false"),
)


def receipt_shapes():
    result = [{"id": "valid-" + sample["id"], "unsigned": expected_receipt(sample), "accepted": True}
              for sample in fixtures()]
    baseline = expected_receipt(fixture("declared", True))
    for number, (path, value) in enumerate(INVALID_FIELDS):
        result.append({"id": f"invalid-{number}-{path}", "unsigned": replace(baseline, path, value), "accepted": False})
    missing = deepcopy(baseline)
    del missing["businessDecision"]
    result.append({"id": "missing-required-null", "unsigned": missing, "accepted": False})
    extra = deepcopy(baseline)
    extra["currentEffect"] = {}
    result.append({"id": "unknown-root-field", "unsigned": extra, "accepted": False})
    web = expected_receipt(fixture())
    for field in ("deviceId", "deviceVersion"):
        changed = replace(web, "actor." + field, identifier(99) if field == "deviceId" else "99")
        result.append({"id": "web-nonnull-" + field, "unsigned": changed, "accepted": False})
    return result
