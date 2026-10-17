"""Offline decoding of full snapshots with narrow synthetic-reader login deltas."""
import base64
from collections import Counter
import json
import re
import uuid


def rows(table):
    return Counter(json.dumps(row, separators=(',', ':')) for row in table['rows'])


def decode(table, raw):
    return {name: base64.b64decode(value) if value is not None else None
            for name, value in zip(table['columns'], raw, strict=True)}


def addition(before, after):
    assert before['columns'] == after['columns']
    assert rows(before) <= rows(after), 'Original row lost or changed'
    delta = rows(after) - rows(before)
    assert delta.total() == 1, 'Require one new authentication row'
    return decode(after, json.loads(next(delta.elements())))


def schema(snapshot):
    result = snapshot['schema'].copy()
    result['operation_event'] = re.sub(r' AUTO_INCREMENT=\d+', '', result['operation_event'])
    return result


def next_id(snapshot):
    value = re.search(r' AUTO_INCREMENT=(\d+)', snapshot['schema']['operation_event'])
    return int(value[1]) if value else 1


def conserve(before, after, reader):
    assert before.keys() == after.keys()
    for name in ('files', 'flywayHistory', 'triggers'):
        assert before[name] == after[name], name
    assert schema(before) == schema(after)
    assert next_id(after) == next_id(before) + 1
    old, new = before['tables'], after['tables']
    assert old.keys() == new.keys()
    changed = {name for name in old if old[name] != new[name]}
    assert changed == {'operation_event', 'spring_session', 'spring_session_attributes'}, changed
    event = addition(old['operation_event'], new['operation_event'])
    assert event['actor_id'] == event['entity_id'] == uuid.UUID(reader['id']).bytes
    assert event['event_type'] == b'WEB_LOGIN' and event['entity_type'] == b'USER' and event['details'] == b'{}'
    assert int(event['id']) == next_id(before)
    session = addition(old['spring_session'], new['spring_session'])
    assert session['PRINCIPAL_NAME'].decode() == reader['username']
    attribute = addition(old['spring_session_attributes'], new['spring_session_attributes'])
    assert attribute['SESSION_PRIMARY_ID'] == session['PRIMARY_ID']
    assert attribute['ATTRIBUTE_NAME'] == b'SPRING_SECURITY_CONTEXT'
    return dict(tables=len(new), exactTables=len(new) - 3, addedRows=3,
                files=len(after['files']), triggers=len(after['triggers']))


def upgrade(before, after):
    assert before['files'] == after['files'] and before['database'] == after['database']
    for name, table in before['tables'].items():
        assert after['tables'][name] == table, name
        assert after['schema'][name] == before['schema'][name], name
    added = after['tables'].keys() - before['tables'].keys()
    assert added == {'independent_association_member'}
    assert after['tables']['independent_association_member']['rows'] == []
    assert after['flywayHistory'][:-1] == before['flywayHistory']
    assert all(row['success'] for row in after['flywayHistory'])
    return dict(originalTables=len(before['tables']), emptyAddedTable=True, historyPrefixPreserved=True)
