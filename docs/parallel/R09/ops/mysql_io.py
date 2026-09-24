"""Protocol queries and native dumps pinned to the two dedicated R09 instances."""
from pathlib import Path
import os
import re
import subprocess
import time
from safety import require, digest, write_json, OpsError


def identifier(value):
    require(bool(re.fullmatch(r'[A-Za-z0-9_]+', value)), 'BAD_SQL_IDENTIFIER')
    return f'`{value}`'


class Database:
    def __init__(self, settings, side):
        require(side in ('source', 'restore'), 'BAD_SIDE')
        self.side = side
        self.settings = settings
        self.root = Path(settings['root']) / side
        self.name = settings.get(f'{side}_database', f'r09_ops_{side}')
        self.port = settings.get(f'{side}_port', 19309 if side == 'source' else 19310)
        require(bool(re.fullmatch(r'[a-z0-9_]+', self.name)), 'BAD_SQL_IDENTIFIER')

    def command(self, executable='mysql', account='client'):
        binary = Path(self.settings['mysql_bin']) / (executable + ('.exe' if __import__('os').name == 'nt' else ''))
        return [str(binary), f'--defaults-file={self.root / (account + ".cnf")}',
                '--protocol=TCP', '--host=127.0.0.1', f'--port={self.port}']

    def environment(self):
        env = dict(os.environ)
        env.pop('MYSQL_PWD', None)
        env['MYSQL_TEST_LOGIN_FILE'] = str(self.root / 'disabled-login-file')
        require(not Path(env['MYSQL_TEST_LOGIN_FILE']).exists(), 'UNEXPECTED_LOGIN_FILE')
        return env

    def query(self, sql, account='client'):
        from mysql_query import query
        return query(self, sql, account)

    def verify(self):
        actual = self.query('SELECT @@port,@@datadir,DATABASE(),CURRENT_USER();').split('\t')
        require(len(actual) == 4, 'INSTANCE_IDENTITY_FAILED')
        require(int(actual[0]) == self.port, 'INSTANCE_PORT_MISMATCH')
        require(Path(actual[1]).resolve() == (self.root / 'mysql').resolve(), 'INSTANCE_DIRECTORY_MISMATCH')
        require(actual[2] == self.name and actual[3] == f'r09_{self.side}@127.0.0.1', 'DATABASE_IDENTITY_MISMATCH')

    def dump(self, destination, extra=()):
        args = self.command('mysqldump') + ['--single-transaction', '--skip-lock-tables',
            '--no-tablespaces', '--set-gtid-purged=OFF', '--hex-blob', '--skip-comments',
            '--skip-add-drop-table', '--order-by-primary', '--complete-insert',
            '--skip-extended-insert', '--default-character-set=utf8mb4', *extra, self.name]
        with Path(destination).open('xb') as stream:
            result = subprocess.run(args, stdout=stream, stderr=subprocess.PIPE, timeout=180, env=self.environment())
        require(result.returncode == 0, 'MYSQL_DUMP_FAILED')
        require(Path(destination).stat().st_size > 0, 'MYSQL_DUMP_EMPTY')

    def import_file(self, path):
        self.verify()
        require(self.side == 'restore', 'RESTORE_TARGET_REQUIRED')
        require(not self.query('SHOW TABLES;'), 'RESTORE_DATABASE_NOT_EMPTY')
        args = self.command() + ['--binary-mode', '--default-character-set=utf8mb4', self.name]
        with Path(path).open('rb') as stream:
            result = subprocess.run(args, stdin=stream, stdout=subprocess.PIPE,
                                    stderr=subprocess.PIPE, timeout=180, env=self.environment())
        require(result.returncode == 0, 'MYSQL_RESTORE_FAILED')


def normalized_schema(text):
    # MySQL may spell inherited utf8mb4 explicitly after logical restore.
    # Add only this redundant declaration; retain every collation/constraint/index.
    lines = []
    for line in text.splitlines():
        column = re.match(r'  `[A-Za-z0-9_]+` ', line)
        if column and ' COLLATE utf8mb4_' in line and ' CHARACTER SET ' not in line:
            line = line.replace(' COLLATE utf8mb4_', ' CHARACTER SET utf8mb4 COLLATE utf8mb4_', 1)
        lines.append(line)
    return '\n'.join(lines)


def table_facts(db):
    db.verify()
    tables = db.query('SHOW TABLES;').splitlines()
    facts = {}
    for table in tables:
        name = identifier(table)
        count = db.query(f'SELECT COUNT(*) FROM {name};')
        checksum_result = db.query(f'CHECKSUM TABLE {name};').split('\t')
        if not count.isdigit() or len(checksum_result) != 2:
            write_json(db.root / f'fact-shape-{time.time_ns()}.json',
                       {'table': table, 'count_result': count, 'checksum_result': checksum_result})
            raise OpsError('MYSQL_FACT_RESULT_SHAPE')
        require(checksum_result[0] == f'{db.name}.{table}', 'MYSQL_FACT_TABLE_MISMATCH')
        checksum = checksum_result[1]
        require(checksum != 'NULL', 'UNSUPPORTED_TABLE_CHECKSUM')
        schema = normalized_schema(db.query(f'SHOW CREATE TABLE {name};'))
        facts[table] = {'count': int(count), 'checksum': checksum, 'schema_sha256': digest(schema.encode())}
    require(bool(facts), 'EMPTY_SCHEMA')
    return facts
