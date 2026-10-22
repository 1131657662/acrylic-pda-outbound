"""Fresh isolated MySQL plus distinct admitted API/nonweb scheduler JVMs."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET

from d15_combination_runtime import ROOT, command, digest, finish_mysql, identity
from d15_combination_runtime import launch_mysql, port_free, prepare, save, wait_mysql


def inputs(output):
    paths = subprocess.check_output(['git', 'ls-files', '--cached', '--others', '--exclude-standard', '-z', 'server', 'report-rendering',
                                     'docs/parallel/R09/tests'], cwd=ROOT).split(bytes([0]))
    names = {path.decode() for path in paths if path}
    names.update(path.relative_to(ROOT).as_posix() for path in
                 (ROOT / 'server/src/test/java').rglob('*SplitWorker*.java'))
    names.update('docs/parallel/R09/tests/' + name for name in
                 ('run_d13_split_worker.py', 'verify_d13_split_worker.py'))
    return {name: digest(ROOT / name) for name in sorted(names) if (ROOT / name).is_file()}


def build_config(output, build):
    assert not build.exists(), 'Never overwrite a prior build'
    build.mkdir(parents=True)
    source = '''gradle.beforeProject { p ->
    p.layout.buildDirectory.set(new File(System.getenv('SPLIT_BUILD'),
        p.rootProject.name == 'acrylic-outbound-server' ? 's' : 'r'))
}
gradle.projectsEvaluated {
    if (rootProject.name == 'acrylic-outbound-server') {
        rootProject.tasks.named('integrationTest', Test).configure {
            dependsOn rootProject.tasks.named('recoveryControlClasspath')
            maxParallelForks = 1
            maxHeapSize = '768m'
            reports.junitXml.outputLocation.set(file(System.getenv('SPLIT_OUTPUT') + '/xml'))
            reports.html.outputLocation.set(file(System.getenv('SPLIT_OUTPUT') + '/html'))
            binaryResultsDirectory.set(file(System.getenv('SPLIT_OUTPUT') + '/binary'))
        }
    }
}
'''
    init = output / 'build.init.gradle'
    init.write_text(source, encoding='utf-8')
    return init


def execute(args):
    output = args.output.resolve()
    assert output.is_relative_to(ROOT / '.local') and not output.exists()
    output.mkdir(parents=True)
    env = os.environ.copy()
    temp = output / 'tmp'
    temp.mkdir()
    env.update(SPLIT_BUILD=str(args.build.resolve()), SPLIT_OUTPUT=str(output),
               TEMP=str(temp), TMP=str(temp), JAVA_TOOL_OPTIONS='-Djava.io.tmpdir=' + str(temp),
               E2A_SPLIT_CLASSPATH=str(args.build.resolve() / 's/recovery-control-classpath.json'),
               E2A_SPLIT_RENDERER=str(args.build.resolve() / 'r/install/acrylic-report-rendering'),
               E2A_SPLIT_BROWSERS=str(args.browsers.resolve()))
    init = build_config(output, args.build.resolve())
    before = inputs(output)
    save(output / 'source-before.json', before)
    base = [str(ROOT / 'gradlew.bat'), '-p', 'server', '--init-script', str(init),
            '--project-cache-dir', str(args.build.resolve() / 'project-cache'),
            '-Pr08FontFile=' + str(args.font.resolve()), '--offline', '--no-daemon', '--console=plain']
    dependencies = external_inputs(args)
    save(output / 'external-before.json', dependencies)
    assert command(base + ['testClasses', 'recoveryControlClasspath', ':report-rendering:installDist'],
                   output, 'compile', env, timeout=900) == 0
    result = runtime(args, output, base, env)
    assert before == inputs(output), 'Source changed while executing'
    assert dependencies == external_inputs(args), 'External input changed while executing'
    assert result['exitCode'] == 0 and result['completed'], result
    assert [result.get(key) for key in ('tests', 'failures', 'errors', 'skipped')] == ['1', '0', '0', '0'], result
    print(json.dumps(dict(status='PASS', output=str(output))))


def external_inputs(args):
    assert digest(args.font) == '2c76254f6fc379fddfce0a7e84fb5385bb135d3e399294f6eeb6680d0365b74b'
    assert (args.browsers / 'chromium_headless_shell-1187/chrome-win/headless_shell.exe').is_file()
    paths = [args.font] + sorted(path for path in args.browsers.rglob('*') if path.is_file())
    return {str(path.resolve()): dict(bytes=path.stat().st_size, sha256=digest(path)) for path in paths}


def runtime(args, output, base, env):
    attempt, runtime = prepare(args.run, args.port, args.http)
    runtime.update({name: env[name] for name in ('SPLIT_BUILD', 'SPLIT_OUTPUT', 'E2A_SPLIT_CLASSPATH',
                                               'E2A_SPLIT_RENDERER', 'E2A_SPLIT_BROWSERS')})
    save(output / 'allocation.json', dict(run=args.run, port=args.port, http=args.http, attempt=str(attempt)))
    process = launch_mysql(attempt, args.port, runtime)
    try:
        wait_mysql(process, args.port)
        identity(attempt, args.run, args.port, runtime, 'identity-before')
        code = command(base + ['integrationTest', '--tests', '*' + args.test_class],
                       output, 'integration', runtime, timeout=900)
        xml = output / ('xml/TEST-com.acrylic.outbound.e2averify.' + args.test_class + '.xml')
        result = dict(exitCode=code, completed=(attempt / 'test-completed.marker').exists())
        if xml.exists():
            result.update(ET.parse(xml).getroot().attrib)
        save(output / 'test-result.json', result)
    finally:
        finish_mysql(process, attempt, args.run, args.port, runtime)
        save(output / 'source-after.json', inputs(output))
        assert port_free(args.http), 'Owned HTTP listener remains'
    return result


def main():
    parser = argparse.ArgumentParser()
    for name in ('run', 'port', 'http'):
        parser.add_argument('--' + name, type=int, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--build', type=Path, required=True)
    parser.add_argument('--font', type=Path, required=True)
    parser.add_argument('--browsers', type=Path, required=True)
    parser.add_argument('--test-class', choices=('E2aSplitWorkerExportIT', 'E2aRestoredSchedulerIT'),
                        default='E2aSplitWorkerExportIT')
    execute(parser.parse_args())


if __name__ == '__main__':
    main()
