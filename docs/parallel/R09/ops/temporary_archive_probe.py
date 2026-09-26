"""One-shot R09 archive consumer in the guarded E2a test instance, not a general database connector."""
import configparser
import json
from pathlib import Path
import sys
import pymysql
from cryptography.hazmat.primitives import serialization
from safety import regular, require, digest, OpsError
from submission_archives import verify_archives

ROOT = Path('D:/acrylic-pda-worktrees/r06-e2a-migration/.local')


class ArchiveProbeDatabase:
    def __init__(self, attempt, side):
        import re
        self.attempt = Path(attempt).resolve()
        require(self.attempt.parent == ROOT.resolve() and re.fullmatch('e2a-cross-20260925-[1-9][0-9]?', self.attempt.name), 'ARCHIVE_PROBE_PATH')
        require(side in ('source', 'restore'), 'ARCHIVE_PROBE_SIDE')
        run = self.attempt.name.rsplit('-', 1)[1]
        self.name = 'acrylic_e2a_cross_20260925_' + run + ('_restore' if side == 'restore' else '')
        self.user = 'e2a_cross_20260925_' + run
        config = configparser.ConfigParser(interpolation=None)
        config.read(regular(self.attempt / 'private/client.cnf'), encoding='utf-8')
        require(config['client']['user'] == self.user and config['client']['host'] == '127.0.0.1' and config['client']['port'] == '19330', 'ARCHIVE_PROBE_CONFIG')
        self.connection = pymysql.connect(host='127.0.0.1', port=19330, user=self.user, password=config['client']['password'], database=self.name,
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
                row[1:] == ['19330', self.name, self.user + '@127.0.0.1', '1'], 'ARCHIVE_PROBE_IDENTITY')
        require(self.query('SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history WHERE success=1;') == '36', 'ARCHIVE_PROBE_SCHEMA')

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
