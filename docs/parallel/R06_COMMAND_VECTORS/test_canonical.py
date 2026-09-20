"""Fixed expected bytes must agree with independent Python and actual Java implementations."""
import json
import unittest
from canonical import canonical, digest, parse
from fixtures import ROOT, load
from java_oracle import invoke, settings


class CanonicalVectors(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        output = invoke('canonical', ROOT / 'canonical_vectors.json', settings())
        cls.java = {item['id']: item for item in json.loads(output)}


def canonical_case(sample):
    def test(self):
        java = self.java[sample['id']]
        if sample.get('reject'):
            with self.assertRaises(ValueError):
                parse(sample['raw'])
            self.assertTrue(java.get('reject'), java)
            self.assertEqual('BusinessException', java['exception'])
            return
        value = parse(sample['raw'])
        self.assertEqual(sample['canonical'].encode('utf-8'), canonical(value))
        self.assertEqual(sample['sha256'], digest(value))
        self.assertEqual(sample['canonical'], java['canonical'])
        self.assertEqual(sample['sha256'], java['sha256'])
    return test


for sample in load('canonical_vectors.json'):
    setattr(CanonicalVectors, 'test_' + sample['id'].replace('-', '_'), canonical_case(sample))


class HashDomains(unittest.TestCase):
    def test_command_matches_independently_frozen_bytes(self):
        data = load('command_vectors.json')
        self.assertEqual(data['commandCanonical'].encode('utf-8'), canonical(data['command']))
        self.assertEqual(data['commandSha256'], digest(data['command'], 'command'))

    def test_receipt_matches_independently_frozen_bytes(self):
        data = load('receipt_vectors.json')
        self.assertEqual(data['receiptCanonical'].encode('utf-8'), canonical(data['originalCommandReceipt']))
        self.assertEqual(data['receiptSha256'], digest(data['originalCommandReceipt'], 'receipt'))

    def test_hash_domains_do_not_alias_for_same_bytes(self):
        data = load('command_vectors.json')
        hashes = [digest(data['command'], 'command')]
        for domain, expected in data['sameBytesOtherDomains'].items():
            self.assertEqual(expected, digest(data['command'], domain))
            hashes.append(expected)
        self.assertEqual(4, len(set(hashes)))

    def test_array_order_changes_evidence(self):
        data = {row['id']: row for row in load('canonical_vectors.json')}
        self.assertNotEqual(data['nested-and-array']['sha256'], data['array-reordered']['sha256'])

    def test_derived_control_hash_is_explicit_and_not_payload_hash(self):
        data = load('command_vectors.json')['derivedControl']
        self.assertEqual(data['canonical'].encode('utf-8'), canonical(data['value']))
        self.assertEqual(data['sha256'], digest(data['value'], 'control'))
        self.assertNotEqual(data['sha256'], digest(data['value']))

    def test_nullable_command_has_its_own_fixed_hash(self):
        data = load('command_vectors.json')
        self.assertEqual(data['nullableCommandSha256'], digest(data['nullableCommand'], 'command'))
        self.assertNotEqual(data['commandSha256'], data['nullableCommandSha256'])
