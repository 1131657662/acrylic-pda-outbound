"""Offline candidate for the SubmissionJson scalar-UTF8 profile; no business writer."""
import hashlib
import json
import re

LIMIT = 9007199254740991
PREFIXES = {
    'command': b'ACRYLIC-R06-C0/COMMAND/1\n',
    'receipt': b'ACRYLIC-R06-C0/RECEIPT/1\n',
    'control': b'ACRYLIC-R06-C0/DERIVED-CONTROL/1\n',
}


def unique_object(pairs):
    value = {}
    for key, item in pairs:
        if key in value:
            raise ValueError('DUPLICATE_KEY')
        value[key] = item
    return value


def disallowed_number(_):
    raise ValueError('NON_INTEGER')


def parse(raw):
    if not raw or len(raw.encode('utf-8')) > 33554432:
        raise ValueError('JSON_SIZE')
    value = json.loads(raw, object_pairs_hook=unique_object,
                       parse_float=disallowed_number, parse_constant=disallowed_number)
    canonical(value)
    return value


def ordered(value):
    if isinstance(value, dict):
        keys = sorted(value, key=lambda key: key.encode('utf-16-be'))
        return {key: ordered(value[key]) for key in keys}
    if isinstance(value, list):
        return [ordered(item) for item in value]
    if value is None or isinstance(value, (str, bool)):
        return value
    if type(value) is int and -LIMIT <= value <= LIMIT:
        return value
    raise ValueError('NON_SAFE_INTEGER_OR_TYPE')


def canonical(value):
    text = json.dumps(ordered(value), ensure_ascii=False, separators=(',', ':'))
    # Match whole escapes so literal backslash-u text keeps its original bytes.
    text = re.sub(r'\\(?:u00[0-1][0-9a-f]|.)', jackson_escape, text)
    return text.encode('utf-8')


def jackson_escape(match):
    token = match.group(0)
    return token[:2] + token[2:].upper() if token.startswith('\\u') else token


def digest(value, domain='payload'):
    prefix = b'' if domain == 'payload' else PREFIXES[domain]
    return hashlib.sha256(prefix + canonical(value)).hexdigest()


def raw_digest(value):
    return hashlib.sha256(value).hexdigest()
