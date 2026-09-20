"""Archive failure paths must be rejected before any restore mutation."""
import io
import json
import tarfile
import unittest
from backup_factory import backup_fixture, test_directory, seal
from archive import extract, verify_archive
from backup import verify, archive_inventory
from safety import OpsError, child


class ArchiveTests(unittest.TestCase):
    def setUp(self):
        self.root = test_directory()
        self.backup = backup_fixture(self.root)

    def test_complete_archive_extracts_identical_files(self):
        snapshot = verify(self.backup)
        destination = self.root / 'recovered'
        extract(self.backup / 'private-files-and-config.tar.gz', archive_inventory(snapshot), destination)
        self.assertEqual((destination / 'files/evidence.txt').read_bytes(), b'synthetic business evidence')

    def test_missing_complete_marker_is_not_success(self):
        (self.backup / 'COMPLETE.json').unlink()
        with self.assertRaises(OpsError):
            verify(self.backup)

    def test_changed_database_fails_checksum(self):
        with (self.backup / 'database.sql.gz').open('ab') as stream:
            stream.write(b'corruption')
        with self.assertRaisesRegex(OpsError, 'BACKUP_CHECKSUM_MISMATCH'):
            verify(self.backup)

    def test_truncated_gzip_with_resealed_hash_still_fails(self):
        path = self.backup / 'database.sql.gz'
        path.write_bytes(path.read_bytes()[:12])
        seal(self.backup)
        with self.assertRaises((EOFError, OSError)):
            verify(self.backup)

    def test_missing_component_fails_even_when_list_resealed(self):
        (self.backup / 'schema.sql').unlink()
        with self.assertRaises(OpsError):
            verify(self.backup)

    def test_changed_member_resealed_tar_fails_internal_digest(self):
        archive = self.backup / 'private-files-and-config.tar.gz'
        with tarfile.open(archive, 'w:gz') as stream:
            info = tarfile.TarInfo('files/evidence.txt')
            content = b'forged business evidence'
            info.size = len(content)
            stream.addfile(info, io.BytesIO(content))
        seal(self.backup)
        with self.assertRaisesRegex(OpsError, 'ARCHIVE_MEMBER_SIZE_MISMATCH|ARCHIVE_MEMBER_HASH_MISMATCH'):
            verify(self.backup)

    def test_archive_traversal_and_links_are_rejected(self):
        for name, kind in [('../escape', tarfile.REGTYPE), ('/absolute', tarfile.REGTYPE),
                           ('files/link', tarfile.SYMTYPE), ('files/hardlink', tarfile.LNKTYPE)]:
            with self.subTest(name=name):
                archive = self.root / 'hostile.tar.gz'
                with tarfile.open(archive, 'w:gz') as stream:
                    info = tarfile.TarInfo(name)
                    info.type, info.linkname = kind, '../../escape'
                    stream.addfile(info)
                with self.assertRaises(OpsError):
                    verify_archive(archive, {name: {'bytes': 0, 'sha256': '0' * 64}})

    def test_missing_signing_material_fails(self):
        path = self.backup / 'snapshot.json'
        snapshot = json.loads(path.read_text())
        del snapshot['trees']['config']['private.pk8']
        path.write_text(json.dumps(snapshot))
        seal(self.backup)
        with self.assertRaisesRegex(OpsError, 'SIGNING_CONFIGURATION_INCOMPLETE'):
            verify(self.backup)

    def test_existing_restore_directory_is_preserved(self):
        destination = self.root / 'recovered'
        destination.mkdir()
        sentinel = destination / 'keep.txt'
        sentinel.write_text('keep')
        with self.assertRaisesRegex(OpsError, 'RESTORE_FILES_ALREADY_EXIST'):
            extract(self.backup / 'private-files-and-config.tar.gz', {}, destination)
        self.assertEqual(sentinel.read_text(), 'keep')

    def test_path_escape_and_windows_drive_are_refused(self):
        for name in ('../escape', 'C:/escape', '/escape', 'files\\escape'):
            with self.subTest(name=name), self.assertRaises(OpsError):
                child(self.root, name)


if __name__ == '__main__':
    unittest.main()
