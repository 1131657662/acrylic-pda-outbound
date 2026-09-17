"""Diagnostic observations are separate from durable facts and physical acceptance."""
import math
from collections import Counter, defaultdict
from evidence import rows


def quantiles(values):
    if not values:
        return None
    ordered = sorted(values)
    def rank(p):
        return round(ordered[max(0, math.ceil(len(ordered) * p) - 1)] / 1_000_000, 3)
    return {"n": len(values), "meanMs": round(sum(values) / len(values) / 1_000_000, 3),
            "p95Ms": rank(.95), "p99Ms": rank(.99), "maxMs": rank(1)}


def observations(paths, captures):
    ids = {c["captureId"] for c in captures}
    events = [e for path in paths for e in rows(path)]
    selected = [e for e in events if e.get("captureId") in ids]
    successful = [e for e in selected if e["type"] == "SAVE_RESULT" and e["status"] == "SAVED"]
    service = defaultdict(int)
    for event in selected:
        service[event["captureId"]] += event.get("dbServiceNs", 0)
    by_id = {c["captureId"]: c for c in captures}
    comparable = [e for e in successful if e.get("bootSessionId") == by_id[e["captureId"]].get("bootSessionId")
                  and e.get("bootSessionId") is not None]
    manual = [e for e in comparable if by_id[e["captureId"]].get("confirmationPolicy", "MANUAL") == "MANUAL"]
    automatic = [e for e in comparable if by_id[e["captureId"]].get("confirmationPolicy") == "AUTO_AFTER_VALIDATION"]
    latency = [e["committedNs"] - e["pressedAtNs"] for e in manual if 0 < e["pressedAtNs"] <= e["committedNs"]]
    automatic_latency = [e["committedNs"] - by_id[e["captureId"]]["receivedElapsedNs"] for e in automatic
                         if e["committedNs"] >= by_id[e["captureId"]]["receivedElapsedNs"]]
    saved = {e["captureId"]: e for e in successful}
    sounds = [e["elapsedNs"] - saved[e["captureId"]]["committedNs"] for e in selected
              if e["type"] == "SOUND_REQUEST" and e.get("sound") == "SAVED" and e["captureId"] in saved
              and e.get("diagnosticBoot") == saved[e["captureId"]].get("diagnosticBoot")]
    return {"scope": "supplied diagnostic files; not acoustic or physical performance proof",
            "diagnosticSaveCount": len(successful), "durableCaptureCount": len(captures),
            "confirmToCommit": quantiles(latency), "autoReceiveToCommit": quantiles(automatic_latency),
            "crossBootLatencyExcludedCount": len(successful) - len(comparable),
            "confirmationPolicies": dict(Counter(c.get("confirmationPolicy", "MANUAL") for c in captures)),
            "databaseServicePerCapture": quantiles(list(service.values())),
            "commitToSoundCall": quantiles(sounds),
            "broadcastCallback": quantiles([e["callbackDurationNs"] for e in events if e["type"] == "BROADCAST_CALLBACK"]),
            "maxObservedQueue": max((e["queued"] for e in selected if "queued" in e), default=None),
            "peakPssBytesInSuppliedLogs": max((e["pssBytes"] for e in events if e["type"] == "MEMORY"), default=None),
            "diagnosticDropped": max((e.get("diagnosticDropped", 0) for e in events), default=0),
            "alertsInSuppliedLogs": dict(Counter(e["type"] for e in events if e["type"] in
                {"QUEUE_OVERFLOW", "INPUT_REFUSED", "OPERATION_ERROR", "CONFIRM_IGNORED"}))}
