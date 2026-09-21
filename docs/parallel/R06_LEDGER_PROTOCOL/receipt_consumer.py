"""Independent actual-wire verifier; trusted facts are test inputs, never client authority."""
import base64
import re
from cryptography.exceptions import InvalidSignature
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding, rsa
from ledger_primitives import canonical, digest, parse, raw_digest, require
from receipt_expectations import expected_receipt


def decode(segment):
    require(isinstance(segment, str) and re.fullmatch(r"[A-Za-z0-9_-]+", segment), "TOKEN_ENCODING")
    raw = base64.urlsafe_b64decode(segment + "=" * (-len(segment) % 4))
    require(base64.urlsafe_b64encode(raw).rstrip(b"=").decode() == segment, "TOKEN_ENCODING")
    return raw


def trusted_key(view, key_id):
    require(view["keyId"] == key_id and view["algorithm"] == "RS256", "UNKNOWN_KEY")
    derived = "offline-" + raw_digest(view["publicKey"].encode("ascii"))[:32]
    require(derived == key_id, "KEY_ID_MISMATCH")
    key = serialization.load_der_public_key(base64.b64decode(view["publicKey"], validate=True))
    require(isinstance(key, rsa.RSAPublicKey) and key.key_size >= 2048, "KEY_INVALID")
    return key


def token_receipt(token, keys):
    require(isinstance(token, str) and len(token) <= 262144, "TOKEN_SIZE")
    pieces = token.split(".")
    require(len(pieces) == 3, "TOKEN_FORMAT")
    header = parse(decode(pieces[0]))
    require(set(header) == {"alg", "typ", "kid"}, "HEADER_SHAPE")
    require(header["alg"] == "RS256" and header["typ"] == "JWT", "TOKEN_ALGORITHM")
    require(header["kid"] in keys, "UNKNOWN_KEY")
    key = trusted_key(keys[header["kid"]], header["kid"])
    try:
        key.verify(decode(pieces[2]), ".".join(pieces[:2]).encode("ascii"),
                   padding.PKCS1v15(), hashes.SHA256())
    except InvalidSignature as error:
        raise ValueError("SIGNATURE_INVALID") from error
    claims = parse(decode(pieces[1]))
    require(set(claims) == {"iss", "aud", "receipt"}, "CLAIM_SHAPE")
    require(claims["iss"] == "acrylic-outbound", "SIGNATURE_ISSUER")
    require(claims["aud"] == "acrylic-r06-command-receipt-v1", "SIGNATURE_AUDIENCE")
    return claims["receipt"]


def verify_response(raw, trusted):
    require(raw_digest(raw) == trusted["originalResponseSha256"], "ORIGINAL_BYTES_CHANGED")
    outer = parse(raw)
    require(isinstance(outer, dict) and "signedReceipt" in outer, "RESPONSE_SHAPE")
    unsigned = {key: value for key, value in outer.items() if key != "signedReceipt"}
    signed = token_receipt(outer["signedReceipt"], trusted["keys"])
    require(canonical(unsigned) == canonical(signed), "SIGNED_CONTENT_CHANGED")
    expected = expected_receipt(trusted["facts"])
    require(canonical(unsigned) == canonical(expected), "TRUSTED_FACTS_CHANGED")
    require(digest(unsigned, "receipt") == trusted["unsignedReceiptSha256"], "RECEIPT_DOMAIN_CHANGED")
    return unsigned


def verify_detail(data, trusted):
    require(set(data) == {"originalCommandReceiptUtf8", "originalResponseSha256", "currentEffect"}, "DETAIL_SHAPE")
    require(data["originalResponseSha256"] == trusted["originalResponseSha256"], "DETAIL_HASH_CHANGED")
    raw = data["originalCommandReceiptUtf8"].encode("utf-8", errors="strict")
    return verify_response(raw, trusted)
