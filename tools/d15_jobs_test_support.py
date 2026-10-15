"""Fresh per-case synthetic task tables with retained configuration and conservation receipts."""
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import uuid
from d15_jobs_fixture import ROOT, admin, verify, account

OPS = ROOT / 'docs/parallel/R09/ops'
sys.path.insert(0, str(OPS))
from safety import write_json


class Fixture:
    def __init__(self, path):
        self.manifest = json.loads(Path(path).read_text(encoding='utf-8-sig'))
        self.root = Path(self.manifest['root'])
        self.base = json.loads((self.root / 'probe.json').read_text())
        with admin(self.manifest) as connection:
            assert verify(connection, self.manifest) == self.base['server_uuid']

    def case(self, name, missing=False, wrong_shape=False):
        database = self.manifest['database'] + '_' + name
        with admin(self.manifest) as connection, connection.cursor() as cursor:
            cursor.execute(f'CREATE DATABASE `{database}`')
            for table in ('background_job', 'flyway_schema_history'):
                cursor.execute(f'CREATE TABLE `{database}`.`{table}` LIKE `{self.manifest["database"]}`.`{table}`')
            cursor.execute(f'INSERT INTO `{database}`.flyway_schema_history SELECT * FROM flyway_schema_history')
            if wrong_shape:
                cursor.execute(f'ALTER TABLE `{database}`.background_job MODIFY lease_until VARCHAR(80) NULL')
            if missing:
                cursor.execute(f'RENAME TABLE `{database}`.background_job TO `{database}`.retained_job')
            grants = ['GRANT SELECT (status,available_at,lease_until) ON `{database}`.' +
                      ('retained_job' if missing else 'background_job') + ' TO \'{user}\'@\'127.0.0.1\'',
                      'GRANT SELECT (version,success) ON `{database}`.flyway_schema_history TO \'{user}\'@\'127.0.0.1\'']
            file = account(cursor, self.manifest, 'd15_' + name, grants, database)
        return {**self.base, 'database': database, 'current_user': 'd15_' + name + '@127.0.0.1',
                'credentials_file': file}

    def seed(self, config, status, count=1):
        with admin(self.manifest) as connection, connection.cursor() as cursor:
            for _ in range(count):
                ident = uuid.uuid4()
                running = status == 'RUNNING'
                cursor.execute(f'INSERT INTO `{config["database"]}`.background_job '
                               '(id,job_type,dedupe_key,status,payload,attempt_count,max_attempts,available_at,'
                               'lease_owner,lease_token,lease_until,last_error,created_at,updated_at) '
                               'VALUES (%s,\'MONITOR_TEST\',%s,%s,%s,%s,3,UTC_TIMESTAMP(3)-INTERVAL 600 SECOND,'
                               '%s,%s,IF(%s,UTC_TIMESTAMP(3)-INTERVAL 10 SECOND,NULL),%s,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))',
                               (ident.bytes, str(ident), status, '{"secret":"CANARY_PAYLOAD"}',
                                0 if status == 'PENDING' else 1, 'CANARY_OWNER' if running else None,
                                uuid.uuid4().bytes if running else None, running, 'CANARY_LAST_ERROR'))

    def facts(self, config):
        with admin(self.manifest) as connection, connection.cursor() as cursor:
            cursor.execute('SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA=%s '
                           'ORDER BY TABLE_NAME', (config['database'],))
            tables = [row[0] for row in cursor.fetchall()]
            facts = {}
            for table in tables:
                cursor.execute(f'SELECT * FROM `{config["database"]}`.`{table}`')
                rows = sorted(repr(row) for row in cursor.fetchall())
                facts[table] = {'rows': len(rows), 'sha256': hashlib.sha256(repr(rows).encode()).hexdigest()}
            return facts

    def cli(self, name, config, monitor=False):
        before = self.facts(config)
        path = self.root / (name + '.json')
        if monitor:
            sys.path.insert(0, str(ROOT / 'docs/parallel/R09/tests'))
            from backup_factory import backup_fixture
            base = self.root / (name + '-backup')
            base.mkdir()
            backup = backup_fixture(base)
            config = {'jobs': config, 'backup': {'directory': str(backup.parent), 'max_age_seconds': 93600},
                      'disks': [{'path': str(self.root), 'minimum_free_bytes': 1, 'minimum_free_percent': 0}]}
        write_json(path, config)
        result = subprocess.run([sys.executable, str(OPS / 'cli.py'), 'monitor' if monitor else 'jobs-monitor',
                                 '--config', str(path)], capture_output=True, text=True, timeout=15)
        (self.root / (name + '.stdout')).write_text(result.stdout)
        (self.root / (name + '.stderr')).write_text(result.stderr)
        after = self.facts(config['jobs'] if monitor else config)
        write_json(self.root / (name + '-conservation.json'), {'before': before, 'after': after,
                   'unchanged': before == after, 'exit_code': result.returncode})
        assert before == after, 'PROBE_MUTATED_DATABASE'
        assert not result.stderr, result.stderr
        assert all(value not in result.stdout for value in ('CANARY', 'password', 'SELECT ', 'd15_admin'))
        return result.returncode, json.loads(result.stdout)
