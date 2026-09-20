"""Run existing committed synthetic business fixtures only against the R09 source."""
import os
from pathlib import Path
import subprocess
from lab import WORKTREE
from mysql_io import Database
from safety import require, read_json, write_json, sha

SCENARIOS = (
    'completeSubmissionRecordsOnceAndRetriesReturnOriginalReceipt',
    'disabledAutomaticAccountingStillRetainsGlobalDispatchGuards',
    'shortShipmentReservesUntilExplicitSupervisorApproval',
    'abortBeforeBodyLeavesPermanentTombstoneAndNoFakePayload',
)
ARCHIVE_SCENARIOS = (
    '*SubmissionArchiveIT.durableCopiesProduceStableSignedConfirmationAndAndroidContract',
    '*AuditArchiveIT.stableSignedSegmentsProduceAndroidContract',
)


def environment(db):
    config = read_json(db.root / 'config/runtime.json')
    env = {k: v for k, v in os.environ.items() if not k.startswith('OUTBOUND_')}
    env.update(OUTBOUND_DB_URL=f'jdbc:mysql://127.0.0.1:{db.port}/{db.name}?connectionTimeZone=UTC',
               OUTBOUND_DB_USER=f'r09_{db.side}', OUTBOUND_DB_PASSWORD=config['password'],
               OUTBOUND_MIGRATE='true', OUTBOUND_COOKIE_SECURE='false', OUTBOUND_BIND='127.0.0.1',
               OUTBOUND_PORT='18309', OUTBOUND_STORAGE=str(db.root / 'files'),
               OUTBOUND_AUTH_SIGNING_PRIVATE_KEY=str(db.root / 'config/private.pk8'),
               OUTBOUND_AUTH_SIGNING_PUBLIC_KEY=str(db.root / 'config/public.der'))
    return env


def seed(settings, source_commit=None):
    db = Database(settings, 'source')
    db.verify()
    require(not db.query('SHOW TABLES;'), 'SEED_REQUIRES_EMPTY_SOURCE')
    from source_snapshot import export
    source = export(settings, source_commit) if source_commit else WORKTREE
    scenarios = [f'*SubmissionApiIT.{name}' for name in SCENARIOS]
    if source_commit:
        scenarios.extend(ARCHIVE_SCENARIOS)
    gradle = os.environ.get('R09_GRADLE', str(WORKTREE / ('gradlew.bat' if os.name == 'nt' else 'gradlew')))
    command = [gradle,
               '-p', str(source / 'server'), 'integrationTest', '--console=plain', '--rerun-tasks']
    for scenario in scenarios:
        command += ['--tests', scenario]
    command.append('bootJar')
    with (db.root / 'fixture.log').open('xb') as log:
        result = subprocess.run(command, env=environment(db), stdout=log, stderr=log, timeout=900)
    require(result.returncode == 0, 'SYNTHETIC_FIXTURES_FAILED')
    jar = source / 'server/build/libs/acrylic-outbound-server-0.1.0.jar'
    write_json(Path(settings['root']) / 'release.json', {
        'base_commit': source_commit or '2369392076083a5a4f59d8ea3d6edebe5067a03f',
        'source_snapshot': bool(source_commit), 'jar_sha256': sha(jar), 'fixture_scenarios': scenarios,
        'schema': db.query('SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history WHERE success=1;')})
