"""Retained V34 references are checked without claiming device provenance."""
import json
from pathlib import Path
import sys
import unittest
from backup_factory import test_directory
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'ops'))
from safety import OpsError, digest
from v34_evidence import verify_evidence

SCOPE, SUBMISSION = 'A' * 32, 'B' * 32
STOP, CAPTURE, SET = 'C' * 32, 'D' * 32, 'E' * 32


class Rows:
    def __init__(self, tables):
        self.tables = tables

    def query(self, sql):
        table = next(name for name in self.tables if 'FROM ' + name in sql)
        return '\n'.join(json.dumps(row) for row in self.tables[table])


def fixture():
    files = test_directory() / 'files'
    files.mkdir()
    stop, capture = b'synthetic STOP, not a device proof', b'synthetic capture, not a device record'
    (files / 'stop.bin').write_bytes(stop)
    (files / 'capture.bin').write_bytes(capture)
    tables = {
        'source_evidence_object': [
            [STOP, SCOPE, SUBMISSION, 1, 'STOP_PROOF', '1' * 32, digest(stop), len(stop), 'stop.bin'],
            [CAPTURE, SCOPE, SUBMISSION, 1, 'CAPTURE_RECORDS', '2' * 32, digest(capture), len(capture), 'capture.bin']],
        'source_evidence_set': [[SET, SCOPE, SUBMISSION, 1, STOP, 'a' * 64, 1, '7B7D']],
        'source_evidence_item': [[SET, CAPTURE, 'CAPTURE', 'b' * 64]],
        'source_evidence_verification': [[SET, 'a' * 64, 'PARTIAL', 'c' * 64, '7B7D']]}
    return files, tables


class EvidenceTests(unittest.TestCase):
    def test_retained_synthetic_rows_check_files_and_references(self):
        files, tables = fixture()
        result = verify_evidence(Rows(tables), files)
        self.assertEqual(result['evidence_object_files_checked'], 2)
        self.assertEqual(result['evidence_items_checked'], 1)
        self.assertIn('not trusted VERIFY', result['evidence_scope'])

    def test_empty_evidence_is_reported_as_empty_not_complete(self):
        files, tables = fixture()
        result = verify_evidence(Rows({name: [] for name in tables}), files)
        self.assertEqual(result['evidence_sets_checked'], 0)
        self.assertEqual(result['evidence_verification_rows_checked'], 0)

    def test_corrupt_file_is_rejected(self):
        files, tables = fixture()
        (files / 'stop.bin').write_bytes(b'X' + (files / 'stop.bin').read_bytes()[1:])
        with self.assertRaisesRegex(OpsError, 'EVIDENCE_FILE_HASH_MISMATCH'):
            verify_evidence(Rows(tables), files)

    def test_wrong_size_is_rejected(self):
        files, tables = fixture()
        tables['source_evidence_object'][0][7] += 1
        with self.assertRaisesRegex(OpsError, 'EVIDENCE_FILE_SIZE_MISMATCH'):
            verify_evidence(Rows(tables), files)

    def test_missing_file_is_rejected(self):
        files, tables = fixture()
        tables['source_evidence_object'][0][8] = 'missing.bin'
        with self.assertRaisesRegex(OpsError, 'REGULAR_FILE_REQUIRED'):
            verify_evidence(Rows(tables), files)

    def test_escaping_file_key_is_rejected(self):
        files, tables = fixture()
        tables['source_evidence_object'][0][8] = '../outside.bin'
        with self.assertRaisesRegex(OpsError, 'UNSAFE_PATH'):
            verify_evidence(Rows(tables), files)

    def test_stop_reference_requires_stop_kind(self):
        files, tables = fixture()
        tables['source_evidence_set'][0][4] = CAPTURE
        with self.assertRaisesRegex(OpsError, 'EVIDENCE_STOP_REFERENCE_MISMATCH'):
            verify_evidence(Rows(tables), files)

    def test_item_count_drift_is_rejected(self):
        files, tables = fixture()
        tables['source_evidence_set'][0][6] = 2
        with self.assertRaisesRegex(OpsError, 'EVIDENCE_ITEM_COUNT_MISMATCH'):
            verify_evidence(Rows(tables), files)

    def test_cross_context_item_is_rejected(self):
        files, tables = fixture()
        tables['source_evidence_object'][1][1] = 'F' * 32
        with self.assertRaisesRegex(OpsError, 'EVIDENCE_ITEM_REFERENCE_MISMATCH'):
            verify_evidence(Rows(tables), files)

    def test_verification_set_hash_drift_is_rejected(self):
        files, tables = fixture()
        tables['source_evidence_set'][0][5] = 'f' * 64
        with self.assertRaisesRegex(OpsError, 'EVIDENCE_VERIFICATION_REFERENCE_MISMATCH'):
            verify_evidence(Rows(tables), files)

    def test_same_file_key_with_conflicting_digest_is_rejected(self):
        files, tables = fixture()
        tables['source_evidence_object'][1][8] = 'stop.bin'
        with self.assertRaisesRegex(OpsError, 'EVIDENCE_FILE_KEY_CONFLICT'):
            verify_evidence(Rows(tables), files)
