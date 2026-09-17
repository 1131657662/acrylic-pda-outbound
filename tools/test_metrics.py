"""Auto and manual timing use distinct clocks/origins; restarts must not fake short latency."""
import json
import tempfile
import unittest
from pathlib import Path
from metrics import observations


class MetricsTest(unittest.TestCase):
    def test_auto_uses_reception_and_manual_uses_confirmation(self):
        captures = [{"captureId": "auto", "bootSessionId": "boot", "confirmationPolicy": "AUTO_AFTER_VALIDATION",
                     "receivedElapsedNs": 100_000_000},
                    {"captureId": "manual", "bootSessionId": "boot", "confirmationPolicy": "MANUAL",
                     "receivedElapsedNs": 1}]
        events = [{"captureId": c["captureId"], "type": "SAVE_RESULT", "status": "SAVED", "bootSessionId": "boot",
                   "committedNs": 200_000_000, "pressedAtNs": 180_000_000} for c in captures]
        result = self.measure(captures, events)
        self.assertEqual(100, result["autoReceiveToCommit"]["p95Ms"])
        self.assertEqual(20, result["confirmToCommit"]["p95Ms"])
        self.assertEqual(1, result["confirmToCommit"]["n"])

    def test_cross_boot_is_counted_but_excluded_from_latency(self):
        captures = [{"captureId": "restart", "bootSessionId": "old", "confirmationPolicy": "AUTO_AFTER_VALIDATION",
                     "receivedElapsedNs": 100}]
        events = [{"captureId": "restart", "type": "SAVE_RESULT", "status": "SAVED", "bootSessionId": "new",
                   "committedNs": 200, "pressedAtNs": 180}]
        result = self.measure(captures, events)
        self.assertEqual(1, result["diagnosticSaveCount"])
        self.assertEqual(1, result["crossBootLatencyExcludedCount"])
        self.assertIsNone(result["autoReceiveToCommit"])

    def measure(self, captures, events):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "events.ndjson"
            path.write_text("\n".join(json.dumps(e) for e in events), encoding="utf-8")
            return observations([path], captures)
