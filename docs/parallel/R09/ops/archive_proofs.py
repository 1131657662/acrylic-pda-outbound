"""Read-only checks of the existing R05 signed archive format and durable files."""
from calendar import monthrange
from datetime import datetime, timezone
import json
import re
from cryptography.exceptions import InvalidSignature
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.asymmetric import padding
from receipts import decode
from safety import child, regular, require, digest, OpsError


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        require(key not in result, 'ARCHIVE_DUPLICATE_JSON_KEY')
        result[key] = value
    return result


def parse(value):
    result = json.loads(value, object_pairs_hook=unique_object)
    canonical(result)
    return result


def canonical(value):
    # Match SubmissionJson/Jackson: UTF-16 key order, uppercase control escapes.
    if isinstance(value, dict):
        keys = sorted(value, key=lambda key: key.encode('utf-16-be'))
        return '{' + ','.join(canonical(key) + ':' + canonical(value[key]) for key in keys) + '}'
    if isinstance(value, list):
        return '[' + ','.join(canonical(item) for item in value) + ']'
    if isinstance(value, str):
        escapes = {'"': '\\"', '\\': '\\\\', '\b': '\\b', '\f': '\\f', '\n': '\\n', '\r': '\\r', '\t': '\\t'}
        return '"' + ''.join(escapes.get(c, f'\\u{ord(c):04X}' if ord(c) < 32 else c) for c in value) + '"'
    require(value is None or isinstance(value, bool) or type(value) is int and abs(value) <= 9007199254740991,
            'ARCHIVE_UNSUPPORTED_JSON_VALUE')
    return json.dumps(value)


def instant(value):
    at = datetime.fromisoformat(value)
    require(at.tzinfo is not None, 'ARCHIVE_TIMEZONE_REQUIRED')
    return at.astimezone(timezone.utc)


def verify_proof(proof, keys, audience):
    public, key_id = keys
    head, body, signature = proof['signedArchive'].split('.')
    header, claims = parse(decode(head)), parse(decode(body))
    require(header == {'alg': 'RS256', 'typ': 'JWT', 'kid': key_id}, 'ARCHIVE_KEY_MISMATCH')
    try:
        public.verify(decode(signature), (head + '.' + body).encode('ascii'), padding.PKCS1v15(), hashes.SHA256())
    except InvalidSignature:
        raise OpsError('ARCHIVE_SIGNATURE_INVALID') from None
    require(claims.get('iss') == 'acrylic-outbound' and claims.get('aud') == audience, 'ARCHIVE_CONTEXT_MISMATCH')
    require(canonical(claims.get('archive')) == canonical({k: v for k, v in proof.items() if k != 'signedArchive'}), 'ARCHIVE_BODY_MISMATCH')
    allowed = (1, 2) if audience == 'acrylic-pda-archive' else (1,)
    require(type(proof['archiveVersion']) is int and proof['archiveVersion'] in allowed, 'ARCHIVE_VERSION_UNSUPPORTED')
    require(proof['localDeletionAllowed'] is False, 'ARCHIVE_DELETION_NOT_ALLOWED')
    require(proof['storagePolicy'] == 'SERVER_DURABLE_FILES_V1', 'ARCHIVE_STORAGE_POLICY_UNSUPPORTED')
    at = instant(proof['archivedAt'])
    five_years = at.replace(year=at.year + 5, day=min(at.day, monthrange(at.year + 5, at.month)[1]))
    require(instant(proof['retainUntil']) >= five_years, 'ARCHIVE_RETENTION_TOO_SHORT')


def content(files, checksum, size):
    require(bool(re.fullmatch('[a-f0-9]{64}', checksum)), 'ARCHIVE_DIGEST_INVALID')
    path = regular(child(files, f'submissions/{checksum}.json'))
    require(0 < path.stat().st_size == int(size) <= 33554432, 'ARCHIVE_FILE_SIZE_MISMATCH')
    value = path.read_bytes()
    require(digest(value) == checksum, 'ARCHIVE_FILE_HASH_MISMATCH')
    return parse(value)
