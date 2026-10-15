"""Task monitor rejects unsafe configuration before connecting to MySQL."""
import unittest
from pathlib import Path
import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'ops'))
from jobs_connection import validate
from jobs_probe import collect
from safety import OpsError


class JobsConfigTests(unittest.TestCase):
    def setUp(self):
        self.config = {'host': '127.0.0.1', 'port': 19343, 'datadir': str(Path.cwd()),
                       'database': 'monitor_fixture', 'current_user': 'probe@127.0.0.1',
                       'server_uuid': '00000000-0000-0000-0000-000000000000',
                       'credentials_file': str(Path.cwd() / 'unused')}

    def test_external_host_and_sql_identifier_inputs_are_refused(self):
        for key, value in [('host', 'example.com'), ('database', 'x;SELECT 1'),
                           ('current_user', 'root@localhost'), ('datadir', 'relative'),
                           ('port', True), ('server_uuid', 'unknown'), ('ssl', True),
                           ('credentials_file', 'relative.cnf')]:
            with self.subTest(key=key), self.assertRaises(OpsError):
                validate({**self.config, key: value})

    def test_bad_limits_are_rejected_before_credential_file_access(self):
        for limits in ({}, {'failed': -1}, {'failed': True}, {'failed': '0'}):
            with self.subTest(limits=limits), self.assertRaisesRegex(OpsError, 'JOB_LIMITS_INVALID'):
                collect({**self.config, 'limits': limits})


if __name__ == '__main__':
    unittest.main()
