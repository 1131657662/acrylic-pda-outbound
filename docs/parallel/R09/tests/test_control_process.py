"""Real independent processes exercise death, lock release and CAS races."""
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
import subprocess
import sys
import unittest
from control_factory import fixture, initialized, next_command, write
from control_store import ControlStore
from control_disk import read
from safety import OpsError

HARNESS = Path(__file__).with_name('control_process.py')


def prepared():
    base, location, initial, result = initialized()
    write(base / 'process.json', {'root': str(location.root), 'installation': location.installation_id,
                                 'restore': [str(item) for item in location.restore_roots]})
    write(base / 'checkpoint.json', result['checkpoint'])
    return base, location, initial, result


def start(base, mode, request='request.json'):
    return subprocess.Popen([sys.executable, str(HARNESS), str(base), mode, request],
                            stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)


def ready(process):
    with ThreadPoolExecutor(max_workers=1) as pool:
        future = pool.submit(process.stdout.readline)
        try:
            return future.result(timeout=15).strip()
        except Exception:
            process.kill()
            raise


class ControlProcessTests(unittest.TestCase):
    def test_death_at_each_persistence_boundary_recovers_exact_original(self):
        for point in ('history_appended', 'pointer_staged', 'pointer_committed'):
            with self.subTest(point=point):
                self.check_crash(point)

    def check_crash(self, point):
        base, location, _, result = prepared()
        command = next_command(result)
        write(base / 'request.json', command)
        process = start(base, point)
        stdout, stderr = process.communicate(timeout=15)
        self.assertEqual((73, '', ''), (process.returncode, stdout, stderr))
        original = read(location.root / 'history' / '000000000000000002.json')
        if point != 'pointer_committed':
            with self.assertRaisesRegex(OpsError, 'CONTROL_COMMAND_INCOMPLETE'):
                ControlStore(location).inspect(result['checkpoint'])
            with self.assertRaisesRegex(OpsError, 'CONTROL_COMMAND_INCOMPLETE'):
                ControlStore(location).apply(next_command(result), result['checkpoint'])
        recovered = ControlStore(location).apply(command, result['checkpoint'])
        self.assertEqual(original, recovered['record'])
        self.assertEqual(recovered, ControlStore(location).inspect(recovered['checkpoint']))
        self.assertEqual(2, len(list((location.root / 'history').iterdir())))

    def test_live_writer_excludes_reader_and_death_releases_os_lock(self):
        base, location, _, result = prepared()
        process = start(base, 'lock')
        try:
            self.assertEqual('LOCKED', ready(process))
            with self.assertRaisesRegex(OpsError, 'CONTROL_COORDINATOR_BUSY'):
                ControlStore(location).inspect(result['checkpoint'])
        finally:
            process.kill()
            process.communicate(timeout=15)
        self.assertEqual(result, ControlStore(location).inspect(result['checkpoint']))
        self.assertTrue((location.root / 'coordinator.lock').exists())

    def test_two_process_cas_race_has_one_winner(self):
        base, location, _, result = prepared()
        write(base / 'first.json', next_command(result))
        write(base / 'second.json', next_command(result))
        children = [start(base, 'race', name) for name in ('first.json', 'second.json')]
        try:
            for child in children:
                self.assertEqual('READY', ready(child))
            with ThreadPoolExecutor(max_workers=2) as pool:
                outputs = list(pool.map(lambda child: child.communicate('\n', timeout=15), children))
            self.assertEqual([0, 2], sorted(child.returncode for child in children))
            failure = outputs[[child.returncode for child in children].index(2)][0]
            self.assertIn(failure.strip(), ('CONTROL_CAS_CONFLICT', 'CONTROL_COORDINATOR_BUSY'))
            latest = ControlStore(location).inspect(result['checkpoint'])
            self.assertEqual('2', latest['record']['revision'])
        finally:
            for child in children:
                if child.poll() is None:
                    child.kill()
                    child.communicate(timeout=15)

    def test_initial_command_recovers_bootstrap_and_first_append_crash(self):
        for point in ('bootstrap_complete', 'history_appended', 'pointer_staged'):
            with self.subTest(point=point):
                base, location, initial = fixture()
                write(base / 'process.json', {'root': str(location.root),
                      'installation': location.installation_id, 'restore': [str(p) for p in location.restore_roots]})
                write(base / 'init.json', initial)
                process = start(base, point, 'init.json')
                stdout, stderr = process.communicate(timeout=15)
                self.assertEqual((73, '', ''), (process.returncode, stdout, stderr))
                with self.assertRaises(OpsError):
                    ControlStore(location).inspect({})
                result = ControlStore(location).initialize(initial)
                self.assertEqual(initial, result['record']['command'])
                self.assertEqual(result, ControlStore(location).initialize(initial))
                self.assertEqual(1, len(list((location.root / 'history').iterdir())))


if __name__ == '__main__':
    unittest.main()
