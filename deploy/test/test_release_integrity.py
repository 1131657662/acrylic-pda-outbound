"""Reject source/manifest drift and runtime tampering using boundary package fixtures."""
import json
from pathlib import Path
import tempfile
import unittest
from release_fixture import make_package
from release_integrity import sha256
from verify_backend_release import verify


class ReleaseIntegrityTest(unittest.TestCase):
    def setUp(self):
        self.scratch = tempfile.TemporaryDirectory(prefix="release-integrity-")
        self.addCleanup(self.scratch.cleanup)
        self.base = Path(self.scratch.name)
        self.release = self.base / "candidate"
        self.release.mkdir()
        make_package(self.release, self.base)
        self.manifest = self.release / "manifest.json"

    def modify_manifest(self, change):
        values = json.loads(self.manifest.read_text())
        change(values)
        self.manifest.write_text(json.dumps(values))

    def test_source_schema_cannot_be_overridden_by_manifest(self):
        self.modify_manifest(lambda m: m.update(flyway=m["flyway"] - 1))
        self.assertRaisesRegex(ValueError, "source facts mismatch", verify, self.release)

    def test_separate_source_record_must_match_candidate_sha(self):
        source = self.release / "source-sha256.json"
        value = json.loads(source.read_text())
        value["gitHead"] = "b" * 40
        source.write_text(json.dumps(value))
        self.modify_manifest(lambda m: m["artifactHashes"].update({"source-sha256.json": sha256(source)}))
        self.assertRaisesRegex(ValueError, "Source SHA mismatch", verify, self.release)

    def test_manifest_does_not_allow_unlisted_file(self):
        (self.release / "extra").write_text("unlisted")
        self.assertRaisesRegex(ValueError, "Unlisted", verify, self.release)

    def test_artifact_path_traversal_is_refused(self):
        self.modify_manifest(lambda m: m["artifactHashes"].update({"../outside": "0" * 64}))
        self.assertRaises(ValueError, verify, self.release)

    def test_linked_parent_directory_is_refused(self):
        source = self.release / "source/contracts"
        moved = self.base / "outside-contracts"
        source.rename(moved)
        source.symlink_to(moved, target_is_directory=True)
        self.assertRaisesRegex(ValueError, "Linked", verify, self.release)

    def test_missing_browser_executable_is_refused(self):
        next((self.release / "browsers").rglob("headless_shell")).unlink()
        self.assertRaises(ValueError, verify, self.release)

    def test_browser_revision_must_agree_with_packaged_driver(self):
        self.modify_manifest(lambda m: m["runtime"]["chromium"].update(revision="wrong"))
        self.assertRaisesRegex(ValueError, "Runtime inventory mismatch", verify, self.release)

    def test_windows_browser_is_refused_even_with_updated_hash(self):
        browser = self.release / "browsers/chromium-1/chrome-linux/chrome"
        browser.write_bytes(b"MZfixture")
        relative = browser.relative_to(self.release).as_posix()
        self.modify_manifest(lambda m: m["artifactHashes"].update({relative: sha256(browser)}))
        self.assertRaisesRegex(ValueError, "Linux ELF", verify, self.release)

    def test_duplicate_manifest_key_is_refused(self):
        self.manifest.write_text(self.manifest.read_text().replace('"formatVersion": 1', '"formatVersion": 1, "formatVersion": 1'))
        self.assertRaisesRegex(ValueError, "Duplicate JSON key", verify, self.release)

    def test_migration_bytes_in_jar_must_match_source(self):
        source = next((self.release / "source/server/src/main/resources/db/migration").glob("V*__*.sql"))
        source.write_bytes(source.read_bytes() + b"\n-- altered")
        name = source.relative_to(self.release / "source").as_posix()
        value = json.loads((self.release / "source-sha256.json").read_text())
        value["sourceHashes"][name] = sha256(source)
        record = self.release / "source-sha256.json"
        record.write_text(json.dumps(value))
        def change(m):
            m["sourceHashes"] = value["sourceHashes"]
            m["artifactHashes"].update({"source/" + name: sha256(source), "source-sha256.json": sha256(record)})
        self.modify_manifest(change)
        self.assertRaisesRegex(ValueError, "JAR migration/source mismatch", verify, self.release)


if __name__ == "__main__":
    unittest.main(verbosity=2)
