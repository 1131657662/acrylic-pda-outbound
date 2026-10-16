"""New HTTP producer -> retained older product worker -> old/new HTTP consumers."""
import json
import traceback

import d15_old_worker_lab as lab
import d15_old_worker_process as process
import d15_old_worker_http as http
import d15_old_worker_checks as checks


def baseline():
    lab.fixture.prepare()
    restored = lab.fixture.facts('restored')
    identity = lab.fixture.input_identity()
    reader = lab.fixture.add_reader(identity)
    facts = lab.fixture.facts('baseline')
    lab.compare.reader_delta(restored, facts, reader)
    assert facts['background_job']['rows'] == [], 'Require empty retained job queue'
    lab.sql.save('private/scenario.json', dict(identity=identity, reader=reader))
    return identity, reader, facts


def queued(identity, reader):
    api = process.start(lab.JARS['new'][0], 'new-queued')
    try:
        process.ready(api, 'new-queued')
        job, key = http.enqueue(identity, reader)
    finally:
        process.stop(api, 'new-queued')
    return job, key, lab.fixture.facts('queued')


def run_worker(job):
    worker = process.start(lab.JARS['old'][0], 'old-worker', worker=True)
    try:
        process.completed(worker, job)
    finally:
        process.stop(worker, 'old-worker')
    return lab.fixture.facts('worked')


def downloaded(label, version, identity, reader, job, key=None):
    api = process.start(lab.JARS[version][0], label)
    try:
        process.ready(api, label)
        file, view = http.read_completed(label, identity, reader, job, key)
    finally:
        process.stop(api, label)
    return file, view, lab.fixture.facts(label)


def verify(base, queued_facts, worked, stages, options):
    counts = {'WEB_LOGIN': 1, 'REPORT_EXPORT_REQUESTED': 1}
    results = {}
    results['queued'] = checks.conservation(base, queued_facts, options | dict(label='queued', logins=1, events=counts))
    counts = counts | {'REPORT_EXPORT_GENERATED': 1}
    results['worked'] = checks.conservation(base, worked, options | dict(label='worked', logins=1, events=counts))
    payload, artifact = checks.job_delta(queued_facts, worked, options['job'])
    for index, (label, file, view, facts) in enumerate(stages, 2):
        counts = counts | dict(WEB_LOGIN=index, REPORT_EXPORT_DOWNLOADED=index - 1)
        results[label] = checks.conservation(base, facts, options | dict(label=label, logins=index, events=counts))
        assert facts['background_job'] == worked['background_job']
        results[label]['xlsx'] = checks.workbook(file, payload, artifact)
        assert view['sha256'] == artifact['sha256']
    results['files'] = checks.final_files(payload, artifact, options['job'])
    lab.sql.save('consumer01.json', results)


def scenario():
    identity, reader, base = baseline()
    print(lab.LAB.name + ': original nonempty facts restored; five synthetic reader rows verified', flush=True)
    job, key, queued_facts = queued(identity, reader)
    print('New JAR real HTTP PENDING/idempotency/permission paths completed', flush=True)
    worked = run_worker(job)
    print('Retained old product scheduled worker completed original task', flush=True)
    stages = []
    for label, version, replay in [('old-read', 'old', None), ('new-read', 'new', key)]:
        file, view, facts = downloaded(label, version, identity, reader, job, replay)
        stages.append((label, file, view, facts))
        print(label + ': real HTTP status/download completed', flush=True)
    verify(base, queued_facts, worked, stages, dict(identity=identity, reader=reader, job=job))
    lab.sql.save('source667-after.json', lab.fixture.original_files())
    assert json.loads((lab.LAB / 'source667-before.json').read_bytes()) == lab.fixture.original_files()
    print('Exact database/file and independent XLSX consumers passed', flush=True)


def main():
    allocation = lab.allocation()
    manifest = lab.inputs()
    lab.runtime.create_lab()
    lab.sql.save('allocation.json', allocation)
    lab.sql.save('input.json', manifest)
    database = lab.runtime.start_mysql()
    try:
        scenario()
        lab.unchanged(manifest)
        lab.sql.save('success.json', dict(scope='D15 old scheduled worker original-confirmed XLSX only',
            inputsUnchanged=True, sourcePreserved=True, productionDefaultsChanged=False))
    except BaseException:
        lab.sql.save('failure.json', dict(traceback=traceback.format_exc()))
        raise
    finally:
        lab.runtime.stop_mysql(database)


if __name__ == '__main__':
    main()
