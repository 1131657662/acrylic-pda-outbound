"""Pinned RSA verification for preserved server receipt originals."""
import base64
import re

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding, rsa

from .codec import canonical, digest, parse, require


def decode(value):
    require(re.fullmatch(r'[A-Za-z0-9_-]+', value) is not None, 'Invalid JWT encoding')
    return base64.urlsafe_b64decode(value + '=' * (-len(value) % 4))


def verified(response, purpose, key):
    name, audience = purpose
    token = response[name]
    require(isinstance(token, str) and len(token) <= 1024 * 1024, 'Oversized signature')
    parts = token.split('.')
    require(len(parts) == 3, 'Invalid JWT format')
    header, claims = parse(decode(parts[0])), parse(decode(parts[1]))
    require(header == {'alg': 'RS256', 'typ': 'JWT', 'kid': key['keyId']}, 'JWT header mismatch')
    require(key['algorithm'] == 'RS256', 'Unsupported key algorithm')
    public = serialization.load_der_public_key(base64.b64decode(key['publicKey'], validate=True))
    require(isinstance(public, rsa.RSAPublicKey) and public.key_size >= 2048, 'Weak RSA key')
    public.verify(decode(parts[2]), (parts[0] + '.' + parts[1]).encode('ascii'),
                  padding.PKCS1v15(), hashes.SHA256())
    require(claims['iss'] == 'acrylic-outbound' and claims['aud'] == audience, 'Wrong signature purpose')
    field = 'receipt' if name == 'signedReceipt' else 'archive'
    unsigned = {k: v for k, v in response.items() if k != name}
    require(canonical(claims[field]) == canonical(unsigned), 'Signed and unsigned response differ')
    return digest(canonical(key))
