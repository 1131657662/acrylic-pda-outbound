"""Bounded ZIP and raw-byte signature validation, independent of Android."""
import base64
import hashlib
import io
import json
import zipfile

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec

from .envelope import MAX_BYTES

PURPOSE = "PDA_READONLY_RESCUE_V1"
REQUIRED = {"order.json", "captures.ndjson", "scans.ndjson", "events.ndjson", "lines.ndjson",
            "dispositions.ndjson", "scan-details.tsv", "product-summary.tsv", "readme.txt"}
OPTIONAL = {"submission.json", "receipt.json", "submission-info.json", "source-evidence.ndjson"}
OPTIONAL |= {"independent-sku.bin", "independent-source.json", "independent-voids.ndjson", "independent-state.json"}
SEAL = {"manifest.json", "manifest.sig", "device-public-key.der"}


def _pairs(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("Duplicate JSON property")
        result[key] = value
    return result


def parse(data: bytes):
    return json.loads(data.decode("utf-8"), object_pairs_hook=_pairs,
                      parse_constant=lambda _: (_ for _ in ()).throw(ValueError("Invalid number")))


def archive(plain: bytes) -> dict[str, bytes]:
    if len(plain) > MAX_BYTES:
        raise ValueError("Archive too large")
    with zipfile.ZipFile(io.BytesIO(plain)) as bundle:
        entries = bundle.infolist()
        names = [entry.filename for entry in entries]
        if len(names) != len(set(names)) or not REQUIRED | SEAL <= set(names):
            raise ValueError("Missing or duplicate archive entries")
        if set(names) - REQUIRED - OPTIONAL - SEAL:
            raise ValueError("Unknown archive entry or path")
        if sum(entry.file_size for entry in entries) > MAX_BYTES:
            raise ValueError("Expanded archive too large")
        if any(entry.flag_bits & 1 or entry.is_dir() for entry in entries):
            raise ValueError("Unsupported ZIP entry")
        return {entry.filename: bundle.read(entry) for entry in entries}


def verify_seal(files: dict[str, bytes]) -> dict:
    raw = files["manifest.json"]
    if len(raw) > 64 * 1024:
        raise ValueError("Manifest too large")
    manifest = parse(raw)
    if manifest["purpose"] != PURPOSE or set(manifest["files"]) != set(files) - SEAL:
        raise ValueError("Manifest membership mismatch")
    for name, fact in manifest["files"].items():
        data = files[name]
        if fact != {"bytes": str(len(data)), "sha256": hashlib.sha256(data).hexdigest()}:
            raise ValueError("File digest or size mismatch")
    der = files["device-public-key.der"]
    if hashlib.sha256(der).hexdigest() != manifest["deviceKeySha256"]:
        raise ValueError("Device key digest mismatch")
    key = serialization.load_der_public_key(der)
    if not isinstance(key, ec.EllipticCurvePublicKey) or not isinstance(key.curve, ec.SECP256R1):
        raise ValueError("Device key must be ECDSA P-256")
    key.verify(files["manifest.sig"], PURPOSE.encode() + b"\n" + raw, ec.ECDSA(hashes.SHA256()))
    return manifest


def source_trust(files: dict[str, bytes], order: dict, trusted: dict | None) -> bool:
    if trusted is None:
        return False
    entry = trusted.get(order["deviceId"])
    if entry is None:
        raise ValueError("Device absent from externally provisioned registration trust")
    if base64.b64decode(entry["publicKeyDerBase64"], validate=True) != files["device-public-key.der"]:
        raise ValueError("Device registration key mismatch")
    if order["operatorId"] not in entry["operatorIds"] or order["warehouseId"] not in entry["warehouseIds"]:
        raise ValueError("Operator or warehouse outside provisioned rescue scope")
    return True
