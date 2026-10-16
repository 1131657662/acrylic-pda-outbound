"""Verify original E06 signed HTTP bytes and exact retained MySQL table facts."""
import base64
import json
from urllib.parse import urlsplit
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding
from e06_retained_audit_io import BASE, ROOT, SERVICE, SESSIONS
from e06_retained_audit_io import conserved, digest, exact_append, facts, load, read


def canonical(value):
    return json.dumps(value, sort_keys=True, separators=(',', ':'), ensure_ascii=False).encode()


def decode(value):
    return base64.urlsafe_b64decode(value + '=' * (-len(value) % 4))


def signed(row, public):
    receipt = row['receipt']
    header, payload, signature = receipt['signedProof'].split('.')
    assert json.loads(decode(header)) == {'alg': 'RS256', 'typ': 'JWT', 'kid': receipt['signingKeyId']}
    public.verify(decode(signature), (header + '.' + payload).encode(), padding.PKCS1v15(), hashes.SHA256())
    assert json.loads(decode(payload)) == {k: v for k, v in receipt.items() if k not in ('signedProof', 'signingKeyId')}
    assert receipt['requestSha256'] == digest(canonical(row['evidence']))
    assert receipt['contextSha256'] == digest(canonical(row['originalContext']))
    assert receipt['pendingReconciliation'] is True
    for name in ['stockAvailable', 'businessProcessed', 'releaseAllowed', 'localDeletionAllowed']:
        assert receipt[name] is False


def retained_http():
    responses = {}
    statuses = {}
    for directory in ['prepare02', 'enabled01', 'lock01', 'independent01']:
        records = sorted((SERVICE / directory).glob('http-*.result.json'))
        statuses[directory] = []
        for record in records:
            prefix = record.name.removesuffix('.result.json')
            result = load(record)
            request = load(record.parent / (prefix + '.input.json'))
            raw = read(record.parent / (prefix + '.response'))
            statuses[directory].append(result['status'])
            if request['requestSha256']:
                assert digest(read(record.parent / (prefix + '.request'))) == request['requestSha256']
            if directory == 'independent01' and result['status'] == 200:
                responses[urlsplit(request['route']).path] = raw
    return responses, statuses


def physical_original(row, public, responses):
    physical = row['physicalEvidence']
    signed(physical, public)
    assert physical['originalContext'] == row['originalContext']
    relay = physical['originalContext']['original']['relayId']
    prefix = '/api/v1/original-relays/' + relay
    original = responses[prefix + '/original']
    assert digest(original) == physical['originalContext']['original']['archiveSha256']
    for item in physical['evidence']['attachments']:
        raw = responses[prefix + '/physical/attachments/' + item['attachmentId']]
        assert digest(raw) == item['evidenceSha256'] and str(len(raw)) == item['sizeBytes']


def signed_originals(responses):
    key = read(ROOT / '.local/e2a-cross-20260925-1514/private/server-public.der')
    assert key == read(ROOT / '.local/e2a-cross-20260925-1539/private/server-public.der')
    assert digest(key) == '5f17c0234838910f45049ad288e4caeb93c3e7da944ccf776d69cecf2554f559'
    public = serialization.load_der_public_key(key)
    saved = load(SERVICE / 'multitask-source.json')
    group = saved['result']
    signed(group, public)
    assert group['receipt']['newCollectionAuthorized'] is False
    task = group['evidence']['additionalTasks'][0]
    member = group['additionalEvidence'][0]
    assert member['evidence'] == task
    assert digest(canonical(member['originalContext'])) == task['contextSha256']
    assert member['physicalEvidence']['receipt']['receiptId'] == task['physicalCheckId']
    members = [group] + group['additionalEvidence']
    for row in members:
        physical_original(row, public, responses)
    for relay in saved['relays']:
        raw = responses['/api/v1/original-relays/' + relay + '/associations']
        assert json.loads(raw)['data'] == group
    original_route = '/api/v1/original-relays/' + saved['relays'][0] + '/original'
    assert responses[original_route] == read(BASE / 'e06-l2-original-consumer02/actual-original.zip')
    return {'signedReceipts': 3, 'memberOriginals': len(members), 'fixedPublicKeySha256': digest(key)}


def database_facts():
    all_facts = {}
    for sub in ['prepare02', 'enabled01', 'lock01', 'independent01']:
        directories = sorted((SERVICE / sub).glob('facts-*'), key=lambda p: int(p.name.split('-')[1]))
        all_facts[sub] = [facts(path) for path in directories]
    original = load(SERVICE / 'restore-run01/before.json')['tables']
    migrated = all_facts['prepare02'][0]
    conserved(original, {name: migrated[name] for name in original}, {'flyway_schema_history'})
    added = set(migrated) - set(original)
    assert len(added) == 11 and all(migrated[name]['rows'] == 0 for name in added)
    before = SERVICE / 'enabled01/facts-23'
    after = SERVICE / 'enabled01/facts-24'
    positive = exact_append(before, after, {name: 1 for name in [
        'independent_association', 'independent_association_member', 'api_idempotency_request', 'operation_event']})
    for index in range(0, 30, 2):
        if index == 22:
            continue
        conserved(all_facts['enabled01'][index], all_facts['enabled01'][index + 1], SESSIONS)
    conserved(*all_facts['lock01'], SESSIONS | {'operation_event'})
    for index in [0, 2]:
        conserved(*all_facts['independent01'][index:index + 2], SESSIONS)
    assert b'LOCK WAIT' in read(SERVICE / 'lock01/actual-lock-wait.tsv')
    assert load(SERVICE / 'lock01/revoked-after-wait.json') == {'status': 401, 'code': 'SESSION_REVOKED'}
    assert load(SERVICE / 'enabled01/tail-rollback.json')['allBusinessFactsUnchanged']
    return {'factCaptures': sum(map(len, all_facts.values())), 'positiveExactAppend': positive,
            'refusalReplayPairs': 14, 'actualLockWaitRevoked': True,
            'originalTables': len(original), 'nonemptyOriginalTables': sum(r['rows'] > 0 for r in original.values())}


def preserve():
    base = BASE / 'preserve-1539-e06-l2-service-01'
    rows = load(base / 'data-before.json')
    after = load(base / 'data-after.json')
    assert {row['path'] for row in rows} == {row['path'] for row in after}
    assert all(row['unchanged'] is True for row in after)
    for row in rows:
        raw = read(row['path'])
        assert len(raw) == row['length'] and digest(raw).lower() == row['sha256'].lower()
    dump = read(base / 'database.sql')
    assert digest(dump) == load(base / 'dump-sha256.json')['Hash'].lower()
    result = load(base / 'result.json')
    assert result['shutdownExit'] == result['dumpExit'] == result['listeners'] == 0
    return {'protectedFilesStillExact': len(rows), 'dumpSha256': digest(dump), 'historicalStop': result}


def audit():
    responses, statuses = retained_http()
    return {'httpStatuses': statuses, 'signatures': signed_originals(responses),
            'database': database_facts(), 'preservation': preserve()}
