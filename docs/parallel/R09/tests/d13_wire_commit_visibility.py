"""Verify actual packet loss, original driver failure, independent committed visibility and product fencing."""
import json

from d15_combination_runtime import digest
from d13_commit_visibility import facts
from d13_wire_packets import is_commit, commit_ok
from verify_d13_restored_closed import load


def observations(attempt):
    private = attempt / 'private'
    home = private / 'restored-bound-wire-commit'
    inner = load(home / 'jdbc-before-commit.json')
    before, after = [load(private / ('commit-' + phase + '-observer.json')) for phase in ('before', 'after')]
    job = load(private / 'commit-request.json')['id']
    assert inner['job'] == before['job'] == after['job'] == job
    assert inner['syntheticAckLoss'] is False and inner['driverCommitReturned'] is False
    assert inner['pid'] == load(home / 'worker-ready.json')['pid'] != before['pid'] == after['pid']
    assert inner['row']['status'] == after['row']['status'] == 'SUCCEEDED' and before['row']['status'] == 'RUNNING'
    assert inner['auditCount'] == after['auditCount'] == 1 and before['auditCount'] == 0
    assert all(f['row']['attempts'] == 1 for f in (inner, before, after))
    assert before['commitVisible'] is False and after['commitVisible'] is True
    assert after['ackStillHeld'] is after['driverFailureAbsent'] is True
    assert inner['database']['connectionId'] not in (before['database']['connectionId'], after['database']['connectionId'])
    for fact in (inner, before, after):
        assert {k: v for k, v in fact['database'].items() if k != 'connectionId'} == load(private / 'target-identity.json')
    assert json.loads(inner['row']['payload']) == json.loads(after['row']['payload'])
    assert json.loads(before['row']['payload'])['artifact'] is None
    return job, home


def transport(home):
    held = load(home / 'wire-ack-held.json'); dropped = load(home / 'wire-drop-receipt.json')
    command = (home / 'wire-commit-command.bin').read_bytes(); ack = (home / 'wire-commit-ok.bin').read_bytes()
    assert is_commit(command) and commit_ok(ack) == {k: held[k] for k in ('sequence', 'statusFlags', 'warnings')}
    assert digest(home / 'wire-commit-command.bin') == held['commandSha256']
    assert digest(home / 'wire-commit-ok.bin') == held['ackSha256'] == dropped['ackSha256']
    assert held['connectionId'] == dropped['connectionId'] == load(home / 'jdbc-before-commit.json')['database']['connectionId']
    assert held['targetPort'] == load(home / 'worker-database-identity.json')['database']['port']
    assert held['originalServerOkReceived'] is True and held['selectedCount'] == 1
    assert held['forwardedToClient'] is dropped['forwardedToClient'] is held['driverCommitReturned'] is held['syntheticAckLoss'] is False
    assert dropped['socketsClosed'] is True and dropped['droppedAtMonotonicNs'] > held['heldAtMonotonicNs']
    failure = load(home / 'jdbc-driver-failure.json')
    assert failure['syntheticAckLoss'] is failure['driverCommitReturned'] is False
    assert failure['pid'] == load(home / 'worker-ready.json')['pid']
    assert failure['sqlState'].startswith('08')
    assert failure['exceptionClass'].startswith(('java.sql.', 'com.mysql.cj.jdbc.exceptions.'))
    assert 'com.mysql.cj' in failure['stack'] and ('EOFException' in failure['stack'] or 'SocketException' in failure['stack'])
    assert not (home / 'jdbc-driver-returned-success.marker').exists()
    return dict(actualTcpAckLoss=True, originalServerOkSha256=held['ackSha256'], driverException=failure['exceptionClass'],
                driverSqlState=failure['sqlState'], driverCommitReturned=False, syntheticAckLoss=False)


def commit(attempt):
    job, home = observations(attempt)
    proof = dict(**facts(attempt, job), **transport(home))
    held = load(home / 'worker-commit-held.json'); result = load(home / 'worker-commit-exit.json')
    assert result['pid'] == held['pid'] and result['actualExitCode'] == 70
    assert not result['aliveAfter'] and not result['normalShutdown']
    assert load(home / 'owner-before.json')['lifecycleLockHeld'] is True
    assert load(home / 'owner-after.json')['lifecycleLockHeld'] is False
    assert not (home / 'worker-closed.marker').exists() and (home / 'api-closed.marker').is_file()
    assert load(home / 'api-commit-refused.json') == dict(pid=load(home / 'api-ready.json')['pid'],
        admissionCode='RECOVERY_LEASE_FENCED', effectCode='RECOVERY_LEASE_FENCED', liveRoots=0)
    output = (home / 'worker.stdout').read_text('utf-8') + (home / 'worker.stderr').read_text('utf-8')
    for code in ('FENCE_F1_PRIME_CONFIRMED', 'FENCE_F2_AUDIT_CONFIRMED', 'FENCE_F3_RELEASE_CONFIRMED', 'FENCE_F4_WRITERS_RETAINED'):
        assert code in output
    return dict(**proof, id=job, realProductCompletionCommit=True, actualProductHaltExit=70,
                independentBeforeAfterVisibility=True)
