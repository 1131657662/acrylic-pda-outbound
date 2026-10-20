"""Read preserved synthetic FORMAL originals in a fresh admitted/P5 copy only."""
import argparse
import json
import re
import shutil

from d15_combination_runtime import ROOT, MYSQL, command, digest, finish_mysql, identity
from d15_combination_runtime import launch_mysql, port_free, prepare, save, wait_mysql
from run_d15_upgrade_rollback import inputs
import xml.etree.ElementTree as ET

SOURCE = ROOT / '.local/e2a-cross-20260925-1462'
DUMP = ROOT / '.local/goal-20261013/returned-stock-preserve-1462-01/database.sql'
DUMP_SHA = '39c03b4b314f340a72bb84b2400d53d9fe2d5a1a26730bf66c5c4abf42b20d68'


def historical_input(attempt):
    assert digest(DUMP) == DUMP_SHA
    data = DUMP.read_bytes()
    assert not re.search(rb'(?im)^\s*(?:CREATE DATABASE|USE\s)', data)
    definers = set(re.findall(rb'DEFINER=`([^`]+)`@`([^`]+)`', data))
    assert definers == {(b'e2a_cross_20260925_1462', b'127.0.0.1')}, definers
    pairs = [(SOURCE / 'formal-quarantine-http/browser-input.json', attempt / 'private/historical-browser.json'),
             (SOURCE / 'formal-quarantine-http/restart-input.json', attempt / 'historical-restart-input.json'),
             (SOURCE / 'formal-quarantine-http/original-formal-proof.json', attempt / 'historical-original-formal-proof.json'),
             (SOURCE / 'private/server-public.der', attempt / 'private/historical-public.der')]
    pairs += [(p, attempt / 'storage' / p.relative_to(SOURCE / 'storage'))
              for p in (SOURCE / 'storage').rglob('*') if p.is_file()]
    manifest = [dict(path=str(DUMP), bytes=DUMP.stat().st_size, sha256=DUMP_SHA)]
    for source, target in pairs:
        assert not target.exists()
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, target)
        assert digest(source) == digest(target)
        manifest.append(dict(source=str(source), target=str(target), bytes=source.stat().st_size, sha256=digest(source)))
    save(attempt / 'historical-source.json', manifest)
    return data


def definer(attempt, run):
    user = f'e2a_cross_20260925_{run}'
    schema = f'acrylic_e2a_cross_20260925_{run}'
    with (attempt / 'private/bootstrap.sql').open('a', encoding='utf-8') as out:
        out.write("CREATE USER 'e2a_cross_20260925_1462'@'127.0.0.1' ACCOUNT LOCK;\n")
        out.write(f"GRANT ALL ON {schema}.* TO 'e2a_cross_20260925_1462'@'127.0.0.1';\n")
        out.write(f"GRANT SET_USER_ID ON *.* TO '{user}'@'127.0.0.1';\n")


def verify(attempt, env):
    name = 'E2aAdmittedFormalHistoryIT'
    args = [str(ROOT / 'gradlew.bat'), '-p', 'server', 'integrationTest', '--tests', '*' + name,
            '-x', ':report-rendering:prepareFont', '--offline', '--no-daemon', '--console=plain']
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
    for field in ('run', 'port', 'http'):
        parser.add_argument('--' + field, required=True, type=int)
    args = parser.parse_args()
    attempt, env = prepare(args.run, args.port, args.http)
    inputs(attempt)
    data = historical_input(attempt)
    definer(attempt, args.run)
    process = launch_mysql(attempt, args.port, env)
    try:
        wait_mysql(process, args.port)
        identity(attempt, args.run, args.port, env, 'identity-before')
        cmd = [str(MYSQL / 'mysql.exe'), '--defaults-file=' + str(attempt / 'private/client.cnf'),
               '--binary-mode', '--default-character-set=utf8mb4', f'acrylic_e2a_cross_20260925_{args.run}']
        assert command(cmd, attempt, 'historical-import', env, data=data) == 0
        verify(attempt, env)
    finally:
        finish_mysql(process, attempt, args.run, args.port, env)
        assert port_free(args.http), 'Dedicated HTTP listener remains'
    assert digest(DUMP) == DUMP_SHA
    print(json.dumps(dict(attempt=str(attempt), status='PASS', mysqlStopped=True)))


if __name__ == '__main__':
    main()
