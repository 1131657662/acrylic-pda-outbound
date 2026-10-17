"""Independent nonmutating OS lock observation; never advances recovery authority."""
import argparse
import hashlib
import json
import msvcrt
from pathlib import Path


def observe(path):
    before = hashlib.sha256(path.read_bytes()).hexdigest()
    with path.open('r+b', buffering=0) as lock:
        try:
            msvcrt.locking(lock.fileno(), msvcrt.LK_NBLCK, 1)
        except OSError:
            status = 'BLOCKED'
        else:
            status = 'FREE'
            lock.seek(0)
            msvcrt.locking(lock.fileno(), msvcrt.LK_UNLCK, 1)
    assert hashlib.sha256(path.read_bytes()).hexdigest() == before
    return dict(status=status, path=str(path.resolve()), sha256=before, nonmutating=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('path', type=Path)
    parser.add_argument('--expect', choices=['BLOCKED', 'FREE'], required=True)
    args = parser.parse_args()
    result = observe(args.path)
    print(json.dumps(result))
    assert result['status'] == args.expect, result
