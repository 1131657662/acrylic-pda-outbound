"""Legacy controls remain read-only; new lock files are immutable prerequisites."""
import shutil
import unittest
from control_factory import initialized, next_command, write
from control_disk import read
from control_java_process import inventory
from control_store import ControlStore
from safety import OpsError


class WriterProtocolTests(unittest.TestCase):
    def test_legacy_store_is_readable_but_never_upgraded_on_apply(self):
        base, location, initial, result = initialized()
        for name in ('writers.json', 'writers.lock', 'intents', 'admission.json'):
            shutil.move(location.root / name, base / name)
        before = inventory(location.root)
        self.assertEqual(result, ControlStore(location).inspect(result['checkpoint']))
        for operation in (lambda: ControlStore(location).initialize(initial),
                          lambda: ControlStore(location).apply(next_command(result), result['checkpoint'])):
            with self.assertRaisesRegex(OpsError, 'CONTROL_WRITER_PROTOCOL_REQUIRED'):
                operation()
        self.assertEqual(before, inventory(location.root))

    def test_missing_half_protocol_fails_without_repair(self):
        for name in ('writers.json', 'writers.lock'):
            with self.subTest(missing=name):
                base, location, _, result = initialized()
                shutil.move(location.root / name, base / name)
                before = inventory(location.root)
                with self.assertRaisesRegex(OpsError, 'CONTROL_FILE_MISSING'):
                    ControlStore(location).apply(next_command(result), result['checkpoint'])
                self.assertEqual(before, inventory(location.root))

    def test_identity_hash_mismatch_preserves_all_originals(self):
        _, location, _, result = initialized()
        path = location.root / 'writers.json'
        write(path, {**read(path), 'identitySha256': 'a' * 64})
        before = inventory(location.root)
        with self.assertRaisesRegex(OpsError, 'CONTROL_WRITER_PROTOCOL_MISMATCH'):
            ControlStore(location).apply(next_command(result), result['checkpoint'])
        self.assertEqual(before, inventory(location.root))

    def test_invalid_lock_size_preserves_all_originals(self):
        _, location, _, result = initialized()
        (location.root / 'writers.lock').write_bytes(b'00')
        before = inventory(location.root)
        with self.assertRaisesRegex(OpsError, 'CONTROL_WRITER_LOCK_INVALID'):
            ControlStore(location).apply(next_command(result), result['checkpoint'])
        self.assertEqual(before, inventory(location.root))
