"""Strict independent RS256 receipt verifier for the C0 proposal, never a credential."""
import base64
import re
from cryptography.exceptions import InvalidSignature
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding, rsa
from canonical import canonical, parse, raw_digest
from shape import fields, require

AUDIENCE = 'acrylic-r06-command-receipt-v1-proposal'


def decode(segment):
    require(isinstance(segment, str) and re.fullmatch(r'[A-Za-z0-9_-]+', segment), 'TOKEN_ENCODING')
    try:
        raw = base64.urlsafe_b64decode(segment + '=' * (-len(segment) % 4))
    except ValueError as error:
        raise ValueError('TOKEN_ENCODING') from error
    require(base64.urlsafe_b64encode(raw).rstrip(b'=').decode('ascii') == segment, 'TOKEN_ENCODING')
    return raw


def trusted_key(view, key_id):
    require(view['keyId'] == key_id and view['algorithm'] == 'RS256', 'UNKNOWN_KEY')
    public_bytes = base64.b64decode(view['publicKey'], validate=True)
    derived_id = 'offline-' + raw_digest(view['publicKey'].encode('utf-8'))[:32]
    require(derived_id == key_id, 'KEY_ID_MISMATCH')
    public_key = serialization.load_der_public_key(public_bytes)
    require(isinstance(public_key, rsa.RSAPublicKey) and public_key.key_size >= 2048, 'KEY_INVALID')
    return public_key


def verify_token(token, keyring):
    require(isinstance(token, str) and len(token) <= 1048576, 'TOKEN_SIZE')
    parts = token.split('.')
    require(len(parts) == 3, 'TOKEN_FORMAT')
    header = parse(decode(parts[0]).decode('utf-8'))
    fields(header, {'alg', 'typ', 'kid'})
    require(header['alg'] == 'RS256' and header['typ'] == 'JWT', 'TOKEN_ALGORITHM')
    require(header['kid'] in keyring, 'UNKNOWN_KEY')
    key = trusted_key(keyring[header['kid']], header['kid'])
    try:
        key.verify(decode(parts[2]), '.'.join(parts[:2]).encode('ascii'), padding.PKCS1v15(), hashes.SHA256())
    except InvalidSignature as error:
        raise ValueError('SIGNATURE_INVALID') from error
    claims = parse(decode(parts[1]).decode('utf-8'))
    fields(claims, {'iss', 'aud', 'receipt'})
    require(claims['iss'] == 'acrylic-outbound' and claims['aud'] == AUDIENCE, 'SIGNATURE_PURPOSE')
    return claims['receipt']


def verify_outer(outer, keyring):
    unsigned = {key: value for key, value in outer.items() if key != 'signedReceipt'}
    signed = verify_token(outer['signedReceipt'], keyring)
    require(canonical(unsigned) == canonical(signed), 'SIGNED_CONTENT_CHANGED')
    return signed
