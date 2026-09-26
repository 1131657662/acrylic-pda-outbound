"""Recalculate archived TEMP business totals from the original frozen plan and ACTIVE scans."""
import re
from archive_proofs import canonical
from safety import digest, require


def number(value):
    require(isinstance(value, str) and re.fullmatch('0|[1-9][0-9]*', value), 'TEMP_ARCHIVE_DECIMAL')
    require(int(value) <= 9223372036854775807, 'TEMP_ARCHIVE_OVERFLOW')
    return int(value)


def products(body):
    result = []
    previous = ''
    for line in body['temporaryOrder']['lines']:
        code = line['deliveryCode']
        require(isinstance(code, str) and re.fullmatch('[0-9]{3}', code) and code > previous, 'TEMP_ARCHIVE_PRODUCT_ORDER')
        previous = code
        scans = [scan for scan in body['scans'] if scan['status'] == 'ACTIVE' and scan['deliveryCode'] == code]
        weight, planned = sum(number(scan['weightGrams']) for scan in scans), number(line['plannedCount'])
        require(planned > 0 and planned >= len(scans) and weight <= 9223372036854775807, 'TEMP_ARCHIVE_PRODUCT_TOTAL')
        result.append({**{key: line[key] for key in ('deliveryCode', 'productId', 'model', 'name', 'plannedCount')},
                       'actualCount': str(len(scans)), 'weightGrams': str(weight), 'shortCount': str(planned - len(scans))})
    codes = {item['deliveryCode'] for item in result}
    require(all(scan['deliveryCode'] in codes for scan in body['scans'] if scan['status'] == 'ACTIVE'), 'TEMP_ARCHIVE_UNKNOWN_PRODUCT')
    require(sum(number(item['actualCount']) for item in result) > 0, 'TEMP_ARCHIVE_EMPTY')
    return result


def decision_products(body, decision):
    require(set(decision) == set('kind candidates products shortShipmentReason shortApprovalReceiptId shortApprovalSha256 submissionReceipt'.split()), 'TEMP_ARCHIVE_DECISION_FIELDS')
    expected = products(body)
    require(canonical(decision['products']) == canonical(expected), 'TEMP_ARCHIVE_DECISION_PRODUCTS')
    require(decision['shortShipmentReason'] == (body.get('shortShipmentReason') or ''), 'TEMP_ARCHIVE_SHORT_REASON')
    short = any(number(item['shortCount']) for item in expected)
    require(not short or isinstance(body.get('shortShipmentReason'), str) and body['shortShipmentReason'].strip(), 'TEMP_ARCHIVE_SHORT_REASON')
    require(body['planSha256'] == digest(canonical(body['temporaryOrder']).encode('utf-8')), 'TEMP_ARCHIVE_PLAN_DIGEST')
    return short


def success_products(body, receipt):
    rows = products(body)
    require(receipt['actualCount'] == body['expectedCount'] == str(sum(number(p['actualCount']) for p in rows)), 'TEMP_ARCHIVE_SUCCESS_COUNT')
    require(receipt['actualWeightGrams'] == body['expectedWeightGrams'] == str(sum(number(p['weightGrams']) for p in rows)), 'TEMP_ARCHIVE_SUCCESS_WEIGHT')
    expected = [{'deliveryCode': p['deliveryCode'], 'actualCount': p['actualCount'], 'actualWeightGrams': p['weightGrams']} for p in rows]
    require(canonical(receipt['items']) == canonical(expected), 'TEMP_ARCHIVE_SUCCESS_PRODUCTS')
    active = [scan for scan in body['scans'] if scan['status'] == 'ACTIVE']
    require(receipt['firstOrderScanSeq'] == str(min(number(s['orderScanSeq']) for s in active)) and
            receipt['lastOrderScanSeq'] == str(max(number(s['orderScanSeq']) for s in active)), 'TEMP_ARCHIVE_SUCCESS_SEQUENCE')
