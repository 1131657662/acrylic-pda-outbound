"""Tampering, rollback and path isolation remain closed; retain damaged files."""
import os
import shutil
import unittest
from uuid import uuid4
from control_factory import initialized, fixture, next_command, write
from control_paths import ControlLocation
from control_store import ControlStore
from control_format import canonical, decode
from safety import OpsError


class ControlIntegrityTests(unittest.TestCase):
    def test_restore_set_cannot_contain_or_be_inside_control_root(self):
        base, location, _ = fixture()
        for other in (base, location.root, location.root / 'nested'):
            with self.subTest(path=str(other)), self.assertRaisesRegex(OpsError, 'CONTROL_INSIDE_RESTORE_SET'):
                ControlStore(ControlLocation(location.root, location.installation_id, (other,)))
        self.assertFalse(location.root.exists())

    def test_configured_restore_set_change_is_refused(self):
        base, location, _, result = initialized()
        other = ControlLocation(location.root, location.installation_id, (base / 'another-business',))
        with self.assertRaisesRegex(OpsError, 'CONTROL_INSTALLATION_MISMATCH'):
            ControlStore(other).inspect(result['checkpoint'])

    def test_history_corruption_and_partial_file_fail_closed(self):
        for payload in (b'{', b'{}\n'):
            _, location, _, result = initialized()
            path = location.root / 'history' / '000000000000000001.json'
            path.write_bytes(payload)
            with self.subTest(payload=payload), self.assertRaises(OpsError):
                ControlStore(location).inspect(result['checkpoint'])
            self.assertEqual(payload, path.read_bytes())

    def test_pointer_rollback_never_auto_repairs_on_read(self):
        _, location, _, original = initialized()
        result = ControlStore(location).apply(next_command(original), original['checkpoint'])
        old_pointer = canonical(original['checkpoint'])
        (location.root / 'current.json').write_bytes(old_pointer)
        with self.assertRaisesRegex(OpsError, 'CONTROL_COMMAND_INCOMPLETE'):
            ControlStore(location).inspect(result['checkpoint'])
        self.assertEqual(old_pointer, (location.root / 'current.json').read_bytes())

    def test_restored_old_directory_fails_new_external_checkpoint(self):
        base, location, _, original = initialized()
        old = base / 'old-copy'
        shutil.copytree(location.root, old)
        latest = ControlStore(location).apply(next_command(original), original['checkpoint'])
        restored = ControlLocation(old, location.installation_id, location.restore_roots)
        with self.assertRaisesRegex(OpsError, 'CONTROL_CHECKPOINT_ROLLBACK'):
            ControlStore(restored).inspect(latest['checkpoint'])

    def test_invalid_pointer_and_unexpected_file_are_not_replaced(self):
        _, location, _, result = initialized()
        write(location.root / 'current.json', {**result['checkpoint'], 'recordSha256': 'f' * 64})
        with self.assertRaisesRegex(OpsError, 'CONTROL_CHECKPOINT_MISMATCH'):
            ControlStore(location).apply(next_command(result), result['checkpoint'])
        write(location.root / 'current.json', result['checkpoint'])
        (location.root / 'unknown.pending').write_bytes(b'original failed evidence')
        with self.assertRaisesRegex(OpsError, 'CONTROL_UNKNOWN_ARTIFACT'):
            ControlStore(location).inspect(result['checkpoint'])

    def test_missing_history_suffix_refused_by_current_pointer(self):
        _, location, _, original = initialized()
        result = ControlStore(location).apply(next_command(original), original['checkpoint'])
        row = location.root / 'history' / '000000000000000002.json'
        row.rename(location.root.parent / 'preserved-missing-row.json')
        with self.assertRaisesRegex(OpsError, 'CONTROL_CHECKPOINT_ROLLBACK'):
            ControlStore(location).inspect(result['checkpoint'])

    def test_hardlink_file_refused(self):
        base, location, _, result = initialized()
        os.link(location.root / 'current.json', base / 'alias.json')
        with self.assertRaisesRegex(OpsError, 'CONTROL_FILE_ALIAS'):
            ControlStore(location).inspect(result['checkpoint'])

    def test_duplicate_keys_noncanonical_json_and_nan_rejected(self):
        for data in (b'{"a":1,"a":2}\n', b'{ "a":1}\n', b'{"a":NaN}\n'):
            with self.subTest(data=data), self.assertRaises(OpsError):
                decode(data)

    def test_unknown_identity_version_and_boolean_are_refused(self):
        from control_disk import read
        for version in (True, 2):
            _, location, _, result = initialized()
            identity = read(location.root / 'identity.json')
            write(location.root / 'identity.json', {**identity, 'formatVersion': version})
            with self.subTest(version=version), self.assertRaisesRegex(OpsError, 'CONTROL_FORMAT_UNSUPPORTED'):
                ControlStore(location).inspect(result['checkpoint'])

    def test_windows_aliases_and_remote_paths_refused(self):
        from control_paths import checked
        base, _, _ = fixture()
        for name in ('NUL', 'CON.txt', 'trailing.', 'trailing ', 'file:stream'):
            with self.subTest(name=name), self.assertRaises(OpsError):
                checked(base / name)
        with self.assertRaises(OpsError):
            checked('\\\\remote-server\\share\\authority')

    def test_parent_junction_is_refused(self):
        import subprocess
        from control_paths import checked
        base, _, _ = fixture()
        target = base / 'target'
        target.mkdir()
        junction = base / 'junction'
        result = subprocess.run(['cmd', '/c', 'mklink', '/J', str(junction), str(target)],
                                capture_output=True, timeout=10)
        self.assertEqual(0, result.returncode)
        with self.assertRaisesRegex(OpsError, 'CONTROL_PATH_LINK'):
            checked(junction / 'authority')


if __name__ == '__main__':
    unittest.main()
