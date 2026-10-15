"""Real MySQL and CLI task monitor acceptance; explicit fresh manifest is mandatory."""
import os
import unittest
from d15_jobs_fixture import admin
from d15_jobs_test_support import Fixture


class JobsProbeIT(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.fixture = Fixture(os.environ['D15_JOBS_MANIFEST'])

    def test_full_migrated_schema_nonempty_success_cli(self):
        config = self.fixture.base
        self.fixture.seed(config, 'SUCCEEDED')
        code, result = self.fixture.cli('healthy', config)
        self.assertEqual((code, result['status'], result['counts']['total']), (0, 'PASS', 1))

    def test_monitor_entry_reports_ready_backlog(self):
        config = self.fixture.case('backlog')
        self.fixture.seed(config, 'PENDING', 101)
        code, result = self.fixture.cli('backlog', config, monitor=True)
        jobs = next(item for item in result['checks'] if item['check'] == 'jobs')
        self.assertEqual((code, result['status'], jobs['counts']['ready']), (2, 'FAIL', 101))
        self.assertIn('ready', jobs['exceeded'])
        self.assertIn('oldest_ready_seconds', jobs['exceeded'])

    def test_expired_lease_failed_and_retry_are_visible(self):
        config = self.fixture.case('faults')
        for status in ('RUNNING', 'FAILED', 'RETRY'):
            self.fixture.seed(config, status)
        code, result = self.fixture.cli('faults', config)
        self.assertEqual(code, 2)
        self.assertEqual(result['counts']['expired_leases'], 1)
        self.assertEqual(result['counts']['failed'], 1)
        self.assertEqual(result['counts']['retry'], 1)
        self.assertIn('expired_leases', result['exceeded'])

    def test_retry_threshold_is_enforced(self):
        config = self.fixture.case('retry')
        self.fixture.seed(config, 'RETRY', 21)
        code, result = self.fixture.cli('retry', config)
        self.assertEqual((code, result['counts']['retry']), (2, 21))
        self.assertIn('retry', result['exceeded'])

    def test_healthy_jobs_integrate_with_backup_and_disk(self):
        config = self.fixture.case('integrated')
        self.fixture.seed(config, 'SUCCEEDED')
        code, result = self.fixture.cli('integrated', config, monitor=True)
        self.assertEqual((code, result['status']), (0, 'PASS'))
        self.assertEqual({item['check'] for item in result['checks']}, {'backup', 'disk_0', 'jobs'})

    def test_identity_mismatch_is_refused(self):
        config = self.fixture.case('identity')
        for field, value in [('datadir', str(self.fixture.root / 'wrong')), ('server_uuid', '0' * 8 + '-0000-0000-0000-' + '0' * 12),
                             ('current_user', 'different@127.0.0.1')]:
            with self.subTest(field=field):
                code, result = self.fixture.cli('identity-' + field, {**config, field: value})
                self.assertEqual(code, 2)
                self.assertNotEqual(result.get('status'), 'PASS')

    def test_database_mismatch_is_refused(self):
        config = self.fixture.case('wrongdb')
        bad = {**config, 'database': self.fixture.base['database']}
        code, result = self.fixture.cli('wrongdb', bad)
        self.assertEqual((code, result['code']), (2, 'OPERATION_FAILED'))

    def test_broad_read_account_and_no_grant_account_are_refused(self):
        for user in ('d15_broad', 'd15_denied'):
            with self.subTest(user=user):
                config = {**self.fixture.base, 'current_user': user + '@127.0.0.1',
                          'credentials_file': str(self.fixture.root / ('private/' + user + '.cnf'))}
                code, result = self.fixture.cli(user, config)
                expected = 'JOB_SELECT_ONLY_ACCOUNT_REQUIRED' if user == 'd15_broad' else 'OPERATION_FAILED'
                self.assertEqual((code, result['code']), (2, expected))

    def test_missing_table_does_not_turn_into_zero(self):
        config = self.fixture.case('missing', missing=True)
        code, result = self.fixture.cli('missing', config)
        self.assertEqual((code, result['code']), (2, 'JOB_SELECT_ONLY_ACCOUNT_REQUIRED'))

    def test_wrong_column_type_is_refused(self):
        config = self.fixture.case('shape', wrong_shape=True)
        code, result = self.fixture.cli('shape', config)
        self.assertEqual((code, result['code']), (2, 'JOB_SCHEMA_UNSUPPORTED'))

    def test_failed_migration_is_refused(self):
        config = self.fixture.case('history')
        with admin(self.fixture.manifest) as connection, connection.cursor() as cursor:
            cursor.execute(f'UPDATE `{config["database"]}`.flyway_schema_history SET success=0 WHERE installed_rank=1')
        code, result = self.fixture.cli('history', config)
        self.assertEqual((code, result['code']), (2, 'JOB_MIGRATION_HISTORY_UNHEALTHY'))

    def test_column_account_cannot_read_payload_or_write(self):
        import pymysql
        from jobs_credentials import credentials
        config = self.fixture.case('privileges')
        account = credentials(config['credentials_file'])
        with pymysql.connect(host='127.0.0.1', port=config['port'], database=config['database'],
                             **account, autocommit=True) as connection, connection.cursor() as cursor:
            for sql in ('SELECT payload FROM background_job', 'DELETE FROM background_job',
                        'UPDATE background_job SET status=\'FAILED\'', 'SELECT 1; SELECT 2'):
                with self.subTest(sql=sql), self.assertRaises(pymysql.MySQLError):
                    cursor.execute(sql)


if __name__ == '__main__':
    unittest.main(verbosity=2)
