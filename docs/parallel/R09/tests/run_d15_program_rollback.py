"""New -> previous -> new program on one fresh restored nonempty database."""
import datetime
import json
import shutil
import subprocess

import d15_program_rollback_fixture as fixture
import d15_rollback_compare as compare
import d15_rollback_http as http
import d15_rollback_runtime as runtime
from d15_rollback_sql import LAB, ROOT, digest, identity, save

JARS = dict(new=(ROOT / 'server/build/libs/acrylic-outbound-server-0.1.0.jar',
    'e130aa2b7844daf6380045e3f3e809672b9c15f5ff31c4a24adf4cdf6bdc92ba'),
    old=(ROOT / '.local/goal-20261013/e06-conflict-audit-1535/server.jar',
    'adbd314f0fd619997dea0f16e0de36a963c3650173e991cbcf259ad1ae7184e1'))


def inputs():
    assert ROOT.resolve() == ROOT.__class__('D:/acrylic-pda-outbound').resolve()
    assert all(shutil.disk_usage(drive).free > minimum for drive, minimum in (('C:/', 5 << 30), ('D:/', 3 << 30)))
    for path, expected in JARS.values():
        assert digest(path) == expected, str(path)
    return dict(at=datetime.datetime.now().astimezone().isoformat(), jars={key: dict(path=str(path),
                bytes=path.stat().st_size, sha256=digest(path)) for key, (path, _) in JARS.items()},
                head=subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT).decode().strip(),
                disks={drive: shutil.disk_usage(drive)._asdict() for drive in ('C:/', 'D:/')})


def program(label, jar, state):
    identity(label + '-before')
    process = runtime.start_api(jar, label)
    try:
        http.ready(process)
        runtime.listener_identity(process, label)
        business = http.run(label, state['identity'], state['reader'])
    finally:
        runtime.stop_api(process, label)
    identity(label + '-after')
    current = fixture.facts(label)
    result = compare.stage(state['facts'], current, dict(reader=state['reader'],
                            before=state['label'], after=label))
    if 'business' in state:
        assert business == state['business'], 'Original business responses changed across JARs'
    state.update(facts=current, label=label, business=business)
    save(label + '-result.json', result)
    print(label + ': HTTP success, exact business/DDL/file conservation', flush=True)


def exercise():
    fixture.prepare()
    restored = fixture.facts('restored')
    selected = fixture.input_identity()
    reader = fixture.add_reader(selected)
    baseline = fixture.facts('baseline')
    # Verify the five setup rows exactly; all source business rows remain.
    compare.reader_delta(restored, baseline, reader)
    state = dict(identity=selected, reader=reader, facts=baseline, label='baseline')
    for label, version in (('new01', 'new'), ('old01', 'old'), ('new02', 'new')):
        program(label, JARS[version][0], state)
    save('source667-after.json', fixture.original_files())
    assert json.loads((LAB / 'source667-before.json').read_bytes()) == fixture.original_files()
    return dict(stages=3, httpRequests=24, tables=len(baseline),
                sourceFiles=604, result='PASS_BOUNDED_FORMAL_READ_COMPATIBILITY')


def main():
    pinned = inputs()
    runtime.create_lab()
    save('input.json', pinned)
    mysql = runtime.start_mysql()
    try:
        result = exercise()
    finally:
        runtime.stop_mysql(mysql)
    result['disksAfter'] = {drive: shutil.disk_usage(drive)._asdict() for drive in ('C:/', 'D:/')}
    save('result.json', result)
    print(json.dumps(result), flush=True)


if __name__ == '__main__':
    main()
