"""Reject malformed TEMP original facts even with a synthetic valid signature."""
import copy
import unittest

from .temporary_receipt import verify_temporary_receipt
from .temporary_receipt_test_factory import TemporaryReceiptFactory


class TemporaryReceiptBoundaryTest(unittest.TestCase):
    def setUp(self):
        self.factory = TemporaryReceiptFactory()

    def rejected(self):
        with self.assertRaises((ValueError, TypeError, KeyError)):
            verify_temporary_receipt(*self.factory.originals())

    def test_external_scope_ids_require_canonical_uuid(self):
        for name in ['submissionId', 'warehouseId', 'operatorId', 'orderId']:
            with self.subTest(name=name):
                value = self.factory.context[name]
                self.factory.context[name] = 'unverified-name'
                self.rejected()
                self.factory.context[name] = value

    def test_unknown_external_scope_fields_are_rejected(self):
        self.factory.context['allowRelease'] = True
        self.rejected()

    def test_external_payload_digest_cannot_be_changed(self):
        payload, response, context, key = self.factory.originals()
        context['payloadSha256'] = '0' * 64
        with self.assertRaises(ValueError):
            verify_temporary_receipt(payload, response, context, key)

    def test_capture_beyond_frozen_high_watermark_is_rejected(self):
        self.factory.body['captureHighWatermark'] = self.factory.receipt['captureHighWatermark'] = '0'
        self.rejected()

    def test_success_cannot_have_no_active_original_scans(self):
        self.factory.body['scans'][0]['status'] = 'VOID'
        self.rejected()

    def test_duplicate_plan_or_receipt_product_is_rejected(self):
        for rows in [self.factory.body['temporaryOrder']['lines'], self.factory.receipt['items']]:
            with self.subTest(rows='plan' if 'plannedCount' in rows[0] else 'receipt'):
                rows.append(copy.deepcopy(rows[0]))
                self.rejected()
                rows.pop()

    def test_plan_count_requires_positive_bounded_integer_text(self):
        for value in ['0', '-1', '01', True, str(2**63)]:
            with self.subTest(value=value):
                self.factory.body['temporaryOrder']['lines'][0]['plannedCount'] = value
                self.rejected()

    def test_duplicate_capture_or_scan_is_rejected(self):
        for name in ['captures', 'scans']:
            with self.subTest(name=name):
                self.factory.body[name].append(copy.deepcopy(self.factory.body[name][0]))
                self.rejected()
                self.factory.body[name].pop()

    def test_committed_capture_cannot_be_missing_its_saved_scan(self):
        capture = copy.deepcopy(self.factory.body['captures'][0])
        from .temporary_receipt_test_factory import identifier
        capture.update(id=identifier(20), orderScanSeq='2')
        self.factory.body['captures'].append(capture)
        self.factory.body['captureHighWatermark'] = self.factory.receipt['captureHighWatermark'] = '2'
        self.rejected()

    def test_capture_sequence_cannot_have_a_gap(self):
        self.factory.body['captures'][0]['orderScanSeq'] = '2'
        self.factory.body['scans'][0]['orderScanSeq'] = '2'
        self.factory.body['captureHighWatermark'] = self.factory.receipt['captureHighWatermark'] = '2'
        self.factory.receipt['firstOrderScanSeq'] = self.factory.receipt['lastOrderScanSeq'] = '2'
        self.rejected()

    def test_nonterminal_capture_is_rejected(self):
        capture = copy.deepcopy(self.factory.body['captures'][0])
        from .temporary_receipt_test_factory import identifier
        capture.update(id=identifier(20), orderScanSeq='2', state='WAITING_CONFIRM')
        self.factory.body['captures'].append(capture)
        self.factory.body['captureHighWatermark'] = self.factory.receipt['captureHighWatermark'] = '2'
        self.rejected()

    def test_plan_limits_follow_original_temp_contract(self):
        self.factory.body['temporaryOrder']['lines'][0]['plannedCount'] = str(2**31)
        self.rejected()


if __name__ == '__main__':
    unittest.main()
