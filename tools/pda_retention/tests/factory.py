"""Generate a clearly labelled, guard-free synthetic Room-shaped database."""
from contextlib import closing
import json
import os
from pathlib import Path
import sqlite3

from ..catalog import create, schema
from ..codec import canonical, digest, parse, require
from ..store import ROOT, connect, healthy, read, write_new

FIXTURE = ROOT / 'server/build/pda-archive-fixture.json'


def fixture_data():
    explicit = os.environ.get('PDA_RETENTION_FIXTURE')
    if not explicit:
        return parse(FIXTURE.read_bytes()), digest(FIXTURE.read_bytes())
    raw = read(explicit)
    require(digest(raw) == os.environ.get('PDA_RETENTION_FIXTURE_SHA256'),
            'Explicit test fixture pin mismatch')
    return parse(raw), digest(raw)


def archive_files(directory, data):
    files = {}
    for name, key in [('payload', 'body'), ('receipt', 'receipt')]:
        path = directory / ('retained-' + name + '.json')
        write_new(path, data[key])
        files[name] = str(path)
    return files


def insert(db, table, overrides):
    fields = list(db.execute('PRAGMA table_info("' + table + '")'))
    row = {f[1]: (0 if f[2] == 'INTEGER' else 'synthetic-unused') if f[3] else None for f in fields}
    row.update(overrides)
    columns = ','.join('"' + name + '"' for name in row)
    db.execute('INSERT INTO "' + table + '" (' + columns + ') VALUES (' +
               ','.join('?' for _ in row) + ')', tuple(row.values()))


def create_source(directory):
    directory.mkdir(parents=True)
    data, fixture_sha = fixture_data()
    model, schema_sha = schema()
    path = directory / 'synthetic-source.sqlite'
    with closing(sqlite3.connect(path)) as db:
        create(db, model)
        populate(db, data)
        db.commit()
    with closing(connect(path)) as db:
        healthy(db)
    spec = dict(scope='SYNTHETIC_GENERATED_ONLY', production=False, source=str(path),
                sourceSha256=digest(path.read_bytes()), schemaSha256=schema_sha,
                submissionId=data['body']['submissionId'], keySha256=digest(canonical(data['signingKey'])),
                now='2027-01-05T00:00:00Z', fixtureSha256=fixture_sha,
                archiveFiles=archive_files(directory, data))
    write_new(directory / 'synthetic-only.json', spec)
    return directory / 'synthetic-only.json', data


def populate(db, data):
    body, archive = data['body'], data['confirmation']
    assignment, order = body['assignmentId'], body['source']['sourceOrderId']
    insert(db, 'prepared_order', dict(assignmentId=assignment, orderId=order,
           operatorId=archive['operatorId'], deviceId=archive['freezeDeviceId'],
           signingKeyJson=canonical(data['signingKey']).decode()))
    insert(db, 'work_order', dict(assignmentId=assignment, orderId=order, state='FROZEN',
           actualCount=2, weightGrams=601500, nextOrderScanSeq=3))
    for capture in body['captures']:
        insert(db, 'work_capture', {**capture, 'originAssignmentId': assignment, 'inputRevision': 1})
    for scan in body['scans']:
        insert(db, 'work_scan', {**scan, 'assignmentId': assignment})
        insert(db, 'prepared_bale', {k: v for k, v in dict(scan, assignmentId=assignment).items()
               if k in {'assignmentId', 'barcode', 'baleId', 'deliveryCode', 'model', 'name', 'rawWeight', 'weightGrams'}})
        insert(db, 'work_line', dict(assignmentId=assignment, deliveryCode=scan['deliveryCode'],
               plannedCount=1, actualCount=1, weightGrams=scan['weightGrams']))
    for event in body['events']:
        insert(db, 'work_event', {**event, 'originAssignmentId': assignment, 'orderPosition': event['localSeq']})
    insert(db, 'local_submission', dict(id=body['submissionId'], assignmentId=assignment,
           payload=canonical(body).decode(), sha256=archive['payloadSha256'], state='SYNCED',
           receipt=canonical(data['receipt']).decode(), autoRetry=0, currentMarker=1))
    insert(db, 'local_submission_archive', dict(submissionId=body['submissionId'],
           recoveryEpoch=archive['recoveryEpoch'], payloadSha256=archive['payloadSha256'],
           receiptSha256=archive['receiptSha256'], confirmationJson=canonical(archive).decode(),
           signingKeyJson=canonical(data['signingKey']).decode()))
    unrelated(db)


def unrelated(db):
    insert(db, 'local_recovery_control', dict(singletonId=1, mode='NORMAL', observedServerEpoch='1', recoverySessionId=None))
    insert(db, 'work_device', dict(deviceId='unrelated-device', nextCaptureSeq=17, nextEventSeq=27))
    insert(db, 'prepared_order', dict(assignmentId='unrelated-assignment', orderId='unrelated-order'))
    insert(db, 'work_order', dict(assignmentId='unrelated-assignment', orderId='unrelated-order', state='WORKING'))
    insert(db, 'local_submission', dict(id='unrelated-submission', assignmentId='unrelated-assignment', state='PENDING'))


def mutate(spec_path, sql, values=()):
    spec = parse(spec_path.read_bytes())
    with closing(connect(spec['source'], True)) as db:
        db.execute(sql, values)
    spec['sourceSha256'] = digest(Path(spec['source']).read_bytes())
    spec_path.write_bytes(canonical(spec))


def allow(prepared, allowed=True):
    run = Path(prepared['run'])
    (run / 'permit.json').write_bytes(canonical(dict(planSha256=prepared['planSha256'],
                                                  runId=run.name, allowed=allowed)))
