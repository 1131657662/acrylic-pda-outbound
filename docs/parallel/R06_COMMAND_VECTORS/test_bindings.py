"""Expected context, idempotency scope, and immutable response recovery examples."""
import unittest
from bindings import check_new_binding, original_replay
from canonical import canonical, digest, raw_digest
from fixtures import command, context, load, replace_path
from shape import command as check_shape


def stored_record():
    signed = load('signed_vectors.json')
    return {'identity': context(), 'commandSha256': load('command_vectors.json')['commandSha256'],
            'rawResponse': signed['originalResponseUtf8'].encode('utf-8'),
            'originalResponseSha256': signed['originalResponseSha256']}


class ContextChanges(unittest.TestCase):
    pass


def context_case(sample):
    def test(self):
        body = replace_path(command(), sample['path'], sample['value'])
        with self.assertRaisesRegex(ValueError, '^' + sample['error'] + '$'):
            check_new_binding(body, command(), context())
    return test


def same_key_case(sample):
    def test(self):
        body = replace_path(command(), sample['path'], sample['value'])
        self.assertNotEqual(digest(command(), 'command'), digest(body, 'command'))
        code = 'KEY_BINDING_INVALID' if sample['path'] == 'commandId' else 'IDEMPOTENCY_CONFLICT'
        with self.assertRaisesRegex(ValueError, '^' + code + '$'):
            original_replay(body, stored_record(), context())
    return test


data = load('command_vectors.json')
for sample in data['changes']:
    setattr(ContextChanges, 'test_context_' + sample['id'].replace('-', '_'), context_case(sample))
for sample in data['changes'] + data['hashOnlyChanges']:
    setattr(ContextChanges, 'test_same_key_' + sample['id'].replace('-', '_'), same_key_case(sample))


class RecoveryExamples(unittest.TestCase):
    def test_matching_context_is_only_a_binding_match(self):
        self.assertEqual('CONTEXT_MATCH_ONLY', check_new_binding(command(), command(), context()))

    def test_nullable_canonical_does_not_promote_declared_payload(self):
        body = command()
        body['expected'].update(canonicalOrderId=None, mappingVersion='0', slotVersion=None,
                                targetRevision=None, receivedPayloadSha256=None)
        check_shape(body)
        self.assertIsNone(body['expected']['receivedPayloadSha256'])
        self.assertIsNotNone(body['expected']['declaredUploadSha256'])

    def test_missing_canonical_is_not_explicit_null(self):
        body = command()
        del body['expected']['canonicalOrderId']
        with self.assertRaisesRegex(ValueError, 'FIELDS_INVALID'):
            check_shape(body)

    def test_null_target_cannot_keep_slot(self):
        body = command()
        body['expected']['canonicalOrderId'] = None
        with self.assertRaisesRegex(ValueError, 'NULL_MAPPING_INVALID'):
            check_shape(body)

    def test_client_cannot_supply_trusted_actor(self):
        body = command()
        body['actor'] = {'principalId': context()['principalId']}
        with self.assertRaisesRegex(ValueError, 'FIELDS_INVALID'):
            check_shape(body)

    def test_unknown_key_is_not_original_result(self):
        identity = context()
        identity['originalKey'] = '00000000-0000-4000-8000-000000000009'
        with self.assertRaisesRegex(ValueError, 'ORIGINAL_COMMAND_NOT_FOUND'):
            original_replay(command(), stored_record(), identity)

    def test_other_actor_is_not_original_result(self):
        identity = context()
        identity['principalId'] = '00000000-0000-4000-8000-000000000009'
        with self.assertRaisesRegex(ValueError, 'ORIGINAL_COMMAND_NOT_FOUND'):
            original_replay(command(), stored_record(), identity)

    def test_route_alias_must_not_resolve_as_another_scope(self):
        identity = context()
        identity['routeScope'] = 'unregistered-alias'
        with self.assertRaisesRegex(ValueError, 'ORIGINAL_COMMAND_NOT_FOUND'):
            original_replay(command(), stored_record(), identity)

    def test_revoked_write_cannot_issue_new_decision(self):
        identity = context()
        identity['currentWriteAllowed'] = False
        with self.assertRaisesRegex(ValueError, 'WRITE_AUTHORIZATION_REQUIRED'):
            check_new_binding(command(), command(), identity)

    def test_revoked_write_keeps_history_when_current_read_is_authorized(self):
        identity = context()
        identity['currentWriteAllowed'] = False
        record = stored_record()
        self.assertEqual(record['rawResponse'], original_replay(command(), record, identity))

    def test_revoked_read_never_exposes_history(self):
        identity = context()
        identity['currentReadAllowed'] = False
        with self.assertRaisesRegex(ValueError, 'READ_AUTHORIZATION_REQUIRED'):
            original_replay(command(), stored_record(), identity)

    def test_lost_response_recovers_identical_bytes_across_effect_changes(self):
        record = stored_record()
        for effect in load('receipt_vectors.json')['currentEffects']:
            query = {'currentEffect': effect, 'originalBytes': original_replay(command(), record, context())}
            self.assertEqual(record['rawResponse'], query['originalBytes'])
            self.assertEqual(record['originalResponseSha256'], raw_digest(query['originalBytes']))
        self.assertNotEqual(record['rawResponse'], canonical(load('signed_vectors.json')['vectors'][0]['originalCommandReceipt']))

    def test_damaged_stored_bytes_require_evidence_recovery(self):
        record = stored_record()
        record['rawResponse'] += b' '
        with self.assertRaisesRegex(ValueError, 'ORIGINAL_RESPONSE_DAMAGED'):
            original_replay(command(), record, context())

    def test_decimal_versions_cannot_be_reencoded_as_json_numbers(self):
        body = command()
        body['workRound'] = 1
        with self.assertRaisesRegex(ValueError, 'DECIMAL_INVALID'):
            check_shape(body)

    def test_normal_confirm_may_omit_new_reason_requirement(self):
        body = command()
        body['commandType'] = 'NORMAL_CONFIRM'
        body['reason'] = ''
        check_shape(body)
