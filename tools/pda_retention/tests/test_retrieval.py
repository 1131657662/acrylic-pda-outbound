"""Actual archive-file reads bound to a signed synthetic deletion candidate."""
from contextlib import closing
from pathlib import Path
import sqlite3
import threading

from ..codec import canonical, parse
from ..consumer import transaction
from ..store import connect, facts
from .factory import allow, mutate
from .support import RetentionCase


class RetrievalTest(RetentionCase):
    def change_spec(self, update):
        spec = parse(self.spec.read_bytes())
        update(spec)
        self.spec.write_bytes(canonical(spec))

    def reject_preparation(self, expected):
        before = self.source.read_bytes()
        result = self.cli(['prepare', self.spec], 'retrieval-rejected')
        self.assertEqual(1, result.returncode, result.stdout)
        self.assertIn(expected.lower().encode(), result.stdout.lower())
        self.assertEqual(before, self.source.read_bytes())
        self.assertFalse(any(self.directory.glob('retention-*/plan.json')))

    def rejected_consumption(self, prepared, expected):
        candidate = Path(prepared['run']) / 'candidate.sqlite'
        before = self.snapshot(candidate)
        result = self.cli(self.consume_args(prepared), 'retrieval-consume-rejected')
        self.assertEqual(1, result.returncode, result.stdout)
        self.assertIn(expected.lower().encode(), result.stdout.lower())
        self.assertEqual(before, self.snapshot(candidate))

    def test_historical_confirmation_without_retrievable_paths_rejects(self):
        self.change_spec(lambda spec: spec.pop('archiveFiles'))
        self.reject_preparation('retrievable archive files required')

    def test_unknown_archive_file_role_rejects(self):
        self.change_spec(lambda spec: spec['archiveFiles'].update(other='unreviewed'))
        self.reject_preparation('retrievable archive files required')

    def test_non_string_archive_path_rejects(self):
        self.change_spec(lambda spec: spec['archiveFiles'].update(payload=1))
        self.reject_preparation('paths must be strings')

    def test_missing_archive_file_cannot_be_replaced_by_database_receipt(self):
        path = Path(self.spec_data['archiveFiles']['receipt'])
        path.rename(path.with_suffix('.unavailable'))
        self.reject_preparation('Missing or oversized input')

    def test_payload_whitespace_drift_rejects(self):
        path = Path(self.spec_data['archiveFiles']['payload'])
        path.write_bytes(path.read_bytes() + b' ')
        self.reject_preparation('archive bytes differ: payload')

    def test_signed_receipt_file_whitespace_drift_rejects(self):
        path = Path(self.spec_data['archiveFiles']['receipt'])
        path.write_bytes(path.read_bytes() + b' ')
        self.reject_preparation('archive bytes differ: receipt')

    def test_same_path_for_both_archive_roles_rejects(self):
        self.change_spec(lambda spec: spec['archiveFiles'].update(
            receipt=spec['archiveFiles']['payload']))
        self.reject_preparation('Distinct archive files required')

    def test_external_archive_path_rejects(self):
        self.change_spec(lambda spec: spec['archiveFiles'].update(payload='C:/untrusted.json'))
        self.reject_preparation('Only project .local')

    def test_relative_archive_path_rejects(self):
        self.change_spec(lambda spec: spec['archiveFiles'].update(payload='.local/payload.json'))
        self.reject_preparation('Absolute archive file paths required')

    def test_files_changed_after_prepare_cannot_delete_any_fact(self):
        prepared = self.prepared()
        allow(prepared)
        path = Path(self.spec_data['archiveFiles']['receipt'])
        path.write_bytes(b'corrupted after preparation')
        self.rejected_consumption(prepared, 'archive bytes differ: receipt')

    def test_last_delete_followed_by_file_loss_rolls_back_all_facts(self):
        prepared = self.prepared()
        allow(prepared)
        run = Path(prepared['run'])
        plan = parse((run / 'plan.json').read_bytes())
        archive = Path(self.spec_data['archiveFiles']['payload'])
        deleted = []
        def remove_at_last_delete(action, table, column, database, source):
            if action == sqlite3.SQLITE_DELETE:
                deleted.append(table)
                if table == 'prepared_order':
                    archive.rename(archive.with_suffix('.unavailable'))
            return sqlite3.SQLITE_OK
        with closing(connect(run / 'candidate.sqlite', True)) as db:
            db.set_authorizer(remove_at_last_delete)
            with self.assertRaisesRegex(ValueError, 'Missing or oversized input'):
                transaction(db, plan, (run, prepared['planSha256']))
            db.set_authorizer(None)
            self.assertEqual(plan['before'], facts(db))
        self.assertEqual('prepared_order', deleted[-1])
        self.assertGreaterEqual(len(deleted), 9)
        (self.directory / 'file-loss-delete-trace.json').write_bytes(canonical(deleted))

    def test_file_lost_during_real_lock_wait_prevents_deletion(self):
        prepared = self.prepared()
        allow(prepared)
        run = Path(prepared['run'])
        plan = parse((run / 'plan.json').read_bytes())
        began, done, outcome = threading.Event(), threading.Event(), []
        def waiter():
            with closing(connect(run / 'candidate.sqlite', True)) as db:
                db.set_trace_callback(lambda sql: began.set() if sql == 'BEGIN IMMEDIATE' else None)
                try:
                    outcome.append(transaction(db, plan, (run, prepared['planSha256'])))
                except Exception as error:
                    outcome.append(str(error))
            done.set()
        with closing(connect(run / 'candidate.sqlite', True)) as blocker:
            blocker.execute('BEGIN IMMEDIATE')
            worker = threading.Thread(target=waiter)
            worker.start()
            self.assertTrue(began.wait(5))
            self.assertFalse(done.wait(0.2))
            archive = Path(self.spec_data['archiveFiles']['receipt'])
            archive.rename(archive.with_suffix('.unavailable'))
            blocker.execute('COMMIT')
        worker.join(15)
        self.assertFalse(worker.is_alive())
        self.assertIn('Missing or oversized input', outcome[0])
        self.assertEqual(plan['before'], self.snapshot(run / 'candidate.sqlite'))
        (self.directory / 'file-loss-lock-wait.json').write_bytes(canonical(outcome))

    def test_replay_also_requires_current_archive_file_read(self):
        prepared = self.prepared()
        allow(prepared)
        success = self.cli(self.consume_args(prepared), 'retrieval-success')
        self.assertEqual(0, success.returncode, success.stdout)
        archive = Path(self.spec_data['archiveFiles']['payload'])
        archive.rename(archive.with_suffix('.unavailable'))
        self.rejected_consumption(prepared, 'Missing or oversized input')

    def test_valid_files_do_not_release_effective_target_guard(self):
        capture = self.fixture['body']['captures'][0]
        mutate(self.spec, 'INSERT INTO work_guard VALUES (?,?,?,?)',
               (capture['barcode'], capture['id'], capture['assignmentId'], 1))
        self.reject_preparation('Effective guard protects target order')
