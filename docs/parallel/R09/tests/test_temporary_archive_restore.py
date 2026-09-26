"""Faults run only against this task's restored isolated schema/files; every mutation is restored."""
import base64
import json
import os
from pathlib import Path
import unittest
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding
from backup_factory import test_directory  # Adds the existing ops directory to sys.path.
from archive_proofs import canonical, parse
from command_archive_rows import read_record
from command_archive_proofs import command_proof
from safety import OpsError
from temporary_archive_probe import ArchiveProbeDatabase


@unittest.skipUnless(os.environ.get('R09_TEMP_ARCHIVE_ATTEMPT'), 'requires dedicated restored archive attempt')
class TemporaryArchiveRestoreTests(unittest.TestCase):
    def setUp(self):
        self.db = ArchiveProbeDatabase(os.environ['R09_TEMP_ARCHIVE_ATTEMPT'], 'restore')
        self.addCleanup(self.db.close)
        self.proofs = json.loads((self.db.attempt / 'archive-proofs.json').read_text(encoding='utf-8'))
        self.proof = self.proofs[1]
        self.id = self.proof['submissionId']

    def update(self, sql, params):
        with self.db.connection.cursor() as cursor:
            cursor.execute(sql, params)
            self.assertEqual(cursor.rowcount, 1)

    def transaction_fault(self, sql, params):
        self.db.connection.begin()
        try:
            self.update(sql, params)
            with self.assertRaises((OpsError, ValueError, KeyError)):
                self.db.inspect()
        finally:
            self.db.connection.rollback()
        self.assertEqual(self.db.inspect()['temporary_archive_commands_verified'], 3)

    def sign_archive(self, proof):
        private = serialization.load_der_private_key((self.db.attempt / 'restored/config/server-private.der').read_bytes(), password=None)
        unsigned = {k: v for k, v in proof.items() if k != 'signedArchive'}
        encode = lambda item: base64.urlsafe_b64encode(canonical(item).encode()).rstrip(b'=').decode()
        header = encode({'alg': 'RS256', 'typ': 'JWT', 'kid': self.db.keys[1]})
        body = encode({'iss': 'acrylic-outbound', 'aud': 'acrylic-pda-archive', 'archive': unsigned})
        token = header + '.' + body
        signature = private.sign(token.encode(), padding.PKCS1v15(), hashes.SHA256())
        return canonical(dict(unsigned, signedArchive=token + '.' + base64.urlsafe_b64encode(signature).rstrip(b'=').decode()))

    def test_restored_normal_and_short_archives_have_full_closure(self):
        result = self.db.inspect()
        self.assertEqual(result['temporary_archives_verified'], 2)
        self.assertEqual(result['temporary_archive_commands_verified'], 3)
        self.assertEqual(result['submission_archives_verified'], 3)
        self.assertEqual(result['submission_archive_file_references'], 8)

    def test_formal_v1_still_requires_both_original_files(self):
        proof = self.proofs[2]
        self.assertEqual(proof['archiveVersion'], 1)
        for name in ('payloadSha256', 'receiptSha256'):
            with self.subTest(name=name):
                path = self.db.files / 'submissions' / (proof[name] + '.json')
                held = path.with_suffix('.r09-held'); self.assertFalse(held.exists())
                path.rename(held)
                try:
                    with self.assertRaises(OpsError):
                        self.db.inspect()
                    self.assertFalse(path.exists())
                finally:
                    held.rename(path)

    def test_each_of_three_missing_files_is_rejected_without_recreation(self):
        for name in ('payloadSha256', 'receiptSha256', 'commandResponseSha256'):
            with self.subTest(name=name):
                path = self.db.files / 'submissions' / (self.proof[name] + '.json')
                held = path.with_suffix('.r09-held'); self.assertFalse(held.exists())
                path.rename(held)
                try:
                    with self.assertRaises(OpsError):
                        self.db.inspect()
                    self.assertFalse(path.exists())
                finally:
                    held.rename(path)
        self.db.inspect()

    def test_each_of_three_corrupted_files_is_rejected(self):
        for name in ('payloadSha256', 'receiptSha256', 'commandResponseSha256'):
            with self.subTest(name=name):
                path = self.db.files / 'submissions' / (self.proof[name] + '.json'); original = path.read_bytes()
                try:
                    path.write_bytes(original + b'\n')
                    with self.assertRaises(OpsError):
                        self.db.inspect()
                finally:
                    path.write_bytes(original)
        self.db.inspect()

    def test_validly_resigned_wrong_archive_identity_and_capabilities_fail(self):
        mutations = [('localDeletionAllowed', True), ('archiveVersion', 3), ('mappingVersion', '2'),
                     ('operatorId', self.proof['freezeDeviceId']), ('authorizationId', self.proof['freezeDeviceId']),
                     ('orderId', self.proofs[0]['orderId']), ('commandReceiptId', self.proofs[0]['commandReceiptId']),
                     ('commandResponseSha256', self.proofs[0]['commandResponseSha256']),
                     ('commandBytes', '1'), ('retainUntil', self.proof['archivedAt']), ('extra', False)]
        for key, value in mutations:
            with self.subTest(key=key):
                proof = dict(self.proof, **{key: value})
                self.transaction_fault('UPDATE submission_archive SET confirmation_text=%s WHERE submission_id=UUID_TO_BIN(%s)',
                                       (self.sign_archive(proof), self.id))

    def test_wrong_command_event_bytes_fail(self):
        self.transaction_fault('UPDATE temporary_decision_event SET context_bytes=%s WHERE receipt_id=UUID_TO_BIN(%s)',
                               (b'{}', self.proof['commandReceiptId']))

    def test_missing_command_audit_fails(self):
        self.transaction_fault("DELETE FROM operation_event WHERE entity_type='TEMPORARY_COMMAND' AND entity_id=UUID_TO_BIN(%s)",
                               (self.proof['commandReceiptId'],))

    def test_wrong_idempotency_reference_fails(self):
        row = read_record(self.db, self.proof['commandReceiptId'])
        self.transaction_fault('UPDATE api_idempotency_request SET resource_id=UUID_TO_BIN(%s) WHERE idempotency_key=%s',
                               (self.proofs[0]['commandReceiptId'], row['commandId']))

    def test_corrupt_approval_original_blob_fails(self):
        row = read_record(self.db, self.proof['commandReceiptId'])
        receipt = command_proof(row, self.db.keys)
        approval = receipt['businessDecision']['shortApprovalReceiptId']
        self.transaction_fault('UPDATE temporary_command_receipt SET original_response_bytes=%s WHERE id=UUID_TO_BIN(%s)', (b'{}', approval))

    def test_unknown_historical_signing_key_fails(self):
        self.transaction_fault('UPDATE temporary_command_receipt SET signature_key_id=%s WHERE id=UUID_TO_BIN(%s)',
                               ('offline-unknown', self.proof['commandReceiptId']))

    def test_current_revocation_does_not_rewrite_historical_approval(self):
        row = read_record(self.db, self.proof['commandReceiptId'])
        confirmation = command_proof(row, self.db.keys)
        approval = read_record(self.db, confirmation['businessDecision']['shortApprovalReceiptId'])
        self.db.connection.begin()
        try:
            self.update("UPDATE app_user SET status='DISABLED',auth_version=auth_version+1 WHERE id=UUID_TO_BIN(%s)", (approval['actorId'],))
            self.assertEqual(self.db.inspect()['temporary_archive_commands_verified'], 3)
        finally:
            self.db.connection.rollback()
