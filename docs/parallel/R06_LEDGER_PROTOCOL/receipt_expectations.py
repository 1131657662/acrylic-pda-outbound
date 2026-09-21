"""Expected C1b wire from the agreed contract and independent synthetic facts."""
from copy import deepcopy
from ledger_primitives import digest

CAPABILITIES = {
    "writeConfirmationAvailable": False, "releaseAllowed": False,
    "mayResumeScanning": False, "stockReleased": False, "localDeletionAllowed": False,
}


def expected_receipt(fixture):
    command = fixture["command"]
    return {
        "receiptVersion": 1, "receiptId": fixture["receiptId"], "commandId": command["commandId"],
        "routeScope": "r06.command.v1", "commandType": command["commandType"],
        "commandSha256": digest(command, "command"), "status": "INTERNAL_TEST_ONLY",
        "businessDecision": None, "recordedAt": fixture["recordedAt"], "eventId": fixture["eventId"],
        "resultingDecisionVersion": str(int(fixture["context"]["decisionVersion"]) + 1),
        "originalContext": deepcopy(fixture["context"]), "actor": deepcopy(fixture["actor"]),
        "expected": deepcopy(command["expected"]), "capabilities": dict(CAPABILITIES),
    }
