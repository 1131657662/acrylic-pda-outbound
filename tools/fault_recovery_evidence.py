"""Byte-preserving readers for recorded fault-recovery evidence; no SQL execution."""
from __future__ import annotations

import base64
import hashlib
import json
from pathlib import Path

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding, rsa


def require(condition: bool, label: str) -> None:
    if not condition:
        raise ValueError(label)


def local_file(root: Path, relative: str) -> Path:
    path = (root / relative).resolve()
    require(path.is_relative_to(root.resolve()), "file.outside_source")
    require(path.is_file(), "file.missing")
    return path


def digest(path: Path) -> str:
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def read_json(path: Path):
    return json.loads(path.read_bytes())


def manifest(root: Path, names: list[str]) -> list[dict]:
    return [{"path": name, "bytes": local_file(root, name).stat().st_size,
             "sha256": digest(local_file(root, name))} for name in sorted(set(names))]


def table_rows(facts: dict, name: str) -> list[dict]:
    table = facts["tables"][name]
    columns = table["columns"]
    require(bool(columns) and len(set(columns)) == len(columns), "table.columns")
    result = []
    for row in table["rows"]:
        require(len(row) == len(columns), "table.row_width")
        result.append(dict(zip(columns, [None if value is None else
                          base64.b64decode(value, validate=True) for value in row])))
    return result


def check_facts(facts: dict) -> dict:
    tables = facts["tables"]
    require(bool(tables) and set(tables) == set(facts["schema"]), "facts.schema")
    counts = {name: len(table_rows(facts, name)) for name in tables}
    history = facts["flywayHistory"]
    require(bool(history) and all(row["success"] is True for row in history), "facts.migration")
    return {"tables": len(counts), "rows": sum(counts.values())}


def check_files(options: tuple[Path, str], entries: dict) -> list[str]:
    root, prefix = options
    names = []
    for relative, expected in entries.items():
        directory = (root / prefix).resolve()
        require(directory.is_relative_to(root.resolve()), "storage.outside_source")
        path = local_file(directory, relative)
        require(path.stat().st_size == expected["bytes"], "storage.bytes")
        require(digest(path) == expected["sha256"], "storage.sha256")
        names.append(path.relative_to(root.resolve()).as_posix())
    return names


def verify_receipt(receipt: dict, public_key: bytes, audience: str) -> None:
    token = receipt["signedReceipt"].split(".")
    require(len(token) == 3, "signature.parts")
    header = json.loads(base64.urlsafe_b64decode(token[0] + "==="))
    payload = json.loads(base64.urlsafe_b64decode(token[1] + "==="))
    require(header.get("alg") == "RS256", "signature.algorithm")
    key = serialization.load_der_public_key(public_key)
    require(isinstance(key, rsa.RSAPublicKey), "signature.key_type")
    key.verify(base64.urlsafe_b64decode(token[2] + "==="),
               (token[0] + "." + token[1]).encode("ascii"), padding.PKCS1v15(), hashes.SHA256())
    require(payload.get("iss") == "acrylic-outbound" and payload.get("aud") == audience,
            "signature.issuer_audience")
    require(payload.get("receipt") == {k: v for k, v in receipt.items() if k != "signedReceipt"},
            "signature.receipt_binding")
