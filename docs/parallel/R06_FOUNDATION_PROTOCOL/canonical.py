"""Independent scalar UTF-8 consumer; does not import C0 or production encoders."""
import hashlib
import json

MAX_BYTES = 262144
SAFE_INTEGER = 9007199254740991
PREFIXES = {
    "command": b"ACRYLIC-R06/COMMAND/1\n",
    "receipt": b"ACRYLIC-R06/RECEIPT/1\n",
    "control": b"ACRYLIC-R06/DERIVED-CONTROL/1\n",
}
SHORT_ESCAPES = {8: r"\b", 9: r"\t", 10: r"\n", 12: r"\f", 13: r"\r"}


def reject_number(_):
    raise ValueError("NON_INTEGER")


def unique_members(pairs):
    result = {}
    for name, value in pairs:
        if name in result:
            raise ValueError("DUPLICATE_MEMBER")
        result[name] = value
    return result


def parse(raw):
    if isinstance(raw, str):
        raw = raw.encode("utf-8", errors="strict")
    if not 0 < len(raw) <= MAX_BYTES:
        raise ValueError("SIZE")
    value = json.loads(raw.decode("utf-8", errors="strict"),
                       object_pairs_hook=unique_members,
                       parse_float=reject_number, parse_constant=reject_number)
    canonical(value)
    return value


def quote(value):
    value.encode("utf-8", errors="strict")
    escaped = []
    for character in value:
        number = ord(character)
        if character in ('"', "\\"):
            escaped.append("\\" + character)
        elif number < 32:
            escaped.append(SHORT_ESCAPES.get(number, "\\u%04X" % number))
        else:
            escaped.append(character)
    return '"' + "".join(escaped) + '"'


def encode(value):
    if value is None:
        return "null"
    if isinstance(value, bool):
        return "true" if value else "false"
    if isinstance(value, str):
        return quote(value)
    if type(value) is int and abs(value) <= SAFE_INTEGER:
        return str(value)
    if isinstance(value, list):
        return "[" + ",".join(encode(item) for item in value) + "]"
    if isinstance(value, dict):
        return encode_object(value)
    raise ValueError("UNSUPPORTED_VALUE")


def encode_object(value):
    if any(not isinstance(key, str) for key in value):
        raise ValueError("MEMBER_NAME")
    keys = sorted(value, key=lambda key: key.encode("utf-16-be", errors="strict"))
    return "{" + ",".join(quote(key) + ":" + encode(value[key]) for key in keys) + "}"


def canonical(value):
    return encode(value).encode("utf-8", errors="strict")


def digest(value, domain):
    return hashlib.sha256(PREFIXES[domain] + canonical(value)).hexdigest()


def raw_digest(raw):
    return hashlib.sha256(raw).hexdigest()
