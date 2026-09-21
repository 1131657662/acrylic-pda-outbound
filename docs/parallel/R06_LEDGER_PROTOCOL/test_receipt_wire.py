"""Actual frozen C1b wire acceptance against independent source facts and Node bytes."""
import unittest
from ledger_primitives import canonical, digest
from ledger_vectors import load, positive
from receipt_consumer import verify_response
from receipt_expectations import expected_receipt
from trusted_facts import fixtures


class ReceiptWireTests(unittest.TestCase):
    pass


def acceptance(index):
    def test(self):
        row, context = positive(index)
        actual = verify_response(row["originalResponseUtf8"].encode(), context)
        self.assertEqual(actual, expected_receipt(fixtures()[index]))
        self.assertNotEqual(actual["actor"]["principalId"], actual["originalContext"]["originalOperatorId"])
    return test


def bytes_and_domain(index):
    def test(self):
        row, _ = positive(index)
        expected = load("wire_vectors.json")[index]
        unsigned = expected_receipt(fixtures()[index])
        self.assertEqual(row["unsignedReceiptUtf8"], expected["unsignedReceiptUtf8"])
        self.assertEqual(canonical(unsigned), expected["unsignedReceiptUtf8"].encode())
        self.assertEqual(row["unsignedReceiptSha256"], expected["unsignedReceiptSha256"])
        self.assertEqual(digest(unsigned, "receipt"), expected["unsignedReceiptSha256"])
    return test


for fixture_index, sample in enumerate(fixtures()):
    suffix = sample["id"].replace("-", "_")
    setattr(ReceiptWireTests, "test_actual_receipt_" + suffix, acceptance(fixture_index))
    setattr(ReceiptWireTests, "test_independent_bytes_" + suffix, bytes_and_domain(fixture_index))
