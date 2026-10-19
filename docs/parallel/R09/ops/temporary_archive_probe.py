"""One-shot R09 archive consumer in the guarded E2a test instance, not a general database connector."""
import configparser
import json
import os
from pathlib import Path
import re
import sys
import pymysql
from cryptography.hazmat.primitives import serialization
from safety import regular, require, digest, OpsError
from submission_archives import verify_archives

class ArchiveProbeDatabase:
    def __init__(self, attempt, side):
        root_value = os.environ.get('E2A_CROSS_ROOT')
        run = os.environ.get('E2A_CROSS_RUN')
        require(root_value is not None and run is not None, 'ARCHIVE_PROBE_CONFIG')
        self.root = Path(root_value).resolve()
        self.attempt = Path(attempt).resolve()
        require(self.root.is_dir() and not self.root.is_symlink() and not self.root.is_junction(), 'ARCHIVE_PROBE_ROOT')
        require(re.fullmatch('[1-9][0-9]{0,3}', run) and self.attempt.parent == (self.root / '.local').resolve()
                and self.attempt.name == 'e2a-cross-20260925-' + run, 'ARCHIVE_PROBE_PATH')
        require(side in ('source', 'restore'), 'ARCHIVE_PROBE_SIDE')
        self.name = 'acrylic_e2a_cross_20260925_' + run + ('_restore' if side == 'restore' else '')
        self.user = 'e2a_cross_20260925_' + run
        require(os.environ.get('E2A_CROSS_DATABASE') == self.name.replace('_restore', '')
                and os.environ.get('E2A_CROSS_USER') == self.user, 'ARCHIVE_PROBE_CONFIG')
        raw_port = os.environ.get('E2A_CROSS_PORT')
        require(raw_port is not None and re.fullmatch('[1-9][0-9]{3,4}', raw_port), 'ARCHIVE_PROBE_CONFIG')
        self.port = int(raw_port)
        self.schema_version = regular(self.attempt / 'archive-probe-schema.txt').read_text(encoding='utf-8').strip()
        require(re.fullmatch('[1-9][0-9]*', self.schema_version), 'ARCHIVE_PROBE_SCHEMA_MARKER')
        config = configparser.ConfigParser(interpolation=None)
        config.read(regular(self.attempt / 'private/client.cnf'), encoding='utf-8')
        require('client' in config and config['client']['user'] == self.user and config['client']['host'] == '127.0.0.1'
                and config['client']['port'] == raw_port, 'ARCHIVE_PROBE_CONFIG')
        self.connection = pymysql.connect(host='127.0.0.1', port=self.port, user=self.user, password=config['client']['password'], database=self.name,
            charset='utf8mb4', autocommit=True, connect_timeout=5, read_timeout=30, write_timeout=30, local_infile=False, init_command="SET time_zone='+00:00'")
        try:
            self.verify()
            self.files = self.attempt / ('storage' if side == 'source' else 'restored/storage')
            keyfile = self.attempt / ('private/server-public.der' if side == 'source' else 'restored/config/server-public.der')
            public = regular(keyfile).read_bytes()
            import base64
            self.keys = (serialization.load_der_public_key(public), 'offline-' + digest(base64.b64encode(public))[:32])
        except BaseException:
            self.connection.close()
            raise

    def verify(self):
        row = self.query('SELECT @@datadir,@@port,DATABASE(),CURRENT_USER(),@@log_bin;').split('\t')
        require(len(row) == 5 and Path(row[0]).resolve() == (self.attempt / 'data').resolve() and
                row[1:] == [str(self.port), self.name, self.user + '@127.0.0.1', '1'], 'ARCHIVE_PROBE_IDENTITY')
        require(self.query('SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history WHERE success=1;') == self.schema_version, 'ARCHIVE_PROBE_SCHEMA')

    def query(self, sql):
        with self.connection.cursor() as cursor:
            cursor.execute(sql)
            return '\n'.join('\t'.join('NULL' if value is None else value.decode('utf-8') if isinstance(value, bytes) else str(value)
                                      for value in row) for row in cursor.fetchall())

    def inspect(self):
        return verify_archives(self, self.files, self.keys)

    def close(self):
        self.connection.close()


def main():
    import argparse
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--attempt', required=True)
    parser.add_argument('--side', choices=('source', 'restore'), required=True)
    args = parser.parse_args()
    db = ArchiveProbeDatabase(args.attempt, args.side)
    try:
        result = db.inspect()
        require(result.get('temporary_archives_verified') == 2 and result.get('temporary_archive_commands_verified') == 3, 'ARCHIVE_PROBE_EXPECTED_COVERAGE')
        require(result['submission_archives_verified'] == 3 and result['submission_archive_file_references'] == 8, 'ARCHIVE_PROBE_MIXED_COVERAGE')
        print(json.dumps({'status': 'PASS', 'scope': 'TEMP V2 and FORMAL V1 archive snapshot only', **result}))
    finally:
        db.close()


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        print(str(error) if isinstance(error, OpsError) else 'ARCHIVE_PROBE_FAILED', file=sys.stderr)
        sys.exit(2)
