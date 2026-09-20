"""Retain synthetic V33 scenarios at the explicitly pinned first-wave business base."""
import os
from pathlib import Path
import shutil
import subprocess
import time
from lab import WORKTREE
from mysql_io import Database
from safety import require, sha, write_json
from seed import environment, SCENARIOS, ARCHIVE_SCENARIOS, R06_SCENARIOS

BASE = '90581375128ee426fcf979a76eb44f4bb7734fef'
FIXTURE = 'server/src/test/java/com/acrylic/outbound/inventory/R09V33FixtureIT.java'


def seed(settings):
    db = Database(settings, 'source')
    db.verify()
    Database(settings, 'restore').verify()
    require(not db.query('SHOW TABLES;'), 'SEED_REQUIRES_EMPTY_SOURCE')
    head = verify_source()
    scenarios = [f'*SubmissionApiIT.{name}' for name in SCENARIOS]
    scenarios += [*ARCHIVE_SCENARIOS, *R06_SCENARIOS, '*R09V33FixtureIT']
    gradle = os.environ.get('R09_GRADLE', str(WORKTREE / ('gradlew.bat' if os.name == 'nt' else 'gradlew')))
    command = [gradle, '-p', str(WORKTREE / 'server'), 'integrationTest', '--console=plain', '--rerun-tasks', '--no-daemon', '--max-workers=2']
    if os.environ.get('R09_FONT_FILE'):
        command.append('-Pr08FontFile=' + os.environ['R09_FONT_FILE'])
    for scenario in scenarios:
        command += ['--tests', scenario]
    command.append('bootJar')
    log_name = f'fixture-{time.time_ns()}.log'
    env = environment(db)
    env['R09_V33_FIXTURE'] = 'true'
    with (db.root / log_name).open('xb') as log:
        result = subprocess.run(command, env=env, stdout=log, stderr=log, timeout=900)
    require(result.returncode == 0, 'SYNTHETIC_FIXTURES_FAILED')
    jar = WORKTREE / 'server/build/libs/acrylic-outbound-server-0.1.0.jar'
    shutil.copyfile(jar, Path(settings['root']) / 'candidate.jar')
    write_json(Path(settings['root']) / 'release.json', {'base_commit': BASE, 'checkout_commit': head, 'source_snapshot': False,
        'jar_sha256': sha(jar), 'fixture_scenarios': scenarios, 'fixture_sha256': sha(WORKTREE / FIXTURE),
        'fixture_log': log_name, 'schema': db.query('SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history;')})


def verify_source():
    head = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=WORKTREE, text=True).strip()
    require(subprocess.run(['git', 'merge-base', '--is-ancestor', BASE, head], cwd=WORKTREE).returncode == 0, 'V33_SOURCE_BASE_MISMATCH')
    paths = ['server', 'report-rendering', f':(exclude){FIXTURE}']
    changed = subprocess.check_output(['git', 'diff', BASE, '--name-only', '--', *paths], cwd=WORKTREE, text=True)
    untracked = subprocess.check_output(['git', 'ls-files', '--others', '--exclude-standard', '--', *paths], cwd=WORKTREE, text=True)
    require(not changed.strip() and not untracked.strip(), 'V33_PRODUCTION_SOURCE_CHANGED')
    return head
