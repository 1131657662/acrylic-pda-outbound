"""Consume actual isolated DB/HTTP evidence; re-sign mutations to exercise semantics beyond crypto."""
import json
import os
from copy import deepcopy
from pathlib import Path
import unittest
from decision_consumer import verify_decision, verify_detail
from decision_forgery import Forgery, replace_path
from decision_signature import verify_signed
from ledger_primitives import canonical, parse

MUTATIONS = (
    ("status", "INTERNAL_TEST_ONLY"), ("businessDecision.kind", "SHORT_APPROVE"),
    ("businessDecision.shortShipmentReason", "替换理由"), ("businessDecision.shortApprovalReceiptId", "wrong"),
    ("businessDecision.shortApprovalSha256", "a" * 64), ("businessDecision.submissionReceipt", None),
    ("businessDecision.products.0.actualCount", "99"), ("businessDecision.products.0.plannedCount", "99"),
    ("businessDecision.products.0.shortCount", "99"), ("businessDecision.products.0.weightGrams", "99"),
    ("businessDecision.products.0.productId", "wrong"), ("businessDecision.products.0.actualCount", 1),
    ("businessDecision.candidates.total", "1"), ("businessDecision.candidates.fingerprint", "a" * 64),
    ("businessDecision.candidates.contextSha256", "a" * 64), ("businessDecision.candidates.fromDate", "2026-01-01"),
    ("businessDecision.candidates.policyVersion", "OTHER"), ("capabilities.localDeletionAllowed", True),
    ("capabilities.releaseAllowed", True), ("actor.principalId", "wrong"),
    ("actor.permissionSnapshot.requiredPermissions", []), ("originalContext.mappingVersion", "2"),
    ("originalContext.warehouseId", "wrong"), ("originalContext.originalOperatorId", "wrong"),
    ("expected.decisionVersion", "99"), ("resultingDecisionVersion", "99"), ("receiptVersion", True),
    ("commandSha256", "a" * 64), ("eventId", "wrong"), ("receiptId", "wrong"),
)


@unittest.skipUnless(os.environ.get("R06_DECISION_VECTORS"), "requires explicit current isolated DB/HTTP vectors")
class RecordedDecisionTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.bundle = json.loads(Path(os.environ["R06_DECISION_VECTORS"]).read_text(encoding="utf-8"))
        cls.forgery = Forgery()
        cls.keys = dict(cls.bundle["keys"], **{cls.forgery.kid: cls.forgery.public})

    def test_actual_normal_short_approve_and_confirm(self):
        for name, vector in self.bundle["cases"].items():
            with self.subTest(name=name):
                result = verify_detail(vector, self.bundle["keys"])
                self.assertEqual(result["status"], "RECORDED")
                self.assertFalse(any(result["capabilities"].values()))
                self.assertEqual(vector["detail"]["originalResponseSha256"], vector["record"]["original_response_sha256"])

    def test_forgery_positive_control_passes_both_layers(self):
        vector = self.bundle["cases"]["normal"]
        changed = self.forgery.response(vector, parse(vector["responseUtf8"]))
        verify_decision(changed, self.keys)

    def test_nested_signed_wrong_success_rejected_even_if_http_matches(self):
        vector = self.bundle["cases"]["normal"]
        for field, value in (("orderId", "wrong"), ("payloadSha256", "a" * 64), ("actualCount", "99"),
                             ("inventoryDeducted", False), ("mayResumeScanning", True), ("receiptVersion", True)):
            with self.subTest(field=field):
                response = parse(vector["responseUtf8"])
                receipt = response["businessDecision"]["submissionReceipt"]
                receipt[field] = value
                signed = self.forgery.sign(receipt, "acrylic-pda-receipt")
                response["businessDecision"]["submissionReceipt"] = signed
                changed = self.forgery.response(vector, response)
                http = parse(changed["successHttpUtf8"]); http["data"] = signed
                changed["successHttpUtf8"] = canonical(http).decode()
                with self.assertRaisesRegex(ValueError, "SUCCESS_FACTS"):
                    verify_decision(changed, self.keys)

    def test_missing_stale_and_revoked_approval_fail(self):
        for path, value in (("approval", None), ("approval.record.id", "wrong"),
                            ("facts.approvalActor.status", "DISABLED"), ("facts.approvalActor.auth_version", "999"),
                            ("facts.approvalActor.permissions", []), ("facts.approvalActor.warehouses", [])):
            with self.subTest(path=path), self.assertRaises(ValueError):
                verify_decision(replace_path(self.bundle["cases"]["shortConfirm"], path, value), self.keys)

    def test_self_approval_fails_even_with_matching_signed_actor_and_facts(self):
        vector = deepcopy(self.bundle["cases"]["shortApprove"])
        response = parse(vector["responseUtf8"])
        operator = response["originalContext"]["originalOperatorId"]
        response["actor"]["principalId"] = operator; vector["facts"]["actor"]["id"] = operator
        changed = self.forgery.response(vector, response)
        with self.assertRaisesRegex(ValueError, "INDEPENDENT_SHORT_APPROVAL"):
            verify_decision(changed, self.keys)

    def test_original_bytes_unknown_key_and_unsigned_domain_fail(self):
        vector = deepcopy(self.bundle["cases"]["normal"])
        with self.assertRaisesRegex(ValueError, "UNKNOWN_KEY"):
            verify_decision(vector, {})
        vector["responseUtf8"] += "\n"
        with self.assertRaisesRegex(ValueError, "ORIGINAL_BYTES_CHANGED"):
            verify_decision(vector, self.keys)
        vector = deepcopy(self.bundle["cases"]["normal"])
        vector["record"]["unsigned_receipt_sha256"] = "a" * 64
        with self.assertRaisesRegex(ValueError, "RECEIPT_DOMAIN"):
            verify_decision(vector, self.keys)

    def test_detail_current_effect_is_not_original_authority(self):
        vector = deepcopy(self.bundle["cases"]["normal"])
        vector["detail"]["currentEffect"] = {"releaseAllowed": True}
        self.assertFalse(verify_detail(vector, self.keys)["capabilities"]["releaseAllowed"])
        vector["detail"]["originalCommandReceiptUtf8"] += "\n"
        with self.assertRaisesRegex(ValueError, "DETAIL_ORIGINAL_CHANGED"):
            verify_detail(vector, self.keys)

    def test_approved_confirmation_references_exact_original_approval(self):
        vector = self.bundle["cases"]["shortConfirm"]
        for field, value in (("shortApprovalReceiptId", None), ("shortApprovalSha256", "b" * 64)):
            response = parse(vector["responseUtf8"]); response["businessDecision"][field] = value
            changed = self.forgery.response(vector, response)
            with self.subTest(field=field), self.assertRaisesRegex(ValueError, "DECISION_FACTS"):
                verify_decision(changed, self.keys)

    def test_other_source_valid_approval_cannot_be_attached(self):
        vector = deepcopy(self.bundle["cases"]["shortConfirm"])
        vector["approval"] = deepcopy(self.bundle["cases"]["otherApprove"])
        with self.assertRaisesRegex(ValueError, "APPROVAL_BODY"):
            verify_decision(vector, self.keys)

    def test_stale_approval_fails_after_matching_command_and_signature_are_rebuilt(self):
        from ledger_primitives import digest
        vector = deepcopy(self.bundle["cases"]["shortConfirm"])
        vector["facts"]["decision"]["decision_version"] = "99"
        command = parse(vector["commandUtf8"]); command["expected"]["decisionVersion"] = "99"
        vector["commandUtf8"] = canonical(command).decode(); vector["record"]["command_sha256"] = digest(command, "command")
        with self.assertRaisesRegex(ValueError, "APPROVAL_STALE"):
            verify_decision(vector, self.keys)


def semantic_mutation(path, value):
    def test(self):
        vector = self.bundle["cases"]["normal"]
        changed = self.forgery.response(vector, replace_path(parse(vector["responseUtf8"]), path, value))
        verify_signed(parse(changed["responseUtf8"]), self.keys, "acrylic-r06-command-receipt-v1")
        with self.assertRaisesRegex(ValueError, "DECISION_FACTS"):
            verify_decision(changed, self.keys)
    return test


for index, (field, replacement) in enumerate(MUTATIONS):
    setattr(RecordedDecisionTests, f"test_signed_mutation_{index:02d}_{field.replace('.', '_')}", semantic_mutation(field, replacement))
