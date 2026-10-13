"""Consume Android originals; semantic mutations never fabricate a device signature."""
import copy
import io
import json
import os
from pathlib import Path
import unittest
import zipfile

from .independent import verify as verify_independent
from .integrity import parse
from .verify import verify


class IndependentRescueTest(unittest.TestCase):
    def setUp(self):
        directory = Path(os.environ["INDEPENDENT_RESCUE_VECTOR_DIR"])
        self.plain = (directory / "independent.zip").read_bytes()
        self.trust = parse((directory / "trust.json").read_bytes())
        self.result = verify(self.plain, self.trust)

    def semantic(self, edit):
        result = copy.deepcopy(self.result)
        edit(result["files"], result["facts"])
        verify_independent(result["files"], result["facts"], result["manifest"])

    @staticmethod
    def original(files, name, edit):
        value = parse(files[name])
        edit(value)
        files[name] = json.dumps(value, ensure_ascii=False).encode("utf-8")

    def test_original_signed_device_preserves_void_rescan_and_pending_input(self):
        result = self.result
        self.assertTrue(result["sourceTrusted"])
        self.assertFalse(result["businessReceiptVerified"])
        self.assertTrue(result["facts"]["independent"]["pendingReconciliation"])
        self.assertFalse(result["facts"]["independent"]["serverAuthorizationVerified"])
        self.assertEqual(["VOID", "ACTIVE"], [r["status"] for r in result["facts"]["scans"]])
        self.assertEqual("15501", result["manifest"]["summary"]["weightGrams"])
        self.assertEqual("1", result["manifest"]["summary"]["pending"])

    def test_missing_registration_never_becomes_authoritative(self):
        self.assertFalse(verify(self.plain)["sourceTrusted"])
        bad = copy.deepcopy(self.trust)
        next(iter(bad.values()))["publicKeyDerBase64"] = "AA=="
        with self.assertRaises(ValueError):
            verify(self.plain, bad)

    def test_changed_sku_bytes_fail_original_digest(self):
        def edit(files, facts):
            files["independent-sku.bin"] += b"\n"
        with self.assertRaises(ValueError):
            self.semantic(edit)

    def test_missing_void_original_cannot_turn_rescan_into_two_effective_bales(self):
        def edit(files, facts):
            files["independent-voids.ndjson"] = b""
        with self.assertRaises(ValueError):
            self.semantic(edit)

    def test_void_authorization_must_follow_its_sample_within_bounded_delay(self):
        for timestamp in ("2026-10-13T09:59:59Z", "2026-10-13T10:02:00.001Z"):
            with self.subTest(timestamp=timestamp), self.assertRaises(ValueError):
                self.semantic(lambda files, facts: self.original(files, "independent-voids.ndjson", lambda v: v.update(authorizedAt=timestamp)))

    def test_claimed_credential_cannot_exceed_original_48_hour_limit(self):
        def edit(files, facts):
            self.original(files, "independent-source.json", lambda s: s["credential"].update(expiresAt="2099-01-01T00:00:00Z"))
        with self.assertRaises(ValueError):
            self.semantic(edit)

    def test_operation_credential_cannot_leak_into_public_source(self):
        for field in ("signedCredential", "password", "accessToken", "privateKey", "localSessionId"):
            with self.subTest(field=field), self.assertRaises(ValueError):
                self.semantic(lambda files, facts: self.original(files, "independent-source.json", lambda s: s.update({field: "secret"})))

    def test_scope_timestamp_and_state_mutations_are_rejected(self):
        edits = [lambda f, r: r["captures"][0].update(deviceId="different-device"),
                 lambda f, r: r["captures"][0].update(boot="999"),
                 lambda f, r: r["captures"][0].update(state="SUCCESS"),
                 lambda f, r: r["captures"][1].update(deviceSequence=r["captures"][0]["deviceSequence"]),
                 lambda f, r: r["events"][0].update(sequence="2"),
                 lambda f, r: r["events"][0].update(taskId="different-task"),
                 lambda f, r: r["order"].update(serverAuthorizationVerified=True),
                 lambda f, r: self.original(f, "independent-state.json", lambda s: s.update(state="SUCCESS")),
                 lambda f, r: self.original(f, "independent-state.json", lambda s: s.update(actualCount="2"))]
        for number, edit in enumerate(edits):
            with self.subTest(mutation=number), self.assertRaises(ValueError):
                self.semantic(edit)

    def test_fixed_plan_cannot_be_below_effective_count(self):
        def edit(files, facts):
            facts["lines"][0]["plannedCount"] = "0"
        with self.assertRaises(ValueError):
            self.semantic(edit)

    def test_independent_source_never_accepts_formal_submission_or_receipt(self):
        for name in ("submission.json", "receipt.json", "submission-info.json", "source-evidence.ndjson"):
            with self.subTest(name=name), self.assertRaises(ValueError):
                self.semantic(lambda files, facts: files.update({name: b"{}"}))

    def test_modified_original_cannot_pass_device_signature(self):
        files = dict(self.result["files"])
        files["independent-state.json"] += b" "
        output = io.BytesIO()
        with zipfile.ZipFile(output, "w") as bundle:
            for name, data in files.items():
                bundle.writestr(name, data)
        with self.assertRaises(ValueError):
            verify(output.getvalue(), self.trust)


if __name__ == "__main__":
    unittest.main()
