"""Archive consistency and strict simulated retention time eligibility."""
import calendar
from datetime import datetime, timezone
import re

from .codec import canonical, digest, parse, require
from .signatures import verified
from .retrieval import verify as verify_files

DAY = 86400 * 10**9


def instant(value):
    match = re.fullmatch(r'(\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d)(?:\.(\d{1,9}))?Z', value)
    require(match is not None, 'UTC instant required')
    base = datetime.strptime(match[1], '%Y-%m-%dT%H:%M:%S').replace(tzinfo=timezone.utc)
    return calendar.timegm(base.utctimetuple()) * 10**9 + int((match[2] or '').ljust(9, '0'))


def retained(archive, now):
    completed, archived = instant(archive['completedAt']), instant(archive['archivedAt'])
    until = instant(archive['retainUntil'])
    year = int(archive['archivedAt'][:4]) + 5
    anniversary = str(year) + archive['archivedAt'][4:]
    if archive['archivedAt'][5:10] == '02-29' and not calendar.isleap(year):
        anniversary = str(year) + '-02-28' + archive['archivedAt'][10:]
    require(completed <= archived <= now < until, 'Archive not currently retained')
    require(now - completed > 90 * DAY, 'Strict 90-day boundary not reached')
    require(until >= instant(anniversary), 'Five-year archive retention missing')
    require(archive['localDeletionAllowed'] is False, 'Production deletion grant forbidden')


def check(local, archive, context):
    body, receipt = parse(local['payload']), parse(local['receipt'])
    confirmation, key = parse(archive['confirmationJson']), parse(archive['signingKeyJson'])
    key_sha = verified(receipt, ('signedReceipt', 'acrylic-pda-receipt'), key)
    verified(confirmation, ('signedArchive', 'acrylic-pda-archive'), key)
    require(key_sha == context['keySha256'], 'Pinned archive key mismatch')
    require(digest(local['payload'].encode()) == local['sha256'], 'Payload bytes changed')
    require(body['source']['kind'] == 'FORMAL' and body['continuationId'] is None and
            body['workRound'] == '1', 'Only initial FORMAL supported')
    require(receipt['status'] == 'SUCCEEDED' and receipt['orderStatus'] == 'COMPLETED', 'Success required')
    bindings(local, body, receipt)
    expected = archive_fields(local, body, receipt)
    for field, value in expected.items():
        require(type(confirmation[field]) is type(value) and confirmation[field] == value,
                'Archive binding mismatch: ' + field)
    require(archive['payloadSha256'] == local['sha256'] and
            archive['receiptSha256'] == expected['receiptSha256'] and
            archive['recoveryEpoch'] == confirmation['recoveryEpoch'], 'Archive row mismatch')
    retained(confirmation, instant(context['now']))
    verify_files(local, confirmation, context)
    return body, confirmation


def bindings(local, body, receipt):
    expected = dict(submissionId=local['id'], orderId=body['source']['sourceOrderId'],
                    payloadSha256=local['sha256'], freezeDeviceId=body['freezeDeviceId'],
                    workRound=body['workRound'], issuedRecoveryEpoch=body['sourceRecoveryEpoch'],
                    source=body['source'], actualCount=body['expectedCount'],
                    actualWeightGrams=body['expectedWeightGrams'],
                    captureHighWatermark=body['captureHighWatermark'],
                    inventoryDeducted=body['autoDeductInventory'], resultVersion='1')
    require(body['submissionId'] == local['id'] and body['assignmentId'] == local['assignmentId'],
            'Submission context changed')
    for field, value in expected.items():
        require(receipt[field] == value, 'Receipt binding mismatch: ' + field)
    require(body['scans'] and body['captureSummary']['pending'] == '0', 'Pending or empty batch')


def archive_fields(local, body, receipt):
    capture = body['captures'][0]
    return dict(submissionId=local['id'], assignmentId=local['assignmentId'],
                orderId=body['source']['sourceOrderId'], warehouseId=capture['warehouseId'],
                operatorId=capture['operatorId'], freezeDeviceId=body['freezeDeviceId'],
                workRound=body['workRound'], sourceRecoveryEpoch=body['sourceRecoveryEpoch'],
                payloadSha256=local['sha256'], receiptSha256=digest(canonical(receipt)),
                payloadBytes=str(len(local['payload'].encode())), receiptBytes=str(len(canonical(receipt))),
                completedAt=receipt['completedAt'], archiveVersion=1,
                storagePolicy='SERVER_DURABLE_FILES_V1', localDeletionAllowed=False)
