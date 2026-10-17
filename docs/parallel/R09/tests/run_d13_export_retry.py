"""Fresh default-off/P5 HTTP retry input with isolated native concurrency and SQL faults."""
import argparse
import json
import shutil

from d15_combination_runtime import ROOT, command, finish_mysql, identity, launch_mysql
from d15_combination_runtime import port_free, prepare, save, wait_mysql
from run_d13_renderer_handoff import result
from run_d15_upgrade_rollback import inputs


def verify(attempt, env):
    unit = ['RecoveryEffectSqlTest', 'RecoverySelectSqlTest', 'ExportResultSelectionTest',
            'ExportVersionCompatibilityTest', 'ExportRecoveryViewTest']
    args = [str(ROOT / 'gradlew.bat'), '-p', 'server', 'recoveryControlClasspath', 'test']
    for name in unit:
        args += ['--tests', '*' + name]
    args += ['integrationTest', '--tests', '*E2aAdmittedRetryIT',
             '-x', ':report-rendering:prepareFont', '--offline', '--no-daemon', '--console=plain']
    marker = attempt / 'run-started.marker'
    marker.write_text('new isolated retry candidate', encoding='utf-8')
    code = command(args, attempt, 'gradle', env, timeout=900)
    rows, missing = [], []
    for task, names in [('test', unit), ('integrationTest', ['E2aAdmittedRetryIT'])]:
        for name in names:
            try:
                rows.append(result(attempt, task, name, marker))
            except AssertionError:
                missing.append(dict(task=task, name=name))
                binary = ROOT / 'server/build/test-results' / task / 'binary'
                if binary.is_dir():
                    shutil.copytree(binary, attempt / (task + '-binary'), dirs_exist_ok=True)
    save(attempt / 'test-result.json', dict(exitCode=code, suites=rows, missing=missing))
    assert not missing, ('Missing fresh XML; original exitCode and binary retained', code, missing)
    assert code == 0 and all(int(row['tests']) > 0 and
        row['failures'] == row['errors'] == row['skipped'] == '0' for row in rows)
    assert (attempt / 'test-completed.marker').exists()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--run', required=True, type=int)
    parser.add_argument('--port', required=True, type=int)
    parser.add_argument('--http', required=True, type=int)
    args = parser.parse_args()
    attempt, env = prepare(args.run, args.port, args.http)
    # Disposable fixture observes the actual lock wait; no production privilege changes.
    with (attempt / 'private/bootstrap.sql').open('a', encoding='utf-8') as sql:
        sql.write(f"GRANT SELECT ON performance_schema.* TO 'e2a_cross_20260925_{args.run}'@'127.0.0.1';\n")
    inputs(attempt)
    process = launch_mysql(attempt, args.port, env)
    try:
        wait_mysql(process, args.port)
        identity(attempt, args.run, args.port, env, 'identity-before')
        verify(attempt, env)
    finally:
        finish_mysql(process, attempt, args.run, args.port, env)
        assert port_free(args.http), 'Dedicated HTTP listener remains'
    print(json.dumps(dict(attempt=str(attempt), status='PASS', mysqlStopped=True)))


if __name__ == '__main__':
    main()
