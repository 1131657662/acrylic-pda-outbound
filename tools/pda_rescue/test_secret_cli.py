"""Exercise the actual offline command against Android-produced files."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from contextlib import redirect_stderr, redirect_stdout
import io
from unittest.mock import patch


class SecretCliTest(unittest.TestCase):
    def setUp(self):
        self.vector = Path(os.environ["PDA_RESCUE_SECRET_VECTOR_DIR"])
        self.package = self.vector / "android-room.pdarescue"
        self.trust = self.vector / "external-trust.json"
        self.material = self.vector / "package-secret.json"
        self.before = {p.name: hashlib.sha256(p.read_bytes()).hexdigest()
                       for p in (self.package, self.trust, self.material)}
        self.temp = tempfile.TemporaryDirectory(prefix="pda-secret-cli-")
        self.addCleanup(self.temp.cleanup)
        self.directory = Path(self.temp.name)

    def tearDown(self):
        self.assertEqual(self.before, {p.name: hashlib.sha256(p.read_bytes()).hexdigest()
                                      for p in (self.package, self.trust, self.material)})

    def invoke(self, material: Path | None = None, extra=(), trusted=True):
        args = [sys.executable, "-m", "tools.pda_rescue.cli", str(self.package)]
        if trusted:
            args += ["--trust", str(self.trust), "--require-trusted"]
        if material:
            args += ["--secret-file", str(material)]
        return subprocess.run(args + list(extra), capture_output=True, text=True, encoding="utf-8", timeout=60, check=False)

    def changed_material(self, patch):
        bad = self.directory / "changed-material.json"
        value = json.loads(self.material.read_text(encoding="utf-8"))
        bad.write_text(json.dumps(value | patch, ensure_ascii=False), encoding="utf-8")
        return bad

    def assert_rejected(self, result):
        self.assertEqual(1, result.returncode)
        self.assertEqual("", result.stdout)
        secret = json.loads(self.material.read_text(encoding="utf-8"))["packagePassword"]
        self.assertNotIn(secret, result.stderr)
        self.assertNotIn("Traceback", result.stderr)

    def test_android_room_package_is_verified_without_logging_secret_or_writing_copy(self):
        result = self.invoke(self.material)
        self.assertEqual(0, result.returncode, result.stderr)
        report = json.loads(result.stdout)
        self.assertTrue(report["integrityVerified"])
        self.assertTrue(report["sourceTrusted"])
        self.assertFalse(report["businessReceiptVerified"])
        secret = json.loads(self.material.read_text(encoding="utf-8"))["packagePassword"]
        self.assertNotIn(secret, result.stdout + result.stderr)
        self.assertGreater(int(report["summary"]["scans"]), 0)

    def test_invalid_material_fails_without_success_output(self):
        bad = self.directory / "invalid-secret.json"
        bad.write_text(json.dumps({"format": "wrong"}), encoding="utf-8")
        result = self.invoke(bad)
        self.assert_rejected(result)
        self.assertNotIn("packagePassword", result.stderr)

    def test_wrong_order_digest_does_not_publish_any_output(self):
        destination = self.directory / "verified"
        result = self.invoke(self.changed_material({"orderSha256": "0" * 64}), ["--out", str(destination)])
        self.assert_rejected(result)
        self.assertFalse(destination.exists())
        self.assertEqual(["changed-material.json"], [p.name for p in self.directory.iterdir()])

    def test_each_scope_mismatch_fails_without_success_output(self):
        for name in ("deviceId", "operatorId", "warehouseId", "source", "localOrderId"):
            with self.subTest(name=name):
                value = "TEMP" if name == "source" else "different-synthetic-scope"
                self.assert_rejected(self.invoke(self.changed_material({name: value})))

    def test_wrong_password_does_not_publish_any_output(self):
        destination = self.directory / "verified"
        self.assert_rejected(self.invoke(self.changed_material({"packagePassword": "0" * 32}),
                                         ["--out", str(destination)]))
        self.assertFalse(destination.exists())

    def test_material_does_not_supply_external_source_trust(self):
        result = self.invoke(self.material, trusted=False)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertFalse(json.loads(result.stdout)["sourceTrusted"])
        self.assert_rejected(self.invoke(self.material, ["--require-trusted"], trusted=False))

    def test_wrong_external_registration_still_fails(self):
        wrong = self.directory / "wrong-trust.json"
        wrong.write_text("{}", encoding="utf-8")
        self.assert_rejected(self.invoke(self.material, ["--trust", str(wrong)]))

    def test_verified_copy_excludes_material_and_preserves_original_order_bytes(self):
        destination = self.directory / "verified"
        result = self.invoke(self.material, ["--out", str(destination)])
        self.assertEqual(0, result.returncode, result.stderr)
        value = json.loads(self.material.read_text(encoding="utf-8"))
        self.assertEqual(value["orderSha256"], hashlib.sha256((destination / "order.json").read_bytes()).hexdigest())
        self.assertFalse((destination / self.material.name).exists())
        for path in destination.iterdir():
            self.assertNotIn(value["packagePassword"].encode(), path.read_bytes(), path.name)
        before = {p.name: p.read_bytes() for p in destination.iterdir()}
        self.assert_rejected(self.invoke(self.material, ["--out", str(destination)]))
        self.assertEqual(before, {p.name: p.read_bytes() for p in destination.iterdir()})

    def test_file_changed_during_verification_does_not_publish(self):
        from . import cli
        original_verify = cli.verify
        changed = self.changed_material({})
        output, errors = io.StringIO(), io.StringIO()
        destination = self.directory / "verified"

        def mutate_after_verify(*args):
            result = original_verify(*args)
            changed.write_bytes(changed.read_bytes() + b" ")
            return result

        argv = ["pda-rescue", str(self.package), "--secret-file", str(changed), "--out", str(destination)]
        with patch("sys.argv", argv), patch.object(cli, "verify", side_effect=mutate_after_verify), \
                redirect_stdout(output), redirect_stderr(errors), self.assertRaises(SystemExit) as stopped:
            cli.main()
        self.assertEqual(1, stopped.exception.code)
        self.assertEqual("", output.getvalue())
        self.assertFalse(destination.exists())

    def test_password_prompt_remains_available_without_secret_file(self):
        from . import cli
        output, errors = io.StringIO(), io.StringIO()
        with patch("sys.argv", ["pda-rescue", str(self.package), "--trust", str(self.trust), "--require-trusted"]), \
                patch("getpass.getpass", return_value=json.loads(self.material.read_text(encoding="utf-8"))["packagePassword"]) as prompt, \
                redirect_stdout(output), redirect_stderr(errors):
            cli.main()
        prompt.assert_called_once()
        self.assertTrue(json.loads(output.getvalue())["sourceTrusted"])
        self.assertEqual("", errors.getvalue())


if __name__ == "__main__":
    unittest.main()
