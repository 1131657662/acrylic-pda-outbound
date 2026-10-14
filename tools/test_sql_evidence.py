"""Evidence survives external CLI failures without inventing successful facts."""
import hashlib
import json
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from tools.sql_evidence import SqlEvidence


class SqlEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.directory = Path(self.temporary.name) / 'evidence'
        self.reader = SqlEvidence({'directory': self.directory, 'mysql': 'mysql.exe',
                                   'config': 'private/client.cnf', 'database': 'isolated'})

    def test_success_preserves_binary_stdout(self):
        raw = b'\x00\xff\tV0010\r\n'
        result = subprocess.CompletedProcess([], 0, raw, b'warning\xff')
        with patch('tools.sql_evidence.subprocess.run', return_value=result) as launch:
            actual = self.reader.query('SELECT 1', 'warehouse')
        self.assertEqual(raw, actual)
        self.assertEqual(raw, (self.directory / '0001.stdout').read_bytes())
        outcome = json.loads((self.directory / '0001.result.json').read_text())
        self.assertEqual(hashlib.sha256(raw).hexdigest(), outcome['stdoutSha256'])
        self.assertEqual(subprocess.DEVNULL, launch.call_args.kwargs['stdin'])

    def test_nonzero_exit_preserves_stderr_before_raising(self):
        result = subprocess.CompletedProcess([], 1, b'partial', b'ERROR 2020\r\n')
        with patch('tools.sql_evidence.subprocess.run', return_value=result):
            with self.assertRaisesRegex(RuntimeError, 'warehouse.*exit=1.*0001'):
                self.reader.query('SELECT 1', 'warehouse')
        self.assertEqual(b'ERROR 2020\r\n', (self.directory / '0001.stderr').read_bytes())
        outcome = json.loads((self.directory / '0001.result.json').read_text())
        self.assertEqual(1, outcome['exitCode'])

    def test_timeout_preserves_partial_output(self):
        error = subprocess.TimeoutExpired(['mysql.exe'], 60, output=b'partial\x00', stderr=b'timeout')
        with patch('tools.sql_evidence.subprocess.run', side_effect=error):
            with self.assertRaisesRegex(RuntimeError, 'warehouse.*0001'):
                self.reader.query('SELECT 1', 'warehouse')
        self.assertEqual(b'partial\x00', (self.directory / '0001.stdout').read_bytes())
        outcome = json.loads((self.directory / '0001.result.json').read_text())
        self.assertIsNone(outcome['exitCode'])
        self.assertEqual('TimeoutExpired', outcome['errorType'])

    def test_launch_failure_preserves_request(self):
        with patch('tools.sql_evidence.subprocess.run', side_effect=FileNotFoundError('missing CLI')):
            with self.assertRaises(RuntimeError):
                self.reader.query('SELECT 1', 'warehouse')
        request = json.loads((self.directory / '0001.request.json').read_text())
        outcome = json.loads((self.directory / '0001.result.json').read_text())
        self.assertEqual('warehouse', request['table'])
        self.assertEqual('FileNotFoundError', outcome['errorType'])

    def test_existing_evidence_directory_is_never_overwritten(self):
        with self.assertRaises(FileExistsError):
            SqlEvidence({'directory': self.directory, 'mysql': 'mysql.exe',
                         'config': 'private/client.cnf', 'database': 'isolated'})


if __name__ == '__main__':
    unittest.main()
