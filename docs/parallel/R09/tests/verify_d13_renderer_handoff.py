"""Independent offline consumption of normal exports and an actual renderer-fence child receipt."""
import argparse
import json
from pathlib import Path
import shutil
import subprocess

from verify_d13_admitted_worker import artifact, control_and_sql, digest, load


def suites(attempt):
    report = load(attempt / 'test-result.json')
    assert report['exitCode'] == 0 and len(report['suites']) == 5
    assert all(row['errors'] == row['failures'] == row['skipped'] == '0' for row in report['suites'])
    unit = sum(int(row['tests']) for row in report['suites'] if row['task'] == 'test')
    integration = sum(int(row['tests']) for row in report['suites'] if row['task'] == 'integrationTest')
    assert unit == 20 and integration == 2
    return dict(unit=unit, integration=integration, failures=0, errors=0, skipped=0)


def fence(attempt):
    home = attempt / 'renderer-fence'
    receipt = load(home / 'result.json')
    assert receipt['exit'] == 70 and receipt['unknownRecords'] == 1
    assert all(receipt[key] is True for key in
               ('writerHeld', 'rendererStopped', 'realFenceSequence', 'nextProcessRefused', 'modelLostReceipt'))
    assert not (home / 'incorrect-return.txt').exists()
    stopped = (home / 'termination-observed.txt').read_text('utf-8')
    assert 'alive=false|childrenAlive=0|MODEL=lost receipt after real native drain' in stopped
    stderr = (home / 'child.stderr').read_text('utf-8')
    for step in ('FENCE_F1_PRIME_CONFIRMED', 'FENCE_F2_AUDIT_CONFIRMED',
                 'FENCE_F3_RELEASE_CONFIRMED', 'FENCE_F4_WRITERS_RETAINED'):
        assert step in stderr
    unknown, orphan = load(home / 'unknown-original.json'), load(home / 'orphan-original.json')
    assert unknown['event'] == 'RECOVERY_LEASE_FENCED'
    assert unknown['operationId'] == orphan['operationId']
    assert unknown['processIdentity'] == orphan['processIdentity']
    receipt.update(unknownSha256=digest(home / 'unknown-original.json'),
                   orphanSha256=digest(home / 'orphan-original.json'), stderrSha256=digest(home / 'child.stderr'))
    return receipt


def preview(attempt, directory):
    rasterizer = shutil.which('pdftoppm')
    assert rasterizer
    subprocess.run([rasterizer, '-scale-to', '1600', '-png', str(attempt / 'admitted-pdf.pdf'),
                    str(directory / 'admitted-pdf')], check=True, capture_output=True)
    images = sorted(directory.glob('admitted-pdf-*.png'))
    assert len(images) == 2
    return [dict(path=str(path), sha256=digest(path)) for path in images]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--attempt', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    assert not args.output.exists()
    args.output.parent.mkdir(parents=True, exist_ok=False)
    attempt = args.attempt.resolve()
    assert load(attempt / 'mysql-stopped.json')['listener'] is False
    normal = {prefix: artifact(attempt, prefix, extension) for prefix, extension in
              [('default-off-xlsx', 'xlsx'), ('admitted-xlsx', 'xlsx'), ('admitted-pdf', 'pdf')]}
    result = dict(result='PASS', scope='bounded renderer/server L1; not D13/L2/L3 complete',
                  tests=suites(attempt), artifacts=normal, controlAndSql=control_and_sql(attempt),
                  fence=fence(attempt), previews=preview(attempt, args.output.parent))
    with args.output.open('x', encoding='utf-8') as output:
        json.dump(result, output, indent=2)
    print(json.dumps(result))


if __name__ == '__main__':
    main()
