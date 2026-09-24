"""Provision new, private R09 MySQL instances; never reset or delete existing data."""
import os
from pathlib import Path
import re
import secrets
import socket
import subprocess
import time
from datetime import datetime, timezone
from mysql_io import Database
from safety import require, run, secure_directory, write_json, read_json

WORKTREE = Path(__file__).resolve().parents[4]
LAB_ROOT = WORKTREE / '.local' / 'r09'


def port_free(port):
    with socket.socket() as probe:
        probe.bind(('127.0.0.1', port))


def load(path):
    path = Path(path).resolve()
    require(path.parent.parent == LAB_ROOT.resolve(), 'LAB_CONFIG_OUTSIDE_R09')
    settings = read_json(path)
    require(Path(settings['root']).resolve() == path.parent, 'LAB_ROOT_MISMATCH')
    require(settings['purpose'] in ('R09_SYNTHETIC_ONLY', 'R09_V34_SYNTHETIC_ONLY'), 'LAB_PURPOSE_MISMATCH')
    if settings['purpose'] == 'R09_V34_SYNTHETIC_ONLY':
        require((settings.get('source_port'), settings.get('restore_port'), settings.get('api_port')) ==
                (19434, 19435, 18434), 'LAB_PORT_MISMATCH')
        token = path.parent.name[-6:]
        for side in ('source', 'restore'):
            require(bool(re.fullmatch('[0-9a-f]{6}', token)) and
                    settings.get(f'{side}_database') == f'r09_v34_{token}_{side}',
                    'LAB_DATABASE_MISMATCH')
    else:
        require(not any(key in settings for key in ('source_port', 'restore_port', 'api_port',
                'source_database', 'restore_database')), 'LAB_PROFILE_MISMATCH')
    require(not path.parent.is_symlink() and not path.parent.is_junction(), 'LAB_LINK_REFUSED')
    return settings


def setup(mysql_bin):
    os.umask(0o077)
    require(LAB_ROOT.resolve().is_relative_to(WORKTREE.resolve()), 'LAB_ROOT_OUTSIDE_WORKTREE')
    for port in (19309, 19310, 18309):
        port_free(port)
    LAB_ROOT.mkdir(parents=True, exist_ok=True)
    stamp = datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ') + '-' + secrets.token_hex(3)
    root = LAB_ROOT / stamp
    secure_directory(root)
    settings = {'purpose': 'R09_SYNTHETIC_ONLY', 'root': str(root), 'mysql_bin': str(Path(mysql_bin).resolve())}
    write_json(root / 'lab.json', settings)
    for side in ('source', 'restore'):
        provision(Database(settings, side))
    run(['java', str(WORKTREE / 'tools/CreateDevelopmentSigningKey.java'), str(root / 'source/config')])
    return root / 'lab.json'


def setup_v34(mysql_bin):
    os.umask(0o077)
    require(LAB_ROOT.resolve().is_relative_to(WORKTREE.resolve()), 'LAB_ROOT_OUTSIDE_WORKTREE')
    for port in (19434, 19435, 18434):
        port_free(port)
    LAB_ROOT.mkdir(parents=True, exist_ok=True)
    token = secrets.token_hex(3)
    root = LAB_ROOT / (datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ') + '-v34-' + token)
    secure_directory(root)
    settings = {'purpose': 'R09_V34_SYNTHETIC_ONLY', 'root': str(root),
                'mysql_bin': str(Path(mysql_bin).resolve()), 'source_port': 19434, 'restore_port': 19435,
                'api_port': 18434, 'source_database': f'r09_v34_{token}_source',
                'restore_database': f'r09_v34_{token}_restore'}
    write_json(root / 'lab.json', settings)
    for side in ('source', 'restore'):
        provision(Database(settings, side))
    run(['java', str(WORKTREE / 'tools/CreateDevelopmentSigningKey.java'), str(root / 'source/config')])
    return root / 'lab.json'


def option_file(path, user, password):
    path.write_text(f'[client]\nuser={user}\npassword={password}\n', encoding='ascii')
    path.chmod(0o600)


def provision(db):
    db.root.mkdir(mode=0o700)
    data = db.root / 'mysql'
    data.mkdir(mode=0o700)
    (db.root / 'files').mkdir(mode=0o700)
    (db.root / 'config').mkdir(mode=0o700)
    root_password, password = secrets.token_hex(32), secrets.token_hex(32)
    option_file(db.root / 'root.cnf', 'root', root_password)
    option_file(db.root / 'client.cnf', f'r09_{db.side}', password)
    write_json(db.root / 'config/runtime.json', {'database': db.name, 'port': db.port, 'password': password})
    exe = Path(db.settings['mysql_bin']) / ('mysqld.exe' if os.name == 'nt' else 'mysqld')
    init = db.root / 'init.sql'
    init.write_text(f"ALTER USER 'root'@'localhost' IDENTIFIED BY '{root_password}';", encoding='ascii')
    args = [str(exe), '--no-defaults', f'--basedir={exe.parent.parent}', f'--datadir={data}']
    run(args + ['--initialize-insecure'])
    start_process(db, args + [f'--init-file={init.as_posix()}'])
    db.query(f"CREATE DATABASE {db.name} CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs;"
             f"CREATE USER 'r09_{db.side}'@'127.0.0.1' IDENTIFIED BY '{password}';"
             f"GRANT ALL ON {db.name}.* TO 'r09_{db.side}'@'127.0.0.1';", 'root')
    db.verify()
    init.unlink()  # Exact task-created file only, after the instance identity was checked.


def start_process(db, args):
    args += ['--bind-address=127.0.0.1', f'--port={db.port}', '--mysqlx=OFF', '--skip-log-bin',
             '--innodb-buffer-pool-size=134217728', '--max-connections=24', '--default-time-zone=+00:00',
             '--general-log=OFF', '--slow-query-log=OFF', '--local-infile=OFF',
             f'--log-error={db.root / "mysql-error.log"}']
    with (db.root / 'process.log').open('xb') as log:
        process = subprocess.Popen(args, stdout=log, stderr=log,
                                   creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0)
    write_json(db.root / 'process.json', {'pid': process.pid})
    for _ in range(80):
        if process.poll() is not None:
            break
        if ready(db):
            return
        time.sleep(.25)
    raise RuntimeError('R09 instance startup failed; private logs retained')


def ready(db):
    try:
        actual = db.query('SELECT @@port,@@datadir;', 'root').split('\t')
        return int(actual[0]) == db.port and Path(actual[1]).resolve() == (db.root / 'mysql').resolve()
    except Exception:
        return False


def stop(settings):
    for side in ('source', 'restore'):
        db = Database(settings, side)
        db.verify()
        run(db.command('mysqladmin', 'root') + ['shutdown'], env=db.environment())
