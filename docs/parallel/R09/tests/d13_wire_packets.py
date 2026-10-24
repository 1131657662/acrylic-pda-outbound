"""Small MySQL classic-protocol framing for an isolated transparent fault proxy."""


def exact(stream, size):
    chunks = bytearray()
    while len(chunks) < size:
        value = stream.recv(size - len(chunks))
        if not value:
            raise EOFError('Peer closed an actual TCP stream')
        chunks.extend(value)
    return bytes(chunks)


def packet(stream):
    header = exact(stream, 4)
    size = int.from_bytes(header[:3], 'little')
    if size > 16 * 1024 * 1024 - 1:
        raise ValueError('Classic packet length outside protocol limit')
    return header + exact(stream, size)


def thread_id(greeting):
    payload = greeting[4:]
    if greeting[3] != 0 or not payload or payload[0] != 10:
        raise ValueError('Expected an actual initial MySQL classic greeting')
    start = payload.index(0, 1) + 1
    if len(payload) < start + 4:
        raise ValueError('Truncated server thread identity')
    return int.from_bytes(payload[start:start + 4], 'little')


def is_commit(value):
    if value[3] != 0 or value[4:5] != b'\x03':
        return False
    sql = value[5:]
    # CLIENT_QUERY_ATTRIBUTES adds the two zero-attribute lenenc values.
    if sql.startswith(b'\x00\x01'):
        sql = sql[2:]
    return sql.strip().lower() == b'commit'


def commit_ok(value):
    # A COMMIT OK has no affected rows or last insert id. Warnings may be nonzero.
    if value[3] != 1 or len(value) < 11 or value[4:7] != b'\x00\x00\x00':
        raise ValueError('Selected COMMIT did not receive the original server OK')
    status = int.from_bytes(value[7:9], 'little')
    if status & 1:
        raise ValueError('Server still reports an active transaction after COMMIT')
    return dict(sequence=value[3], statusFlags=status, warnings=int.from_bytes(value[9:11], 'little'))
