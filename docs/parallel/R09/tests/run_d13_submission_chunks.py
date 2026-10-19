"""Run one fresh real-TCP submission chunk candidate on an isolated MySQL instance."""
import argparse
import json
from pathlib import Path

from d15_combination_runtime import ROOT, command, finish_mysql, identity, launch_mysql
from d15_combination_runtime import port_free, prepare, save, wait_mysql


def inputs(attempt):
    import hashlib
    import subprocess
    paths = subprocess.check_output(['git', 'ls-files', '-c', '-o', '--exclude-standard', '-z'], cwd=ROOT).decode().split('\0')
    rows = []
    for name in sorted(set(filter(None, paths))):
        path = ROOT / name
        if path.is_file():
            rows.append(dict(path=name, bytes=path.stat().st_size,
                             sha256=hashlib.file_digest(path.open('rb'), 'sha256').hexdigest()))
    save(attempt / 'inputs.json', rows)
    (attempt / 'tracked.patch').write_bytes(subprocess.check_output(['git', 'diff', '--binary', 'HEAD'], cwd=ROOT))
    (attempt / 'status.txt').write_bytes(subprocess.check_output(['git', 'status', '--short', '-uall'], cwd=ROOT))
    save(attempt / 'git.json', dict(head=subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT).decode().strip(),
                                    branch=subprocess.check_output(['git', 'branch', '--show-current'], cwd=ROOT).decode().strip()))


def test(attempt, env):
    args = [str(ROOT / 'gradlew.bat'), '-p', 'server', 'recoveryControlClasspath', 'integrationTest',
            '--tests', '*E2aSubmissionChunksIT', '-x', ':report-rendering:prepareFont',
            '--offline', '--no-daemon', '--console=plain']
    code = command(args, attempt, 'gradle', env, timeout=1200)
    result = dict(exitCode=code, completed=(attempt / 'test-completed.marker').exists())
    save(attempt / 'test-result.json', result)
    assert code == 0 and result['completed']


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--run', required=True, type=int)
    parser.add_argument('--port', required=True, type=int)
    parser.add_argument('--http', required=True, type=int)
    args = parser.parse_args()
    attempt, env = prepare(args.run, args.port, args.http)
    with (attempt / 'private/bootstrap.sql').open('a', encoding='utf-8') as sql:
        sql.write(f"GRANT SELECT ON performance_schema.* TO 'e2a_cross_20260925_{args.run}'@'127.0.0.1';\n")
    inputs(attempt)
    process = launch_mysql(attempt, args.port, env)
    try:
        wait_mysql(process, args.port)
        identity(attempt, args.run, args.port, env, 'identity-before')
        test(attempt, env)
    finally:
        finish_mysql(process, attempt, args.run, args.port, env)
        assert port_free(args.http)
    print(json.dumps(dict(attempt=str(attempt), status='PASS', mysqlStopped=True)))


if __name__ == '__main__':
    main()
