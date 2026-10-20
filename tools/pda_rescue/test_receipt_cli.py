"""Exercise the opt-in command without services or real credentials."""
from contextlib import redirect_stderr, redirect_stdout
import hashlib
import io
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from cryptography.hazmat.primitives.ciphers.aead import AESGCM

from .envelope import MAGIC
from .receipt_cli import main
from .receipt_test_factory import ReceiptFactory, encode


class ReceiptCommandTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory(prefix='receipt-command-')
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.factory = ReceiptFactory()
        plain, trust = self.factory.package()
        self.password = 'synthetic-command-password-only'
        salt, nonce = os.urandom(16), os.urandom(12)
        header = MAGIC + salt + nonce
        key = hashlib.pbkdf2_hmac('sha256', self.password.encode(), salt, 600_000, 32)
        (self.root / 'package').write_bytes(header + AESGCM(key).encrypt(nonce, plain, header))
        (self.root / 'trust.json').write_bytes(encode(trust))
        (self.root / 'key.json').write_bytes(encode(self.factory.key))

    def invoke(self, password=None):
        output, errors = io.StringIO(), io.StringIO()
        args = ['receipt_cli', str(self.root / 'package'), '--trust', str(self.root / 'trust.json'),
                '--receipt-key', str(self.root / 'key.json')]
        with patch('sys.argv', args), patch('getpass.getpass', return_value=password or self.password), \
                redirect_stdout(output), redirect_stderr(errors):
            try:
                main()
                return 0, output.getvalue(), errors.getvalue()
            except SystemExit as error:
                return error.code, output.getvalue(), errors.getvalue()

    def test_success_reports_history_without_changing_any_input_file(self):
        before = {p.name: p.read_bytes() for p in self.root.iterdir()}
        code, output, error = self.invoke()
        report = json.loads(output)
        self.assertEqual((0, ''), (code, error))
        self.assertTrue(report['businessReceiptVerified'])
        self.assertFalse(report['currentBusinessStateVerified'])
        self.assertEqual(before, {p.name: p.read_bytes() for p in self.root.iterdir()})
        self.assertNotIn(self.password, output)

    def test_wrong_password_produces_no_success_report(self):
        code, output, error = self.invoke('wrong-synthetic-password')
        self.assertEqual((1, ''), (code, output))
        self.assertIn('InvalidTag', error)
        self.assertNotIn('wrong-synthetic-password', error)

    def test_wrong_key_produces_no_success_report(self):
        (self.root / 'key.json').write_bytes(encode(ReceiptFactory().key))
        code, output, error = self.invoke()
        self.assertEqual((1, ''), (code, output))
        self.assertIn('InvalidSignature', error)

    def test_duplicate_trust_properties_produce_no_success_report(self):
        (self.root / 'key.json').write_bytes(b'{"algorithm":"RS256","algorithm":"none"}')
        code, output, error = self.invoke()
        self.assertEqual((1, ''), (code, output))
        self.assertIn('ValueError', error)

    def test_original_change_during_verification_produces_no_success_report(self):
        from .envelope import decrypt
        original_decrypt = decrypt
        def change_after_decrypt(path, password):
            plain = original_decrypt(path, password)
            path.write_bytes(path.read_bytes() + b' ')
            return plain
        with patch('tools.pda_rescue.receipt_cli.decrypt', side_effect=change_after_decrypt):
            code, output, error = self.invoke()
        self.assertEqual((1, ''), (code, output))
        self.assertIn('ValueError', error)


if __name__ == '__main__':
    unittest.main()
