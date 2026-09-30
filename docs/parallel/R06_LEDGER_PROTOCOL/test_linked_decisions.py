"""Real HTTP/raw-DB linked vectors, including valid signatures over semantically false claims."""
import json
import os
from copy import deepcopy
from pathlib import Path
import unittest
from decision_consumer import verify_decision
from decision_forgery import Forgery, replace_path
from decision_signature import verify_signed
from ledger_primitives import canonical, parse
from linked_consumer import verify_linked_chain, verify_linked_decision, verify_linked_detail

MUTATIONS = (
    ("status", "INTERNAL_TEST_ONLY"), ("businessDecision.kind", "SHORT_APPROVE"),
    ("businessDecision.submissionReceipt.targetOrderId", "wrong"),
    ("businessDecision.submissionReceipt.mappingVersion", "3"),
    ("businessDecision.submissionReceipt.mappingId", "wrong"),
    ("businessDecision.submissionReceipt.sourceCanonicalOrderId", "wrong"),
    ("businessDecision.submissionReceipt.targetRowVersion", "99"),
    ("businessDecision.submissionReceipt.applyState", "PLANNED"),
    ("businessDecision.submissionReceipt.candidateContextSha256", "a" * 64),
    ("businessDecision.products.0.actualCount", "99"), ("businessDecision.products.0.actualCount", 1),
    ("businessDecision.candidates.total", "0"), ("businessDecision.candidates.fingerprint", "a" * 64),
    ("businessDecision.candidates.contextSha256", "a" * 64), ("businessDecision.candidates.policyVersion", "OTHER"),
    ("actor.principalId", "wrong"), ("actor.permissionSnapshot.requiredPermissions", []),
    ("actor.deviceId", "wrong"), ("actor.sessionId", "wrong"), ("actor.deviceVersion", "99"),
    ("originalContext.mappingVersion", "2"), ("originalContext.warehouseId", "wrong"),
    ("expected.decisionVersion", "99"), ("resultingDecisionVersion", "99"), ("receiptVersion", True),
    ("commandSha256", "a" * 64), ("eventId", "wrong"), ("receiptId", "wrong"),
    ("capabilities.localDeletionAllowed", True), ("capabilities.releaseAllowed", True),
)


@unittest.skipUnless(os.environ.get("R06_LINKED_VECTORS"), "requires explicit isolated real HTTP/raw DB vectors")
class LinkedDecisionTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.bundle = json.loads(Path(os.environ["R06_LINKED_VECTORS"]).read_text(encoding="utf-8"))
        cls.forgery = Forgery()
        cls.keys = dict(cls.bundle["keys"], **{cls.forgery.kid: cls.forgery.public})

    def test_real_http_chain_accepts_mapping2_with_nonzero_candidates(self):
        result = verify_linked_chain(self.bundle)
        self.assertEqual(result["businessDecision"]["candidates"]["total"], "1")
        self.assertEqual(result["originalContext"]["mappingVersion"], "2")
        self.assertFalse(any(result["capabilities"].values()))

    def test_old_consumer_still_refuses_mapping2(self):
        with self.assertRaisesRegex(ValueError, "UNSUPPORTED_SCOPE"):
            verify_decision(self.bundle["cases"]["confirm"], self.bundle["keys"])

    def test_forgery_positive_control_accepts_both_decisions(self):
        for name, vector in self.bundle["cases"].items():
            with self.subTest(name=name):
                verify_linked_decision(self.forgery.response(vector, parse(vector["responseUtf8"])), self.keys)

    def test_changed_original_unknown_key_and_wrong_domain_fail(self):
        original = self.bundle["cases"]["link"]
        with self.assertRaisesRegex(ValueError, "UNKNOWN_KEY"):
            verify_linked_decision(original, {})
        changed = deepcopy(original)
        changed["responseUtf8"] += "\n"
        with self.assertRaisesRegex(ValueError, "ORIGINAL_BYTES_CHANGED"):
            verify_linked_decision(changed, self.keys)
        changed = replace_path(original, "record.unsigned_receipt_sha256", "a" * 64)
        with self.assertRaisesRegex(ValueError, "RECEIPT_DOMAIN"):
            verify_linked_decision(changed, self.keys)

    def test_session_authority_facts_fail_closed(self):
        for path, value in (("actor.permissions", []), ("actor.warehouses", []), ("actor.status", "DISABLED"),
                            ("pdaSession.user_id", "wrong"), ("pdaSession.warehouse_id", "wrong"),
                            ("pdaSession.device_version", "99"), ("pdaSession.device_status", "DISABLED"),
                            ("pdaSession.revoked_at", "2026-01-01T00:00:00Z"),
                            ("pdaSession.access_expires_at", "2026-01-01T00:00:00Z")):
            with self.subTest(path=path), self.assertRaises(ValueError):
                verify_linked_decision(replace_path(self.bundle["cases"]["link"], "facts." + path, value), self.keys)

    def test_missing_or_foreign_candidates_fail_closed(self):
        vector = self.bundle["cases"]["link"]
        changed = deepcopy(vector)
        changed["facts"]["formalOrders"] = []
        with self.assertRaisesRegex(ValueError, "LINK_CANDIDATE_COUNT"):
            verify_linked_decision(changed, self.keys)
        changed = deepcopy(vector)
        for order in changed["facts"]["formalOrders"]:
            order["warehouse_id"] = "another-warehouse"
        with self.assertRaisesRegex(ValueError, "LINK_CANDIDATE_COUNT"):
            verify_linked_decision(changed, self.keys)

    def test_mapping_origin_and_history_are_independent_requirements(self):
        vector = self.bundle["cases"]["confirm"]
        for path, value in (("facts.mapping.origin", "MIGRATED_BASELINE"),
                            ("facts.mapping.scope_id", "wrong"), ("facts.mapping.warehouse_id", "wrong"),
                            ("facts.scope.mapping_version", "3"), ("facts.scope.work_round", "2")):
            with self.subTest(path=path), self.assertRaises(ValueError):
                verify_linked_decision(replace_path(vector, path, value), self.keys)

    def test_current_effect_cannot_replace_original_authority(self):
        vector = deepcopy(self.bundle["cases"]["confirm"])
        vector["detail"]["currentEffect"] = {"releaseAllowed": True}
        self.assertFalse(verify_linked_detail(vector, self.keys)["capabilities"]["releaseAllowed"])
        vector["detail"]["originalCommandReceiptUtf8"] += "\n"
        with self.assertRaisesRegex(ValueError, "DETAIL_ORIGINAL_CHANGED"):
            verify_linked_detail(vector, self.keys)

    def test_signed_wrong_nested_success_rejected_even_when_http_agrees(self):
        vector = self.bundle["cases"]["confirm"]
        for field, value in (("orderId", "wrong"), ("source", {"kind": "FORMAL"}), ("payloadSha256", "a" * 64),
                             ("actualCount", "99"), ("mayResumeScanning", True), ("workRound", "2")):
            response = parse(vector["responseUtf8"])
            receipt = response["businessDecision"]["submissionReceipt"]
            receipt[field] = value
            signed = self.forgery.sign(receipt, "acrylic-pda-receipt")
            response["businessDecision"]["submissionReceipt"] = signed
            changed = self.forgery.response(vector, response)
            http = parse(changed["successHttpUtf8"])
            http["data"] = signed
            changed["successHttpUtf8"] = canonical(http).decode()
            with self.subTest(field=field), self.assertRaisesRegex(ValueError, "SUCCESS_FACTS"):
                verify_linked_decision(changed, self.keys)

    def test_wrong_accounting_rejected_when_replay_snapshot_agrees(self):
        vector = self.bundle["cases"]["confirm"]
        for path, value in (("results.0.order_id", "wrong"), ("commitments.0.order_id", "wrong"),
                            ("dispatches.0.order_id", "wrong"), ("receiptCount", "3"),
                            ("originalOrder.status", "COMPLETED")):
            changed = replace_path(vector, "after." + path, value)
            changed["afterReplay"] = deepcopy(changed["after"])
            with self.subTest(path=path), self.assertRaises(ValueError):
                verify_linked_decision(changed, self.keys)


def semantic_mutation(path, value):
    def test(self):
        vector = self.bundle["cases"]["link"]
        changed = self.forgery.response(vector, replace_path(parse(vector["responseUtf8"]), path, value))
        verify_signed(parse(changed["responseUtf8"]), self.keys, "acrylic-r06-command-receipt-v1")
        with self.assertRaisesRegex(ValueError, "LINK_DECISION_FACTS"):
            verify_linked_decision(changed, self.keys)
    return test


for index, (path, value) in enumerate(MUTATIONS):
    setattr(LinkedDecisionTests, f"test_signed_mutation_{index:02d}_{path.replace('.', '_')}", semantic_mutation(path, value))
