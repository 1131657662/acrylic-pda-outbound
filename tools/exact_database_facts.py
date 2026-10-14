"""Exact ASCII row multisets with independently checked row and column counts."""
import hashlib
import json
import re
from pathlib import Path

from tools.sql_evidence import SqlEvidence


def identifier(value):
    if not re.fullmatch('[A-Za-z0-9_]+', value):
        raise ValueError(f'Invalid database identifier: {value!r}')
    return '`' + value + '`'


def table_facts(reader, name, directory):
    quoted = identifier(name)
    raw_columns = reader.query('SHOW COLUMNS FROM ' + quoted, name)
    columns = [line.split(b'\t', 1)[0].decode('ascii') for line in raw_columns.splitlines()]
    if not columns or len(set(columns)) != len(columns):
        raise ValueError(f'Missing/duplicate columns: table={name}, evidence={reader.directory}')
    expressions = [f"IF({identifier(c)} IS NULL,'N',CONCAT('V',HEX(CAST({identifier(c)} AS BINARY))))" for c in columns]
    raw = reader.query('SELECT ' + ','.join(expressions) + ' FROM ' + quoted, name)
    count = reader.query('SELECT COUNT(*) FROM ' + quoted, name).strip()
    rows = raw.splitlines()
    if not re.fullmatch(rb'[0-9]+', count) or int(count) != len(rows):
        raise ValueError(f'Row count mismatch: table={name}, count={count!r}, '
                         f'rows={len(rows)}, evidence={reader.directory}')
    for row in rows:
        fields = row.split(b'\t')
        if len(fields) != len(columns) or any(not re.fullmatch(rb'N|V(?:[0-9A-F]{2})*', f) for f in fields):
            raise ValueError(f'Invalid encoded row: table={name}, evidence={reader.directory}')
    (directory / (name + '.hex.tsv')).write_bytes(raw)
    return {'rows': int(count), 'columns': columns,
            'sha256': hashlib.sha256(b'\n'.join(sorted(rows))).hexdigest()}


def capture(options):
    directory = Path(options['directory'])
    directory.mkdir()
    reader = SqlEvidence({**options, 'directory': directory / 'sql'})
    names = reader.query('SELECT table_name FROM information_schema.tables '
                         'WHERE table_schema=DATABASE() ORDER BY table_name').decode('ascii').splitlines()
    if not names or len(set(names)) != len(names):
        raise ValueError(f'Missing/duplicate table inventory: evidence={reader.directory}')
    facts = {name: table_facts(reader, name, directory) for name in names}
    (directory / 'facts.json').write_text(json.dumps(facts, indent=2), encoding='utf-8')
    return facts


def conserved(before, after, allowed=()):
    removed = sorted(set(before) - set(after))
    added = sorted(set(after) - set(before))
    changed = sorted(name for name in set(before) & set(after) if before[name] != after[name])
    if removed or added or not set(changed).issubset(allowed):
        raise ValueError(f'Conservation failed: removed={removed}, added={added}, changed={changed}')
    return changed
