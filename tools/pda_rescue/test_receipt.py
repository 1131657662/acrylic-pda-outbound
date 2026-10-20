"""Offline original-receipt checks; all keys and facts here are synthetic."""
import copy
import unittest

from cryptography.exceptions import InvalidSignature

from .receipt import verify_formal_receipt
from .receipt_test_factory import ReceiptFactory, encode
from .verify import verify


class FormalReceiptTest(unittest.TestCase):
    def setUp(self):
        self.factory = ReceiptFactory()

    def checked(self, files=None, key=None):
        plain, trust = self.factory.package(files)
        return verify_formal_receipt(plain, trust, key or self.factory.key)

    def test_successful_original_receipt_reports_historical_read_only_result(self):
        result = self.checked()
        self.assertTrue(result['businessReceiptVerified'])
        self.assertEqual('HISTORICAL_FORMAL_SUCCESS_RECEIPT', result['verificationScope'])
        for field in ['currentBusinessStateVerified', 'releaseAllowed', 'localDeletionAllowed']:
            self.assertIs(result[field], False)

    def test_default_package_verification_still_does_not_verify_business_receipt(self):
        plain, trust = self.factory.package()
        self.assertIs(verify(plain, trust)['businessReceiptVerified'], False)

    def test_absent_registration_trust_is_rejected(self):
        plain, _ = self.factory.package()
        with self.assertRaises(ValueError):
            verify_formal_receipt(plain, None, self.factory.key)

    def test_wrong_server_public_key_is_rejected(self):
        other = ReceiptFactory().key
        with self.assertRaises(InvalidSignature):
            self.checked(key=other)

    def test_token_header_and_claim_scope_are_pinned(self):
        for header in [{'alg': 'HS256', 'typ': 'JWT', 'kid': self.factory.key['keyId']},
                       {'alg': 'RS256', 'typ': 'JWT', 'kid': 'another-key'},
                       {'alg': 'RS256', 'typ': 'JWT', 'kid': self.factory.key['keyId'], 'jku': 'https://invalid.test/key'}]:
            with self.subTest(header=header), self.assertRaises(ValueError):
                self.checked(self.factory.files(self.factory.token(header=header)))
        for field in ['iss', 'aud']:
            claims = {'iss': 'acrylic-outbound', 'aud': 'acrylic-pda-receipt', 'receipt': self.factory.receipt}
            claims[field] = 'wrong-purpose'
            with self.subTest(field=field), self.assertRaises(ValueError):
                self.checked(self.factory.files(self.factory.token(claims=claims)))

    def test_unsigned_response_change_is_rejected(self):
        original = self.factory.token()
        self.factory.receipt['orderNo'] = 'changed-after-signature'
        with self.assertRaises(ValueError):
            self.checked(self.factory.files(original))

    def test_json_boolean_cannot_equal_numeric_flag(self):
        signed = copy.deepcopy(self.factory.receipt)
        signed['stockReleased'] = 0
        with self.assertRaises(ValueError):
            self.checked(self.factory.files(self.factory.token(receipt=signed)))

    def test_signed_context_mismatch_is_rejected(self):
        for field in ['submissionId', 'orderId', 'payloadSha256', 'freezeDeviceId', 'workRound',
                      'issuedRecoveryEpoch', 'captureHighWatermark', 'actualCount', 'actualWeightGrams',
                      'firstOrderScanSeq', 'lastOrderScanSeq', 'resultVersion']:
            original = self.factory.receipt[field]
            self.factory.receipt[field] = 'different'
            with self.subTest(field=field), self.assertRaises(ValueError):
                self.checked()
            self.factory.receipt[field] = original

    def test_signed_permissions_cannot_grant_deletion_or_scanning(self):
        for field in ['localDeletionAllowed', 'mayResumeScanning', 'stockReleased']:
            self.factory.receipt[field] = True
            with self.subTest(field=field), self.assertRaises(ValueError):
                self.checked()
            if field == 'localDeletionAllowed':
                del self.factory.receipt[field]
            else:
                self.factory.receipt[field] = False

    def test_signed_product_totals_and_duplicates_are_rejected(self):
        original = copy.deepcopy(self.factory.receipt['items'])
        for items in [[], original * 2, [{**original[0], 'actualWeightGrams': '1235'}],
                      [{**original[0], 'deliveryCode': 'another-product'}]]:
            self.factory.receipt['items'] = items
            with self.subTest(items=items), self.assertRaises(ValueError):
                self.checked()

    def test_duplicate_receipt_properties_are_rejected(self):
        files = self.factory.files()
        files['receipt.json'] = files['receipt.json'].replace(b'{', b'{"status":"SUCCEEDED",', 1)
        with self.assertRaises(ValueError):
            self.checked(files)

    def test_temporary_source_cannot_be_reported_as_formal_success(self):
        files = self.factory.files()
        files['order.json'] = files['order.json'].replace(b'FORMAL', b'TEMP')
        with self.assertRaises(ValueError):
            self.checked(files)

    def test_unfrozen_package_cannot_be_reported_as_business_receipt(self):
        files = {k: v for k, v in self.factory.files().items() if k not in
                 {'submission.json', 'submission-info.json', 'receipt.json'}}
        with self.assertRaises(ValueError):
            self.checked(files)

    def test_changed_frozen_payload_never_passes_receipt_digest(self):
        self.factory.body['captureHighWatermark'] = '2'
        with self.assertRaises(ValueError):
            self.checked()

    def test_unknown_receipt_status_is_rejected(self):
        self.factory.receipt['status'] = 'REVIEW_REQUIRED'
        with self.assertRaises(ValueError):
            self.checked()

    def test_signed_source_mismatch_is_rejected(self):
        self.factory.receipt['source']['sourceOrderId'] = 'other-order'
        with self.assertRaises(ValueError):
            self.checked()

    def test_signed_scan_projection_must_match_original_package(self):
        self.factory.body['scans'][0]['barcode'] = 'changed-frozen-barcode'
        self.factory.receipt = self.factory.success()
        files = self.factory.files()
        files['scans.ndjson'] = files['scans.ndjson'].replace(b'changed-frozen-barcode', b'synthetic-barcode')
        files['scan-details.tsv'] = files['scan-details.tsv'].replace(b'changed-frozen-barcode', b'synthetic-barcode')
        with self.assertRaises(ValueError):
            self.checked(files)

    def test_invalid_or_padded_token_never_verifies(self):
        parts = self.factory.token().split('.')
        for token in [parts[0] + '=.' + parts[1] + '.' + parts[2], 'invalid',
                      '.'.join([parts[0], parts[1], parts[2][:-4] + 'AAAA']), 'a' * 262145]:
            with self.subTest(tokenLength=len(token)), self.assertRaises((ValueError, InvalidSignature)):
                self.checked(self.factory.files(token))

    def test_duplicate_signed_claim_properties_are_rejected(self):
        import base64
        parts = self.factory.token().split('.')
        payload = base64.urlsafe_b64decode(parts[1] + '=' * (-len(parts[1]) % 4))
        payload = payload.replace(b'{', b'{"iss":"acrylic-outbound",', 1)
        parts[1] = base64.urlsafe_b64encode(payload).rstrip(b'=').decode('ascii')
        with self.assertRaises(ValueError):
            self.checked(self.factory.files('.'.join(parts)))

    def test_untrusted_key_metadata_and_weak_rsa_are_rejected(self):
        import base64
        from cryptography.hazmat.primitives import serialization
        from cryptography.hazmat.primitives.asymmetric import rsa
        weak = rsa.generate_private_key(public_exponent=65537, key_size=1024).public_key()
        encoded = base64.b64encode(weak.public_bytes(serialization.Encoding.DER,
                                                   serialization.PublicFormat.SubjectPublicKeyInfo)).decode()
        for patch in [{'algorithm': 'HS256'}, {'publicKey': encoded}, {'keyId': ''}, {'publicKey': 'invalid'},
                      {'untrustedExtra': 'unused'}]:
            with self.subTest(patch=patch), self.assertRaises(ValueError):
                self.checked(key={**self.factory.key, **patch})

    def test_original_bale_or_dispatch_cycle_mismatch_is_rejected(self):
        for field in ['baleId', 'expectedDispatchCycle', 'localSeq']:
            files = self.factory.files()
            import json
            row = json.loads(files['scans.ndjson'])
            row[field] = 'other-value'
            files['scans.ndjson'] = encode(row) + b'\n'
            with self.subTest(field=field), self.assertRaises(ValueError):
                self.checked(files)

    def test_resigned_capture_owner_cannot_cross_package_scope(self):
        for field in ['operatorId', 'warehouseId']:
            original = self.factory.body['captures'][0][field]
            self.factory.body['captures'][0][field] = 'another-scope'
            self.factory.receipt = self.factory.success()
            with self.subTest(field=field), self.assertRaises(ValueError):
                self.checked()
            self.factory.body['captures'][0][field] = original


if __name__ == '__main__':
    unittest.main()
