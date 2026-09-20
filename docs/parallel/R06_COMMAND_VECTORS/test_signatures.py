"""Verify actual Java signatures independently, then bind to the saved request."""
import base64
import unittest
from bindings import check_receipt_binding
from fixtures import command, context, load
from signatures import verify_outer


def signed_sample():
    data = load('signed_vectors.json')
    key = data['publicKey']
    return data['vectors'][0]['originalCommandReceipt'], {key['keyId']: key}


class SignedContextVectors(unittest.TestCase):
    pass


def signature_case(index):
    def test(self):
        data = load('signed_vectors.json')
        vector = data['vectors'][index]
        key = data['publicKey']
        def check():
            receipt = verify_outer(vector['originalCommandReceipt'], {key['keyId']: key})
            return check_receipt_binding(receipt, command(), context())
        if vector['expectedError'] is None:
            self.assertEqual(load('receipt_vectors.json')['originalCommandReceipt'], check())
        else:
            with self.assertRaisesRegex(ValueError, '^' + vector['expectedError'] + '$'):
                check()
    return test


for index, vector in enumerate(load('signed_vectors.json')['vectors']):
    setattr(SignedContextVectors, 'test_' + vector['id'].replace('-', '_'), signature_case(index))


class MalformedProofs(unittest.TestCase):
    def test_unknown_key_has_no_embedded_key_fallback(self):
        outer, _ = signed_sample()
        with self.assertRaisesRegex(ValueError, 'UNKNOWN_KEY'):
            verify_outer(outer, {})

    def test_outer_payload_hash_cannot_replace_signed_value(self):
        outer, keyring = signed_sample()
        outer['originalContext']['expected']['receivedPayloadSha256'] = 'd' * 64
        with self.assertRaisesRegex(ValueError, 'SIGNED_CONTENT_CHANGED'):
            verify_outer(outer, keyring)

    def test_current_effect_cannot_be_inserted_into_original_receipt(self):
        outer, keyring = signed_sample()
        outer['currentEffect'] = load('receipt_vectors.json')['currentEffects'][0]
        with self.assertRaisesRegex(ValueError, 'SIGNED_CONTENT_CHANGED'):
            verify_outer(outer, keyring)

    def test_corrupted_signature_never_verifies(self):
        outer, keyring = signed_sample()
        parts = outer['signedReceipt'].split('.')
        signature = bytearray(base64.urlsafe_b64decode(parts[2] + '=='))
        signature[0] ^= 1
        parts[2] = base64.urlsafe_b64encode(signature).rstrip(b'=').decode('ascii')
        outer['signedReceipt'] = '.'.join(parts)
        with self.assertRaisesRegex(ValueError, 'SIGNATURE_INVALID'):
            verify_outer(outer, keyring)

    def test_semantically_same_claim_bytes_do_not_reuse_signature(self):
        outer, keyring = signed_sample()
        parts = outer['signedReceipt'].split('.')
        payload = base64.urlsafe_b64decode(parts[1] + '=' * (-len(parts[1]) % 4))
        parts[1] = base64.urlsafe_b64encode(b' ' + payload).rstrip(b'=').decode('ascii')
        outer['signedReceipt'] = '.'.join(parts)
        with self.assertRaisesRegex(ValueError, 'SIGNATURE_INVALID'):
            verify_outer(outer, keyring)

    def test_padding_is_not_canonical_base64url(self):
        outer, keyring = signed_sample()
        outer['signedReceipt'] += '='
        with self.assertRaisesRegex(ValueError, 'TOKEN_ENCODING'):
            verify_outer(outer, keyring)
