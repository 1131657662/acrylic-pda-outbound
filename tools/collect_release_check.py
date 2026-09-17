"""Collect a release Demo run started/exported through its UI; no debug receiver or run-as."""
import argparse
import json
import shutil
from pathlib import Path, PurePosixPath
from demo_control import Device
from run_benchmark_check import assess

PACKAGE = "com.acrylic.m71demo"
EVIDENCE = f"/sdcard/Android/data/{PACKAGE}/files/evidence"


def collect(args):
    path = PurePosixPath(args.export_path)
    if path.parent.name != args.order_id or ".." in path.parts:
        raise ValueError("Use the exact order export path shown by the release Demo")
    if f"/Android/data/{PACKAGE}/files/evidence/" not in str(path):
        raise ValueError("Export must be inside the release Demo evidence directory")
    args.output.mkdir(parents=True, exist_ok=False)
    device = Device()
    device.run("pull", args.export_path, str(args.output / "export"))
    device.run("pull", f"{EVIDENCE}/expected-{args.order_id}.ndjson", str(args.output / "expected.ndjson"))
    names = device.shell("ls", EVIDENCE).decode().split()
    latest = max(n for n in names if n.startswith("events-") and n.endswith(".ndjson"))
    device.run("pull", f"{EVIDENCE}/{latest}", str(args.output / "full-events.ndjson"))
    (args.output / "package.txt").write_bytes(device.shell("dumpsys", "package", PACKAGE))
    (args.output / "meminfo-after.txt").write_bytes(device.shell("dumpsys", "meminfo", "--local", PACKAGE))
    shutil.copyfile("demo-artifacts/RELEASE_VALIDATION_INFO.txt", args.output / "APK_INFO.txt")
    result = assess(args.output, args.order_id, args.count)
    print(json.dumps({"integrity": result["integrityPassed"], "chain": result["chain"]["passed"],
                      "latencyTargetPassed": result["latencyTargetPassed"],
                      "latency": result["observations"]["autoReceiveToCommit"],
                      "debugReceiverUsed": False, "physicalInputPassed": False}), flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("output", type=Path)
    parser.add_argument("--order-id", required=True)
    parser.add_argument("--export-path", required=True)
    parser.add_argument("--count", type=int, required=True)
    collect(parser.parse_args())
