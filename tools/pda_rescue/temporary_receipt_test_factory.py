"""Synthetic first-round TEMP original files; never a field identity."""
import hashlib
import uuid

from .receipt_test_factory import ReceiptFactory, encode


def identifier(number):
    return str(uuid.UUID(int=number))


class TemporaryReceiptFactory:
    def __init__(self):
        self.signer = ReceiptFactory()
        self.key = self.signer.key
        self.body = self.signer.body
        self.body.update(submissionId=identifier(1), freezeDeviceId=identifier(2), assignmentId=None,
                         continuationId=None, authorizationId=identifier(3))
        self.body['source'] = {'kind': 'TEMP', 'creatorDeviceId': identifier(2), 'clientOrderId': identifier(4)}
        self.body['temporaryOrder'] = {**self.body['source'], 'authorizationId': identifier(3), 'lines': [
            {'deliveryCode': '003', 'plannedCount': '1'}, {'deliveryCode': '999', 'plannedCount': '1'}]}
        self.body['temporaryOrder'].pop('kind')
        self.body['captures'] = [{'id': identifier(5), 'orderScanSeq': '1', 'state': 'COMMITTED',
                                 'operatorId': identifier(6), 'originDeviceId': identifier(2),
                                 'authorizationId': identifier(3), 'clientOrderId': identifier(4),
                                 'barcode': 'synthetic-barcode', 'expectedDispatchCycle': '1'}]
        self.body['scans'][0].update(captureId=identifier(5), baleId=identifier(7))
        self.context = {'submissionId': identifier(1), 'creatorDeviceId': identifier(2),
                        'authorizationId': identifier(3), 'clientOrderId': identifier(4),
                        'operatorId': identifier(6), 'warehouseId': identifier(8), 'orderId': identifier(9)}
        self.receipt = self.signer.receipt
        self.receipt.update(submissionId=identifier(1), freezeDeviceId=identifier(2), orderId=identifier(9),
                            source=self.body['source'].copy())
        self.receipt['items'].append({'deliveryCode': '999', 'actualCount': '0', 'actualWeightGrams': '0'})

    def originals(self):
        payload = encode(self.body)
        self.context['payloadSha256'] = hashlib.sha256(payload).hexdigest()
        self.receipt['payloadSha256'] = self.context['payloadSha256']
        response = encode({'code': 'OK', 'data': {**self.receipt, 'signedReceipt': self.signer.token(self.receipt)}})
        return payload, response, self.context.copy(), self.key.copy()
