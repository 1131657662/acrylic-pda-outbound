"""One fresh input for normal export and a lost native termination receipt with real halt."""
import argparse
import json
import shutil
import xml.etree.ElementTree as ET

from d15_combination_runtime import ROOT, command, finish_mysql, identity, launch_mysql
from d15_combination_runtime import port_free, prepare, save, wait_mysql
from run_d15_upgrade_rollback import inputs


def verify(attempt, env):
    unit = ['RecoveryProcessHandoffTest', 'ExportRendererProcessTest', 'RecoveryRequestAdmittedDataSourceTest']
    integration = ['E2aAdmittedWorkerExportIT', 'RecoveryRendererFenceIT']
    args = [str(ROOT / 'gradlew.bat'), '-p', 'server', 'recoveryControlClasspath', 'test']
    for name in unit:
        args += ['--tests', '*' + name]
    args += ['integrationTest']
    for name in integration:
        args += ['--tests', '*' + name]
    args += ['-x', ':report-rendering:prepareFont', '--offline', '--no-daemon', '--console=plain']
    marker = attempt / 'run-started.marker'
    marker.write_text('new disposable renderer handoff candidate', encoding='utf-8')
    code = command(args, attempt, 'gradle', env, timeout=900)
    rows = []
    for task, names in [('test', unit), ('integrationTest', integration)]:
        for name in names:
            rows.append(result(attempt, task, name, marker))
    save(attempt / 'test-result.json', dict(exitCode=code, suites=rows))
    assert code == 0 and all(int(row['tests']) > 0 and row['failures'] == row['errors'] == row['skipped'] == '0' for row in rows)
    assert (attempt / 'test-completed.marker').exists()


def result(attempt, task, name, marker):
    files = list((ROOT / 'server/build/test-results' / task).glob('TEST-*.' + name + '.xml'))
    assert len(files) == 1 and files[0].stat().st_mtime >= marker.stat().st_mtime, (task, name)
    shutil.copy2(files[0], attempt / files[0].name)
    return dict(task=task, **ET.parse(files[0]).getroot().attrib)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--run', required=True, type=int)
    parser.add_argument('--port', required=True, type=int)
    parser.add_argument('--http', required=True, type=int)
    args = parser.parse_args()
    attempt, env = prepare(args.run, args.port, args.http)
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
