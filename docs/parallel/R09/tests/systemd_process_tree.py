"""Isolated user-service process hierarchy; never opens project databases or services."""
from pathlib import Path
import os
import signal
import subprocess
import sys


def main():
    mode, root = sys.argv[1], Path(sys.argv[2])
    # Make systemd exercise its final kill; successful return cannot mean TERM was merely sent.
    signal.signal(signal.SIGTERM, signal.SIG_IGN)
    if mode == 'leaf':
        print(f'LEAF {os.getpid()}', flush=True)
        signal.pause()
        return
    child = subprocess.Popen([sys.executable, __file__, 'leaf', str(root)],
                             stdout=subprocess.PIPE, text=True)
    if child.stdout.readline().strip() != f'LEAF {child.pid}':
        raise RuntimeError('Missing child readiness')
    (root / 'ready').write_text(f'{os.getpid()} {child.pid}\n')
    signal.pause()


if __name__ == '__main__':
    main()
