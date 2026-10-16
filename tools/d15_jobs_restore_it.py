"""Consume restored original business and explicitly synthetic task facts via the real CLI."""
import json
import os
import unittest
from d15_jobs_restore_support import prepare, derive_tasks, empty_restore, probe_config, originals
from d15_jobs_fixture import admin, verify
from safety import OpsError, write_json


class RestoredJobsIT(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.fixture = prepare(os.environ['D15_JOBS_RESTORE_MANIFEST'])
        cls.config = derive_tasks(cls.fixture)

    @classmethod
    def tearDownClass(cls):
        write_json(cls.fixture.root / 'source667-after.json', originals())

    def test_restored_tasks_are_visible_with_default_alerts(self):
        code, result = self.fixture.cli('restored-default', self.config)
        counts = result['counts']
        self.assertEqual((code, result['status']), (2, 'FAIL'))
        self.assertEqual({key: counts[key] for key in ('total', 'failed', 'expired_leases', 'ready', 'retry')},
                         {'total': 5, 'failed': 1, 'expired_leases': 1, 'ready': 2, 'retry': 1})
        self.assertGreaterEqual(counts['oldest_ready_seconds'], 600)
        self.assertEqual(set(result['exceeded']), {'failed', 'expired_leases', 'oldest_ready_seconds'})

    def test_explicit_thresholds_allow_bounded_success(self):
        config = {**self.config, 'limits': {'failed': 1, 'expired_leases': 1, 'ready': 2,
                                          'oldest_ready_seconds': 86400, 'retry': 1}}
        code, result = self.fixture.cli('restored-explicit-limits', config)
        self.assertEqual((code, result['status'], result['counts']['total']), (0, 'PASS', 5))

    def test_independent_raw_task_counts_match_the_cli(self):
        with admin(self.fixture.manifest) as connection, connection.cursor() as cursor:
            verify(connection, self.fixture.manifest)
            cursor.execute('SELECT status,available_at,lease_until,UTC_TIMESTAMP(3) FROM `d15_monitor_1668_restored`.background_job')
            tasks = cursor.fetchall()
        expected = {'total': len(tasks), 'failed': sum(row[0] == 'FAILED' for row in tasks),
                    'retry': sum(row[0] == 'RETRY' for row in tasks),
                    'expired_leases': sum(row[0] == 'RUNNING' and row[2] < row[3] for row in tasks),
                    'ready': sum(row[0] in ('PENDING', 'RETRY') and row[1] <= row[3] for row in tasks)}
        code, result = self.fixture.cli('restored-independent-counts', self.config)
        self.assertEqual(code, 2)
        self.assertEqual({key: result['counts'][key] for key in expected}, expected)
        write_json(self.fixture.root / 'independent-task-counts.json', expected)

    def test_wrong_instance_identity_is_refused(self):
        config = {**self.config, 'server_uuid': '00000000-0000-0000-0000-000000000000'}
        code, result = self.fixture.cli('restored-wrong-uuid', config)
        self.assertEqual((code, result['code']), (2, 'JOB_INSTANCE_IDENTITY_MISMATCH'))

    def test_revoked_column_access_is_refused(self):
        config = probe_config(self.fixture.manifest, self.config['database'], 'd15_revoked')
        with admin(self.fixture.manifest) as connection, connection.cursor() as cursor:
            verify(connection, self.fixture.manifest)
            cursor.execute("REVOKE SELECT (status,available_at,lease_until) ON `d15_monitor_1668_restored`.background_job FROM 'd15_revoked'@'127.0.0.1'")
        code, result = self.fixture.cli('restored-revoked', config)
        self.assertEqual((code, result['code']), (2, 'JOB_SELECT_ONLY_ACCOUNT_REQUIRED'))

    def test_restored_column_account_cannot_read_payload_or_write(self):
        import pymysql
        from jobs_credentials import credentials
        before = self.fixture.facts(self.config)
        with pymysql.connect(host='127.0.0.1', port=self.config['port'], database=self.config['database'],
                             **credentials(self.config['credentials_file']), autocommit=True) as connection:
            with connection.cursor() as cursor:
                for sql in ('SELECT payload FROM background_job', 'DELETE FROM background_job',
                            "UPDATE background_job SET status='FAILED'", 'SELECT 1; SELECT 2'):
                    with self.subTest(sql=sql), self.assertRaises(pymysql.MySQLError):
                        cursor.execute(sql)
        self.assertEqual(before, self.fixture.facts(self.config))
        write_json(self.fixture.root / 'restored-privilege-conservation.json', {'unchanged': True, 'before': before})

    def test_nonempty_restore_is_rejected_before_writes(self):
        before = self.fixture.facts(self.config)
        with self.assertRaisesRegex(OpsError, 'RESTORE_DATABASE_NOT_EMPTY'):
            empty_restore(self.fixture.manifest, self.config['database'],
                          self.fixture.root / 'synthetic-tasks-backup.sql', 'forbidden-overwrite')
        self.assertEqual(before, self.fixture.facts(self.config))
        self.assertFalse((self.fixture.root / 'forbidden-overwrite.stdout').exists())
        write_json(self.fixture.root / 'nonempty-restore-conservation.json', {'unchanged': True, 'before': before})


if __name__ == '__main__':
    unittest.main(verbosity=2)
