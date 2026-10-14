"""Keep JVM evidence separate from decoded JDBC results, then independently check every fact."""
import hashlib
import json
import subprocess
import time
from pathlib import Path

from tools.exact_database_facts import table_facts


class CapturedQueries:
    def __init__(self, directory):
        self.directory = directory
        self.number = 2  # JDBC verified identity precedes the table inventory.

    def query(self, statement, table='__database__'):
        prefix = self.directory / f'{self.number:04d}'
        self.number += 1
        if prefix.with_suffix('.sql').read_text() != statement:
            raise ValueError(f'JDBC query mismatch: table={table}, evidence={prefix}')
        if prefix.with_suffix('.table').read_text() != table:
            raise ValueError(f'JDBC table mismatch: table={table}, evidence={prefix}')
        if prefix.with_suffix('.status').read_text() != '0':
            raise ValueError(f'JDBC query failed: table={table}, evidence={prefix}')
        return prefix.with_suffix('.stdout').read_bytes()


def retain_queries(directory, options):
    for path in directory.glob('*.sql'):
        prefix = path.with_suffix('')
        streams = [prefix.with_suffix('.stdout').read_bytes(), prefix.with_suffix('.stderr').read_bytes()]
        query = {'backend': 'JDBC', 'table': prefix.with_suffix('.table').read_text(),
                 'config': str(options['config']), 'database': options['database'], 'query': path.read_text(),
                 'sqlErrorCode': int(prefix.with_suffix('.status').read_text()),
                 'seconds': float(prefix.with_suffix('.seconds').read_text()),
                 'stdoutBytes': len(streams[0]), 'stderrBytes': len(streams[1]),
                 'stdoutSha256': hashlib.sha256(streams[0]).hexdigest(),
                 'stderrSha256': hashlib.sha256(streams[1]).hexdigest(),
                 'resultFormat': 'JDBC getBytes TSV; separate JVM stdout/stderr/exit in process.*'}
        prefix.with_suffix('.result.json').write_text(json.dumps(query, indent=2), encoding='utf-8')


def execute(options, directory):
    source = Path(__file__).with_name('JdbcDatabaseCapture.java').read_bytes()
    (directory / 'process-source.java').write_bytes(source)
    args = [str(options['java']), '-Djava.io.tmpdir=' + str(options['temporary']),
            '--class-path', str(options['driver']), str(Path(__file__).with_name('JdbcDatabaseCapture.java')),
            str(options['config']), options['database'], str(directory), str(options['port']), str(options['datadir'])]
    (directory / 'process.request.json').write_text(json.dumps({'args': args, 'stdin': 'DEVNULL'}, indent=2))
    started = time.monotonic()
    try:
        process = subprocess.run(args, stdin=subprocess.DEVNULL, capture_output=True, timeout=120)
    except (subprocess.TimeoutExpired, OSError) as error:
        (directory / 'process.stdout').write_bytes(getattr(error, 'stdout', None) or b'')
        (directory / 'process.stderr').write_bytes(getattr(error, 'stderr', None) or b'')
        (directory / 'process.result.json').write_text(json.dumps({'exitCode': None,
            'errorType': type(error).__name__, 'seconds': time.monotonic() - started}))
        raise RuntimeError(f'JDBC execution failed: evidence={directory}') from error
    (directory / 'process.stdout').write_bytes(process.stdout)
    (directory / 'process.stderr').write_bytes(process.stderr)
    (directory / 'process.result.json').write_text(json.dumps({'exitCode': process.returncode,
        'seconds': time.monotonic() - started, 'sourceSha256': hashlib.sha256(source).hexdigest(),
        'stdoutSha256': hashlib.sha256(process.stdout).hexdigest(),
        'stderrSha256': hashlib.sha256(process.stderr).hexdigest(),
        'driverSha256': hashlib.sha256(Path(options['driver']).read_bytes()).hexdigest()}))
    retain_queries(directory, options)
    if process.returncode:
        raise RuntimeError(f'JDBC exit={process.returncode}, evidence={directory}')


def capture(options):
    directory = Path(options['directory'])
    directory.mkdir()
    sql_directory = directory / 'sql'
    sql_directory.mkdir()
    execute(options, sql_directory)
    identity = (sql_directory / '0001.stdout').read_bytes().decode().strip().split('\t')
    if identity[:2] != [options['database'], str(options['port'])] or Path(identity[2]).resolve() != Path(options['datadir']).resolve():
        raise ValueError(f'Wrong isolated JDBC identity: evidence={sql_directory}')
    reader = CapturedQueries(sql_directory)
    names = reader.query('SELECT table_name FROM information_schema.tables '
                         'WHERE table_schema=DATABASE() ORDER BY table_name').decode('ascii').splitlines()
    if not names or len(set(names)) != len(names):
        raise ValueError(f'Missing/duplicate JDBC inventory: evidence={sql_directory}')
    facts = {name: table_facts(reader, name, directory) for name in names}
    (directory / 'facts.json').write_text(json.dumps(facts, indent=2), encoding='utf-8')
    return facts
