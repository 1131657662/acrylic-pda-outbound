"""CLI retention with unrelated original audit prefixes and their dependencies."""
from contextlib import closing
from pathlib import Path

from ..codec import canonical, parse
from ..store import connect, restore
from .audit_case import RETAINED, UnrelatedAuditCase
from .factory import allow, insert
from . import test_concurrency, test_consumer


class UnrelatedAuditConsumerTest(UnrelatedAuditCase, test_consumer.ConsumerTest):
    """Run default-off, actual CLI deletion/replay, recovery and rollback on audit input."""


class UnrelatedAuditConcurrencyTest(UnrelatedAuditCase, test_concurrency.ConcurrencyTest):
    """Run simultaneous CLI and real lock-wait revocation with both audit generations."""


class AuditReferenceTest(UnrelatedAuditCase):
    def test_original_audits_and_all_dependencies_survive_target_deletion(self):
        prepared = self.prepared()
        run = Path(prepared['run'])
        before = self.snapshot(self.source)
        allow(prepared)
        result = self.cli(self.consume_args(prepared), 'consume-original-audits')
        self.assertEqual(0, result.returncode, result.stdout)
        after = self.snapshot(run / 'candidate.sqlite')
        for table in RETAINED:
            originals = sorted(canonical(row).decode() for row in self.retained[table])
            self.assertTrue(all(row in after[table] for row in originals), table)
        for table in ('local_audit_archive', 'local_audit_checkpoint', 'work_guard', 'work_device'):
            self.assertEqual(before[table], after[table], table)
        restore(run / 'backup.sqlite', run / 'after-delete-restored.sqlite')
        self.assertEqual(before, self.snapshot(run / 'after-delete-restored.sqlite'))

    def test_pending_archive_rejects(self):
        self.reject_change("UPDATE local_audit_archive SET proofJson=NULL,signingKeyJson=NULL WHERE recoveryEpoch='2'",
                           expected='Pending audit')

    def test_archive_binding_tamper_rejects(self):
        self.reject_change("UPDATE local_audit_archive SET payloadSha256='changed' WHERE recoveryEpoch='1'",
                           expected='Audit binding')

    def test_signature_tamper_rejects(self):
        row = self.retained['local_audit_archive'][0]
        proof = parse(row['proofJson'])
        token = proof['signedArchive'].split('.')
        token[2] = ('A' if token[2][0] != 'A' else 'B') + token[2][1:]
        proof['signedArchive'] = '.'.join(token)
        self.reject_change('UPDATE local_audit_archive SET proofJson=? WHERE recoveryEpoch=?',
                           (canonical(proof).decode(), row['recoveryEpoch']))

    def test_unsigned_byte_count_tamper_rejects(self):
        row = self.retained['local_audit_archive'][0]
        proof = parse(row['proofJson'])
        proof['byteCount'] = str(int(proof['byteCount']) + 1)
        self.reject_change('UPDATE local_audit_archive SET proofJson=? WHERE recoveryEpoch=?',
                           (canonical(proof).decode(), row['recoveryEpoch']), 'Signed and unsigned')

    def test_unpinned_public_key_rejects(self):
        spec = parse(self.spec.read_bytes())
        spec['auditKeySha256'] = '0' * 64
        self.spec.write_bytes(canonical(spec))
        self.reject_change('SELECT 1', expected='Pinned audit key')

    def test_prefix_gap_rejects(self):
        self.reject_change('UPDATE local_audit_archive SET afterSeq=1', expected='Audit prefix gap')

    def test_event_detail_tamper_rejects(self):
        self.reject_change('UPDATE work_event SET detail=? WHERE originDeviceId=? AND localSeq=1',
                           ('changed', self.audit_device), 'Audit event digest')

    def test_event_missing_rejects(self):
        self.reject_change('DELETE FROM work_event WHERE originDeviceId=? AND localSeq=2',
                           (self.audit_device,), 'Audit event prefix')

    def test_event_moved_outside_prefix_rejects(self):
        self.reject_change('UPDATE work_event SET localSeq=16 WHERE originDeviceId=? AND localSeq=2',
                           (self.audit_device,), 'Audit event prefix')

    def test_actual_event_assignment_mismatch_rejects(self):
        assignment = 'unrelated-third-assignment'
        with closing(connect(self.source, True)) as db:
            for table in ('prepared_order', 'work_order'):
                row = dict(self.retained[table][0], assignmentId=assignment, orderId='unrelated-third-order')
                insert(db, table, row)
        self.reject_change('UPDATE work_event SET assignmentId=?,originAssignmentId=? WHERE originDeviceId=?',
                           (assignment, self.audit_assignment, self.audit_device), 'Audit event assignment')

    def test_checkpoint_ahead_rejects(self):
        self.reject_change('UPDATE local_audit_checkpoint SET acknowledgedThrough=acknowledgedThrough+1',
                           expected='Audit checkpoint')

    def test_checkpoint_behind_rejects(self):
        self.reject_change('UPDATE local_audit_checkpoint SET acknowledgedThrough=acknowledgedThrough-1',
                           expected='Audit checkpoint')

    def test_checkpoint_pending_rejects(self):
        self.reject_change("UPDATE local_audit_checkpoint SET pendingJson='{}'", expected='Audit checkpoint')

    def test_checkpoint_missing_rejects(self):
        self.reject_change("DELETE FROM local_audit_checkpoint WHERE recoveryEpoch='1'", expected='Audit checkpoint namespace')

    def test_orphan_checkpoint_rejects(self):
        self.reject_change("INSERT INTO local_audit_checkpoint VALUES ('3',?,0,NULL)",
                           (self.audit_device,), 'Audit checkpoint namespace')

    def test_restore_request_original_missing_rejects(self):
        self.reject_change("UPDATE local_audit_archive SET requestJson='{\"archive\":{}}' WHERE recoveryEpoch='2'",
                           expected='Audit restore original proof')

    def test_original_request_tamper_rejects(self):
        self.reject_change("UPDATE local_audit_archive SET requestJson='{}' WHERE recoveryEpoch='1'",
                           expected='Audit original request')

    def test_capture_warehouse_mismatch_rejects(self):
        self.reject_change('UPDATE work_capture SET warehouseId=? WHERE assignmentId=?',
                           ('different-warehouse', self.audit_assignment), 'Audit capture identity')

    def test_device_sequence_regression_rejects(self):
        self.reject_change('UPDATE work_device SET nextEventSeq=15 WHERE deviceId=?',
                           (self.audit_device,), 'Audit device sequence')

    def test_original_order_identity_mismatch_rejects(self):
        self.reject_change('UPDATE prepared_order SET orderId=? WHERE assignmentId=?',
                           ('changed-order', self.audit_assignment), 'Audit order identity')

    def test_target_reference_in_retained_event_rejects(self):
        self.reject_change('UPDATE work_event SET detail=? WHERE originDeviceId=? AND localSeq=1',
                           (self.fixture['body']['submissionId'], self.audit_device), 'Audit event digest')

    def test_target_event_reference_in_unarchived_tail_rejects(self):
        row = dict(self.retained['work_event'][0], id='unrelated-tail-event', captureId=None,
                   localSeq=16, orderPosition=16, detail=self.fixture['body']['events'][0]['id'])
        with closing(connect(self.source, True)) as db:
            insert(db, 'work_event', row)
        self.reject_change('SELECT 1', expected='External reference: work_event')

    def test_target_event_rebinding_rejects(self):
        event = self.fixture['body']['events'][0]
        # The target frozen payload is revalidated first; rebinding it cannot create deletion eligibility.
        self.reject_change('UPDATE work_event SET originDeviceId=?,localSeq=16 WHERE id=?',
                           (self.audit_device, event['id']), 'Frozen record differs')

    def test_effective_target_guard_still_rejects_with_valid_audits(self):
        capture = self.fixture['body']['captures'][0]
        self.reject_change('INSERT INTO work_guard VALUES (?,?,?,?)',
                           (capture['barcode'], capture['id'], capture['assignmentId'], 1), 'guard')

    def test_recovery_barrier_still_rejects_with_valid_audits(self):
        self.reject_change("UPDATE local_recovery_control SET mode='RECONCILING'", expected='Recovery reference')
