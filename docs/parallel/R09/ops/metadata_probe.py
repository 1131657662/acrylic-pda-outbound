"""Repeat strict metadata reads, retaining differences; never retry past a failure."""
from pathlib import Path
import time
from mysql_io import Database, table_facts
from safety import read_json, write_json, require


def probe(settings, backup, rounds=5):
    expected = read_json(Path(backup) / 'database-facts.json')
    require(1 <= rounds <= 10, 'METADATA_ROUNDS_INVALID')
    started = time.monotonic()
    observations = []
    for side in ('source', 'restore'):
        db = Database(settings, side)
        for number in range(rounds):
            actual = table_facts(db)
            if actual != expected:
                write_json(db.root / f'metadata-differences-{time.time_ns()}.json',
                           {name: {'expected': expected.get(name), 'actual': actual.get(name)}
                            for name in expected.keys() | actual.keys() if expected.get(name) != actual.get(name)})
            require(actual == expected, 'METADATA_FACTS_DIFFER')
            observations.append({'side': side, 'round': number + 1, 'tables': len(actual)})
    result = {'status': 'PASS', 'seconds': round(time.monotonic() - started, 3), 'observations': observations,
              'historical_intermittent_failure_root_caused': False}
    write_json(Path(settings['root']) / 'metadata-probe.json', result)
    return result
