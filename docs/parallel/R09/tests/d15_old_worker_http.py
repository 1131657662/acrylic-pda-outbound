"""Record actual HTTP export requests and binary downloads under pinned programs."""
import hashlib
import http.cookiejar
import json
import urllib.error
import urllib.parse
import urllib.request
import uuid

from d15_old_worker_lab import LAB, HTTP, sql


class Client:
    def __init__(self, label):
        self.label, self.index = label, 0
        self.client = urllib.request.build_opener(urllib.request.ProxyHandler({}),
            urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
        self.headers = {}
        (LAB / 'private' / label).mkdir()

    def request(self, path, status=200, body=None, headers=None, binary=False):
        self.index += 1
        request = urllib.request.Request(f'http://127.0.0.1:{HTTP}/api/v1' + path,
            data=body, headers=self.headers | (headers or {}))
        try:
            response = self.client.open(request, timeout=30)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            raw, code, returned = response.read(), response.code, dict(response.headers)
        name = f'private/{self.label}/{self.index:02}'
        (LAB / (name + ('.xlsx' if binary else '.json'))).write_bytes(raw)
        sql.save(name + '-http.json', dict(path=path, method=request.get_method(), status=code,
            expected=status, bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest(), responseHeaders=returned,
            requestBodySha256=hashlib.sha256(body).hexdigest() if body else None))
        assert code == status, (path, code, raw[:500])
        if binary:
            return raw
        parsed = json.loads(raw)
        assert code != 200 or (parsed['code'] == 'OK' and parsed['serviceMode'] != 'UNAVAILABLE')
        return parsed

    def login(self, reader):
        csrf = self.request('/auth/csrf')['data']
        headers = {csrf['headerName']: csrf['token'], 'Content-Type': 'application/x-www-form-urlencoded'}
        body = urllib.parse.urlencode(dict(username=reader['username'], password=reader['password'])).encode()
        assert self.request('/auth/web/login', body=body, headers=headers)['data']['authenticated']
        csrf = self.request('/auth/csrf')
        self.headers = {csrf['data']['headerName']: csrf['data']['token']}
        self.epoch = csrf['serverRecoveryEpoch']

    def create(self, identity, key, status=200, format_name='XLSX'):
        body = json.dumps(dict(format=format_name, basis='ORIGINAL_CONFIRMED')).encode()
        headers = {'Idempotency-Key': str(key), 'X-Server-Recovery-Epoch': self.epoch,
                   'Content-Type': 'application/json'}
        return self.request('/orders/' + identity['order'] + '/exports?warehouseId=' + identity['warehouse'],
                            status, body, headers)

    def view(self, identity, job):
        return self.request(f'/exports/{job}?warehouseId=' + identity['warehouse'])['data']

    def download(self, identity, job):
        return self.request(f'/exports/{job}/file?warehouseId=' + identity['warehouse'], binary=True)


def enqueue(identity, reader):
    client, key = Client('new-queued'), uuid.uuid4()
    client.request('/orders/' + identity['order'] + '?warehouseId=' + identity['warehouse'], 401)
    client.login(reader)
    denied = client.create(identity | dict(warehouse=str(uuid.uuid4())), uuid.uuid4(), 404)
    assert denied['code'] == 'WAREHOUSE_NOT_FOUND'
    created = client.create(identity, key)['data']
    assert created['status'] == 'PENDING' and created['attempts'] == 0 and not created['downloadable']
    assert client.create(identity, key)['data'] == created
    assert client.create(identity, key, 409, 'SCAN_XLSX')['code'] == 'IDEMPOTENCY_CONFLICT'
    sql.save('queued-http-result.json', dict(view=created, key=str(key), requests=client.index))
    return uuid.UUID(created['id']), key


def read_completed(label, identity, reader, job, key=None):
    client = Client(label)
    client.login(reader)
    view = client.view(identity, job)
    assert view['status'] == 'SUCCEEDED' and view['attempts'] == 1 and view['downloadable']
    body = client.download(identity, job)
    assert hashlib.sha256(body).hexdigest() == view['sha256'] and len(body) == view['bytes']
    if key:
        assert client.create(identity, key)['data'] == view
        assert client.create(identity, key, 409, 'SCAN_XLSX')['code'] == 'IDEMPOTENCY_CONFLICT'
    target = LAB / (label + '.xlsx')
    target.write_bytes(body)
    sql.save(label + '-result.json', dict(view=view, requests=client.index, sha256=sql.digest(target)))
    return target, view
