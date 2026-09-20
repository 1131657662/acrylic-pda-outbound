"""Direct MySQL protocol queries, explicit task credentials and redacted failures."""
import configparser
import time
import pymysql
from safety import regular, require, write_json, digest, OpsError


def options(db, account):
    require(account in ('client', 'root'), 'DATABASE_ACCOUNT_INVALID')
    config = configparser.ConfigParser(interpolation=None)
    config.read(regular(db.root / (account + '.cnf')), encoding='ascii')
    expected = 'root' if account == 'root' else f'r09_{db.side}'
    require(config['client']['user'] == expected, 'DATABASE_ACCOUNT_MISMATCH')
    return {'host': '127.0.0.1', 'port': db.port, 'user': expected,
            'password': config['client']['password'], 'database': db.name if account == 'client' else None,
            'charset': 'utf8mb4', 'autocommit': True, 'connect_timeout': 5, 'read_timeout': 60,
            'write_timeout': 60, 'local_infile': False, 'init_command': "SET time_zone='+00:00'",
            'client_flag': pymysql.constants.CLIENT.MULTI_STATEMENTS}


def cell(value):
    if value is None:
        return 'NULL'
    return value.decode('utf-8') if isinstance(value, bytes) else str(value)


def query(db, sql, account):
    # No implicit option/login file, default DB, reconnect, automatic retry, or SQL logging.
    try:
        with pymysql.connect(**options(db, account)) as connection, connection.cursor() as cursor:
            cursor.execute(sql)
            results = []
            while True:
                results.extend('\t'.join(cell(value) for value in row) for row in cursor.fetchall())
                if not cursor.nextset():
                    return '\n'.join(results).strip()
    except pymysql.MySQLError as error:
        code = error.args[0] if error.args and isinstance(error.args[0], int) else None
        write_json(db.root / f'driver-failure-{time.time_ns()}.json',
                   {'query_sha256': digest(sql.encode()), 'mysql_error_code': code})
        raise OpsError('MYSQL_QUERY_FAILED') from None
