"""Identity-check, dump, authenticate shutdown, then hash the retained D15 lab."""
import json
import os
from pathlib import Path
import subprocess
import sys
from d15_jobs_fixture import admin, verify
from d15_jobs_test_support import Fixture
from safety import require, write_json, inventory

BIN = Path('C:/Program Files/MySQL/MySQL Server 8.0/bin')


def stopped(manifest):
    command = f'$p=Get-Process -Id {int(manifest["process_id"])} -ErrorAction SilentlyContinue; if($p){{ $p | Wait-Process -Timeout 30 -ErrorAction Stop }}; exit 0'
    env = {key: value for key, value in os.environ.items() if key.casefold() != 'psmodulepath'}
    result = subprocess.run(['powershell', '-NoProfile', '-NonInteractive', '-Command', command],
                            capture_output=True, text=True, timeout=35, env=env)
    require(result.returncode == 0 and not result.stderr, 'MYSQL_PROCESS_NOT_STOPPED')
    import socket
    with socket.socket() as probe:
        require(probe.connect_ex(('127.0.0.1', manifest['port'])) != 0, 'MYSQL_PORT_STILL_LISTENING')


def finalize(manifest):
    stopped(manifest)
    root = Path(manifest['root'])
    require((root / 'preserved.sql').stat().st_size > 0 and (root / 'shutdown.log').exists(),
            'PRESERVE_INPUT_MISSING')
    facts = json.loads((root / 'facts-before-shutdown.json').read_text())
    hashes = inventory(root)
    write_json(root / 'preserved-hashes.json', hashes)
    write_json(root / 'preserve-result.json', {'status': 'PASS', 'databases': len(facts),
               'tables': sum(len(tables) for tables in facts.values()), 'dump_fact_conservation': True,
               'authenticated_shutdown': True, 'hashed_files': len(hashes)})
    print(json.dumps({'status': 'PASS', 'databases': len(facts), 'hashed_files': len(hashes)}))


def native(manifest, executable, args, output):
    env = {key: value for key, value in os.environ.items() if key.upper() != 'MYSQL_PWD'}
    env['MYSQL_TEST_LOGIN_FILE'] = str(Path(manifest['root']) / 'no-login-file')
    require(not Path(env['MYSQL_TEST_LOGIN_FILE']).exists(), 'UNEXPECTED_LOGIN_FILE')
    command = [str(BIN / (executable + '.exe')), '--defaults-file=' + manifest['root'] + '/private/admin.cnf',
               '--protocol=TCP', '--host=127.0.0.1', '--port=' + str(manifest['port']), *args]
    with Path(output).open('xb') as stream:
        result = subprocess.run(command, stdout=stream, stderr=subprocess.PIPE, timeout=180, env=env)
    Path(str(output) + '.stderr').write_bytes(result.stderr)
    require(result.returncode == 0, 'PRESERVE_NATIVE_FAILED')


def preserve(path):
    fixture = Fixture(path)
    manifest = fixture.manifest
    with admin(manifest) as connection, connection.cursor() as cursor:
        verify(connection, manifest)
        cursor.execute('SHOW DATABASES')
        databases = [row[0] for row in cursor.fetchall() if row[0].startswith(manifest['database'])]
    facts = {database: fixture.facts({**fixture.base, 'database': database}) for database in databases}
    write_json(fixture.root / 'facts-before-shutdown.json', facts)
    native(manifest, 'mysqldump', ['--single-transaction', '--skip-lock-tables', '--no-tablespaces',
           '--set-gtid-purged=OFF', '--hex-blob', '--routines', '--triggers', '--databases', *databases],
           fixture.root / 'preserved.sql')
    after = {database: fixture.facts({**fixture.base, 'database': database}) for database in databases}
    require(facts == after, 'DUMP_CHANGED_DATABASE')
    native(manifest, 'mysqladmin', ['shutdown'], fixture.root / 'shutdown.log')
    write_json(fixture.root / 'dump-conservation.json', {'unchanged': facts == after, 'after': after})
    finalize(manifest)


if __name__ == '__main__':
    if len(sys.argv) == 3 and sys.argv[2] == '--finalize-stopped':
        finalize(json.loads(Path(sys.argv[1]).read_text(encoding='utf-8-sig')))
    else:
        preserve(sys.argv[1])
