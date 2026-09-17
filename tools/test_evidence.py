"""Regression cases use a real Room service/export fixture produced by the Android tests."""
import json
import shutil
import tempfile
import unittest
from pathlib import Path
from evidence import digest, verify


class EvidenceTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="m71-verifier-")
        self.root = Path(self.temp.name)
        fixture = Path(__file__).resolve().parents[1] / "demo-artifacts/jvm-evidence"
        shutil.copytree(fixture, self.root, dirs_exist_ok=True)

    def tearDown(self):
        self.temp.cleanup()

    def rewrite(self, name, records):
        path = self.root / name
        path.write_text("\n".join(json.dumps(row) for row in records) + "\n", encoding="utf-8")
        manifest = json.loads((self.root / "manifest.json").read_text(encoding="utf-8"))
        manifest["files"][name] = digest(path)
        (self.root / "manifest.json").write_text(json.dumps(manifest), encoding="utf-8")

    def rows(self, name):
        return [json.loads(line) for line in (self.root / name).read_text(encoding="utf-8").splitlines()]

    def test_real_export_matches_independent_sequence(self):
        self.assertTrue(verify(self.root, self.root / "expected.ndjson")["integrityPassed"])

    def test_no_external_baseline_never_claims_no_loss(self):
        result = verify(self.root)
        self.assertFalse(result["externalSequenceCompared"])
        self.assertFalse(result["hardwarePassed"])

    def test_modified_file_hash_fails(self):
        with (self.root / "scans.ndjson").open("a", encoding="utf-8") as target:
            target.write("\n")
        self.assertFalse(verify(self.root)["integrityPassed"])

    def test_reordered_rows_fail_even_with_updated_digest(self):
        self.rewrite("scans.ndjson", list(reversed(self.rows("scans.ndjson"))))
        self.assertFalse(verify(self.root)["integrityPassed"])

    def test_missing_capture_fails_even_with_updated_digest(self):
        self.rewrite("captures.ndjson", self.rows("captures.ndjson")[1:])
        self.assertFalse(verify(self.root)["integrityPassed"])

    def test_changed_policy_fails_even_with_updated_digest(self):
        scans = self.rows("scans.ndjson")
        scans[0]["confirmationPolicy"] = "AUTO_AFTER_VALIDATION"
        self.rewrite("scans.ndjson", scans)
        self.assertFalse(verify(self.root)["integrityPassed"])

    def test_missing_confirmation_audit_fails_even_with_updated_digest(self):
        self.rewrite("audit.ndjson", [a for a in self.rows("audit.ndjson") if a["type"] != "CONFIRM_REQUESTED"])
        self.assertFalse(verify(self.root)["integrityPassed"])

    def test_auto_policy_requires_matching_origin_and_confirmation_audit(self):
        for name in ("captures.ndjson", "scans.ndjson"):
            records = self.rows(name)
            for row in records:
                row.update(confirmationPolicy="AUTO_AFTER_VALIDATION", inputSource="TEST_INJECTION")
            self.rewrite(name, records)
        self.assertFalse(verify(self.root)["integrityPassed"])
        audit = self.rows("audit.ndjson")
        for row in audit:
            if row["type"] == "CONFIRM_REQUESTED":
                row["reason"] = "AUTO_AFTER_VALIDATION:TEST_INJECTION"
        self.rewrite("audit.ndjson", audit)
        self.assertTrue(verify(self.root)["integrityPassed"])


if __name__ == "__main__":
    unittest.main()
