"""Validation tests for bounded Android secret material input."""
import hashlib
import json
import os
from pathlib import Path
import secrets
import subprocess
import tempfile
import unittest
import uuid

from .secret_file import CUSTODY, FORMAT, PURPOSE, MAX_BYTES, load


def create_windows_junction(link: Path, target: Path):
    if os.name != "nt":
        return None
    env = os.environ.copy()
    env["PDA_TEST_JUNCTION_LINK"] = str(link)
    env["PDA_TEST_JUNCTION_TARGET"] = str(target)
    script = "$ErrorActionPreference='Stop'; New-Item -ItemType Junction -Path $env:PDA_TEST_JUNCTION_LINK -Target $env:PDA_TEST_JUNCTION_TARGET | Out-Null"
    return subprocess.run(["powershell.exe", "-NoProfile", "-NonInteractive", "-Command", script],
                          env=env, capture_output=True, timeout=15, check=False)


def material(order: dict, raw_order: bytes) -> dict:
    return {"format": FORMAT, "purpose": PURPOSE, "packagePassword": secrets.token_urlsafe(24),
            "custody": CUSTODY, **{name: order[name] for name in
            ("source", "localOrderId", "deviceId", "operatorId", "warehouseId")},
            "orderSha256": hashlib.sha256(raw_order).hexdigest(), "materialId": str(uuid.uuid4())}


class SecretMaterialTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="pda-secret-material-")
        self.addCleanup(self.temp.cleanup)
        self.path = Path(self.temp.name) / "secret.json"
        self.order = {"source": "FORMAL", "localOrderId": "synthetic-order", "deviceId": "synthetic-device",
                      "operatorId": "synthetic-operator", "warehouseId": "synthetic-warehouse"}
        self.raw_order = json.dumps(self.order, separators=(",", ":")).encode()
        self.value = material(self.order, self.raw_order)
        self.path.write_bytes(json.dumps(self.value, ensure_ascii=False).encode())
        self.result = {"files": {"order.json": self.raw_order}, "facts": {"order": self.order}}

    def test_valid_material_binds_to_exact_order_bytes_and_scope(self):
        loaded = load(self.path)
        loaded.bind(self.result)
        self.assertEqual(self.value["packagePassword"], loaded.password)
        self.assertNotIn(loaded.password, repr(loaded))
        self.assertNotIn(str(self.path), repr(loaded))

    def test_duplicate_unknown_and_non_text_fields_are_rejected(self):
        for raw in (b'{"format":"one","format":"two"}',
                    json.dumps(self.value | {"unknown": "x"}).encode(),
                    json.dumps(self.value | {"deviceId": 42}).encode()):
            self.path.write_bytes(raw)
            with self.assertRaises(ValueError):
                load(self.path)

    def test_invalid_order_hash_or_changed_scope_is_rejected(self):
        self.path.write_bytes(json.dumps(self.value | {"orderSha256": "0" * 64}).encode())
        with self.assertRaises(ValueError):
            load(self.path).bind(self.result)
        self.path.write_bytes(json.dumps(self.value).encode())
        for name in self.order:
            with self.subTest(name=name):
                changed = {"files": self.result["files"], "facts": {"order": self.order | {name: "other"}}}
                with self.assertRaises(ValueError):
                    load(self.path).bind(changed)

    def test_oversize_material_is_rejected_before_parsing(self):
        for raw in (b"", b" " * (MAX_BYTES + 1)):
            self.path.write_bytes(raw)
            with self.assertRaises(ValueError):
                load(self.path)

    def test_invalid_utf8_bom_and_non_object_json_are_rejected(self):
        for raw in (b"\xff", b"\xef\xbb\xbf{}", b"[]", b"null", b"NaN"):
            self.path.write_bytes(raw)
            with self.assertRaises(ValueError):
                load(self.path)

    def test_duplicate_key_in_otherwise_valid_material_is_rejected(self):
        raw = json.dumps(self.value).encode()
        self.path.write_bytes(raw[:-1] + b',"source":"FORMAL"}')
        with self.assertRaises(ValueError):
            load(self.path)

    def test_missing_fields_are_rejected(self):
        for name in self.value:
            with self.subTest(name=name):
                self.path.write_bytes(json.dumps({k: v for k, v in self.value.items() if k != name}).encode())
                with self.assertRaises(ValueError):
                    load(self.path)

    def test_wrong_format_password_digest_and_identifier_are_rejected(self):
        cases = [("format", "wrong"), ("purpose", "wrong"), ("custody", "wrong"),
                 ("packagePassword", "x" * 31), ("packagePassword", "+" * 32),
                 ("orderSha256", "A" * 64), ("materialId", "invalid"),
                 ("materialId", str(uuid.uuid1())), ("materialId", str(uuid.uuid4()).upper()),
                 ("source", "UNKNOWN")]
        for name, value in cases:
            with self.subTest(name=name, value=value):
                self.path.write_bytes(json.dumps(self.value | {name: value}).encode())
                with self.assertRaises(ValueError):
                    load(self.path)

    def test_invalid_scope_text_is_rejected(self):
        for value in ("", " ", "x" * 129, "line\nbreak", "null\0byte", "\ud800"):
            with self.subTest(value=repr(value)):
                self.path.write_bytes(json.dumps(self.value | {"deviceId": value}).encode())
                with self.assertRaises(ValueError):
                    load(self.path)

    def test_reformatted_original_order_does_not_match_raw_digest(self):
        changed = self.result | {"files": {"order.json": json.dumps(self.order, indent=2).encode()}}
        with self.assertRaises(ValueError):
            load(self.path).bind(changed)

    def test_material_changed_after_load_is_rejected(self):
        loaded = load(self.path)
        self.path.write_bytes(json.dumps(self.value, indent=2).encode())
        with self.assertRaises(ValueError):
            loaded.bind(self.result)

    def test_material_removed_after_load_is_rejected(self):
        loaded = load(self.path)
        self.path.unlink()
        with self.assertRaises(OSError):
            loaded.bind(self.result)

    def test_directory_is_rejected(self):
        with self.assertRaises(ValueError):
            load(self.path.parent)

    def test_hard_link_is_rejected(self):
        linked = self.path.parent / "hard-link.json"
        os.link(self.path, linked)
        with self.assertRaises(ValueError):
            load(self.path)
        with self.assertRaises(ValueError):
            load(linked)

    def test_symbolic_link_is_rejected(self):
        linked = self.path.parent / "symbolic-link.json"
        try:
            linked.symlink_to(self.path)
        except OSError as error:
            self.skipTest(f"Symbolic link privilege unavailable: {type(error).__name__}")
        with self.assertRaises(ValueError):
            load(linked)

    def test_parent_link_is_rejected(self):
        linked = self.path.parent / "parent-link"
        try:
            linked.symlink_to(self.path.parent, target_is_directory=True)
        except OSError as error:
            self.skipTest(f"Symbolic link privilege unavailable: {type(error).__name__}")
        with self.assertRaises(ValueError):
            load(linked / self.path.name)

    def test_parent_junction_is_rejected(self):
        target = self.path.parent / "junction-target"
        target.mkdir()
        linked_file = target / self.path.name
        linked_file.write_bytes(self.path.read_bytes())
        linked = self.path.parent / "parent-junction"
        result = create_windows_junction(linked, target)
        if result is None:
            self.skipTest("Windows directory junction requires Windows")
        self.assertEqual(0, result.returncode, repr(result.stderr))
        try:
            with self.assertRaises(ValueError):
                load(linked / linked_file.name)
        finally:
            linked.rmdir()


if __name__ == "__main__":
    unittest.main()
