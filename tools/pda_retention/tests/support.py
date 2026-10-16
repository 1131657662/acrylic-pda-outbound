"""Persistent test evidence; failures and databases are never removed."""
from contextlib import closing
import os
from pathlib import Path
import subprocess
import sys
import unittest
import uuid

from ..codec import canonical, parse
from ..consumer import prepare
from ..store import ROOT, connect, facts
from .factory import create_source


class RetentionCase(unittest.TestCase):
    def setUp(self):
        root = Path(os.environ['PDA_RETENTION_TEST_ROOT'])
        self.directory = root / ('case-' + str(uuid.uuid4())[:8])
        self.spec, self.fixture = create_source(self.directory)
        self.spec_data = parse(self.spec.read_bytes())
        self.source = Path(self.spec_data['source'])
        (self.directory / 'test-name.txt').write_text(self.id(), encoding='utf-8')

    def prepared(self):
        return prepare(self.spec)

    def snapshot(self, path):
        with closing(connect(path)) as db:
            return facts(db)

    def cli(self, args, label):
        result = subprocess.run([sys.executable, '-m', 'tools.pda_retention', *map(str, args)],
                                cwd=ROOT, capture_output=True)
        (self.directory / (label + '.stdout.json')).write_bytes(result.stdout)
        (self.directory / (label + '.stderr.txt')).write_bytes(result.stderr)
        (self.directory / (label + '.command.json')).write_bytes(canonical(dict(args=list(map(str, args)), exit=result.returncode)))
        return result

    def consume_args(self, prepared, enabled=True):
        args = ['consume', prepared['run'], '--expected-plan-sha256', prepared['planSha256']]
        return args + (['--enable-isolated-deletion'] if enabled else [])
