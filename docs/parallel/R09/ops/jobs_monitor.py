"""Aggregate-only V24+ task health; lab identity checks are mandatory."""
from safety import require

QUERY = '''SELECT COUNT(*),
    COALESCE(SUM(status='FAILED'),0),
    COALESCE(SUM(status='RUNNING' AND lease_until<UTC_TIMESTAMP(3)),0),
    COALESCE(SUM(status IN ('PENDING','RETRY') AND available_at<=UTC_TIMESTAMP(3)),0),
    COALESCE(MAX(CASE WHEN status IN ('PENDING','RETRY') THEN
      GREATEST(TIMESTAMPDIFF(SECOND,available_at,UTC_TIMESTAMP(3)),0) ELSE 0 END),0),
    COALESCE(SUM(status='RETRY'),0) FROM background_job;'''
FIELDS = ('total', 'failed', 'expired_leases', 'ready', 'oldest_ready_seconds', 'retry')
LIMITS = {'failed': 0, 'expired_leases': 0, 'ready': 100, 'oldest_ready_seconds': 300, 'retry': 20}


def evaluate(values, limits):
    require(set(limits) == set(LIMITS), 'JOB_LIMITS_INVALID')
    require(all(type(n) is int and n >= 0 for n in limits.values()), 'JOB_LIMITS_INVALID')
    failures = [key for key in limits if values[key] > limits[key]]
    return {'status': 'FAIL' if failures else 'PASS', 'counts': values, 'exceeded': failures}


def collect(db, limits=None):
    db.verify()
    require(db.query('SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history;') in ('28', '32'), 'UNSUPPORTED_SCHEMA')
    row = db.query(QUERY).split('\t')
    require(len(row) == len(FIELDS) and all(value.isdigit() for value in row), 'JOB_METRICS_INVALID')
    return evaluate(dict(zip(FIELDS, map(int, row))), LIMITS if limits is None else limits)
