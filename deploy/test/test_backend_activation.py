#!/usr/bin/env python3
"""Exercise release rejection and fail-closed activation with command boundary stubs."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

from verify_backend_release import verify
from release_fixture import make_package, SCHEMA

STUBS = r'''
api_active=1
worker_active=1
systemctl() {
  printf '%s\n' "$*" >> "$ACTIVATION_TEST_LOG"
  case "$1 ${2:-}" in
    'is-active --quiet') [[ "$worker_active" == 1 ]]; return ;;
    'stop acrylic-outbound-test-worker.service') worker_active=0 ;;
    'stop acrylic-outbound-test.service') api_active=0 ;;
    'start acrylic-outbound-test.service') api_active=1 ;;
    'start acrylic-outbound-test-worker.service')
      [[ "$ACTIVATION_FAIL" != worker ]] || return 1
      worker_active=1 ;;
  esac
}
mysql() { printf '%s\n' "$ACTIVATION_SCHEMA"; }
curl() { [[ "$ACTIVATION_FAIL" != readiness ]]; }
install() { [[ "$ACTIVATION_FAIL" != install ]]; }
sleep() { :; }
id() { printf '0\n'; }
'''


class BackendActivationTest(unittest.TestCase):
    def setUp(self):
        self.scratch = tempfile.TemporaryDirectory(prefix='r05-activation-')
        self.addCleanup(self.scratch.cleanup)
        self.base = Path(self.scratch.name)
        self.release = self.base / 'releases' / 'candidate'
        self.release.mkdir(parents=True)
        self.log = self.base / 'events'
        self.stub = self.base / 'stubs.sh'
        self.stub.write_text(STUBS)
        self.make_package()
        (self.base / 'current').symlink_to('/previous-unmodified-release')

    def make_package(self):
        make_package(self.release, self.base)

    def activate(self, failure='', schema=None):
        env = dict(os.environ, BASH_ENV=str(self.stub), ACTIVATION_TEST_LOG=str(self.log),
                   ACTIVATION_FAIL=failure, ACTIVATION_SCHEMA=str(SCHEMA if schema is None else schema))
        result = subprocess.run(['bash', str(self.release / 'deploy/activate_backend.sh'), str(self.release)],
                                env=env, capture_output=True, text=True, timeout=15)
        self.events = self.log.read_text().splitlines() if self.log.exists() else []
        return result

    def assert_stopped(self, result):
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(self.events[-2:], ['stop acrylic-outbound-test-worker.service',
                                          'stop acrylic-outbound-test.service'])
        self.assertNotIn('enable --now acrylic-outbound-test-backup.timer', self.events)

    def test_valid_package_starts_api_then_worker(self):
        result = self.activate()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual((self.base / 'current').resolve(), self.release)
        self.assertLess(self.events.index('start acrylic-outbound-test.service'),
                        self.events.index('start acrylic-outbound-test-worker.service'))
        self.assertIn('enable --now acrylic-outbound-test-backup.timer', self.events)

    def test_corrupt_artifact_rejected_before_service_changes(self):
        (self.release / 'server.jar').write_bytes(b'corrupted')
        self.assertNotEqual(self.activate().returncode, 0)
        self.assertEqual(self.events, [])
        self.assertEqual(os.readlink(self.base / 'current'), '/previous-unmodified-release')

    def test_wrong_schema_rejected_before_service_changes(self):
        self.assertNotEqual(self.activate(schema=str(SCHEMA - 1)).returncode, 0)
        self.assertEqual(self.events, [])

    def test_unit_install_failure_leaves_writers_stopped(self):
        self.assert_stopped(self.activate(failure='install'))
        self.assertEqual(os.readlink(self.base / 'current'), '/previous-unmodified-release')

    def test_readiness_failure_stops_writers(self):
        self.assert_stopped(self.activate(failure='readiness'))
        self.assertNotIn('start acrylic-outbound-test-worker.service', self.events)

    def test_worker_failure_stops_both_writers(self):
        self.assert_stopped(self.activate(failure='worker'))

    def test_release_id_mismatch_is_rejected(self):
        manifest = self.release / 'manifest.json'
        values = json.loads(manifest.read_text())
        values['release'] = 'another-release'
        manifest.write_text(json.dumps(values))
        self.assertNotEqual(self.activate().returncode, 0)
        self.assertEqual(self.events, [])

    def test_linked_artifact_is_rejected(self):
        artifact = self.release / 'server.jar'
        original = self.base / 'outside.jar'
        shutil.move(artifact, original)
        artifact.symlink_to(original)
        self.assertRaises(ValueError, verify, self.release)


if __name__ == '__main__':
    unittest.main(verbosity=2)
