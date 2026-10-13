"""Independent Python consumer of the Android signed closure vector and semantic refusals."""
import copy
import os
import unittest
from pathlib import Path
from cryptography.exceptions import InvalidSignature
from .integrity import parse
from .verify import verify
from .independent import verify as verify_independent


class IndependentClosureTest(unittest.TestCase):
    def setUp(self):
        directory = Path(os.environ['INDEPENDENT_CLOSURE_VECTOR_DIR'])
        self.result = verify((directory / 'closed.zip').read_bytes(), parse((directory / 'trust.json').read_bytes()))

    def test_complete_device_boundary_keeps_pending_business_state(self):
        value = self.result['facts']['independent']
        self.assertEqual('2', value['closure']['inputHighWater'])
        self.assertEqual('1', value['closure']['void'])
        self.assertTrue(value['pendingReconciliation'])
        self.assertFalse(value['closure']['businessProcessed'])
        self.assertFalse(value['closure']['localDeletionAllowed'])

    def test_original_prefix_cannot_change_after_closure(self):
        mutations = [lambda f: f['captures'][0].update(rawInput='changed'),
                     lambda f: f['lines'][0].update(plannedCount='999'),
                     lambda f: f['events'][0].update(detail='{}'),
                     lambda f: f['events'].pop(),
                     lambda f: f['captures'][0].update(state='WAITING_CONFIRM'),
                     lambda f: f['order'].update(state='SCANNING')]
        for edit in mutations:
            with self.subTest(edit=edit), self.assertRaises((ValueError, InvalidSignature)):
                result = copy.deepcopy(self.result)
                edit(result['facts'])
                verify_independent(result['files'], result['facts'], result['manifest'])

    def test_modified_end_signature_is_rejected(self):
        result = copy.deepcopy(self.result)
        event = result['facts']['events'][-1]; envelope = parse(event['detail'].encode())
        envelope['signature'] = 'AAAA'
        import json
        event['detail'] = json.dumps(envelope)
        with self.assertRaises((ValueError, InvalidSignature)):
            verify_independent(result['files'], result['facts'], result['manifest'])
