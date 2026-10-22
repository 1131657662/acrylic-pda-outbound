"""One fresh isolated MySQL and one P5 lost-acknowledgement integration test."""
import argparse
import json
import os
from pathlib import Path
import secrets
import subprocess
import xml.etree.ElementTree as ET

from d15_combination_runtime import ROOT, MYSQL, command, digest, launch_mysql, port_free, save, wait_mysql


def inputs():
    names = subprocess.check_output(['git', 'ls-files', '--cached', '--others', '--exclude-standard', '-z',
                                     'server', 'report-rendering', 'docs/parallel/R09/tests', 'tools'], cwd=ROOT)
    return {name.decode(): digest(ROOT / name.decode()) for name in sorted(set(names.split(b'\0')))
            if name and (ROOT / name.decode()).is_file()}


def prepare(output, run, port, build):
    assert 19400 <= port <= 19999 and 1 <= run <= 9999
    instance = ROOT / f'.local/mysql{port}-r09batch'
    assert not instance.exists() and not output.exists() and not build.exists() and port_free(port)
    output.mkdir(parents=True)
    instance.mkdir()
    build.mkdir()
    for name in ('private', 'tmp'):
        (instance / name).mkdir()
    sid = subprocess.check_output(['whoami', '/user', '/fo', 'csv', '/nh']).decode().split(',')[-1].strip().strip('"')
    subprocess.run(['icacls', str(instance / 'private'), '/inheritance:r', '/grant:r',
                    '*' + sid + ':(OI)(CI)F', '*S-1-5-18:(OI)(CI)F'], check=True, capture_output=True)
    env = os.environ.copy()
    env.update(JAVA_HOME='C:/Program Files/Java/jdk-21.0.11', TEMP=str(instance / 'tmp'), TMP=str(instance / 'tmp'),
               JAVA_TOOL_OPTIONS='-Djava.io.tmpdir=' + str(instance / 'tmp'),
               MYSQL_TEST_LOGIN_FILE=str(instance / 'private/disabled-login-file'),
               P5_BUILD=str(build), P5_OUTPUT=str(output),
               P5_ROOT=str(ROOT),
               P5_LOST_COMMIT_CLASSPATH=str(build / 's/recovery-control-classpath.json'),
               B11_EFFECT_EVIDENCE_DIR=str(output), B12_FENCE_EVIDENCE_DIR=str(output))
    env.pop('MYSQL_PWD', None)
    schema = f'p5_lost_{run}'
    password = secrets.token_hex(32)
    credentials(instance, schema, port, password)
    env.update(OUTBOUND_DB_URL=f'jdbc:mysql://127.0.0.1:{port}/{schema}?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC',
               OUTBOUND_DB_USER=schema, OUTBOUND_DB_PASSWORD=password)
    save(output / 'allocation.json', dict(run=run, port=port, schema=schema, instance=str(instance), build=str(build)))
    return instance, env


def credentials(instance, schema, port, password):
    config = f'[client]\nhost=127.0.0.1\nport={port}\nuser={schema}\npassword={password}\nprotocol=tcp\n'
    (instance / 'private/client.cnf').write_text(config, encoding='utf-8')
    sql = "ALTER USER 'root'@'localhost' IDENTIFIED BY '" + secrets.token_hex(32) + "';\n"
    sql += f"CREATE DATABASE {schema};\nCREATE USER '{schema}'@'127.0.0.1' IDENTIFIED BY '{password}';\n"
    sql += f"GRANT ALL ON {schema}.* TO '{schema}'@'127.0.0.1';\nGRANT SHUTDOWN ON *.* TO '{schema}'@'127.0.0.1';\n"
    (instance / 'private/bootstrap.sql').write_text(sql, encoding='utf-8')


def build_config(output):
    init = output / 'build.init.gradle'
    init.write_text('''gradle.beforeProject { p ->
    p.layout.buildDirectory.set(new File(System.getenv('P5_BUILD'),
        p.rootProject.name == 'acrylic-outbound-server' ? 's' : 'r'))
}
gradle.projectsEvaluated {
    if (rootProject.name == 'acrylic-outbound-server') {
        rootProject.tasks.named('integrationTest', Test).configure {
            workingDir file(System.getenv('P5_ROOT'))
            dependsOn rootProject.tasks.named('recoveryControlClasspath')
            maxParallelForks = 1
            maxHeapSize = '768m'
            reports.junitXml.outputLocation.set(file(System.getenv('P5_OUTPUT') + '/xml'))
            reports.html.outputLocation.set(file(System.getenv('P5_OUTPUT') + '/html'))
            binaryResultsDirectory.set(file(System.getenv('P5_OUTPUT') + '/binary'))
        }
    }
}
''', encoding='utf-8')
    return init


def sql(instance, output, env, label, text):
    args = [str(MYSQL / 'mysql.exe'), '--defaults-file=' + str(instance / 'private/client.cnf'),
            '--batch', '--skip-column-names', env['OUTBOUND_DB_USER'], '-e', text]
    assert command(args, output, label, env) == 0
    return (output / (label + '.stdout')).read_text('utf-8').strip()


def identity(instance, output, env, label, port):
    cells = sql(instance, output, env, label,
                'SELECT @@port,@@datadir,DATABASE(),CURRENT_USER(),@@log_bin,@@server_uuid;').split('\t')
    assert cells[0] == str(port) and cells[2] == env['OUTBOUND_DB_USER']
    assert cells[3] == env['OUTBOUND_DB_USER'] + '@127.0.0.1' and cells[4] == '1'
    assert Path(cells[1].replace('\\\\', '/')).resolve() == (instance / 'data').resolve()


def shutdown(process, instance, output, env, port):
    identity(instance, output, env, 'identity-final', port)
    stopped = command([str(MYSQL / 'mysqladmin.exe'), '--defaults-file=' + str(instance / 'private/client.cnf'),
                       'shutdown'], output, 'shutdown', env, timeout=60)
    assert stopped == 0
    code = process.wait(timeout=60)
    assert code == 0 and port_free(port)
    save(output / 'mysql-stopped.json', dict(pid=process.pid, exitCode=code, port=port, listener=False))


def database_evidence(instance, output, env):
    summary = output / 'lost-commit-summary.tsv'
    if summary.exists():
        table = summary.read_text('utf-8').splitlines()[1].split('\t')[0]
        assert len(table) == 34 and table.startswith('f_') and all(c in '0123456789abcdef' for c in table[2:])
        assert sql(instance, output, env, 'sql-rows', f'SELECT id,val FROM {table} ORDER BY id;') == '1\tvalue\n2\tvalue'
    args = [str(MYSQL / 'mysqldump.exe'), '--defaults-file=' + str(instance / 'private/client.cnf'),
            '--single-transaction', '--skip-lock-tables', '--no-tablespaces', '--set-gtid-purged=OFF', env['OUTBOUND_DB_USER']]
    assert command(args, instance / 'private', 'final-dump', env) == 0


def runtime(instance, output, base, env, port):
    process = launch_mysql(instance, port, env)
    result = dict(exitCode=-1)
    try:
        wait_mysql(process, port)
        identity(instance, output, env, 'identity-before', port)
        code = command(base + ['integrationTest', '--tests', '*RecoveryFenceLostCommitIT'],
                       output, 'integration', env, timeout=600)
        result['exitCode'] = code
        xml = output / 'xml/TEST-com.acrylic.outbound.recovery.RecoveryFenceLostCommitIT.xml'
        if xml.exists():
            result.update(ET.parse(xml).getroot().attrib)
        save(output / 'test-result.json', result)
        database_evidence(instance, output, env)
    finally:
        shutdown(process, instance, output, env, port)
    bins = sorted(instance.glob('binlog.[0-9]*'))
    assert bins
    assert command([str(MYSQL / 'mysqlbinlog.exe'), '--no-defaults', '--base64-output=DECODE-ROWS', '-vv']
                   + [str(p) for p in bins], output, 'binlog', env) == 0
    return result


def execute(args):
    output, build = args.output.resolve(), args.build.resolve()
    assert output.is_relative_to(ROOT / '.local') and build.is_relative_to(ROOT / '.local')
    instance, env = prepare(output, args.run, args.port, build)
    before = inputs()
    save(output / 'source-before.json', before)
    init = build_config(output)
    font = ROOT / '.local/s22b/r/generated/resources/NotoSansCJKsc-Regular.otf'
    assert digest(font) == '2c76254f6fc379fddfce0a7e84fb5385bb135d3e399294f6eeb6680d0365b74b'
    base = [str(ROOT / 'gradlew.bat'), '-p', 'server', '--init-script', str(init), '--project-cache-dir',
            str(build / 'project-cache'), '-Pr08FontFile=' + str(font), '--offline', '--no-daemon', '--console=plain']
    assert command(base + ['testClasses', 'recoveryControlClasspath'], output, 'compile', env, timeout=600) == 0
    jars = json.loads((build / 's/recovery-control-classpath.json').read_text('utf-8'))
    dependencies = {p: digest(Path(p)) for p in jars if Path(p).is_file()}
    save(output / 'dependencies-before.json', dependencies)
    result = runtime(instance, output, base, env, args.port)
    save(output / 'source-after.json', inputs())
    assert before == inputs() and dependencies == {p: digest(Path(p)) for p in dependencies}
    assert result['exitCode'] == 0 and [result.get(k) for k in ('tests', 'failures', 'errors', 'skipped')] == ['1', '0', '0', '0']
    print(json.dumps(dict(status='PASS', output=str(output))))


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    for field in ('run', 'port'):
        parser.add_argument('--' + field, type=int, required=True)
    for field in ('output', 'build'):
        parser.add_argument('--' + field, type=Path, required=True)
    execute(parser.parse_args())
