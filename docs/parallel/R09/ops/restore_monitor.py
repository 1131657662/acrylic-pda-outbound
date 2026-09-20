"""Bind a local restore report to a verified backup; this never releases recovery."""
from pathlib import Path
from monitor import timestamp
from safety import require, read_json, regular, sha
from backup import verify


def latest_evidence(config, now):
    base = Path(config['backup']['directory'])
    candidates = sorted(path for path in base.iterdir() if path.is_dir())
    require(bool(candidates), 'BACKUP_MISSING')
    target = config['restore_evidence']
    require(Path(target['backup']).resolve() == candidates[-1].resolve(), 'LATEST_BACKUP_NOT_RESTORE_VERIFIED')
    return evidence(target, now)


def evidence(config, now):
    backup = Path(config['backup'])
    require(not backup.name.endswith('.partial'), 'INCOMPLETE_BACKUP_REFUSED')
    verify(backup)
    report = read_json(regular(Path(config['report'])))
    require(report.get('status') == 'PASS', 'RESTORE_NOT_VERIFIED')
    require(report.get('scope') == 'isolated snapshot restore; no disaster reconstruction', 'RESTORE_SCOPE_MISMATCH')
    require(report.get('backup_sums_sha256') == sha(backup / 'SHA256SUMS'), 'RESTORE_BACKUP_MISMATCH')
    require(report.get('business') == read_json(backup / 'business.json'), 'RESTORE_BUSINESS_MISMATCH')
    require(report.get('tables') == len(read_json(backup / 'database-facts.json')), 'RESTORE_TABLE_COUNT_MISMATCH')
    release = read_json(backup / 'manifest.json')
    require(release.get('base_commit') == config['source_commit'] and release.get('schema') == config['schema'],
            'RESTORE_BASELINE_MISMATCH')
    completed = timestamp(read_json(backup / 'COMPLETE.json')['finished_at'])
    verified = timestamp(report['verified_at'])
    require(completed <= verified <= now + 300, 'RESTORE_CLOCK_INVALID')
    require(now - verified <= config['max_age_seconds'], 'RESTORE_EVIDENCE_STALE')
    return {'restore_verified': True, 'schema': release['schema'], 'age_seconds': round(max(now - verified, 0)),
            'scope': 'isolated_snapshot_only', 'disaster_reconstruction_verified': False, 'local_deletion_allowed': False}
