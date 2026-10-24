"""Exact versioned identity shapes; v2 normal identities have no closed-control write purpose."""
from pathlib import Path
from control_format import fields, checksum, ZERO_HASH
from control_paths import checked
from safety import require

COMMON = 'formatVersion installationId restoreRoots initialCommandSha256'
NORMAL = COMMON + ' authorityKind closedAuthority leaseRoot'


def validate(identity, location, allow_initial_normal=False):
    require(type(identity) is dict, 'CONTROL_FIELDS')
    number = identity.get('formatVersion')
    require(type(number) is int and number in (1, 2), 'CONTROL_FORMAT_UNSUPPORTED')
    require(number != 2 or set(identity) != set(COMMON.split()), 'CONTROL_FORMAT_UNSUPPORTED')
    fields(identity, COMMON if number == 1 else NORMAL)
    checksum(identity['initialCommandSha256'])
    if number == 1:
        expected = {**location.identity(), 'initialCommandSha256': identity['initialCommandSha256']}
        require(identity == expected, 'CONTROL_INSTALLATION_MISMATCH')
        return identity
    require(allow_initial_normal is True and identity['authorityKind'] == 'INITIAL_NORMAL_INSTALL',
            'CONTROL_AUTHORITY_PURPOSE_FORBIDDEN')
    require(identity['initialCommandSha256'] != ZERO_HASH, 'CONTROL_BOOTSTRAP_MISMATCH')
    external_paths(identity, location)
    require(identity['installationId'] == location.installation_id, 'CONTROL_INSTALLATION_MISMATCH')
    require(identity['restoreRoots'] == list(location.restore_roots), 'CONTROL_RESTORE_SET_MISMATCH')
    return identity


def external_paths(identity, location):
    external = []
    for name in ('closedAuthority', 'leaseRoot'):
        value = identity[name]
        require(type(value) is str and bool(value), 'CONTROL_ABSOLUTE_PATH_REQUIRED')
        path = checked(value)
        require(str(path) == value, 'CONTROL_PATH_ALIAS')
        external.append(path)
    occupied = [location.root, *(Path(p) for p in location.restore_roots)]
    for path in external:
        for other in occupied:
            require(not path.is_relative_to(other) and not other.is_relative_to(path),
                    'CONTROL_AUTHORITY_PATH_OVERLAP')
        occupied.append(path)
