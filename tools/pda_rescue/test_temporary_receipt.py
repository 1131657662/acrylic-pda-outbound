"""TEMP original-file trust boundaries, independent of a rescue package."""
import copy
import unittest

from .receipt_test_factory import encode
from .temporary_receipt import verify_temporary_receipt
from .temporary_receipt_test_factory import TemporaryReceiptFactory, identifier


class TemporaryReceiptTest(unittest.TestCase):
    def setUp(self):
        self.factory = TemporaryReceiptFactory()

    def check_rejected(self):
        with self.assertRaises((ValueError, KeyError, TypeError)):
            verify_temporary_receipt(*self.factory.originals())

    def test_first_round_success_reports_historical_scope_only(self):
        inputs = self.factory.originals()
        report = verify_temporary_receipt(*inputs)
        self.assertTrue(report['businessReceiptVerified'])
        self.assertEqual('HISTORICAL_TEMP_FIRST_ROUND_SUCCESS_RECEIPT', report['verificationScope'])
        for name in ['currentBusinessStateVerified', 'releaseAllowed', 'localDeletionAllowed', 'packageIntegrityVerified']:
            self.assertIs(report[name], False)
        self.assertEqual(inputs[2]['warehouseId'], report['externalContext']['warehouseId'])

    def test_void_record_does_not_contribute_to_success_totals(self):
        scan = copy.deepcopy(self.factory.body['scans'][0])
        scan.update(captureId=identifier(10), orderScanSeq='2', localSeq='2', status='VOID')
        capture = copy.deepcopy(self.factory.body['captures'][0])
        capture.update(id=identifier(10), orderScanSeq='2')
        self.factory.body['scans'].append(scan)
        self.factory.body['captures'].append(capture)
        self.factory.body['captureHighWatermark'] = self.factory.receipt['captureHighWatermark'] = '2'
        self.assertTrue(verify_temporary_receipt(*self.factory.originals())['businessReceiptVerified'])

    def test_missing_external_context_is_rejected(self):
        payload, response, _, key = self.factory.originals()
        with self.assertRaises(ValueError):
            verify_temporary_receipt(payload, response, None, key)

    def test_wrong_external_target_is_rejected(self):
        self.factory.context['orderId'] = identifier(11)
        self.check_rejected()

    def test_zero_contribution_product_cannot_be_omitted(self):
        self.factory.receipt['items'].pop()
        self.check_rejected()

    def test_extra_zero_product_is_rejected(self):
        self.factory.receipt['items'].append({'deliveryCode': '888', 'actualCount': '0', 'actualWeightGrams': '0'})
        self.check_rejected()

    def test_signed_product_grams_must_equal_active_originals(self):
        self.factory.receipt['items'][0]['actualWeightGrams'] = '1235'
        self.check_rejected()

    def test_captures_must_use_original_operator_and_authorization(self):
        for field in ['operatorId', 'originDeviceId', 'authorizationId', 'clientOrderId']:
            with self.subTest(field=field):
                original = self.factory.body['captures'][0][field]
                self.factory.body['captures'][0][field] = identifier(12)
                self.check_rejected()
                self.factory.body['captures'][0][field] = original

    def test_scan_cannot_reference_an_absent_capture(self):
        self.factory.body['scans'][0]['captureId'] = identifier(12)
        self.check_rejected()

    def test_scan_cannot_change_capture_barcode_or_cycle(self):
        for field, value in [('barcode', 'other'), ('expectedDispatchCycle', '2')]:
            with self.subTest(field=field):
                original = self.factory.body['scans'][0][field]
                self.factory.body['scans'][0][field] = value
                self.check_rejected()
                self.factory.body['scans'][0][field] = original

    def test_multiround_and_cancelled_handover_inputs_are_rejected(self):
        self.factory.body['workRound'] = self.factory.receipt['workRound'] = '2'
        self.check_rejected()
        self.factory.body['workRound'] = self.factory.receipt['workRound'] = '1'
        self.factory.body['temporaryHandover'] = {}
        self.check_rejected()

    def test_unknown_scan_status_is_rejected(self):
        self.factory.body['scans'][0]['status'] = 'RETAINED'
        self.check_rejected()

    def test_false_is_not_an_integer_receipt_version(self):
        self.factory.receipt['receiptVersion'] = True
        self.check_rejected()

    def test_success_cannot_open_deletion(self):
        self.factory.receipt['localDeletionAllowed'] = True
        self.check_rejected()

    def test_non_success_response_is_rejected(self):
        self.factory.receipt['status'] = 'REVIEW_REQUIRED'
        self.check_rejected()

    def test_oversized_product_aggregate_is_rejected(self):
        self.factory.body['scans'][0]['weightGrams'] = str(2**63)
        self.check_rejected()

    def test_changed_frozen_bytes_are_rejected(self):
        payload, response, context, key = self.factory.originals()
        with self.assertRaises(ValueError):
            verify_temporary_receipt(payload + b' ', response, context, key)

    def test_outer_response_cannot_override_signed_fields(self):
        import json
        payload, raw, context, key = self.factory.originals()
        response = json.loads(raw)
        response['data']['orderId'] = identifier(11)
        with self.assertRaises(ValueError):
            verify_temporary_receipt(payload, encode(response), context, key)


if __name__ == '__main__':
    unittest.main()
