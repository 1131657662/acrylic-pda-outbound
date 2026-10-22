"""Archive-file consumers on restored native facts with retained guards and audits."""
from pathlib import Path

from ..codec import canonical, digest, parse
from ..store import restore
from .factory import allow
from .restored_formal_case import RestoredFormalCase
from . import test_retrieval


class RestoredRetrievalTest(RestoredFormalCase):
    test_real_lock_wait_file_loss_preserves_restored_facts = (
        test_retrieval.RetrievalTest.test_file_lost_during_real_lock_wait_prevents_deletion)
    test_last_delete_file_loss_rolls_back_restored_facts = (
        test_retrieval.RetrievalTest.test_last_delete_followed_by_file_loss_rolls_back_all_facts)

    def restored_spec(self, prepared):
        run = Path(prepared['run'])
        restored = self.directory / 'complete-restored.sqlite'
        restore(run / 'backup.sqlite', restored)
        self.assertEqual(self.snapshot(self.source), self.snapshot(restored))
        self.assert_formal_preserved(restored)
        spec = dict(self.spec_data, source=str(restored), sourceSha256=digest(restored.read_bytes()))
        path = self.directory / 'restored-spec.json'
        with path.open('xb') as output:
            output.write(canonical(spec))
        return path, restored

    def consume_prepared(self, prepared, label):
        allow(prepared)
        result = self.cli(self.consume_args(prepared), label)
        self.assertEqual(0, result.returncode, result.stdout)
        self.assertEqual('COMMITTED_SYNTHETIC_ONLY', parse(result.stdout)['state'])
        candidate = Path(prepared['run']) / 'candidate.sqlite'
        self.assert_formal_preserved(candidate)
        return candidate

    def test_restored_backup_uses_same_retrievable_files_for_new_cli_consumer(self):
        before = self.snapshot(self.source)
        original = self.prepared()
        first = self.consume_prepared(original, 'before-restore-consume')
        spec, restored = self.restored_spec(original)
        preparation = self.cli(['prepare', spec], 'restored-prepare')
        self.assertEqual(0, preparation.returncode, preparation.stdout)
        prepared = parse(preparation.stdout)
        plan = parse((Path(prepared['run']) / 'plan.json').read_bytes())
        self.assertEqual(self.spec_data['archiveFiles'], plan['spec']['archiveFiles'])
        candidate = self.consume_prepared(prepared, 'restored-consume')
        self.assertEqual(self.snapshot(first), self.snapshot(candidate))
        self.assertEqual(before, self.snapshot(restored))
        replay = self.cli(self.consume_args(prepared), 'restored-replay')
        self.assertEqual(0, replay.returncode, replay.stdout)
        self.assertEqual('REPLAY_NO_WRITE', parse(replay.stdout)['state'])
        restore(Path(prepared['run']) / 'backup.sqlite', self.directory / 'second-restored.sqlite')
        self.assertEqual(before, self.snapshot(self.directory / 'second-restored.sqlite'))

    def test_restored_backup_receipt_does_not_replace_missing_archive_file(self):
        prepared = self.prepared()
        spec, restored = self.restored_spec(prepared)
        before = self.snapshot(restored)
        archive = Path(self.spec_data['archiveFiles']['receipt'])
        archive.rename(archive.with_suffix('.unavailable'))
        result = self.cli(['prepare', spec], 'restored-file-unavailable')
        self.assertEqual(1, result.returncode, result.stdout)
        self.assertIn(b'Missing or oversized input', result.stdout)
        self.assertEqual(before, self.snapshot(restored))
        self.assert_formal_preserved(restored)
        plans = list(self.directory.glob('retention-*/plan.json'))
        self.assertEqual([Path(prepared['run']) / 'plan.json'], plans)
