"""Run the committed Worker only on the new R09 source, then stop that exact child."""
import os
from pathlib import Path
import subprocess
import time
from lab import port_free
from mysql_io import Database
from seed import environment
from source_snapshot import release_jar
from safety import require, write_json, OpsError
from backup import assert_no_writers
from jobs_monitor import collect


def probe(settings):
    db = Database(settings, 'source')
    db.verify()
    require(db.query('SELECT COUNT(*) FROM background_job;') == '0', 'WORKER_PROBE_REQUIRES_EMPTY_QUEUE')
    assert_no_writers(db)
    port_free(18309)
    env = environment(db)
    env.update(OUTBOUND_MIGRATE='false', SPRING_PROFILES_ACTIVE='worker')
    java = str(Path(os.environ['JAVA_HOME']) / 'bin/java.exe') if os.name == 'nt' else 'java'
    args = [java, '-Duser.timezone=UTC', '-Xms64m', '-Xmx256m', '-jar', str(release_jar(settings))]
    with (db.root / 'worker-probe.log').open('xb') as log:
        process = subprocess.Popen(args, env=env, stdout=log, stderr=log,
                                   creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0)
    try:
        wait_completed(db, process)
        port_free(18309)
    finally:
        process.terminate()
        process.wait(timeout=30)
    assert_no_writers(db)
    result = collect(db)
    result.update(child_stopped=True, nonweb_port_free=True)
    write_json(db.root / 'worker-probe.json', result)
    return result


def wait_completed(db, process):
    for _ in range(120):
        require(process.poll() is None, 'WORKER_EXITED_DURING_STARTUP')
        count = db.query("SELECT COUNT(*) FROM background_job WHERE job_type='REVIEW_REMINDER_SWEEP' AND status='SUCCEEDED';")
        if int(count) > 0:
            return
        time.sleep(.5)
    raise OpsError('WORKER_COMPLETION_TIMEOUT')
