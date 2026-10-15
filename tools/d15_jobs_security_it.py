"""Real private-file, authentication and combined failure behavior on the retained instance."""
import os
from pathlib import Path
import subprocess
import unittest
from d15_jobs_test_support import Fixture


class JobsSecurityIT(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.fixture = Fixture(os.environ['D15_JOBS_MANIFEST'])

    def test_wrong_password_is_redacted_through_combined_monitor(self):
        config = self.fixture.case('wrongpass')
        path = self.fixture.root / 'private/wrongpass-invalid.cnf'
        path.write_text('[client]\nuser=d15_wrongpass\npassword=CANARY_BAD_PASSWORD\n')
        code, result = self.fixture.cli('wrongpass', {**config, 'credentials_file': str(path)}, monitor=True)
        jobs = next(item for item in result['checks'] if item['check'] == 'jobs')
        self.assertEqual((code, jobs['code']), (2, 'PROBE_FAILED'))

    def test_public_credential_file_is_refused(self):
        config = self.fixture.case('public')
        path = self.fixture.root / 'private/public.cnf'
        path.write_bytes(Path(config['credentials_file']).read_bytes())
        if os.name == 'nt':
            result = subprocess.run(['icacls', str(path), '/grant', '*S-1-1-0:R'], capture_output=True)
            self.assertEqual(result.returncode, 0)
        else:
            path.chmod(0o644)
        code, result = self.fixture.cli('public', {**config, 'credentials_file': str(path)})
        self.assertEqual((code, result['code']), (2, 'JOB_CREDENTIAL_FILE_NOT_PRIVATE'))

    def test_implicit_options_are_refused(self):
        config = self.fixture.case('options')
        path = self.fixture.root / 'private/options-invalid.cnf'
        path.write_text('[client]\nuser=d15_options\npassword=CANARY\nhost=example.com\n')
        code, result = self.fixture.cli('options', {**config, 'credentials_file': str(path)})
        self.assertEqual((code, result['code']), (2, 'JOB_CREDENTIAL_FORMAT_INVALID'))


if __name__ == '__main__':
    unittest.main(verbosity=2)
