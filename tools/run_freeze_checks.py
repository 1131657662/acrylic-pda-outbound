"""D6 process death before/after frozen-body registration, using isolated test orders."""
import json
import time
from pathlib import Path
from demo_control import Device, EVIDENCE, PACKAGE
from device_snapshot import snapshot, order_facts
from run_crash_checks import wait_dead, save_before


def one(device, root, stage):
    root.mkdir(parents=True, exist_ok=False)
    run = device.command("CREATE", count=1)
    device.command("INPUT", raw="003X" + str(run["startSerial"]).zfill(7))
    time.sleep(.5)
    assert device.command("STATUS")["saved"] == 1
    save_before(device, root)
    pid = device.shell("pidof", PACKAGE).decode().strip()
    try:
        device.command("FREEZE", fault=stage, faultMode="KILL_PROCESS", timeout=4)
    except TimeoutError:
        pass
    wait_dead(device, pid)
    (root / "fault-witness.json").write_bytes(device.shell("cat", f"{EVIDENCE}/fault-{run['orderId']}-{stage}.json"))
    snapshot(device, root / "killed.tar.gz")
    killed = order_facts(root / "killed.tar.gz", run["orderId"])
    assert len(killed["local_submission"]) == (1 if stage == "AFTER_FREEZE_REGISTER" else 0)
    device.run("pull", f"{EVIDENCE}/{run['orderId']}", str(root / "bodies-at-kill"))
    device.launch(); device.command("STATUS")
    path = device.command("FREEZE")["path"]
    assert device.command("FREEZE")["path"] == path
    device.run("pull", path, str(root / "effective-body"))
    device.shell("am", "force-stop", PACKAGE)
    snapshot(device, root / "recovered.tar.gz")
    recovered = order_facts(root / "recovered.tar.gz", run["orderId"])
    validate(root, killed, recovered, stage, path)
    device.launch(); device.command("STATUS")


def validate(root, killed, recovered, stage, path):
    from evidence import verify
    assert recovered["integrity"] == ["ok"] and recovered["foreignKeys"] == []
    assert len(recovered["local_submission"]) == 1 and recovered["local_order"][0]["state"] == "FROZEN"
    if stage == "AFTER_FREEZE_REGISTER":
        assert killed["local_submission"] == recovered["local_submission"]
    bodies = [p.parent for p in (root / "bodies-at-kill").rglob("manifest.json")]
    assert bodies and all(verify(p)["integrityPassed"] for p in bodies)
    assert verify(root / "effective-body")["integrityPassed"]
    (root / "verification.json").write_text(json.dumps({"processRecoveryPassed": True,
        "physicalPowerLossPassed": False, "stage": stage, "effectivePath": path, "orphanBodiesPreserved": len(bodies),
        "killed": killed, "recovered": recovered}, indent=2))
    print(stage + ": process recovery PASS", flush=True)


if __name__ == "__main__":
    import sys
    root = Path(sys.argv[1]); device = Device(); device.launch(); device.command("STATUS")
    for stage in ("BEFORE_FREEZE_REGISTER", "AFTER_FREEZE_REGISTER"):
        one(device, root / stage, stage)
