"""Restore a pinned stopped development backup on an explicitly allocated D15 lab."""
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
from d15_jobs_fixture import ROOT, admin, verify, ready, account
from d15_jobs_test_support import Fixture
from safety import require, write_json

SOURCE = ROOT / '.local/e2a-cross-20260925-667'
PRIOR = ROOT / '.local/goal-20261016/e07-formal-repository01/consumer02'
MYSQL = Path('C:/Program Files/MySQL/MySQL Server 8.0/bin')
DUMP_ARGS = ['--single-transaction', '--skip-lock-tables', '--no-tablespaces',
             '--set-gtid-purged=OFF', '--hex-blob', '--skip-comments', '--skip-add-drop-table',
             '--order-by-primary', '--complete-insert', '--skip-extended-insert']


def digest(path):
    with Path(path).open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def originals():
    rows = json.loads((PRIOR / 'manifest-after.json').read_bytes())
    require(len(rows) == 604, 'SOURCE_MANIFEST_INCOMPLETE')
    for row in rows:
        path = (SOURCE / row['path']).resolve()
        require(path.is_relative_to(SOURCE.resolve()), 'SOURCE_PATH_INVALID')
        require(path.stat().st_size == row['length'] and digest(path) == row['sha256'],
                'STOPPED_SOURCE_CHANGED')
    return {'files': len(rows), 'bytes': sum(r['length'] for r in rows),
            'manifest_sha256': digest(PRIOR / 'manifest-after.json'), 'unchanged': True}


def native(manifest, executable, arguments, output, source=None):
    root = Path(manifest['root'])
    env = {key: value for key, value in os.environ.items() if key.upper() != 'MYSQL_PWD'}
    env['MYSQL_TEST_LOGIN_FILE'] = str(root / 'no-login-file')
    env.update(TEMP=str(root / 'tmp'), TMP=str(root / 'tmp'))
    require(not Path(env['MYSQL_TEST_LOGIN_FILE']).exists(), 'UNEXPECTED_LOGIN_FILE')
    command = [str(MYSQL / (executable + '.exe')), '--defaults-file=' + str(root / 'private/admin.cnf'),
               '--protocol=TCP', '--host=127.0.0.1', '--port=' + str(manifest['port']), *arguments]
    with Path(output).open('xb') as stream:
        data = None if source is None else Path(source).open('rb')
        try:
            result = subprocess.run(command, stdin=data, stdout=stream, stderr=subprocess.PIPE,
                                    timeout=120, env=env)
        finally:
            if data is not None:
                data.close()
    Path(str(output) + '.stderr').write_bytes(result.stderr)
    require(result.returncode == 0, 'RESTORE_NATIVE_COMMAND_FAILED')


def identifier(value):
    require(bool(re.fullmatch(r'[a-z][a-z0-9_]{0,63}', value)), 'RESTORE_SCHEMA_INVALID')
    return '`' + value + '`'


def empty_restore(manifest, database, source, label):
    require(database in (manifest['database'], manifest['database'] + '_restored'), 'RESTORE_TARGET_INVALID')
    with admin(manifest) as connection, connection.cursor() as cursor:
        verify(connection, manifest)
        cursor.execute('SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=%s', (database,))
        require(cursor.fetchone()[0] == 0, 'RESTORE_DATABASE_NOT_EMPTY')
    native(manifest, 'mysql', ['--binary-mode', '--default-character-set=utf8mb4', database],
           Path(manifest['root']) / (label + '.stdout'), source)


def dump(manifest, database, name):
    path = Path(manifest['root']) / name
    native(manifest, 'mysqldump', [*DUMP_ARGS, database], path)
    return path


def same_dump(left, right):
    # MySQL makes the implicit utf8mb4 character set explicit after an import.
    normalize = lambda data: data.replace(b' CHARACTER SET utf8mb4 COLLATE utf8mb4_', b' COLLATE utf8mb4_')
    require(normalize(left.read_bytes()) == normalize(right.read_bytes()), 'RESTORED_LOGICAL_DUMP_DIFFERS')


def probe_config(manifest, database, user):
    grants = ['GRANT SELECT (status,available_at,lease_until) ON `{database}`.background_job TO \'{user}\'@\'127.0.0.1\'',
              'GRANT SELECT (version,success) ON `{database}`.flyway_schema_history TO \'{user}\'@\'127.0.0.1\'']
    with admin(manifest) as connection, connection.cursor() as cursor:
        uuid = verify(connection, manifest)
        file = account(cursor, manifest, user, grants, database)
    return {key: manifest[key] for key in ('port', 'datadir')} | {
        'host': '127.0.0.1', 'database': database, 'current_user': user + '@127.0.0.1',
        'server_uuid': uuid, 'credentials_file': file}


def copy_materials(manifest):
    root = Path(manifest['root']) / 'original-materials'
    shutil.copytree(SOURCE / 'storage', root / 'storage')
    (root / 'config').mkdir()
    for name in ('server-private.der', 'server-public.der'):
        shutil.copy2(SOURCE / 'private' / name, root / 'config' / name)
    expected = json.loads((SOURCE / 'private/archive-snapshot-facts.json').read_bytes())['files']
    for name, row in expected.items():
        path = root / 'storage' / name
        require(digest(path) == row['sha256'] and path.stat().st_size == row['bytes'], 'RESTORED_FILE_DIFFERS')
    return {'storage_files': len(expected), 'signing_files': 2, 'all_bytes_equal': True}


def prepare(path):
    manifest = json.loads(Path(path).read_text(encoding='utf-8-sig'))
    require(manifest['purpose'] == 'D15_SYNTHETIC_MONITOR_ONLY', 'LAB_PURPOSE_INVALID')
    require(Path(manifest['root']).resolve() == ROOT / '.local/d15-monitor-1668'
            and manifest['port'] == 19378 and manifest['database'] == 'd15_monitor_1668', 'LAB_ALLOCATION_INVALID')
    require(Path(manifest['datadir']).resolve() == Path(manifest['root']) / 'data', 'LAB_DATADIR_INVALID')
    ready(manifest)
    root = Path(manifest['root'])
    write_json(root / 'source667-before.json', originals())
    empty_restore(manifest, manifest['database'], SOURCE / 'private/archive-snapshot.sql', 'original-import')
    same_dump(SOURCE / 'private/archive-snapshot.sql', dump(manifest, manifest['database'], 'original-restored.sql'))
    write_json(root / 'restored-materials.json', copy_materials(manifest))
    write_json(root / 'probe.json', probe_config(manifest, manifest['database'], 'd15_original'))
    return Fixture(path)


def derive_tasks(fixture):
    manifest, root = fixture.manifest, fixture.root
    original = fixture.facts(fixture.base)
    write_json(root / 'original-restored-facts.json', original)
    code, result = fixture.cli('original-empty-jobs', fixture.base)
    require(code == 0 and result['counts']['total'] == 0, 'ORIGINAL_JOB_PROBE_FAILED')
    for status in ('SUCCEEDED', 'FAILED', 'RUNNING', 'RETRY', 'PENDING'):
        fixture.seed(fixture.base, status)
    source = fixture.facts(fixture.base)
    require({key: row for key, row in source.items() if key != 'background_job'} ==
            {key: row for key, row in original.items() if key != 'background_job'}, 'DERIVATIVE_CHANGED_BUSINESS')
    backup = dump(manifest, manifest['database'], 'synthetic-tasks-backup.sql')
    with admin(manifest) as connection, connection.cursor() as cursor:
        verify(connection, manifest)
        cursor.execute('CREATE DATABASE ' + identifier(manifest['database'] + '_restored'))
    database = manifest['database'] + '_restored'
    empty_restore(manifest, database, backup, 'tasks-import')
    same_dump(backup, dump(manifest, database, 'synthetic-tasks-restored.sql'))
    config = probe_config(manifest, database, 'd15_restored')
    require(source == fixture.facts(config), 'RESTORED_ALL_TABLE_FACTS_DIFFER')
    write_json(root / 'synthetic-tasks-source-facts.json', source)
    write_json(root / 'synthetic-tasks-restored-config.json', config)
    return config
