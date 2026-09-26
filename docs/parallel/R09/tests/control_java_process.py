"""Real child processes with bounded pipe handshakes and retained transcripts."""
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path, PureWindowsPath
import hashlib
import json
import os
import subprocess
import sys

MAIN = 'com.acrylic.outbound.recovery.RecoveryControlProcess'
PYTHON = Path(__file__).with_name('control_process.py')


def classpath(path):
    values = json.loads(Path(path).read_text(encoding='utf-8'))
    needed = ('jackson-core-', 'jackson-databind-', 'jackson-annotations-', 'jna-', 'jna-platform-')
    selected = [item for item in values if PureWindowsPath(item).name.startswith(needed)
                or item.replace('\\', '/').endswith(('/classes/java/main', '/classes/java/test'))]
    if os.name != 'nt':
        selected = [str(Path('/mnt') / PureWindowsPath(p).drive[0].lower()
                        / Path(*PureWindowsPath(p).parts[1:])) for p in selected]
    if not selected or any(not Path(item).exists() for item in selected):
        raise RuntimeError('Compiled Java harness/dependencies required; no conditional skip')
    return selected


def inventory(root):
    return {str(p.relative_to(root)): hashlib.sha256(p.read_bytes()).hexdigest()
            for p in root.rglob('*') if p.is_file()}


class Processes:
    def __init__(self, java, paths, log):
        self.java = [java, '-cp', os.pathsep.join(paths), MAIN]
        self.log = log
        self.children = []

    def start(self, args):
        child = subprocess.Popen(args, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                                 stderr=subprocess.PIPE, text=True, encoding='utf-8')
        self.children.append(child)
        self.record({'event': 'start', 'pid': child.pid, 'args': args})
        return child

    def ready(self, child):
        with ThreadPoolExecutor(max_workers=1) as pool:
            future = pool.submit(child.stdout.readline)
            try:
                line = future.result(timeout=20).strip()
            except Exception:
                child.kill()
                self.finish(child)
                raise
        self.record({'event': 'handshake', 'pid': child.pid, 'line': line})
        if line != 'LOCKED':
            raise AssertionError(f'Expected LOCKED, got {line!r}')

    def finish(self, child, value=None):
        try:
            output, error = child.communicate(value, timeout=20)
        except subprocess.TimeoutExpired:
            child.kill()
            output, error = child.communicate(timeout=10)
            self.record({'event': 'timeout', 'pid': child.pid, 'out': output, 'err': error})
            raise
        result = (child.returncode, output.strip(), error.strip())
        self.record({'event': 'exit', 'pid': child.pid, 'result': result})
        return result

    def run_java(self, *args):
        return self.finish(self.start([*self.java, *map(str, args)]))

    def hold_java(self, root, mode='hold'):
        child = self.start([*self.java, mode, str(root)])
        self.ready(child)
        return child

    def hold_python(self, base):
        child = self.start([sys.executable, str(PYTHON), str(base), 'lock'])
        self.ready(child)
        return child

    def close(self):
        for child in self.children:
            if child.poll() is None:
                child.kill()
                self.finish(child)

    def record(self, value):
        with self.log.open('a', encoding='utf-8') as stream:
            stream.write(json.dumps(value, ensure_ascii=False) + '\n')
