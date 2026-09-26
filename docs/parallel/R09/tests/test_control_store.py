"""Observable CAS, replay and closed-state semantics against actual files."""
import unittest
from uuid import uuid4
from control_factory import fixture, initialized, next_command
from control_store import ControlStore
from control_paths import ControlLocation
from safety import OpsError


class ControlStoreTests(unittest.TestCase):
    def test_ordinary_read_does_not_initialize(self):
        _, location, _ = fixture()
        with self.assertRaisesRegex(OpsError, 'CONTROL_FILE_MISSING'):
            ControlStore(location).inspect({})
        self.assertFalse(location.root.exists())

    def test_explicit_initialization_and_restart_keep_original_record(self):
        _, location, initial, result = initialized()
        self.assertEqual(result, ControlStore(location).initialize(initial))
        self.assertEqual(result, ControlStore(location).inspect(result['checkpoint']))
        self.assertFalse(result['ordinaryWritesAllowed'])
        self.assertFalse(result['localDeletionAllowed'])

    def test_same_id_different_body_refused(self):
        _, location, initial, result = initialized()
        changed = {**initial, 'actorId': 'another-operator'}
        with self.assertRaisesRegex(OpsError, 'CONTROL_COMMAND_CONFLICT'):
            ControlStore(location).apply(changed, result['checkpoint'])

    def test_stale_revision_refused_without_append(self):
        _, location, _, result = initialized()
        command = {**next_command(result), 'expectedRevision': '0'}
        with self.assertRaisesRegex(OpsError, 'CONTROL_CAS_CONFLICT'):
            ControlStore(location).apply(command, result['checkpoint'])
        self.assertEqual(1, len(list((location.root / 'history').iterdir())))

    def test_original_retry_after_later_commands_returns_original_result(self):
        _, location, initial, result = initialized()
        later = ControlStore(location).apply(next_command(result), result['checkpoint'])
        self.assertEqual(result, ControlStore(location).apply(initial, later['checkpoint']))
        self.assertEqual(later, ControlStore(location).inspect(later['checkpoint']))

    def test_closed_phase_chain_never_opens_writes(self):
        _, location, _, result = initialized()
        for phase in ('RECOVERING', 'VERIFYING', 'RELEASE_READY'):
            previous = result
            result = ControlStore(location).apply(next_command(result, phase), result['checkpoint'])
            self.assertFalse(result['ordinaryWritesAllowed'])
            self.assertEqual(previous['record']['recordSha256'], result['record']['previousRecordSha256'])
        with self.assertRaisesRegex(OpsError, 'CONTROL_PHASE_CLOSED_ONLY'):
            ControlStore(location).apply(next_command(result, 'NORMAL'), result['checkpoint'])

    def test_installation_mismatch_refused(self):
        _, location, _, result = initialized()
        other = ControlLocation(location.root, str(uuid4()), location.restore_roots)
        with self.assertRaisesRegex(OpsError, 'CONTROL_INSTALLATION_MISMATCH'):
            ControlStore(other).inspect(result['checkpoint'])

    def test_all_semantic_changes_fail_before_append(self):
        _, location, _, result = initialized()
        changes = [('phase', 'VERIFYING'), ('epochId', result['record']['command']['epochId']),
                   ('snapshotId', 'other'), ('affectedWarehouseIds', ['W3']),
                   ('recoverySessionId', str(uuid4())), ('manifestSha256', 'c' * 64)]
        for field, value in changes:
            with self.subTest(field=field), self.assertRaises(OpsError):
                ControlStore(location).apply({**next_command(result), field: value}, result['checkpoint'])
        self.assertEqual(1, len(list((location.root / 'history').iterdir())))

    def test_validation_rejects_bad_format_and_fields(self):
        _, location, _, result = initialized()
        changes = [('formatVersion', True), ('formatVersion', 2), ('expectedRevision', '01'),
                   ('commandId', '../file'), ('evidenceSha256', '0' * 64), ('affectedWarehouseIds', []),
                   ('affectedWarehouseIds', ['W2', 'W1']), ('affectedWarehouseIds', ['W1', 'W1']),
                   ('possibleLossThrough', '2026-09-23T00:00:00Z'), ('requestedAt', 'invalid')]
        for field, value in changes:
            with self.subTest(field=field), self.assertRaises(OpsError):
                ControlStore(location).apply({**next_command(result), field: value}, result['checkpoint'])

    def test_unknown_properties_are_not_silently_ignored(self):
        _, location, _, result = initialized()
        with self.assertRaisesRegex(OpsError, 'CONTROL_FIELDS'):
            ControlStore(location).apply({**next_command(result), 'allowWrite': True}, result['checkpoint'])


if __name__ == '__main__':
    unittest.main()
