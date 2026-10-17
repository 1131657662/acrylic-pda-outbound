"""Consume one completed candidate's raw HTTP, pinned processes, migration and recovery artifacts."""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import re

from d15_combination_consume import conserve, upgrade

LABELS = ('pre-old', 'pre-new', 'post-new', 'post-old', 'post-new-again', 'restored-old', 'restored-new')
JARS = {'old': 'adbd314f0fd619997dea0f16e0de36a963c3650173e991cbcf259ad1ae7184e1',
        'new': 'e130aa2b7844daf6380045e3f3e809672b9c15f5ff31c4a24adf4cdf6bdc92ba'}


def load(path):
    return json.loads(path.read_bytes())


def digest(path):
    with path.open('rb') as source:
        return hashlib.file_digest(source, 'sha256').hexdigest()


def http(directory, reader):
    metadata = list(directory.glob('*.http.json'))
    assert {path.name for path in metadata} == {name + '.http.json' for name in
        ('unauthenticated', 'csrf', 'login', 'orders', 'detail', 'revisions', 'publications', 'exports')}
    for path in metadata:
        item = load(path)
        assert item['status'] == item['expected'] == (401 if path.stem == 'unauthenticated.http' else 200)
        body = path.with_name(path.name.replace('.http.json', '.body.json'))
        assert digest(body) == item['sha256']
        if item['status'] == 200:
            parsed = load(body)
            assert parsed['code'] == 'OK' and parsed['serviceMode'] != 'UNAVAILABLE'
    data = load(directory / 'business.json')
    for name in ('orders', 'detail', 'revisions', 'publications', 'exports'):
        assert data[name] == load(directory / (name + '.body.json'))['data']
    for name in ('orders', 'detail'):
        assert reader['seed']['orderId'] in json.dumps(data[name])
    assert data['revisions'] and data['publications']
    return data


def stages(attempt, reader):
    results, business, snapshots = [], None, {}
    for label in LABELS:
        directory = attempt / 'private' / ('program-' + label)
        before, after = load(directory / 'before.json'), load(directory / 'after.json')
        result = conserve(before, after, reader)
        assert (directory / 'verified.marker').is_file()
        process, listener = load(directory / 'process.json'), load(directory / 'listener.json')
        assert listener['OwningProcess'] == process['pid'] == load(directory / 'stopped.json')['pid']
        assert load(directory / 'stopped.json')['listener'] is False
        assert process['jarSha256'] == digest(Path(process['args'][5])) == JARS['old' if 'old' in label else 'new']
        assert {'--spring.flyway.enabled=false', '--outbound.worker.enabled=false', '--outbound.bootstrap.enabled=false',
                '--spring.session.jdbc.cleanup-cron=-'} <= set(process['args'])
        data = http(directory / 'http', reader)
        if business is None:
            business = data
        assert data == business
        result.update(label=label, httpRequests=8, pid=process['pid'])
        results.append(result)
        snapshots[label] = (before, after)
    assert len({row['pid'] for row in results}) == len(LABELS)
    return results, snapshots


def count_tuples(raw):
    quoted, escaped, depth, count = False, False, 0, 0
    for byte in raw:
        if escaped:
            escaped = False
        elif byte == 92 and quoted:
            escaped = True
        elif byte == 39:
            quoted = not quoted
        elif not quoted and byte == 40:
            count += depth == 0
            depth += 1
        elif not quoted and byte == 41:
            depth -= 1
    assert not quoted and depth == 0
    return count


def dump(attempt, run, snapshots):
    path = attempt / 'private/final-dump.stdout'
    schemas, inserts, current = {}, {}, None
    for line in path.read_bytes().splitlines():
        selected = re.match(rb'^USE `([A-Za-z0-9_]+)`;', line)
        if selected:
            current = selected[1].decode()
            schemas[current], inserts[current] = set(), Counter()
        table = re.match(rb'^CREATE TABLE `([A-Za-z0-9_]+)`', line)
        if table:
            schemas[current].add(table[1].decode())
        values = re.match(rb'^INSERT INTO `([A-Za-z0-9_]+)`(?: \([^\r\n]+?\))? VALUES (.*);$', line)
        if values:
            inserts[current][values[1].decode()] += count_tuples(values[2])
    for name, label in ((f'acrylic_e2a_cross_20260925_{run}', 'post-new-again'),
                        (f'acrylic_e2a_cross_20260925_{run}_restore', 'restored-new')):
        snapshot = snapshots[label][1]
        expected = {table: len(value['rows']) for table, value in snapshot['tables'].items() if value['rows']}
        expected['flyway_schema_history'] = len(snapshot['flywayHistory'])
        assert schemas[name] == snapshot['tables'].keys() | {'flyway_schema_history'}
        assert dict(inserts[name]) == expected
    return dict(sha256=digest(path), schemas={name: dict(tables=len(tables), rows=inserts[name].total())
                                            for name, tables in schemas.items()})


def files(attempt):
    expected = load(attempt / 'checkpoint-full.json')['files']
    for directory in (attempt / 'storage', attempt / 'upgrade-checkpoint/storage', attempt / 'upgrade-restored/storage'):
        actual = {path.relative_to(directory).as_posix(): dict(bytes=path.stat().st_size, sha256=digest(path))
                  for path in directory.rglob('*') if path.is_file()}
        assert actual == expected
    for name in ('server-private.der', 'server-public.der'):
        original = digest(attempt / 'private' / name)
        assert digest(attempt / 'upgrade-checkpoint/config' / name) == original
        assert digest(attempt / 'upgrade-restored/config' / name) == original
    assert (attempt / 'upgrade-checkpoint/database.sql').stat().st_size > 0
    return dict(storageFiles=len(expected), developmentSigningFiles=2,
                checkpointDumpSha256=digest(attempt / 'upgrade-checkpoint/database.sql'))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--attempt', required=True, type=Path)
    args = parser.parse_args()
    attempt = args.attempt.resolve()
    run = int(attempt.name.rsplit('-', 1)[1])
    result = load(attempt / 'test-result.json')
    assert result['exitCode'] == 0 and result['tests'] == '1' and result['skipped'] == result['failures'] == result['errors'] == '0'
    assert result['completed'] and load(attempt / 'mysql-stopped.json')['listener'] is False
    results, snapshots = stages(attempt, load(attempt / 'reader-input.json'))
    migration = upgrade(load(attempt / 'upgrade-before.json'), load(attempt / 'upgrade-after.json'))
    assert snapshots['restored-old'][0] == load(attempt / 'checkpoint-full.json')
    assert snapshots['pre-new'][0] == snapshots['pre-old'][1]
    assert snapshots['post-old'][0] == snapshots['post-new'][1]
    assert snapshots['post-new-again'][0] == snapshots['post-old'][1]
    assert snapshots['restored-new'][0] == snapshots['restored-old'][1]
    old_triggers = snapshots['pre-new'][1]['triggers']
    assert all(item in snapshots['post-new'][0]['triggers'] for item in old_triggers)
    assert (attempt / 'restore-refused.marker').is_file()
    print(json.dumps(dict(status='PASS_OFFLINE_CONSUMER', stages=results, upgrade=migration,
                         dump=dump(attempt, run, snapshots), preservedFiles=files(attempt)), indent=2))


if __name__ == '__main__':
    main()
