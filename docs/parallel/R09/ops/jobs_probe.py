"""Aggregate job health after identity, privilege and structural schema checks."""
from jobs_connection import connect, COLUMNS
from jobs_monitor import FIELDS, LIMITS, QUERY, evaluate
from safety import require

SHAPE = {'background_job': {'status': ('varchar', 'NO'), 'available_at': ('datetime', 'NO'),
                            'lease_until': ('datetime', 'YES')},
         'flyway_schema_history': {'version': ('varchar', 'YES'), 'success': ('tinyint', 'NO')}}


def schema(cursor, database):
    for table, columns in COLUMNS.items():
        cursor.execute('SELECT TABLE_TYPE,ENGINE FROM information_schema.TABLES '
                       'WHERE TABLE_SCHEMA=%s AND TABLE_NAME=%s', (database, table))
        require(cursor.fetchone() == ('BASE TABLE', 'InnoDB'), 'JOB_SCHEMA_UNSUPPORTED')
        cursor.execute('SELECT COLUMN_NAME,DATA_TYPE,IS_NULLABLE FROM information_schema.COLUMNS '
                       'WHERE TABLE_SCHEMA=%s AND TABLE_NAME=%s', (database, table))
        actual = {row[0]: row[1:] for row in cursor.fetchall() if row[0] in columns}
        require(actual == SHAPE[table], 'JOB_SCHEMA_UNSUPPORTED')
    cursor.execute('SELECT COUNT(*),COALESCE(SUM(success=0),0) FROM flyway_schema_history '
                   'WHERE version IS NOT NULL')
    count, failed = cursor.fetchone()
    require(count > 0 and failed == 0, 'JOB_MIGRATION_HISTORY_UNHEALTHY')


def collect(config):
    limits = config.get('limits', LIMITS)
    # Reject invalid thresholds before any network or credential access.
    evaluate(dict.fromkeys(FIELDS, 0), limits)
    with connect(config) as cursor:
        schema(cursor, config['database'])
        cursor.execute(QUERY)
        row = cursor.fetchone()
        require(len(row) == len(FIELDS) and all(value is not None and value >= 0 and
                int(value) == value for value in row), 'JOB_METRICS_INVALID')
        return evaluate(dict(zip(FIELDS, map(int, row))), limits)
