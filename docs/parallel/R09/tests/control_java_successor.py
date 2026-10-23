"""Java reads Python successor originals and hostile rehashed/pending states."""
from pathlib import Path
import os
import sys
import unittest
from uuid import uuid4
from control_java_cases import JavaControlCase
from control_java_writers import JavaWriterTests
from control_java_process import inventory
from control_factory import next_command, write
from control_format import hashed
from control_store import ControlStore
from control_successor_factory import normal, successor, release, append_unchecked
from safety import OpsError


class JavaSuccessorTests(JavaControlCase):
    writer = JavaWriterTests.writer

    def setUp(self):
        super().setUp()
        self.initial = self.result['record']['command']
        self.result = release(self.location, self.result)
        self.normal, self.normal_path = normal(self.location, self.result)
        self.command = successor(self.result, self.normal)

    def apply(self, value):
        return ControlStore(self.location).apply(value, self.result['checkpoint'])

    def assert_both_refused(self, code):
        self.assert_refused(code)
        original = inventory(self.location.root)
        with self.assertRaisesRegex(OpsError, code):
            ControlStore(self.location).inspect(self.result['checkpoint'])
        self.assertEqual(original, inventory(self.location.root))

    def test_three_complete_rounds_accept_same_bytes_keep_originals_and_old_replay(self):
        original = inventory(self.location.root)
        for _ in range(2):
            self.result = self.apply(self.command)
            self.assert_snapshot(self.result)
            for phase in ('RECOVERING', 'VERIFYING', 'RELEASE_READY'):
                self.result = self.apply(next_command(self.result, phase))
                self.assert_snapshot(self.result)
            value, _ = normal(self.location, self.result)
            self.command = successor(self.result, value)
        for name, digest in original.items():
            if name not in ('current.json', 'admission.json'):
                self.assertEqual(digest, inventory(self.location.root)[name])
        self.assertEqual(self.initial, self.apply(self.initial)['record']['command'])
        write(self.trusted, self.result['checkpoint'])
        self.assert_snapshot(self.result)

    def test_missing_normal_after_successor_is_refused_without_repair(self):
        self.result = self.apply(self.command)
        self.normal_path.rename(self.base / 'retained-normal.json')
        self.assert_both_refused('CONTROL_SUCCESSOR_NORMAL_REQUIRED')

    def test_corrupt_normal_is_refused_without_rewrite(self):
        self.result = self.apply(self.command)
        write(self.normal_path, {**self.normal, 'intentSha256': 'f' * 64})
        self.assert_both_refused('CONTROL_NORMAL_INTENT_HASH')

    def test_rehashed_normal_cannot_point_to_another_record(self):
        self.result = self.apply(self.command)
        body = {k: v for k, v in self.normal.items() if k != 'intentSha256'}
        body['previousRecordSha256'] = 'f' * 64
        write(self.normal_path, {**body, 'intentSha256': hashed(body)})
        self.assert_both_refused('CONTROL_SUCCESSOR_NORMAL_BINDING')

    def test_history_normal_filename_is_exact_hash(self):
        self.result = release(self.location, self.apply(self.command))
        _, path = normal(self.location, self.result)
        path.rename(path.with_name('wrong.json'))
        self.assert_both_refused('CONTROL_SUCCESSOR_NORMAL_LAYOUT')

    def test_history_normal_cannot_be_hardlinked(self):
        self.result = release(self.location, self.apply(self.command))
        _, path = normal(self.location, self.result)
        os.link(path, self.base / 'aliased-normal.json')
        self.assert_both_refused('CONTROL_FILE_ALIAS')

    def test_rehashed_record_with_wrong_predecessor_is_refused(self):
        append_unchecked(self.location, self.result, {**self.command, 'predecessorReleaseSha256': 'e' * 64})
        self.assert_both_refused('CONTROL_SUCCESSOR_PREDECESSOR')

    def test_rehashed_history_cannot_reuse_any_prior_epoch(self):
        self.result = self.apply(self.command)
        cmd = {**next_command(self.result), 'epochId': self.initial['epochId']}
        append_unchecked(self.location, self.result, cmd)
        self.assert_both_refused('CONTROL_EPOCH_REUSED')

    def test_pending_intent_cannot_reuse_any_prior_epoch(self):
        self.result = self.apply(self.command)
        cmd = {**next_command(self.result), 'epochId': self.initial['epochId']}
        append_unchecked(self.location, self.result, cmd, pending=True)
        self.assert_both_refused('CONTROL_EPOCH_REUSED')

    def test_rehashed_history_cannot_reuse_first_session_after_second_release(self):
        self.result = release(self.location, self.apply(self.command))
        value, _ = normal(self.location, self.result)
        cmd = {**successor(self.result, value), 'recoverySessionId': self.initial['recoverySessionId']}
        append_unchecked(self.location, self.result, cmd)
        self.assert_both_refused('CONTROL_SESSION_REUSED')

    def test_pending_intent_cannot_change_target_within_round(self):
        self.result = self.apply(self.command)
        cmd = {**next_command(self.result), 'targetDatabaseSchema': 'another'}
        append_unchecked(self.location, self.result, cmd, pending=True)
        self.assert_both_refused('CONTROL_SUCCESSOR_CONTEXT_CHANGED')

    def test_writer_drain_precedes_successor_record_and_blocks_new_writers(self):
        original = inventory(self.location.root)
        child = self.writer()
        with self.assertRaisesRegex(OpsError, 'CONTROL_WRITERS_ACTIVE'):
            self.apply(self.command)
        self.assert_snapshot(self.result)
        self.assertEqual((2, 'CONTROL_CLOSING_INTENT', ''), self.processes.finish(self.writer('probe', False)))
        self.assertEqual((0, '', ''), self.processes.finish(child, '\n'))
        self.result = self.apply(self.command)
        self.assert_snapshot(self.result)
        for name, digest in original.items():
            if name not in ('current.json', 'admission.json'):
                self.assertEqual(digest, inventory(self.location.root)[name])


class JavaSuccessorCrashTests(JavaSuccessorTests):
    # Inherit setup/helpers only; the runner explicitly selects the test below.
    def test_process_death_at_each_successor_boundary_resumes_only_original(self):
        for point in ('intent_appended', 'intent_head_staged', 'intent_head_committed',
                      'history_appended', 'pointer_staged', 'pointer_committed'):
            with self.subTest(point=point):
                if point != 'intent_appended':
                    self.base, self.location, _, self.result = __import__('control_successor_factory').released(self.base)
                    self.normal, _ = normal(self.location, self.result)
                    self.command = successor(self.result, self.normal)
                    write(self.trusted, self.result['checkpoint'])
                self.crash_and_resume(point)

    def crash_and_resume(self, point):
        original = inventory(self.location.root)
        write(self.base / 'checkpoint.json', self.result['checkpoint'])
        write(self.base / 'next.json', self.command)
        write(self.base / 'process.json', {'root': str(self.location.root), 'installation': self.location.installation_id,
                                          'restore': [str(p) for p in self.location.restore_roots]})
        args = [sys.executable, str(Path(__file__).with_name('control_process.py')), str(self.base), point, 'next.json']
        self.assertEqual((73, '', ''), self.processes.finish(self.processes.start(args)))
        if point == 'intent_head_committed':
            self.assert_snapshot(self.result)
            self.assertEqual((2, 'CONTROL_CLOSING_INTENT', ''), self.processes.finish(self.writer('probe', False)))
        elif point != 'pointer_committed':
            code = 'CONTROL_INTENT_INCOMPLETE' if point == 'intent_appended' else 'CONTROL_UNKNOWN_ARTIFACT'
            if point in ('history_appended', 'pointer_staged'):
                code = 'CONTROL_COMMAND_INCOMPLETE'
            self.assert_refused(code)
        pending_original = inventory(self.location.root)
        with self.assertRaisesRegex(OpsError, 'CONTROL_INTENT_CONFLICT|CONTROL_CAS_CONFLICT|CONTROL_COMMAND_INCOMPLETE'):
            self.apply({**self.command, 'commandId': str(uuid4())})
        self.assertEqual(pending_original, inventory(self.location.root))
        self.result = self.apply(self.command)
        self.assert_snapshot(self.result)
        self.assertEqual(self.result, self.apply(self.command))
        for name, digest in original.items():
            if name not in ('current.json', 'admission.json'):
                self.assertEqual(digest, inventory(self.location.root)[name])
