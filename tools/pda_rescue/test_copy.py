"""Encrypted snapshot retention and failure boundaries with current Android signed originals."""
import json
import os
import unittest
from unittest.mock import patch

from .copy import create, retrieve, write_new
from .copy_test_factory import fixture, PASSWORD, LABELS
from .copy_record import digest
from .integrity import parse


class EncryptedCopyTest(unittest.TestCase):
    def setUp(self):
        self.temporary, self.root, self.package, self.trust = fixture()
        self.addCleanup(self.temporary.cleanup)
        self.destination = self.root / 'copy'

    def create_copy(self):
        value = create((self.package, PASSWORD, self.trust), self.destination, LABELS)
        self.copy_hash = digest((self.destination / 'copy.json').read_bytes())
        return value

    def retrieve_copy(self, expected=None):
        return retrieve(self.destination, (PASSWORD, self.trust, expected or self.copy_hash))

    def test_roundtrip_preserves_encrypted_bytes_and_snapshot_without_authority(self):
        original = self.package.read_bytes()
        value = self.create_copy()
        report = self.retrieve_copy()
        self.assertEqual(original, (self.destination / 'original.pdarescue').read_bytes())
        self.assertEqual(value, report['copy'])
        self.assertEqual({'original.pdarescue', 'copy.json'}, {p.name for p in self.destination.iterdir()})
        self.assertFalse(report['externalStorageVerified'])
        self.assertFalse(report['localDeletionAllowed'])
        self.assertTrue(report['protectsExportedSnapshotOnly'])
        self.assertNotIn(PASSWORD, (self.destination / 'copy.json').read_text(encoding='utf-8'))

    def test_existing_destination_and_report_are_preserved(self):
        self.destination.mkdir()
        marker = self.destination / 'keep.txt'
        marker.write_bytes(b'earlier copy')
        with self.assertRaises(ValueError):
            self.create_copy()
        with self.assertRaises(FileExistsError):
            write_new(marker, b'replacement')
        self.assertEqual(b'earlier copy', marker.read_bytes())

    def test_wrong_password_and_changed_ciphertext_never_publish(self):
        with self.assertRaises(Exception):
            create((self.package, 'different-synthetic-passphrase', self.trust), self.destination, LABELS)
        data = self.package.read_bytes()
        self.package.write_bytes(data[:-1] + bytes([data[-1] ^ 1]))
        with self.assertRaises(Exception):
            self.create_copy()
        self.assertFalse(self.destination.exists())

    def test_changed_registration_scope_is_refused_before_copy(self):
        trusted = parse(self.trust.read_bytes())
        next(iter(trusted.values()))['operatorIds'] = ['foreign-operator']
        self.trust.write_text(json.dumps(trusted), encoding='utf-8')
        with self.assertRaises(ValueError):
            self.create_copy()
        self.assertFalse(self.destination.exists())

    def test_missing_custody_and_control_characters_are_refused(self):
        for labels in ({}, {'custodian': 'user', 'location': 'bad\nlocation'}, dict(LABELS, extra='unbound')):
            with self.subTest(labels=labels), self.assertRaises(ValueError):
                create((self.package, PASSWORD, self.trust), self.destination, labels)
        self.assertFalse(self.destination.exists())

    def test_interrupted_output_retains_partial_without_complete_record(self):
        original = self.package.read_bytes()
        with patch('tools.pda_rescue.copy.os.fsync', side_effect=OSError('synthetic disk failure')):
            with self.assertRaises(OSError):
                self.create_copy()
        partial = list(self.root.glob('pda-copy-partial-*'))
        self.assertEqual(1, len(partial))
        self.assertFalse((partial[0] / 'copy.json').exists())
        self.assertFalse(self.destination.exists())
        self.assertEqual(original, self.package.read_bytes())

    def test_changed_copy_bytes_are_refused_on_retrieval(self):
        self.create_copy()
        package = self.destination / 'original.pdarescue'
        package.write_bytes(package.read_bytes()[:-1])
        with self.assertRaises(Exception):
            self.retrieve_copy()

    def test_changed_snapshot_record_is_refused_on_retrieval(self):
        self.create_copy()
        path = self.destination / 'copy.json'
        value = parse(path.read_bytes())
        value['snapshot']['summary']['active'] = '999'
        path.write_text(json.dumps(value), encoding='utf-8')
        with self.assertRaisesRegex(ValueError, 'separately recorded SHA256'):
            self.retrieve_copy()
        with self.assertRaisesRegex(ValueError, 'differs from recorded snapshot'):
            self.retrieve_copy(digest(path.read_bytes()))

    def test_record_cannot_claim_external_storage_business_or_deletion(self):
        self.create_copy()
        path = self.destination / 'copy.json'
        original = parse(path.read_bytes())
        for field in ('externalStorageVerified', 'businessReceiptVerified', 'localDeletionAllowed'):
            value = dict(original, **{field: True})
            path.write_text(json.dumps(value), encoding='utf-8')
            with self.subTest(field=field), self.assertRaises(ValueError):
                self.retrieve_copy(digest(path.read_bytes()))

    def test_changed_custody_is_refused_with_original_separate_hash(self):
        self.create_copy()
        path = self.destination / 'copy.json'
        value = parse(path.read_bytes())
        value['custody']['custodian'] = 'changed unverified custodian'
        path.write_text(json.dumps(value), encoding='utf-8')
        with self.assertRaisesRegex(ValueError, 'separately recorded SHA256'):
            self.retrieve_copy()

    def test_unknown_record_format_is_refused_even_with_matching_hash(self):
        self.create_copy()
        path = self.destination / 'copy.json'
        value = dict(parse(path.read_bytes()), format='UNKNOWN')
        path.write_text(json.dumps(value), encoding='utf-8')
        with self.assertRaisesRegex(ValueError, 'Unknown copy record'):
            self.retrieve_copy(digest(path.read_bytes()))

    def test_missing_encrypted_copy_is_refused(self):
        self.create_copy()
        (self.destination / 'original.pdarescue').unlink()
        with self.assertRaisesRegex(ValueError, 'regular file'):
            self.retrieve_copy()

    def test_hardlinked_input_is_refused_before_copy(self):
        os.link(self.package, self.root / 'linked.pdarescue')
        with self.assertRaisesRegex(ValueError, 'regular file'):
            self.create_copy()
        self.assertFalse(self.destination.exists())

    def test_changed_registration_bytes_are_refused_on_retrieval(self):
        self.create_copy()
        self.trust.write_bytes(self.trust.read_bytes() + b' ')
        with self.assertRaisesRegex(ValueError, 'differs from recorded snapshot'):
            self.retrieve_copy()

    def test_bom_registration_is_verified_without_rewriting_original(self):
        trusted = b'\xef\xbb\xbf' + self.trust.read_bytes()
        self.trust.write_bytes(trusted)
        self.create_copy()
        self.assertTrue(self.retrieve_copy()['sourceTrusted'])
        self.assertEqual(trusted, self.trust.read_bytes())
