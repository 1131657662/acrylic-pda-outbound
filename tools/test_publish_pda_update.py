"""Publication safety checks use generated test keys and synthetic bytes only."""
import base64
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec


class PublishUpdateTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.key = ec.generate_private_key(ec.SECP256R1())
        (self.root / "key.pem").write_bytes(self.key.private_bytes(
            serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8, serialization.NoEncryption()))
        (self.root / "input.apk").write_bytes(b"synthetic-apk-bytes")

    def publish(self, release="test-1", version=12, extra=()):
        return subprocess.run([sys.executable, str(Path(__file__).with_name("publish_pda_update.py")),
            "--apk", str(self.root / "input.apk"), "--channel", "staging", "--release-id", release,
            "--version-code", str(version), "--version-name", "test", "--certificate-sha256", "a" * 64,
            "--min-supported-version-code", "10", "--server-compatibility", "0.17.0",
            "--release-notes", "test", "--signing-key", str(self.root / "key.pem"),
            "--output-dir", str(self.root / "published"), *extra], capture_output=True)

    def test_manifest_signature_authenticates_published_bytes(self):
        self.assertEqual(0, self.publish().returncode)
        manifest = json.loads((self.root / "published/updates/staging/manifest.json").read_text())
        raw = base64.b64decode(manifest["payload"])
        self.key.public_key().verify(base64.b64decode(manifest["signature"]), raw, ec.ECDSA(hashes.SHA256()))
        self.assertEqual(12, json.loads(raw)["versionCode"])
        self.assertFalse(json.loads(raw)["firstInstallAllowed"])

    def test_existing_release_cannot_be_replaced(self):
        self.assertEqual(0, self.publish().returncode)
        (self.root / "input.apk").write_bytes(b"changed")
        self.assertNotEqual(0, self.publish(version=13).returncode)
        self.assertEqual(b"synthetic-apk-bytes", (self.root / "published/updates/staging/test-1.apk").read_bytes())

    def test_lower_version_does_not_replace_current_manifest(self):
        self.assertEqual(0, self.publish().returncode)
        path = self.root / "published/updates/staging/manifest.json"
        before = path.read_bytes()
        self.assertNotEqual(0, self.publish("test-2", 11).returncode)
        self.assertEqual(before, path.read_bytes())

    def test_first_install_requires_android_tools(self):
        result = self.publish(extra=("--first-install-allowed",))
        self.assertNotEqual(0, result.returncode)
        self.assertFalse((self.root / "published").exists())

    def test_debug_first_install_is_rejected_before_publication(self):
        result = self.publish(extra=("--channel", "debug", "--first-install-allowed"))
        self.assertNotEqual(0, result.returncode)
        self.assertFalse((self.root / "published").exists())


if __name__ == "__main__":
    unittest.main()
