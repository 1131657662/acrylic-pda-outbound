"""Bounded local health probes; emit aggregate status only and never send alerts."""
from datetime import datetime, timezone
import json
from pathlib import Path
import re
import shutil
import socket
import subprocess
import time
import urllib.request
import urllib.parse
import urllib.error
from backup import verify
from safety import require, read_json, OpsError


def timestamp(value):
    parsed = datetime.fromisoformat(value)
    require(parsed.tzinfo is not None, 'TIMESTAMP_TIMEZONE_REQUIRED')
    return parsed.timestamp()


def freshness(config, now):
    base = Path(config['directory'])
    require(base.is_dir(), 'BACKUP_DIRECTORY_MISSING')
    candidates = sorted(p for p in base.iterdir() if p.is_dir())
    require(bool(candidates), 'BACKUP_MISSING')
    # Newer partial/failed backup must not be hidden by an older valid snapshot.
    latest = candidates[-1]
    require(not latest.name.endswith('.partial'), 'INCOMPLETE_BACKUP_REFUSED')
    if config.get('format') == 'r02':
        from r02_audit import verify_r02
        result = verify_r02(latest)
        age = now - result['captured_timestamp']
        require(-300 <= age <= config['max_age_seconds'], 'BACKUP_STALE_OR_CLOCK_INVALID')
        return {'age_seconds': round(max(age, 0)), 'archive_verified': True, 'restore_verified': False}
    verify(latest)
    completed = read_json(latest / 'COMPLETE.json')
    snapshot = read_json(latest / 'snapshot.json')
    finished = timestamp(completed['finished_at'])
    captured = timestamp(snapshot['captured_at'])
    require(captured <= finished <= now + 300 and captured <= now + 300, 'BACKUP_CLOCK_INVALID')
    age = max(0, now - captured)
    require(age <= config['max_age_seconds'], 'BACKUP_STALE')
    return {'age_seconds': round(age), 'archive_verified': True, 'restore_verified': False}


def disk(config):
    values = shutil.disk_usage(config['path'])
    percent = values.free / values.total * 100
    require(values.free >= config['minimum_free_bytes'], 'DISK_FREE_BYTES_LOW')
    require(percent >= config['minimum_free_percent'], 'DISK_FREE_PERCENT_LOW')
    return {'free_bytes': values.free, 'free_percent': round(percent, 2)}


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def http(config):
    parsed = urllib.parse.urlsplit(config['url'])
    require(parsed.hostname == '127.0.0.1' and parsed.scheme == 'http', 'LOCAL_HTTP_REQUIRED')
    require(parsed.username is None and parsed.password is None and not parsed.query, 'HTTP_CREDENTIALS_REFUSED')
    require(parsed.path == '/api/v1/auth/csrf', 'UNSUPPORTED_HEALTH_PATH')
    start = time.monotonic()
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect())
    try:
        with opener.open(config['url'], timeout=config['timeout_seconds']) as response:
            require(response.status == 200, 'HTTP_STATUS_UNHEALTHY')
            data = json.loads(response.read(65537))
    except urllib.error.HTTPError as error:
        error.close()
        raise OpsError('HTTP_STATUS_UNHEALTHY') from None
    latency = round((time.monotonic() - start) * 1000)
    require(data.get('code') == 'OK', 'HTTP_ENVELOPE_UNHEALTHY')
    require(data.get('serviceMode') == 'NORMAL', 'SERVICE_NOT_NORMAL')
    require(data.get('serverRecoveryEpoch') not in (None, '', 'unknown'), 'SERVICE_EPOCH_UNAVAILABLE')
    require(latency <= config['max_latency_ms'], 'HTTP_LATENCY_HIGH')
    return {'latency_ms': latency, 'service_mode': 'NORMAL'}


def tcp(config):
    require(config['host'] == '127.0.0.1', 'LOCAL_TCP_REQUIRED')
    with socket.create_connection((config['host'], config['port']), timeout=3):
        return {'connected': True}


def service(config):
    unit = config['unit']
    require(bool(re.fullmatch(r'acrylic-outbound-test(?:-backup|-worker)?\.service', unit)), 'SERVICE_NOT_ALLOWLISTED')
    result = subprocess.run(['systemctl', 'show', unit, '--property=ActiveState,Result,NRestarts', '--no-pager'],
                            capture_output=True, text=True, timeout=5)
    require(result.returncode == 0, 'SERVICE_QUERY_FAILED')
    values = dict(line.split('=', 1) for line in result.stdout.splitlines() if '=' in line)
    require(values.get('Result') == 'success', 'SERVICE_FAILED')
    if not unit.endswith('-backup.service'):
        require(values.get('ActiveState') == 'active', 'SERVICE_INACTIVE')
    restarts = int(values.get('NRestarts', '0'))
    require(restarts <= config['max_restarts'], 'SERVICE_RESTART_LIMIT')
    return {'active_state': values.get('ActiveState'), 'restarts_since_start': restarts}


def check(name, probe):
    try:
        return {'check': name, 'status': 'PASS', **probe()}
    except Exception as error:
        return {'check': name, 'status': 'FAIL', 'code': str(error) if isinstance(error, OpsError) else 'PROBE_FAILED'}


def collect(config):
    now = time.time()
    results = [check('backup', lambda: freshness(config['backup'], now))]
    if 'restore_evidence' in config:
        from restore_monitor import latest_evidence
        results.append(check('restore_evidence', lambda: latest_evidence(config, now)))
    for name in ('http', 'tcp'):
        if name in config:
            results.append(check(name, lambda name=name: globals()[name](config[name])))
    for index, item in enumerate(config.get('disks', [])):
        results.append(check(f'disk_{index}', lambda item=item: disk(item)))
    for index, item in enumerate(config.get('services', [])):
        results.append(check(f'service_{index}', lambda item=item: service(item)))
    require(bool(config.get('disks')), 'DISK_PROBE_REQUIRED')
    return {'observed_at': datetime.now(timezone.utc).isoformat(),
            'status': 'PASS' if all(r['status'] == 'PASS' for r in results) else 'FAIL', 'checks': results}
