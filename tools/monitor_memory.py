"""Host dumpsys samples include graphics; sampled peaks are not instantaneous upper bounds."""
import argparse
import json
import re
import time
from pathlib import Path
from demo_control import Device, PACKAGE


def parse_memory(raw):
    total = re.search(r"^\s*TOTAL\s+PSS:\s*(\d+)", raw, re.MULTILINE)
    if total is None:
        total = re.search(r"^\s*TOTAL\s+(\d+)\s", raw, re.MULTILINE)
    if total is None:
        raise ValueError("No process TOTAL PSS in dumpsys; missing samples cannot imply a pass")
    graphics = re.search(r"^\s*Graphics:\s*(\d+)", raw, re.MULTILINE)
    return {"totalPssBytes": int(total[1]) * 1024,
            "graphicsPssBytes": int(graphics[1]) * 1024 if graphics else None}


def sample(device, options, number):
    started = time.monotonic()
    at = int(time.time() * 1000)
    raw = device.shell("dumpsys", "meminfo", "--local", options.package, timeout=15)
    name = f"sample-{number:04}.txt"
    (options.output / name).write_bytes(raw)
    return {"hostWallMs": at, "durationMs": round((time.monotonic() - started) * 1000, 3),
            "rawFile": name, **parse_memory(raw.decode("utf-8", errors="replace"))}


def run(args):
    args.output.mkdir(parents=True, exist_ok=False)
    device = Device()
    start = time.monotonic()
    samples, failures = [], []
    while time.monotonic() - start < args.seconds and not (args.output / "STOP").exists():
        number = len(samples) + len(failures) + 1
        try:
            result = sample(device, args, number)
            samples.append(result)
        except Exception as error:
            result = {"sample": number, "hostWallMs": int(time.time() * 1000), "error": str(error)}
            failures.append(result)
        with (args.output / "samples.ndjson").open("a", encoding="utf-8") as trace:
            trace.write(json.dumps(result) + "\n")
        time.sleep(max(0, min(start + number * args.interval - time.monotonic(), 1 if (args.output / "STOP").exists() else args.interval)))
    report = {"sampleCount": len(samples), "failures": failures,
              "intervalSeconds": args.interval, "durationSeconds": time.monotonic() - start,
              "peakTotalPssBytes": max((s["totalPssBytes"] for s in samples), default=None),
              "peakGraphicsPssBytes": max((s["graphicsPssBytes"] or 0 for s in samples), default=None),
              "absolutePeakProven": False,
              "commandMode": "dumpsys meminfo --local (no app-process detail callback)",
              "package": args.package,
              "scope": "host samples; may perturb workload; raw files retained"}
    report["sampledThresholdPassed"] = bool(samples and not failures and report["peakTotalPssBytes"] <= 250_000_000)
    (args.output / "summary.json").write_text(json.dumps(report, indent=2))
    print(json.dumps(report), flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("output", type=Path)
    parser.add_argument("--seconds", type=float, default=650)
    parser.add_argument("--interval", type=float, default=2)
    parser.add_argument("--package", default=PACKAGE)
    args = parser.parse_args()
    if args.seconds <= 0 or args.interval < 1:
        parser.error("Positive duration and interval of at least one second required")
    run(args)
