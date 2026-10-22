"""Read original HTTP/export/P5/dump evidence without starting any service."""
import argparse
import json
from pathlib import Path
import re

from d13_export_file_consume import pdf, xlsx
from d15_combination_runtime import digest, save


def load(path):
    return json.loads(path.read_bytes())


def artifact(attempt, extension):
    prefix = 'split-' + extension
    record = load(attempt / (prefix + '.json'))
    view, payload = record['view'], record['payload']
    path, source = attempt / (prefix + '.' + extension), attempt / (prefix + '.snapshot.json')
    assert digest(source) == payload['snapshotHash']
    assert digest(path) == view['sha256'] == payload['artifact']['sha256']
    assert path.stat().st_size == view['bytes'] == payload['artifact']['bytes']
    assert view['status'] == 'SUCCEEDED' and view['downloadable'] and view['attempts'] == 1
    result = pdf(path, load(source)) if extension == 'pdf' else xlsx(path, load(source))
    statuses = []
    for metadata in sorted((attempt / ('private/http-' + prefix)).glob('*.http.json')):
        row = load(metadata)
        assert row['status'] == row['expected']
        assert digest(metadata.with_name(metadata.name.replace('.http.json', '.body'))) == row['sha256']
        statuses.append(row['status'])
    assert statuses == [401, 200, 200, 404, 200, 200, 200, 200, 200, 409, 200, 200]
    return dict(**result, httpStatuses=statuses, sha256=digest(path), snapshotSha256=digest(source))


def processes(attempt):
    home = attempt / 'private/split'
    api, worker = load(home / 'api-ready.json'), load(home / 'worker-ready.json')
    assert api['pid'] != worker['pid'] and api['web'] and not worker['web']
    for name in ('api-closed', 'worker-closed', 'writer-blocked', 'probe-held', 'probe-released'):
        assert (home / (name + '.marker')).is_file()
    assert 'STARTED' in (home / 'successor.stdout').read_text('utf-8')
    p5 = attempt / 'private/p5-retirement/p5'
    index = load(p5 / 'leases/index.json')
    assert not index['entries']
    assert [path.name for path in (p5 / 'leases').iterdir()] == ['index.json']
    pids = {load(path)['pid'] for path in (p5 / 'processes').glob('*.json')}
    assert len(pids) == 3 and {api['pid'], worker['pid']}.issubset(pids)
    return dict(api=api, worker=worker, retiredRoots=0, independentSuccessor=True, processPids=sorted(pids))


def verify(attempt):
    result = {extension: artifact(attempt, extension) for extension in ('xlsx', 'pdf')}
    assert load(attempt / 'split-xlsx.snapshot.json')['orders'] == load(attempt / 'split-pdf.snapshot.json')['orders']
    raw = (attempt / 'private/final-dump.stdout').read_bytes()
    assert b'CREATE TABLE `recovery_worker_probe`' in raw and b'INSERT INTO `recovery_worker_probe`' not in raw
    assert raw.count(b"'REPORT_RENDER_V1'") == 2 and raw.count(b"'RECOVERY_WORKER_PROBE'") == 1
    assert re.search(rb"\(0x[0-9A-F]+,'RECOVERY_WORKER_PROBE','[0-9a-f-]+','FAILED',", raw)
    assert (attempt / 'mysql-stopped.json').is_file() and load(attempt / 'mysql-stopped.json')['exitCode'] == 0
    result.update(processes=processes(attempt), failedProbeRows=0, dumpSha256=digest(attempt / 'private/final-dump.stdout'))
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--attempt', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    save(args.output, verify(args.attempt))
    print(json.dumps(dict(status='PASS', output=str(args.output))))


if __name__ == '__main__':
    main()
