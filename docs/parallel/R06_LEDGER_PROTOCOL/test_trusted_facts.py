"""Valid signatures must not override fixed trusted facts or the actual receipt shape."""
import unittest
from ledger_cases import negative_cases
from ledger_vectors import load, trusted
from receipt_consumer import verify_response


class TrustedFactsTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.vectors = load()
        cls.rows = {row["id"]: row for row in cls.vectors["forged"]}


def rejection(scenario):
    def test(self):
        row = self.rows[scenario["id"]]
        context = trusted(row, scenario["facts"], self.vectors["publicKey"])
        with self.assertRaisesRegex(ValueError, "^" + scenario["expectedRejection"] + "$"):
            verify_response(row["originalResponseUtf8"].encode(), context)
    return test


for scenario in negative_cases():
    name = "test_reject_" + scenario["id"].replace("-", "_")
    setattr(TrustedFactsTests, name, rejection(scenario))
