"""Exact nonempty fact conservation with a narrow authentication delta allowance."""
from collections import Counter
import json
import re

from d15_rollback_sql import LAB, save


def rows(value):
    return Counter(json.dumps(row) for row in value['rows'])


def decoded(value, row):
    return dict(zip(value['columns'], (bytes.fromhex(cell).decode('utf-8', errors='backslashreplace')
                                      if cell is not None else None for cell in row)))


def reader_delta(before, after, reader):
    assert before.keys() == after.keys()
    changed = {name for name in before if before[name] != after[name]}
    assert changed == {'app_user', 'role', 'role_permission', 'user_role', 'user_warehouse'}, changed
    added = {}
    for name in changed:
        assert rows(before[name]) <= rows(after[name])
        delta = list((rows(after[name]) - rows(before[name])).elements())
        assert len(delta) == 1, name
        added[name] = json.loads(delta[0])
    assert added['app_user'][after['app_user']['columns'].index('id')].lower() == reader['actor']
    assert schema('restored') == schema('baseline')
    assert json.loads((LAB / 'restored-files.json').read_bytes()) == json.loads((LAB / 'baseline-files.json').read_bytes())
    save('reader-added-rows.json', added)


def audit_delta(before, after, reader):
    assert rows(before) <= rows(after), 'Existing audit row changed'
    delta = list((rows(after) - rows(before)).elements())
    assert len(delta) == 1, 'Exactly one successful login per JVM'
    row = json.loads(delta[0])
    value = dict(zip(after['columns'], row))
    assert value['actor_id'].lower() == reader['actor'] == value['entity_id'].lower()
    assert bytes.fromhex(value['event_type']).decode() == 'WEB_LOGIN'
    assert bytes.fromhex(value['entity_type']).decode() == 'USER'
    assert json.loads(bytes.fromhex(value['details'])) == {}
    return decoded(after, row)


def schema(label):
    text = (LAB / (label + '-schema.sql')).read_text('utf-8')
    # Only the login audit's next id changes. Preserve all other DDL and counters.
    pattern = r'(CREATE TABLE `operation_event` \(.*?\) ENGINE=InnoDB) AUTO_INCREMENT=\d+'
    return re.sub(pattern, r'\1 AUTO_INCREMENT=<login-only>', text, flags=re.S)


def sessions(before, after, reader):
    for name in ('spring_session', 'spring_session_attributes'):
        assert rows(before[name]) <= rows(after[name]), 'Original session changed'
        assert len(after[name]['rows']) == len(before[name]['rows']) + 1
    session = list((rows(after['spring_session']) - rows(before['spring_session'])).elements())[0]
    value = decoded(after['spring_session'], json.loads(session))
    assert value['PRINCIPAL_NAME'] == reader['username']
    attribute = list((rows(after['spring_session_attributes']) - rows(before['spring_session_attributes'])).elements())[0]
    item = decoded(after['spring_session_attributes'], json.loads(attribute))
    assert item['SESSION_PRIMARY_ID'] == value['PRIMARY_ID']
    assert item['ATTRIBUTE_NAME'] == 'SPRING_SECURITY_CONTEXT'


def stage(before, after, options):
    assert before.keys() == after.keys()
    sessions(before, after, options['reader'])
    changed = []
    for name in before:
        assert before[name]['columns'] == after[name]['columns']
        assert before[name]['types'] == after[name]['types']
        if name in ('spring_session', 'spring_session_attributes'):
            changed.append(name)
        elif name == 'operation_event':
            event = audit_delta(before[name], after[name], options['reader'])
            changed.append(name)
        else:
            assert before[name] == after[name], name
    assert schema(options['before']) == schema(options['after']), 'Unexpected DDL or counter change'
    old_files = json.loads((LAB / (options['before'] + '-files.json')).read_bytes())
    assert old_files == json.loads((LAB / (options['after'] + '-files.json')).read_bytes())
    return dict(tables=len(after), exactTables=len(after) - 3, authenticationTables=changed,
                loginEvent=event, files=len(old_files))
