"""Pinned SQL/file preparation for one read-only program rollback experiment."""
import json
import shutil
import uuid

import bcrypt

from d15_rollback_sql import LAB, ROOT, SOURCE, digest, query, save, sql, dump_schema


def original_files():
    manifest = ROOT / '.local/goal-20261016/e07-formal-repository01/consumer02/manifest-after.json'
    rows = json.loads(manifest.read_bytes())
    assert len(rows) == 604
    for row in rows:
        path = (SOURCE / row['path']).resolve()
        assert path.is_relative_to(SOURCE) and path.stat().st_size == row['length']
        assert digest(path) == row['sha256'], row['path']
    return dict(files=len(rows), bytes=sum(row['length'] for row in rows), manifestSha256=digest(manifest))


def prepare():
    save('source667-before.json', original_files())
    assert query('SHOW TABLES') == []
    source = SOURCE / 'private/archive-snapshot.sql'
    save('import-input.json', dict(source=str(source), sha256=digest(source), bytes=source.stat().st_size))
    sql(source.read_bytes(), 'import')
    shutil.copytree(SOURCE / 'storage', LAB / 'storage')
    for name in ('server-private.der', 'server-public.der', 'formal-http-input.json'):
        shutil.copy2(SOURCE / 'private' / name, LAB / 'private' / name)


def files():
    paths = list((LAB / 'storage').rglob('*')) + list((LAB / 'private').glob('server-*.der'))
    paths += [LAB / 'private/formal-http-input.json']
    return {path.relative_to(LAB).as_posix(): dict(bytes=path.stat().st_size, sha256=digest(path))
            for path in paths if path.is_file()}


def facts(label):
    columns = query("SELECT JSON_ARRAY(TABLE_NAME,COLUMN_NAME,DATA_TYPE) FROM information_schema.columns "
                    "WHERE TABLE_SCHEMA=DATABASE() ORDER BY TABLE_NAME,ORDINAL_POSITION")
    result = {}
    for table, column, kind in columns:
        assert table.replace('_', '').isalnum() and column.replace('_', '').isalnum()
        result.setdefault(table, dict(columns=[], types=[], rows=[]))
        result[table]['columns'].append(column)
        result[table]['types'].append(kind)
    statements = []
    for table, value in result.items():
        fields = ','.join('IF(`' + col + '` IS NULL,NULL,HEX(CAST(`' + col + '` AS BINARY)))'
                          for col in value['columns'])
        statements.append("SELECT JSON_ARRAY('" + table + "',JSON_ARRAY(" + fields + ')) FROM `' + table + '`;')
    for table, row in query('\n'.join(statements)):
        result[table]['rows'].append(row)
    for value in result.values():
        value['rows'].sort(key=lambda row: json.dumps(row))
    save(label + '-facts.json', result)
    dump_schema(label)
    save(label + '-files.json', files())
    return result


def input_identity():
    original = json.loads((LAB / 'private/formal-http-input.json').read_bytes())
    assert query('SELECT JSON_ARRAY(COUNT(*)) FROM independent_association_member') == [[0]]
    assert query('SELECT JSON_ARRAY(COUNT(*)) FROM submission')[0][0] > 0
    return dict(warehouse=str(uuid.UUID(original['session']['warehouseId'])),
                order=str(uuid.UUID(original['assignment']['orderId'])))


def add_reader(identity):
    actor, role = uuid.uuid4().hex, uuid.uuid4().hex
    password = uuid.uuid4().hex + 'Aa1!'
    hashed = bcrypt.hashpw(password.encode(), bcrypt.gensalt()).decode()
    warehouse = uuid.UUID(identity['warehouse']).hex
    statements = ['START TRANSACTION;',
        f"INSERT INTO app_user(id,username,display_name,password_hash,created_at) VALUES(X'{actor}',"
        f"'d15-reader-673','SYNTHETIC D15 query only','{hashed}',UTC_TIMESTAMP(3));",
        f"INSERT INTO role VALUES(X'{role}','D15_READER_673','Synthetic isolated reader');",
        f"INSERT INTO role_permission SELECT X'{role}',id FROM permission WHERE code='order:read';",
        f"INSERT INTO user_role VALUES(X'{actor}',X'{role}');",
        f"INSERT INTO user_warehouse VALUES(X'{actor}',X'{warehouse}');", 'COMMIT;']
    sql('\n'.join(statements).encode(), 'reader-setup')
    save('private/web-reader.json', dict(actor=actor, role=role, username='d15-reader-673', password=password))
    return dict(actor=actor, role=role, username='d15-reader-673', password=password)
