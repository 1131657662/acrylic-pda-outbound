"""Run the fixed production codec and crypto verifier, without a database or Gradle."""
import json
import unittest
from ledger_cases import negative_cases
from ledger_java import LedgerDriver
from ledger_primitives import canonical, digest
from ledger_vectors import load, trusted
from receipt_consumer import verify_response
from receipt_expectations import expected_receipt
from receipt_shapes import receipt_shapes
from trusted_facts import fixtures


class ProductionCodecTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.driver = LedgerDriver()
        cls.addClassCleanup(cls.driver.close)
        cls.driver.compile()
        cls.generated = cls.driver.invoke({"codec": fixtures(), "forged": []})

    def test_crypto_verifier_enforces_purpose_but_does_not_claim_database_semantics(self):
        vectors = load()
        scenarios = {item["id"]: item for item in negative_cases()}
        cases = [{"id": row["id"], "token": row["signedReceipt"], "keyId": row["keyId"],
                  "unsigned": json.loads(row["unsignedReceiptUtf8"])}
                 for row in vectors["codec"] + vectors["forged"]]
        result = self.driver.invoke({"publicKey": vectors["publicKey"], "cases": cases}, "verify")
        for actual in result:
            with self.subTest(case=actual["id"]):
                expected = scenarios.get(actual["id"], {}).get("cryptographicAcceptance", True)
                self.assertEqual(actual["accepted"], expected)

    def test_actual_receipt_shape_accepts_null_mapping_and_rejects_coercions(self):
        scenarios = receipt_shapes()
        actual = self.driver.invoke(scenarios, "shape")
        self.assertEqual(len(actual), len(scenarios))
        for expected, result in zip(scenarios, actual):
            with self.subTest(case=expected["id"]):
                self.assertEqual(result["id"], expected["id"])
                self.assertEqual(result["accepted"], expected["accepted"])


def production_codec(index):
    def test(self):
        row = self.generated["codec"][index]
        facts = fixtures()[index]
        expected = expected_receipt(facts)
        self.assertEqual(row["unsignedReceiptUtf8"].encode(), canonical(expected))
        self.assertEqual(row["unsignedReceiptSha256"], digest(expected, "receipt"))
        actual = verify_response(row["originalResponseUtf8"].encode(),
                                 trusted(row, facts, self.generated["publicKey"]))
        self.assertEqual(actual, expected)
    return test


for fixture_index, sample in enumerate(fixtures()):
    name = "test_actual_codec_" + sample["id"].replace("-", "_")
    setattr(ProductionCodecTests, name, production_codec(fixture_index))
