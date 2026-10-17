"""Read literal rows from the retained mysqldump without executing SQL."""
import re

TOKEN = re.compile(r"\s*(NULL|0x[0-9a-fA-F]+|-?\d+(?:\.\d+)?|'(?:\\.|[^'\\])*')\s*([,)])")
ESCAPES = {'0': '\0', 'n': '\n', 'r': '\r', 't': '\t', 'Z': '\x1a'}


def literal(text):
    if text == 'NULL':
        return None
    if text.startswith('0x'):
        return bytes.fromhex(text[2:]).hex()
    if text.startswith("'"):
        return re.sub(r'\\(.)', lambda m: ESCAPES.get(m[1], m[1]), text[1:-1])
    assert re.fullmatch(r'-?\d+', text), 'Unexpected noninteger evidence field'
    return int(text)


def values(text):
    rows, position = [], 0
    while position < len(text):
        assert text[position] == '('
        position += 1
        row = []
        while True:
            match = TOKEN.match(text, position)
            assert match, ('Unsupported dump literal', position)
            row.append(literal(match[1]))
            position = match.end()
            if match[2] == ')':
                break
        rows.append(row)
        if position < len(text):
            assert text[position] == ','
            position += 1
    return rows


def table(sql, name):
    assert re.fullmatch(r'[a-z_]+', name)
    definitions = re.findall(r'CREATE TABLE `' + name + r'` \(\n(.*?)\n\)', sql, re.S)
    assert len(definitions) == 1, (name, len(definitions))
    columns = re.findall(r'^  `([^`]+)` ', definitions[0], re.M)
    records = []
    for insert in re.findall(r'^INSERT INTO `' + name + r'` VALUES (.*);$', sql, re.M):
        for row in values(insert):
            assert len(row) == len(columns), name
            records.append(dict(zip(columns, row)))
    return records
