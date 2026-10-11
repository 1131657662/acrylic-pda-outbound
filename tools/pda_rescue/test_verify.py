"""Cross-runtime vectors are synthetic. Tests never use an original device private key."""
import copy
import io
import json
import os
from pathlib import Path
import unittest
import zipfile

from cryptography.exceptions import InvalidTag

from .cli import publish, RescuePublishError
from .envelope import decrypt
from .facts import verify_facts
from .integrity import archive, parse
from .verify import verify


class RescueVerificationTest(unittest.TestCase):
    def setUp(self):
        self.directory = Path(os.environ["PDA_RESCUE_VECTOR_DIR"])
        self.plain = decrypt(self.directory / "synthetic.pdarescue", "synthetic-rescue-password-only")
        self.trust = parse((self.directory / "synthetic-trust.json").read_bytes())

    def test_android_signed_vector_preserves_original_counts_and_long_sequences(self):
        result = verify(self.plain, self.trust)
        self.assertTrue(result["sourceTrusted"])
        self.assertFalse(result["businessReceiptVerified"])
        self.assertEqual("9007199254740993", result["facts"]["scans"][0]["orderScanSeq"])
        self.assertEqual("003", result["facts"]["scans"][0]["deliveryCode"])
        self.assertEqual("123456", result["manifest"]["summary"]["weightGrams"])
        self.assertEqual("1", result["manifest"]["summary"]["pending"])
        self.assertIn(b"'=", result["files"]["scan-details.tsv"])

    def test_without_registration_trust_only_integrity_is_reported(self):
        self.assertFalse(verify(self.plain)["sourceTrusted"])

    def test_changed_registered_scope_cannot_be_trusted(self):
        for field in ("operatorIds", "warehouseIds"):
            trusted = copy.deepcopy(self.trust)
            trusted["synthetic-device"][field] = ["different-scope"]
            with self.assertRaises(ValueError):
                verify(self.plain, trusted)

    def test_device_key_substitution_is_rejected(self):
        trusted = copy.deepcopy(self.trust)
        trusted["synthetic-device"]["publicKeyDerBase64"] = "AA=="
        with self.assertRaises(ValueError):
            verify(self.plain, trusted)

    def test_corrupt_ciphertext_never_returns_plaintext(self):
        with self.assertRaises(InvalidTag):
            decrypt(self.directory / "synthetic.pdarescue", "incorrect-password-for-test")

    def test_duplicate_zip_names_and_path_entries_are_rejected(self):
        original = archive(self.plain)
        for name in ("manifest.json", "../secret"):
            output = io.BytesIO()
            with zipfile.ZipFile(output, "w") as bundle:
                for entry, data in original.items():
                    bundle.writestr(entry, data)
                bundle.writestr(name, b"invalid")
            with self.assertRaises(ValueError):
                verify(output.getvalue(), self.trust)

    def test_changed_raw_file_cannot_pass_original_manifest(self):
        original = archive(self.plain)
        original["scans.ndjson"] += b"{}\n"
        output = io.BytesIO()
        with zipfile.ZipFile(output, "w") as bundle:
            for name, data in original.items():
                bundle.writestr(name, data)
        with self.assertRaises(ValueError):
            verify(output.getvalue(), self.trust)

    def test_resigned_counts_or_human_table_still_need_semantic_consistency(self):
        result = verify(self.plain, self.trust)
        manifest = copy.deepcopy(result["manifest"])
        manifest["summary"]["active"] = "2"
        with self.assertRaises(ValueError):
            verify_facts(result["files"], manifest)
        files = dict(result["files"])
        files["product-summary.tsv"] = b"wrong\n"
        with self.assertRaises(ValueError):
            verify_facts(files, result["manifest"])

    def test_duplicate_json_properties_are_rejected(self):
        with self.assertRaises(ValueError):
            parse(b'{"id":"one","id":"two"}')

    def test_publish_never_overwrites_existing_original_directory(self):
        result = verify(self.plain, self.trust)
        destination = self.directory / "verified-pc-copy"
        if not destination.exists():
            publish(result, destination)
        before = (destination / "manifest.json").read_bytes()
        with self.assertRaises(ValueError):
            publish(result, destination)
        self.assertEqual(before, (destination / "manifest.json").read_bytes())

    def test_failed_conversion_keeps_partial_evidence_without_publishing_complete_copy(self):
        result = verify(self.plain, self.trust)
        destination = self.directory / "must-not-be-complete"
        def fail(directory):
            (directory / "synthetic-failure.log").write_text("synthetic conversion failure")
            raise OSError("synthetic write failure")
        with self.assertRaises(RescuePublishError):
            publish(result, destination, fail)
        self.assertFalse(destination.exists())
        self.assertTrue(any(path.exists() for path in self.directory.glob("pda-rescue-partial-*/synthetic-failure.log")))

    def test_frozen_android_original_is_independently_verified_byte_for_byte(self):
        result = verify((self.directory / "synthetic-frozen.zip").read_bytes())
        self.assertEqual(b'{ "submissionId" : "synthetic-batch" }\n', result["files"]["submission.json"])
        self.assertFalse(result["businessReceiptVerified"])
        files = dict(result["files"])
        files["submission.json"] += b" "
        with self.assertRaises(ValueError):
            verify_facts(files, result["manifest"])

    def test_empty_android_order_is_exportable_without_fake_submission(self):
        result = verify((self.directory / "synthetic-empty.zip").read_bytes())
        self.assertEqual("0", result["manifest"]["summary"]["active"])
        self.assertEqual([], result["facts"]["captures"])
        self.assertNotIn("submission.json", result["files"])

    def test_expanded_zip_limit_is_checked_before_reading_entries(self):
        from .envelope import MAX_BYTES
        output = io.BytesIO()
        with zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED) as bundle:
            for name, data in archive(self.plain).items():
                bundle.writestr(name, data)
            bundle.writestr("source-evidence.ndjson", b" " * (MAX_BYTES + 1))
        with self.assertRaisesRegex(ValueError, "Expanded archive too large"):
            archive(output.getvalue())

    def test_original_active_disposition_does_not_contribute_to_effective_totals(self):
        result = verify((self.directory / "synthetic-disposed.zip").read_bytes())
        self.assertEqual("ACTIVE", result["facts"]["scans"][0]["status"])
        self.assertEqual("0", result["manifest"]["summary"]["active"])
        self.assertEqual("1", result["manifest"]["summary"]["disposed"])
        self.assertEqual("0", result["manifest"]["summary"]["weightGrams"])
        self.assertIn(b"QUARANTINE", result["files"]["scan-details.tsv"])


if __name__ == "__main__":
    unittest.main()
