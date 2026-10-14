"""Reject missing CLI output and compare every schema/row fact precisely."""
import hashlib
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from tools.exact_database_facts import conserved, table_facts
from tools.sql_evidence import SqlEvidence


class ExactFactsTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.directory = Path(self.temporary.name)
        self.reader = SqlEvidence({'directory': self.directory / 'sql', 'mysql': 'mysql.exe',
                                   'config': 'private/client.cnf', 'database': 'isolated'})

    def outputs(self, *values):
        return patch('tools.sql_evidence.subprocess.run',
                     side_effect=[subprocess.CompletedProcess([], 0, value, b'') for value in values])

    def test_binary_null_empty_and_duplicate_rows_remain_distinct(self):
        raw = b'N\tV\r\nV00FF\tV41\r\nV00FF\tV41\r\n'
        with self.outputs(b'id\tvarbinary\nvalue\ttext\n', raw, b'3\n'):
            facts = table_facts(self.reader, 'warehouse', self.directory)
        expected = b'N\tV\nV00FF\tV41\nV00FF\tV41'
        self.assertEqual(3, facts['rows'])
        self.assertEqual(hashlib.sha256(expected).hexdigest(), facts['sha256'])
        self.assertEqual(raw, (self.directory / 'warehouse.hex.tsv').read_bytes())

    def test_empty_show_columns_fails_with_table_and_evidence(self):
        with self.outputs(b''):
            with self.assertRaisesRegex(ValueError, 'warehouse.*evidence='):
                table_facts(self.reader, 'warehouse', self.directory)
        self.assertEqual(b'', (self.directory / 'sql/0001.stdout').read_bytes())

    def test_empty_row_output_cannot_erase_nonempty_table(self):
        with self.outputs(b'id\tbinary\n', b'', b'2\n'):
            with self.assertRaisesRegex(ValueError, 'warehouse.*rows=0'):
                table_facts(self.reader, 'warehouse', self.directory)

    def test_malformed_hex_cannot_become_a_valid_fact(self):
        with self.outputs(b'id\tbinary\n', b'V0F0\n', b'1\n'):
            with self.assertRaisesRegex(ValueError, 'Invalid encoded row.*warehouse'):
                table_facts(self.reader, 'warehouse', self.directory)

    def test_business_column_change_cannot_hide_in_allowed_table(self):
        before = {'warehouse': {'rows': 2, 'columns': ['id'], 'sha256': 'a'}}
        after = {'warehouse': {'rows': 2, 'columns': ['id', 'code'], 'sha256': 'a'}}
        with self.assertRaisesRegex(ValueError, 'warehouse'):
            conserved(before, after, {'spring_session'})

    def test_added_table_requires_explicit_migration_comparison(self):
        with self.assertRaisesRegex(ValueError, 'added=.*physical'):
            conserved({'warehouse': {}}, {'warehouse': {}, 'physical': {}}, {'physical'})


if __name__ == '__main__':
    unittest.main()
