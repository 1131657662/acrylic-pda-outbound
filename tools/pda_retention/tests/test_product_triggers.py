"""Actual CLI deletion/recovery and protection with native triggers plus original audit references."""
from contextlib import closing
from pathlib import Path
from unittest.mock import patch

from ..codec import canonical, parse
from ..product_triggers import reviewed
from ..store import connect, restore
from .audit_case import UnrelatedAuditCase
from .factory import allow, insert, mutate
from . import test_concurrency, test_consumer


class NativeTriggerCase(UnrelatedAuditCase):
    def setUp(self):
        super().setUp()
        with closing(connect(self.source, True)) as db:
            for row in reviewed():
                db.execute(row['sql'])
        mutate(self.spec, 'SELECT 1')
        spec = parse(self.spec.read_bytes())
        spec['triggerProfile'] = 'PRODUCT_NATIVE'
        self.spec.write_bytes(canonical(spec))
        self.spec_data = spec

    def triggers(self, path):
        with closing(connect(path)) as db:
            return [tuple(row) for row in db.execute(
                "SELECT name,tbl_name,sql FROM sqlite_master WHERE type='trigger' ORDER BY name")]


class NativeConsumerTest(NativeTriggerCase, test_consumer.ConsumerTest):
    """Default-off, true CLI success/replay, changed input and last SQL rollback on native structure."""


class NativeConcurrencyTest(NativeTriggerCase, test_concurrency.ConcurrencyTest):
    """Same-plan concurrency and actual lock-wait revocation with all native protections installed."""


class NativeTriggerTest(NativeTriggerCase):
    def test_checkout_line_endings_do_not_change_reviewed_definitions(self):
        original = Path.read_bytes
        def crlf(path):
            raw = original(path).replace(b'\r\n', b'\n')
            return raw.replace(b'\n', b'\r\n') if path.suffix in ('.json', '.kt') else raw
        expected = reviewed()
        with patch.object(Path, 'read_bytes', crlf):
            self.assertEqual(expected, reviewed())

    def test_changed_product_source_requires_new_review(self):
        original = Path.read_bytes
        def changed(path):
            raw = original(path)
            return raw + b'\n// changed protection\n' if path.name == 'IndependentScanConstraints.kt' else raw
        with patch.object(Path, 'read_bytes', changed):
            with self.assertRaisesRegex(ValueError, 'source changed'):
                self.prepared()

    def test_cli_deletion_preserves_every_native_trigger_and_restores_original(self):
        before, triggers = self.snapshot(self.source), self.triggers(self.source)
        prepared = self.prepared()
        allow(prepared)
        result = self.cli(self.consume_args(prepared), 'native-consume')
        self.assertEqual(0, result.returncode, result.stdout)
        run = Path(prepared['run'])
        self.assertEqual(triggers, self.triggers(run / 'candidate.sqlite'))
        restore(run / 'backup.sqlite', run / 'native-restored.sqlite')
        self.assertEqual(before, self.snapshot(run / 'native-restored.sqlite'))
        self.assertEqual(triggers, self.triggers(run / 'native-restored.sqlite'))

    def test_effective_target_guard_still_rejects_with_native_triggers(self):
        capture = self.fixture['body']['captures'][0]
        with closing(connect(self.source, True)) as db:
            insert(db, 'work_guard', dict(barcode=capture['barcode'], captureId=capture['id'],
                   assignmentId=capture['assignmentId'], dispatchCycle=int(capture['expectedDispatchCycle'])))
        self.reject_change('SELECT 1', expected='Effective guard')

    def test_one_missing_trigger_rejects(self):
        self.reject_change('DROP TRIGGER independent_guard_source_conflict', expected='trigger definitions')

    def test_all_removed_triggers_cannot_be_treated_as_synthetic(self):
        with closing(connect(self.source, True)) as db:
            for name, _, _ in self.triggers(self.source):
                db.execute('DROP TRIGGER "' + name + '"')
        self.reject_change('SELECT 1', expected='trigger definitions')

    def test_changed_native_trigger_body_rejects(self):
        with closing(connect(self.source, True)) as db:
            db.execute('DROP TRIGGER work_guard_independent_conflict')
            db.execute("CREATE TRIGGER work_guard_independent_conflict BEFORE INSERT ON work_guard BEGIN SELECT 1; END")
        self.reject_change('SELECT 1', expected='trigger definitions')

    def test_extra_trigger_rejects(self):
        self.reject_change('CREATE TRIGGER opaque_trigger BEFORE DELETE ON work_order BEGIN SELECT 1; END',
                           expected='trigger definitions')

    def test_view_rejects(self):
        self.reject_change('CREATE VIEW opaque_reference AS SELECT * FROM work_order', expected='Views')

    def test_unknown_profile_rejects(self):
        spec = parse(self.spec.read_bytes())
        spec['triggerProfile'] = 'unreviewed'
        self.spec.write_bytes(canonical(spec))
        self.reject_change('SELECT 1', expected='Unknown trigger profile')

    def test_default_synthetic_profile_does_not_accept_native_triggers(self):
        spec = parse(self.spec.read_bytes())
        del spec['triggerProfile']
        self.spec.write_bytes(canonical(spec))
        self.reject_change('SELECT 1', expected='Synthetic schema')

    def test_trigger_drift_after_prepare_cannot_delete_any_fact(self):
        prepared = self.prepared()
        allow(prepared)
        candidate = Path(prepared['run']) / 'candidate.sqlite'
        with closing(connect(candidate, True)) as db:
            db.execute('DROP TRIGGER independent_guard_source_conflict')
        before = self.snapshot(candidate)
        result = self.cli(self.consume_args(prepared), 'changed-trigger-consume')
        self.assertEqual(1, result.returncode)
        self.assertIn(b'trigger definitions', result.stdout)
        self.assertEqual(before, self.snapshot(candidate))
