"""Insert plainly synthetic V34 file references into a new private source only."""
from uuid import uuid4
import re
from safety import child, digest, require


def identity(db):
    query = '''SELECT HEX(c.warehouse_id),HEX(c.id),HEX(s.id),s.work_round,
        HEX(s.freeze_device_id),HEX(s.operator_id) FROM submission_scope c
        JOIN submission s ON s.scope_id=c.id WHERE c.creator_device_id IS NOT NULL
        AND s.status='REVIEW_REQUIRED' ORDER BY s.id LIMIT 1;'''
    values = db.query(query).split('\t')
    require(len(values) == 6 and all(re.fullmatch('[0-9A-F]{32}', part) for part in
            (values[:3] + values[4:])), 'V34_SYNTHETIC_PARENT_MISSING')
    require(values[3].isdigit() and int(values[3]) > 0, 'V34_SYNTHETIC_PARENT_MISSING')
    return values


def store_object(db, parent, kind, content):
    object_id, original_id = uuid4().hex.upper(), uuid4().hex.upper()
    name = f'r09-v34/{object_id.lower()}.bin'
    path = child(db.root / 'files', name)
    path.parent.mkdir(mode=0o700, exist_ok=True)
    with path.open('xb') as stream:
        stream.write(content)
    warehouse, scope, submission, round_no, device, actor = parent
    sql = '''INSERT INTO source_evidence_object(id,warehouse_id,scope_id,submission_id,work_round,
        kind,original_object_id,bytes_sha256,byte_count,file_key,origin_device_id,uploaded_by,received_at)
        VALUES(UNHEX('{id}'),UNHEX('{warehouse}'),UNHEX('{scope}'),UNHEX('{submission}'),{round_no},
        '{kind}',UNHEX('{original}'),'{hash}',{size},'{name}',UNHEX('{device}'),UNHEX('{actor}'),UTC_TIMESTAMP(3));'''
    db.query(sql.format(id=object_id, warehouse=warehouse, scope=scope, submission=submission,
                        round_no=round_no, kind=kind, original=original_id, hash=digest(content),
                        size=len(content), name=name, device=device, actor=actor))
    return object_id


def seed_evidence(db):
    parent = identity(db)
    stop = store_object(db, parent, 'STOP_PROOF', b'R09 V34 synthetic STOP bytes; NOT device evidence\n')
    capture = store_object(db, parent, 'CAPTURE_RECORDS', b'R09 V34 synthetic capture bytes; NOT device evidence\n')
    warehouse, scope, submission, round_no, _, _ = parent
    set_id, record_id, verification_id = (uuid4().hex.upper() for _ in range(3))
    manifest = b'{"synthetic":true,"trustedVerify":false}'
    findings = b'{"status":"PARTIAL","synthetic":true}'
    set_hash = digest(manifest)
    db.query(f'''INSERT INTO source_evidence_set(id,warehouse_id,scope_id,submission_id,work_round,
        stop_object_id,manifest_bytes,set_sha256,capture_high_watermark,audit_through_seq,item_count,received_at)
        VALUES(UNHEX('{set_id}'),UNHEX('{warehouse}'),UNHEX('{scope}'),UNHEX('{submission}'),{round_no},
        UNHEX('{stop}'),UNHEX('{manifest.hex()}'),'{set_hash}',1,1,1,UTC_TIMESTAMP(3));''')
    db.query(f'''INSERT INTO source_evidence_item(set_id,warehouse_id,scope_id,submission_id,work_round,
        item_kind,original_record_id,object_id,original_device_seq,record_sha256,record_status)
        VALUES(UNHEX('{set_id}'),UNHEX('{warehouse}'),UNHEX('{scope}'),UNHEX('{submission}'),{round_no},
        'CAPTURE',UNHEX('{record_id}'),UNHEX('{capture}'),1,'{digest(b'synthetic record')}', 'SYNTHETIC');''')
    db.query(f'''INSERT INTO source_evidence_verification(id,set_id,scope_id,submission_id,work_round,
        verification_version,algorithm_version,set_sha256,result,known_facts_sha256,findings_bytes,verified_at)
        VALUES(UNHEX('{verification_id}'),UNHEX('{set_id}'),UNHEX('{scope}'),UNHEX('{submission}'),{round_no},
        1,1,'{set_hash}','PARTIAL','{digest(b'synthetic unknown facts')}',UNHEX('{findings.hex()}'),UTC_TIMESTAMP(3));''')
    return {'synthetic_objects': 2, 'synthetic_sets': 1, 'synthetic_items': 1, 'synthetic_partial_rows': 1}
