"""Fresh normal P5 retirement, real Windows refusal, and product HTTP/worker regression."""
import argparse
import json

from d15_combination_runtime import command, finish_mysql, identity, launch_mysql
from d15_combination_runtime import port_free, prepare, save, wait_mysql, ROOT
from run_d13_renderer_handoff import result
from run_d15_upgrade_rollback import inputs


def verify(attempt, env, native=False):
    unit = ['RecoveryLeaseRetirementTest', 'RecoveryOperationRetirementTest',
            'RecoveryLeasePublicationTest', 'RecoveryWriterLeaseTest', 'RecoveryWriterReferenceConcurrencyTest',
            'RecoveryRequestAdmittedDataSourceTest', 'RecoveryProcessHandoffTest', 'ExportRendererProcessTest',
            'RecoveryEffectSqlTest', 'RecoverySelectSqlTest', 'CommandScalarRowsTest']
    integration = ['RecoveryRetirementNativeIT', 'RecoveryRendererFenceIT']
    if not native:
        integration.insert(0, 'E2aAdmittedWorkerExportIT')
    args = [str(ROOT / 'gradlew.bat'), '-p', 'server', 'recoveryControlClasspath', 'test']
    for name in unit:
        args += ['--tests', '*' + name]
    args += ['integrationTest']
    for name in integration:
        args += ['--tests', '*' + name]
    args += ['-x', ':report-rendering:prepareFont', '--offline', '--no-daemon', '--console=plain']
    marker = attempt / 'run-started.marker'
    marker.write_text('normal P5 retirement and actual OS refusal', encoding='utf-8')
    code = command(args, attempt, 'gradle', env, timeout=900)
    rows = [result(attempt, task, name, marker)
            for task, names in [('test', unit), ('integrationTest', integration)] for name in names]
    save(attempt / 'test-result.json', dict(exitCode=code, suites=rows, productHttp=not native))
    assert code == 0 and all(int(row['tests']) > 0 and
        row['failures'] == row['errors'] == row['skipped'] == '0' for row in rows)
    assert native or (attempt / 'test-completed.marker').exists()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--run', required=True, type=int)
    parser.add_argument('--port', required=True, type=int)
    parser.add_argument('--http', required=True, type=int)
    parser.add_argument('--native-only', action='store_true')
    args = parser.parse_args()
    attempt, env = prepare(args.run, args.port, args.http)
    inputs(attempt)
    if args.native_only:
        verify(attempt, env, native=True)
        print(json.dumps(dict(attempt=str(attempt), status='PASS', scope='native/unit only; HTTP remains unproven')))
        return
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
