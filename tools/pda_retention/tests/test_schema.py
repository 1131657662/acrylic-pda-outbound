"""Reject schema definitions hidden from the usual column/FK checks."""
from contextlib import closing
from pathlib import Path

from ..consumer import consume, prepare
from ..store import connect
from .factory import allow, mutate
from .support import RetentionCase


class SchemaTest(RetentionCase):
    def reject_mutation(self, sql):
        mutate(self.spec, sql)
        before = self.source.read_bytes()
        with self.assertRaisesRegex(ValueError, 'Schema changed|identity mismatch'):
            prepare(self.spec)
        self.assertEqual(before, self.source.read_bytes())

    def test_extra_table_constraint_rejects_even_with_room_identity(self):
        with closing(connect(self.source, True)) as db:
            sql = db.execute("SELECT sql FROM sqlite_master WHERE name='work_line'").fetchone()[0]
            db.execute('DROP TABLE work_line')
            db.execute(sql[:-1] + ', CHECK(actualCount >= 0))')
        self.reject_mutation('SELECT 1')

    def test_extra_expression_index_rejects(self):
        self.reject_mutation('CREATE INDEX opaque_index ON work_line (lower(deliveryCode))')

    def test_work_order_identity_mismatch_protected(self):
        self.reject_mutation("UPDATE work_order SET orderId='other' WHERE state='FROZEN'")

    def test_numeric_permit_is_not_authorization(self):
        prepared = self.prepared()
        allow(prepared, 1)
        candidate = Path(prepared['run']) / 'candidate.sqlite'
        before = candidate.read_bytes()
        with self.assertRaisesRegex(ValueError, 'permit'):
            consume(prepared['run'], prepared['planSha256'], True)
        self.assertEqual(before, candidate.read_bytes())
