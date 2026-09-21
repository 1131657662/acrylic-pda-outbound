"""The response bytes remain original while the query's currentEffect may change."""
from copy import deepcopy
import unittest
from ledger_primitives import canonical, digest, parse, raw_digest
from ledger_vectors import positive
from receipt_consumer import verify_detail, verify_response


class OriginalBytesTests(unittest.TestCase):
    def test_extra_response_whitespace_is_not_original(self):
        row, context = positive()
        with self.assertRaisesRegex(ValueError, "ORIGINAL_BYTES_CHANGED"):
            verify_response((row["originalResponseUtf8"] + "\n").encode(), context)

    def test_wrong_unsigned_domain_is_rejected(self):
        row, context = positive()
        context["unsignedReceiptSha256"] = digest(parse(row["unsignedReceiptUtf8"]), "control")
        with self.assertRaisesRegex(ValueError, "RECEIPT_DOMAIN_CHANGED"):
            verify_response(row["originalResponseUtf8"].encode(), context)

    def test_unknown_historical_kid_does_not_use_fallback(self):
        row, context = positive()
        context["keys"] = {}
        with self.assertRaisesRegex(ValueError, "UNKNOWN_KEY"):
            verify_response(row["originalResponseUtf8"].encode(), context)

    def test_damaged_signature_fails_with_recomputed_blob_hash(self):
        row, context = positive()
        outer = parse(row["originalResponseUtf8"])
        pieces = outer["signedReceipt"].split(".")
        pieces[2] = ("A" if pieces[2][0] != "A" else "B") + pieces[2][1:]
        outer["signedReceipt"] = ".".join(pieces)
        raw = canonical(outer)
        context["originalResponseSha256"] = raw_digest(raw)
        with self.assertRaisesRegex(ValueError, "SIGNATURE_INVALID"):
            verify_response(raw, context)

    def test_damaged_utf8_blob_fails_even_with_matching_raw_hash(self):
        row, context = positive()
        raw = row["originalResponseUtf8"].encode() + b"\xff"
        context["originalResponseSha256"] = raw_digest(raw)
        with self.assertRaises(UnicodeDecodeError):
            verify_response(raw, context)

    def test_current_effect_changes_do_not_change_original_response(self):
        row, context = positive()
        detail = {"originalCommandReceiptUtf8": row["originalResponseUtf8"],
                  "originalResponseSha256": row["originalResponseSha256"],
                  "currentEffect": {"reasons": ["OPERATIONS_NOT_IMPLEMENTED"]}}
        original = verify_detail(detail, context)
        changed = deepcopy(detail)
        changed["currentEffect"] = {"reasons": ["MAPPING_CHANGED", "SESSION_CHANGED", "OPERATIONS_NOT_IMPLEMENTED"]}
        self.assertEqual(original, verify_detail(changed, context))
        self.assertEqual(detail["originalCommandReceiptUtf8"], changed["originalCommandReceiptUtf8"])

    def test_detail_hash_must_match_original_reference(self):
        row, context = positive()
        detail = {"originalCommandReceiptUtf8": row["originalResponseUtf8"],
                  "originalResponseSha256": "f" * 64, "currentEffect": {}}
        with self.assertRaisesRegex(ValueError, "DETAIL_HASH_CHANGED"):
            verify_detail(detail, context)
