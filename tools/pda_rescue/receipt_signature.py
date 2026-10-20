"""Verify historical business receipt signatures against caller-provisioned keys."""
import base64
import json
import re

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding, rsa

from .integrity import parse


def same(actual, expected):
    if json.dumps(actual, sort_keys=True, ensure_ascii=False, allow_nan=False) != json.dumps(
            expected, sort_keys=True, ensure_ascii=False, allow_nan=False):
        raise ValueError('Signed receipt differs from original context or response')


def decode(part):
    if not re.fullmatch(r'[A-Za-z0-9_-]+', part):
        raise ValueError('Invalid receipt token encoding')
    raw = base64.urlsafe_b64decode(part + '=' * (-len(part) % 4))
    if base64.urlsafe_b64encode(raw).rstrip(b'=').decode('ascii') != part:
        raise ValueError('Noncanonical receipt token encoding')
    return raw


def public_key(trusted):
    if not isinstance(trusted, dict) or set(trusted) != {'algorithm', 'keyId', 'publicKey'}:
        raise ValueError('An externally provisioned receipt key is required')
    if trusted['algorithm'] != 'RS256' or not isinstance(trusted['keyId'], str) or not 1 <= len(trusted['keyId']) <= 200:
        raise ValueError('Invalid receipt key metadata')
    text = trusted['publicKey']
    if not isinstance(text, str) or len(text) > 16384:
        raise ValueError('Receipt public key exceeds resource limit')
    der = base64.b64decode(text, validate=True)
    key = serialization.load_der_public_key(der)
    if not isinstance(key, rsa.RSAPublicKey) or not 2048 <= key.key_size <= 8192:
        raise ValueError('Receipt key must be RSA with at least 2048 bits')
    return key, der


def verify_signature(receipt, trusted):
    token = receipt.get('signedReceipt')
    if not isinstance(token, str) or len(token) > 262144 or token.count('.') != 2:
        raise ValueError('Invalid signed receipt')
    head, payload, signature = token.split('.')
    header, claims = parse(decode(head)), parse(decode(payload))
    key, der = public_key(trusted)
    same(header, {'alg': 'RS256', 'typ': 'JWT', 'kid': trusted['keyId']})
    if not isinstance(claims, dict) or set(claims) != {'iss', 'aud', 'receipt'}:
        raise ValueError('Unsupported business receipt claims')
    if claims['iss'] != 'acrylic-outbound' or claims['aud'] != 'acrylic-pda-receipt':
        raise ValueError('Receipt issuer or audience mismatch')
    key.verify(decode(signature), (head + '.' + payload).encode('ascii'), padding.PKCS1v15(), hashes.SHA256())
    same(claims['receipt'], {name: value for name, value in receipt.items() if name != 'signedReceipt'})
    return der
