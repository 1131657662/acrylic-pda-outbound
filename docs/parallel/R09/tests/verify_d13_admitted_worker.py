"""Offline audit of one actual production-scheduler admission run; no service/database startup."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess

from d13_export_file_consume import pdf, xlsx


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def load(path):
    return json.loads(path.read_bytes())


def http(directory):
    metadata = sorted(directory.glob('*.http.json'))
    statuses = []
    for path in metadata:
        row = load(path)
        assert row['status'] == row['expected']
        assert digest(path.with_name(path.name.replace('.http.json', '.body'))) == row['sha256']
        statuses.append(row['status'])
    assert statuses == [401, 200, 200, 404, 200, 200, 200, 200, 200, 409, 200, 200]
    return dict(requests=len(metadata), statuses=statuses)


def artifact(attempt, prefix, extension):
    record = load(attempt / (prefix + '.json'))
    view, payload = record['view'], record['payload']
    source = attempt / (prefix + '.snapshot.json')
    assert digest(source) == payload['snapshotHash']
    snapshot = load(source)
    path = attempt / (prefix + '.' + extension)
    assert digest(path) == view['sha256'] == payload['artifact']['sha256']
    assert path.stat().st_size == view['bytes'] == payload['artifact']['bytes']
    assert view['status'] == 'SUCCEEDED' and view['downloadable'] and view['attempts'] == 1
    result = pdf(path, snapshot) if extension == 'pdf' else xlsx(path, snapshot)
    result.update(sha256=digest(path), snapshotSha256=digest(source), http=http(attempt / 'private' / ('http-' + prefix)))
    return result


def control_and_sql(attempt):
    control = attempt / 'worker-authority'
    assert (control / 'python-call-1.out').read_text().strip() == 'CONTROL_WRITERS_ACTIVE'
    for index in (1, 2):
        assert (control / f'python-call-{index}.err').stat().st_size == 0
    advanced = load(control / 'python-call-2.out')
    assert advanced['status'] == 'PASS' and advanced['record']['revision'] == '2'
    assert advanced['ordinaryWritesAllowed'] is False and advanced['localDeletionAllowed'] is False
    stale = attempt / 'private/http-stale/001.http.json'
    assert load(stale)['status'] == load(stale)['expected'] == 503
    body = stale.with_name('001.body')
    assert digest(body) == load(stale)['sha256'] and load(body)['code'] == 'CONTROL_INSTANCE_STALE'
    dump = attempt / 'private/final-dump.stdout'
    raw = dump.read_bytes()
    assert b'CREATE TABLE `recovery_worker_probe`' in raw and b'INSERT INTO `recovery_worker_probe`' not in raw
    assert raw.count(b"'REPORT_RENDER_V1'") == 3 and raw.count(b"'RECOVERY_WORKER_PROBE'") == 1
    assert re.search(rb"\(0x[0-9A-F]+,'RECOVERY_WORKER_PROBE','[0-9a-f-]+','FAILED',", raw)
    orders = [load(attempt / (name + '.snapshot.json'))['orders']
              for name in ('default-off-xlsx', 'admitted-xlsx', 'admitted-pdf')]
    assert orders[0] == orders[1] == orders[2]
    return dict(staleHttp=503, revision=2, ordinaryWrites=False, localDeletion=False,
                probeRows=0, exportJobs=3, failedProbe=1, dumpSha256=digest(dump), ordersEqual=True)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--attempt', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    attempt = args.attempt.resolve()
    result = load(attempt / 'test-result.json')
    assert result['exitCode'] == 0 and result['tests'] == '1' and result['completed']
    assert result['failures'] == result['errors'] == result['skipped'] == '0'
    assert load(attempt / 'mysql-stopped.json')['listener'] is False
    checks = load(attempt / 'worker-checks.json')
    assert checks['kind'] == 'WORKER_JOB' and all(value is True for key, value in checks.items() if key not in ('kind', 'probeJob'))
    artifacts = {prefix: artifact(attempt, prefix, extension) for prefix, extension in
                 [('default-off-xlsx', 'xlsx'), ('admitted-xlsx', 'xlsx'), ('admitted-pdf', 'pdf')]}
    preview = args.output.parent / 'admitted-pdf-page'
    args.output.parent.mkdir(parents=True, exist_ok=True)
    rasterizer = shutil.which('pdftoppm')
    assert rasterizer and not preview.with_suffix('.png').exists()
    subprocess.run([rasterizer, '-f', '1', '-singlefile', '-scale-to', '1600', '-png',
                    str(attempt / 'admitted-pdf.pdf'), str(preview)], check=True, capture_output=True)
    assert preview.with_suffix('.png').stat().st_size > 1000
    summary = dict(result='PASS', scope='D13 bounded L1, not D13/L2/L3 complete',
                   artifacts=artifacts, worker=checks, independentControlAndSql=control_and_sql(attempt),
                   mysqlStopped=True, previewSha256=digest(preview.with_suffix('.png')))
    with args.output.open('x', encoding='utf-8') as output:
        json.dump(summary, output, indent=2)
    print(json.dumps(summary, ensure_ascii=True))


if __name__ == '__main__':
    main()
