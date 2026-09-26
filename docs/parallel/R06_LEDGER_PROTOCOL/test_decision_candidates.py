"""Independent consumption of nonempty real HTTP pages and nonzero signed short approvals."""
import json
import os
from copy import deepcopy
from pathlib import Path
import unittest
from candidate_consumer import verify_pages
from decision_consumer import verify_decision
from decision_forgery import Forgery
from ledger_primitives import canonical, digest, parse


@unittest.skipUnless(os.environ.get("R06_DECISION_VECTORS"), "requires current isolated real DB/HTTP vectors")
class CandidateConsumerTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.bundle = json.loads(Path(os.environ["R06_DECISION_VECTORS"]).read_text(encoding="utf-8"))
        cls.forgery = Forgery()
        cls.keys = dict(cls.bundle["keys"], **{cls.forgery.kid: cls.forgery.public})

    def test_real_pages_contain_entire_nonzero_set(self):
        rows = verify_pages(self.bundle["candidateRead"])
        self.assertEqual(len(rows), 21)
        self.assertTrue(any(row["order"]["hasPcWork"] for row in rows))
        self.assertTrue(any(row["order"]["status"] == "READY" for row in rows))

    def test_page_omission_duplication_wrong_order_or_new_capability_rejected(self):
        for kind in ("omit", "duplicate", "reverse", "release", "link", "fingerprint", "context", "hash", "cursor"):
            vector = deepcopy(self.bundle["candidateRead"]); page = vector["pages"][0]
            if kind == "omit": page["items"].pop()
            if kind == "duplicate": page["items"].append(page["items"][0])
            if kind == "reverse": page["items"].reverse()
            if kind == "release": page["releaseAllowed"] = True
            if kind == "link": page["items"][0]["linkAvailable"] = True
            if kind == "fingerprint": page["candidateSetSha256"] = "a" * 64
            if kind == "context": page["context"]["canonicalOrderId"] = "foreign"
            if kind == "hash": page["contextSha256"] = "a" * 64
            if kind == "cursor": page["nextCursor"] = vector["pages"][1]["nextCursor"]
            with self.subTest(kind=kind), self.assertRaises(ValueError):
                verify_pages(vector)

    def test_short_approval_binds_nonzero_candidate_facts(self):
        result = verify_decision(self.bundle["cases"]["nonzeroApprove"], self.bundle["keys"])
        self.assertEqual(result["businessDecision"]["candidates"]["total"], "21")
        self.assertIsNone(result["businessDecision"]["submissionReceipt"])

    def test_signed_nonzero_fingerprint_change_rejected(self):
        vector = self.bundle["cases"]["nonzeroApprove"]
        response = parse(vector["responseUtf8"])
        response["businessDecision"]["candidates"]["fingerprint"] = "a" * 64
        with self.assertRaisesRegex(ValueError, "DECISION_FACTS"):
            verify_decision(self.forgery.response(vector, response), self.keys)

    def test_nonzero_short_approval_cannot_be_relabelled_confirmation(self):
        vector = deepcopy(self.bundle["cases"]["nonzeroApprove"])
        command = parse(vector["commandUtf8"]); command["commandType"] = "NORMAL_CONFIRM"
        vector["commandUtf8"] = canonical(command).decode(); vector["record"]["command_sha256"] = digest(command, "command")
        response = parse(vector["responseUtf8"]); response["commandType"] = "NORMAL_CONFIRM"
        response["businessDecision"]["kind"] = "NORMAL_CONFIRM"
        with self.assertRaisesRegex(ValueError, "NONZERO_CANDIDATES_UNRESOLVED"):
            verify_decision(self.forgery.response(vector, response), self.keys)
