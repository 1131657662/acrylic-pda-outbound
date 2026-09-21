"""Independent synthetic receipt consumer; no production DTO or business authorization."""
import base64
import re
from cryptography.exceptions import InvalidSignature
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding, rsa
from canonical import canonical, digest, parse, raw_digest

AUDIENCE = "acrylic-r06-command-receipt-v1"


def require(value, code):
    if not value:
        raise ValueError(code)


def decode(segment):
    require(isinstance(segment, str) and re.fullmatch(r"[A-Za-z0-9_-]+", segment), "TOKEN_ENCODING")
    try:
        raw = base64.urlsafe_b64decode(segment + "=" * (-len(segment) % 4))
    except ValueError as error:
        raise ValueError("TOKEN_ENCODING") from error
    require(base64.urlsafe_b64encode(raw).rstrip(b"=").decode() == segment, "TOKEN_ENCODING")
    return raw


def trusted_key(view, key_id):
    require(view["keyId"] == key_id and view["algorithm"] == "RS256", "UNKNOWN_KEY")
    derived = "offline-" + raw_digest(view["publicKey"].encode())[:32]
    require(derived == key_id, "KEY_ID_MISMATCH")
    raw = base64.b64decode(view["publicKey"], validate=True)
    key = serialization.load_der_public_key(raw)
    require(isinstance(key, rsa.RSAPublicKey) and key.key_size >= 2048, "KEY_INVALID")
    return key


def verify_token(token, keyring):
    require(isinstance(token, str) and len(token) <= 1048576, "TOKEN_SIZE")
    pieces = token.split(".")
    require(len(pieces) == 3, "TOKEN_FORMAT")
    header = parse(decode(pieces[0]))
    require(set(header) == {"alg", "typ", "kid"}, "HEADER_SHAPE")
    require(header["alg"] == "RS256" and header["typ"] == "JWT", "TOKEN_ALGORITHM")
    require(header["kid"] in keyring, "UNKNOWN_KEY")
    key = trusted_key(keyring[header["kid"]], header["kid"])
    try:
        key.verify(decode(pieces[2]), ".".join(pieces[:2]).encode("ascii"),
                   padding.PKCS1v15(), hashes.SHA256())
    except InvalidSignature as error:
        raise ValueError("SIGNATURE_INVALID") from error
    claims = parse(decode(pieces[1]))
    require(set(claims) == {"iss", "aud", "receipt"}, "CLAIM_SHAPE")
    require(claims["iss"] == "acrylic-outbound" and claims["aud"] == AUDIENCE, "SIGNATURE_PURPOSE")
    return claims["receipt"]


def verify_response(raw, trusted):
    require(raw_digest(raw) == trusted["responseHash"], "ORIGINAL_BYTES_CHANGED")
    outer = parse(raw)
    unsigned = {key: value for key, value in outer.items() if key != "signedReceipt"}
    signed = verify_token(outer["signedReceipt"], trusted["keys"])
    require(canonical(unsigned) == canonical(signed), "SIGNED_CONTENT_CHANGED")
    expected = trusted["receipt"]
    require(canonical(unsigned) == canonical(expected), "ORIGINAL_CONTEXT_CHANGED")
    require(unsigned["commandSha256"] == digest(trusted["command"], "command"), "COMMAND_CHANGED")
    require(unsigned["commandId"] == trusted["command"]["commandId"], "ORIGINAL_KEY_CHANGED")
    return unsigned
