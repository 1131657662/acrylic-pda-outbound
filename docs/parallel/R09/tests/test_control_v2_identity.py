"""Read v2 history without granting its identity any closed-control write purpose."""
import json
from pathlib import Path
import unittest
from control_factory import next_command, write, initialized
from control_v2_factory import promoted, rewrite, files
from control_store import ControlStore
from test_control_cli import invoke
from safety import OpsError


class ControlV2IdentityTests(unittest.TestCase):
    def test_v2_history_read_survives_restart_with_original_bytes(self):
        _, location, _, expected = promoted()
        before = files(location.root)
        actual = ControlStore(location).inspect(expected['checkpoint'])
        self.assertEqual(expected, actual)
        self.assertFalse(actual['ordinaryWritesAllowed'])
        self.assertFalse(actual['localDeletionAllowed'])
        self.assertFalse(actual['sharedGateIntegrated'])
        self.assertEqual(before, files(location.root))

    def test_v1_read_and_closed_control_write_remain_supported(self):
        _, location, _, original = initialized()
        result = ControlStore(location).apply(next_command(original), original['checkpoint'])
        self.assertEqual(result, ControlStore(location).inspect(result['checkpoint']))
        self.assertFalse(result['ordinaryWritesAllowed'])

    def test_v2_init_replay_and_apply_refuse_without_any_write(self):
        _, location, initial, result = promoted()
        store = ControlStore(location)
        before = files(location.root)
        calls = (lambda: store.initialize(initial), lambda: store.apply(initial, result['checkpoint']),
                 lambda: store.apply(next_command(result), result['checkpoint']))
        for call in calls:
            with self.assertRaisesRegex(OpsError, 'CONTROL_AUTHORITY_PURPOSE_FORBIDDEN'):
                call()
            self.assertEqual(before, files(location.root))

    def refused(self, change):
        _, location, _, result = promoted()
        identity = json.loads((location.root / 'identity.json').read_bytes())
        change(identity)
        rewrite(location, identity)  # Attacker recalculating an untrusted descriptor gains no purpose.
        before = files(location.root)
        with self.assertRaises(OpsError):
            ControlStore(location).inspect(result['checkpoint'])
        self.assertEqual(before, files(location.root))

    def test_unknown_or_noninteger_versions_refuse(self):
        for version in (True, 2.0, '2', 0, 3):
            with self.subTest(version=version):
                self.refused(lambda value: value.update(formatVersion=version))

    def test_wrong_or_absent_kind_is_not_downgraded(self):
        for kind in ('CLOSED_RECOVERY_SESSION', 'MODEL', '', None):
            with self.subTest(kind=kind):
                self.refused(lambda value: value.update(authorityKind=kind))
        self.refused(lambda value: value.pop('authorityKind'))

    def test_exact_field_set_is_required(self):
        self.refused(lambda value: value.update(allowWrites=True))
        for key in ('leaseRoot', 'closedAuthority', 'initialCommandSha256'):
            with self.subTest(missing=key):
                self.refused(lambda value: value.pop(key))

    def test_bound_installation_and_restore_set_are_required(self):
        self.refused(lambda value: value.update(installationId='00000000-0000-0000-0000-000000000001'))
        self.refused(lambda value: value.update(restoreRoots=[]))
        self.refused(lambda value: value['restoreRoots'].append(value['restoreRoots'][0]))

    def test_initial_hash_is_nonzero_and_bootstrap_bound(self):
        for digest in ('0' * 64, 'a' * 64, 'x' * 64, None):
            with self.subTest(digest=digest):
                self.refused(lambda value: value.update(initialCommandSha256=digest))

    def test_external_paths_are_absolute_canonical_and_distinct(self):
        for path in ('', 'relative/p5', None, 1):
            with self.subTest(path=path):
                self.refused(lambda value: value.update(leaseRoot=path))
        self.refused(lambda value: value.update(leaseRoot=value['closedAuthority']))
        self.refused(lambda value: value.update(closedAuthority=value['closedAuthority'] + '/..'))

    def test_paths_do_not_overlap_control_restore_or_each_other(self):
        for field in ('leaseRoot', 'closedAuthority'):
            for relative in ('authority', 'authority/nested', 'business', '.'):
                with self.subTest(field=field, path=relative):
                    self.refused(lambda value: value.update({field: str(Path(value['leaseRoot']).parent / relative)}))
        self.refused(lambda value: value.update(leaseRoot=str(Path(value['closedAuthority']) / 'nested')))

    def test_original_writer_descriptor_still_binds_exact_identity(self):
        _, location, _, result = promoted()
        value = json.loads((location.root / 'identity.json').read_bytes())
        value['closedAuthority'] += '-changed'
        write(location.root / 'identity.json', value)
        before = files(location.root)
        with self.assertRaisesRegex(OpsError, 'CONTROL_WRITER_PROTOCOL_MISMATCH'):
            ControlStore(location).inspect(result['checkpoint'])
        self.assertEqual(before, files(location.root))

    def test_bad_checkpoint_and_changed_history_still_refuse(self):
        _, location, _, result = promoted()
        checkpoint = {**result['checkpoint'], 'recordSha256': 'f' * 64}
        before = files(location.root)
        with self.assertRaises(OpsError):
            ControlStore(location).inspect(checkpoint)
        self.assertEqual(before, files(location.root))
        record = next((location.root / 'history').iterdir())
        value = json.loads(record.read_bytes()); value['phase'] = 'NORMAL'
        write(record, value)
        before = files(location.root)
        with self.assertRaises(OpsError):
            ControlStore(location).inspect(result['checkpoint'])
        self.assertEqual(before, files(location.root))

    def test_actual_cli_reads_v2_and_refuses_mutation_with_redacted_output(self):
        base, location, _, result = promoted()
        checkpoint = write(base / 'checkpoint.json', result['checkpoint'])
        before = files(location.root)
        actual = invoke(location, 'control-read', ('--checkpoint', str(checkpoint)))
        self.assertEqual(0, actual.returncode, actual.stdout)
        self.assertEqual(result, json.loads(actual.stdout))
        request = write(base / 'next.json', next_command(result))
        denied = invoke(location, 'control-apply', ('--checkpoint', str(checkpoint), '--request', str(request)))
        self.assertEqual(2, denied.returncode)
        self.assertEqual('CONTROL_AUTHORITY_PURPOSE_FORBIDDEN', json.loads(denied.stdout)['code'])
        self.assertNotIn(str(base), denied.stdout)
        self.assertEqual('', denied.stderr)
        self.assertEqual(before, files(location.root))


if __name__ == '__main__':
    unittest.main()
