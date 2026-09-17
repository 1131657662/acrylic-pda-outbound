"""Seed and verify the full synthetic history on M71 without deleting existing data."""
import argparse
import json
import subprocess
import time
from pathlib import Path
from demo_control import Device, EVIDENCE, PACKAGE
from evidence import verify as verify_export
from verify_history import verify as verify_history
from verify_retention import verify as verify_retention


def backup(device, path):
    device.shell("am", "force-stop", PACKAGE)
    with path.open("xb") as output:
        subprocess.run(device.prefix + ["exec-out", "run-as", PACKAGE, "tar", "-cf", "-",
                                        "databases", "shared_prefs", "files"], stdout=output, check=True)


def export_samples(device, root, ids):
    result = []
    for number, order_id in enumerate(ids):
        device.shell("am", "force-stop", PACKAGE)
        device.launch()
        started = time.monotonic()
        device.command("SELECT", orderId=order_id)
        state = device.command("STATUS")
        assert state["orderId"] == order_id
        assert state["saved"] == 400 and state["weightGrams"] == 120200000 and state["pending"] == 0
        selected_ms = (time.monotonic() - started) * 1000
        started = time.monotonic()
        exported = device.command("EXPORT")["path"]
        export_ms = (time.monotonic() - started) * 1000
        target = root / f"history-export-{number+1}"
        device.run("pull", exported, str(target))
        checked = verify_export(target)
        assert checked["integrityPassed"] and checked["effectiveCount"] == 400 and checked["orderId"] == order_id
        result.append({"orderId": order_id, "selectAndStatusWithAdbMs": selected_ms,
                       "exportWithAdbMs": export_ms, "verification": checked})
    return result


def run(root, baseline):
    root.mkdir(parents=True, exist_ok=False)
    device = Device(); device.launch(); device.command("STATUS")
    (root / "space-before.txt").write_bytes(device.shell("df", "-k", "/data"))
    print("Generating 360000 synthetic history scans and 1080000 audits", flush=True)
    started = time.monotonic()
    state = device.command("HISTORY", timeout=3600)
    duration = time.monotonic() - started
    (root / "seed-result.json").write_text(json.dumps({"durationSeconds": duration, "state": state}, indent=2))
    names = device.shell("ls", EVIDENCE).decode().split()
    latest = max(n for n in names if n.startswith("events-") and n.endswith(".ndjson"))
    device.run("pull", f"{EVIDENCE}/{latest}", str(root / "seed-events.ndjson"))
    (root / "space-after.txt").write_bytes(device.shell("df", "-k", "/data"))
    print("Fixture generated; preserving stopped DB/WAL before independent full checks", flush=True)
    backup(device, root / "history-database.tar")
    history = verify_history(root / "history-database.tar")
    (root / "history-verification.json").write_text(json.dumps(history, indent=2))
    retained = verify_retention(baseline, root / "history-database.tar")
    (root / "retention-verification.json").write_text(json.dumps(retained, indent=2))
    print("Full history and original facts verified; checking cold-process paged exports", flush=True)
    samples = export_samples(device, root, history["sampleOrderIds"])
    (root / "sample-export-verification.json").write_text(json.dumps(samples, indent=2))
    print(json.dumps({"historyPassed": True, "sampleExports": len(samples), "seedSeconds": duration}), flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("output", type=Path)
    parser.add_argument("--baseline", type=Path, default=Path("demo-artifacts/m71-20260916-auto/before-0.1.9.tar"))
    args = parser.parse_args()
    run(args.output, args.baseline)
