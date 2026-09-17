"""Thirty-minute scripted workflow mix. Manual-path events are simulated, not physical key tests."""
import argparse
import json
import time
import uuid
from pathlib import Path
from demo_control import Device, PACKAGE
from evidence import verify


def receive(device, root, raw, **options):
    capture = str(uuid.uuid4())
    with (root / "expected-actions.ndjson").open("a") as trace:
        trace.write(json.dumps({"captureId": capture, "raw": raw, "options": options}) + "\n")
    device.command("INPUT", raw=raw, captureId=capture, **options)
    time.sleep(.25)
    return capture, device.command("STATUS")


def duplicates(device, root, codes):
    _, state = receive(device, root, codes[0]); assert state["saved"] == 1
    duplicate, state = receive(device, root, codes[0])
    assert state["saved"] == 1 and state["captureState"] == "INVALID"
    assert state["error"] == "BARCODE_ALREADY_OCCUPIED"
    if root.name == "cycle-001":
        (root / "duplicate-screen.png").write_bytes(device.run("exec-out", "screencap", "-p"))
    _, state = receive(device, root, codes[1]); assert state["saved"] == 1
    device.command("ACK", captureId=duplicate)
    assert device.command("STATUS")["saved"] == 2


def manual_and_void(device, root, codes):
    manual, state = receive(device, root, codes[2], manual=True)
    assert state["saved"] == 2 and state["captureState"] == "WAITING_CONFIRM"
    if root.name == "cycle-001":
        (root / "manual-confirm-screen.png").write_bytes(device.run("exec-out", "screencap", "-p"))
    _, state = receive(device, root, codes[3]); assert state["saved"] == 2
    device.command("CONFIRM", captureId=manual)
    assert device.command("STATUS")["saved"] == 4
    device.command("VOID")
    assert device.command("STATUS")["saved"] == 3
    _, state = receive(device, root, codes[3]); assert state["saved"] == 4


def failures(device, root, codes):
    _, state = receive(device, root, codes[4]); assert state["saved"] == 5
    over, state = receive(device, root, codes[6])
    assert state["error"] == "PLAN_EXCEEDED" and state["saved"] == 5
    device.command("ACK", captureId=over)
    invalid, state = receive(device, root, "BROKEN")
    assert state["error"] == "BARCODE_FORMAT" and state["saved"] == 5
    device.command("ACK", captureId=invalid)
    _, state = receive(device, root, codes[5], fault="DURING_SAVE", faultMode="STORAGE_ERROR")
    assert state["captureState"] == "STORAGE_ERROR" and state["saved"] == 5
    if root.name == "cycle-001":
        (root / "storage-error-screen.png").write_bytes(device.run("exec-out", "screencap", "-p"))
    device.command("RECOVER")
    assert device.command("STATUS")["saved"] == 6
    device.command("NOTE")


def cycle(device, root):
    root.mkdir()
    run = device.command("CREATE", count=8)
    device.command("PLAN_LIMIT", limit=3)
    codes = [("003" if i % 2 == 0 else "571") + "X" + str(run["startSerial"] + i).zfill(7) for i in range(8)]
    duplicates(device, root, codes)
    manual_and_void(device, root, codes)
    failures(device, root, codes)
    exported = device.command("EXPORT")["path"]
    device.run("pull", exported, str(root / "export"))
    result = verify(root / "export")
    assert result["integrityPassed"] and result["effectiveCount"] == 6
    (root / "verification.json").write_text(json.dumps(result, indent=2))
    return run["orderId"]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("output", type=Path)
    parser.add_argument("--seconds", type=int, default=1800)
    args = parser.parse_args(); args.output.mkdir(parents=True, exist_ok=False)
    device = Device(); device.launch(); device.command("STATUS")
    start = time.monotonic(); number = 0; previous = None
    while time.monotonic() - start < args.seconds:
        target = args.output / f"cycle-{number+1:03}"
        current = cycle(device, target)
        if previous:
            device.command("SELECT", orderId=previous)
            assert device.command("STATUS")["saved"] == 6
            device.command("SELECT", orderId=current)
        previous = current; number += 1
        print(f"Mixed cycle {number} PASS; {int(time.monotonic()-start)} / {args.seconds}s", flush=True)
        delay = min(max(0, number * 30 - (time.monotonic() - start)), max(0, args.seconds - (time.monotonic() - start)))
        time.sleep(delay)
    (args.output / "summary.json").write_text(json.dumps({"softwareMixPassed": True, "physicalInputPassed": False,
        "cycles": number, "durationSeconds": time.monotonic()-start, "effectiveScans": number*6}, indent=2))
    from mixed_evidence import collect
    collect(device, args.output)


if __name__ == "__main__":
    main()
