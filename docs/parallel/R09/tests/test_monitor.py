"""Monitor faults use local fixtures and explicit I/O boundary doubles."""
import json
import subprocess
import time
import unittest
from unittest.mock import patch
from backup_factory import backup_fixture, test_directory, seal
from monitor import freshness, disk, http, service, check
from safety import OpsError


class MonitorTests(unittest.TestCase):
    def setUp(self):
        self.root = test_directory()
        self.backup = backup_fixture(self.root)
        self.config = {'directory': str(self.backup.parent), 'max_age_seconds': 93600}

    def test_fresh_valid_backup_passes(self):
        self.assertTrue(freshness(self.config, time.time())['archive_verified'])

    def test_old_backup_fails(self):
        with self.assertRaisesRegex(OpsError, 'BACKUP_STALE'):
            freshness(self.config, time.time() + 100000)

    def test_future_backup_fails(self):
        with self.assertRaisesRegex(OpsError, 'BACKUP_CLOCK_INVALID'):
            freshness(self.config, time.time() - 1000)

    def test_new_partial_is_not_hidden_by_previous_good_backup(self):
        (self.backup.parent / '20990918T000000Z-001.partial').mkdir()
        with self.assertRaises(OpsError):
            freshness(self.config, time.time())

    def test_partial_with_complete_marker_still_fails(self):
        self.backup.rename(self.backup.with_name(self.backup.name + '.partial'))
        with self.assertRaisesRegex(OpsError, 'INCOMPLETE_BACKUP_REFUSED'):
            freshness(self.config, time.time())

    def test_missing_backup_fails(self):
        self.config['directory'] = str(self.root / 'absent')
        with self.assertRaisesRegex(OpsError, 'BACKUP_DIRECTORY_MISSING'):
            freshness(self.config, time.time())

    def test_disk_bytes_and_percentage_thresholds(self):
        for limits in [(10**30, 0), (0, 100)]:
            with self.subTest(limits=limits), self.assertRaises(OpsError):
                disk({'path': str(self.root), 'minimum_free_bytes': limits[0], 'minimum_free_percent': limits[1]})

    def test_external_http_and_credential_urls_rejected(self):
        for url in ('https://example.com/api/v1/auth/csrf', 'http://u:p@127.0.0.1/api/v1/auth/csrf'):
            with self.subTest(url=url), self.assertRaises(OpsError):
                http({'url': url})

    def test_service_failure_inactive_and_restart_storm(self):
        for output in ('ActiveState=active\nResult=exit-code\nNRestarts=0',
                       'ActiveState=inactive\nResult=success\nNRestarts=0',
                       'ActiveState=active\nResult=success\nNRestarts=8'):
            result = subprocess.CompletedProcess([], 0, stdout=output, stderr='')
            with self.subTest(output=output), patch('monitor.subprocess.run', return_value=result):
                with self.assertRaises(OpsError):
                    service({'unit': 'acrylic-outbound-test.service', 'max_restarts': 3})

    def test_probe_exception_does_not_expose_password_or_sql(self):
        def failed():
            raise RuntimeError('password=synthetic-secret SELECT sensitive-data')
        output = json.dumps(check('source', failed))
        self.assertNotIn('synthetic-secret', output)
        self.assertNotIn('sensitive-data', output)
        self.assertIn('PROBE_FAILED', output)

    def test_worker_inactive_is_not_treated_as_completed_oneshot(self):
        config = {'unit': 'acrylic-outbound-test-worker.service', 'max_restarts': 3}
        result = subprocess.CompletedProcess([], 0, stdout='ActiveState=inactive\nResult=success\nNRestarts=0', stderr='')
        with patch('monitor.subprocess.run', return_value=result):
            with self.assertRaisesRegex(OpsError, 'SERVICE_INACTIVE'):
                service(config)


if __name__ == '__main__':
    unittest.main()
