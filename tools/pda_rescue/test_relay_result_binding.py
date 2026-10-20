"""A downloaded result must match the process response before publication."""
import copy
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from uuid import uuid4
from unittest.mock import patch

from .relay import transfer
from .relay_journal import RelayJournal


class ResultWire:
    def __init__(self, journal):
        self.reception = {'relayId': str(uuid4()), 'exportId': str(uuid4()),
                          'archiveSha256': journal.state['input']['archiveSha256'],
                          'submissionId': str(uuid4()), 'sourceTrust': 'TRUSTED'}
        self.result = {**self.reception, 'resultId': str(uuid4()), 'localDeletionAllowed': False,
                       'businessReceipt': {'status': 'SUCCEEDED', 'actualWeightGrams': '605000'},
                       'signedRelayReceipt': 'synthetic-placeholder-not-a-signature'}
        self.download = copy.deepcopy(self.result)
        self.raw = None
        self.process_keys = []

    def data(self, path, body=None, key=None):
        if not path:
            return copy.deepcopy(self.reception)
        if path.endswith('/submission'):
            return {'state': 'SUCCEEDED'}
        self.process_keys.append(key)
        return copy.deepcopy(self.result)

    def relay(self, path):
        return self.raw if self.raw is not None else json.dumps(self.download).encode()


class RelayResultBindingTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory(prefix='relay-result-binding-')
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.context = {'server': 'http://127.0.0.1:19434', 'warehouse': str(uuid4())}
        self.journal = RelayJournal(self.root, b'synthetic-original', self.context)
        self.wire = ResultWire(self.journal)

    def test_matching_result_preserves_exact_download_bytes(self):
        self.wire.raw = json.dumps(self.wire.result, indent=2).encode() + b'\n'
        result = transfer(self.wire, self.journal, True)
        self.assertEqual('SUCCEEDED', result['status'])
        self.assertEqual(self.wire.raw, Path(result['resultFile']).read_bytes())
        self.assertIs(result['localDeletionAllowed'], False)

    def test_different_result_id_is_not_published(self):
        self.wire.download['resultId'] = str(uuid4())
        self.assert_refused()

    def test_different_business_status_is_not_published(self):
        self.wire.download['businessReceipt']['status'] = 'REVIEW_REQUIRED'
        self.assert_refused()

    def test_same_status_with_different_business_content_is_not_published(self):
        self.wire.download['businessReceipt']['actualWeightGrams'] = '1'
        self.assert_refused()

    def test_numeric_false_cannot_replace_deletion_boolean(self):
        self.wire.download['localDeletionAllowed'] = 0
        self.assert_refused()

    def test_changed_signature_or_unknown_field_is_not_published(self):
        for field in ['signedRelayReceipt', 'unexpected']:
            with self.subTest(field=field):
                self.wire.download = copy.deepcopy(self.wire.result)
                self.wire.download[field] = 'changed'
                self.assert_refused()

    def test_duplicate_json_properties_are_not_published(self):
        raw = json.dumps(self.wire.result).encode()
        self.wire.raw = raw.replace(b'{', b'{"localDeletionAllowed":false,', 1)
        self.assert_refused()

    def test_conflict_is_retained_without_advancing_original_keys(self):
        self.wire.download['businessReceipt']['status'] = 'REVIEW_REQUIRED'
        self.assert_refused()
        raw = self.wire.relay('')
        path = self.root / ('result-conflict-' + hashlib.sha256(raw).hexdigest() + '.bin')
        self.assertEqual(raw, path.read_bytes())
        self.wire.download = copy.deepcopy(self.wire.result)
        reopened = RelayJournal(self.root, b'synthetic-original', self.context)
        result = transfer(self.wire, reopened, True)
        self.assertEqual('SUCCEEDED', result['status'])
        self.assertEqual(1, len(set(self.wire.process_keys)))
        self.assertEqual(raw, path.read_bytes())

    def test_conflict_evidence_write_failure_does_not_publish_or_advance(self):
        transfer(self.wire, self.journal)
        before = (self.root / 'request.json').read_bytes()
        self.wire.download['resultId'] = str(uuid4())
        with patch('tools.pda_rescue.relay_journal.os.fsync', side_effect=OSError('synthetic disk failure')):
            with self.assertRaises(OSError):
                transfer(self.wire, self.journal, True)
        self.assertFalse(list(self.root.glob('*.pda-receipt.json')))
        self.assertEqual(before, (self.root / 'request.json').read_bytes())

    def test_existing_conflict_file_cannot_be_overwritten(self):
        self.wire.download['resultId'] = str(uuid4())
        raw = self.wire.relay('')
        path = self.root / ('result-conflict-' + hashlib.sha256(raw).hexdigest() + '.bin')
        path.write_bytes(b'earlier-evidence')
        self.assert_refused()
        self.assertEqual(b'earlier-evidence', path.read_bytes())

    def test_refresh_conflict_retains_pending_refresh_key_after_restart(self):
        self.journal.state['lastResultState'] = 'REVIEW_REQUIRED'
        self.journal.save()
        self.wire.download['resultId'] = str(uuid4())
        with self.assertRaises(ValueError):
            transfer(self.wire, self.journal, True)
        refresh_key = self.journal.state['processKey']
        reopened = RelayJournal(self.root, b'synthetic-original', self.context)
        self.assertTrue(reopened.state['pendingRefresh'])
        self.assertEqual('REVIEW_REQUIRED', reopened.state['lastResultState'])
        self.wire.download = copy.deepcopy(self.wire.result)
        transfer(self.wire, reopened, True)
        self.assertEqual([refresh_key, refresh_key], self.wire.process_keys)
        self.assertFalse(reopened.state['pendingRefresh'])

    def assert_refused(self):
        keys = [self.journal.state[name] for name in ['receiveKey', 'processKey']]
        with self.assertRaises(ValueError):
            transfer(self.wire, self.journal, True)
        reopened = RelayJournal(self.root, b'synthetic-original', self.context)
        self.assertEqual(keys, [reopened.state[name] for name in ['receiveKey', 'processKey']])
        self.assertNotIn('lastResultState', reopened.state)
        self.assertFalse(list(self.root.glob('*.pda-receipt.json')))
        self.assertEqual(b'synthetic-original', (self.root / 'original.zip').read_bytes())


if __name__ == '__main__':
    unittest.main()
