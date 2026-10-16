"""Native MySQL transport pinned to the sole 673 instance."""
from pathlib import Path
import configparser
import hashlib
import json
import os
import subprocess

ROOT = Path(__file__).resolve().parents[4]
LAB = ROOT / '.local/e2a-cross-20260925-673'
SOURCE = ROOT / '.local/e2a-cross-20260925-667'
MYSQL = Path('C:/Program Files/MySQL/MySQL Server 8.0/bin')
DATABASE = 'acrylic_e2a_cross_20260925_673'
USER = 'e2a_cross_20260925_673'
PORT = 19383


def save(name, value):
    with (LAB / name).open('x', encoding='utf-8') as stream:
        json.dump(value, stream, indent=2)


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def environment():
    result = {key: value for key, value in os.environ.items()
              if not key.startswith(('OUTBOUND_', 'SPRING_', 'MYSQL_', 'JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS'))}
    result.update(TEMP=str(LAB / 'tmp'), TMP=str(LAB / 'tmp'),
                  MYSQL_TEST_LOGIN_FILE=str(LAB / 'private/nonexistent-login'))
    return result


def client(config_name='client.cnf'):
    config = configparser.ConfigParser(interpolation=None)
    assert config_name in ('client.cnf', 'restore.cnf')
    config.read(LAB / 'private' / config_name)
    expected = USER if config_name == 'client.cnf' else 'd15_restore_673'
    assert config['client']['user'] == expected and int(config['client']['port']) == PORT
    return [str(MYSQL / 'mysql.exe'), '--defaults-file=' + str(LAB / 'private' / config_name),
            '--protocol=TCP', '--host=127.0.0.1', '--port=' + str(PORT), '--binary-mode',
            '--default-character-set=utf8mb4', '--batch', '--raw', '--skip-column-names', DATABASE]


def sql(data, label):
    args = client('restore.cnf' if label == 'import' else 'client.cnf')
    with (LAB / (label + '.stdout')).open('xb') as out, (LAB / (label + '.stderr')).open('xb') as err:
        result = subprocess.run(args, input=data, stdout=out, stderr=err, env=environment(), timeout=90)
    save(label + '-command.json', dict(args=args, inputSha256=hashlib.sha256(data).hexdigest(),
                                      exitCode=result.returncode))
    assert result.returncode == 0, label
    return (LAB / (label + '.stdout')).read_bytes()


def query(statement):
    sentinel = 'D15_NATIVE_END_673'
    data = ("SET time_zone='+00:00';\n" + statement.rstrip(';') + ";\nSELECT '" + sentinel + "';\n").encode()
    result = subprocess.run(client(), input=data, capture_output=True, env=environment(), timeout=60)
    assert result.returncode == 0, result.stderr.decode(errors='replace')
    lines = result.stdout.decode('utf-8').splitlines()
    assert lines and lines[-1] == sentinel, 'Missing native CLI sentinel; never interpret empty output as empty facts'
    return [json.loads(line) for line in lines[:-1]]


def identity(label):
    row = query('SELECT JSON_ARRAY(@@datadir,@@port,DATABASE(),CURRENT_USER(),@@log_bin,@@server_uuid)')[0]
    assert Path(row[0]).resolve() == (LAB / 'data').resolve()
    assert row[1:5] == [PORT, DATABASE, USER + '@127.0.0.1', 1]
    prior = LAB / 'mysql-identity.json'
    if prior.exists():
        assert json.loads(prior.read_bytes()) == row
    else:
        save(prior.name, row)
    save(label + '-identity.json', row)
    return row


def dump_schema(label):
    args = [str(MYSQL / 'mysqldump.exe'), '--defaults-file=' + str(LAB / 'private/client.cnf'),
            '--no-tablespaces', '--no-data', '--skip-comments', '--compact', '--skip-add-drop-table', DATABASE]
    result = subprocess.run(args, capture_output=True, env=environment(), timeout=60)
    (LAB / (label + '-schema.sql')).write_bytes(result.stdout)
    (LAB / (label + '-schema.stderr')).write_bytes(result.stderr)
    assert result.returncode == 0
