"""Real SQLite lock wait revocation and simultaneous same-plan CLI calls."""
from contextlib import closing
from pathlib import Path
import subprocess
import sys
import threading
import time

from ..codec import canonical, parse
from ..consumer import transaction
from ..store import ROOT, connect, facts
from .factory import allow
from .support import RetentionCase


class ConcurrencyTest(RetentionCase):
    def test_same_plan_concurrent_cli_commits_once(self):
        prepared = self.prepared()
        allow(prepared)
        args = [sys.executable, '-m', 'tools.pda_retention', *self.consume_args(prepared)]
        children = [subprocess.Popen(args, cwd=ROOT, stdout=subprocess.PIPE, stderr=subprocess.PIPE) for _ in range(2)]
        outputs = [child.communicate(timeout=20) for child in children]
        for ordinal, (child, (stdout, stderr)) in enumerate(zip(children, outputs)):
            (self.directory / f'concurrent-{ordinal}.json').write_bytes(stdout)
            (self.directory / f'concurrent-{ordinal}.stderr.txt').write_bytes(stderr)
            self.assertEqual(0, child.returncode, stdout)
        self.assertEqual({'COMMITTED_SYNTHETIC_ONLY', 'REPLAY_NO_WRITE'},
                         {parse(stdout)['state'] for stdout, _ in outputs})

    def test_real_lock_wait_then_revoke_prevents_deletion(self):
        prepared = self.prepared()
        allow(prepared)
        run = Path(prepared['run'])
        plan = parse((run / 'plan.json').read_bytes())
        began, done = threading.Event(), threading.Event()
        outcome = []
        def waiter():
            with closing(connect(run / 'candidate.sqlite', True)) as db:
                db.set_trace_callback(lambda sql: began.set() if sql == 'BEGIN IMMEDIATE' else None)
                try:
                    outcome.append(transaction(db, plan, (run, prepared['planSha256'])))
                except Exception as error:
                    outcome.append(str(error))
            done.set()
        with closing(connect(run / 'candidate.sqlite', True)) as blocker:
            blocker.execute('BEGIN IMMEDIATE')
            start = time.monotonic()
            worker = threading.Thread(target=waiter)
            worker.start()
            self.assertTrue(began.wait(5), 'Consumer never attempted BEGIN IMMEDIATE')
            self.assertFalse(done.wait(0.2), 'Consumer did not wait for real write lock')
            allow(prepared, False)
            blocker.execute('COMMIT')
        worker.join(15)
        self.assertFalse(worker.is_alive())
        self.assertIn('revoked', outcome[0])
        self.assertEqual(plan['before'], self.snapshot(run / 'candidate.sqlite'))
        (self.directory / 'lock-wait.json').write_bytes(canonical(dict(
            seconds=time.monotonic()-start, beginObserved=True, heldUntilRevoked=True, outcome=outcome)))
