"""Validate a supervisor's read-only historical-authorization response without accepting credentials."""
from uuid import UUID


def checked_review(value, reception):
    fields = {'relayId', 'exportId', 'archiveSha256', 'originalTaskId', 'credentialId', 'sourceTrust',
              'authorizationState', 'authorizationReason', 'currentSubjectState', 'pendingReconciliation',
              'completeBoundaryVerified', 'newCollectionAuthorized', 'businessProcessed', 'localDeletionAllowed'}
    if not isinstance(value, dict) or set(value) != fields:
        raise ValueError('Unexpected independent review fields; do not infer authorization')
    for name in ('relayId', 'exportId', 'archiveSha256'):
        if value[name] != reception[name]:
            raise ValueError('Review belongs to different originals')
    for name in ('relayId', 'exportId', 'originalTaskId', 'credentialId'):
        if str(UUID(value[name])) != value[name]:
            raise ValueError('Invalid original review identity')
    for name in ('completeBoundaryVerified', 'newCollectionAuthorized', 'businessProcessed', 'localDeletionAllowed'):
        if value[name] is not False:
            raise ValueError('Read-only review cannot open business capabilities')
    if value['pendingReconciliation'] is not True:
        raise ValueError('Independent originals must remain pending reconciliation')
    reasons = {'CREDENTIAL_NOT_FOUND', 'CREDENTIAL_CONTENT_MISMATCH', 'CREDENTIAL_SIGNATURE_INVALID', 'HISTORICAL_KEY_UNAVAILABLE'}
    verified = value['authorizationState'] == 'VERIFIED' and value['authorizationReason'] == 'ORIGINAL_ISSUANCE_VERIFIED'
    unverified = value['authorizationState'] == 'UNVERIFIED' and value['authorizationReason'] in reasons
    if not (verified or unverified) or value['sourceTrust'] not in ('TRUSTED', 'UNVERIFIED'):
        raise ValueError('Unknown historical authorization status')
    if value['currentSubjectState'] not in ('UNCHANGED', 'CHANGED_OR_UNAVAILABLE'):
        raise ValueError('Unknown current subject status')
    return value
