"""ACL helper diagnostics must fail closed even if the child prints PRIVATE."""
import os
import subprocess
import unittest
from unittest.mock import patch
from backup_factory import test_directory
from jobs_credentials import private_file
from safety import OpsError


@unittest.skipUnless(os.name == 'nt', 'Windows ACL subprocess boundary')
class JobsCredentialTests(unittest.TestCase):
    def setUp(self):
        self.file = test_directory() / 'credentials.cnf'
        self.file.write_text('[client]\nuser=synthetic\npassword=not-used\n')

    def test_acl_child_stderr_refuses_even_with_zero_exit_and_private_stdout(self):
        child = subprocess.CompletedProcess([], 0, stdout='PRIVATE\n', stderr='CANARY_ACL_FAILURE')
        with patch('jobs_credentials.subprocess.run', return_value=child):
            with self.assertRaisesRegex(OpsError, '^JOB_CREDENTIAL_FILE_NOT_PRIVATE$'):
                private_file(self.file)

    def test_acl_child_environment_omits_incompatible_powershell_module_path(self):
        child = subprocess.CompletedProcess([], 0, stdout='PRIVATE\n', stderr='')
        with patch.dict(os.environ, {'PSModulePath': 'incompatible'}):
            with patch('jobs_credentials.subprocess.run', return_value=child) as run:
                self.assertEqual(private_file(self.file), self.file)
        self.assertFalse(any(key.casefold() == 'psmodulepath' for key in run.call_args.kwargs['env']))


if __name__ == '__main__':
    unittest.main()
