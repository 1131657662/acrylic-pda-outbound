"""Public consumer refuses scope drift, credentials and business capabilities."""
import unittest
from uuid import uuid4
from .independent_review import checked_review


class IndependentReviewTest(unittest.TestCase):
    def setUp(self):
        self.reception = {'relayId': str(uuid4()), 'exportId': str(uuid4()), 'archiveSha256': 'a' * 64}
        self.value = {**self.reception, 'originalTaskId': str(uuid4()), 'credentialId': str(uuid4()),
                      'sourceTrust': 'TRUSTED', 'authorizationState': 'VERIFIED',
                      'authorizationReason': 'ORIGINAL_ISSUANCE_VERIFIED', 'currentSubjectState': 'CHANGED_OR_UNAVAILABLE',
                      'pendingReconciliation': True, 'completeBoundaryVerified': False, 'newCollectionAuthorized': False,
                      'businessProcessed': False, 'localDeletionAllowed': False}

    def test_historical_issuance_and_changed_subject_are_separate(self):
        self.assertEqual(self.value, checked_review(self.value, self.reception))

    def test_unverified_original_remains_readable(self):
        self.value.update(authorizationState='UNVERIFIED', authorizationReason='CREDENTIAL_NOT_FOUND')
        self.assertEqual('UNVERIFIED', checked_review(self.value, self.reception)['authorizationState'])

    def test_different_original_is_rejected(self):
        for field in ('relayId', 'exportId', 'archiveSha256'):
            with self.subTest(field=field), self.assertRaises(ValueError):
                checked_review({**self.value, field: str(uuid4())}, self.reception)

    def test_credentials_and_unknown_fields_are_rejected(self):
        for field in ('signedCredential', 'accessToken', 'unknown'):
            with self.subTest(field=field), self.assertRaises(ValueError):
                checked_review({**self.value, field: 'secret'}, self.reception)

    def test_business_capabilities_cannot_be_opened_by_review(self):
        for field in ('completeBoundaryVerified', 'newCollectionAuthorized', 'businessProcessed', 'localDeletionAllowed'):
            with self.subTest(field=field), self.assertRaises(ValueError):
                checked_review({**self.value, field: True}, self.reception)

    def test_inconsistent_verification_reason_is_rejected(self):
        with self.assertRaises(ValueError):
            checked_review({**self.value, 'authorizationReason': 'CREDENTIAL_NOT_FOUND'}, self.reception)
