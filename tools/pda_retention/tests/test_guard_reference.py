"""CLI deletion preserves unrelated guards; malformed or target guards reject."""
from pathlib import Path

from ..codec import parse
from .factory import allow, mutate
from .guard_case import UnrelatedGuardCase
from . import test_concurrency, test_consumer


class UnrelatedGuardConsumerTest(UnrelatedGuardCase, test_consumer.ConsumerTest):
    """Repeat real CLI/default-off/replay/rollback with an effective unrelated guard."""


class UnrelatedGuardConcurrencyTest(UnrelatedGuardCase, test_concurrency.ConcurrencyTest):
    """Verify guarded input under actual concurrent calls and lock-wait revocation."""


class GuardReferenceTest(UnrelatedGuardCase):
    def reject_changed_chain(self, sql, values=(), expected='guard'):
        mutate(self.spec, sql, values)
        before = self.source.read_bytes()
        result = self.cli(['prepare', self.spec], 'rejected-prepare')
        self.assertEqual(1, result.returncode, result.stdout)
        self.assertIn(expected.lower().encode(), result.stdout.lower())
        self.assertEqual(before, self.source.read_bytes())
        runs = list(self.directory.glob('retention-*'))
        self.assertEqual(1, len(runs))
        self.assertFalse((runs[0] / 'plan.json').exists())
        self.assertFalse((runs[0] / 'permit.json').exists())
        self.assertEqual(self.snapshot(self.source), self.snapshot(runs[0] / 'candidate.sqlite'))

    def test_unrelated_guard_survives_complete_target_deletion(self):
        prepared = self.prepared()
        run = Path(prepared['run'])
        plan = parse((run / 'plan.json').read_bytes())
        self.assertEqual(1, len(plan['before']['work_guard']))
        self.assertNotIn('work_guard', plan['selected'])
        self.assertTrue(all('unrelated-capture' not in raw
                            for values in plan['selected'].values() for raw in values))
        self.assertEqual(self.snapshot(self.source), self.snapshot(run / 'backup.sqlite'))
        allow(prepared)
        result = self.cli(self.consume_args(prepared), 'consume-with-unrelated-guard')
        self.assertEqual(0, result.returncode, result.stdout)
        after = self.snapshot(run / 'candidate.sqlite')
        self.assertEqual(plan['before']['work_guard'], after['work_guard'])
        for table in ('work_order', 'work_capture', 'work_scan'):
            self.assertTrue(after[table])
            self.assertTrue(all('unrelated-' in row for row in after[table]))

    def test_guard_assignment_mismatch_rejects(self):
        self.reject_changed_chain("UPDATE work_guard SET assignmentId='unknown-assignment'")

    def test_guard_existing_assignment_mismatch_rejects(self):
        self.reject_changed_chain('UPDATE work_guard SET assignmentId=?',
                                  (self.fixture['body']['assignmentId'],))

    def test_guard_barcode_mismatch_rejects(self):
        self.reject_changed_chain("UPDATE work_guard SET barcode='DIFFERENT01'")

    def test_guard_cycle_mismatch_rejects(self):
        self.reject_changed_chain('UPDATE work_guard SET dispatchCycle=dispatchCycle+1')

    def test_void_scan_with_guard_rejects(self):
        self.reject_changed_chain("UPDATE work_scan SET status='VOID' WHERE captureId='unrelated-capture'")

    def test_pending_capture_with_guard_rejects(self):
        self.reject_changed_chain("UPDATE work_capture SET state='RECEIVED' WHERE id='unrelated-capture'")

    def test_capture_barcode_mismatch_rejects(self):
        self.reject_changed_chain("UPDATE work_capture SET barcode='DIFFERENT01' WHERE id='unrelated-capture'")

    def test_capture_order_mismatch_rejects(self):
        self.reject_changed_chain("UPDATE work_capture SET orderId='unknown-order' WHERE id='unrelated-capture'")

    def test_capture_assignment_mismatch_rejects(self):
        self.reject_changed_chain("UPDATE work_capture SET assignmentId=? WHERE id='unrelated-capture'",
                                  (self.fixture['body']['assignmentId'],), expected='Frozen record set differs')

    def test_capture_origin_assignment_reference_rejects(self):
        self.reject_changed_chain("UPDATE work_capture SET originAssignmentId='other-origin' "
                                  "WHERE id='unrelated-capture'")

    def test_scan_assignment_mismatch_rejects(self):
        self.reject_changed_chain('UPDATE work_scan SET assignmentId=?,orderScanSeq=99 '
                                  "WHERE captureId='unrelated-capture'",
                                  (self.fixture['body']['assignmentId'],), expected='Frozen record set differs')

    def test_capture_cycle_mismatch_rejects(self):
        self.reject_changed_chain('UPDATE work_capture SET expectedDispatchCycle=expectedDispatchCycle+1 '
                                  "WHERE id='unrelated-capture'")

    def test_cross_assignment_guard_target_capture_rejects(self):
        capture = self.fixture['body']['captures'][0]['id']
        self.reject_changed_chain('UPDATE work_guard SET captureId=?', (capture,))

    def test_target_guard_alongside_unrelated_guard_still_rejects(self):
        capture = self.fixture['body']['captures'][0]
        self.reject_changed_chain('INSERT INTO work_guard VALUES (?,?,?,?)',
                                  (capture['barcode'], capture['id'], capture['assignmentId'], 1))
