"""Identity-pinned, bounded read-only connection for task health."""
from contextlib import contextmanager
from pathlib import Path
import re
from jobs_credentials import credentials
from safety import require

KEYS = {'host', 'port', 'datadir', 'database', 'current_user', 'server_uuid', 'credentials_file'}
COLUMNS = {'background_job': {'status', 'available_at', 'lease_until'},
           'flyway_schema_history': {'version', 'success'}}


def validate(config):
    require(set(config) in (KEYS, KEYS | {'limits'}), 'JOB_CONFIG_INVALID')
    require(config['host'] == '127.0.0.1', 'JOB_LOOPBACK_REQUIRED')
    require(type(config['port']) is int and 1 <= config['port'] <= 65535, 'JOB_PORT_INVALID')
    require(isinstance(config['database'], str) and
            re.fullmatch(r'[a-z][a-z0-9_]{0,63}', config['database']), 'JOB_DATABASE_INVALID')
    require(isinstance(config['current_user'], str) and
            re.fullmatch(r'[a-z][a-z0-9_]{0,31}@127\.0\.0\.1', config['current_user']), 'JOB_USER_INVALID')
    require(isinstance(config['server_uuid'], str) and
            re.fullmatch(r'[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}', config['server_uuid']), 'JOB_UUID_INVALID')
    require(Path(config['datadir']).is_absolute(), 'JOB_DATADIR_INVALID')
    require(Path(config['credentials_file']).is_absolute(), 'JOB_CREDENTIAL_PATH_INVALID')


def identity(cursor, config):
    cursor.execute('SELECT @@port,@@datadir,DATABASE(),CURRENT_USER(),@@server_uuid')
    row = cursor.fetchone()
    require(row[0] == config['port'] and Path(row[1]).resolve() == Path(config['datadir']).resolve()
            and row[2:] == (config['database'], config['current_user'], config['server_uuid']),
            'JOB_INSTANCE_IDENTITY_MISMATCH')


def grants(cursor, config):
    cursor.execute('SHOW GRANTS')
    seen = set()
    for (grant,) in cursor.fetchall():
        if re.fullmatch(r'GRANT USAGE ON \*\.\* TO `[^`]+`@`127\.0\.0\.1`', grant):
            continue
        match = re.fullmatch(r'GRANT SELECT \(([^)]+)\) ON `([^`]+)`\.`([^`]+)` TO `[^`]+`@`127\.0\.0\.1`', grant)
        require(match is not None, 'JOB_SELECT_ONLY_ACCOUNT_REQUIRED')
        columns, database, table = match.groups()
        require(database == config['database'] and table in COLUMNS and
                {part.strip().strip('`') for part in columns.split(',')} == COLUMNS[table],
                'JOB_SELECT_ONLY_ACCOUNT_REQUIRED')
        seen.add(table)
    require(seen == set(COLUMNS), 'JOB_SELECT_ONLY_ACCOUNT_REQUIRED')


@contextmanager
def connect(config):
    validate(config)
    account = credentials(config['credentials_file'])
    require(account['user'] == config['current_user'].split('@')[0], 'JOB_CREDENTIAL_USER_MISMATCH')
    import pymysql
    with pymysql.connect(host=config['host'], port=config['port'], database=config['database'],
                         **account, charset='utf8mb4', autocommit=False, connect_timeout=3,
                         read_timeout=5, write_timeout=5, local_infile=False) as connection:
        with connection.cursor() as cursor:
            identity(cursor, config)
            grants(cursor, config)
            cursor.execute('SET SESSION MAX_EXECUTION_TIME=3000')
            cursor.execute('SET SESSION lock_wait_timeout=3')
            cursor.execute('START TRANSACTION READ ONLY')
            try:
                yield cursor
            finally:
                connection.rollback()
