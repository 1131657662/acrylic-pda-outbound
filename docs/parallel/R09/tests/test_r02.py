"""Existing R02 format remains readable without altering the deployed script."""
import gzip
import io
import json
import tarfile
import unittest
from backup_factory import test_directory
from r02_audit import verify_r02, NAMES, REQUIRED, CONFIG, FILES
from safety import sha, OpsError


class LegacyTests(unittest.TestCase):
    def setUp(self):
        self.root = test_directory() / '20260918T010000Z'
        self.root.mkdir()
        with gzip.open(self.root / 'database.sql.gz', 'wb') as stream:
            stream.write(b'-- synthetic legacy SQL')
        (self.root / 'database-facts.json').write_text(json.dumps({'example': {'count': 1, 'checksum': '12'}}))
        (self.root / 'release.txt').write_text('/opt/acrylic-outbound-test/releases/synthetic')
        (self.root / 'manifest.json').write_text('{}')
        self.archive(REQUIRED)

    def archive(self, configs):
        with tarfile.open(self.root / 'private-files-and-config.tar.gz', 'w:gz') as stream:
            for name in [CONFIG + item for item in configs] + [FILES + 'synthetic.txt']:
                info = tarfile.TarInfo(name)
                info.size = 9
                stream.addfile(info, io.BytesIO(b'synthetic'))
        sums = ''.join(f'{sha(self.root / name)}  {name}\n' for name in sorted(NAMES))
        (self.root / 'SHA256SUMS').write_text(sums)

    def test_legacy_archive_passes_with_explicit_restore_limit(self):
        result = verify_r02(self.root)
        self.assertEqual(result['tables'], 1)
        self.assertIn('actual restore remains separate', result['scope'])

    def test_missing_legacy_signing_key_fails(self):
        self.archive(REQUIRED - {'signing.pk8'})
        with self.assertRaisesRegex(OpsError, 'SIGNING_CONFIGURATION_INCOMPLETE'):
            verify_r02(self.root)

    def test_failed_legacy_dump_cannot_pass_with_partial_checksums(self):
        (self.root / 'SHA256SUMS').write_text('')
        with self.assertRaisesRegex(OpsError, 'BACKUP_COMPONENT_MISSING'):
            verify_r02(self.root)

    def checkpoint(self, text):
        path = self.root / 'active-services.txt'
        path.write_text(text, encoding='ascii')
        with (self.root / 'SHA256SUMS').open('a') as stream:
            stream.write(f'{sha(path)}  active-services.txt\n')

    def test_worker_checkpoint_format_and_empty_checkpoint(self):
        self.checkpoint('acrylic-outbound-test.service\nacrylic-outbound-test-worker.service\n')
        self.assertEqual(verify_r02(self.root)['writer_checkpoint'], 'api-worker-checkpoint')
        for empty in ('', '\n'):
            self.archive(REQUIRED)
            self.checkpoint(empty)
            self.assertEqual(verify_r02(self.root)['writer_checkpoint'], 'api-worker-checkpoint')

    def test_unknown_service_and_unchecksummed_checkpoint_fail(self):
        self.checkpoint('mysql.service\n')
        with self.assertRaisesRegex(OpsError, 'INVALID_SERVICE_CHECKPOINT'):
            verify_r02(self.root)
        self.archive(REQUIRED)
        with self.assertRaisesRegex(OpsError, 'UNCHECKSUMMED_SERVICE_CHECKPOINT'):
            verify_r02(self.root)


if __name__ == '__main__':
    unittest.main()
