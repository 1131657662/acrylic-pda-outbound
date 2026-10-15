"""Create synthetic task inputs on the allocated D15 instance, retaining every file."""
import configparser
import json
from pathlib import Path
import secrets
import subprocess
import sys
import time
import pymysql

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'docs/parallel/R09/ops'))
from safety import require, write_json


def admin(manifest):
    config = configparser.ConfigParser(interpolation=None)
    config.read(Path(manifest['root']) / 'private/admin.cnf')
    return pymysql.connect(host='127.0.0.1', port=manifest['port'], **dict(config['client']),
                           database=manifest['database'], autocommit=True, connect_timeout=3)


def verify(connection, manifest):
    with connection.cursor() as cursor:
        cursor.execute('SELECT @@port,@@datadir,DATABASE(),@@server_uuid')
        row = cursor.fetchone()
    require(row[:1] == (manifest['port'],) and Path(row[1]).resolve() == Path(manifest['datadir']).resolve()
            and row[2] == manifest['database'], 'FIXTURE_IDENTITY_MISMATCH')
    return row[3]


def ready(manifest):
    for _ in range(30):
        try:
            with admin(manifest) as connection:
                return verify(connection, manifest)
        except pymysql.OperationalError:
            time.sleep(0.5)
    raise RuntimeError('ISOLATED_INSTANCE_NOT_READY')


def migrate(manifest):
    root = Path(manifest['root'])
    java = root / 'Migrate.java'
    java.write_text('''import java.nio.file.*; import java.util.*; import java.io.*;
import org.flywaydb.core.Flyway;
class Migrate { public static void main(String[] a) throws Exception {
 Properties p=new Properties(); try(var r=Files.newBufferedReader(Path.of(a[0]))){p.load(r);}
 Flyway.configure().dataSource(a[1],p.getProperty("user"),p.getProperty("password"))
 .locations("filesystem:"+a[2]).load().migrate(); }}''', encoding='utf-8')
    cnf = configparser.ConfigParser(interpolation=None)
    cnf.read(root / 'private/admin.cnf')
    props = root / 'private/migration.properties'
    props.write_text('user=' + cnf['client']['user'] + '\npassword=' + cnf['client']['password'] + '\n')
    classpath = ';'.join(json.loads((ROOT / 'server/build/recovery-rekey-classpath.json').read_text()))
    command = ['java', f'-Djava.io.tmpdir={root / "tmp"}', '--class-path', classpath, str(java), str(props),
               f'jdbc:mysql://127.0.0.1:{manifest["port"]}/{manifest["database"]}?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC',
               str(ROOT / 'server/src/main/resources/db/migration')]
    with (root / 'migration.log').open('xb') as output:
        result = subprocess.run(command, stdout=output, stderr=subprocess.STDOUT, timeout=180)
    require(result.returncode == 0, 'FIXTURE_MIGRATION_FAILED')


def account(cursor, manifest, user, grant, database=None):
    database = database or manifest['database']
    password = secrets.token_hex(32)
    cursor.execute(f"CREATE USER '{user}'@'127.0.0.1' IDENTIFIED BY %s", (password,))
    for statement in grant:
        cursor.execute(statement.format(database=database, user=user))
    path = Path(manifest['root']) / f'private/{user}.cnf'
    path.write_text(f'[client]\nuser={user}\npassword={password}\n', encoding='utf-8')
    return str(path)


def setup(path):
    manifest = json.loads(Path(path).read_text(encoding='utf-8-sig'))
    require(manifest['purpose'] == 'D15_SYNTHETIC_MONITOR_ONLY', 'FIXTURE_PURPOSE_INVALID')
    uuid = ready(manifest)
    migrate(manifest)
    grants = ['GRANT SELECT (status,available_at,lease_until) ON `{database}`.background_job TO \'{user}\'@\'127.0.0.1\'',
              'GRANT SELECT (version,success) ON `{database}`.flyway_schema_history TO \'{user}\'@\'127.0.0.1\'']
    with admin(manifest) as connection, connection.cursor() as cursor:
        file = account(cursor, manifest, 'd15_probe', grants)
        account(cursor, manifest, 'd15_broad', ['GRANT SELECT ON `{database}`.* TO \'{user}\'@\'127.0.0.1\''])
        account(cursor, manifest, 'd15_denied', [])
    config = {key: manifest[key] for key in ('port', 'datadir', 'database')}
    config.update(host='127.0.0.1', current_user='d15_probe@127.0.0.1', server_uuid=uuid, credentials_file=file)
    write_json(Path(manifest['root']) / 'probe.json', config)
    print(json.dumps({'status': 'PASS', 'fixture_ready': True}))


if __name__ == '__main__':
    setup(sys.argv[1])
