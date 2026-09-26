"""Bidirectional live process contention proves the common byte-range protocol."""
from concurrent.futures import ThreadPoolExecutor
from control_disk import locked
from control_store import ControlStore
from control_factory import next_command
from control_java_cases import JavaControlCase
from safety import OpsError


class JavaLockTests(JavaControlCase):
    def assert_python_busy(self):
        with self.assertRaisesRegex(OpsError, 'CONTROL_COORDINATOR_BUSY'):
            ControlStore(self.location).inspect(self.result['checkpoint'])
        with self.assertRaisesRegex(OpsError, 'CONTROL_COORDINATOR_BUSY'):
            ControlStore(self.location).apply(next_command(self.result), self.result['checkpoint'])

    def assert_java_busy(self):
        self.assertEqual((2, 'CONTROL_COORDINATOR_BUSY', ''),
                         self.processes.run_java('probe', self.location.root))

    def test_python_holder_blocks_java_until_normal_release(self):
        child = self.processes.hold_python(self.base)
        self.assert_java_busy()
        self.assertEqual((0, '', ''), self.processes.finish(child, '\n'))
        self.assert_snapshot(self.result)

    def test_java_holder_blocks_python_read_and_write_until_normal_release(self):
        child = self.processes.hold_java(self.location.root)
        self.assert_python_busy()
        self.assertEqual((0, '', ''), self.processes.finish(child, '\n'))
        self.assertEqual(self.result, ControlStore(self.location).inspect(self.result['checkpoint']))

    def test_python_holder_death_releases_for_java(self):
        child = self.processes.hold_python(self.base)
        self.assert_java_busy()
        child.kill()
        self.assertNotEqual(0, self.processes.finish(child)[0])
        self.assert_snapshot(self.result)
        self.assertTrue((self.location.root / 'coordinator.lock').exists())

    def test_java_holder_death_releases_for_python(self):
        child = self.processes.hold_java(self.location.root)
        self.assert_python_busy()
        child.kill()
        self.assertNotEqual(0, self.processes.finish(child)[0])
        self.assertEqual(self.result, ControlStore(self.location).inspect(self.result['checkpoint']))
        self.assertTrue((self.location.root / 'coordinator.lock').exists())

    def test_nested_python_call_cannot_drop_outer_posix_lock(self):
        with locked(self.location.root):
            self.assert_python_busy()
            self.assert_java_busy()
        self.assert_snapshot(self.result)

    def test_threaded_python_call_cannot_drop_outer_posix_lock(self):
        with locked(self.location.root):
            with ThreadPoolExecutor(max_workers=1) as pool:
                pool.submit(self.assert_python_busy).result(timeout=20)
            self.assert_java_busy()
        self.assert_snapshot(self.result)

    def test_nested_java_call_cannot_drop_outer_posix_lock(self):
        child = self.processes.hold_java(self.location.root, 'nested')
        self.assert_python_busy()
        self.assertEqual((0, '', ''), self.processes.finish(child, '\n'))
        self.assertEqual(self.result, ControlStore(self.location).inspect(self.result['checkpoint']))

    def test_threaded_java_call_cannot_drop_outer_posix_lock(self):
        child = self.processes.hold_java(self.location.root, 'threaded')
        self.assert_python_busy()
        self.assertEqual((0, '', ''), self.processes.finish(child, '\n'))
        self.assertEqual(self.result, ControlStore(self.location).inspect(self.result['checkpoint']))
