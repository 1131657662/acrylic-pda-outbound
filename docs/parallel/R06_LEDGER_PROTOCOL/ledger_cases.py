"""Valid RSA signatures carrying deliberately wrong C1b facts and wire shapes."""
from copy import deepcopy
from ledger_primitives import identifier
from receipt_expectations import expected_receipt
from trusted_facts import fixture, replace

FACT_MUTATIONS = (
    ("actor.principalId", identifier(99)),
    ("actor.userAuthVersion", "13"),
    ("actor.deviceId", identifier(99)),
    ("actor.sessionId", identifier(99)),
    ("actor.deviceVersion", "99"),
    ("actor.permissionSnapshot.requiredPermissions", ["order:resume"]),
    ("actor.permissionSnapshot.effectivePermissions", ["temporary_order:confirm"]),
    ("actor.permissionSnapshot.roleIds", [identifier(99)]),
    ("actor.permissionSnapshot.warehouseIds", [identifier(99)]),
    ("originalContext.originalOperatorId", identifier(20)),
    ("originalContext.creatorDeviceId", identifier(99)),
    ("originalContext.freezeDeviceId", identifier(99)),
    ("originalContext.authorizationId", identifier(99)),
    ("originalContext.authorizationUserVersion", "99"),
    ("originalContext.sourceRecoveryEpoch", "99"),
    ("originalContext.mappingId", identifier(99)),
    ("originalContext.mappingVersion", "99"),
    ("originalContext.receivedPayloadSha256", "a" * 64),
    ("originalContext.declaredUploadSha256", "b" * 64),
    ("originalContext.canonicalOrderId", identifier(99)),
    ("expected.decisionVersion", "99"),
    ("expected.userAuthVersion", "99"),
    ("commandId", identifier(99)),
    ("receiptId", identifier(99)),
    ("eventId", identifier(99)),
    ("recordedAt", "2026-09-21T08:09:11.123Z"),
    ("resultingDecisionVersion", "99"),
    ("routeScope", "r06.command.v1.alias"),
    ("commandSha256", "c" * 64),
    ("status", "SUCCEEDED"),
    ("businessDecision", {"status": "SUCCEEDED"}),
    ("capabilities.releaseAllowed", True),
    ("capabilities.localDeletionAllowed", True),
)


def claims(unsigned):
    return {"iss": "acrylic-outbound", "aud": "acrylic-r06-command-receipt-v1", "receipt": deepcopy(unsigned)}


def case(label, facts, unsigned):
    return {"id": label, "facts": facts, "unsigned": unsigned,
            "claims": claims(unsigned), "cryptographicAcceptance": True,
            "expectedRejection": "TRUSTED_FACTS_CHANGED"}


def negative_cases():
    facts = fixture("declared", True)
    expected = expected_receipt(facts)
    result = [case("wrong-" + path.replace(".", "-"), facts, replace(expected, path, value))
              for path, value in FACT_MUTATIONS]
    result.extend(shape_cases(facts, expected))
    result.extend(purpose_cases(facts, expected))
    web = fixture()
    result.append(case("web-session-must-be-null", web,
                       replace(expected_receipt(web), "actor.sessionId", identifier(99))))
    result.append(case("web-device-must-be-null", web,
                       replace(expected_receipt(web), "actor.deviceId", identifier(99))))
    return result


def shape_cases(facts, expected):
    result = []
    for label, value in (("overflow", 4294967297), ("string", "1"), ("boolean", True)):
        result.append(case("receipt-version-" + label, facts, replace(expected, "receiptVersion", value)))
    missing = deepcopy(expected)
    del missing["businessDecision"]
    result.append(case("missing-null-business-decision", facts, missing))
    extra = deepcopy(expected)
    extra["currentEffect"] = {"releaseAllowed": True}
    result.append(case("effect-inside-original", facts, extra))
    result.append(case("permission-list-wrong-type", facts,
                       replace(expected, "actor.permissionSnapshot.effectivePermissions", "temporary_order:confirm")))
    result.append(case("role-list-reversed", facts,
                       replace(expected, "actor.permissionSnapshot.roleIds", [identifier(24), identifier(23)])))
    result.append(case("permission-duplicate", facts,
                       replace(expected, "actor.permissionSnapshot.requiredPermissions",
                               ["temporary_order:confirm", "temporary_order:confirm"])))
    return result


def purpose_cases(facts, expected):
    result = []
    for field, wrong in (("iss", "other-issuer"), ("aud", "acrylic-pda-receipt"),
                         ("aud", "acrylic-r06-command-receipt-proposal")):
        row = case("wrong-claim-" + field + "-" + wrong, facts, expected)
        row["claims"][field] = wrong
        row["cryptographicAcceptance"] = False
        row["expectedRejection"] = "SIGNATURE_ISSUER" if field == "iss" else "SIGNATURE_AUDIENCE"
        result.append(row)
    row = case("signature-inner-outer-mismatch", facts, expected)
    row["claims"]["receipt"]["actor"]["principalId"] = identifier(99)
    row["cryptographicAcceptance"] = False
    row["expectedRejection"] = "SIGNED_CONTENT_CHANGED"
    result.append(row)
    return result
