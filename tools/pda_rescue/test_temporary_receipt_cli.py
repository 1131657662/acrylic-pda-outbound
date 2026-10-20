"""Exercise the TEMP file command against disposable synthetic originals."""
from contextlib import redirect_stderr, redirect_stdout
import io
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

from . import temporary_receipt_cli as command
from .receipt_test_factory import ReceiptFactory, encode
from .temporary_receipt_test_factory import TemporaryReceiptFactory


class TemporaryReceiptCommandTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory(prefix='temporary-receipt-command-')
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        payload, response, context, key = TemporaryReceiptFactory().originals()
        for name, raw in [('payload', payload), ('response', response),
                          ('context', encode(context)), ('key', encode(key))]:
            (self.root / name).write_bytes(raw)
        self.args = [str(self.root / name) for name in ['payload', 'response']]
        self.args += ['--context', str(self.root / 'context'), '--receipt-key', str(self.root / 'key')]

    def invoke_process(self):
        environment = {**os.environ, 'PYTHONDONTWRITEBYTECODE': '1', 'PYTHONUTF8': '1'}
        return subprocess.run([sys.executable, '-m', 'tools.pda_rescue.temporary_receipt_cli', *self.args],
                              capture_output=True, text=True, encoding='utf-8', env=environment, timeout=15)

    def test_process_success_preserves_every_original_byte(self):
        before = {p.name: p.read_bytes() for p in self.root.iterdir()}
        result = self.invoke_process()
        self.assertEqual((0, ''), (result.returncode, result.stderr))
        report = json.loads(result.stdout)
        self.assertTrue(report['businessReceiptVerified'])
        for name in ['packageIntegrityVerified', 'currentBusinessStateVerified', 'releaseAllowed', 'localDeletionAllowed']:
            self.assertIs(report[name], False)
        self.assertEqual(before, {p.name: p.read_bytes() for p in self.root.iterdir()})

    def test_wrong_public_key_process_returns_no_success(self):
        (self.root / 'key').write_bytes(encode(ReceiptFactory().key))
        result = self.invoke_process()
        self.assertEqual((1, ''), (result.returncode, result.stdout))
        self.assertIn('InvalidSignature', result.stderr)

    def test_duplicate_external_context_properties_return_no_success(self):
        (self.root / 'context').write_bytes(b'{"submissionId":"first","submissionId":"second"}')
        result = self.invoke_process()
        self.assertEqual((1, ''), (result.returncode, result.stdout))
        self.assertIn('ValueError', result.stderr)

    def test_invalid_json_does_not_echo_raw_input(self):
        (self.root / 'response').write_bytes(b'synthetic-sensitive-marker-not-json')
        result = self.invoke_process()
        self.assertEqual((1, ''), (result.returncode, result.stdout))
        self.assertNotIn('synthetic-sensitive-marker', result.stderr)

    def test_oversized_response_is_rejected_before_parsing(self):
        with (self.root / 'response').open('r+b') as stream:
            stream.truncate(command.MAX_RESPONSE + 1)
        result = self.invoke_process()
        self.assertEqual((1, ''), (result.returncode, result.stdout))
        self.assertIn('ValueError', result.stderr)

    def test_changed_input_or_trust_returns_no_success(self):
        for name in ['payload', 'response', 'context', 'key']:
            with self.subTest(name=name):
                output, error = io.StringIO(), io.StringIO()
                original_read = command.original
                changed = self.root / name
                original_bytes = changed.read_bytes()
                def read_then_change(path, limit):
                    raw = original_read(path, limit)
                    if path == changed and raw == original_bytes:
                        path.write_bytes(raw + b' ')
                    return raw
                with patch('sys.argv', ['temporary_receipt_cli', *self.args]), \
                        patch.object(command, 'original', side_effect=read_then_change), \
                        redirect_stdout(output), redirect_stderr(error), self.assertRaises(SystemExit) as stopped:
                    command.main()
                self.assertEqual((1, ''), (stopped.exception.code, output.getvalue()))
                self.assertIn('ValueError', error.getvalue())
                changed.write_bytes(original_bytes)


if __name__ == '__main__':
    unittest.main()
