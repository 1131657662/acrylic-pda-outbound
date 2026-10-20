"""Fresh synthetic keys and signed FORMAL packages; never a field identity."""
import base64
import hashlib
import io
import json
import zipfile

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec, padding, rsa

from .facts import PRODUCT_HEADERS, SCAN_FIELDS, SCAN_HEADERS, records, summary, tsv
from .integrity import PURPOSE


def encode(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(',', ':')).encode('utf-8')


def b64(data):
    return base64.urlsafe_b64encode(data).rstrip(b'=')


class ReceiptFactory:
    def __init__(self):
        self.server = rsa.generate_private_key(public_exponent=65537, key_size=2048)
        self.device = ec.generate_private_key(ec.SECP256R1())
        der = self.server.public_key().public_bytes(serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo)
        self.key = {'keyId': 'synthetic-receipt-key', 'algorithm': 'RS256', 'publicKey': base64.b64encode(der).decode()}
        self.body = self.frozen()
        self.receipt = self.success()

    def frozen(self):
        scan = dict(zip(SCAN_FIELDS, ['1', 'synthetic-capture', 'synthetic-barcode', '003', 'model',
                                     'name', '1.234', '1234', 'ACTIVE', '2026-01-01T00:00:00Z']))
        scan.update(baleId='synthetic-bale', expectedDispatchCycle='1', localSeq='1')
        return {'submissionId': 'synthetic-submission', 'source': {'kind': 'FORMAL', 'sourceOrderId': 'synthetic-order'},
                'assignmentId': 'synthetic-assignment', 'freezeDeviceId': 'synthetic-device', 'workRound': '1',
                'sourceRecoveryEpoch': '1', 'captureHighWatermark': '1', 'expectedCount': '1',
                'expectedWeightGrams': '1234', 'autoDeductInventory': True, 'scans': [scan], 'dispositions': [],
                'captures': [{'id': 'synthetic-capture', 'orderScanSeq': '1', 'state': 'COMMITTED',
                              'operatorId': 'synthetic-operator', 'warehouseId': 'synthetic-warehouse',
                              'originDeviceId': 'synthetic-device', 'assignmentId': 'synthetic-assignment'}]}

    def success(self):
        return {'submissionId': self.body['submissionId'], 'orderId': 'synthetic-order', 'status': 'SUCCEEDED',
                'payloadSha256': hashlib.sha256(encode(self.body)).hexdigest(), 'freezeDeviceId': 'synthetic-device',
                'workRound': '1', 'issuedRecoveryEpoch': '1', 'mayResumeScanning': False, 'stockReleased': False,
                'source': self.body['source'].copy(), 'orderStatus': 'COMPLETED', 'actualCount': '1',
                'actualWeightGrams': '1234', 'inventoryDeducted': True, 'resultVersion': '1', 'receiptVersion': 1,
                'captureHighWatermark': '1', 'firstOrderScanSeq': '1', 'lastOrderScanSeq': '1',
                'completedAt': '2026-01-01T00:00:00Z', 'orderNo': 'synthetic-order-number',
                'items': [{'deliveryCode': '003', 'actualCount': '1', 'actualWeightGrams': '1234'}]}

    def token(self, receipt=None, header=None, claims=None):
        head = b64(encode(header or {'alg': 'RS256', 'typ': 'JWT', 'kid': self.key['keyId']}))
        payload = b64(encode(claims or {'iss': 'acrylic-outbound', 'aud': 'acrylic-pda-receipt',
                                      'receipt': receipt if receipt is not None else self.receipt}))
        message = head + b'.' + payload
        signature = self.server.sign(message, padding.PKCS1v15(), hashes.SHA256())
        return (message + b'.' + b64(signature)).decode('ascii')

    def files(self, token=None):
        scan = self.body['scans'][0]
        order = {'source': 'FORMAL', 'orderId': 'synthetic-order', 'localOrderId': 'synthetic-assignment',
                 'deviceId': 'synthetic-device', 'operatorId': 'synthetic-operator', 'warehouseId': 'synthetic-warehouse',
                 'state': 'FROZEN', 'timeTrust': 'UNCONFIRMED_EXPORT_TIME'}
        capture = self.body['captures'][0]
        return {'order.json': encode(order), 'captures.ndjson': encode(capture) + b'\n',
                'scans.ndjson': encode(scan) + b'\n', 'events.ndjson': b'', 'dispositions.ndjson': b'',
                'lines.ndjson': encode({'deliveryCode': '003', 'plannedCount': '1'}) + b'\n',
                'scan-details.tsv': tsv(SCAN_HEADERS, [[scan[field] for field in SCAN_FIELDS] + ['']]),
                'product-summary.tsv': tsv(PRODUCT_HEADERS, [['003', '1', '1', '0', '0', '0', '1234']]),
                'readme.txt': b'Synthetic read-only test package', 'submission.json': encode(self.body),
                'submission-info.json': encode({'id': self.body['submissionId'], 'sha256': hashlib.sha256(encode(self.body)).hexdigest()}),
                'receipt.json': encode({**self.receipt, 'signedReceipt': token or self.token()})}

    def package(self, files=None):
        files = files if files is not None else self.files()
        der = self.device.public_key().public_bytes(serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo)
        manifest = {'purpose': PURPOSE, 'exportId': 'synthetic-export', 'deviceKeySha256': hashlib.sha256(der).hexdigest(),
                    'files': {name: {'bytes': str(len(data)), 'sha256': hashlib.sha256(data).hexdigest()} for name, data in files.items()},
                    'summary': summary(tuple(records(files[name + '.ndjson']) for name in
                                             ['captures', 'scans', 'events', 'dispositions']))}
        raw = encode(manifest)
        sealed = {**files, 'manifest.json': raw, 'device-public-key.der': der,
                  'manifest.sig': self.device.sign(PURPOSE.encode() + b'\n' + raw, ec.ECDSA(hashes.SHA256()))}
        output = io.BytesIO()
        with zipfile.ZipFile(output, 'w') as bundle:
            for name, data in sealed.items():
                bundle.writestr(name, data)
        trust = {'synthetic-device': {'publicKeyDerBase64': base64.b64encode(der).decode(),
                                    'operatorIds': ['synthetic-operator'], 'warehouseIds': ['synthetic-warehouse']}}
        return output.getvalue(), trust
