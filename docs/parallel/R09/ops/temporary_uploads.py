"""Verify declared V33 uploads without treating unaccepted chunks as a payload."""
import hashlib
import re
from archive_proofs import parse
from safety import child, regular, require, sha

CHUNK = 262144


def geometry(upload, chunks):
    size, count = upload['size'], upload['count']
    require(type(size) is int and 0 < size <= 33554432 and count == (size + CHUNK - 1) // CHUNK,
            'TEMP_UPLOAD_GEOMETRY_MISMATCH')
    require(bool(re.fullmatch('[a-f0-9]{64}', upload['hash'])), 'TEMP_UPLOAD_HASH_INVALID')
    indices = [c['index'] for c in chunks]
    require(len(set(indices)) == len(indices), 'TEMP_CHUNK_INDEX_MISMATCH')
    for chunk in chunks:
        index = chunk['index']
        require(type(index) is int and 0 <= index < count, 'TEMP_CHUNK_INDEX_MISMATCH')
        require(chunk['size'] == min(CHUNK, size - index * CHUNK), 'TEMP_CHUNK_SIZE_MISMATCH')


def verify_upload(db, files, key, payload, status):
    rows = db.query("SELECT JSON_OBJECT('hash',payload_sha256,'size',byte_count,'count',chunk_count) "
                    f"FROM submission_upload WHERE submission_id=UNHEX('{key}');").splitlines()
    if not rows:
        require(status != 'RECEIVING', 'TEMP_UPLOAD_STATE_MISMATCH')
        return 0
    require(status != 'REGISTERED', 'TEMP_UPLOAD_STATE_MISMATCH')
    upload = parse(rows[0])
    chunks = [parse(line) for line in db.query("SELECT JSON_OBJECT('index',chunk_index,'size',byte_count,"
        "'hash',sha256,'file',file_key) FROM submission_chunk "
        f"WHERE submission_id=UNHEX('{key}') ORDER BY chunk_index;").splitlines()]
    geometry(upload, chunks)
    combined = hashlib.sha256()
    for chunk in chunks:
        path = regular(child(files, chunk['file']))
        require(path.stat().st_size == chunk['size'] and sha(path) == chunk['hash'], 'TEMP_CHUNK_FILE_MISMATCH')
        combined.update(path.read_bytes())
    if len(chunks) == upload['count']:
        require(combined.hexdigest() == upload['hash'], 'TEMP_CHUNK_ASSEMBLY_MISMATCH')
    if payload:
        require(payload['hash'] == upload['hash'] and payload['size'] == upload['size'], 'TEMP_ACCEPTED_UPLOAD_MISMATCH')
    return len(chunks)
