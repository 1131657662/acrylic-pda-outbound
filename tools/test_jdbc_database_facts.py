"""JDBC consumer refuses mismatched evidence and preserves failed JVM output."""
import json
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from tools.jdbc_database_facts import CapturedQueries, execute


class JdbcFactsTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.directory = Path(self.temporary.name)

    def options(self):
        driver = self.directory / 'driver.jar'
        driver.write_bytes(b'test external driver')
        return {'java': 'java.exe', 'temporary': self.directory, 'driver': driver,
                'config': self.directory / 'client.cnf', 'database': 'isolated',
                'port': 19332, 'datadir': self.directory / 'data'}

    def test_different_query_cannot_reuse_another_result(self):
        (self.directory / '0002.sql').write_text('SHOW COLUMNS FROM `wrong`')
        with self.assertRaisesRegex(ValueError, 'query mismatch.*warehouse'):
            CapturedQueries(self.directory).query('SELECT 1', 'warehouse')

    def test_different_table_cannot_reuse_same_query(self):
        (self.directory / '0002.sql').write_text('SELECT 1')
        (self.directory / '0002.table').write_text('wrong')
        with self.assertRaisesRegex(ValueError, 'table mismatch.*warehouse'):
            CapturedQueries(self.directory).query('SELECT 1', 'warehouse')

    def test_failed_jvm_retains_stderr_and_exit_code(self):
        options = self.options()
        failure = subprocess.CompletedProcess([], 1, b'partial\x00', b'Wrong isolated identity')
        with patch('tools.jdbc_database_facts.subprocess.run', return_value=failure):
            with self.assertRaisesRegex(RuntimeError, 'JDBC exit=1'):
                execute(options, self.directory)
        self.assertEqual(b'Wrong isolated identity', (self.directory / 'process.stderr').read_bytes())
        self.assertEqual(1, json.loads((self.directory / 'process.result.json').read_text())['exitCode'])

    def test_timed_out_jvm_does_not_invent_exit_code(self):
        options = self.options()
        error = subprocess.TimeoutExpired(['java.exe'], 120, output=b'partial', stderr=b'timed out')
        with patch('tools.jdbc_database_facts.subprocess.run', side_effect=error):
            with self.assertRaisesRegex(RuntimeError, 'JDBC execution failed'):
                execute(options, self.directory)
        self.assertEqual(b'partial', (self.directory / 'process.stdout').read_bytes())
        self.assertIsNone(json.loads((self.directory / 'process.result.json').read_text())['exitCode'])


if __name__ == '__main__':
    unittest.main()
