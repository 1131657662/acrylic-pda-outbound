"""Ephemeral test-only RSA signer for semantically wrong but cryptographically valid receipts."""
import base64
from copy import deepcopy
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding, rsa
from ledger_primitives import canonical, digest, raw_digest


def encoded(raw):
    return base64.urlsafe_b64encode(raw).rstrip(b"=").decode("ascii")


class Forgery:
    def __init__(self):
        self.private = rsa.generate_private_key(public_exponent=65537, key_size=2048)
        public = base64.b64encode(self.private.public_key().public_bytes(
            serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo)).decode("ascii")
        self.kid = "offline-" + raw_digest(public.encode("ascii"))[:32]
        self.public = {"keyId": self.kid, "algorithm": "RS256", "publicKey": public}

    def sign(self, value, audience="acrylic-r06-command-receipt-v1"):
        unsigned = {key: item for key, item in value.items() if key != "signedReceipt"}
        header = {"alg": "RS256", "typ": "JWT", "kid": self.kid}
        claims = {"iss": "acrylic-outbound", "aud": audience, "receipt": unsigned}
        data = encoded(canonical(header)) + "." + encoded(canonical(claims))
        signature = self.private.sign(data.encode("ascii"), padding.PKCS1v15(), hashes.SHA256())
        return dict(unsigned, signedReceipt=data + "." + encoded(signature))

    def response(self, vector, unsigned):
        result = deepcopy(vector)
        signed = self.sign(unsigned)
        result["responseUtf8"] = canonical(signed).decode("utf-8")
        result["record"]["original_response_sha256"] = raw_digest(result["responseUtf8"].encode())
        result["record"]["unsigned_receipt_sha256"] = digest(
            {key: item for key, item in signed.items() if key != "signedReceipt"}, "receipt")
        return result


def replace_path(value, path, replacement):
    result = deepcopy(value)
    target = result
    parts = path.split(".")
    for key in parts[:-1]:
        target = target[int(key)] if isinstance(target, list) else target[key]
    key = int(parts[-1]) if isinstance(target, list) else parts[-1]
    target[key] = replacement
    return result
