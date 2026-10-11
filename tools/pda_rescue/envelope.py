"""Fixed-format AES-GCM envelope, shared with Android RescueEnvelope."""
import hashlib
from pathlib import Path

from cryptography.hazmat.primitives.ciphers.aead import AESGCM

MAGIC = b"PDARESCUE1\n"
MAX_BYTES = 64 * 1024 * 1024
HEADER_SIZE = len(MAGIC) + 28


def decrypt(path: Path, password: str) -> bytes:
    if not 16 <= len(password) <= 128 or not all(33 <= ord(c) <= 126 for c in password):
        raise ValueError("Use a 16–128 character ASCII password without spaces")
    size = path.stat().st_size
    if not HEADER_SIZE + 17 <= size <= MAX_BYTES + HEADER_SIZE + 16:
        raise ValueError("Invalid envelope size")
    data = path.read_bytes()
    if not data.startswith(MAGIC):
        raise ValueError("Unknown envelope format")
    salt = data[len(MAGIC):len(MAGIC) + 16]
    nonce = data[len(MAGIC) + 16:HEADER_SIZE]
    key = hashlib.pbkdf2_hmac("sha256", password.encode("ascii"), salt, 600_000, 32)
    return AESGCM(key).decrypt(nonce, data[HEADER_SIZE:], data[:HEADER_SIZE])
