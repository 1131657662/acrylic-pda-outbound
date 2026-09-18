#!/usr/bin/env python3
"""Restore a test backup into a fresh, retained verification database."""
import gzip
import json
import os
from pathlib import Path
import re
import subprocess
import sys
from datetime import datetime, timezone
from initialize import mysql


def restore_database(backup, database):
    mysql(f'CREATE DATABASE {database} CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;')
    with gzip.open(backup / 'database.sql.gz', 'rt') as stream:
        result = subprocess.run(['mysql', '--defaults-file=/etc/mysql/debian.cnf',
                                 '--protocol=socket', database],
                                input=stream.read(), text=True, capture_output=True)
    if result.returncode:
        raise RuntimeError('Restore failed; retained verification database for inspection')


def table_facts(database, tables):
    facts = {}
    for table in tables:
        count, checksum = mysql(f'SELECT COUNT(*) FROM {database}.{table}; '
                                f'CHECKSUM TABLE {database}.{table};').splitlines()
        facts[table] = {'count': int(count), 'checksum': checksum.split('\t')[1]}
    return facts


def main():
    os.umask(0o077)
    backup = Path(sys.argv[1]).resolve()
    if os.geteuid() != 0 or backup.parent != Path('/opt/backups/acrylic-outbound-test'):
        raise RuntimeError('Only root may verify an isolated test backup')
    subprocess.run(['sha256sum', '-c', 'SHA256SUMS'], cwd=backup, check=True)
    database = 'acrylic_outbound_restore_' + datetime.now(timezone.utc).strftime('%Y%m%d%H%M%S')
    restore_database(backup, database)
    expected = json.loads((backup / 'database-facts.json').read_text())
    tables = mysql(f'SHOW TABLES FROM {database};').splitlines()
    if set(tables) != set(expected):
        raise RuntimeError('Restored table set differs from backup')
    if not all(re.fullmatch(r'[a-zA-Z0-9_]+', table) for table in tables):
        raise RuntimeError('Unexpected table identifier')
    actual = table_facts(database, tables)
    differences = [t for t in tables if expected[t] != actual[t]]
    result = {'database': database, 'tables': len(tables), 'differences': differences,
              'note': 'Compared with stopped-service backup counts and checksums.'}
    (backup / 'restore-verification.json').write_text(json.dumps(result, indent=2))
    print(json.dumps(result))
    if differences:
        raise RuntimeError('Restored table contents differ from the current source')


if __name__ == '__main__':
    main()
