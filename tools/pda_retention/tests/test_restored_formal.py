"""Actual CLI/rollback/concurrency with restored normal guards, original receipts and audit generations."""
from contextlib import closing
from pathlib import Path
import sqlite3

from ..store import connect, restore
from .factory import allow
from .restored_formal_case import RestoredFormalCase
from . import test_concurrency, test_consumer


class RestoredFormalConsumerTest(RestoredFormalCase, test_consumer.ConsumerTest):
    """All existing consumer paths run on the complete three-source combined input."""


class RestoredFormalConcurrencyTest(RestoredFormalCase, test_concurrency.ConcurrencyTest):
    """Actual same-key concurrency and lock-wait revocation retain restored original orders."""


class RestoredFormalReferenceTest(RestoredFormalCase):
    def test_cli_preserves_restored_normal_order_and_both_audit_generations(self):
        before = self.snapshot(self.source)
        prepared = self.prepared()
        allow(prepared)
        result = self.cli(self.consume_args(prepared), 'restored-formal-consume')
        self.assertEqual(0, result.returncode, result.stdout)
        run = Path(prepared['run'])
        self.assert_formal_preserved(run / 'candidate.sqlite')
        after = self.snapshot(run / 'candidate.sqlite')
        for table in ('work_guard', 'local_audit_archive', 'local_audit_checkpoint'):
            self.assertEqual(before[table], after[table])
        restore(run / 'backup.sqlite', run / 'whole-combination-restored.sqlite')
        self.assertEqual(before, self.snapshot(run / 'whole-combination-restored.sqlite'))
        self.assert_formal_preserved(run / 'whole-combination-restored.sqlite')

    def test_restored_guard_parent_cannot_be_deleted(self):
        capture = self.formal['work_guard'][0]['captureId']
        before = self.snapshot(self.source)
        with closing(connect(self.source, True)) as db:
            with self.assertRaisesRegex(sqlite3.IntegrityError, 'FOREIGN KEY'):
                db.execute('DELETE FROM work_capture WHERE id=?', (capture,))
        self.assertEqual(before, self.snapshot(self.source))

    def test_restored_guard_cycle_changed_rejects(self):
        guard = self.formal['work_guard'][0]
        self.reject_change('UPDATE work_guard SET dispatchCycle=dispatchCycle+1 WHERE captureId=?',
                           (guard['captureId'],), expected='dispatch cycle')

    def test_restored_guard_referencing_target_capture_rejects(self):
        target = self.fixture['body']['captures'][0]['id']
        guard = self.formal['work_guard'][0]
        self.reject_change('UPDATE work_guard SET captureId=? WHERE captureId=?',
                           (target, guard['captureId']), expected='Effective guard')

    def test_retained_normal_prepared_row_referencing_target_rejects(self):
        target = self.fixture['body']['submissionId']
        assignment = self.formal['prepared_order'][0]['assignmentId']
        self.reject_change('UPDATE prepared_order SET manifestJson=? WHERE assignmentId=?',
                           ('{"restoreReference":"' + target + '"}', assignment), expected='External reference')
