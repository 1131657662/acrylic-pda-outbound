"""Mutation checks against explicitly supplied, preserved isolated-run evidence."""
from __future__ import annotations

import base64
import copy
import os
from pathlib import Path
import unittest

from cryptography.exceptions import InvalidSignature
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import rsa

from fault_recovery_accounting import check_business
from fault_recovery_evidence import check_facts, local_file, read_json, table_rows, verify_receipt
from fault_withdrawal_recovery_consumer import check_before, check_http, consume, validate


@unittest.skipUnless(os.environ.get("FAULT_RECOVERY_EVIDENCE_ROOT"), "Explicit preserved isolated evidence required")
class FaultRecoveryConsumerTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = Path(os.environ["FAULT_RECOVERY_EVIDENCE_ROOT"]).resolve()
        cls.final = read_json(cls.source / "final-facts.json")
        cls.scenario = read_json(cls.source / "private/scenario.json")
        cls.key = (cls.source / "private-signing/server-public.der").read_bytes()

    def modified_facts(self, table, column, value):
        facts = copy.deepcopy(self.final)
        snapshot = facts["tables"][table]
        snapshot["rows"][0][snapshot["columns"].index(column)] = base64.b64encode(value).decode("ascii")
        return facts

    def test_preserved_five_checkpoint_evidence_passes(self):
        result = validate(self.source)
        self.assertEqual(5, len(result["checkpoints"]))
        self.assertTrue(result["accountingOnce"])
        self.assertFalse(result["originalIdentityOrCustodyProven"])

    def test_duplicate_ledger_rejects_double_accounting(self):
        facts = copy.deepcopy(self.final)
        facts["tables"]["inventory_ledger"]["rows"].append(facts["tables"]["inventory_ledger"]["rows"][0])
        with self.assertRaisesRegex(ValueError, "business.ledger_count"):
            check_business(facts, self.scenario)

    def test_wrong_ledger_weight_is_rejected(self):
        facts = self.modified_facts("inventory_ledger", "weight_delta_g", b"-1")
        with self.assertRaisesRegex(ValueError, "business.ledger_weight"):
            check_business(facts, self.scenario)

    def test_old_assignment_cannot_revive(self):
        facts = self.modified_facts("order_assignment", "status", b"ACTIVE")
        with self.assertRaisesRegex(ValueError, "business.assignments"):
            check_business(facts, self.scenario)

    def test_changed_capture_sequence_is_rejected(self):
        facts = self.modified_facts("dispatch_record", "order_scan_seq", b"99")
        with self.assertRaisesRegex(ValueError, "business.scan_order"):
            check_business(facts, self.scenario)

    def test_new_device_cannot_inherit_old_captures(self):
        scenario = copy.deepcopy(self.scenario)
        scenario["submission"]["captures"][0]["id"] = scenario["oldSubmission"]["captures"][0]["id"]
        with self.assertRaisesRegex(ValueError, "business.independent_captures"):
            check_business(self.final, scenario)

    def test_replay_must_refuse_old_submission(self):
        http = read_json(self.source / "checkpoints/before-withdrawal/http-result.json")
        http["oldSubmissionStatus"] = 200
        with self.assertRaisesRegex(ValueError, "http.oldSubmissionStatus"):
            check_http(http, self.final, self.scenario)

    def test_replay_must_preserve_accounting(self):
        http = read_json(self.source / "checkpoints/before-withdrawal/http-result.json")
        http["accounting"]["inventory_ledger"]["rows"] = []
        with self.assertRaisesRegex(ValueError, "http.accounting"):
            check_http(http, self.final, self.scenario)

    def test_forged_receipt_body_is_rejected(self):
        receipt = copy.deepcopy(self.scenario["success"])
        receipt["stockReleased"] = True
        with self.assertRaisesRegex(ValueError, "signature.receipt_binding"):
            verify_receipt(receipt, self.key, "acrylic-pda-receipt")

    def test_wrong_public_key_is_rejected(self):
        key = rsa.generate_private_key(public_exponent=65537, key_size=2048).public_key()
        wrong = key.public_bytes(serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo)
        with self.assertRaises(InvalidSignature):
            verify_receipt(self.scenario["success"], wrong, "acrylic-pda-receipt")

    def test_wrong_receipt_audience_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "signature.issuer_audience"):
            verify_receipt(self.scenario["success"], self.key, "unrelated-audience")

    def test_empty_migration_history_is_rejected(self):
        facts = copy.deepcopy(self.final)
        facts["flywayHistory"] = []
        with self.assertRaisesRegex(ValueError, "facts.migration"):
            check_facts(facts)

    def test_invalid_base64_is_rejected(self):
        facts = copy.deepcopy(self.final)
        facts["tables"]["inventory_ledger"]["rows"][0][0] = "???"
        with self.assertRaises(ValueError):
            table_rows(facts, "inventory_ledger")

    def test_schema_missing_empty_table_is_rejected(self):
        facts = copy.deepcopy(self.final)
        facts["schema"].pop(next(iter(facts["schema"])))
        with self.assertRaisesRegex(ValueError, "facts.schema"):
            check_facts(facts)

    def test_before_withdrawal_cannot_be_final_success(self):
        with self.assertRaisesRegex(ValueError, "checkpoint.before_assignments"):
            check_before(self.final, 0)

    def test_path_escape_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "file.outside_source"):
            local_file(self.source, "../outside.json")

    def test_output_cannot_overlap_input(self):
        with self.assertRaisesRegex(ValueError, "output.overlaps_source"):
            consume((self.source, self.source / "unread-bindings.json"), self.source / "forbidden-output")


if __name__ == "__main__":
    unittest.main()
