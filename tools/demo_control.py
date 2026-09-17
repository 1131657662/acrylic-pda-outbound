"""Debug-only same-UID Demo commands. No command constitutes physical acceptance."""
import argparse
import json
import shlex
import subprocess
import time
import uuid
from pathlib import Path

PACKAGE = "com.acrylic.m71demo.debug"
EVIDENCE = f"/sdcard/Android/data/{PACKAGE}/files/evidence"


class Device:
    def __init__(self, serial="22D2910042", adb=None):
        self.prefix = [str(adb or Path.home() / "AppData/Local/Android/Sdk/platform-tools/adb.exe"), "-s", serial]

    def run(self, *args, timeout=30):
        result = subprocess.run(self.prefix + list(args), capture_output=True, timeout=timeout)
        if result.returncode:
            raise RuntimeError(result.stderr.decode(errors="replace") + result.stdout.decode(errors="replace"))
        return result.stdout

    def shell(self, *args, timeout=30):
        return self.run("shell", shlex.join(args), timeout=timeout)

    def command(self, op, timeout=30, **fields):
        request = str(uuid.uuid4())
        args = ["run-as", PACKAGE, "am", "broadcast", "--user", "0", "-n",
                f"{PACKAGE}/com.acrylic.m71demo.automation.DemoControlReceiver",
                "--es", "requestId", request, "--es", "op", op]
        for key, value in fields.items():
            kind = "--ez" if isinstance(value, bool) else "--ei" if isinstance(value, int) else "--es"
            args += [kind, key, str(value).lower() if isinstance(value, bool) else str(value)]
        self.shell(*args)
        path = f"{EVIDENCE}/automation/{request}.json"
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            result = subprocess.run(self.prefix + ["shell", "cat", path], capture_output=True, timeout=10)
            if result.returncode == 0:
                # Android 9 AtomicFile exposes its new base while finishWrite is pending.
                # Retry only this response read; never resend the mutation.
                try:
                    body = json.loads(result.stdout)
                except json.JSONDecodeError:
                    body = None
                if body is not None:
                    if body.get("requestId") != request:
                        raise RuntimeError("Response requestId mismatch")
                    if not body["ok"]:
                        raise RuntimeError(body)
                    return body["result"]
            time.sleep(2 if op == "HISTORY" else .2)
        raise TimeoutError(f"No response for {op}, requestId={request}; do not blindly repeat mutations")

    def launch(self):
        state = self.shell("dumpsys", "power").decode()
        if "mWakefulness=Asleep" in state:
            self.shell("input", "keyevent", "26")
            time.sleep(.5)
        self.shell("wm", "dismiss-keyguard")
        self.shell("am", "start", "-n", f"{PACKAGE}/com.acrylic.m71demo.ui.MainActivity")
        time.sleep(1)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("op")
    parser.add_argument("--fields", default="{}")
    parser.add_argument("--serial", default="22D2910042")
    parser.add_argument("--timeout", type=int, default=30)
    parser.add_argument("--launch", action="store_true")
    args = parser.parse_args()
    device = Device(args.serial)
    if args.launch:
        device.launch()
    print(json.dumps(device.command(args.op, timeout=args.timeout, **json.loads(args.fields)), ensure_ascii=False))


if __name__ == "__main__":
    main()
