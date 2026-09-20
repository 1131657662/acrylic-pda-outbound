"""Verify retained TEMP qualification signatures and original preparation bytes."""
import gzip
import io
from uuid import UUID
from cryptography.exceptions import InvalidSignature
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.asymmetric import padding
from archive_proofs import parse, instant
from receipts import decode
from safety import require, digest, OpsError

IDENTITY = ('authorizationId', 'bundleId', 'warehouseId', 'deviceId', 'userId',
            'authorizationEpoch', 'userAuthVersion', 'deviceVersion', 'serverRecoveryEpoch')
PARTS = {'inventory', 'mapping', 'policy', 'rules', 'availability'}


def claims(token, keys, audience):
    public, key_id = keys
    head, body, signature = token.split('.')
    require(parse(decode(head)) == {'alg': 'RS256', 'typ': 'JWT', 'kid': key_id}, 'TEMP_KEY_MISMATCH')
    try:
        public.verify(decode(signature), (head + '.' + body).encode('ascii'), padding.PKCS1v15(), hashes.SHA256())
    except InvalidSignature:
        raise OpsError('TEMP_SIGNATURE_INVALID') from None
    value = parse(decode(body))
    require(value.get('iss') == 'acrylic-outbound' and value.get('aud') == audience, 'TEMP_CONTEXT_MISMATCH')
    # Deliberately do not compare expiry with now: expired evidence remains valid evidence.
    return value


def verify_authorization(row, keys):
    raw = bytes.fromhex(row[13])
    require(digest(raw) == row[14], 'TEMP_MANIFEST_HASH_MISMATCH')
    manifest = parse(raw)
    identity = dict(zip(IDENTITY, row[:9]))
    require(manifest['schemaVersion'] == 1 and manifest['purpose'] == 'TEMP_WORK', 'TEMP_MANIFEST_VERSION_MISMATCH')
    require(manifest['identity'] == identity and row[11] == keys[1], 'TEMP_IDENTITY_MISMATCH')
    token = claims(row[12], keys, 'acrylic-pda-temporary-work')
    proof = claims(row[15], keys, 'acrylic-pda-temporary-preparation')
    require(token['identity'] == identity and token['jti'] == row[0] and token['purpose'] == 'TEMP_WORK', 'TEMP_IDENTITY_MISMATCH')
    require(token['scope'] == 'temporary_order:create pda:scan pda:submit', 'TEMP_SCOPE_MISMATCH')
    require(proof['authorizationId'] == row[0] and proof['bundleId'] == row[1], 'TEMP_IDENTITY_MISMATCH')
    for signed in (token, proof):
        require(signed['manifestSha256'] == row[14], 'TEMP_MANIFEST_HASH_MISMATCH')
        require(signed['iat'] == int(row[9]) and signed['exp'] == int(row[10]), 'TEMP_ISSUANCE_MISMATCH')
    require(int(row[10]) - int(row[9]) == 172800, 'TEMP_ISSUANCE_MISMATCH')
    require(instant(manifest['issuedAt']).timestamp() == int(row[9]) and
            instant(manifest['expiresAt']).timestamp() == int(row[10]), 'TEMP_ISSUANCE_MISMATCH')
    require(manifest['inventorySnapshotId'] == row[16] and manifest['mappingRevision'] == row[17]
            and manifest['policyVersion'] == row[18], 'TEMP_BASELINE_MISMATCH')
    return manifest


def verify_part(compressed, part):
    require(len(compressed) == part['compressedBytes'] and digest(compressed) == part['compressedSha256'],
            'TEMP_COMPRESSED_PART_MISMATCH')
    with gzip.GzipFile(fileobj=io.BytesIO(compressed)) as stream:
        raw = stream.read(16 * 1024 * 1024 + 1)
    require(len(raw) <= 16 * 1024 * 1024 and len(raw) == part['bytes'] and digest(raw) == part['sha256'],
            'TEMP_RAW_PART_MISMATCH')


def verify_parts(db, authorization, manifest):
    descriptors = manifest['parts']
    require(len(descriptors) == 5 and {p['name'] for p in descriptors} == PARTS, 'TEMP_PART_SET_MISMATCH')
    key = UUID(authorization).hex
    rows = db.query(f"SELECT part_name,HEX(compressed) FROM temporary_preparation_part WHERE authorization_id=UNHEX('{key}');")
    parts = dict(line.split('\t') for line in rows.splitlines())
    require(set(parts) == PARTS, 'TEMP_PART_SET_MISMATCH')
    for descriptor in descriptors:
        verify_part(bytes.fromhex(parts[descriptor['name']]), descriptor)


def verify_preparations(db, keys):
    query = '''SELECT BIN_TO_UUID(a.id),BIN_TO_UUID(a.bundle_id),BIN_TO_UUID(a.warehouse_id),BIN_TO_UUID(a.device_id),
        BIN_TO_UUID(a.user_id),a.authorization_epoch,a.user_auth_version,a.device_version,a.recovery_epoch,
        CAST(UNIX_TIMESTAMP(a.issued_at) AS UNSIGNED),CAST(UNIX_TIMESTAMP(a.expires_at) AS UNSIGNED),a.key_id,
        a.signed_token,HEX(a.manifest_text),a.manifest_sha256,a.signed_manifest,BIN_TO_UUID(a.inventory_snapshot_id),
        a.mapping_revision,p.version FROM temporary_authorization a LEFT JOIN policy_version p ON p.id=a.policy_id ORDER BY a.id;'''
    rows = db.query(query).splitlines()
    for line in rows:
        row = line.split('\t')
        manifest = verify_authorization(row, keys)
        verify_parts(db, row[0], manifest)
    return {'temporary_qualifications_verified': len(rows), 'temporary_parts_verified': len(rows) * 5,
            'temporary_origins_verified': int(db.query('SELECT COUNT(*) FROM temporary_work_origin;')),
            'temporary_audit_events_verified': int(db.query('SELECT COUNT(*) FROM pda_audit_event WHERE assignment_id IS NULL;'))}
