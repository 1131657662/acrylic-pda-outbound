"""Small UI smoke-test helper; ADB events are not physical M71 key events."""
import argparse
import re
import subprocess
import sys
import uuid
from xml.etree import ElementTree as ET


def main():
    sys.stdout.reconfigure(encoding="utf-8")
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("serial")
    parser.add_argument("--adb", required=True)
    parser.add_argument("--tap")
    args = parser.parse_args()
    command = [args.adb, "-s", args.serial, "shell"]
    remote = f"/sdcard/demo-ui-{uuid.uuid4()}.xml"
    dumped = subprocess.run(command + ["uiautomator", "dump", remote], check=True, capture_output=True, timeout=30)
    if b"dumped to" not in dumped.stdout:
        raise RuntimeError("UI snapshot not available; wait until software load stops. No stale XML is used.")
    result = subprocess.run(command + ["cat", remote], check=True, capture_output=True)
    nodes = list(ET.fromstring(result.stdout).iter("node"))
    if not args.tap:
        for node in nodes:
            if node.get("text"):
                print(node.get("bounds"), node.get("text")[:180])
        return
    matches = [n for n in nodes if n.get("text") == args.tap or n.get("resource-id") == args.tap]
    if len(matches) != 1:
        raise ValueError(f"Expected exactly one visible target, found {len(matches)}")
    bounds = [int(value) for value in re.findall(r"\d+", matches[0].get("bounds"))]
    x, y = (bounds[0] + bounds[2]) // 2, (bounds[1] + bounds[3]) // 2
    subprocess.run(command + ["input", "tap", str(x), str(y)], check=True)


if __name__ == "__main__":
    main()
