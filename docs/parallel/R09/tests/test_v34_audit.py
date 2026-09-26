"""V34 retains the TEMP audit columns and original source checks."""
import unittest
from audit_archives import events
from audit_origins import provenance
from safety import OpsError


class AuditDatabase:
    def __init__(self, fail_temporary=False):
        self.queries = []
        self.fail_temporary = fail_temporary

    def query(self, sql):
        self.queries.append(sql)
        if 'MAX(CAST(version' in sql:
            return '34'
        if 'FROM pda_audit_event ORDER BY' in sql:
            return ''
        if self.fail_temporary and 'LEFT JOIN temporary_authorization' in sql:
            return '1'
        return '0'


class V34AuditTests(unittest.TestCase):
    def test_event_query_retains_temporary_original_columns(self):
        db = AuditDatabase()
        self.assertEqual(events(db), {})
        self.assertTrue(any('BIN_TO_UUID(temporary_authorization_id)' in query
                            for query in db.queries))

    def test_temporary_provenance_drift_still_blocks_v34(self):
        with self.assertRaisesRegex(OpsError, 'AUDIT_TEMP_PROVENANCE_MISMATCH'):
            provenance(AuditDatabase(fail_temporary=True), '34')
