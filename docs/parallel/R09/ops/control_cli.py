"""Local control commands never open ordinary writes or execute DB recovery."""
from pathlib import Path
from control_disk import read
from control_paths import ControlLocation, checked
from control_store import ControlStore
from safety import require


def arguments(commands):
    for name in ('control-init', 'control-read', 'control-apply'):
        parser = commands.add_parser(name)
        parser.add_argument('--control-root', required=True)
        parser.add_argument('--installation-id', required=True)
        parser.add_argument('--restore-root', action='append', required=True)
        if name != 'control-read':
            parser.add_argument('--request', required=True, help='Canonical original JSON command file')
        if name != 'control-init':
            parser.add_argument('--checkpoint', required=True, help='Separately trusted canonical checkpoint file')


def execute(args):
    location = ControlLocation(Path(args.control_root), args.installation_id, tuple(args.restore_root))
    store = ControlStore(location)
    if args.command == 'control-init':
        return store.initialize(read(Path(args.request)))
    trusted = trusted_checkpoint(Path(args.checkpoint), store.location)
    if args.command == 'control-read':
        return store.inspect(trusted)
    return store.apply(read(Path(args.request)), trusted)


def trusted_checkpoint(path, location):
    path = checked(path)
    for root in (location.root, *(Path(item) for item in location.restore_roots)):
        require(not path.is_relative_to(root), 'CONTROL_CHECKPOINT_NOT_EXTERNAL')
    return read(path)
