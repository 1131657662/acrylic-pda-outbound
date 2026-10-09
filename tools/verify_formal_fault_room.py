import hashlib
import json
import sqlite3
import sys
from pathlib import Path


def connect(path):
    assert path.is_file(), str(path)
    db = sqlite3.connect(path.resolve().as_uri() + '?mode=ro', uri=True)
    db.row_factory = sqlite3.Row
    assert db.execute('PRAGMA integrity_check').fetchone()[0] == 'ok'
    assert db.execute('PRAGMA foreign_key_check').fetchall() == []
    return db


def verify(attempt):
    with connect(attempt / 'private/android-room.db') as db:
        stage = dict(db.execute('SELECT * FROM local_handover_stage').fetchone())
        local = dict(db.execute('SELECT * FROM local_submission').fetchone())
        continuation = [tuple(row) for row in db.execute('SELECT * FROM local_handover_continuation')]
    manifest = json.loads(stage['manifestJson'])
    assert manifest['stopKind'] == 'FAULT_FENCE' and 'stopReceipt' not in manifest
    assert manifest['faultReceipt']['businessDecision']['oldDeviceAcknowledged'] is False
    assert manifest['faultReceipt']['businessDecision']['archivedEvidence']['sourceKind'] == 'FORMAL'
    assert hashlib.sha256(stage['recoveryBytes']).hexdigest() == stage['recoverySha256']
    assert len(continuation) == 1
    assert local['state'] == 'FROZEN' and local['receipt'] is None
    assert hashlib.sha256(local['payload'].encode()).hexdigest() == local['sha256']
    with connect(attempt / 'private/formal-success-room.db') as db:
        after = dict(db.execute('SELECT * FROM local_submission').fetchone())
        assert dict(db.execute('SELECT * FROM local_handover_stage').fetchone()) == stage
        assert [tuple(row) for row in db.execute('SELECT * FROM local_handover_continuation')] == continuation
    assert after['state'] == 'SYNCED' and after['autoRetry'] == 0
    for field in ('id', 'assignmentId', 'payload', 'sha256', 'currentMarker'):
        assert after[field] == local[field]
    receipt = json.loads(after['receipt'])
    assert receipt['status'] == 'SUCCEEDED' and receipt['payloadSha256'] == local['sha256']
    assert receipt['inventoryDeducted'] is True
    assert receipt['actualCount'] == '2' and receipt['actualWeightGrams'] == '601500'
    result = json.loads((attempt / 'formal-success-android.json').read_text())
    assert result['lostRead'] and result['roomTailRollback'] and result['reopened']
    assert result['localDeletionAllowed'] is False
    return {'consumer': 'Independent read-only SQLite/hash', 'frozenAndSuccessBytesEqual': True,
            'immutableStageAndContinuation': True, 'oneDurableSuccess': True, 'syntheticIdentities': True}


attempt = Path(sys.argv[1]).resolve()
assert (attempt / 'test-completed.marker').is_file()
print(json.dumps(verify(attempt), ensure_ascii=False, indent=2))
