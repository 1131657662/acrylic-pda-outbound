"""Deterministic evidence encoding."""
import hashlib
import json


def _unique(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError('Duplicate JSON property')
        result[key] = value
    return result


def parse(raw):
    return json.loads(raw, object_pairs_hook=_unique,
                      parse_constant=lambda _: fail('Non-finite JSON number'))


def canonical(value):
    return json.dumps(value, sort_keys=True, ensure_ascii=False,
                      separators=(',', ':'), allow_nan=False).encode('utf-8')


def digest(raw):
    return hashlib.sha256(raw).hexdigest()


def fail(message):
    raise ValueError(message)


def require(condition, message):
    if not condition:
        fail(message)
