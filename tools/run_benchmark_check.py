"""Run one independent software load on M71, preserving failed as well as passed evidence."""
import argparse
import json
import time
from pathlib import Path
from demo_control import Device, EVIDENCE, PACKAGE
from evidence import read_export, verify
from metrics import observations, quantiles


def collect(device, root, order):
    output = device.command("EXPORT")
    device.run("pull", output["path"], str(root / "export"))
    device.run("pull", f"{EVIDENCE}/expected-{order}.ndjson", str(root / "expected.ndjson"))
    names = device.shell("ls", EVIDENCE).decode().split()
    events = sorted(name for name in names if name.startswith("events-") and name.endswith(".ndjson"))
    device.run("pull", f"{EVIDENCE}/{events[-1]}", str(root / "full-events.ndjson"))
    (root / "package.txt").write_bytes(device.shell("dumpsys", "package", PACKAGE))
    (root / "meminfo-after.txt").write_bytes(device.shell("dumpsys", "meminfo", PACKAGE))
    return output["path"]


def assess(root, order, count):
    rows = [json.loads(line) for line in (root / "full-events.ndjson").read_text(encoding="utf-8").splitlines()]
    begin = next(e["elapsedNs"] for e in rows if e["type"] == "BENCHMARK_START" and e["orderId"] == order)
    window = [e for e in rows if e["elapsedNs"] >= begin]
    (root / "window.ndjson").write_text("\n".join(json.dumps(e) for e in window) + "\n")
    result = verify(root / "export", root / "expected.ndjson")
    _, exported = read_export(root / "export")
    result["observations"] = observations([root / "window.ndjson"], exported["captures"])
    result["chain"] = chain(window, exported["captures"], count)
    result["transactionTiming"] = timing(window)
    measured = result["observations"]["autoReceiveToCommit"]
    result["latencyTargetPassed"] = bool(measured and measured["p95Ms"] <= 100 and measured["p99Ms"] <= 200)
    (root / "verification.json").write_text(json.dumps(result, indent=2))
    return result


def chain(events, captures, count):
    callbacks = [e for e in events if e["type"] == "BROADCAST_CALLBACK" and e.get("source") == "TEST_INJECTION"]
    saves = [e for e in events if e["type"] == "SAVE_RESULT" and e.get("status") == "SAVED"]
    sounds = [e for e in events if e["type"] == "SOUND_REQUEST" and e.get("sound") == "SAVED"]
    ids = [c["captureId"] for c in captures]
    errors = []
    if len(ids) != count or [e["captureId"] for e in callbacks] != ids:
        errors.append("broadcast identity/count/order mismatch")
    if [e.get("rawInput") for e in callbacks] != [c["rawInput"] for c in captures]:
        errors.append("broadcast raw characters mismatch")
    for label, rows in [("save", saves), ("sound", sounds)]:
        if [e["captureId"] for e in rows] != ids:
            errors.append(f"{label} sequence mismatch")
    if any(e.get("streamId") == 0 for e in sounds):
        errors.append("SoundPool playback failed")
    times = [c["receivedElapsedNs"] for c in captures]
    return {"passed": not errors, "errors": errors, "expectedCount": count,
            "receiveSpanSeconds": (times[-1] - times[0]) / 1e9 if len(times) > 1 else 0,
            "intervals": quantiles([b - a for a, b in zip(times, times[1:])])}


def timing(events):
    result = {}
    for stage in ("RECEIVE", "VALIDATE_INTENT", "SAVE"):
        rows = [e for e in events if e["type"] == "TRANSACTION_TIMING" and e["stage"] == stage]
        result[stage] = {field: quantiles([e[field] for e in rows]) for field in ("beginNs", "bodyNs", "commitNs")}
    result["queueWait"] = quantiles([e["queueWaitNs"] for e in events if e["type"] == "WORKER_RECEIVE"])
    result["uiRefresh"] = quantiles([e["durationNs"] for e in events if e["type"] == "UI_REFRESH"])
    return result


def run(device, root, rate, seconds, cold=False):
    root.mkdir(parents=True, exist_ok=False)
    if cold:
        device.shell("am", "force-stop", PACKAGE)
    device.launch()
    device.command("BENCHMARK", rate=rate, seconds=seconds)
    time.sleep(1)
    initial = device.command("STATUS")
    (root / "start.json").write_text(json.dumps(initial, indent=2))
    order = initial["orderId"]
    print(json.dumps({"started": str(root), "order": order, "rate": rate, "seconds": seconds}), flush=True)
    start = time.monotonic()
    while time.monotonic() - start < seconds:
        time.sleep(min(30, seconds - (time.monotonic() - start)))
        print(f"{root.name}: elapsed {int(time.monotonic()-start)} / {seconds}s", flush=True)
    state = device.command("STATUS")
    (root / "end.json").write_text(json.dumps(state, indent=2))
    if state["benchmarkRunning"] or state["pending"] or state.get("captureId") or state["saved"] != rate * seconds:
        raise RuntimeError(f"Incomplete load; retained evidence: {state}")
    collect(device, root, order)
    result = assess(root, order, rate * seconds)
    print(json.dumps({"result": str(root), "integrity": result["integrityPassed"],
                      "chain": result["chain"], "latency": result["observations"]["autoReceiveToCommit"],
                      "latencyTargetPassed": result["latencyTargetPassed"]}), flush=True)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("output", type=Path)
    parser.add_argument("--rate", type=int, default=4, choices=range(1, 5))
    parser.add_argument("--seconds", type=int, default=600)
    parser.add_argument("--cold", action="store_true")
    args = parser.parse_args()
    run(Device(), args.output, args.rate, args.seconds, args.cold)


if __name__ == "__main__":
    main()
