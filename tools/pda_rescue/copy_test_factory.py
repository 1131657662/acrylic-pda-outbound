"""Synthetic encrypted fixture over the current Android closure vector; never field proof."""
import hashlib
import os
from pathlib import Path
import tempfile

from cryptography.hazmat.primitives.ciphers.aead import AESGCM

from .envelope import MAGIC

PASSWORD = 'synthetic-copy-test-passphrase'
LABELS = {'custodian': 'synthetic isolated operator', 'location': 'same-machine test; not external storage'}


def fixture():
    vector = Path(os.environ['INDEPENDENT_CLOSURE_VECTOR_DIR'])
    temporary = tempfile.TemporaryDirectory(prefix='pda-copy-test-')
    root = Path(temporary.name)
    plain = (vector / 'closed.zip').read_bytes()
    salt, nonce = os.urandom(16), os.urandom(12)
    header = MAGIC + salt + nonce
    key = hashlib.pbkdf2_hmac('sha256', PASSWORD.encode('ascii'), salt, 600_000, 32)
    package = root / 'original.pdarescue'
    package.write_bytes(header + AESGCM(key).encrypt(nonce, plain, header))
    trust = root / 'trust.json'
    trust.write_bytes((vector / 'trust.json').read_bytes())
    return temporary, root, package, trust
