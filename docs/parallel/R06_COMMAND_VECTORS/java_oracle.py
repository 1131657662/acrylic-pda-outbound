"""Compile six actual production classes into a disposable offline oracle directory."""
import os
from pathlib import Path
import subprocess
import tempfile
from fixtures import ROOT

JAVA_SOURCES = (
    'common/BusinessException.java', 'common/JsonText.java', 'devices/DeviceCrypto.java',
    'inventory/ImportFiles.java', 'pda/OfflineSigner.java', 'submissions/SubmissionJson.java',
)
DEPENDENCIES = (
    ('com.fasterxml.jackson.core', 'jackson-core', '2.21.4'),
    ('com.fasterxml.jackson.core', 'jackson-databind', '2.21.4'),
    ('com.fasterxml.jackson.core', 'jackson-annotations', '2.21'),
    ('org.springframework', 'spring-context', '6.2.19'),
    ('org.springframework', 'spring-beans', '6.2.19'),
    ('org.springframework', 'spring-core', '6.2.19'),
)


def classpath(cache):
    jars = []
    for group, artifact, version in DEPENDENCIES:
        matches = list((cache / group / artifact / version).glob(f'*/{artifact}-{version}.jar'))
        if len(matches) != 1:
            raise ValueError(f'Explicit cached dependency required: {artifact}:{version}')
        jars.append(str(matches[0]))
    return os.pathsep.join(jars)


def invoke(mode, input_path, settings):
    repository = ROOT.parents[2]
    source_root = repository / 'server/src/main/java/com/acrylic/outbound'
    sources = [str(source_root / name) for name in JAVA_SOURCES]
    sources.append(str(ROOT / 'TemporaryCommandVectorOracle.java'))
    dependencies = classpath(settings['cache'])
    with tempfile.TemporaryDirectory(prefix='acrylic-c0-oracle-') as output:
        compiler = str(settings['jdk'] / 'bin/javac')
        subprocess.run([compiler, '-encoding', 'UTF-8', '-cp', dependencies, '-d', output, *sources],
                       check=True, capture_output=True, timeout=60)
        runtime = str(settings['jdk'] / 'bin/java')
        command = [runtime, '-cp', output + os.pathsep + dependencies,
                   'TemporaryCommandVectorOracle', mode, str(input_path)]
        result = subprocess.run(command, check=True, capture_output=True, timeout=60)
    return result.stdout.decode('utf-8')


def settings():
    return {'jdk': Path(os.environ['JAVA_HOME']),
            'cache': Path(os.environ.get('GRADLE_USER_HOME', str(Path.home() / '.gradle')))
            / 'caches/modules-2/files-2.1'}


if __name__ == '__main__':
    import argparse
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('mode', choices=('canonical', 'sign'))
    parser.add_argument('input', type=Path)
    parser.add_argument('output', type=Path)
    args = parser.parse_args()
    args.output.write_text(invoke(args.mode, args.input, settings()), encoding='utf-8')
