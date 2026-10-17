"""Independently compare original GB18030 bytes, HTTP and retained database rows."""
from decimal import Decimal
import hashlib
import json
import uuid

from d13_import_dump import table


def load(path):
    return json.loads(path.read_bytes())


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def source_rows(path):
    data = path.read_bytes()
    assert len(data) == 118
    rows = []
    for offset in (0, 59):
        raw = data[offset:offset + 59]
        assert raw[57:] == b'\r\n'
        fields = [raw[a:b].decode('gb18030').strip() for a, b in ((0, 20), (20, 34), (34, 47), (47, 57))]
        grams = Decimal(fields[3]) * 1000
        assert grams == grams.to_integral_value()
        rows.append(dict(model=fields[0], name=fields[1], barcode=fields[2], rawWeight=fields[3],
                         deliveryCode=fields[2][:3], weightGrams=str(int(grams))))
    assert sum(int(row['weightGrams']) for row in rows) == 601500
    return rows


def content(attempt, prefix, database):
    record = load(attempt / ('import-' + prefix + '.json'))
    path = attempt / (prefix + '.sku')
    assert digest(path) == record['fileSha256'] == record['view']['fileSha256']
    persisted = attempt / ('storage/inventory-imports/' + digest(path) + '.txt')
    assert path.read_bytes() == persisted.read_bytes()
    expected = source_rows(path)
    view, rows = record['view'], record['rows']['items']
    assert view['status'] == 'READY_FOR_REVIEW' and view['publishedSnapshotId'] is None
    assert view['validRows'] == 2 and view['totalWeightGrams'] == '601500'
    assert [row['parsedFields'] for row in rows] == expected
    original = path.read_bytes()
    assert [row['rawText'] for row in rows] == [original[a:a + 57].decode('gb18030') for a in (0, 59)]
    assert [row['lineNo'] for row in rows] == [1, 2] and not record['rows']['hasMore']
    raw_http = [load(p).get('data') for p in (attempt / 'private').glob('http-import-*/*.body')]
    assert view in raw_http and record['rows'] in raw_http
    check_database(record, rows, database)
    return dict(id=record['id'], warehouseId=view['warehouseId'], sha256=digest(path),
                barcodes=[row['barcode'] for row in expected])


def check_database(record, rows, database):
    identifier = uuid.UUID(record['id']).hex
    imports = [row for row in database['inventory_import'] if row['id'] == identifier]
    assert len(imports) == 1
    item = imports[0]
    assert item['file_sha256'] == record['fileSha256'] and item['total_weight_g'] == 601500
    assert item['status'] == 'READY_FOR_REVIEW' and item['published_snapshot_id'] is None
    stored = sorted((row for row in database['inventory_import_row'] if row['import_id'] == identifier),
                    key=lambda row: row['line_no'])
    assert len(stored) == 2
    for sql, http in zip(stored, rows):
        assert sql['raw_text'] == http['rawText'] and sql['error_code'] is None
        assert json.loads(sql['parsed_fields']) == http['parsedFields']


def product(attempt):
    path = attempt / 'private/final-dump.stdout'
    sql = path.read_text('utf-8')
    names = ('inventory_import', 'inventory_import_row', 'api_idempotency_request', 'operation_event')
    database = {name: table(sql, name) for name in names}
    prefixes = ('off-positive', 'p5-positive', 'concurrent-content', 'held-content', 'tail-recovered')
    artifacts = {name: content(attempt, name, database) for name in prefixes}
    assert len({row['id'] for row in artifacts.values()}) == 5
    identifiers = {uuid.UUID(row['id']).hex for row in artifacts.values()}
    warehouse = uuid.UUID(artifacts['off-positive']['warehouseId']).hex
    assert {row['id'] for row in database['inventory_import'] if row['warehouse_id'] == warehouse} == identifiers
    assert sum(row['import_id'] in identifiers for row in database['inventory_import_row']) == 10
    facts(attempt, database, artifacts)
    return dict(artifacts=artifacts, dumpSha256=digest(path))


def facts(attempt, database, artifacts):
    concurrent, held, rollback, revoked = (load(attempt / ('import-' + name + '.json'))
                                         for name in ('concurrent', 'held', 'rollback', 'revoked'))
    assert concurrent['records'] == 1 and concurrent['id'] == artifacts['concurrent-content']['id']
    assert all(held[k] is True for k in ('actualLockWait', 'fileBeforeCommit', 'independentWriterBlocked'))
    assert held['id'] == artifacts['held-content']['id']
    assert rollback['transactionRolledBack'] and rollback['idempotencyRowsAfterFailure'] == 0
    assert rollback['id'] == artifacts['tail-recovered']['id']
    assert revoked['actualLockWait'] and revoked['noImport']
    records = database['api_idempotency_request']
    assert not any(row['idempotency_key'] == revoked['key'] for row in records)
    for value in (concurrent, held, rollback):
        matches = [row for row in records if row['idempotency_key'] == value['key']]
        assert len(matches) == 1 and matches[0]['resource_id'] == uuid.UUID(value['id']).hex
    for artifact in artifacts.values():
        events = [row for row in database['operation_event']
                  if row['entity_id'] == uuid.UUID(artifact['id']).hex and row['event_type'] == 'INVENTORY_PREFLIGHT']
        assert len(events) == 1
