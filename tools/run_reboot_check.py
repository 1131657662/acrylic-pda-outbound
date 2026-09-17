"""Normal Android reboot: old confirmation remains auditable and requires authorization review."""
import json
import subprocess
import time
import uuid
from pathlib import Path
from demo_control import Device, EVIDENCE, PACKAGE
from device_snapshot import snapshot, order_facts
from run_crash_checks import save_before, wait_dead


def reboot(device):
    old = device.shell("settings", "get", "global", "boot_count").decode().strip()
    device.run("reboot")
    deadline = time.monotonic() + 240
    while time.monotonic() < deadline:
        time.sleep(3)
        try:
            new = device.shell("settings", "get", "global", "boot_count", timeout=10).decode().strip()
            completed = device.shell("getprop", "sys.boot_completed", timeout=10).decode().strip()
            if new != old and completed == "1":
                return {"bootCountBefore": old, "bootCountAfter": new, "normalOsReboot": True}
        except (RuntimeError, subprocess.TimeoutExpired):
            pass
    raise TimeoutError("Device boot did not complete; preserve all evidence and do not resend input")


def validate(facts, capture):
    assert facts["integrity"] == ["ok"] and facts["foreignKeys"] == []
    assert facts["local_scan"] == [] and facts["local_barcode_guard"] == []
    assert facts["local_order"][0]["actualCount"] == facts["local_order"][0]["weightGrams"] == 0
    inputs = facts["local_input_capture"]
    assert len(inputs) == 1 and inputs[0]["id"] == capture
    assert inputs[0]["state"] == "BUSINESS_REJECTED" and inputs[0]["error"] == "AUTH_REBOOT_REVIEW"
    audit = [r for r in facts["local_operation_event"] if r["captureId"] == capture]
    assert sorted(r["type"] for r in audit) == ["BUSINESS_REJECTED", "CONFIRM_REQUESTED", "RECEIVED"]


def prepare_confirmation(device, root):
    order = device.command("CREATE", count=1)
    capture = str(uuid.uuid4())
    intent = {"orderId": order["orderId"], "captureId": capture, "stage": "AFTER_CONFIRM"}
    (root / "intent.json").write_text(json.dumps(intent, indent=2))
    save_before(device, root)
    pid = device.shell("pidof", PACKAGE).decode().strip()
    try:
        device.command("INPUT", raw="003X" + str(order["startSerial"]).zfill(7), captureId=capture,
                       fault="AFTER_CONFIRM", faultMode="KILL_PROCESS", timeout=4)
    except TimeoutError:
        pass
    wait_dead(device, pid)
    (root / "fault-witness.json").write_bytes(device.shell("cat", f"{EVIDENCE}/fault-{capture}-AFTER_CONFIRM.json"))
    snapshot(device, root / "before-reboot.tar.gz")
    return order, capture


def run(root):
    root.mkdir(parents=True, exist_ok=False)
    device = Device(); device.launch(); device.command("STATUS")
    order, capture = prepare_confirmation(device, root)
    print("Confirmation durable; performing normal OS reboot (not sudden power loss)", flush=True)
    boot = reboot(device)
    device.launch(); state = device.command("STATUS")
    device.shell("am", "force-stop", PACKAGE)
    snapshot(device, root / "after-reboot.tar.gz")
    facts = order_facts(root / "after-reboot.tar.gz", order["orderId"])
    validate(facts, capture)
    old = order_facts(root / "before-reboot.tar.gz", order["orderId"])["local_input_capture"][0]
    current = facts["local_input_capture"][0]
    for key in ("originDeviceId", "orderScanSeq", "deviceCaptureSeq", "authorizationId", "bootSessionId",
                "expectedDispatchCycle", "sourceRecoveryEpoch", "confirmationPolicy", "confirmedAtNs"):
        assert current[key] == old[key]
    result = {**boot, "authorizationReviewPassed": True, "physicalPowerLossPassed": False, "state": state, "facts": facts}
    (root / "verification.json").write_text(json.dumps(result, indent=2))
    device.launch(); device.command("STATUS"); device.command("ACK", captureId=capture)
    print(json.dumps({**boot, "authorizationReviewPassed": True}), flush=True)


if __name__ == "__main__":
    import sys
    run(Path(sys.argv[1]))
