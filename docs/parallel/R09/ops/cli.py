"""R09 operator entry point. All failures are redacted and exit nonzero."""
import argparse
import json
from pathlib import Path
import sys
from safety import OpsError, read_json


def arguments():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='command', required=True)
    setup = commands.add_parser('setup')
    setup.add_argument('--mysql-bin', required=True)
    for name in ('seed', 'backup', 'restore', 'stop', 'api-probe', 'worker-probe', 'jobs', 'v28-faults', 'metadata-probe'):
        child = commands.add_parser(name)
        child.add_argument('--lab', required=True)
        if name in ('restore', 'metadata-probe'):
            child.add_argument('--backup', required=True)
        if name == 'seed':
            child.add_argument('--source-commit', help='Full committed server hash; adds V28 archive fixtures')
    verify = commands.add_parser('verify')
    verify.add_argument('--backup', required=True)
    legacy = commands.add_parser('verify-r02')
    legacy.add_argument('--backup', required=True)
    monitor = commands.add_parser('monitor')
    monitor.add_argument('--config', required=True)
    return parser.parse_args()


def execute(args):
    if args.command == 'verify-r02':
        from r02_audit import verify_r02
        return {'status': 'PASS', **verify_r02(args.backup)}
    if args.command == 'monitor':
        from monitor import collect
        return collect(read_json(args.config))
    if args.command == 'verify':
        from backup import verify
        verify(Path(args.backup))
        return {'status': 'PASS', 'scope': 'archive integrity only'}
    from lab import setup, load, stop
    if args.command == 'setup':
        return {'status': 'PASS', 'lab': str(setup(args.mysql_bin))}
    settings = load(args.lab)
    if args.command == 'worker-probe':
        from worker_probe import probe
        return probe(settings)
    if args.command == 'jobs':
        from jobs_monitor import collect
        from mysql_io import Database
        return collect(Database(settings, 'restore'))
    return lab_command(args, settings)


def lab_command(args, settings):
    from lab import stop
    if args.command == 'v28-faults':
        from v28_drill import drill
        return drill(settings)
    if args.command == 'metadata-probe':
        from metadata_probe import probe
        return probe(settings, args.backup)
    if args.command == 'backup':
        from backup import create
        return {'status': 'PASS', 'backup': str(create(settings))}
    if args.command == 'restore':
        from backup import restore
        return restore(settings, args.backup)
    if args.command == 'seed':
        from seed import seed
        seed(settings, args.source_commit)
    elif args.command == 'api-probe':
        from api_probe import probe
        return probe(settings)
    else:
        stop(settings)
    return {'status': 'PASS', 'command': args.command}


def main():
    try:
        result = execute(arguments())
    except Exception as error:
        result = {'status': 'FAIL', 'code': str(error) if isinstance(error, OpsError) else 'OPERATION_FAILED',
                  'note': 'Private artifacts retained; no SQL, credentials or child output printed.'}
    print(json.dumps(result, ensure_ascii=False))
    return 0 if result['status'] == 'PASS' else 2


if __name__ == '__main__':
    sys.exit(main())
