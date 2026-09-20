"""Target protection and exact schema equivalence boundaries."""
import unittest
from backup_factory import test_directory
from mysql_io import normalized_schema
from safety import exclusive, OpsError, child


class SafetyTests(unittest.TestCase):
    def test_concurrent_operation_is_rejected_without_removing_original_lock(self):
        root = test_directory()
        with exclusive(root):
            with self.assertRaisesRegex(OpsError, 'R09_OPERATION_LOCKED'):
                with exclusive(root):
                    self.fail('second operation acquired lock')
            self.assertTrue((root / '.r09-operation.lock').exists())
        self.assertFalse((root / '.r09-operation.lock').exists())

    def test_schema_normalizes_only_redundant_utf8mb4_declaration(self):
        original = '  `name` varchar(100) COLLATE utf8mb4_0900_as_cs NOT NULL,'
        restored = '  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs NOT NULL,'
        self.assertEqual(normalized_schema(original), normalized_schema(restored))
        for changed in (restored.replace('as_cs', 'ai_ci'), restored.replace('100', '99'),
                        restored.replace('NOT NULL', 'NULL')):
            with self.subTest(changed=changed):
                self.assertNotEqual(normalized_schema(original), normalized_schema(changed))

    def test_windows_aliases_are_rejected(self):
        root = test_directory()
        for name in ('files/NUL', 'files/CON.txt', 'files/trailing.', 'files/trailing '):
            with self.subTest(name=name), self.assertRaises(OpsError):
                child(root, name)


if __name__ == '__main__':
    unittest.main()
