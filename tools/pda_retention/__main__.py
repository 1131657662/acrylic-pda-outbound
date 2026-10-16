"""Offline synthetic-copy maintenance CLI; no production mode."""
import argparse
import json
import sys

from .consumer import consume, prepare


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='command', required=True)
    preparation = commands.add_parser('prepare')
    preparation.add_argument('synthetic_spec')
    consumption = commands.add_parser('consume')
    consumption.add_argument('run')
    consumption.add_argument('--expected-plan-sha256', required=True)
    consumption.add_argument('--enable-isolated-deletion', action='store_true')
    args = parser.parse_args()
    try:
        if args.command == 'prepare':
            result = prepare(args.synthetic_spec)
        else:
            result = consume(args.run, args.expected_plan_sha256, args.enable_isolated_deletion)
        print(json.dumps(result, sort_keys=True))
        return 0
    except Exception as error:
        print(json.dumps(dict(state='REJECTED_OR_INCOMPLETE', errorType=type(error).__name__,
                              message=str(error), instruction='Preserve run and inspect attempt/database facts')))
        return 1


if __name__ == '__main__':
    sys.exit(main())
