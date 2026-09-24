"""Check retained V34 rows and referenced bytes; never attest source truth."""
import json
import re
from safety import child, regular, require, sha


def rows(db, query, width):
    for line in db.query(query).splitlines():
        try:
            row = json.loads(line)
        except (ValueError, TypeError):
            require(False, 'EVIDENCE_ROW_INVALID')
        require(isinstance(row, list) and len(row) == width, 'EVIDENCE_ROW_INVALID')
        yield row


def valid_id(value):
    require(isinstance(value, str) and bool(re.fullmatch('[0-9A-F]{32}', value)), 'EVIDENCE_ID_INVALID')


def valid_hash(value):
    require(isinstance(value, str) and bool(re.fullmatch('[a-f0-9]{64}', value)), 'EVIDENCE_HASH_INVALID')


def objects(db, files):
    query = '''SELECT JSON_ARRAY(HEX(id),HEX(scope_id),HEX(submission_id),work_round,kind,
        HEX(original_object_id),bytes_sha256,byte_count,file_key) FROM source_evidence_object ORDER BY id;'''
    found, references = {}, {}
    for row in rows(db, query, 9):
        object_id, scope, submission, round_no, kind, original, expected, size, name = row
        for value in (object_id, scope, submission, original):
            valid_id(value)
        valid_hash(expected)
        require(isinstance(name, str) and name and isinstance(size, int) and size > 0, 'EVIDENCE_ROW_INVALID')
        require(isinstance(round_no, int) and round_no > 0 and object_id not in found, 'EVIDENCE_ROW_INVALID')
        require(name not in references or references[name] == (expected, size), 'EVIDENCE_FILE_KEY_CONFLICT')
        path = regular(child(files, name))
        require(path.stat().st_size == size, 'EVIDENCE_FILE_SIZE_MISMATCH')
        require(sha(path) == expected, 'EVIDENCE_FILE_HASH_MISMATCH')
        references[name] = (expected, size)
        found[object_id] = (scope, submission, round_no, kind)
    return found


def sets(db, found):
    query = '''SELECT JSON_ARRAY(HEX(id),HEX(scope_id),HEX(submission_id),work_round,
        HEX(stop_object_id),set_sha256,item_count,HEX(manifest_bytes)) FROM source_evidence_set ORDER BY id;'''
    retained = {}
    for row in rows(db, query, 8):
        set_id, scope, submission, round_no, stop, set_hash, count, manifest = row
        valid_id(set_id)
        valid_hash(set_hash)
        require(isinstance(manifest, str) and bool(re.fullmatch('[0-9A-F]+', manifest)), 'EVIDENCE_MANIFEST_MISSING')
        require(found.get(stop) == (scope, submission, round_no, 'STOP_PROOF'), 'EVIDENCE_STOP_REFERENCE_MISMATCH')
        require(isinstance(count, int) and count >= 0 and set_id not in retained, 'EVIDENCE_ROW_INVALID')
        retained[set_id] = (scope, submission, round_no, set_hash, count)
    return retained


def items(db, found, retained):
    query = '''SELECT JSON_ARRAY(HEX(set_id),HEX(object_id),item_kind,record_sha256)
        FROM source_evidence_item ORDER BY set_id,item_kind,original_record_id;'''
    counts = {set_id: 0 for set_id in retained}
    for set_id, object_id, kind, record_hash in rows(db, query, 4):
        valid_hash(record_hash)
        require(set_id in retained and object_id in found, 'EVIDENCE_ITEM_REFERENCE_MISMATCH')
        require(found[object_id][:3] == retained[set_id][:3], 'EVIDENCE_ITEM_REFERENCE_MISMATCH')
        require(kind in ('CAPTURE', 'SCAN', 'AUDIT', 'STOP', 'ARCHIVE'), 'EVIDENCE_ITEM_KIND_INVALID')
        counts[set_id] += 1
    for set_id, count in counts.items():
        require(count == retained[set_id][4], 'EVIDENCE_ITEM_COUNT_MISMATCH')
    return sum(counts.values())


def verifications(db, retained):
    query = '''SELECT JSON_ARRAY(HEX(set_id),set_sha256,result,known_facts_sha256,
        HEX(findings_bytes)) FROM source_evidence_verification ORDER BY set_id,verification_version;'''
    count = 0
    for set_id, set_hash, result, facts_hash, findings in rows(db, query, 5):
        valid_hash(facts_hash)
        require(set_id in retained and set_hash == retained[set_id][3], 'EVIDENCE_VERIFICATION_REFERENCE_MISMATCH')
        require(result in ('PARTIAL', 'VERIFIED_COMPLETE_AT_STOP', 'CONFLICT', 'UNVERIFIABLE'), 'EVIDENCE_ROW_INVALID')
        require(isinstance(findings, str) and bool(re.fullmatch('[0-9A-F]+', findings)), 'EVIDENCE_FINDINGS_MISSING')
        count += 1
    return count


def verify_evidence(db, files):
    found = objects(db, files)
    retained = sets(db, found)
    item_count = items(db, found, retained)
    verification_count = verifications(db, retained)
    return {'evidence_object_files_checked': len(found), 'evidence_sets_checked': len(retained),
            'evidence_items_checked': item_count, 'evidence_verification_rows_checked': verification_count,
            'evidence_scope': 'retained bytes and references only; not trusted VERIFY'}
