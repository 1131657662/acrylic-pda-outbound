"""Signed archive faults use synthetic RSA keys and no business data."""
import base64
import copy
import unittest
from cryptography.hazmat.primitives.asymmetric import rsa, padding
from cryptography.hazmat.primitives import hashes
from backup_factory import test_directory
from archive_proofs import canonical, parse, verify_proof
from jobs_monitor import evaluate, FIELDS, LIMITS
from safety import OpsError


class ArchiveProofTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.private = rsa.generate_private_key(public_exponent=65537, key_size=2048)

    def proof(self, **changes):
        value = {'archiveVersion': 1, 'localDeletionAllowed': False, 'storagePolicy': 'SERVER_DURABLE_FILES_V1',
                 'archivedAt': '2024-02-29T00:00:00Z', 'retainUntil': '2029-02-28T00:00:00Z', **changes}
        encode = lambda item: base64.urlsafe_b64encode(canonical(item).encode()).rstrip(b'=').decode()
        head = encode({'alg': 'RS256', 'typ': 'JWT', 'kid': 'synthetic'})
        body = encode({'iss': 'acrylic-outbound', 'aud': 'acrylic-pda-archive', 'archive': value})
        signature = self.private.sign((head + '.' + body).encode(), padding.PKCS1v15(), hashes.SHA256())
        value['signedArchive'] = head + '.' + body + '.' + base64.urlsafe_b64encode(signature).rstrip(b'=').decode()
        return value

    def verify(self, value, audience='acrylic-pda-archive'):
        verify_proof(value, (self.private.public_key(), 'synthetic'), audience)

    def test_valid_leap_day_retention_and_original_signature(self):
        self.verify(self.proof())

    def test_unsigned_change_wrong_audience_and_corrupt_signature(self):
        value = self.proof()
        value['retainUntil'] = '2030-02-28T00:00:00Z'
        with self.assertRaisesRegex(OpsError, 'ARCHIVE_BODY_MISMATCH'):
            self.verify(value)
        with self.assertRaisesRegex(OpsError, 'ARCHIVE_CONTEXT_MISMATCH'):
            self.verify(self.proof(), 'acrylic-pda-audit-archive')
        value = self.proof()
        segments = value['signedArchive'].split('.')
        segments[2] = ('A' if segments[2][0] != 'A' else 'B') + segments[2][1:]
        value['signedArchive'] = '.'.join(segments)
        with self.assertRaisesRegex(OpsError, 'ARCHIVE_SIGNATURE_INVALID'):
            self.verify(value)

    def test_signed_cleanup_and_short_retention_still_rejected(self):
        for changes, code in (({'localDeletionAllowed': True}, 'ARCHIVE_DELETION_NOT_ALLOWED'),
                              ({'retainUntil': '2029-02-27T23:59:59Z'}, 'ARCHIVE_RETENTION_TOO_SHORT')):
            with self.subTest(changes=changes), self.assertRaisesRegex(OpsError, code):
                self.verify(self.proof(**changes))

    def test_canonical_json_matches_jackson_control_and_unicode_rules(self):
        self.assertEqual(canonical({'b': '\x1f', 'a': '中文\n\\u000a'}), '{"a":"中文\\n\\\\u000a","b":"\\u001F"}')
        self.assertEqual(canonical({'\ue000': 1, '\U00010000': 2}), '{"\U00010000":2,"\ue000":1}')
        with self.assertRaisesRegex(OpsError, 'ARCHIVE_DUPLICATE_JSON_KEY'):
            parse('{"a":1,"a":2}')
        with self.assertRaisesRegex(OpsError, 'ARCHIVE_UNSUPPORTED_JSON_VALUE'):
            canonical({'value': 1.5})

    def test_task_limits_fail_each_metric_and_allow_exact_threshold(self):
        values = dict.fromkeys(FIELDS, 0)
        self.assertEqual(evaluate(values, LIMITS)['status'], 'PASS')
        for name, limit in LIMITS.items():
            with self.subTest(name=name):
                boundary = {**values, name: limit}
                self.assertEqual(evaluate(boundary, LIMITS)['status'], 'PASS')
                self.assertEqual(evaluate({**boundary, name: limit + 1}, LIMITS)['exceeded'], [name])
