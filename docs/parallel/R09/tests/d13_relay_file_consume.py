"""Independent ZIP/member/signature, original-download and database comparison."""
import base64
import hashlib
import io
import json
import uuid
import zipfile

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec
from d13_import_dump import table


def load(path):
    return json.loads(path.read_bytes())


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def archive(path):
    with zipfile.ZipFile(io.BytesIO(path.read_bytes())) as source:
        names = source.namelist()
        assert len(names) == len(set(names)) == 12 and source.testzip() is None
        assert not any('/' in name or '\\' in name for name in names)
        files = {name: source.read(name) for name in names}
    manifest = json.loads(files['manifest.json'])
    assert manifest['purpose'] == 'PDA_READONLY_RESCUE_V1'
    assert manifest['deviceKeySha256'] == hashlib.sha256(files['device-public-key.der']).hexdigest()
    key = serialization.load_der_public_key(files['device-public-key.der'])
    assert isinstance(key, ec.EllipticCurvePublicKey) and isinstance(key.curve, ec.SECP256R1)
    key.verify(files['manifest.sig'], b'PDA_READONLY_RESCUE_V1\n' + files['manifest.json'], ec.ECDSA(hashes.SHA256()))
    assert set(manifest['files']) == set(files) - {'manifest.json', 'manifest.sig', 'device-public-key.der'}
    for name, fact in manifest['files'].items():
        assert int(fact['bytes']) == len(files[name]) and fact['sha256'] == hashlib.sha256(files[name]).hexdigest()
    assert 'submission.json' not in files and 'receipt.json' not in files
    return manifest, json.loads(files['order.json']), files['device-public-key.der']


def content(attempt, prefix, database):
    receipt = load(attempt / ('relay-' + prefix + '.json'))
    source, download = attempt / (prefix + '.zip'), attempt / (prefix + '-download.zip')
    assert source.read_bytes() == download.read_bytes()
    assert digest(source) == receipt['archiveSha256']
    stored = attempt / ('storage/original-relays/' + digest(source) + '.zip')
    assert source.read_bytes() == stored.read_bytes()
    manifest, order, public = archive(source)
    assert manifest['exportId'] == receipt['exportId']
    assert receipt['sourceTrust'] == ('UNVERIFIED' if prefix == 'unverified-content' else 'TRUSTED')
    assert receipt['receptionState'] == 'ORIGINALS_STORED'
    assert receipt['businessProcessed'] is receipt['localDeletionAllowed'] is False and receipt['submissionId'] is None
    check_database(receipt, order, public, database)
    originals = [p for p in (attempt / 'private').glob('http-relay-*/*.body') if p.read_bytes() == source.read_bytes()]
    assert originals, 'No original HTTP download equals archive'
    responses = [load(p) for p in (attempt / 'private').glob('http-relay-*/*.body') if p.read_bytes().startswith(b'{')]
    assert receipt in [row.get('data') for row in responses]
    return dict(id=receipt['relayId'], exportId=receipt['exportId'], sha256=digest(source), warehouse=order['warehouseId'])


def check_database(receipt, order, public, database):
    identifier = uuid.UUID(receipt['relayId']).hex
    rows = [row for row in database['original_relay'] if row['id'] == identifier]
    assert len(rows) == 1
    row = rows[0]
    assert row['archive_sha256'] == receipt['archiveSha256'] and row['source_trust'] == receipt['sourceTrust']
    assert row['export_id'] == uuid.UUID(receipt['exportId']).hex
    for column, field in [('warehouse_id', 'warehouseId'), ('original_device_id', 'deviceId'), ('original_operator_id', 'operatorId')]:
        assert row[column] == uuid.UUID(order[field]).hex
    devices = [device for device in database['device'] if device['id'] == uuid.UUID(order['deviceId']).hex]
    assert len(devices) == 1
    assert (base64.b64decode(devices[0]['public_key'], validate=True) == public) == (receipt['sourceTrust'] == 'TRUSTED')


def product(attempt):
    path = attempt / 'private/final-dump.stdout'
    sql = path.read_text('utf-8')
    names = ('original_relay', 'device', 'api_idempotency_request', 'operation_event',
             'submission', 'dispatch_record', 'inventory_ledger')
    database = {name: table(sql, name) for name in names}
    prefixes = ('off-positive', 'p5-positive', 'unverified-content', 'concurrent-content', 'held-content', 'tail-recovered')
    artifacts = {name: content(attempt, name, database) for name in prefixes}
    assert len({row['id'] for row in artifacts.values()}) == 6 and len(database['original_relay']) == 6
    before = load(attempt / 'relay-business.json')
    assert all(len(database[name]) == count for name, count in before.items())
    facts(attempt, database, artifacts)
    return dict(artifacts=artifacts, noBusinessEffects=before, dumpSha256=digest(path))


def facts(attempt, database, artifacts):
    concurrent, held, rollback, revoked = (load(attempt / ('relay-' + name + '.json'))
                                         for name in ('concurrent', 'held', 'rollback', 'revoked'))
    assert concurrent['records'] == 1 and concurrent['id'] == artifacts['concurrent-content']['id']
    assert all(held[k] is True for k in ('actualLockWait', 'fileBeforeCommit', 'independentWriterBlocked'))
    assert held['id'] == artifacts['held-content']['id']
    assert rollback['transactionRolledBack'] and rollback['idempotencyRowsAfterFailure'] == 0
    assert rollback['id'] == artifacts['tail-recovered']['id']
    assert revoked['actualLockWait'] and revoked['noRelay']
    assert not any(row['idempotency_key'] == revoked['key'] for row in database['api_idempotency_request'])
    for fact in (concurrent, held, rollback):
        records = [row for row in database['api_idempotency_request'] if row['idempotency_key'] == fact['key']]
        assert len(records) == 1 and records[0]['resource_id'] == uuid.UUID(fact['id']).hex
    for artifact in artifacts.values():
        events = [row for row in database['operation_event']
                  if row['entity_id'] == uuid.UUID(artifact['id']).hex and row['event_type'] == 'relay.receive']
        assert len(events) == 1
