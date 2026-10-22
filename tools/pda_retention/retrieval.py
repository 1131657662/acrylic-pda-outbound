"""Re-read isolated archive files; a historical signature is not a health check."""
from pathlib import Path

from .codec import canonical, digest, parse, require
from .store import location, read


def verify(local, confirmation, context):
    paths = context.get('archiveFiles')
    require(isinstance(paths, dict) and set(paths) == {'payload', 'receipt'},
            'Explicit retrievable archive files required')
    require(all(isinstance(value, str) for value in paths.values()),
            'Archive file paths must be strings')
    require(all(Path(value).is_absolute() for value in paths.values()),
            'Absolute archive file paths required')
    resolved = {name: location(value) for name, value in paths.items()}
    require(resolved['payload'] != resolved['receipt'], 'Distinct archive files required')
    expected = {'payload': local['payload'].encode('utf-8'),
                'receipt': canonical(parse(local['receipt']))}
    for name, path in resolved.items():
        raw = read(path)
        require(raw == expected[name] and digest(raw) == confirmation[name + 'Sha256']
                and str(len(raw)) == confirmation[name + 'Bytes'],
                'Retrievable archive bytes differ: ' + name)
