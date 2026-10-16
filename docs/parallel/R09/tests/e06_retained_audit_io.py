"""Read retained evidence without opening a server, database or Android device."""
import hashlib
import json
import re
from collections import Counter
from pathlib import Path

ROOT = Path('D:/acrylic-pda-outbound')
BASE = ROOT / '.local/goal-20261014'
SERVICE = ROOT / '.local/goal-20261013/e06-conflict-audit-1539'
SESSIONS = {'spring_session', 'spring_session_attributes'}
INPUTS = {}


def read(path):
    path = Path(path).resolve()
    data = path.read_bytes()
    INPUTS[str(path.relative_to(ROOT)).replace('\\', '/')] = {
        'length': len(data), 'sha256': hashlib.sha256(data).hexdigest()}
    return data


def load(path):
    return json.loads(read(path).decode('utf-8-sig'))


def digest(data):
    return hashlib.sha256(data).hexdigest()


def bindings(path):
    changes = []
    rows = load(path)
    for row in rows:
        target = Path(row['path'])
        target = target if target.is_absolute() else ROOT / target
        actual = digest(read(target))
        if actual.lower() != row['sha256'].lower():
            changes.append({'path': str(target.relative_to(ROOT)).replace('\\', '/'),
                            'originalSha256': row['sha256'], 'actualSha256': actual})
    return {'manifest': str(path.relative_to(ROOT)), 'paths': len(rows), 'changed': changes}


def facts(directory):
    values = load(directory / 'facts.json')
    for table, row in values.items():
        raw = read(directory / (table + '.hex.tsv'))
        lines = raw.splitlines()
        assert len(lines) == row['rows'], (directory, table, 'count')
        assert digest(b'\n'.join(sorted(lines))) == row['sha256'], (directory, table, 'hash')
        for line in lines:
            fields = line.split(b'\t')
            assert len(fields) == len(row['columns']), (directory, table, 'columns')
            assert all(re.fullmatch(rb'N|V(?:[0-9A-F]{2})*', value) for value in fields)
    sql = directory / 'sql'
    assert load(sql / 'process.result.json')['exitCode'] == 0
    identity = read(sql / '0001.stdout').decode().strip().split('\t')
    assert identity[:2] == ['acrylic_e2a_cross_20260925_1539', '19332']
    assert Path(identity[2]).resolve() == (ROOT / '.local/e2a-cross-20260925-1539/data').resolve()
    return values


def conserved(before, after, allowed=()):
    assert set(before) == set(after), 'Table inventory changed'
    changed = {name for name in before if before[name] != after[name]}
    assert changed <= set(allowed), changed - set(allowed)
    return sorted(changed)


def exact_append(before_dir, after_dir, expected):
    before, after = facts(before_dir), facts(after_dir)
    conserved(before, after, SESSIONS | set(expected))
    for name, count in expected.items():
        old = Counter(read(before_dir / (name + '.hex.tsv')).splitlines())
        new = Counter(read(after_dir / (name + '.hex.tsv')).splitlines())
        assert not old - new and sum((new - old).values()) == count, name
    return {'tables': len(before), 'append': expected}


def write(directory, name, value):
    path = directory / name
    assert not path.exists(), path
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False), encoding='utf-8')
