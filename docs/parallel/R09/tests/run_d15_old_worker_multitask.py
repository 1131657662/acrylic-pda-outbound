"""Run two old-producer export jobs through one new worker and both consumers."""
import json
import traceback

import d15_multitask_checks as checks
import d15_old_worker_http as http
import d15_old_worker_lab as lab
import d15_old_worker_process as process


def baseline():
    lab.fixture.prepare()
    restored = lab.fixture.facts('restored')
    identity = lab.fixture.input_identity()
    reader = lab.fixture.add_reader(identity)
    facts = lab.fixture.facts('baseline')
    lab.compare.reader_delta(restored, facts, reader)
    assert facts['background_job']['rows'] == []
    lab.sql.save('private/scenario.json', dict(identity=identity, reader=reader))
    return identity, reader, facts


def queue(identity, reader, index):
    label = f'old-queued-{index}'
    api = process.start(lab.JARS['old'][0], label)
    try:
        process.ready(api, label)
        return http.enqueue(identity, reader, label, result_name=f'{label}-http-result.json')
    finally:
        process.stop(api, label)


def worker(jobs):
    process_handle = process.start(lab.JARS['new'][0], 'new-worker', worker=True)
    try:
        for job, _ in jobs:
            process.completed(process_handle, job)
    finally:
        process.stop(process_handle, 'new-worker')
    return lab.fixture.facts('worked')


def download(version, identity, reader, jobs):
    stages = []
    for index, (job, key) in enumerate(jobs, 1):
        label = f'{version}-read-{index}'
        api = process.start(lab.JARS[version][0], label)
        try:
            process.ready(api, label)
            path, view = http.read_completed(label, identity, reader, job, key)
            stages.append((label, path, view, job))
        finally:
            process.stop(api, label)
    return stages


def scenario():
    identity, reader, base = baseline()
    jobs = [queue(identity, reader, index) for index in (1, 2)]
    queued = lab.fixture.facts('queued')
    checks.conservation(base, queued, reader, identity, [job for job, _ in jobs], 2,
                        {'WEB_LOGIN': 2, 'REPORT_EXPORT_REQUESTED': 2})
    checks.job_rows(queued, [job for job, _ in jobs], False)
    worked = worker(jobs)
    checks.conservation(base, worked, reader, identity, [job for job, _ in jobs], 2,
                        {'WEB_LOGIN': 2, 'REPORT_EXPORT_REQUESTED': 2,
                         'REPORT_EXPORT_GENERATED': 2})
    artifacts = checks.artifacts(worked, [job for job, _ in jobs])
    checks.files('baseline', 'worked', jobs, artifacts)
    stages = download('old', identity, reader, jobs) + download('new', identity, reader, jobs)
    after = lab.fixture.facts('after-downloads')
    checks.conservation(worked, after, reader, identity, [job for job, _ in jobs], 4,
                        {'WEB_LOGIN': 4, 'REPORT_EXPORT_DOWNLOADED': 4})
    checks.downloads(stages, artifacts)
    lab.sql.save('source667-after.json', lab.fixture.original_files())
    assert json.loads((lab.LAB / 'source667-before.json').read_bytes()) == lab.fixture.original_files()
    print('Two queued tasks, one worker, four authenticated downloads and independent consumers passed', flush=True)


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
        lab.sql.save('success.json', dict(scope='D15 two-task producer/worker compatibility',
                                          producer='old', worker='new', format=lab.FORMAT,
                                          basis=lab.BASIS, inputsUnchanged=True,
                                          sourcePreserved=True, productionDefaultsChanged=False))
    except BaseException:
        lab.sql.save('failure.json', dict(traceback=traceback.format_exc()))
        raise
    finally:
        lab.runtime.stop_mysql(database)


if __name__ == '__main__':
    main()
