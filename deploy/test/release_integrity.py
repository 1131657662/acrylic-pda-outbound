"""Strict portable manifest paths and streaming file hashes."""
import hashlib
import json
from pathlib import PurePosixPath
import re


def require(condition, message):
    if not condition:
        raise ValueError(message)


def sha256(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def no_duplicates(pairs):
    result = {}
    for key, value in pairs:
        require(key not in result, "Duplicate JSON key")
        result[key] = value
    return result


def read_json(path):
    return json.loads(path.read_text(encoding="utf-8"), object_pairs_hook=no_duplicates)


def safe_file(root, relative):
    require(isinstance(relative, str) and relative and "\\" not in relative, "Unsafe artifact path")
    require(not re.match(r"^[A-Za-z]:", relative), "Unsafe artifact path")
    parts = PurePosixPath(relative).parts
    require(not relative.startswith("/") and all(p not in {"..", "."} for p in parts), "Unsafe artifact path")
    require(str(PurePosixPath(relative)) == relative, "Noncanonical artifact path")
    path = root
    for part in parts:
        path = path / part
        require(not path.is_symlink(), "Linked artifact or parent")
    require(path.is_file() and root.resolve() in path.resolve().parents, "Missing or escaped artifact")
    return path


def verify_hashes(root, hashes):
    require(isinstance(hashes, dict) and hashes, "Missing artifact hashes")
    require(not any(p.is_symlink() for p in root.rglob("*")), "Linked package entry")
    actual = {p.relative_to(root).as_posix() for p in root.rglob("*") if p.is_file()}
    require(actual == set(hashes) | {"manifest.json"}, "Unlisted or missing artifact")
    for name, expected in hashes.items():
        require(isinstance(expected, str) and re.fullmatch(r"[0-9a-f]{64}", expected), "Invalid SHA-256")
        require(sha256(safe_file(root, name)) == expected, "Artifact mismatch: " + name)
