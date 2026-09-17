"""Compare scripted mixed inputs and sound requests against independent expected actions."""
import json
from demo_control import EVIDENCE
from evidence import rows
from metrics import observations


def verify_cycle(directory, events):
    expected = rows(directory / "expected-actions.ndjson")
    captures = rows(directory / "export/captures.ndjson")
    assert len(expected) == len(captures) == 10
    assert [(r["captureId"], r["raw"]) for r in expected] == [(r["captureId"], r["rawInput"]) for r in captures]
    for index, capture in enumerate(captures):
        assert capture["inputSource"] == ("MANUAL" if expected[index]["options"].get("manual") else "TEST_INJECTION")
        sound = "ERROR" if index in (1, 7, 8) else "SAVED"
        requests = [e for e in events if e["type"] == "SOUND_REQUEST" and e.get("captureId") == capture["captureId"]]
        assert len(requests) == 1 and requests[0]["sound"] == sound and requests[0]["streamId"] > 0
        if sound == "SAVED":
            saved = [e for e in events if e["type"] == "SAVE_RESULT" and e.get("captureId") == capture["captureId"] and e["status"] == "SAVED"]
            assert len(saved) == 1 and requests[0]["elapsedNs"] >= saved[0]["committedNs"]
    return captures


def collect(device, root):
    names = device.shell("ls", EVIDENCE).decode().split()
    latest = sorted(n for n in names if n.startswith("events-") and n.endswith(".ndjson"))[-1]
    device.run("pull", f"{EVIDENCE}/{latest}", str(root / "events.ndjson"))
    return verify(root)


def verify(root):
    events = rows(root / "events.ndjson")
    captures = []
    cycles = sorted(root.glob("cycle-*"))
    for directory in cycles:
        captures.extend(verify_cycle(directory, events))
    result = {"externalActionSequencePassed": True, "soundRequestPolicyPassed": True,
              "cycles": len(cycles), "inputCount": len(captures), "acousticOnsetMeasured": False,
              "observations": observations([root / "events.ndjson"], captures)}
    (root / "sequence-and-sound.json").write_text(json.dumps(result, indent=2))
    return result
