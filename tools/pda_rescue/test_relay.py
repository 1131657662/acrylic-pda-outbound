"""Durable retry boundaries; actual Web/MySQL consumption is verified separately."""
import json
import os
from pathlib import Path
import tempfile
import unittest
from uuid import uuid4

from .relay_journal import RelayJournal
from .relay_transport import base_url
from .relay import transfer


class RelayRetryTest(unittest.TestCase):
    def setUp(self):
        root = Path(os.environ["PDA_RESCUE_TEST_ROOT"])
        root.mkdir(parents=True, exist_ok=True)
        self.directory = Path(tempfile.mkdtemp(prefix="relay-test-", dir=root))
        self.context = {"server": "http://127.0.0.1:19434", "warehouse": str(uuid4())}

    def test_lost_receive_response_reuses_original_bytes_and_keys_after_restart(self):
        journal = RelayJournal(self.directory, b"original", self.context)
        before = dict(journal.state)
        class LostWire:
            def data(self, path, body, key):
                if body != b"original" or key != before["receiveKey"]:
                    raise AssertionError("Original request meaning changed")
                raise TimeoutError("Deliberately lost response after receiving request")
        with self.assertRaises(TimeoutError):
            transfer(LostWire(), journal, True)
        reopened = RelayJournal(self.directory, b"original", self.context)
        self.assertEqual(before, reopened.state)
        self.assertEqual(b"original", (self.directory / "original.zip").read_bytes())
        self.assertFalse(any(self.directory.glob("*.pda-receipt.json")))

    def test_different_original_or_server_cannot_reuse_journal(self):
        RelayJournal(self.directory, b"original", self.context)
        with self.assertRaises(ValueError):
            RelayJournal(self.directory, b"changed", self.context)
        with self.assertRaises(ValueError):
            RelayJournal(self.directory, b"original", {**self.context, "server": "https://different.example"})

    def test_result_retries_preserve_exact_bytes_and_do_not_overwrite_conflicts(self):
        journal = RelayJournal(self.directory, b"original", self.context)
        receipt = {"relayId": str(uuid4()), "archiveSha256": journal.state["input"]["archiveSha256"]}
        journal.received(receipt)
        result = {**receipt, "resultId": str(uuid4()), "localDeletionAllowed": False, "businessReceipt": {"status": "REVIEW_REQUIRED"}}
        raw = json.dumps(result).encode()
        path = journal.result(raw)
        self.assertEqual(path, RelayJournal(self.directory, b"original", self.context).result(raw))
        with self.assertRaises(ValueError):
            journal.result(json.dumps({**result, "businessReceipt": {"status": "SUCCEEDED"}}).encode())
        self.assertEqual(raw, path.read_bytes())

    def test_non_tls_remote_urls_and_embedded_credentials_are_rejected(self):
        for value in ("http://warehouse.example", "https://user:secret@example.com", "https://example.com/api", "https://example.com?q=1"):
            with self.subTest(value=value), self.assertRaises(ValueError):
                base_url(value)
        self.assertEqual("https://warehouse.example", base_url("https://warehouse.example/"))
        self.assertEqual("http://127.0.0.1:19434", base_url("http://127.0.0.1:19434"))

    def test_progressed_review_gets_one_new_key_and_lost_refresh_retains_it(self):
        journal = RelayJournal(self.directory, b"original", self.context)
        old_key = journal.state["processKey"]
        journal.state["lastResultState"] = "REVIEW_REQUIRED"
        journal.save()
        journal.prepare_process("SUCCEEDED")
        refresh_key = journal.state["processKey"]
        self.assertNotEqual(old_key, refresh_key)
        reopened = RelayJournal(self.directory, b"original", self.context)
        reopened.prepare_process("SUCCEEDED")
        self.assertEqual(refresh_key, reopened.state["processKey"])


if __name__ == "__main__":
    unittest.main()
