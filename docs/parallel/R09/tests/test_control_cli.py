"""Actual operator CLI maintains canonical request recovery and redacted errors."""
import json
from pathlib import Path
import subprocess
import sys
import unittest
from control_factory import fixture, next_command, write

CLI = Path(__file__).resolve().parents[1] / 'ops' / 'cli.py'


def invoke(location, operation, extra=()):
    args = [sys.executable, str(CLI), operation, '--control-root', str(location.root),
            '--installation-id', location.installation_id, '--restore-root', str(location.restore_roots[0]), *extra]
    return subprocess.run(args, capture_output=True, text=True, timeout=15)


class ControlCliTests(unittest.TestCase):
    def test_init_read_apply_and_lost_response_replay(self):
        base, location, initial = fixture()
        path = write(base / 'init.json', initial)
        first = invoke(location, 'control-init', ('--request', str(path)))
        self.assertEqual(0, first.returncode, first.stdout)
        original = json.loads(first.stdout)
        trusted = write(base / 'trusted.json', original['checkpoint'])
        path = write(base / 'next.json', next_command(original))
        options = ('--request', str(path), '--checkpoint', str(trusted))
        changed = invoke(location, 'control-apply', options)
        replay = invoke(location, 'control-apply', options)
        self.assertEqual((0, changed.stdout), (replay.returncode, replay.stdout))
        current = invoke(location, 'control-read', ('--checkpoint', str(trusted)))
        self.assertEqual((0, changed.stdout), (current.returncode, current.stdout))
        self.assertFalse(json.loads(current.stdout)['ordinaryWritesAllowed'])

    def test_errors_do_not_print_private_paths_or_traceback(self):
        base, location, initial = fixture()
        path = base / 'private-secret.json'
        path.write_text('{', encoding='utf-8')
        result = invoke(location, 'control-init', ('--request', str(path)))
        self.assertEqual(2, result.returncode)
        self.assertEqual('CONTROL_JSON_INVALID', json.loads(result.stdout)['code'])
        self.assertNotIn('private-secret', result.stdout)
        self.assertEqual('', result.stderr)

    def test_checkpoint_inside_restore_set_is_refused(self):
        base, location, initial = fixture()
        request = write(base / 'init.json', initial)
        result = invoke(location, 'control-init', ('--request', str(request)))
        trusted = json.loads(result.stdout)['checkpoint']
        business = location.restore_roots[0]
        business.mkdir()
        path = write(business / 'checkpoint.json', trusted)
        result = invoke(location, 'control-read', ('--checkpoint', str(path)))
        self.assertEqual(2, result.returncode)
        self.assertEqual('CONTROL_CHECKPOINT_NOT_EXTERNAL', json.loads(result.stdout)['code'])


if __name__ == '__main__':
    unittest.main()
