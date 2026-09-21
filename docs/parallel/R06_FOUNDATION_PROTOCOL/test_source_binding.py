"""Tie final validation to the reviewed production source blobs across Windows line endings."""
import hashlib
import os
from pathlib import Path
import unittest
from fixtures import ROOT, load


class ProductionSourceBinding(unittest.TestCase):
    def test_compiled_production_files_match_frozen_source_blobs(self):
        repository = Path(os.environ.get("FOUNDATION_SOURCE_ROOT", str(ROOT.parents[2])))
        manifest = load("source_manifest.json")
        self.assertRegex(manifest["productionCommit"], r"^[0-9a-f]{40}$")
        for path, expected in manifest["productionSourcesLfSha256"].items():
            raw = (repository / path).read_bytes().replace(b"\r\n", b"\n")
            self.assertEqual(expected, hashlib.sha256(raw).hexdigest(), path)
