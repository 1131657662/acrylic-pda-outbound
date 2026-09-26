"""Durable admission intent failures preserve the exact command and all original files."""
from pathlib import Path
import shutil
import subprocess
import sys
import unittest
from control_factory import initialized, next_command, write
from control_disk import read
from control_intents import inspect
from control_java_process import inventory
from control_store import ControlStore
from safety import OpsError


class ControlIntentTests(unittest.TestCase):
    def crashed(self, point):
        base, location, _, result = initialized()
        value = next_command(result)
        write(base / 'checkpoint.json', result['checkpoint'])
        write(base / 'next.json', value)
        write(base / 'process.json', {'root': str(location.root), 'installation': location.installation_id,
                                      'restore': [str(path) for path in location.restore_roots]})
        process = subprocess.run([sys.executable, str(Path(__file__).with_name('control_process.py')),
                                  str(base), point, 'next.json'], capture_output=True, timeout=20)
        (base / 'intent-crash.out').write_bytes(process.stdout)
        (base / 'intent-crash.err').write_bytes(process.stderr)
        self.assertEqual(73, process.returncode)
        return base, location, result, value

    def test_process_death_at_each_intent_boundary_resumes_original(self):
        for point in ('intent_appended', 'intent_head_staged', 'intent_head_committed'):
            with self.subTest(point=point):
                _, location, before, value = self.crashed(point)
                if point != 'intent_head_committed':
                    with self.assertRaisesRegex(OpsError, 'CONTROL_INTENT_INCOMPLETE'):
                        ControlStore(location).inspect(before['checkpoint'])
                else:
                    self.assertEqual(before, ControlStore(location).inspect(before['checkpoint']))
                original = read(location.root / 'intents/000000000000000002.json')
                result = ControlStore(location).apply(value, before['checkpoint'])
                self.assertEqual(value, result['record']['command'])
                self.assertEqual('2', result['record']['revision'])
                self.assertEqual(original, read(location.root / 'intents/000000000000000002.json'))
                self.assertEqual(result, ControlStore(location).apply(value, before['checkpoint']))

    def test_pending_intent_cannot_be_replaced_by_another_command(self):
        _, location, before, value = self.crashed('intent_head_committed')
        original = inventory(location.root)
        for different in (next_command(before), {**value, 'actorId': 'different-actor'}):
            with self.assertRaisesRegex(OpsError, 'CONTROL_INTENT_CONFLICT'):
                ControlStore(location).apply(different, before['checkpoint'])
        self.assertEqual(original, inventory(location.root))

    def test_invalid_command_does_not_publish_a_stop_intent(self):
        _, location, _, before = initialized()
        original = inventory(location.root)
        for value in ({**next_command(before), 'phase': 'VERIFYING'}, {**next_command(before), 'expectedRevision': '0'}):
            with self.assertRaises(OpsError):
                ControlStore(location).apply(value, before['checkpoint'])
        self.assertEqual(original, inventory(location.root))

    def test_missing_intent_is_not_reconstructed_from_head(self):
        base, location, before, value = self.crashed('intent_head_committed')
        shutil.move(location.root / 'intents/000000000000000002.json', base / 'retained-intent.json')
        original = inventory(location.root)
        with self.assertRaisesRegex(OpsError, 'CONTROL_INTENT_HEAD_CONFLICT'):
            ControlStore(location).apply(value, before['checkpoint'])
        self.assertEqual(original, inventory(location.root))

    def test_completed_intent_must_match_committed_record(self):
        _, location, _, before = initialized()
        value = next_command(before)
        after = ControlStore(location).apply(value, before['checkpoint'])
        path = location.root / 'intents/000000000000000002.json'
        damaged = read(path)
        damaged['commandSha256'] = 'e' * 64
        write(path, damaged)
        with self.assertRaisesRegex(OpsError, 'CONTROL_INTENT_CORRUPT'):
            ControlStore(location).inspect(after['checkpoint'])

    def test_boolean_header_version_is_not_integer_one(self):
        _, location, _, before = initialized()
        path = location.root / 'admission.json'
        write(path, {**read(path), 'formatVersion': True})
        with self.assertRaisesRegex(OpsError, 'CONTROL_FORMAT_UNSUPPORTED'):
            ControlStore(location).inspect(before['checkpoint'])

    def test_version_one_protocol_is_read_only_and_never_auto_upgraded(self):
        base, location, initial, before = initialized()
        for name in ('intents', 'admission.json'):
            shutil.move(location.root / name, base / name)
        path = location.root / 'writers.json'
        write(path, {**read(path), 'formatVersion': 1})
        original = inventory(location.root)
        self.assertEqual(before, ControlStore(location).inspect(before['checkpoint']))
        with self.assertRaisesRegex(OpsError, 'CONTROL_ADMISSION_PROTOCOL_REQUIRED'):
            ControlStore(location).apply(next_command(before), before['checkpoint'])
        self.assertEqual(original, inventory(location.root))


if __name__ == '__main__':
    unittest.main()
