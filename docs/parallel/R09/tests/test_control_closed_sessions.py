"""Closed-session registrations coexist with Python control changes without granting writes."""
import os
import unittest
from control_factory import initialized, next_command, write
from control_store import ControlStore
from safety import OpsError


class ControlClosedSessionTests(unittest.TestCase):
    def registered(self):
        base, location, _, result = initialized()
        directory = location.root / 'closed-sessions'
        directory.mkdir()
        path = directory / (result['record']['recordSha256'] + '.json')
        value = {'formatVersion': 1, 'authorityRoot': str(base / 'external-session'),
                 'authoritySha256': 'c' * 64}
        write(path, value)
        return base, location, result, path, value

    def test_registration_survives_control_advance_with_all_capabilities_closed(self):
        _, location, original, path, _ = self.registered()
        before = path.read_bytes()
        result = ControlStore(location).apply(next_command(original), original['checkpoint'])
        self.assertEqual('RECOVERING', result['record']['command']['phase'])
        self.assertFalse(result['ordinaryWritesAllowed'])
        self.assertFalse(result['localDeletionAllowed'])
        self.assertEqual(before, path.read_bytes())

    def test_unknown_registration_name_is_preserved_and_refused(self):
        _, location, result, path, _ = self.registered()
        path.rename(path.with_name('unknown.json'))
        with self.assertRaisesRegex(OpsError, 'CONTROL_CLOSED_SESSION_LAYOUT'):
            ControlStore(location).inspect(result['checkpoint'])
        self.assertTrue(path.with_name('unknown.json').exists())

    def test_extra_fields_cannot_grant_permission(self):
        _, location, result, path, value = self.registered()
        write(path, {**value, 'ordinaryWritesAllowed': True})
        with self.assertRaisesRegex(OpsError, 'CONTROL_FIELDS'):
            ControlStore(location).inspect(result['checkpoint'])

    def test_zero_pin_relative_root_and_unknown_format_are_refused(self):
        for update, error in (({'authoritySha256': '0' * 64}, 'CONTROL_CLOSED_SESSION_LAYOUT'),
                              ({'authorityRoot': 'relative'}, 'CONTROL_CLOSED_SESSION_LAYOUT'),
                              ({'formatVersion': True}, 'CONTROL_FORMAT_UNSUPPORTED')):
            with self.subTest(update=update):
                _, location, result, path, value = self.registered()
                write(path, {**value, **update})
                with self.assertRaisesRegex(OpsError, error):
                    ControlStore(location).inspect(result['checkpoint'])

    def test_directory_replaced_with_file_is_refused(self):
        base, location, result, _, _ = self.registered()
        directory = location.root / 'closed-sessions'
        directory.rename(base / 'retained-sessions')
        directory.write_bytes(b'{}\n')
        with self.assertRaisesRegex(OpsError, 'CONTROL_CLOSED_SESSION_LAYOUT'):
            ControlStore(location).inspect(result['checkpoint'])

    def test_hardlinked_registration_is_refused(self):
        base, location, result, path, _ = self.registered()
        os.link(path, base / 'retained-alias.json')
        with self.assertRaisesRegex(OpsError, 'CONTROL_FILE_ALIAS'):
            ControlStore(location).inspect(result['checkpoint'])


if __name__ == '__main__':
    unittest.main()
