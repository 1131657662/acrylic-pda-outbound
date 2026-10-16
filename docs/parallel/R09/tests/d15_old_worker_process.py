"""Launch only pinned JARs against the dedicated worker compatibility database."""
import configparser
import json
import subprocess
import time

from d15_old_worker_lab import LAB, PORT, HTTP, DATABASE, USER, RENDERER, sql, runtime

JAVA = 'C:/Program Files/Java/jdk-21.0.11/bin/java.exe'


def start(jar, label, worker=False):
    config = configparser.ConfigParser(interpolation=None)
    config.read(LAB / 'private/client.cnf')
    env = sql.environment()
    env.update(OUTBOUND_DB_URL=f'jdbc:mysql://127.0.0.1:{PORT}/{DATABASE}?useSSL=false&allowPublicKeyRetrieval=true',
               OUTBOUND_DB_USER=USER, OUTBOUND_DB_PASSWORD=config['client']['password'])
    args = [JAVA, '-Xms64m', '-Xmx384m', '-Djava.io.tmpdir=' + str(LAB / 'tmp'), '-jar', str(jar),
            '--spring.flyway.enabled=false', '--outbound.storage=' + str(LAB / 'storage'),
            '--outbound.bootstrap.enabled=false', '--spring.session.jdbc.cleanup-cron=-',
            '--server.servlet.session.cookie.secure=false',
            '--outbound.exports.renderer-home=' + str(RENDERER),
            '--outbound.signing.private-key=' + str(LAB / 'private/server-private.der'),
            '--outbound.signing.public-key=' + str(LAB / 'private/server-public.der')]
    args += (['--spring.profiles.active=worker', '--spring.main.web-application-type=none',
              '--outbound.worker.enabled=true', '--outbound.worker.poll-ms=500',
              '--outbound.worker.reminder-poll-ms=3600000'] if worker else
             ['--server.address=127.0.0.1', '--server.port=' + str(HTTP), '--outbound.worker.enabled=false'])
    with (LAB / (label + '.stdout')).open('xb') as out, (LAB / (label + '.stderr')).open('xb') as err:
        process = subprocess.Popen(args, cwd=LAB, env=env, stdout=out, stderr=err,
                                   creationflags=subprocess.CREATE_NO_WINDOW)
    sql.save(label + '-command.json', dict(args=args, pid=process.pid, scheduledWorker=worker))
    return process


def ready(process, label):
    for _ in range(180):
        assert process.poll() is None, 'API exited before readiness'
        if not runtime.port_free(HTTP):
            command = f'Get-NetTCPConnection -State Listen -LocalPort {HTTP} | Select-Object LocalPort,OwningProcess | ConvertTo-Json'
            result = subprocess.check_output(['powershell', '-NoProfile', '-Command', command])
            row = json.loads(result)
            assert row == dict(LocalPort=HTTP, OwningProcess=process.pid), row
            sql.save(label + '-listener.json', row)
            return
        time.sleep(.4)
    raise AssertionError('API readiness timeout')


def completed(process, job):
    for _ in range(240):
        assert process.poll() is None, 'Old worker exited unexpectedly'
        row = sql.query("SELECT JSON_ARRAY(status,attempt_count,last_error) FROM background_job WHERE id=X'" + job.hex + "'")[0]
        if row[0] == 'SUCCEEDED':
            assert row == ['SUCCEEDED', 1, None], row
            return
        assert row[0] in ('PENDING', 'RUNNING'), row
        time.sleep(.5)
    raise AssertionError('Old worker completion timeout')


def stop(process, label):
    if process.poll() is None:
        process.terminate()
    code = process.wait(timeout=30)
    runtime.require_drained(HTTP)
    command = "@(Get-CimInstance Win32_Process | Where-Object {$_.Name -eq 'java.exe' -and $_.CommandLine -like '*" + LAB.name + "*'} | Select-Object ProcessId,ParentProcessId,CommandLine) | ConvertTo-Json"
    raw = subprocess.check_output(['powershell', '-NoProfile', '-Command', command])
    children = json.loads(raw) if raw.strip() else []
    sql.save(label + '-remaining-java.json', children)
    assert children == [], 'Dedicated worker/renderer Java process remains'
    sql.save(label + '-stopped.json', dict(pid=process.pid, exitCode=code, httpListener=False,
        method='Windows TerminateProcess on this Popen handle; not graceful/Linux shutdown'))
