"""Original accepted body identity and retained stock cannot drift during recovery."""
import copy
import json
import unittest
from backup_factory import test_directory
from archive_proofs import canonical
from temporary_payloads import body_control, reservations
from safety import OpsError, digest


def fixture():
    row = dict(id='01994300-0000-7000-8000-000000000001', order='order', creator='device', client='client',
        auth='auth', freeze='device', round=1, epoch=1, status='REVIEW_REQUIRED', issues=[])
    row['manifest'] = json.dumps(dict(identity={'authorizationEpoch': '1'}, inventorySnapshotId='snapshot', policyVersion='1'))
    plan = dict(clientOrderId='client', creatorDeviceId='device', authorizationId='auth', revision='1')
    body = dict(schemaVersion=2, submissionId=row['id'], source=dict(kind='TEMP', creatorDeviceId='device', clientOrderId='client'),
        assignmentId=None, continuationId=None, authorizationId='auth', freezeDeviceId='device', workRound='1', sourceRecoveryEpoch='1',
        expectedCount='1', expectedWeightGrams='100', captureHighWatermark='1', authorizationEpoch='1', inventorySnapshotId='snapshot',
        policyVersion='1', temporaryOrder=plan, orderRevision='1', planSha256=digest(canonical(plan).encode('utf-8')))
    body['scans'] = [dict(barcode='00300000001', status='ACTIVE', expectedDispatchCycle='1')]
    return row, body, dict(version=2, count=1, weight=100, high=1)


class RetainedRows:
    def __init__(self, rows):
        self.rows = rows

    def query(self, _sql):
        return self.rows


class PayloadTests(unittest.TestCase):
    def test_original_body_control_and_plan_match(self):
        body_control(*fixture())

    def test_control_and_snapshot_rebinding_are_rejected(self):
        for field in ('submissionId', 'authorizationId', 'assignmentId', 'continuationId', 'freezeDeviceId',
                      'workRound', 'sourceRecoveryEpoch', 'authorizationEpoch', 'inventorySnapshotId', 'policyVersion'):
            row, body, payload = fixture()
            body[field] = 'different'
            with self.subTest(field=field), self.assertRaises(OpsError):
                body_control(row, body, payload)

    def test_plan_hash_and_source_must_both_match(self):
        row, body, payload = fixture()
        body['temporaryOrder']['clientOrderId'] = 'other'
        with self.assertRaisesRegex(OpsError, 'TEMP_PLAN_HASH_MISMATCH'):
            body_control(row, body, payload)
        body['planSha256'] = digest(canonical(body['temporaryOrder']).encode('utf-8'))
        with self.assertRaisesRegex(OpsError, 'TEMP_PLAN_SOURCE_MISMATCH'):
            body_control(row, body, payload)

    def test_received_summary_does_not_follow_declared_metadata(self):
        row, body, payload = fixture()
        payload['count'] = 2
        with self.assertRaisesRegex(OpsError, 'TEMP_PAYLOAD_SUMMARY_MISMATCH'):
            body_control(row, body, payload)


class ReservationTests(unittest.TestCase):
    def test_pending_clean_scan_requires_retained_reservation(self):
        row, body, _ = fixture()
        reservations(RetainedRows('00300000001\t1\tREVIEW_RESERVED\torder'), row, body)
        with self.assertRaisesRegex(OpsError, 'TEMP_RESERVATION_MISMATCH'):
            reservations(RetainedRows(''), row, body)

    def test_pending_conflict_does_not_fabricate_ownership(self):
        row, body, _ = fixture()
        row['issues'] = [dict(code='BALE_ALREADY_COMMITTED', barcode='00300000001')]
        reservations(RetainedRows(''), row, body)
        with self.assertRaisesRegex(OpsError, 'TEMP_RESERVATION_MISMATCH'):
            reservations(RetainedRows('00300000001\t1\tREVIEW_RESERVED\torder'), row, body)

    def test_abort_with_cleared_issues_only_proves_retained_subset(self):
        row, body, _ = fixture()
        row.update(status='ABORTED', issues=None)
        reservations(RetainedRows(''), row, body)
        reservations(RetainedRows('00300000001\t1\tREVIEW_RESERVED\torder'), row, body)
        for actual in ('00300000001\t1\tDISPATCHED\torder', '00300000001\t2\tREVIEW_RESERVED\torder',
                       '00300000001\t1\tREVIEW_RESERVED\tother', '00300000002\t1\tREVIEW_RESERVED\torder'):
            with self.subTest(actual=actual), self.assertRaisesRegex(OpsError, 'TEMP_RESERVATION_MISMATCH'):
                reservations(RetainedRows(actual), row, body)
