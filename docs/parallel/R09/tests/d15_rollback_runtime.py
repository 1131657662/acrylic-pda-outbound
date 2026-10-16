"""One-shot lifecycle of the dedicated rollback instance; no shared process control."""
import configparser
import secrets
import socket
import subprocess
import time

from d15_rollback_sql import LAB, MYSQL, PORT, DATABASE, USER, environment, identity, save


def port_free(port):
    with socket.socket() as sock:
        return sock.connect_ex(('127.0.0.1', port)) != 0


def require_drained(port):
    for _ in range(80):
        if port_free(port):
            return
        time.sleep(.25)
    raise AssertionError('Listener remains on ' + str(port))


def create_lab():
    assert not LAB.exists(), 'Never reuse an existing or failed lab'
    assert all(port_free(port) for port in (PORT, 19449, 19450, 19451))
    LAB.mkdir()
    for directory in ('private', 'tmp'):
        (LAB / directory).mkdir()
    sid = subprocess.check_output(['whoami', '/user', '/fo', 'csv', '/nh']).decode().split(',')[-1].strip().strip('"')
    subprocess.run(['icacls', str(LAB / 'private'), '/inheritance:r', '/grant:r',
                    '*' + sid + ':(OI)(CI)F', '*S-1-5-18:(OI)(CI)F'], check=True, capture_output=True)
    write_configuration()


def write_configuration():
    password, restore_password = secrets.token_hex(32), secrets.token_hex(32)
    (LAB / 'private/client.cnf').write_text('[client]\nuser=' + USER + '\npassword=' + password +
        '\nhost=127.0.0.1\nport=' + str(PORT) + '\nprotocol=tcp\n', encoding='utf-8')
    bootstrap = "ALTER USER 'root'@'localhost' IDENTIFIED BY '" + secrets.token_hex(32) + "';\n"
    bootstrap += f"CREATE DATABASE {DATABASE};\nCREATE USER '{USER}'@'127.0.0.1' IDENTIFIED BY '{password}';\n"
    bootstrap += f"GRANT ALL ON {DATABASE}.* TO '{USER}'@'127.0.0.1';\n"
    bootstrap += f"GRANT SHUTDOWN ON *.* TO '{USER}'@'127.0.0.1';\n"
    bootstrap += f"CREATE USER 'd15_restore_673'@'127.0.0.1' IDENTIFIED BY '{restore_password}';\n"
    bootstrap += f"GRANT ALL ON {DATABASE}.* TO 'd15_restore_673'@'127.0.0.1';\n"
    bootstrap += "GRANT SET_USER_ID ON *.* TO 'd15_restore_673'@'127.0.0.1';\n"
    bootstrap += "CREATE USER 'e2a_cross_20260925_667'@'127.0.0.1' ACCOUNT LOCK;\n"
    bootstrap += f"GRANT ALL ON {DATABASE}.* TO 'e2a_cross_20260925_667'@'127.0.0.1';\n"
    (LAB / 'private/restore.cnf').write_text('[client]\nuser=d15_restore_673\npassword=' + restore_password +
        '\nhost=127.0.0.1\nport=' + str(PORT) + '\nprotocol=tcp\n', encoding='utf-8')
    (LAB / 'private/bootstrap.sql').write_text(bootstrap, encoding='utf-8')


def start_mysql():
    args = [str(MYSQL / 'mysqld.exe'), '--no-defaults', '--initialize-insecure',
            '--datadir=' + str(LAB / 'data'), '--mysqlx=OFF', '--log-error=' + str(LAB / 'initialize.log')]
    result = subprocess.run(args, capture_output=True, env=environment(), timeout=90)
    (LAB / 'initialize.stdout').write_bytes(result.stdout)
    (LAB / 'initialize.stderr').write_bytes(result.stderr)
    save('initialize-command.json', dict(args=args, exitCode=result.returncode))
    assert result.returncode == 0
    args = [str(MYSQL / 'mysqld.exe'), '--no-defaults', '--datadir=' + str(LAB / 'data'),
            '--port=' + str(PORT), '--bind-address=127.0.0.1', '--mysqlx=OFF', '--skip-name-resolve',
            '--innodb-buffer-pool-size=64M', '--log-bin-trust-function-creators=ON',
            '--log-bin=' + str(LAB / 'binlog'), '--binlog-format=ROW',
            '--log-error=' + str(LAB / 'mysql.log'), '--init-file=' + str(LAB / 'private/bootstrap.sql')]
    with (LAB / 'mysql.stdout').open('xb') as out, (LAB / 'mysql.stderr').open('xb') as err:
        process = subprocess.Popen(args, stdout=out, stderr=err, env=environment(), creationflags=subprocess.CREATE_NO_WINDOW)
    save('mysql-command.json', dict(args=args, pid=process.pid))
    for _ in range(120):
        assert process.poll() is None, 'MySQL startup exited'
        if not port_free(PORT):
            identity('mysql-start')
            return process
        time.sleep(.25)
    raise AssertionError('MySQL readiness timeout')


def stop_mysql(process):
    identity('mysql-stop')
    dump = [str(MYSQL / 'mysqldump.exe'), '--defaults-file=' + str(LAB / 'private/client.cnf'),
            '--single-transaction', '--hex-blob', '--complete-insert', '--no-tablespaces',
            '--set-gtid-purged=OFF', '--routines', '--events', '--triggers', DATABASE]
    with (LAB / 'private/final.sql').open('xb') as out, (LAB / 'private/final-dump.stderr').open('xb') as err:
        dumped = subprocess.run(dump, stdout=out, stderr=err, env=environment(), timeout=90)
    save('dump-command.json', dict(args=dump, exitCode=dumped.returncode))
    args = [str(MYSQL / 'mysqladmin.exe'), '--defaults-file=' + str(LAB / 'private/client.cnf'), 'shutdown']
    result = subprocess.run(args, capture_output=True, env=environment(), timeout=60)
    (LAB / 'shutdown.stdout').write_bytes(result.stdout)
    (LAB / 'shutdown.stderr').write_bytes(result.stderr)
    assert result.returncode == 0
    code = process.wait(timeout=60)
    require_drained(PORT)
    save('mysql-stopped.json', dict(pid=process.pid, exitCode=code, port=PORT, listener=False))
    assert dumped.returncode == 0, 'Dump failure retained after authenticated shutdown'


def start_api(jar, label):
    assert port_free(19449)
    config = configparser.ConfigParser(interpolation=None)
    config.read(LAB / 'private/client.cnf')
    env = environment()
    env.update(OUTBOUND_DB_URL=f'jdbc:mysql://127.0.0.1:{PORT}/{DATABASE}?useSSL=false&allowPublicKeyRetrieval=true',
               OUTBOUND_DB_USER=USER, OUTBOUND_DB_PASSWORD=config['client']['password'])
    java = 'C:/Program Files/Java/jdk-21.0.11/bin/java.exe'
    args = [java, '-Xms64m', '-Xmx384m', '-Djava.io.tmpdir=' + str(LAB / 'tmp'), '-jar', str(jar),
            '--server.address=127.0.0.1', '--server.port=19449', '--spring.flyway.enabled=false',
            '--outbound.storage=' + str(LAB / 'storage'), '--outbound.worker.enabled=false',
            '--outbound.bootstrap.enabled=false', '--spring.session.jdbc.cleanup-cron=-',
            '--server.servlet.session.cookie.secure=false',
            '--outbound.signing.private-key=' + str(LAB / 'private/server-private.der'),
            '--outbound.signing.public-key=' + str(LAB / 'private/server-public.der')]
    with (LAB / (label + '-api.stdout')).open('xb') as out, (LAB / (label + '-api.stderr')).open('xb') as err:
        process = subprocess.Popen(args, stdout=out, stderr=err, cwd=LAB, env=env,
                                   creationflags=subprocess.CREATE_NO_WINDOW)
    save(label + '-api-command.json', dict(args=args, pid=process.pid))
    return process


def listener_identity(process, label):
    command = 'Get-NetTCPConnection -State Listen -LocalPort 19449 | Select-Object LocalPort,OwningProcess | ConvertTo-Json'
    result = subprocess.run(['powershell', '-NoProfile', '-Command', command], capture_output=True, check=True)
    import json
    row = json.loads(result.stdout)
    assert row == dict(LocalPort=19449, OwningProcess=process.pid), row
    save(label + '-listener.json', row)


def stop_api(process, label):
    # Windows TerminateProcess: explicitly recorded, never called on a discovered/shared PID.
    if process.poll() is None:
        process.terminate()
    code = process.wait(timeout=30)
    require_drained(19449)
    save(label + '-api-stopped.json', dict(pid=process.pid, exitCode=code, port=19449,
         listener=False, method='Windows TerminateProcess after completed HTTP reads; not graceful shutdown'))
