"""Read-only, independent consumer of a P5 rotation receipt and both retained evidence sets."""
import hashlib
import hmac
import json
from pathlib import Path


def require(condition, code):
    if not condition:
        raise ValueError(code)


def canonical(value):
    return (json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")) + "\n").encode("utf-8")


def read(path):
    with Path(path).open("rb") as stream:
        raw = stream.read(65537)
    require(0 < len(raw) <= 65536, "REKEY_VERIFY_SIZE")
    value = json.loads(raw)
    require(canonical(value) == raw, "REKEY_VERIFY_CANONICAL")
    return value


def digest(value):
    return hashlib.sha256(canonical(value)).hexdigest()


def authenticated(path, key, field="anchorTag"):
    value = read(path)
    body = {name: content for name, content in value.items() if name != field}
    expected = hmac.digest(key, canonical(body), "sha256").hex()
    require(hmac.compare_digest(value[field], expected), "REKEY_VERIFY_TAG")
    return body


def verify(request, receipt, old_key, new_key):
    require(receipt["status"] == "REKEYED" and receipt["runtimeUpdated"] is False, "REKEY_VERIFY_STATUS")
    for name, key in (("oldAnchorId", old_key), ("newAnchorId", new_key)):
        require(receipt[name] == request[name] == hashlib.sha256(key).hexdigest(), "REKEY_VERIFY_KEY")
    root = Path(request["leaseRoot"])
    backup = Path(receipt["evidenceDirectory"])
    require(backup.is_dir() and backup.parent == root.parent and backup.name.startswith(".rekey-backup-"),
            "REKEY_VERIFY_BACKUP")
    marker = read(root / "lease-root.json")
    original = read(backup / "lease-root.json")
    require(digest(original) == request["markerSha256"], "REKEY_VERIFY_ORIGINAL")
    expected = {**original, "anchorId": request["newAnchorId"]}
    expected.pop("bindingSha256")
    expected["bindingSha256"] = digest(expected)
    require(marker == expected and digest(marker) == receipt["markerSha256"], "REKEY_VERIFY_MARKER")
    old_index = authenticated(backup / "leases/index.json", old_key, "indexTag")
    new_index = authenticated(root / "leases/index.json", new_key, "indexTag")
    require(old_index == new_index and old_index["entries"] == [], "REKEY_VERIFY_LEASES")
    require({p.name for p in (root / "leases").iterdir()} == {"index.json"}, "REKEY_VERIFY_LEASES")
    old_files = {p.name for p in (backup / "processes").iterdir()} if (backup / "processes").exists() else set()
    require(old_files == {p.name for p in (root / "processes").iterdir()}, "REKEY_VERIFY_PROCESSES")
    for name in sorted(old_files):
        old = authenticated(backup / "processes" / name, old_key)
        new = authenticated(root / "processes" / name, new_key)
        require(old == new, "REKEY_VERIFY_PROCESS_FACTS")
    require(receipt["processRecords"] == len(old_files) and receipt["leaseRecords"] == 0, "REKEY_VERIFY_COUNTS")
    return {"rotationVerified": True, "processRecords": len(old_files), "originalEvidenceRetained": True,
            "runtimeUpdated": False, "productionApproved": False}
