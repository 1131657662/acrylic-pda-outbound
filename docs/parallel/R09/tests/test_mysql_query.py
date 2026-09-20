"""Protocol boundary failures must not become successful empty query results."""
import json
import subprocess
from types import SimpleNamespace
import unittest
from unittest.mock import MagicMock, patch
import pymysql
from backup_factory import test_directory
from mysql_query import options, query
from mysql_io import Database
from safety import OpsError


class QueryTests(unittest.TestCase):
    def setUp(self):
        self.root = test_directory()
        self.db = SimpleNamespace(root=self.root, side='restore', port=19310, name='r09_ops_restore')
        (self.root / 'client.cnf').write_text('[client]\nuser=r09_restore\npassword=synthetic-secret\n', encoding='ascii')

    def test_connection_is_explicit_local_and_wont_enable_file_upload(self):
        value = options(self.db, 'client')
        self.assertEqual((value['host'], value['port'], value['database']), ('127.0.0.1', 19310, 'r09_ops_restore'))
        self.assertFalse(value['local_infile'])
        self.assertNotIn('read_default_file', value)

    def test_failure_is_redacted_and_never_retried(self):
        with patch('mysql_query.pymysql.connect', side_effect=pymysql.OperationalError(2003, 'synthetic-secret raw-sql')) as connect:
            with self.assertRaisesRegex(OpsError, 'MYSQL_QUERY_FAILED'):
                query(self.db, 'SELECT raw-sql', 'client')
            connect.assert_called_once()
        output = next(self.root.glob('driver-failure-*.json')).read_text()
        self.assertNotIn('synthetic-secret', output)
        self.assertNotIn('raw-sql', output)
        self.assertEqual(json.loads(output)['mysql_error_code'], 2003)

    def test_multiple_results_preserve_utf8_nulls_and_multiline_schema(self):
        connection = MagicMock()
        cursor = connection.__enter__.return_value.cursor.return_value.__enter__.return_value
        cursor.fetchall.side_effect = [[], [('表', 'CREATE\nTABLE', None)], [(2,)]]
        cursor.nextset.side_effect = [True, True, None]
        with patch('mysql_query.pymysql.connect', return_value=connection):
            self.assertEqual(query(self.db, 'synthetic query', 'client'), '表\tCREATE\nTABLE\tNULL\n2')

    def test_account_mismatch_fails_before_connection(self):
        (self.root / 'client.cnf').write_text('[client]\nuser=shared\npassword=synthetic-secret\n', encoding='ascii')
        with patch('mysql_query.pymysql.connect') as connect:
            with self.assertRaisesRegex(OpsError, 'DATABASE_ACCOUNT_MISMATCH'):
                query(self.db, 'SELECT 1;', 'client')
            connect.assert_not_called()

    def test_zero_exit_without_native_dump_output_fails_closed(self):
        db = Database({'root': str(self.root), 'mysql_bin': str(self.root)}, 'source')
        result = subprocess.CompletedProcess([], 0, stdout=b'', stderr=b'')
        with patch('mysql_io.subprocess.run', return_value=result):
            with self.assertRaisesRegex(OpsError, 'MYSQL_DUMP_EMPTY'):
                db.dump(self.root / 'empty.sql')
