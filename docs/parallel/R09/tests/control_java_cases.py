"""Independent Java consumes actual Python control originals; no reserialization."""
from pathlib import Path
import json
import shutil
import unittest
from control_factory import fixture, next_command, write
from control_java_process import Processes, inventory
from control_paths import ControlLocation
from control_store import ControlStore


class JavaControlCase(unittest.TestCase):
    config = None

    def setUp(self):
        java, paths, root = self.config
        self.base, self.location, initial = fixture(root)
        self.processes = Processes(java, paths, self.base / 'transcript.jsonl')
        self.addCleanup(self.processes.close)
        write(self.base / 'case.json', {'test': self.id(), 'platform': __import__('platform').platform()})
        self.result = ControlStore(self.location).initialize(initial)
        self.trusted = write(self.base / 'trusted.json', self.result['checkpoint'])
        write(self.base / 'process.json', {'root': str(self.location.root),
              'installation': self.location.installation_id,
              'restore': [str(p) for p in self.location.restore_roots]})

    def read_java(self):
        config = write(self.base / 'java-config.json', {'root': str(self.location.root),
                       'installationId': self.location.installation_id, 'checkpoint': str(self.trusted),
                       'restoreRoots': [str(p) for p in self.location.restore_roots]})
        return self.processes.run_java('read-config', config)

    def assert_snapshot(self, expected):
        before = inventory(self.location.root)
        code, out, err = self.read_java()
        self.assertEqual((0, ''), (code, err), out)
        result = json.loads(out)
        record, cmd = expected['record'], expected['record']['command']
        keys = ('installationId', 'phase', 'recoverySessionId', 'epochId', 'snapshotId',
                'manifestSha256', 'evidenceSha256', 'affectedWarehouseIds',
                'possibleLossFrom', 'possibleLossThrough')
        self.assertEqual({**{k: cmd[k] for k in keys}, 'revision': record['revision'],
                          'recordSha256': record['recordSha256'], 'ordinaryWritesAllowed': False,
                          'localDeletionAllowed': False}, result)
        self.assertEqual(before, inventory(self.location.root))

    def assert_refused(self, code):
        before = inventory(self.location.root)
        self.assertEqual((2, code, ''), self.read_java())
        self.assertEqual(before, inventory(self.location.root))


class JavaOriginalTests(JavaControlCase):
    def test_original_four_phases_accept_old_and_latest_trusted_anchors(self):
        self.assert_snapshot(self.result)
        for phase in ('RECOVERING', 'VERIFYING', 'RELEASE_READY'):
            self.result = ControlStore(self.location).apply(next_command(self.result, phase), self.result['checkpoint'])
            self.assert_snapshot(self.result)
        write(self.trusted, self.result['checkpoint'])
        self.assert_snapshot(self.result)

    def test_original_unicode_restore_paths_preserve_python_canonical_bytes(self):
        base, _, initial = fixture(self.base)
        roots = tuple(base / name for name in ('\U00010000-private', '\ue000-private', '资料'))
        self.location = ControlLocation(base / 'authority', initial['installationId'], roots)
        self.result = ControlStore(self.location).initialize(initial)
        write(self.trusted, self.result['checkpoint'])
        self.assert_snapshot(self.result)

    def test_partial_pointer_is_refused_without_repair(self):
        ControlStore(self.location).apply(next_command(self.result), self.result['checkpoint'])
        write(self.location.root / 'current.json', self.result['checkpoint'])
        self.assert_refused('CONTROL_COMMAND_INCOMPLETE')

    def test_whole_directory_rollback_is_refused_by_external_trusted_anchor(self):
        old = self.base / 'old-authority'
        shutil.copytree(self.location.root, old)
        later = ControlStore(self.location).apply(next_command(self.result), self.result['checkpoint'])
        write(self.trusted, later['checkpoint'])
        self.location = ControlLocation(old, self.location.installation_id, self.location.restore_roots)
        self.assert_refused('CONTROL_CHECKPOINT_ROLLBACK')

    def test_corrupt_original_command_is_refused_without_rewriting(self):
        path = self.location.root / 'history' / '000000000000000001.json'
        record = json.loads(path.read_bytes())
        record['command']['actorId'] = 'changed-actor'
        write(path, record)
        self.assert_refused('CONTROL_COMMAND_HASH')

    def test_missing_authority_does_not_initialize(self):
        root = self.base / 'missing'
        self.location = ControlLocation(root, self.location.installation_id, self.location.restore_roots)
        self.assert_refused('CONTROL_FILE_MISSING')
        self.assertFalse(root.exists())

    def test_hardlinked_history_is_refused(self):
        import os
        os.link(self.location.root / 'history' / '000000000000000001.json', self.base / 'history-alias')
        self.assert_refused('CONTROL_FILE_ALIAS')

    def test_malformed_lock_is_refused_without_repair(self):
        from safety import OpsError
        (self.location.root / 'coordinator.lock').write_bytes(b'')
        with self.assertRaisesRegex(OpsError, 'CONTROL_LOCK_INVALID'):
            ControlStore(self.location).inspect(self.result['checkpoint'])
        self.assert_refused('CONTROL_LOCK_INVALID')
