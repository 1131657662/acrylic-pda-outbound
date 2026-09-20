"""Start only a child R09 API, exercise health detection, then stop that child."""
import os
from pathlib import Path
import subprocess
import time
from lab import WORKTREE, port_free
from mysql_io import Database
from monitor import http, tcp
from safety import require, write_json, OpsError
from seed import environment
from source_snapshot import release_jar


def probe(settings):
    db = Database(settings, 'restore')
    db.verify()
    port_free(18309)
    recovered = db.root / 'recovered'
    env = environment(db)
    env.update(OUTBOUND_MIGRATE='false', OUTBOUND_STORAGE=str(recovered / 'files'),
               OUTBOUND_AUTH_SIGNING_PRIVATE_KEY=str(recovered / 'config/private.pk8'),
               OUTBOUND_AUTH_SIGNING_PUBLIC_KEY=str(recovered / 'config/public.der'))
    java = str(Path(os.environ['JAVA_HOME']) / 'bin/java.exe') if os.name == 'nt' else 'java'
    args = [java, '-Xms64m', '-Xmx384m', '-jar',
            str(release_jar(settings))]
    with (db.root / 'api-probe.log').open('xb') as log:
        process = subprocess.Popen(args, env=env, stdout=log, stderr=log,
                                   creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0)
    config = {'url': 'http://127.0.0.1:18309/api/v1/auth/csrf', 'timeout_seconds': 3, 'max_latency_ms': 3000}
    try:
        result = wait_ready(process, config)
    finally:
        process.terminate()
        process.wait(timeout=30)
    try:
        tcp({'host': '127.0.0.1', 'port': 18309})
    except OSError:
        result['stopped_service_detected'] = True
    require(result.get('stopped_service_detected'), 'CHILD_SERVICE_DID_NOT_STOP')
    result['status'] = 'PASS'
    write_json(db.root / 'api-probe.json', result)
    return result


def wait_ready(process, config):
    for _ in range(100):
        require(process.poll() is None, 'API_EXITED_DURING_STARTUP')
        try:
            return http(config)
        except Exception:
            time.sleep(.25)
    raise OpsError('API_STARTUP_TIMEOUT')
