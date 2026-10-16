"""Retention time, original byte binding and protected reference rejection."""
from contextlib import closing
from copy import deepcopy
from pathlib import Path

from ..catalog import schema, validate
from ..codec import canonical, digest, parse
from ..consumer import consume, prepare
from ..proof import DAY, instant, retained
from ..store import connect, location
from .factory import allow, insert, mutate
from .support import RetentionCase


class GuardsTest(RetentionCase):
    def reject_mutation(self, sql, values=()):
        mutate(self.spec, sql, values)
        before = self.source.read_bytes()
        with self.assertRaises((ValueError, KeyError)):
            prepare(self.spec)
        self.assertEqual(before, self.source.read_bytes())

    def test_valid_guard_prevents_whole_order_deletion(self):
        capture = self.fixture['body']['captures'][0]
        self.reject_mutation('INSERT INTO work_guard VALUES (?,?,?,?)',
                             (capture['barcode'], capture['id'], capture['assignmentId'], 1))

    def test_unresolved_input_protected(self):
        self.reject_mutation("UPDATE work_capture SET state='RECEIVED'")

    def test_autoretry_submission_protected(self):
        self.reject_mutation('UPDATE local_submission SET autoRetry=1')

    def test_inflight_order_protected(self):
        self.reject_mutation("UPDATE work_order SET state='WORKING'")

    def test_continuation_reference_protects_order(self):
        self.reject_mutation('INSERT INTO local_continuation VALUES (?,?,?,?,?,?,?,?,?)',
                             ('synthetic-ticket', self.fixture['body']['assignmentId'], '{}', '{}',
                              2, 3, 'synthetic-request', 1, '[]'))

    def test_recovery_and_unknown_reference_tables_protected(self):
        self.reject_mutation("UPDATE local_recovery_control SET mode='RECOVERY_REQUIRED'")

    def test_unknown_table_fails_closed(self):
        self.reject_mutation('CREATE TABLE unknown_future_reference (value TEXT)')

    def test_removed_foreign_key_rejects_even_with_room_identity(self):
        entity = next(e for e in schema()[0]['entities'] if e['tableName'] == 'work_line')
        sql = entity['createSql'].replace('${TABLE_NAME}', 'work_line')
        sql = sql[:sql.index(', FOREIGN KEY')] + ')'
        with closing(connect(self.source, True)) as db:
            db.execute('DROP TABLE work_line')
            db.execute(sql)
        self.reject_mutation('SELECT 1')

    def test_changed_payload_bytes_cannot_be_rehashed_into_archive(self):
        body = canonical(self.fixture['body']).decode() + ' '
        self.reject_mutation('UPDATE local_submission SET payload=?,sha256=? WHERE id=?',
                             (body, digest(body.encode()), self.fixture['body']['submissionId']))

    def test_archive_unsigned_tampering_rejected(self):
        archive = deepcopy(self.fixture['confirmation'])
        archive['localDeletionAllowed'] = True
        self.reject_mutation('UPDATE local_submission_archive SET confirmationJson=?',
                             (canonical(archive).decode(),))

    def test_pinned_key_mismatch_rejects(self):
        spec = parse(self.spec.read_bytes())
        spec['keySha256'] = '0' * 64
        self.spec.write_bytes(canonical(spec))
        with self.assertRaisesRegex(ValueError, 'Pinned'):
            prepare(self.spec)

    def test_corrupt_backup_prevents_deletion(self):
        prepared = self.prepared()
        allow(prepared)
        run = Path(prepared['run'])
        before = (run / 'candidate.sqlite').read_bytes()
        with (run / 'backup.sqlite').open('ab') as output:
            output.write(b'corrupted')
        with self.assertRaisesRegex(ValueError, 'Backup changed'):
            consume(run, prepared['planSha256'], True)
        self.assertEqual(before, (run / 'candidate.sqlite').read_bytes())

    def test_strict_ninety_day_nanosecond_boundary(self):
        archive = self.fixture['confirmation']
        boundary = instant(archive['completedAt']) + 90 * DAY
        for delta in (-1, 0):
            with self.subTest(delta=delta), self.assertRaisesRegex(ValueError, '90-day'):
                retained(archive, boundary + delta)
        retained(archive, boundary + 1)

    def test_cloud_retention_less_than_five_years_rejects(self):
        archive = deepcopy(self.fixture['confirmation'])
        archive['retainUntil'] = '2031-10-05T18:23:33.701999999Z'
        with self.assertRaisesRegex(ValueError, 'Five-year'):
            retained(archive, instant(self.spec_data['now']))

    def test_expired_archive_rejects(self):
        archive = self.fixture['confirmation']
        with self.assertRaisesRegex(ValueError, 'not currently retained'):
            retained(archive, instant(archive['retainUntil']))

    def test_path_escape_and_parent_traversal_rejects(self):
        for path in ('C:/not-a-retention-copy.sqlite', str(self.directory / '../escape.sqlite')):
            with self.subTest(path=path), self.assertRaises(ValueError):
                location(path)
