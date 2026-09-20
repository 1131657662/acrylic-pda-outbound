"""Verify restored RSA signing material and the original terminal receipts."""
import base64
import json
from pathlib import Path
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding
from safety import require, digest


def decode(segment):
    return base64.urlsafe_b64decode(segment + '=' * (-len(segment) % 4))


def signing_keys(config):
    public_bytes = (Path(config) / 'public.der').read_bytes()
    public = serialization.load_der_public_key(public_bytes)
    private = serialization.load_der_private_key((Path(config) / 'private.pk8').read_bytes(), password=None)
    challenge = b'R09 isolated restore key-pair verification'
    signature = private.sign(challenge, padding.PKCS1v15(), hashes.SHA256())
    public.verify(signature, challenge, padding.PKCS1v15(), hashes.SHA256())
    key_id = 'offline-' + digest(base64.b64encode(public_bytes))[:32]
    return public, key_id


def verify_receipt(receipt, keys):
    public, key_id = keys
    head, body, signature = receipt['signedReceipt'].split('.')
    header = json.loads(decode(head))
    require(header.get('alg') == 'RS256' and header.get('kid') == key_id, 'RECEIPT_KEY_MISMATCH')
    public.verify(decode(signature), (head + '.' + body).encode('ascii'), padding.PKCS1v15(), hashes.SHA256())
    claims = json.loads(decode(body))
    require(claims.get('iss') == 'acrylic-outbound' and claims.get('aud') == 'acrylic-pda-receipt', 'RECEIPT_CONTEXT_MISMATCH')
    expected = {k: v for k, v in receipt.items() if k != 'signedReceipt'}
    require(claims['receipt'] == expected, 'RECEIPT_BODY_MISMATCH')


def verify_all(db, config):
    keys = signing_keys(config)
    rows = db.query("SELECT BIN_TO_UUID(id),status,HEX(receipt_text) FROM submission "
                    "WHERE status IN ('SUCCEEDED','ABORTED') ORDER BY id;").splitlines()
    for row in rows:
        submission, status, encoded = row.split('\t')
        receipt = json.loads(bytes.fromhex(encoded))
        verify_receipt(receipt, keys)
        require(receipt['submissionId'] == submission and receipt['status'] == status, 'RECEIPT_SUBMISSION_MISMATCH')
        verify_result(db, receipt)
    return len(rows)


def verify_result(db, receipt):
    from uuid import UUID
    submission = UUID(receipt['submissionId']).hex
    if receipt['status'] != 'SUCCEEDED':
        return
    # UUID is parsed before use; it can only contribute hexadecimal digits.
    row = db.query('SELECT r.actual_count,r.weight_g,r.inventory_deducted,p.payload_sha256 '
                   'FROM order_result_version r INNER JOIN submission_payload p '
                   'ON p.submission_id=r.submission_id '
                   f"WHERE r.submission_id=UNHEX('{submission}');").split('\t')
    require(len(row) == 4, 'RECEIPT_RESULT_MISSING')
    require(int(row[0]) == int(receipt['actualCount']), 'RECEIPT_PACKAGE_MISMATCH')
    require(int(row[1]) == int(receipt['actualWeightGrams']), 'RECEIPT_WEIGHT_MISMATCH')
    require(bool(int(row[2])) == receipt['inventoryDeducted'], 'RECEIPT_DEDUCTION_MISMATCH')
    require(row[3] == receipt['payloadSha256'], 'RECEIPT_PAYLOAD_MISMATCH')
