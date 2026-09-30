"""Consume actual formal-order HTTP/raw-DB vectors and reject altered query claims."""
import json
import os
from copy import deepcopy
from pathlib import Path
import unittest
from reverse_candidates import verify_reverse_candidates


@unittest.skipUnless(os.environ.get("R06_REVERSE_VECTORS"), "requires actual isolated HTTP/raw-DB vectors")
class ReverseCandidateTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.vector = json.loads(Path(os.environ["R06_REVERSE_VECTORS"]).read_text(encoding="utf-8"))

    def test_successful_temporary_source_is_still_an_advisory_candidate(self):
        result = verify_reverse_candidates(self.vector)
        self.assertGreater(result["candidates"]["total"], 0)
        self.assertEqual(result["candidates"]["items"][0]["status"], "SUCCEEDED")

    def test_raw_relation_order_does_not_change_result(self):
        changed = deepcopy(self.vector)
        for name in changed["facts"]:
            changed["facts"][name].reverse()
            changed["stateBefore"][name].reverse()
            changed["stateAfter"][name].reverse()
        verify_reverse_candidates(changed)

    def test_missing_read_permission_is_rejected(self):
        changed = deepcopy(self.vector)
        changed["actor"]["permissions"].remove("order:read")
        with self.assertRaisesRegex(ValueError, "REVERSE_ACTOR_AUTHORITY"):
            verify_reverse_candidates(changed)

    def test_business_mutation_is_rejected(self):
        changed = deepcopy(self.vector)
        changed["stateAfter"]["results"].clear()
        with self.assertRaisesRegex(ValueError, "REVERSE_QUERY_MUTATED_BUSINESS"):
            verify_reverse_candidates(changed)

    def test_bad_original_payload_hash_is_rejected(self):
        changed = deepcopy(self.vector)
        target = json.loads(changed["payloadUtf8"])["submissionId"]
        for key in ("facts", "stateBefore", "stateAfter"):
            for row in changed[key]["payloads"]:
                if row["submission_id"] == target:
                    row["payload_sha256"] = "0" * 64
        with self.assertRaisesRegex(ValueError, "REVERSE_PAYLOAD_FACTS"):
            verify_reverse_candidates(changed)


MUTATIONS = (("releaseAllowed", True), ("advisoryOnly", False), ("hasFormalWork", True),
             ("orderVersion", "99"), ("revision", 99), ("candidates.total", 0),
             ("candidates.pageSize", 200), ("candidates.items", []),
             ("candidates.items.0.status", "REVIEW_REQUIRED"),
             ("candidates.items.0.orderNo", "fabricated"),
             ("candidates.items.0.actualCount", "99"),
             ("candidates.items.0.sameVehicle", False),
             ("range.fromDate", "2000-01-01"))


def altered_response(path, value):
    def check(self):
        changed = deepcopy(self.vector)
        node = changed["response"]
        keys = path.split(".")
        for key in keys[:-1]:
            node = node[int(key)] if isinstance(node, list) else node[key]
        node[keys[-1]] = deepcopy(value)
        with self.assertRaisesRegex(ValueError, "REVERSE_RESPONSE_FACTS"):
            verify_reverse_candidates(changed)
    return check


for index, (field, replacement) in enumerate(MUTATIONS):
    setattr(ReverseCandidateTests, f"test_reject_altered_response_{index:02d}_{field.replace('.', '_')}", altered_response(field, replacement))


if __name__ == "__main__":
    unittest.main()
