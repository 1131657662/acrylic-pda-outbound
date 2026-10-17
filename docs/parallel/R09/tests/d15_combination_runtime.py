"""One fresh dedicated MySQL lifecycle for the upgrade/program/restore combination."""
import hashlib
import json
import os
from pathlib import Path
import secrets
import socket
import subprocess
import time

ROOT = Path('D:/acrylic-pda-outbound')
MYSQL = Path('C:/Program Files/MySQL/MySQL Server 8.0/bin')


def save(path, value):
    with path.open('x', encoding='utf-8') as output:
        json.dump(value, output, indent=2)


def digest(path):
    with path.open('rb') as source:
        return hashlib.file_digest(source, 'sha256').hexdigest()


def port_free(port):
    with socket.socket() as client:
        return client.connect_ex(('127.0.0.1', port)) != 0


def command(args, directory, label, env, data=None, timeout=120):
    with (directory / (label + '.stdout')).open('xb') as out, (directory / (label + '.stderr')).open('xb') as err:
        result = subprocess.run(args, input=data, stdout=out, stderr=err, env=env,
                                cwd=ROOT, timeout=timeout, creationflags=subprocess.CREATE_NO_WINDOW)
    save(directory / (label + '-command.json'), dict(args=args, exitCode=result.returncode))
    return result.returncode


def prepare(run, port, http):
    assert 1 <= run <= 9999 and 1024 <= port <= 65535 and 1024 <= http <= 65535 and port != http
    attempt = ROOT / f'.local/e2a-cross-20260925-{run}'
    assert not attempt.exists(), 'Never reuse an existing attempt'
    assert port_free(port) and port_free(http), 'Allocated port occupied'
    attempt.mkdir()
    for name in ('private', 'tmp', 'storage'):
        (attempt / name).mkdir()
    sid = subprocess.check_output(['whoami', '/user', '/fo', 'csv', '/nh']).decode().split(',')[-1].strip().strip('"')
    subprocess.run(['icacls', str(attempt / 'private'), '/inheritance:r', '/grant:r',
                    '*' + sid + ':(OI)(CI)F', '*S-1-5-18:(OI)(CI)F'], check=True, capture_output=True)
    env = os.environ.copy()
    env.update(TEMP=str(attempt / 'tmp'), TMP=str(attempt / 'tmp'),
               JAVA_TOOL_OPTIONS='-Djava.io.tmpdir=' + str(attempt / 'tmp'),
               MYSQL_TEST_LOGIN_FILE=str(attempt / 'private/disabled-login-file'))
    env.pop('MYSQL_PWD', None)
    password = secrets.token_hex(32)
    schema, user = f'acrylic_e2a_cross_20260925_{run}', f'e2a_cross_20260925_{run}'
    write_config(attempt, schema, user, port, password)
    env.update(E2A_CROSS_VERIFY='true', E2A_CROSS_RUN=str(run), E2A_CROSS_ROOT=str(ROOT),
               E2A_CROSS_PORT=str(port), E2A_HTTP_PORT=str(http), E2A_CROSS_PASSWORD=password,
               OUTBOUND_DB_PASSWORD=password, OUTBOUND_COOKIE_SECURE='false')
    return attempt, env


def write_config(attempt, schema, user, port, password):
    config = f'[client]\nhost=127.0.0.1\nport={port}\nuser={user}\npassword={password}\nprotocol=tcp\n'
    (attempt / 'private/client.cnf').write_text(config, encoding='utf-8')
    sql = "ALTER USER 'root'@'localhost' IDENTIFIED BY '" + secrets.token_hex(32) + "';\n"
    sql += f"CREATE USER '{user}'@'127.0.0.1' IDENTIFIED BY '{password}';\n"
    for database in (schema, schema + '_restore'):
        sql += f'CREATE DATABASE {database} CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs;\n'
        sql += f"GRANT ALL ON {database}.* TO '{user}'@'127.0.0.1';\n"
    sql += f"GRANT SHUTDOWN ON *.* TO '{user}'@'127.0.0.1';\n"
    (attempt / 'private/bootstrap.sql').write_text(sql, encoding='utf-8')


def launch_mysql(attempt, port, env):
    args = [str(MYSQL / 'mysqld.exe'), '--no-defaults', '--initialize-insecure',
            '--datadir=' + str(attempt / 'data'), '--mysqlx=OFF', '--log-error=' + str(attempt / 'initialize.log')]
    assert command(args, attempt, 'initialize', env) == 0
    args = [str(MYSQL / 'mysqld.exe'), '--no-defaults', '--datadir=' + str(attempt / 'data'),
            '--port=' + str(port), '--bind-address=127.0.0.1', '--mysqlx=OFF', '--skip-name-resolve',
            '--innodb-buffer-pool-size=64M', '--log-bin-trust-function-creators=ON',
            '--log-bin=' + str(attempt / 'binlog'), '--binlog-format=ROW',
            '--log-error=' + str(attempt / 'mysql.log'), '--init-file=' + str(attempt / 'private/bootstrap.sql')]
    with (attempt / 'mysql.stdout').open('xb') as out, (attempt / 'mysql.stderr').open('xb') as err:
        process = subprocess.Popen(args, stdout=out, stderr=err, env=env, creationflags=subprocess.CREATE_NO_WINDOW)
    save(attempt / 'mysql-process.json', dict(args=args, pid=process.pid))
    return process


def identity(attempt, run, port, env, label):
    sql = 'SELECT @@port,@@datadir,DATABASE(),CURRENT_USER(),@@log_bin,@@server_uuid;'
    args = [str(MYSQL / 'mysql.exe'), '--defaults-file=' + str(attempt / 'private/client.cnf'),
            '--batch', '--skip-column-names', f'acrylic_e2a_cross_20260925_{run}', '-e', sql]
    assert command(args, attempt, label, env) == 0
    cells = (attempt / (label + '.stdout')).read_text('utf-8').strip().split('\t')
    assert cells[0] == str(port) and cells[2] == f'acrylic_e2a_cross_20260925_{run}'
    assert Path(cells[1].replace('\\\\', '/')).resolve() == (attempt / 'data').resolve()
    assert cells[3] == f'e2a_cross_20260925_{run}@127.0.0.1' and cells[4] == '1'
    return cells


def wait_mysql(process, port):
    for _ in range(160):
        assert process.poll() is None, 'Dedicated MySQL startup exited'
        if not port_free(port):
            return
        time.sleep(.25)
    raise AssertionError('Dedicated MySQL readiness timeout')


def finish_mysql(process, attempt, run, port, env):
    identity(attempt, run, port, env, 'identity-final')
    args = [str(MYSQL / 'mysqldump.exe'), '--defaults-file=' + str(attempt / 'private/client.cnf'),
            '--single-transaction', '--skip-lock-tables', '--no-tablespaces', '--set-gtid-purged=OFF',
            '--hex-blob', '--routines', '--events', '--triggers', '--databases',
            f'acrylic_e2a_cross_20260925_{run}', f'acrylic_e2a_cross_20260925_{run}_restore']
    dumped = -1
    try:
        dumped = command(args, attempt / 'private', 'final-dump', env)
    finally:
        args = [str(MYSQL / 'mysqladmin.exe'), '--defaults-file=' + str(attempt / 'private/client.cnf'), 'shutdown']
        stopped = command(args, attempt, 'shutdown', env, timeout=60)
        assert stopped == 0, 'Authenticated shutdown failed'
        code = process.wait(timeout=60)
        assert port_free(port)
        save(attempt / 'mysql-stopped.json', dict(pid=process.pid, exitCode=code, port=port, listener=False))
    assert dumped == 0
