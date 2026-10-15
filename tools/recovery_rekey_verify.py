"""Read-only, independent consumer of a P5 rotation receipt and both retained evidence sets."""
import hashlib
import hmac
import json
from pathlib import Path
import re


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


INVENTORY_FILE = 'rekey-inventory.json'


def original_descriptor(backup, name):
    path = backup / name
    for item in (path, *path.parents):
        require(not item.is_symlink() and not item.is_junction(), 'REKEY_INVENTORY_PATH')
    require(path.is_file() and path.stat().st_nlink == 1, 'REKEY_INVENTORY_FILE')
    raw = canonical(read(path))
    return {'bytes': len(raw), 'path': name, 'sha256': hashlib.sha256(raw).hexdigest()}


def original_names(backup):
    allowed = {'lease-root.json', 'leases', 'processes', INVENTORY_FILE}
    require({p.name for p in backup.iterdir()} <= allowed, 'REKEY_INVENTORY_LAYOUT')
    for directory in (backup / 'leases', backup / 'processes'):
        require(not directory.is_symlink() and not directory.is_junction(), 'REKEY_INVENTORY_PATH')
    require({p.name for p in (backup / 'leases').iterdir()} == {'index.json'}, 'REKEY_INVENTORY_LAYOUT')
    names = ['lease-root.json', 'leases/index.json']
    processes = backup / 'processes'
    for path in processes.iterdir() if processes.exists() else []:
        require(re.fullmatch(r'[0-9a-f-]{36}\.json', path.name), 'REKEY_INVENTORY_PROCESS')
        names.append('processes/' + path.name)
        require(len(names) <= 9998, 'REKEY_INVENTORY_LIMIT')
    return sorted(names)


def verify_inventory(request, backup, keys):
    path = backup / INVENTORY_FILE
    if not path.exists():
        return False
    original_descriptor(backup, INVENTORY_FILE)
    manifest = authenticated(path, keys[0], 'inventoryTag')
    fields = {'formatVersion', 'operation', 'installationId', 'authorityRoot', 'leaseRoot', 'identitySha256',
              'markerSha256', 'oldAnchorId', 'newAnchorId', 'recordCount', 'processRecords', 'totalBytes', 'recordsSha256'}
    require(set(manifest) == fields and type(manifest['formatVersion']) is int and
            manifest['formatVersion'] == 1 and manifest['operation'] == 'REKEY_P5_INVENTORY', 'REKEY_INVENTORY_FORMAT')
    for name in ('installationId', 'authorityRoot', 'leaseRoot', 'identitySha256', 'markerSha256', 'oldAnchorId', 'newAnchorId'):
        require(manifest[name] == request[name], 'REKEY_INVENTORY_BINDING')
    for name, key in zip(('oldAnchorId', 'newAnchorId'), keys):
        require(manifest[name] == hashlib.sha256(key).hexdigest(), 'REKEY_INVENTORY_KEY')
    descriptors = [original_descriptor(backup, name) for name in original_names(backup)]
    summary = hashlib.sha256()
    for entry in descriptors:
        summary.update(canonical(entry))
    facts = {'recordCount': len(descriptors), 'processRecords': len(descriptors) - 2,
             'totalBytes': sum(entry['bytes'] for entry in descriptors), 'recordsSha256': summary.hexdigest()}
    for name, value in facts.items():
        require(type(manifest[name]) is type(value) and manifest[name] == value, 'REKEY_INVENTORY_SET')
    return True


def verify_receipt_inventory(request, receipt, keys):
    backup = Path(receipt['evidenceDirectory'])
    authenticated_set = verify_inventory(request, backup, keys)
    if 'inventorySha256' in receipt:
        require(authenticated_set and digest(read(backup / INVENTORY_FILE)) == receipt['inventorySha256'],
                'REKEY_VERIFY_INVENTORY_RECEIPT')
    return authenticated_set


def verify(request, receipt, old_key, new_key):
    require(receipt["status"] == "REKEYED" and receipt["runtimeUpdated"] is False, "REKEY_VERIFY_STATUS")
    for name, key in (("oldAnchorId", old_key), ("newAnchorId", new_key)):
        require(receipt[name] == request[name] == hashlib.sha256(key).hexdigest(), "REKEY_VERIFY_KEY")
    root = Path(request["leaseRoot"])
    backup = Path(receipt["evidenceDirectory"])
    require(backup.is_dir() and backup.parent == root.parent and backup.name.startswith(".rekey-backup-"),
            "REKEY_VERIFY_BACKUP")
    set_authenticated = verify_receipt_inventory(request, receipt, (old_key, new_key))
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
            "processSetAuthenticated": set_authenticated,
            "runtimeUpdated": False, "productionApproved": False}
