"""APK verification tests mock Android tool processes at the external boundary."""
from argparse import Namespace
from pathlib import Path
import subprocess
import unittest
from unittest.mock import patch
from verify_pda_release_apk import parse_metadata, verify_first_install


def metadata(**overrides):
    values = {"package": "com.acrylic.outbound.pda.staging", "code": 20, "name": "test", "api": 28, **overrides}
    return (f"package: name='{values['package']}' versionCode='{values['code']}' versionName='{values['name']}'\n"
            f"sdkVersion:'{values['api']}'\n")


def signer(certificate="a" * 64):
    return f"Signer #1 certificate SHA-256 digest: {certificate}\n"


class VerifyApkTest(unittest.TestCase):
    def setUp(self):
        self.args = Namespace(apk=Path("synthetic.apk"), android_build_tools=Path("tools"), version_code=20,
                              version_name="test", certificate_sha256="a" * 64)

    def run_verification(self, badging, certificates):
        results = [subprocess.CompletedProcess([], 0, stdout=text) for text in (badging, certificates)]
        with patch("verify_pda_release_apk.subprocess.run", side_effect=results):
            verify_first_install(self.args, "com.acrylic.outbound.pda.staging")

    def test_matching_metadata_and_certificate_are_accepted(self):
        self.run_verification(metadata(), signer())

    def test_each_metadata_mismatch_is_rejected(self):
        for field, value in (("package", "com.other.app"), ("code", 19), ("name", "wrong"), ("api", 29)):
            with self.subTest(field=field), self.assertRaises(SystemExit):
                self.run_verification(metadata(**{field: value}), signer())

    def test_certificate_mismatch_is_rejected(self):
        with self.assertRaises(SystemExit):
            self.run_verification(metadata(), signer("b" * 64))

    def test_missing_or_multiple_signers_are_rejected(self):
        for certificates in ("", signer() + signer().replace("#1", "#2")):
            with self.subTest(certificates=certificates), self.assertRaises(ValueError):
                parse_metadata(metadata(), certificates)

    def test_unsigned_or_corrupt_apk_tool_failure_is_rejected(self):
        with patch("verify_pda_release_apk.subprocess.run", side_effect=subprocess.CalledProcessError(1, "apksigner")):
            with self.assertRaises(SystemExit):
                verify_first_install(self.args, "com.acrylic.outbound.pda.staging")


if __name__ == "__main__":
    unittest.main()
