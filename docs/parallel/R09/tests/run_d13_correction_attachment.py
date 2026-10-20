"""Fresh isolated MySQL input for the admitted correction-attachment HTTP lifecycle."""
import argparse
import json
import shutil
import xml.etree.ElementTree as ET

from d15_combination_runtime import ROOT, command, finish_mysql, identity, launch_mysql
from d15_combination_runtime import port_free, prepare, save, wait_mysql
from run_d15_upgrade_rollback import inputs


def verify(attempt, env):
    name = 'E2aAdmittedCorrectionAttachmentIT'
    args = [str(ROOT / 'gradlew.bat'), '-p', 'server', 'integrationTest',
            '--tests', '*' + name, '-x', ':report-rendering:prepareFont',
            '--offline', '--no-daemon', '--console=plain']
    code = command(args, attempt, 'gradle', env, timeout=900)
    source = ROOT / f'server/build/test-results/integrationTest/TEST-com.acrylic.outbound.e2averify.{name}.xml'
    started = attempt / 'test-started.marker'
    result = dict(exitCode=code, started=started.exists(), completed=(attempt / 'test-completed.marker').exists())
    if source.exists() and started.exists() and source.stat().st_mtime >= started.stat().st_mtime:
        shutil.copy2(source, attempt / source.name)
        result.update(ET.parse(source).getroot().attrib)
    save(attempt / 'test-result.json', result)
    assert code == 0 and result.get('tests') == '1' and result.get('failures') == '0', result
    assert result.get('errors') == '0' and result.get('skipped') == '0' and result['completed'], result


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
