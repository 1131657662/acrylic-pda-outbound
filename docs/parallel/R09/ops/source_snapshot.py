"""Export a pinned committed server into a new private lab; never change a checkout."""
import io
from pathlib import Path
import re
import tarfile
from lab import WORKTREE
from safety import child, require, run, sha, read_json


def export(settings, commit):
    require(bool(re.fullmatch('[0-9a-f]{40}', commit)), 'FULL_COMMIT_REQUIRED')
    root = Path(settings['root']) / 'source-code'
    require(not root.exists(), 'SOURCE_SNAPSHOT_EXISTS')
    data = run(['git', '-C', str(WORKTREE), 'archive', commit, 'server'])
    root.mkdir(mode=0o700)
    with tarfile.open(fileobj=io.BytesIO(data)) as archive:
        for member in archive:
            require(member.isdir() or member.isfile(), 'SOURCE_LINK_REFUSED')
            target = child(root, member.name)
            if member.isdir():
                target.mkdir(parents=True, exist_ok=True)
            else:
                target.parent.mkdir(parents=True, exist_ok=True)
                with archive.extractfile(member) as source, target.open('xb') as output:
                    output.write(source.read())
    return root


def release_jar(settings):
    release = read_json(Path(settings['root']) / 'release.json')
    root = Path(settings['root']) / 'source-code' if release.get('source_snapshot') else WORKTREE
    jar = root / 'server/build/libs/acrylic-outbound-server-0.1.0.jar'
    require(sha(jar) == release['jar_sha256'], 'RELEASE_JAR_CHANGED')
    return jar
