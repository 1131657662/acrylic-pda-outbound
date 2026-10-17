"""Bind the retained rollback fixture to one fresh, identity-checked worker lab."""
from pathlib import Path
import json
import os
import shutil
import socket
import subprocess

import d15_rollback_sql as sql

ROOT = Path(__file__).resolve().parents[4]
RUN = int(os.environ.get('D15_WORKER_RUN', '677'))
PORT = int(os.environ.get('D15_WORKER_DB_PORT', '19387'))
HTTP = int(os.environ.get('D15_WORKER_HTTP_PORT', '19461'))
PRODUCER = os.environ.get('D15_WORKER_PRODUCER', 'new')
FORMAT = os.environ.get('D15_WORKER_FORMAT', 'XLSX')
BASIS = os.environ.get('D15_WORKER_BASIS', 'ORIGINAL_CONFIRMED')
assert 600 <= RUN <= 999 and 1024 <= PORT <= 65535 and 1024 <= HTTP <= 65533
assert PORT not in (HTTP, HTTP + 1, HTTP + 2) and PRODUCER in ('old', 'new')
assert FORMAT in ('XLSX', 'SCAN_XLSX', 'PDF') and BASIS in ('ORIGINAL_CONFIRMED', 'CURRENT_EFFECTIVE')
assert FORMAT != 'SCAN_XLSX' or BASIS == 'ORIGINAL_CONFIRMED'
LAB = ROOT / f'.local/e2a-cross-20260925-{RUN}'
DATABASE, USER = f'acrylic_e2a_cross_20260925_{RUN}', f'e2a_cross_20260925_{RUN}'
assert sql.ROOT == ROOT
sql.LAB, sql.PORT, sql.DATABASE, sql.USER = LAB, PORT, DATABASE, USER
# These modules capture the SQL bindings at import. The standalone runner imports
# this module first; the historical 673 entry point is never invoked.
import d15_program_rollback_fixture as fixture
import d15_rollback_runtime as runtime
import d15_rollback_compare as compare

JARS = dict(new=(ROOT / 'server/build/libs/acrylic-outbound-server-0.1.0.jar',
    'e130aa2b7844daf6380045e3f3e809672b9c15f5ff31c4a24adf4cdf6bdc92ba'),
    old=(ROOT / '.local/goal-20261013/e06-conflict-audit-1535/server.jar',
    'adbd314f0fd619997dea0f16e0de36a963c3650173e991cbcf259ad1ae7184e1'))
RENDERER = ROOT / 'report-rendering/build/install/acrylic-report-rendering'
BROWSERS = ROOT / 'report-rendering/build/browsers'


def allocation():
    assert not LAB.exists(), 'Preserve every previous attempt; never reuse a lab'
    trees = subprocess.check_output(['git', 'worktree', 'list', '--porcelain'], cwd=ROOT).decode()
    for line in trees.splitlines():
        if line.startswith('worktree '):
            assert not (Path(line[9:]) / LAB.relative_to(ROOT)).exists()
    for port in (PORT, HTTP, HTTP + 1, HTTP + 2):
        with socket.socket() as probe:
            probe.setsockopt(socket.SOL_SOCKET, socket.SO_EXCLUSIVEADDRUSE, 1)
            probe.bind(('127.0.0.1', port))
    disks = {drive: shutil.disk_usage(drive)._asdict() for drive in ('C:/', 'D:/')}
    assert disks['C:/']['free'] > 5 << 30 and disks['D:/']['free'] > 4 << 30
    for path, expected in JARS.values():
        assert sql.digest(path) == expected, str(path)
    assert RENDERER.is_dir() and len(list((RENDERER / 'lib').glob('*.jar'))) == 29
    assert BROWSERS.is_dir() and (BROWSERS / 'chromium-1187').is_dir()
    return dict(worktrees=trees, ports=[PORT, HTTP, HTTP + 1, HTTP + 2], producer=PRODUCER,
                format=FORMAT, basis=BASIS,
                disks=disks, browsers=str(BROWSERS), estimatedGrowthBytes=1 << 30, exclusiveBind=True)


def inputs():
    paths = list((ROOT / 'docs/parallel/R09/tests').glob('*d15*.py'))
    paths += list((RENDERER / 'lib').glob('*.jar')) + [item[0] for item in JARS.values()]
    paths += [ROOT / 'report-rendering/build.gradle', ROOT / 'server/build.gradle']
    paths += [ROOT / '.local/goal-20261016/d15-restored-worker01/xlsx_readback01.py']
    paths += [ROOT / 'docs/history/GOAL_D15_RESTORED_EXPORT_WORKER_2026-10-16.md',
              ROOT / 'docs/history/GOAL_D15_PROGRAM_ROLLBACK_2026-10-16.md']
    return {path.relative_to(ROOT).as_posix(): dict(bytes=path.stat().st_size, sha256=sql.digest(path))
            for path in paths}


def unchanged(manifest):
    for name, row in manifest.items():
        path = ROOT / name
        assert path.stat().st_size == row['bytes'] and sql.digest(path) == row['sha256'], name
