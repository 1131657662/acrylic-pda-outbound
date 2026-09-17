"""D0-D6 process-kill campaign on synthetic orders; never physical power-loss evidence."""
import argparse
import json
import time
import uuid
from pathlib import Path
from demo_control import Device, EVIDENCE, PACKAGE
from device_snapshot import snapshot, order_facts, verify_saved

STAGES = {"D0": "BEFORE_CAPTURE", "D1": "AFTER_CAPTURE", "D2": "AFTER_CONFIRM",
          "D3": "DURING_SAVE", "D4": "AFTER_SAVE", "D5": "AFTER_FEEDBACK"}


def wait_dead(device, old_pid):
    deadline = time.monotonic() + 15
    while time.monotonic() < deadline:
        try:
            pid = device.shell("pidof", PACKAGE).decode().strip()
        except RuntimeError:
            return
        if pid != old_pid:
            return
        time.sleep(.2)
    raise AssertionError("Armed kill did not terminate the original process")


def save_before(device, root):
    device.shell("am", "force-stop", PACKAGE)
    snapshot(device, root / "before.tar.gz")
    device.launch()
    device.command("STATUS")


def one(device, root, gate):
    root.mkdir(parents=True, exist_ok=False)
    run = device.command("CREATE", count=2)
    capture = str(uuid.uuid4())
    raw = "003X" + str(run["startSerial"]).zfill(7)
    intent = {"gate": gate, "stage": STAGES[gate], "orderId": run["orderId"], "captureId": capture, "raw": raw}
    (root / "intent.json").write_text(json.dumps(intent, indent=2))
    save_before(device, root)
    pid = device.shell("pidof", PACKAGE).decode().strip()
    try:
        device.command("INPUT", raw=raw, captureId=capture, fault=STAGES[gate], faultMode="KILL_PROCESS", timeout=4)
    except TimeoutError:
        pass  # Durable fault witness and dead PID below determine whether the injection occurred.
    wait_dead(device, pid)
    witness = device.shell("cat", f"{EVIDENCE}/fault-{capture}-{STAGES[gate]}.json")
    (root / "fault-witness.json").write_bytes(witness)
    snapshot(device, root / "killed.tar.gz")
    killed = order_facts(root / "killed.tar.gz", run["orderId"])
    check_killed(killed, gate, capture)
    device.launch()
    state = device.command("STATUS")
    device.shell("am", "force-stop", PACKAGE)
    snapshot(device, root / "recovered.tar.gz")
    recovered = order_facts(root / "recovered.tar.gz", run["orderId"])
    verify_saved(recovered, capture, 0 if gate == "D0" else 1)
    result = {"processRecoveryPassed": True, "physicalPowerLossPassed": False, "gate": gate,
              "state": state, "killed": killed, "recovered": recovered}
    (root / "verification.json").write_text(json.dumps(result, indent=2))
    print(f"{root.name}: process recovery PASS; physical power-loss NOT TESTED", flush=True)
    device.launch()
    device.command("STATUS")


def check_killed(facts, gate, capture):
    assert facts["integrity"] == ["ok"] and facts["foreignKeys"] == []
    rows = [r for r in facts["local_input_capture"] if r["id"] == capture]
    expected = {"D1": "RECEIVED", "D2": "CONFIRM_REQUESTED", "D3": "CONFIRM_REQUESTED", "D4": "COMMITTED", "D5": "COMMITTED"}
    if gate == "D0":
        assert rows == [] and facts["local_scan"] == []
    else:
        assert len(rows) == 1 and rows[0]["state"] == expected[gate]
        if gate in ("D1", "D2", "D3"):
            assert facts["local_scan"] == [] and facts["local_barcode_guard"] == []
            assert not any(r["type"] == "SAVED" for r in facts["local_operation_event"])
        else:
            verify_saved(facts, capture)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("output", type=Path)
    parser.add_argument("--repeats", type=int, default=20)
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    device = Device(); device.launch(); device.command("STATUS")
    for gate in STAGES:
        for number in range(1 if gate == "D0" else args.repeats):
            target = args.output / f"{gate}-{number+1:02}"
            if (target / "verification.json").exists():
                continue
            one(device, target, gate)


if __name__ == "__main__":
    main()
