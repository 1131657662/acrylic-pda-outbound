"""Synthetic future receipt content: intentionally not a production receipt schema."""
import copy
from canonical import digest
from fixtures import identifier, replacement
from scenarios import evidence_command


def receipt_fixture():
    body = evidence_command("PHYSICAL_DISPOSITION")
    return {"receiptVersion": 1, "receiptId": identifier(30), "commandId": body["commandId"],
            "routeScope": "r06.command.v1", "commandType": body["commandType"],
            "commandSha256": digest(body, "command"), "originalContext": body,
            "actor": {"principalId": identifier(31), "userAuthVersion": "2",
                      "deviceId": None, "sessionId": None, "permissionSnapshot": []},
            "originalOperatorId": identifier(32), "decision": "SYNTHETIC_ONLY",
            "recordedAt": "2026-09-21T00:00:00Z", "localDeletionAllowed": False}


def changed_receipts():
    receipt = receipt_fixture()
    changes = {"receiptId": identifier(39), "commandId": identifier(40),
               "routeScope": "r06.command.v1-proposal", "commandSha256": "0" * 64,
               "actor.principalId": identifier(41), "actor.userAuthVersion": "3",
               "actor.deviceId": identifier(42), "actor.sessionId": identifier(43),
               "actor.permissionSnapshot": ["temporary_order:confirm"],
               "originalOperatorId": identifier(44), "localDeletionAllowed": True,
               "originalContext.source.clientOrderId": identifier(45),
               "originalContext.source.creatorDeviceId": identifier(46),
               "originalContext.expected.canonicalOrderId": identifier(47)}
    for key, value in receipt["originalContext"]["expected"].items():
        if "evidence" in key.lower() or "verification" in key.lower() or "Manifest" in key:
            changes["originalContext.expected." + key] = "9" if value is None else str(value) + "changed"
    return [{"id": path.replace(".", "-"), "receipt": replacement(receipt, path, value)}
            for path, value in changes.items()]


def signing_samples():
    base = {"iss": "acrylic-outbound", "aud": "acrylic-r06-command-receipt-v1",
            "receipt": receipt_fixture()}
    samples = [{"id": "valid", "claims": base}]
    samples += [{"id": row["id"], "claims": dict(base, receipt=row["receipt"])}
                for row in changed_receipts()]
    samples += [{"id": "wrong-audience", "claims": dict(base, aud="acrylic-pda-receipt")},
                {"id": "wrong-issuer", "claims": dict(base, iss="untrusted")},
                {"id": "proposal-audience", "claims": dict(base, aud="acrylic-r06-command-receipt-v1-proposal")}]
    nullable = copy.deepcopy(base)
    nullable["receipt"]["originalContext"]["expected"].update(
        canonicalOrderId=None, mappingVersion="0", slotVersion=None, targetRevision=None,
        receivedPayloadSha256=None)
    nullable["receipt"]["commandSha256"] = digest(nullable["receipt"]["originalContext"], "command")
    samples.append({"id": "nullable-synthetic", "claims": nullable})
    second = copy.deepcopy(base)
    second["receipt"]["receiptId"] = identifier(90)
    second["receipt"]["actor"]["principalId"] = identifier(91)
    samples.append({"id": "second-actor-same-command", "claims": second})
    return samples
