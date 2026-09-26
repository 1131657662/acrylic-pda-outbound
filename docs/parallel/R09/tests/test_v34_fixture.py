"""V34 lab controls reject port substitution and only create synthetic rows."""
import json
from pathlib import Path
import sys
import unittest
from uuid import uuid4
from backup_factory import test_directory
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'ops'))
from lab import LAB_ROOT, load
from mysql_io import Database
from safety import OpsError
from v34_fixture import seed_evidence


class RecordingDatabase:
    def __init__(self, root):
        self.root = root
        (root / 'files').mkdir()
        self.writes = []

    def query(self, sql):
        if sql.startswith('SELECT'):
            return '\t'.join(['A' * 32, 'B' * 32, 'C' * 32, '1', 'D' * 32, 'E' * 32])
        self.writes.append(sql)
        return ''


def profile():
    test_directory()
    token = 'abcdef'
    root = LAB_ROOT / f'v34-profile-{uuid4().hex}-{token}'
    root.mkdir()
    return root, {'root': str(root), 'purpose': 'R09_V34_SYNTHETIC_ONLY', 'mysql_bin': 'unused',
                  'source_port': 19434, 'restore_port': 19435, 'api_port': 18434,
                  'source_database': f'r09_v34_{token}_source',
                  'restore_database': f'r09_v34_{token}_restore'}


class V34FixtureTests(unittest.TestCase):
    def test_new_profile_uses_dedicated_names_and_ports(self):
        root, settings = profile()
        (root / 'lab.json').write_text(json.dumps(settings), encoding='utf-8')
        loaded = load(root / 'lab.json')
        self.assertEqual(Database(loaded, 'source').port, 19434)
        self.assertEqual(Database(loaded, 'restore').name, settings['restore_database'])

    def test_profile_rejects_shared_or_r06_ports(self):
        for port in (3306, 19330):
            with self.subTest(port=port):
                root, settings = profile()
                settings['source_port'] = port
                (root / 'lab.json').write_text(json.dumps(settings), encoding='utf-8')
                with self.assertRaisesRegex(OpsError, 'LAB_PORT_MISMATCH'):
                    load(root / 'lab.json')

    def test_profile_rejects_database_from_other_lab(self):
        root, settings = profile()
        settings['source_database'] = 'r09_v34_123456_source'
        (root / 'lab.json').write_text(json.dumps(settings), encoding='utf-8')
        with self.assertRaisesRegex(OpsError, 'LAB_DATABASE_MISMATCH'):
            load(root / 'lab.json')

    def test_fixture_keeps_provenance_explicitly_synthetic(self):
        db = RecordingDatabase(test_directory())
        seeded = seed_evidence(db)
        self.assertEqual(seeded['synthetic_objects'], 2)
        self.assertEqual(len(list((db.root / 'files' / 'r09-v34').iterdir())), 2)
        self.assertEqual(len(db.writes), 5)
        self.assertIn("'PARTIAL'", db.writes[-1])
        self.assertNotIn('VERIFIED_COMPLETE_AT_STOP', db.writes[-1])
