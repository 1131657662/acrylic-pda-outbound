"""Single-machine recovery-control CAS store. Every outcome remains closed."""
from control_disk import append, locked, write_pointer, sync_directory
from control_format import canonical, hashed
from control_history import load, history_name
from control_model import command, create, checkpoint, view
from safety import require, secure_directory
from control_writers import descriptor, exclusive


class ControlStore:
    def __init__(self, location, fault=lambda point: None):
        self.location = location.validated()
        self.fault = fault

    def initialize(self, value):
        value = self._command(value)
        require(value['formatVersion'] == 1, 'CONTROL_INITIAL_FORMAT')
        require(value['expectedRevision'] == '0' and value['phase'] == 'QUIESCING',
                'CONTROL_INITIAL_PHASE')
        if not self.location.root.exists():
            self._bootstrap(value)
        with locked(self.location.root):
            records, number, identity = load(self.location)
            require(identity['initialCommandSha256'] == hashed(value), 'CONTROL_BOOTSTRAP_CONFLICT')
            with exclusive(self.location.root, identity):
                if records:
                    return self._retry(records, number, value)
                return self._commit(create(None, value))

    def inspect(self, trusted_checkpoint):
        with locked(self.location.root):
            records, number, _ = load(self.location, trusted_checkpoint, allow_initial_normal=True)
            require(records and number == len(records), 'CONTROL_COMMAND_INCOMPLETE')
            if (self.location.root / 'admission.json').exists():
                from control_intents import inspect
                inspect(self.location.root, records)
            return view(records[-1])

    def apply(self, value, trusted_checkpoint):
        value = self._command(value)
        with locked(self.location.root):
            records, number, identity = load(self.location, trusted_checkpoint)
            require(records, 'CONTROL_NOT_INITIALIZED')
            require(number == len(records) or value['commandId'] == records[-1]['command']['commandId'],
                    'CONTROL_COMMAND_INCOMPLETE')
            from control_writers import validate
            from control_admission import prepare
            validate(self.location.root, identity, required=True)
            from control_successor import before_append
            before_append(self.location.root, records, value)
            prepare(self.location.root, records, value, self.fault)
            with exclusive(self.location.root, identity):
                retry = self._retry(records, number, value)
                if retry is not None:
                    return retry
                return self._commit(create(records[-1], value))

    def _command(self, value):
        from control_format import decode
        value = command(decode(canonical(value)))  # Detach mutable caller input.
        require(value['installationId'] == self.location.installation_id, 'CONTROL_INSTALLATION_MISMATCH')
        return value

    def _bootstrap(self, value):
        require(self.location.root.parent.is_dir(), 'CONTROL_PARENT_MISSING')
        secure_directory(self.location.root)
        (self.location.root / 'history').mkdir(mode=0o700)
        identity = {**self.location.identity(), 'initialCommandSha256': hashed(value)}
        append(self.location.root / 'identity.json', canonical(identity))
        append(self.location.root / 'coordinator.lock', b'0')
        append(self.location.root / 'writers.lock', b'0')
        from control_admission import bootstrap
        bootstrap(self.location.root, value['installationId'])
        append(self.location.root / 'writers.json', canonical(descriptor(identity, self.location.root)))
        sync_directory(self.location.root.parent)
        self.fault('bootstrap_complete')

    def _retry(self, records, number, value):
        matches = [row for row in records if row['command']['commandId'] == value['commandId']]
        if matches:
            require(matches[0]['commandSha256'] == hashed(value), 'CONTROL_COMMAND_CONFLICT')
        if number != len(records):
            require(matches and matches[0] == records[-1], 'CONTROL_COMMAND_INCOMPLETE')
            write_pointer(self.location.root, checkpoint(records[-1]), self.fault)
        return view(matches[0]) if matches else None

    def _commit(self, record):
        target = self.location.root / 'history' / history_name(int(record['revision']))
        append(target, canonical(record))
        self.fault('history_appended')
        write_pointer(self.location.root, checkpoint(record), self.fault)
        return view(record)
