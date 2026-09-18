#!/usr/bin/env python3
"""Record table counts and checksums at the stopped test-service backup boundary."""
import json
import re
import subprocess


def query(sql):
    return subprocess.run(['mysql', '--defaults-file=/etc/acrylic-outbound-test/migration.cnf',
                           '-NB', 'acrylic_outbound_test', '-e', sql],
                          check=True, capture_output=True, text=True).stdout.strip()


tables = query('SHOW TABLES;').splitlines()
if not all(re.fullmatch(r'[a-zA-Z0-9_]+', table) for table in tables):
    raise RuntimeError('Unexpected table identifier')
facts = {}
for table in tables:
    count, checksum = query(f'SELECT COUNT(*) FROM {table}; CHECKSUM TABLE {table};').splitlines()
    facts[table] = {'count': int(count), 'checksum': checksum.split('\t')[1]}
print(json.dumps(facts, indent=2))
