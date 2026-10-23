"""Successor rounds against original files, replay and refusal without mutation."""
import unittest
from uuid import uuid4
from control_factory import fixture, next_command, write
from control_format import hashed
from control_java_process import inventory
from control_store import ControlStore
from control_successor import EXTRA_FIELDS
from control_successor_factory import released, normal, successor, release, append_unchecked
from safety import OpsError


class ControlSuccessorTests(unittest.TestCase):
    def setUp(self):
        self.base, self.location, self.initial, self.result = released()
        self.normal, self.normal_path = normal(self.location, self.result)
        self.command = successor(self.result, self.normal)

    def apply(self, value):
        return ControlStore(self.location).apply(value, self.result['checkpoint'])

    def refuse(self, value, code):
        before = inventory(self.location.root)
        with self.assertRaisesRegex(OpsError, code):
            self.apply(value)
        self.assertEqual(before, inventory(self.location.root))

    def test_three_rounds_keep_originals_and_original_replay(self):
        original = inventory(self.location.root)
        first = self.apply(self.command)
        for index in range(2):
            self.result = release(self.location, first)
            value, _ = normal(self.location, self.result)
            first = self.apply(successor(self.result, value)) if index == 0 else self.result
        self.result = first
        for name, digest in original.items():
            if name not in ('current.json', 'admission.json'):
                self.assertEqual(digest, inventory(self.location.root)[name])
        self.assertEqual(self.command, self.apply(self.command)['record']['command'])
        self.assertEqual(self.initial, self.apply(self.initial)['record']['command'])
        self.assertEqual(first, ControlStore(self.location).inspect(first['checkpoint']))
        self.assertFalse(first['ordinaryWritesAllowed'])
        self.assertFalse(first['localDeletionAllowed'])

    def test_invalid_successor_does_not_publish_intent(self):
        changes = [('expectedRevision', '3', 'CONTROL_CAS_CONFLICT'),
                   ('phase', 'RECOVERING', 'CONTROL_SUCCESSOR_TRANSITION'),
                   ('epochId', str(uuid4()), 'CONTROL_EPOCH_TRANSITION'),
                   ('recoverySessionId', self.result['record']['command']['recoverySessionId'], 'CONTROL_SESSION_REUSED'),
                   ('predecessorReleaseSha256', 'e' * 64, 'CONTROL_SUCCESSOR_PREDECESSOR'),
                   ('predecessorNormalIntentSha256', 'e' * 64, 'CONTROL_SUCCESSOR_NORMAL_REQUIRED'),
                   ('sourceBindingSha256', '0' * 64, 'CONTROL_SUCCESSOR_EVIDENCE_REQUIRED'),
                   ('sourceNormalSha256', 'invalid', 'CONTROL_HASH'),
                   ('targetServerUuid', str(uuid4()).upper(), 'CONTROL_UUID'),
                   ('targetDatabaseSchema', '../other', 'CONTROL_SUCCESSOR_SCHEMA'),
                   ('formatVersion', True, 'CONTROL_FORMAT_UNSUPPORTED')]
        for key, value, code in changes:
            with self.subTest(key=key):
                self.refuse({**self.command, key: value}, code)

    def test_version_one_cannot_open_a_successor(self):
        value = {k: v for k, v in self.command.items() if k not in EXTRA_FIELDS.split()}
        self.refuse({**value, 'formatVersion': 1}, 'CONTROL_SUCCESSOR_TRANSITION')

    def test_missing_normal_is_not_recreated(self):
        self.normal_path.rename(self.base / 'retained-normal.json')
        self.refuse(self.command, 'CONTROL_SUCCESSOR_NORMAL_REQUIRED')
        self.assertFalse(self.normal_path.exists())

    def test_corrupt_normal_hash_is_refused(self):
        write(self.normal_path, {**self.normal, 'intentSha256': 'e' * 64})
        self.refuse(self.command, 'CONTROL_NORMAL_INTENT_HASH')

    def test_rehashed_normal_must_bind_release_revision_previous_and_installation(self):
        updates = ({'controlRevision': 3}, {'controlRecordSha256': 'e' * 64},
                   {'previousRecordSha256': 'f' * 64}, {'installationId': str(uuid4())})
        for update in updates:
            with self.subTest(update=update):
                body = {k: v for k, v in {**self.normal, **update}.items() if k != 'intentSha256'}
                write(self.normal_path, {**body, 'intentSha256': hashed(body)})
                self.refuse(self.command, 'CONTROL_SUCCESSOR_NORMAL_BINDING')

    def test_context_pins_cannot_change_or_downgrade_mid_round(self):
        self.result = self.apply(self.command)
        following = next_command(self.result)
        for key in EXTRA_FIELDS.split():
            value = str(uuid4()) if key == 'targetServerUuid' else 'another' if key == 'targetDatabaseSchema' else 'e' * 64
            with self.subTest(key=key):
                self.refuse({**following, key: value}, 'CONTROL_SUCCESSOR_CONTEXT_CHANGED')
        self.refuse({**following, 'snapshotId': 'another'}, 'CONTROL_CONTEXT_CHANGED')
        legacy = {k: v for k, v in following.items() if k not in EXTRA_FIELDS.split()}
        self.refuse({**legacy, 'formatVersion': 1}, 'CONTROL_ROUND_VERSION')

    def test_earlier_round_session_cannot_be_reused(self):
        self.result = release(self.location, self.apply(self.command))
        value, _ = normal(self.location, self.result)
        cmd = successor(self.result, value)
        self.refuse({**cmd, 'recoverySessionId': self.initial['recoverySessionId']}, 'CONTROL_SESSION_REUSED')

    def test_any_historical_epoch_cannot_be_reused_at_recovering(self):
        self.result = self.apply(self.command)
        self.refuse({**next_command(self.result), 'epochId': self.initial['epochId']}, 'CONTROL_EPOCH_REUSED')

    def test_rehashed_hostile_history_and_pending_cannot_reuse_epoch(self):
        for pending in (False, True):
            with self.subTest(pending=pending):
                _, location, initial, before = released()
                value, _ = normal(location, before)
                current = ControlStore(location).apply(successor(before, value), before['checkpoint'])
                cmd = {**next_command(current), 'epochId': initial['epochId']}
                append_unchecked(location, current, cmd, pending=pending)
                original = inventory(location.root)
                with self.assertRaisesRegex(OpsError, 'CONTROL_EPOCH_REUSED'):
                    ControlStore(location).inspect(before['checkpoint'])
                self.assertEqual(original, inventory(location.root))

    def test_v2_bootstrap_is_rejected_before_creating_any_files(self):
        _, location, initial = fixture()
        cmd = {**self.command, **initial, 'formatVersion': 2}
        with self.assertRaisesRegex(OpsError, 'CONTROL_INITIAL_FORMAT'):
            ControlStore(location).initialize(cmd)
        self.assertFalse(location.root.exists())


if __name__ == '__main__':
    unittest.main()
