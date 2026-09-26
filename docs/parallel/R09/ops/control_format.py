"""Strict, bounded JSON encoding for the local recovery-control protocol."""
from datetime import datetime, timezone
import json
import re
from uuid import UUID
from safety import OpsError, require, digest

MAX_BYTES = 65536
ZERO_HASH = '0' * 64


def canonical(value):
    try:
        data = (json.dumps(value, ensure_ascii=False, sort_keys=True,
                           separators=(',', ':'), allow_nan=False) + '\n').encode('utf-8')
    except (ValueError, TypeError, UnicodeError):
        raise OpsError('CONTROL_JSON_INVALID') from None
    require(len(data) <= MAX_BYTES, 'CONTROL_JSON_TOO_LARGE')
    return data


def unique(pairs):
    result = {}
    for key, value in pairs:
        require(key not in result, 'CONTROL_DUPLICATE_KEY')
        result[key] = value
    return result


def decode(data):
    require(0 < len(data) <= MAX_BYTES, 'CONTROL_JSON_SIZE')
    try:
        value = json.loads(data.decode('utf-8'), object_pairs_hook=unique)
    except (ValueError, UnicodeError, RecursionError):
        raise OpsError('CONTROL_JSON_INVALID') from None
    require(canonical(value) == data, 'CONTROL_NONCANONICAL_JSON')
    return value


def fields(value, expected):
    require(type(value) is dict and set(value) == set(expected.split()), 'CONTROL_FIELDS')


def version(value):
    require(type(value) is int and value == 1, 'CONTROL_FORMAT_UNSUPPORTED')


def identifier(value):
    require(type(value) is str and bool(re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}', value)),
            'CONTROL_IDENTIFIER')


def uuid(value):
    require(type(value) is str, 'CONTROL_UUID')
    try:
        require(str(UUID(value)) == value and UUID(value).int != 0, 'CONTROL_UUID')
    except ValueError:
        raise OpsError('CONTROL_UUID') from None


def revision(value):
    require(type(value) is str and bool(re.fullmatch(r'0|[1-9][0-9]{0,17}', value)), 'CONTROL_REVISION')
    return int(value)


def checksum(value):
    require(type(value) is str and bool(re.fullmatch('[a-f0-9]{64}', value)), 'CONTROL_HASH')


def instant(value):
    require(type(value) is str and bool(re.fullmatch(r'\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(?:\.\d{1,6})?Z', value)),
            'CONTROL_TIME')
    try:
        return datetime.fromisoformat(value.replace('Z', '+00:00'))
    except ValueError:
        raise OpsError('CONTROL_TIME') from None


def now():
    return datetime.now(timezone.utc).isoformat(timespec='microseconds').replace('+00:00', 'Z')


def hashed(value):
    return digest(canonical(value))
