"""Actual Java shared leases exclude Python control mutations on Windows and Linux."""
from concurrent.futures import ThreadPoolExecutor
import os
import shutil
from control_java_cases import JavaControlCase
from control_java_process import inventory
from control_factory import next_command, write
from control_disk import read, locked
from control_store import ControlStore
from control_writers import exclusive
from control_paths import ControlLocation
from safety import OpsError


class JavaWriterTests(JavaControlCase):
    def writer(self, mode='hold', ready=True):
        config = write(self.base / 'writer-config.json', {'root': str(self.location.root),
            'installationId': self.location.installation_id, 'checkpoint': str(self.trusted),
            'restoreRoots': [str(p) for p in self.location.restore_roots]})
        args = [*self.processes.java[:-1], 'com.acrylic.outbound.recovery.RecoveryWriterProcess', str(config), mode]
        child = self.processes.start(args)
        if ready:
            self.processes.ready(child)
        return child

    def assert_blocked(self, command):
        before = inventory(self.location.root)
        # A read must not release the writer byte through POSIX descriptor closure.
        self.assertEqual(self.result, ControlStore(self.location).inspect(self.result['checkpoint']))
        with self.assertRaisesRegex(OpsError, 'CONTROL_WRITERS_ACTIVE'):
            ControlStore(self.location).apply(command, self.result['checkpoint'])
        after = inventory(self.location.root)
        self.assertEqual({key: value for key, value in before.items() if key not in ('admission.json',)
                          and not key.startswith('intents')},
                         {key: value for key, value in after.items() if key not in ('admission.json',)
                          and not key.startswith('intents')})
        self.assertEqual((2, 'CONTROL_CLOSING_INTENT', ''), self.processes.finish(self.writer('probe', False)))

    def test_two_java_processes_share_until_last_writer_exits(self):
        command = next_command(self.result)
        first, second = self.writer(), self.writer()
        self.assert_blocked(command)
        self.assertEqual((0, '', ''), self.processes.finish(first, '\n'))
        self.assert_blocked(command)
        self.assertEqual((0, '', ''), self.processes.finish(second, '\n'))
        later = ControlStore(self.location).apply(command, self.result['checkpoint'])
        self.assertEqual('2', later['record']['revision'])
        self.assert_snapshot(later)

    def test_references_and_cross_thread_closes_keep_outer_lease(self):
        for mode, phase in (('refs', 'RECOVERING'), ('threaded', 'VERIFYING')):
            with self.subTest(mode=mode):
                child = self.writer(mode)
                command = next_command(self.result, phase)
                self.assert_blocked(command)
                self.assertEqual((0, '', ''), self.processes.finish(child, '\n'))
                self.result = ControlStore(self.location).apply(command, self.result['checkpoint'])

    def test_java_death_allows_original_python_command_retry(self):
        command = next_command(self.result)
        child = self.writer()
        self.assert_blocked(command)
        child.kill()
        self.assertNotEqual(0, self.processes.finish(child)[0])
        self.assertEqual(command, ControlStore(self.location).apply(command, self.result['checkpoint'])['record']['command'])

    def test_python_exclusive_excludes_java_without_dropping_outer_lock(self):
        identity = read(self.location.root / 'identity.json')
        with exclusive(self.location.root, identity):
            self.assertEqual((2, 'CONTROL_WRITERS_ACTIVE', ''), self.processes.finish(self.writer('probe', False)))
            with ThreadPoolExecutor(max_workers=1) as pool:
                pool.submit(self.assert_nested_exclusive_busy).result(timeout=20)
            self.assertEqual((2, 'CONTROL_WRITERS_ACTIVE', ''), self.processes.finish(self.writer('probe', False)))
        self.assertEqual((0, 'LOCKED', ''), self.processes.finish(self.writer('probe', False)))

    def assert_nested_exclusive_busy(self):
        with self.assertRaisesRegex(OpsError, 'CONTROL_WRITERS_ACTIVE'):
            with exclusive(self.location.root, read(self.location.root / 'identity.json')):
                raise AssertionError('Nested exclusive entered')

    def test_writer_acquisition_obeys_coordinator_first(self):
        with locked(self.location.root):
            self.assertEqual((2, 'CONTROL_COORDINATOR_BUSY', ''), self.processes.finish(self.writer('probe', False)))

    def test_legacy_originals_are_readable_but_not_writer_admissible(self):
        for name in ('writers.json', 'writers.lock', 'intents', 'admission.json'):
            shutil.move(self.location.root / name, self.base / name)
        before = inventory(self.location.root)
        self.assert_snapshot(self.result)
        self.assertEqual((2, 'CONTROL_WRITER_PROTOCOL_REQUIRED', ''), self.processes.finish(self.writer('probe', False)))
        self.assertEqual(before, inventory(self.location.root))

    def test_replaced_inode_is_refused_by_existing_java_holder(self):
        child = self.writer('replace')
        path = self.location.root / 'writers.lock'
        path.rename(self.base / 'original-writers.lock')
        path.write_bytes(b'0')
        before = inventory(self.location.root)
        with self.assertRaisesRegex(OpsError, 'CONTROL_WRITER_LOCK_REPLACED'):
            ControlStore(self.location).apply(next_command(self.result), self.result['checkpoint'])
        self.assertEqual(before, inventory(self.location.root))
        self.assertEqual((2, 'CONTROL_WRITER_LOCK_REPLACED', ''), self.processes.finish(child, '\n'))

    def test_hardlinked_lock_is_refused_without_opening_another_descriptor(self):
        os.link(self.location.root / 'writers.lock', self.base / 'alias.lock')
        before = inventory(self.location.root)
        self.assertEqual((2, 'CONTROL_FILE_ALIAS', ''), self.processes.finish(self.writer('probe', False)))
        self.assertEqual(before, inventory(self.location.root))

    def test_same_inode_reached_after_directory_rename_keeps_one_java_descriptor(self):
        child = self.writer('hold' if os.name == 'nt' else 'relocated')
        moved = self.base / 'relocated-authority'
        if os.name == 'nt':
            # Windows forbids renaming a directory containing this live Java channel.
            # Verify the refusal and continuing exclusion; this is an assertion, not a skip.
            with self.assertRaises(PermissionError):
                self.location.root.rename(moved)
            self.assert_blocked(next_command(self.result))
            self.assertEqual((0, '', ''), self.processes.finish(child, '\n'))
            return
        self.location.root.rename(moved)
        self.location = ControlLocation(moved, self.location.installation_id, self.location.restore_roots)
        config = read(self.base / 'writer-config.json')
        write(self.base / 'writer-config.json', {**config, 'root': str(moved)})
        child.stdin.write('x')
        child.stdin.flush()
        self.processes.ready(child)
        self.assert_blocked(next_command(self.result))
        self.assertEqual((0, '', ''), self.processes.finish(child, '\n'))
