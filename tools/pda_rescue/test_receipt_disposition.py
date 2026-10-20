"""Effective totals preserve original disposed scans; synthetic signed facts only."""
import copy
import unittest

from .facts import PRODUCT_HEADERS, SCAN_FIELDS, SCAN_HEADERS, tsv
from .receipt import verify_formal_receipt
from .receipt_test_factory import ReceiptFactory, encode


class DisposedReceiptTest(unittest.TestCase):
    def setUp(self):
        self.factory = ReceiptFactory()

    def disposed_files(self):
        factory = self.factory
        capture = {**factory.body['captures'][0], 'id': 'disposed-capture', 'orderScanSeq': '2'}
        scan = {**factory.body['scans'][0], 'captureId': capture['id'], 'orderScanSeq': '2',
                'baleId': 'disposed-bale', 'barcode': 'disposed-barcode', 'localSeq': '2'}
        proof = {'captureId': capture['id'], 'approvedEvidenceId': 'synthetic-evidence', 'action': 'QUARANTINE'}
        factory.body['scans'].append(scan)
        factory.body['captures'].append(capture)
        factory.body['dispositions'].append(proof)
        factory.body['captureHighWatermark'] = '2'
        factory.receipt = factory.success()
        factory.receipt['captureHighWatermark'] = '2'
        files = factory.files()
        files['captures.ndjson'] = b''.join(encode(row) + b'\n' for row in factory.body['captures'])
        files['scans.ndjson'] = b''.join(encode(row) + b'\n' for row in factory.body['scans'])
        files['dispositions.ndjson'] = encode({'captureId': capture['id'], 'evidenceId': 'synthetic-evidence',
                                             'action': 'QUARANTINE', 'proofJson': encode(proof).decode()}) + b'\n'
        files['scan-details.tsv'] = tsv(SCAN_HEADERS, [[row[field] for field in SCAN_FIELDS] +
            ['QUARANTINE' if row['captureId'] == capture['id'] else ''] for row in factory.body['scans']])
        files['product-summary.tsv'] = tsv(PRODUCT_HEADERS, [['003', '1', '1', '0', '1', '0', '1234']])
        return files

    def test_original_disposed_scan_is_retained_without_effective_contribution(self):
        plain, trust = self.factory.package(self.disposed_files())
        result = verify_formal_receipt(plain, trust, self.factory.key)
        self.assertTrue(result['businessReceiptVerified'])
        self.assertEqual(('2', '1', '1', '1234'), tuple(result['summary'][field] for field in
                         ['scans', 'active', 'disposed', 'weightGrams']))

    def test_changed_original_disposition_proof_is_rejected(self):
        files = self.disposed_files()
        files['dispositions.ndjson'] = files['dispositions.ndjson'].replace(b'synthetic-evidence', b'changed-evidence')
        plain, trust = self.factory.package(files)
        with self.assertRaises(ValueError):
            verify_formal_receipt(plain, trust, self.factory.key)

    def test_signed_zero_contribution_product_does_not_add_inventory(self):
        self.factory.receipt['items'].append({'deliveryCode': 'zero-product', 'actualCount': '0', 'actualWeightGrams': '0'})
        plain, trust = self.factory.package()
        result = verify_formal_receipt(plain, trust, self.factory.key)
        self.assertTrue(result['businessReceiptVerified'])
        self.assertEqual('1234', result['summary']['weightGrams'])


if __name__ == '__main__':
    unittest.main()
