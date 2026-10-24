"""Synthetic v2 identity inputs for a read-only consumer; never a product supplier."""
import hashlib
import json
from control_factory import fixture, write
from control_store import ControlStore
from control_writers import descriptor


def promoted():
    base, location, initial = fixture()
    store = ControlStore(location)
    result = store.initialize(initial)
    location = store.location
    identity = json.loads((location.root / 'identity.json').read_bytes())
    identity.update(formatVersion=2, authorityKind='INITIAL_NORMAL_INSTALL',
                    closedAuthority=str(base / 'closed'), leaseRoot=str(base / 'p5'))
    rewrite(location, identity)
    return base, location, initial, result


def rewrite(location, identity):
    write(location.root / 'identity.json', identity)
    write(location.root / 'writers.json', descriptor(identity, location.root))


def files(root):
    return {p.relative_to(root).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest()
            for p in root.rglob('*') if p.is_file()}
