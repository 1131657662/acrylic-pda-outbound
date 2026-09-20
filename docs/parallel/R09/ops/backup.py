"""Complete-marker backups and verified empty-target restores for the R09 lab."""
from datetime import datetime, timezone
import gzip
from pathlib import Path
import secrets
import shutil
import time
from archive import pack, extract, gzip_check, verify_archive
from mysql_io import Database, table_facts, normalized_schema
from safety import require, regular, sha, inventory, read_json, write_json, exclusive

COMPONENTS = {'database.sql.gz', 'database-facts.json', 'private-files-and-config.tar.gz',
              'schema.sql', 'business.json', 'manifest.json', 'snapshot.json'}


def utc_now():
    return datetime.now(timezone.utc).isoformat()


def logical_hash(db, destination):
    db.dump(destination, ['--no-create-info', '--skip-triggers'])
    result = sha(destination)
    destination.unlink()  # Exact newly created dump inside the task's private backup/restore directory.
    return result


def create(settings):
    with exclusive(settings['root']):
        return capture_backup(settings)


def capture_backup(settings):
    db = Database(settings, 'source')
    db.verify()
    assert_no_writers(db)
    require(db.query('SELECT @@super_read_only,@@read_only;', 'root') == '0\t0', 'SOURCE_ALREADY_READ_ONLY')
    base = Path(settings['root']) / 'backups'
    base.mkdir(exist_ok=True, mode=0o700)
    name = datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ') + '-' + secrets.token_hex(3)
    pending = base / (name + '.partial')
    pending.mkdir(mode=0o700)
    started = utc_now()
    db.query('SET GLOBAL super_read_only=ON;', 'root')
    try:
        capture(db, pending, started)
    finally:
        db.verify()
        db.query('SET GLOBAL super_read_only=OFF; SET GLOBAL read_only=OFF;', 'root')
    write_json(pending / 'COMPLETE.json', {'format': 'r09-lab-v1', 'finished_at': utc_now(),
                                        'sums_sha256': sha(pending / 'SHA256SUMS')})
    verify(pending)
    final = base / name
    pending.rename(final)
    return final


def assert_no_writers(db):
    # Dedicated instance: all foreground connections must be gone before file/DB capture.
    query = "SELECT COUNT(*) FROM information_schema.PROCESSLIST WHERE ID<>CONNECTION_ID() "
    query += "AND USER NOT IN ('event_scheduler','system user');"
    require(db.query(query, 'root') == '0', 'SOURCE_CLIENTS_STILL_CONNECTED')


def capture(db, destination, started):
    from consistency import inspect
    require(db.query('SELECT @@super_read_only;') == '1', 'SOURCE_NOT_FROZEN')
    roots = {'files': db.root / 'files', 'config': db.root / 'config'}
    before = table_facts(db)
    trees = {label: inventory(root) for label, root in roots.items()}
    require({'private.pk8', 'public.der', 'runtime.json'} <= set(trees['config']), 'SIGNING_CONFIGURATION_INCOMPLETE')
    business = inspect(db, roots)
    db.dump(destination / 'database.sql')
    with (destination / 'database.sql').open('rb') as source, gzip.open(destination / 'database.sql.gz', 'xb') as target:
        shutil.copyfileobj(source, target)
    (destination / 'database.sql').unlink()
    db.dump(destination / 'schema.sql', ['--no-data'])
    logical = logical_hash(db, destination / 'data-only.sql')
    pack(destination / 'private-files-and-config.tar.gz', roots)
    require(before == table_facts(db), 'SOURCE_DATABASE_CHANGED')
    require(trees == {label: inventory(root) for label, root in roots.items()}, 'SOURCE_FILES_CHANGED')
    write_json(destination / 'database-facts.json', before)
    write_json(destination / 'business.json', business)
    shutil.copyfile(Path(db.settings['root']) / 'release.json', destination / 'manifest.json')
    write_json(destination / 'snapshot.json', {'started_at': started, 'captured_at': utc_now(),
        'format': 'r09-lab-v1', 'database': db.name, 'source_port': db.port,
        'mysql_version': db.query('SELECT VERSION();'), 'logical_data_sha256': logical, 'trees': trees})
    lines = [f'{sha(destination / name)}  {name}\n' for name in sorted(COMPONENTS)]
    (destination / 'SHA256SUMS').write_text(''.join(lines), encoding='ascii')


def verify(backup):
    backup = Path(backup)
    require(backup.is_dir() and not backup.is_symlink() and not backup.is_junction(), 'INVALID_BACKUP_DIRECTORY')
    complete = read_json(regular(backup / 'COMPLETE.json'))
    require(complete['format'] == 'r09-lab-v1', 'UNSUPPORTED_BACKUP_FORMAT')
    require(sha(regular(backup / 'SHA256SUMS')) == complete['sums_sha256'], 'CHECKSUM_LIST_MISMATCH')
    entries = {}
    for line in (backup / 'SHA256SUMS').read_text(encoding='ascii').splitlines():
        checksum, name = line.split('  ', 1)
        require(name in COMPONENTS and name not in entries, 'CHECKSUM_MEMBER_SET_MISMATCH')
        require(sha(regular(backup / name)) == checksum, 'BACKUP_CHECKSUM_MISMATCH')
        entries[name] = checksum
    require(set(entries) == COMPONENTS, 'BACKUP_COMPONENT_MISSING')
    snapshot = read_json(backup / 'snapshot.json')
    require(snapshot['format'] == complete['format'], 'SNAPSHOT_FORMAT_MISMATCH')
    require(set(snapshot['trees']) == {'files', 'config'}, 'SNAPSHOT_TREE_MISMATCH')
    require({'private.pk8', 'public.der', 'runtime.json'} <= set(snapshot['trees']['config']), 'SIGNING_CONFIGURATION_INCOMPLETE')
    gzip_check(backup / 'database.sql.gz')
    verify_archive(backup / 'private-files-and-config.tar.gz', archive_inventory(snapshot))
    return snapshot


def archive_inventory(snapshot):
    return {f'{label}/{name}': info for label, tree in snapshot['trees'].items() for name, info in tree.items()}


def restore(settings, backup):
    with exclusive(settings['root']):
        return restore_backup(settings, backup)


def restore_backup(settings, backup):
    from consistency import inspect
    started = time.monotonic()
    backup = Path(backup).resolve()
    require(backup.parent == Path(settings['root']) / 'backups', 'BACKUP_OUTSIDE_LAB')
    require(not backup.name.endswith('.partial'), 'INCOMPLETE_BACKUP_REFUSED')
    snapshot = verify(backup)  # No database or storage writes before full archive validation.
    db = Database(settings, 'restore')
    db.verify()
    require(not db.query('SHOW TABLES;'), 'RESTORE_DATABASE_NOT_EMPTY')
    destination = db.root / 'recovered'
    extract(backup / 'private-files-and-config.tar.gz', archive_inventory(snapshot), destination)
    with gzip.open(backup / 'database.sql.gz', 'rb') as source, (destination / 'database.sql').open('xb') as target:
        shutil.copyfileobj(source, target)
    db.import_file(destination / 'database.sql')
    (destination / 'database.sql').unlink()
    require(table_facts(db) == read_json(backup / 'database-facts.json'), 'RESTORED_TABLES_DIFFER')
    db.dump(destination / 'schema.sql', ['--no-data'])
    schemas = [normalized_schema(p.read_text(encoding='utf-8')) for p in (destination / 'schema.sql', backup / 'schema.sql')]
    require(schemas[0] == schemas[1], 'RESTORED_SCHEMA_DIFFERS')
    require(logical_hash(db, destination / 'data-only.sql') == snapshot['logical_data_sha256'], 'RESTORED_DATA_DIFFERS')
    business = inspect(db, {'files': destination / 'files', 'config': destination / 'config'})
    require(business == read_json(backup / 'business.json'), 'RESTORED_BUSINESS_DIFFERS')
    result = {'status': 'PASS', 'verified_at': utc_now(), 'seconds': round(time.monotonic() - started, 3),
        'database': db.name, 'port': db.port, 'tables': len(table_facts(db)), 'business': business,
        'backup_sums_sha256': sha(backup / 'SHA256SUMS'), 'scope': 'isolated snapshot restore; no disaster reconstruction'}
    write_json(destination / 'verification.json', result)
    return result
