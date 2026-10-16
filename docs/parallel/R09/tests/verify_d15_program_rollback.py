"""Offline consumer of the completed raw HTTP, SQL facts and program identity evidence."""
from collections import Counter
import hashlib
import json
import re

import d15_rollback_compare as compare
from d15_rollback_sql import LAB, ROOT, digest


def load(name):
    return json.loads((LAB / name).read_bytes())


def http(label):
    directory = LAB / 'private' / label
    count = 0
    for path in directory.glob('*-http.json'):
        metadata = json.loads(path.read_bytes())
        response = directory / path.name.replace('-http.json', '.json')
        assert response.stat().st_size == metadata['bytes']
        assert digest(response) == metadata['sha256']
        assert metadata['status'] == metadata['expected']
        count += 1
    assert count == 8
    return count


def stages():
    reader = load('private/web-reader.json')
    before = load('baseline-facts.json')
    label = 'baseline'
    results = []
    assert load('source667-before.json') == load('source667-after.json')
    business = load('new01-business.json')
    for current in ('new01', 'old01', 'new02'):
        after = load(current + '-facts.json')
        result = compare.stage(before, after, dict(reader=reader, before=label, after=current))
        assert load(current + '-business.json') == business
        assert load(current + '-api-stopped.json')['listener'] is False
        assert load(current + '-listener.json')['OwningProcess'] == load(current + '-api-command.json')['pid']
        result.update(httpRequests=http(current), label=current)
        results.append(result)
        before, label = after, current
    return results


def dump_facts():
    raw = (LAB / 'private/final.sql').read_bytes()
    # This input contains one physical INSERT line per table, with multiple tuple values.
    tables = re.findall(rb'^CREATE TABLE `([A-Za-z0-9_]+)`', raw, re.M)
    facts = load('new02-facts.json')
    assert {name.decode() for name in tables} == set(facts)
    counts = Counter()
    for line in raw.splitlines():
        match = re.match(rb'INSERT INTO `([A-Za-z0-9_]+)` \([^\r\n]+?\) VALUES (.*);$', line)
        if match:
            counts[match[1].decode()] += count_tuples(match[2])
    assert dict(counts) == {name: len(value['rows']) for name, value in facts.items() if value['rows']}
    return dict(tables=len(tables), rows=sum(counts.values()), sha256=hashlib.sha256(raw).hexdigest())


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


def main():
    assert load('mysql-stopped.json')['listener'] is False
    results = stages()
    dump = dump_facts()
    source = ROOT / '.local/e2a-cross-20260925-667'
    manifest = ROOT / '.local/goal-20261016/e07-formal-repository01/consumer02/manifest-after.json'
    for row in json.loads(manifest.read_bytes()):
        path = source / row['path']
        assert digest(path) == row['sha256'] and path.stat().st_size == row['length']
    print(json.dumps(dict(result='PASS_OFFLINE_CONSUMER', stages=results, finalDump=dump,
                         sourceFilesUnchanged=604), indent=2))


if __name__ == '__main__':
    main()
