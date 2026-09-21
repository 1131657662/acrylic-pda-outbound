"""Real OfflineSigner RSA outputs, synthetic future receipt binding expectations."""
import unittest
from canonical import canonical, digest, parse, raw_digest
from fixtures import load
from scenarios import evidence_command
from signatures import verify_response, verify_token
from signing_cases import receipt_fixture


def trusted(row):
    public = load("signed_vectors.json")["publicKey"]
    return {"responseHash": row["originalResponseSha256"], "keys": {public["keyId"]: public},
            "receipt": receipt_fixture(), "command": evidence_command("PHYSICAL_DISPOSITION")}


def first():
    return load("signed_vectors.json")["vectors"][0]


class SyntheticSignatures(unittest.TestCase):
    def test_valid_synthetic_receipt_matches_original_bytes(self):
        row = first()
        raw = row["originalResponseUtf8"].encode()
        self.assertEqual(receipt_fixture(), verify_response(raw, trusted(row)))
        self.assertNotEqual(raw, canonical(parse(raw)))

    def test_unsigned_receipt_uses_new_production_domain(self):
        self.assertEqual(digest(receipt_fixture(), "receipt"), first()["unsignedReceiptSha256"])

    def test_unknown_kid_never_uses_embedded_or_fallback_key(self):
        row = first()
        context = trusted(row)
        context["keys"] = {}
        with self.assertRaisesRegex(ValueError, "UNKNOWN_KEY"):
            verify_response(row["originalResponseUtf8"].encode(), context)

    def test_noncanonical_base64url_is_rejected(self):
        token = parse(first()["originalResponseUtf8"])["signedReceipt"]
        parts = token.split(".")
        parts[0] += "="
        with self.assertRaisesRegex(ValueError, "TOKEN_ENCODING"):
            verify_token(".".join(parts), trusted(first())["keys"])

    def test_signature_damage_is_rejected(self):
        token = parse(first()["originalResponseUtf8"])["signedReceipt"]
        parts = token.split(".")
        parts[2] = ("B" if parts[2][0] == "A" else "A") + parts[2][1:]
        with self.assertRaisesRegex(ValueError, "SIGNATURE_INVALID"):
            verify_token(".".join(parts), trusted(first())["keys"])

    def test_outer_context_change_fails_even_if_transport_hash_matches(self):
        row = first()
        outer = parse(row["originalResponseUtf8"])
        outer["actor"]["principalId"] = "00000000-0000-4000-8000-000000000099"
        raw = canonical(outer)
        context = trusted(row)
        context["responseHash"] = raw_digest(raw)
        with self.assertRaisesRegex(ValueError, "SIGNED_CONTENT_CHANGED"):
            verify_response(raw, context)

    def test_response_whitespace_change_is_detected(self):
        row = first()
        with self.assertRaisesRegex(ValueError, "ORIGINAL_BYTES_CHANGED"):
            verify_response(row["originalResponseUtf8"].encode() + b"\n", trusted(row))

    def test_same_command_id_is_actor_scoped_and_receipt_id_is_separate(self):
        row = next(row for row in load("signed_vectors.json")["vectors"]
                   if row["id"] == "second-actor-same-command")
        context = trusted(row)
        context["receipt"]["receiptId"] = "00000000-0000-4000-8000-000000000090"
        context["receipt"]["actor"]["principalId"] = "00000000-0000-4000-8000-000000000091"
        result = verify_response(row["originalResponseUtf8"].encode(), context)
        self.assertEqual(receipt_fixture()["commandId"], result["commandId"])
        self.assertNotEqual(result["receiptId"], result["commandId"])

    def test_nullable_original_context_can_verify_when_trusted_context_is_nullable(self):
        row = next(row for row in load("signed_vectors.json")["vectors"]
                   if row["id"] == "nullable-synthetic")
        context = trusted(row)
        context["command"]["expected"].update(canonicalOrderId=None, mappingVersion="0",
                                              slotVersion=None, targetRevision=None,
                                              receivedPayloadSha256=None)
        context["receipt"]["originalContext"] = context["command"]
        context["receipt"]["commandSha256"] = digest(context["command"], "command")
        result = verify_response(row["originalResponseUtf8"].encode(), context)
        self.assertIsNone(result["originalContext"]["expected"]["canonicalOrderId"])

    def test_current_effect_cannot_be_added_to_original_signed_receipt(self):
        row = first()
        outer = parse(row["originalResponseUtf8"])
        outer["currentEffect"] = {"localDeletionAllowed": False}
        raw = canonical(outer)
        context = trusted(row)
        context["responseHash"] = raw_digest(raw)
        with self.assertRaisesRegex(ValueError, "SIGNED_CONTENT_CHANGED"):
            verify_response(raw, context)


def mutated_context(row):
    def test(self):
        code = "SIGNATURE_PURPOSE" if row["id"] in {
            "wrong-audience", "wrong-issuer", "proposal-audience"} else "ORIGINAL_CONTEXT_CHANGED"
        with self.assertRaisesRegex(ValueError, code):
            verify_response(row["originalResponseUtf8"].encode(), trusted(row))
    return test


for row in load("signed_vectors.json")["vectors"][1:]:
    setattr(SyntheticSignatures, "test_signed_" + row["id"], mutated_context(row))
