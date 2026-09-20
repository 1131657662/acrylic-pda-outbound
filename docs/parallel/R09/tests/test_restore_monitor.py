"""A stale or unrelated PASS report cannot certify a different backup."""
from datetime import datetime, timezone, timedelta
import time
import json
import unittest
from backup_factory import backup_fixture, test_directory, seal
from safety import write_json, sha, OpsError
from restore_monitor import evidence, latest_evidence


class RestoreMonitorTests(unittest.TestCase):
    def setUp(self):
        root = test_directory()
        self.backup = backup_fixture(root)
        (self.backup / 'manifest.json').write_text(json.dumps({'base_commit': 'a' * 40, 'schema': '32'}), encoding='utf-8')
        seal(self.backup)
        self.report = root / 'verification.json'
        self.value = {'status': 'PASS', 'scope': 'isolated snapshot restore; no disaster reconstruction',
            'backup_sums_sha256': sha(self.backup / 'SHA256SUMS'), 'business': {}, 'tables': 0,
            'verified_at': datetime.now(timezone.utc).isoformat()}
        self.config = dict(backup=str(self.backup), report=str(self.report), source_commit='a' * 40,
                           schema='32', max_age_seconds=3600)

    def check(self):
        self.report.write_text(json.dumps(self.value), encoding='utf-8')
        return evidence(self.config, time.time())

    def test_valid_report_does_not_authorize_deletion_or_reconstruction(self):
        result = self.check()
        self.assertTrue(result['restore_verified'])
        self.assertFalse(result['disaster_reconstruction_verified'])
        self.assertFalse(result['local_deletion_allowed'])

    def test_unrelated_backup_or_baseline_is_rejected(self):
        self.value['backup_sums_sha256'] = '0' * 64
        with self.assertRaisesRegex(OpsError, 'RESTORE_BACKUP_MISMATCH'):
            self.check()
        self.value['backup_sums_sha256'] = sha(self.backup / 'SHA256SUMS')
        self.config['schema'] = '33'
        with self.assertRaisesRegex(OpsError, 'RESTORE_BASELINE_MISMATCH'):
            self.check()

    def test_changed_business_summary_and_failed_report_are_rejected(self):
        self.value['business'] = {'different': True}
        with self.assertRaisesRegex(OpsError, 'RESTORE_BUSINESS_MISMATCH'):
            self.check()
        self.value['status'] = 'FAIL'
        with self.assertRaisesRegex(OpsError, 'RESTORE_NOT_VERIFIED'):
            self.check()

    def test_future_report_is_rejected(self):
        self.value['verified_at'] = (datetime.now(timezone.utc) + timedelta(hours=1)).isoformat()
        with self.assertRaisesRegex(OpsError, 'RESTORE_CLOCK_INVALID'):
            self.check()

    def test_report_freshness_is_enforced(self):
        write_json(self.report, self.value)
        with self.assertRaisesRegex(OpsError, 'RESTORE_EVIDENCE_STALE'):
            evidence(self.config, time.time() + 3601)

    def test_older_restore_report_cannot_cover_newer_backup(self):
        (self.backup.parent / '20990920T000000Z-new').mkdir()
        config = {'backup': {'directory': str(self.backup.parent)}, 'restore_evidence': self.config}
        with self.assertRaisesRegex(OpsError, 'LATEST_BACKUP_NOT_RESTORE_VERIFIED'):
            latest_evidence(config, time.time())
