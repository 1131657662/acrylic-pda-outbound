"""Pin V34 production bytes while generating new R09-only synthetic facts."""
import os
from pathlib import Path
import shutil
import subprocess
import time
from lab import WORKTREE
from mysql_io import Database
from safety import require, sha, write_json
from seed import environment, SCENARIOS, ARCHIVE_SCENARIOS, R06_SCENARIOS
from v34_fixture import seed_evidence

BASE = '23ae8c492acf47e89875852d2573392fcf379f80'
FIXTURE = 'docs/parallel/R09/ops/v34_fixture.py'
TEMP_SCENARIOS = ('*TemporarySubmissionIT.retryUsesOneCanonicalOrderAndNeverDispatches',
                  '*TemporarySubmissionIT.abortBeforePayloadCreatesOnlyControlAndCannotResurrect')


def verify_source():
    head = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=WORKTREE, text=True).strip()
    ancestor = subprocess.run(['git', 'merge-base', '--is-ancestor', BASE, head], cwd=WORKTREE)
    require(ancestor.returncode == 0, 'V34_SOURCE_BASE_MISMATCH')
    paths = ['server', 'report-rendering']
    changed = subprocess.check_output(['git', 'diff', BASE, '--name-only', '--', *paths], cwd=WORKTREE, text=True)
    untracked = subprocess.check_output(['git', 'ls-files', '--others', '--exclude-standard', '--', *paths], cwd=WORKTREE, text=True)
    require(not changed.strip() and not untracked.strip(), 'V34_PRODUCTION_SOURCE_CHANGED')
    return head


def seed(settings):
    require(settings['purpose'] == 'R09_V34_SYNTHETIC_ONLY', 'V34_LAB_REQUIRED')
    db = Database(settings, 'source')
    db.verify()
    Database(settings, 'restore').verify()
    require(not db.query('SHOW TABLES;'), 'SEED_REQUIRES_EMPTY_SOURCE')
    head = verify_source()
    scenarios = [f'*SubmissionApiIT.{name}' for name in SCENARIOS]
    scenarios += [*ARCHIVE_SCENARIOS, *R06_SCENARIOS, *TEMP_SCENARIOS]
    gradle = os.environ.get('R09_GRADLE', str(WORKTREE / ('gradlew.bat' if os.name == 'nt' else 'gradlew')))
    command = [gradle, '-p', str(WORKTREE / 'server'), 'integrationTest', '--console=plain',
               '--rerun-tasks', '--no-daemon', '--max-workers=2']
    if os.environ.get('R09_FONT_FILE'):
        command.append('-Pr08FontFile=' + os.environ['R09_FONT_FILE'])
    for scenario in scenarios:
        command += ['--tests', scenario]
    command.append('bootJar')
    log_name = f'fixture-v34-{time.time_ns()}.log'
    with (db.root / log_name).open('xb') as log:
        result = subprocess.run(command, env=environment(db), stdout=log, stderr=log, timeout=900)
    require(result.returncode == 0, 'SYNTHETIC_FIXTURES_FAILED')
    schema = db.query('SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history;')
    require(schema == '34', 'UNSUPPORTED_SCHEMA')
    seeded = seed_evidence(db)
    jar = WORKTREE / 'server/build/libs/acrylic-outbound-server-0.1.0.jar'
    shutil.copyfile(jar, Path(settings['root']) / 'candidate.jar')
    write_json(Path(settings['root']) / 'release.json', {'base_commit': BASE, 'checkout_commit': head,
        'source_snapshot': False, 'jar_sha256': sha(jar), 'fixture_scenarios': scenarios,
        'fixture_sha256': sha(WORKTREE / FIXTURE), 'fixture_log': log_name, 'schema': schema,
        'source_evidence': seeded, 'trustedVerify': False})
