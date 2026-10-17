"""Run one guarded nonempty upgrade/program rollback/checkpoint restore candidate."""
import argparse
import json
import shutil
import subprocess
import xml.etree.ElementTree as ET

from d15_combination_runtime import ROOT, command, digest, finish_mysql, identity, launch_mysql
from d15_combination_runtime import port_free, prepare, save, wait_mysql


def inputs(attempt):
    paths = subprocess.check_output(['git', 'ls-files', '-c', '-o', '--exclude-standard', '-z'], cwd=ROOT).decode().split('\0')
    manifest = [dict(path=name, bytes=(ROOT / name).stat().st_size, sha256=digest(ROOT / name))
                for name in sorted(set(filter(None, paths))) if (ROOT / name).is_file()]
    save(attempt / 'inputs.json', manifest)
    (attempt / 'tracked.patch').write_bytes(subprocess.check_output(['git', 'diff', '--binary', 'HEAD'], cwd=ROOT))
    (attempt / 'status.txt').write_bytes(subprocess.check_output(['git', 'status', '--short', '-uall'], cwd=ROOT))
    save(attempt / 'git.json', dict(head=subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT).decode().strip(),
         branch=subprocess.check_output(['git', 'branch', '--show-current'], cwd=ROOT).decode().strip()))


def test(attempt, env):
    args = [str(ROOT / 'gradlew.bat'), '-p', 'server', 'integrationTest',
            '--tests', '*E2aUpgradeRollbackCombinationIT', '-x', ':report-rendering:prepareFont',
            '--offline', '--no-daemon', '--console=plain']
    code = command(args, attempt, 'gradle', env, timeout=900)
    source = ROOT / 'server/build/test-results/integrationTest/TEST-com.acrylic.outbound.e2averify.E2aUpgradeRollbackCombinationIT.xml'
    assert source.exists() and source.stat().st_mtime >= (attempt / 'test-started.marker').stat().st_mtime
    target = attempt / source.name
    shutil.copy2(source, target)
    suite = ET.parse(target).getroot()
    result = dict(exitCode=code, **suite.attrib, completed=(attempt / 'test-completed.marker').exists())
    save(attempt / 'test-result.json', result)
    assert code == 0 and result['tests'] == '1' and result['failures'] == '0'
    assert result['errors'] == '0' and result['skipped'] == '0' and result['completed']


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
        test(attempt, env)
    finally:
        finish_mysql(process, attempt, args.run, args.port, env)
        assert port_free(args.http), 'Dedicated HTTP listener remains'
    print(json.dumps(dict(attempt=str(attempt), status='PASS', mysqlStopped=True)))


if __name__ == '__main__':
    main()
