"""Combine a distinct synthetic target with pinned product-restored FORMAL facts and audits."""
from contextlib import closing
import os
from pathlib import Path

from ..codec import canonical, digest, parse, require
from ..store import connect, facts, healthy
from .factory import insert, mutate
from .test_product_triggers import NativeTriggerCase

FORMAL = ('work_device', 'prepared_order', 'work_order', 'prepared_bale', 'work_line',
          'work_capture', 'work_scan', 'work_guard', 'work_event', 'local_submission',
          'local_submission_archive')


class RestoredFormalCase(NativeTriggerCase):
    def setUp(self):
        super().setUp()
        source = Path(os.environ['PDA_RETENTION_FORMAL_SOURCE'])
        require(digest(source.read_bytes()) == os.environ['PDA_RETENTION_FORMAL_SOURCE_SHA256'],
                'Restored FORMAL source pin mismatch')
        with closing(connect(source)) as original:
            healthy(original)
            source_facts = facts(original)
            self.formal = {table: [parse(row) for row in source_facts[table]] for table in FORMAL}
        require(len(self.formal['local_submission']) == 1 and len(self.formal['work_guard']) == 2,
                'Expected intact restored FORMAL batch with both effective guards')
        with closing(connect(self.source, True)) as db:
            for table in FORMAL:
                for row in self.formal[table]:
                    insert(db, table, row)
            healthy(db)
        mutate(self.spec, 'SELECT 1')
        self.spec_data = parse(self.spec.read_bytes())
        (self.directory / 'retained-formal-before.json').write_bytes(canonical(self.formal))
        (self.directory / 'retained-formal-source.json').write_bytes(canonical(dict(
            path=str(source), sha256=digest(source.read_bytes()), syntheticCombinedInput=True)))

    def assert_formal_preserved(self, database):
        with closing(connect(database)) as db:
            actual = facts(db)
        for table, rows in self.formal.items():
            for row in rows:
                self.assertIn(canonical(row).decode(), actual[table], (table, row))
