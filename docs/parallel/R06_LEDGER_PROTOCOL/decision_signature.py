"""Strict historical signatures for recorded decisions; no production imports."""
from cryptography.exceptions import InvalidSignature
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.asymmetric import padding
from ledger_primitives import canonical, parse, require
from receipt_consumer import decode, trusted_key


def verify_signed(value, keys, audience):
    require(isinstance(value, dict) and "signedReceipt" in value, "SIGNED_SHAPE")
    token = value["signedReceipt"]
    require(isinstance(token, str) and len(token) <= 262144, "TOKEN_SIZE")
    parts = token.split(".")
    require(len(parts) == 3, "TOKEN_FORMAT")
    header = parse(decode(parts[0]))
    require(set(header) == {"alg", "typ", "kid"}, "HEADER_SHAPE")
    require(header["alg"] == "RS256" and header["typ"] == "JWT", "TOKEN_ALGORITHM")
    require(header["kid"] in keys, "UNKNOWN_KEY")
    key = trusted_key(keys[header["kid"]], header["kid"])
    try:
        key.verify(decode(parts[2]), ".".join(parts[:2]).encode("ascii"),
                   padding.PKCS1v15(), hashes.SHA256())
    except InvalidSignature as error:
        raise ValueError("SIGNATURE_INVALID") from error
    claims = parse(decode(parts[1]))
    require(set(claims) == {"iss", "aud", "receipt"}, "CLAIM_SHAPE")
    require(claims["iss"] == "acrylic-outbound" and claims["aud"] == audience, "SIGNATURE_PURPOSE")
    unsigned = {key: item for key, item in value.items() if key != "signedReceipt"}
    require(canonical(unsigned) == canonical(claims["receipt"]), "SIGNED_CONTENT_CHANGED")
    return unsigned
