"""Actual CLI success, default-off, restored originals and atomic rollback."""
from contextlib import closing
from pathlib import Path
import sqlite3

from ..codec import digest, parse
from ..consumer import consume, expected_after, transaction
from ..store import connect, facts, restore
from .factory import allow, mutate
from .support import RetentionCase


class ConsumerTest(RetentionCase):
    def test_cli_success_restores_complete_original_after_deletion(self):
        before = self.snapshot(self.source)
        preparation = self.cli(['prepare', self.spec], 'prepare')
        self.assertEqual(0, preparation.returncode, preparation.stdout)
        prepared = parse(preparation.stdout)
        allow(prepared)
        result = self.cli(self.consume_args(prepared), 'consume')
        self.assertEqual(0, result.returncode, result.stdout)
        run = Path(prepared['run'])
        plan = parse((run / 'plan.json').read_bytes())
        self.assertEqual(expected_after(plan), self.snapshot(run / 'candidate.sqlite'))
        self.assertEqual(before, self.snapshot(self.source))
        restore(run / 'backup.sqlite', run / 'after-delete-restored.sqlite')
        self.assertEqual(before, self.snapshot(run / 'after-delete-restored.sqlite'))
        self.assertEqual(self.spec_data['sourceSha256'], digest(self.source.read_bytes()))
        self.assertEqual('COMMITTED_SYNTHETIC_ONLY', parse(result.stdout)['state'])
        replay = self.cli(self.consume_args(prepared), 'replay')
        self.assertEqual(0, replay.returncode, replay.stdout)
        self.assertEqual('REPLAY_NO_WRITE', parse(replay.stdout)['state'])

    def test_default_off_with_and_without_permit_preserves_candidate(self):
        prepared = self.prepared()
        candidate = Path(prepared['run']) / 'candidate.sqlite'
        original = candidate.read_bytes()
        for granted in (False, True):
            allow(prepared, granted)
            result = self.cli(self.consume_args(prepared, False), 'off-' + str(granted))
            self.assertEqual(1, result.returncode)
            self.assertEqual(original, candidate.read_bytes())

    def test_enabled_without_permit_rejects_without_business_write(self):
        prepared = self.prepared()
        candidate = Path(prepared['run']) / 'candidate.sqlite'
        original = candidate.read_bytes()
        result = self.cli(self.consume_args(prepared), 'no-permit')
        self.assertEqual(1, result.returncode)
        self.assertIn(b'permit', result.stdout)
        self.assertEqual(original, candidate.read_bytes())

    def test_last_delete_sql_failure_rolls_back_all_previous_deletes(self):
        prepared = self.prepared()
        allow(prepared)
        run = Path(prepared['run'])
        plan = parse((run / 'plan.json').read_bytes())
        deleted_tables = []
        def authorizer(action, table, column, database, source):
            if action == sqlite3.SQLITE_DELETE:
                deleted_tables.append(table)
                if table == 'prepared_order':
                    return sqlite3.SQLITE_DENY
            return sqlite3.SQLITE_OK
        with closing(connect(run / 'candidate.sqlite', True)) as db:
            db.set_authorizer(authorizer)
            with self.assertRaises(sqlite3.DatabaseError):
                transaction(db, plan, (run, prepared['planSha256']))
            db.set_authorizer(None)
            self.assertEqual(plan['before'], facts(db))
        self.assertEqual('prepared_order', deleted_tables[-1])
        self.assertGreaterEqual(len(deleted_tables), 9)
        (self.directory / 'denied-delete-trace.txt').write_text('\n'.join(deleted_tables), encoding='utf-8')

    def test_changed_candidate_rejects_without_further_write(self):
        prepared = self.prepared()
        allow(prepared)
        candidate = Path(prepared['run']) / 'candidate.sqlite'
        with closing(connect(candidate, True)) as db:
            db.execute("UPDATE work_device SET nextCaptureSeq=nextCaptureSeq+1")
        before = candidate.read_bytes()
        result = self.cli(self.consume_args(prepared), 'changed-candidate')
        self.assertEqual(1, result.returncode)
        self.assertEqual(before, candidate.read_bytes())
