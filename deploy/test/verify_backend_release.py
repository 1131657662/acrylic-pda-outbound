#!/usr/bin/env python3
"""Verify immutable source facts, all hashes and Linux runtime before stopping writers."""
from pathlib import Path
import re
import sys
sys.dont_write_bytecode = True
from release_facts import facts, source_inputs
from release_integrity import read_json, require, safe_file, sha256, verify_hashes
from release_runtime import runtime_facts, verify_binary_sources

REQUIRED = {
    "server.jar", "source-sha256.json", "build-receipt.json", "deploy/verify_backend_release.py",
    "deploy/release_facts.py", "deploy/release_integrity.py", "deploy/release_runtime.py",
    "deploy/activate_backend.sh", "deploy/migrate.sh", "deploy/backup.sh",
    "deploy/service_checkpoint.sh", "deploy/backup_facts.py",
    "deploy/acrylic-outbound-test.service", "deploy/acrylic-outbound-test-worker.service",
    "deploy/acrylic-outbound-test-backup.service", "deploy/acrylic-outbound-test-backup.timer",
}


def verify_sources(root, manifest):
    record = read_json(safe_file(root, "source-sha256.json"))
    require(record["gitHead"] == manifest["gitHead"], "Source SHA mismatch")
    hashes = record["sourceHashes"]
    require(hashes == manifest["sourceHashes"], "Source inventory mismatch")
    source = root / "source"
    for name in source_inputs(source):
        require(sha256(safe_file(source, name)) == hashes[name], "Source content mismatch")
    derived = facts(source)
    require(all(manifest[key] == value for key, value in derived.items()), "Unsupported contract/schema or source facts mismatch")
    verify_binary_sources(root, hashes)
    build = read_json(safe_file(root, "build-receipt.json"))
    require(build["gitHead"] == record["gitHead"] and build["sourceHashes"] == hashes, "Build input mismatch")
    require(build["exitCode"] == 0, "Build failure")
    for name, expected in build["artifactHashes"].items():
        require(manifest["artifactHashes"][name] == expected, "Build artifact mismatch")
    built = {name for name in manifest["artifactHashes"] if name == "server.jar" or
             name.startswith("report-rendering/acrylic-report-rendering/")}
    require(set(build["artifactHashes"]) == built, "Incomplete build artifact provenance")
    return derived


def verify(root):
    require(not root.is_symlink() and root.is_dir(), "Release must be a real directory")
    manifest = read_json(safe_file(root, "manifest.json"))
    require(manifest["formatVersion"] == 1, "Unsupported manifest format")
    require(manifest["release"] == root.name and re.fullmatch(r"[A-Za-z0-9._-]+", root.name), "Release ID mismatch")
    require(manifest["environment"] == "independent-test", "Environment mismatch")
    require(re.fullmatch(r"[0-9a-f]{40}", manifest["gitHead"]), "Invalid Git SHA")
    require(manifest["gitStatus"] == [], "Dirty source tree is not a candidate")
    hashes = manifest["artifactHashes"]
    require(REQUIRED <= hashes.keys(), "Missing deployment artifact")
    verify_hashes(root, hashes)
    require(hashes["server.jar"] == manifest["serverSha256"], "Server hash mismatch")
    verify_sources(root, manifest)
    require(manifest["runtime"] == runtime_facts(root), "Runtime inventory mismatch")
    return manifest


if __name__ == "__main__":
    result = verify(Path(sys.argv[1]))
    print(result["flyway"] if len(sys.argv) == 3 and sys.argv[2] == "--schema" else
          "Verified backend package: " + result["release"])
