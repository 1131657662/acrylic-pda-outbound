"""Historical sources and date-counter safety are checked without granting authority."""
import gzip
import unittest
from backup_factory import test_directory
from order_counters import order_highwaters, compare_counters
from audit_origins import event_index
from temporary_proofs import verify_part
from safety import OpsError, digest


def audit_fixture(temporary=False):
    row = ['event', 'device', '1', 'digest', 'encoded', 'warehouse', 'assignment', 'operator', '1', 'NULL', 'NULL']
    value = dict(id='event', originDeviceId='device', localSeq='1', operatorId='operator', assignmentId='assignment')
    if temporary:
        row[6], row[9], row[10] = 'NULL', 'authorization', 'client'
        value.pop('assignmentId')
        value.update(authorizationId='authorization', source=dict(kind='TEMP', creatorDeviceId='device', clientOrderId='client'))
    return value, row


class CounterTests(unittest.TestCase):
    def test_beijing_day_extended_sequence_and_legacy_are_preserved(self):
        highs, count, legacy = order_highwaters([('FH202609200001', '20260920'),
            ('FH2026092010000', '20260920'), ('OBhistoric', '20200101')])
        self.assertEqual((highs, count, legacy), ({'20260920': 10000}, 2, 1))
        self.assertEqual(compare_counters(highs, [('20260920', 10001)])['order_counter_dates_verified'], 1)

    def test_missing_or_regressed_counter_blocks_restore(self):
        for rows, code in [([], 'MISSING'), ([('20260920', 9)], 'BEHIND')]:
            with self.subTest(code=code), self.assertRaisesRegex(OpsError, 'ORDER_COUNTER_' + code):
                compare_counters({'20260920': 10}, rows)

    def test_counter_above_max_and_exhaustion_are_distinct(self):
        self.assertEqual(compare_counters({}, [('20260920', 2147483647)])['order_counter_exhausted_dates'], 1)
        with self.assertRaisesRegex(OpsError, 'ORDER_COUNTER_INVALID'):
            compare_counters({}, [('20260920', 2147483648)])

    def test_number_date_and_canonical_sequence_must_match(self):
        for number, day in [('FH202609200001', '20260919'), ('FH2026092000001', '20260920'),
                            ('FH202609202147483648', '20260920'), ('FH202609200000', '20260920')]:
            with self.subTest(number=number), self.assertRaises(OpsError):
                order_highwaters([(number, day)])


class AuditSourceTests(unittest.TestCase):
    def test_formal_and_temporary_indices_pass(self):
        for temporary in (False, True):
            value, row = audit_fixture(temporary)
            event_index(value, row)

    def test_wrong_qualification_creator_and_client_are_rejected(self):
        for field in ('authorizationId', 'creatorDeviceId', 'clientOrderId'):
            value, row = audit_fixture(True)
            target = value if field == 'authorizationId' else value['source']
            target[field] = 'different'
            with self.subTest(field=field), self.assertRaisesRegex(OpsError, 'AUDIT_TEMP_INDEX_MISMATCH'):
                event_index(value, row)

    def test_mixed_formal_and_temporary_shape_is_rejected(self):
        value, row = audit_fixture(True)
        value['assignmentId'] = 'assignment'
        with self.assertRaisesRegex(OpsError, 'AUDIT_SOURCE_SHAPE_MISMATCH'):
            event_index(value, row)

    def test_preparation_bytes_match_both_signed_digests(self):
        raw = b'[{"synthetic":true}]'; compressed = gzip.compress(raw)
        part = dict(compressedBytes=len(compressed), bytes=len(raw), compressedSha256=digest(compressed), sha256=digest(raw))
        verify_part(compressed, part)
        part['sha256'] = '0' * 64
        with self.assertRaisesRegex(OpsError, 'TEMP_RAW_PART_MISMATCH'):
            verify_part(compressed, part)
