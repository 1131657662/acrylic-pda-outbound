"""Protocol examples fixed from W1's fifth-wave interface contract."""
import base64
from canonical import canonical
from fixtures import command, identifier, load, omission, replacement

EVIDENCE = {
    "evidenceSetId": identifier(21), "evidenceSetSha256": "e" * 64,
    "verificationId": identifier(22), "verificationVersion": "1",
    "verificationAlgorithmVersion": "1",
}


def evidence_command(kind):
    value = command(kind)
    value["expected"].update(EVIDENCE)
    prefix = "disposition" if kind == "PHYSICAL_DISPOSITION" else "retained"
    value["expected"][prefix + "ManifestSha256"] = "f" * 64
    value["expected"][prefix + "ManifestVersion"] = "1"
    return value


def sample(name, value, mode="command"):
    return {"id": name, "raw": canonical(value).decode("utf-8"), "mode": mode}


def valid_commands():
    kinds = ["NORMAL_CONFIRM", "DUPLICATE_EXCLUDE", "LINK", "REJECT", "SHORT_APPROVE"]
    result = [sample(kind.lower(), command(kind)) for kind in kinds]
    result += [sample(kind.lower(), evidence_command(kind)) for kind in
               ("PHYSICAL_DISPOSITION", "REOPEN_WORK")]
    nullable = command()
    nullable["expected"].update(canonicalOrderId=None, mappingVersion="0", slotVersion=None,
                                targetRevision=None, receivedPayloadSha256=None)
    result.append(sample("nullable-canonical", nullable))
    result.append(sample("normal-reason-preserved", replacement(command(), "reason", " \t\n中é ")))
    result.append(sample("reason-1000-utf16", replacement(command(), "reason", "𐀀" * 500)))
    return result


def invalid_commands():
    result = [sample("missing-" + key, omission(command(), "expected." + key))
              for key in command()["expected"]]
    changes = {"commandVersion": 4294967297, "commandId": "bad", "workRound": "01", "reason": None,
               "source.kind": "FORMAL", "expected.userAuthVersion": 2,
               "expected.evidenceSetId": identifier(21), "reason-too-long": "𐀀" * 501}
    for key, value in changes.items():
        path = "reason" if key == "reason-too-long" else key
        result.append(sample("bad-" + key, replacement(command(), path, value)))
    actor = command()
    actor["actor"] = {"principalId": identifier(99)}
    result.append(sample("client-actor", actor))
    result.append(sample("reject-blank-reason", replacement(command("REJECT"), "reason", " \t\n")))
    result += invalid_evidence()
    return [dict(row, reject=True) for row in result]


def invalid_evidence():
    result = []
    for kind in ("PHYSICAL_DISPOSITION", "REOPEN_WORK"):
        body = evidence_command(kind)
        for key, value in body["expected"].items():
            if key in EVIDENCE or ("Manifest" in key and value is not None):
                result.append(sample(kind + "-null-" + key, replacement(body, "expected." + key, None)))
        for key in ("verificationVersion", "verificationAlgorithmVersion"):
            result.append(sample(kind + "-zero-" + key, replacement(body, "expected." + key, "0")))
        opposite = "retained" if kind == "PHYSICAL_DISPOSITION" else "disposition"
        body["expected"][opposite + "ManifestSha256"] = "e" * 64
        body["expected"][opposite + "ManifestVersion"] = "1"
        result.append(sample(kind + "-opposite-manifest", body))
    return result


def binding_commands():
    result = []
    for kind in ("PHYSICAL_DISPOSITION", "REOPEN_WORK"):
        body = evidence_command(kind)
        for key, value in body["expected"].items():
            if value is None:
                continue
            changed = "3" if value.isdecimal() else ("9" * 64 if len(value) == 64 else identifier(88))
            result.append(sample(kind + "-bind-" + key, replacement(body, "expected." + key, changed), "commandHash"))
    body = evidence_command("PHYSICAL_DISPOSITION")
    changes = {"commandId": identifier(80), "warehouseId": identifier(81),
               "source.creatorDeviceId": identifier(82), "source.clientOrderId": identifier(83),
               "submissionId": identifier(84), "authorizationId": identifier(85),
               "freezeDeviceId": identifier(86), "workRound": "2",
               "sourceRecoveryEpoch": "2", "transportRecoveryEpoch": "2", "reason": "另一个理由"}
    result += [sample("top-binding-" + path, replacement(body, path, value), "commandHash")
               for path, value in changes.items()]
    return result


def byte_cases():
    result = []
    for name, raw in {"invalid-utf8": b'"\xc0\xaf"', "utf8-surrogate": b'"\xed\xa0\x80"',
                      "truncated-utf8": b'"\xe4\xb8"', "utf8-bom": b'\xef\xbb\xbf{}'}.items():
        result.append({"id": name, "rawBase64": base64.b64encode(raw).decode(),
                       "mode": "canonical", "reject": True})
    result += [{"id": "input-limit", "mode": "canonical", "raw": '"' + "a" * 262142 + '"'},
               {"id": "input-over-limit", "mode": "canonical", "raw": '"' + "a" * 262143 + '"', "reject": True},
               {"id": "raw-hash-invalid-utf8", "mode": "rawHash", "rawBase64": "wK8=", "reject": True}]
    return result


def primitive_cases():
    result = [dict(row, mode="canonical") for row in load("canonical_vectors.json")["vectors"]]
    result += valid_commands() + invalid_commands() + binding_commands() + byte_cases()
    for mode in ("receiptHash", "controlHash"):
        result += [{"id": mode + "-scalar", "mode": mode, "raw": "null", "reject": True}]
    result += [{"id": "raw-is-exact", "mode": "rawHash", "raw": "  not JSON\n"}]
    result += [{"id": mode, "mode": mode, "raw": "{}"}
               for mode in ("routeScope", "receiptAudience", "issuer", "versions")]
    return result
