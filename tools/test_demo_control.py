"""Partial reply reads must never cause an automatic mutation resend."""
import json
import subprocess
import unittest
from unittest.mock import patch
from demo_control import Device


class ReplyTest(unittest.TestCase):
    def test_empty_and_partial_reply_are_retried_without_resending(self):
        request = "11111111-1111-4111-8111-111111111111"
        payloads = [b"", b'{"ok":', json.dumps({"ok": True, "requestId": request, "result": {"saved": 1}}).encode()]
        replies = [subprocess.CompletedProcess([], 0, value, b"") for value in payloads]
        with patch("demo_control.uuid.uuid4", return_value=request), patch.object(Device, "shell") as send:
            with patch("demo_control.subprocess.run", side_effect=replies) as read, patch("demo_control.time.sleep"):
                result = Device().command("INPUT", raw="003X0000001")
        self.assertEqual({"saved": 1}, result)
        self.assertEqual(1, send.call_count)
        self.assertEqual(3, read.call_count)

    def test_mismatched_reply_is_rejected(self):
        payload = json.dumps({"ok": True, "requestId": "wrong", "result": {}}).encode()
        with patch.object(Device, "shell"), patch("demo_control.subprocess.run", return_value=subprocess.CompletedProcess([], 0, payload)):
            with self.assertRaisesRegex(RuntimeError, "requestId mismatch"):
                Device().command("STATUS")


if __name__ == "__main__":
    unittest.main()
