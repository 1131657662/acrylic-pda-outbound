"""MySQL CLI calls with original byte evidence and explicit child stdin."""
import hashlib
import json
import subprocess
import time
from pathlib import Path


class SqlEvidence:
    def __init__(self, options):
        self.directory = Path(options['directory'])
        self.directory.mkdir()
        self.number = 0
        self.args = [str(options['mysql']), '--defaults-file=' + str(options['config']),
                     '--binary-mode', '--raw', '-N', '-B', '--default-character-set=utf8mb4',
                     '--max-allowed-packet=1G', '--skip-reconnect', '--connect-timeout=10',
                     '--database=' + options['database']]

    def query(self, statement, table='__database__'):
        self.number += 1
        prefix = self.directory / f'{self.number:04d}'
        args = [*self.args, '--execute=' + statement]
        prefix.with_suffix('.request.json').write_text(json.dumps(
            {'table': table, 'args': args, 'stdin': 'DEVNULL'}, indent=2), encoding='utf-8')
        started = time.monotonic()
        try:
            result = subprocess.run(args, stdin=subprocess.DEVNULL, capture_output=True, timeout=60)
        except (subprocess.TimeoutExpired, OSError) as error:
            self.retain(prefix, {'exitCode': None, 'seconds': time.monotonic() - started,
                                'errorType': type(error).__name__, 'error': str(error)},
                        (getattr(error, 'stdout', None) or b'', getattr(error, 'stderr', None) or b''))
            raise RuntimeError(f'SQL execution failed: table={table}, evidence={prefix}') from error
        self.retain(prefix, {'exitCode': result.returncode, 'seconds': time.monotonic() - started},
                    (result.stdout, result.stderr))
        if result.returncode:
            raise RuntimeError(f'SQL failed: table={table}, exit={result.returncode}, evidence={prefix}')
        return result.stdout

    def retain(self, prefix, outcome, streams):
        stdout, stderr = streams
        prefix.with_suffix('.stdout').write_bytes(stdout)
        prefix.with_suffix('.stderr').write_bytes(stderr)
        prefix.with_suffix('.result.json').write_text(json.dumps({
            **outcome, 'stdoutBytes': len(stdout), 'stderrBytes': len(stderr),
            'stdoutSha256': hashlib.sha256(stdout).hexdigest(),
            'stderrSha256': hashlib.sha256(stderr).hexdigest()}, indent=2), encoding='utf-8')
