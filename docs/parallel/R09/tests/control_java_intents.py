"""Actual Python crash states are read by independent Java admission code."""
from pathlib import Path
import shutil
import sys
from control_java_cases import JavaControlCase
from control_java_writers import JavaWriterTests
from control_factory import next_command, write
from control_disk import read
from control_store import ControlStore


class JavaIntentTests(JavaControlCase):
    writer = JavaWriterTests.writer

    def crash(self, point):
        command = next_command(self.result)
        write(self.base / 'checkpoint.json', self.result['checkpoint'])
        write(self.base / 'next.json', command)
        args = [sys.executable, str(Path(__file__).with_name('control_process.py')),
                str(self.base), point, 'next.json']
        self.assertEqual((73, '', ''), self.processes.finish(self.processes.start(args)))
        return command

    def test_python_death_after_intent_append_refuses_java_before_original_retry(self):
        command = self.crash('intent_appended')
        self.assertEqual((2, 'CONTROL_INTENT_INCOMPLETE', ''), self.processes.finish(self.writer('probe', False)))
        later = ControlStore(self.location).apply(command, self.result['checkpoint'])
        self.assert_snapshot(later)

    def test_python_death_with_staged_head_never_admits_java(self):
        command = self.crash('intent_head_staged')
        self.assertEqual((2, 'CONTROL_UNKNOWN_ARTIFACT', ''), self.processes.finish(self.writer('probe', False)))
        later = ControlStore(self.location).apply(command, self.result['checkpoint'])
        self.assert_snapshot(later)

    def test_python_death_after_published_head_keeps_new_java_writers_closed(self):
        command = self.crash('intent_head_committed')
        self.assert_snapshot(self.result)
        self.assertEqual((2, 'CONTROL_CLOSING_INTENT', ''), self.processes.finish(self.writer('probe', False)))
        later = ControlStore(self.location).apply(command, self.result['checkpoint'])
        self.assert_snapshot(later)
        self.assertEqual((0, 'LOCKED', ''), self.processes.finish(self.writer('probe', False)))

    def test_missing_intent_cannot_be_recreated_by_java(self):
        self.crash('intent_head_committed')
        path = self.location.root / 'intents/000000000000000002.json'
        shutil.move(path, self.base / 'retained-intent.json')
        self.assert_refused('CONTROL_INTENT_HEAD_CONFLICT')
        self.assertFalse(path.exists())

    def test_corrupted_intent_is_rejected_before_database_admission(self):
        self.crash('intent_head_committed')
        path = self.location.root / 'intents/000000000000000002.json'
        write(path, {**read(path), 'intentSha256': 'f' * 64})
        self.assert_refused('CONTROL_INTENT_CORRUPT')

    def test_boolean_header_version_is_rejected_by_independent_consumer(self):
        path = self.location.root / 'admission.json'
        write(path, {**read(path), 'formatVersion': True})
        self.assert_refused('CONTROL_FORMAT_UNSUPPORTED')
