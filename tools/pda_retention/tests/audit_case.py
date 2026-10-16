"""Explicit synthetic combination of an initial target and preserved audit facts."""
from contextlib import closing
import os
from pathlib import Path

from ..codec import canonical, digest, parse, require
from ..store import connect, facts, quote
from .factory import insert, mutate
from .support import RetentionCase

RETAINED = ('work_device', 'prepared_order', 'prepared_bale', 'work_order', 'work_line',
            'work_capture', 'work_scan', 'work_guard', 'work_event', 'local_submission',
            'local_submission_archive', 'local_audit_archive', 'local_audit_checkpoint')


class UnrelatedAuditCase(RetentionCase):
    def setUp(self):
        super().setUp()
        source = Path(os.environ['PDA_RETENTION_AUDIT_SOURCE'])
        require(digest(source.read_bytes()) == os.environ['PDA_RETENTION_AUDIT_SOURCE_SHA256'],
                'Audit test source pin mismatch')
        with closing(connect(source)) as original:
            self.retained = {t: [dict(r) for r in original.execute('SELECT * FROM ' + quote(t))]
                             for t in RETAINED}
        with closing(connect(self.source, True)) as db:
            for table, records in self.retained.items():
                for row in records:
                    insert(db, table, row)
        mutate(self.spec, 'SELECT 1')
        spec = parse(self.spec.read_bytes())
        spec.update(auditKeySha256=os.environ['PDA_RETENTION_AUDIT_KEY_SHA256'],
                    retainedSourceSha256=os.environ['PDA_RETENTION_AUDIT_SOURCE_SHA256'])
        self.spec.write_bytes(canonical(spec))
        self.spec_data = spec
        self.audit_device = self.retained['local_audit_archive'][0]['originDeviceId']
        self.audit_assignment = self.retained['work_order'][0]['assignmentId']

    def reject_change(self, sql, values=(), expected=None):
        mutate(self.spec, sql, values)
        before = self.source.read_bytes()
        result = self.cli(['prepare', self.spec], 'rejected-prepare')
        self.assertEqual(1, result.returncode, result.stdout)
        if expected:
            self.assertIn(expected.lower().encode(), result.stdout.lower())
        self.assertEqual(before, self.source.read_bytes())
        runs = list(self.directory.glob('retention-*'))
        self.assertEqual(1, len(runs))
        self.assertFalse((runs[0] / 'plan.json').exists())
        self.assertFalse((runs[0] / 'permit.json').exists())
        self.assertEqual(self.snapshot(self.source), self.snapshot(runs[0] / 'candidate.sqlite'))
