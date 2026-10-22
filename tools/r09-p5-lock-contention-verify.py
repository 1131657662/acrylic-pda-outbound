"""Independent raw-file consumer for commit then real OS fence-lock contention."""
import argparse
import hashlib
import json
from pathlib import Path
import runpy

SHARED = runpy.run_path(str(Path(__file__).with_name('r09-p5-lost-commit-verify.py')))


def children(base, operation, owner):
    paths = list(base.glob('child-*'))
    assert len(paths) == 2
    owners = [p for p in paths if (p / 'operation.txt').exists()]
    assert len(owners) == 1
    child = owners[0]
    fresh = next(p for p in paths if p != child)
    assert (child / 'operation.txt').read_text('utf-8') == operation
    receipt = (child / 'receipt.txt').read_text('utf-8')
    assert f'|pid={owner["pid"]}|' in receipt and receipt.endswith('|exit=70')
    status = (child / 'status.txt').read_text('utf-8')
    markers = ['PREPARED|uncommitted=2|delegateInsert=1', 'DRIVER_COMMITTED|awaiting=lose-ack', 'ACK_LOSS_RELEASED']
    assert all(token in status for token in markers)
    assert [status.index(token) for token in markers] == sorted(status.index(token) for token in markers)
    assert (child / 'lose-ack').read_text('utf-8') == 'lose-ack'
    assert not (child / 'commit-returned.marker').exists()
    stderr = (child / 'stderr.txt').read_text('utf-8')
    for token in ('FENCE_F1_PRIME_UNCONFIRMED', 'CONTROL_FENCE_LOCK_UNCONFIRMED',
                  'FENCE_F2_AUDIT_CONFIRMED', 'FENCE_F3_RELEASE_CONFIRMED', 'FENCE_F4_WRITERS_RETAINED'):
        assert token in stderr
    assert 'FENCE_F1_PRIME_CONFIRMED' not in stderr
    assert (fresh / 'receipt.txt').read_text('utf-8').endswith('|exit=1')
    assert 'RECOVERY_LEASE_OUTSTANDING' in (fresh / 'stderr.txt').read_text('utf-8')
    assert 'EFFECT|' not in (fresh / 'status.txt').read_text('utf-8')
    return dict(ownerPid=owner['pid'], haltExit=70, freshExit=1, f1Confirmed=False)


def contention(output):
    records = (output / 'real-records.tsv').read_text('utf-8')
    markers = ['REAL|normalCommit|before=0|observer=1|driverCommits=1',
               'REAL|prepared|observer=1|ownerLocked=true|gate=accepted',
               'REAL|f1Contention|driverCommitted=true|observer=2|exclusiveLock=true|ownerAlive=true',
               'REAL|f1Contention|childExit=70|exclusiveLockStillValid=true',
               'REAL|f1Contention|exclusiveLockReleased=true',
               'REAL|lostCommit|observer=2|ownerLocked=false|exit=70']
    assert all(token in records for token in markers)
    assert [records.index(token) for token in markers] == sorted(records.index(token) for token in markers)
    assert json.loads((output / 'scenario.json').read_text('utf-8')) == dict(
        scenario='contended', test='RecoveryFenceContendedCommitIT')


def preserved(base, operation):
    originals = base / 'originals-before-observation'
    files = [p for p in originals.rglob('*') if p.is_file()]
    exceptions = {f'leases/{operation}.json', 'leases/index.json'}
    for path in files:
        relative = path.relative_to(originals)
        if relative.as_posix() not in exceptions:
            assert path.read_bytes() == (base / 'p5' / relative).read_bytes(), relative
    return dict(originalFiles=len(files), observationMutations=sorted(exceptions))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('output', type=Path)
    output = parser.parse_args().output.resolve()
    assert output.is_relative_to(Path('D:/acrylic-pda-outbound/.local'))
    base, operation, owner = SHARED['original_chain'](output)
    contention(output)
    result = dict(status='PASS', scope='MODEL installation/admission/ack loss; real driver commit/P5/OS contention/halt',
                  unknownAuditAuthenticated=False, anchoredRecords=['lease', 'process', 'index'], operation=operation)
    result.update(children(base, operation, owner))
    result.update(SHARED['database'](output))
    result.update(SHARED['observations'](output))
    result.update(preserved(base, operation))
    result['evidence'] = {p.relative_to(output).as_posix(): dict(bytes=p.stat().st_size,
        sha256=hashlib.sha256(p.read_bytes()).hexdigest()) for p in sorted(output.rglob('*')) if p.is_file()}
    with (output / 'independent-verification.json').open('x', encoding='utf-8') as stream:
        json.dump(result, stream, indent=2)
    print(json.dumps({k: v for k, v in result.items() if k != 'evidence'}))


if __name__ == '__main__':
    main()
