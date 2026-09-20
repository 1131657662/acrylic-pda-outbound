"""Cancellation cannot fabricate a target or turn declared bytes into accepted facts."""
import copy
import json
import unittest
from backup_factory import test_directory
from temporary_submissions import control, abort_receipt, source
from temporary_uploads import geometry, CHUNK
from safety import OpsError


def row_fixture():
    return dict(id='submission', current='submission', scopeRound=1, round=1, formal=None, assignment=None,
        continuation=None, creator='device', client='client', auth='auth', originAuth='auth', freeze='device',
        actor='user', authDevice='device', authUser='user', epoch=1, authEpoch=1, warehouse='warehouse',
        authWarehouse='warehouse', status='ABORTED', order=None, receipt=None)


def cancelled(row, payload=None):
    return dict(submissionId=row['id'], orderId=row['order'], status='ABORTED', source=source(row),
        freezeDeviceId=row['freeze'], workRound='1', issuedRecoveryEpoch='1', payloadSha256=payload['hash'] if payload else None,
        payloadPresent=bool(payload), stockReleased=False, mayResumeScanning=False)


class ControlTests(unittest.TestCase):
    def test_original_control_and_bodyless_cancel_are_valid(self):
        row = row_fixture()
        row['receipt'] = json.dumps(cancelled(row))
        control(row)
        abort_receipt(row, None)

    def test_different_origin_identity_or_current_round_is_refused(self):
        for key, value in [('originAuth', 'other'), ('freeze', 'other'), ('actor', 'other'), ('epoch', 2),
                           ('warehouse', 'other'), ('current', 'other'), ('round', 2), ('assignment', 'invented')]:
            row = row_fixture()
            row[key] = value
            with self.subTest(key=key), self.assertRaises(OpsError):
                control(row)

    def test_future_temp_terminal_states_fail_closed(self):
        for status in ('SUCCEEDED', 'REJECTED', 'COMPLETED'):
            row = row_fixture()
            row['status'] = status
            with self.subTest(status=status), self.assertRaisesRegex(OpsError, 'TEMP_STATE_UNSUPPORTED'):
                control(row)

    def test_cancel_cannot_claim_release_resume_or_declared_upload_as_body(self):
        for key, value in [('stockReleased', True), ('mayResumeScanning', True), ('payloadPresent', True),
                           ('payloadSha256', '0' * 64), ('orderId', 'invented')]:
            row = row_fixture()
            receipt = cancelled(row)
            receipt[key] = value
            row['receipt'] = json.dumps(receipt)
            with self.subTest(key=key), self.assertRaises(OpsError):
                abort_receipt(row, None)

    def test_after_body_abort_preserves_original_target_and_digest(self):
        row, payload = row_fixture(), {'hash': 'a' * 64}
        row['order'] = 'canonical'
        row['receipt'] = json.dumps(cancelled(row, payload))
        abort_receipt(row, payload)

    def test_unsigned_pending_state_cannot_retain_terminal_receipt(self):
        row = row_fixture()
        row.update(status='REVIEW_REQUIRED', receipt='{}')
        with self.assertRaisesRegex(OpsError, 'TEMP_UNEXPECTED_TERMINAL_RECEIPT'):
            abort_receipt(row, None)


class UploadTests(unittest.TestCase):
    def test_empty_sparse_and_full_chunks_share_one_declared_geometry(self):
        upload = {'size': CHUNK + 17, 'count': 2, 'hash': 'a' * 64}
        chunks = [{'index': 0, 'size': CHUNK}, {'index': 1, 'size': 17}]
        for subset in ([], chunks[:1], chunks[1:], chunks):
            geometry(upload, subset)

    def test_bad_count_overflow_duplicate_index_or_wrong_tail_are_refused(self):
        cases = [({'size': CHUNK + 17, 'count': 1}, []), ({'size': 33554433, 'count': 129}, []),
                 ({'size': 17, 'count': 1}, [{'index': 0, 'size': 17}] * 2),
                 ({'size': 17, 'count': 1}, [{'index': 1, 'size': 17}]),
                 ({'size': 17, 'count': 1}, [{'index': 0, 'size': 16}])]
        for upload, chunks in cases:
            with self.subTest(upload=upload, chunks=chunks), self.assertRaises(OpsError):
                geometry({**upload, 'hash': 'a' * 64}, chunks)
