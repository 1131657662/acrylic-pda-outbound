"""Preserved real HTTP requests for the original FORMAL order under both programs."""
import hashlib
import http.cookiejar
import json
import time
import urllib.error
import urllib.parse
import urllib.request

from d15_rollback_sql import LAB, save

BASE = 'http://127.0.0.1:19449/api/v1'


def wire():
    return urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()),
                                      urllib.request.ProxyHandler({}))


def request(client, path, options):
    req = urllib.request.Request(BASE + path, data=options.get('body'), headers=options.get('headers', {}))
    try:
        response = client.open(req, timeout=15)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        body = response.read()
        code, headers = response.code, dict(response.headers)
    directory = LAB / 'private' / options['label']
    directory.mkdir(exist_ok=True)
    name = options['name']
    (directory / (name + '.json')).write_bytes(body)
    save('private/' + options['label'] + '/' + name + '-http.json', dict(path=path,
         method=req.get_method(), status=code, responseHeaders=headers, bytes=len(body),
         sha256=hashlib.sha256(body).hexdigest(), expected=options['status']))
    assert code == options['status'], (path, code, body[:500])
    parsed = json.loads(body)
    if code == 200:
        assert parsed['code'] == 'OK' and parsed['serviceMode'] != 'UNAVAILABLE', parsed
    return parsed


def ready(process):
    for _ in range(150):
        assert process.poll() is None, 'API exited before readiness'
        # TCP readiness avoids creating unrecorded session/CSRF database rows.
        from d15_rollback_runtime import port_free
        if not port_free(19449):
            return
        time.sleep(.4)
    raise AssertionError('API readiness timeout')


def run(label, identity, reader):
    client = wire()
    options = dict(label=label, name='unauthenticated', status=401)
    order = '/orders/' + identity['order']
    suffix = '?warehouseId=' + identity['warehouse']
    request(client, order + suffix, options)
    options.update(name='csrf', status=200)
    csrf = request(client, '/auth/csrf', options)['data']
    options.update(name='login', body=urllib.parse.urlencode(dict(username=reader['username'],
        password=reader['password'])).encode(), headers={csrf['headerName']: csrf['token'],
        'Content-Type': 'application/x-www-form-urlencoded'})
    result = request(client, '/auth/web/login', options)
    assert result['data']['authenticated'] is True
    data = {}
    for name, route in dict(orders='/orders', detail=order, revisions=order + '/revisions',
                            publications=order + '/publications', exports=order + '/exports').items():
        data[name] = request(client, route + suffix, dict(label=label, name=name, status=200))['data']
    assert identity['order'] in json.dumps(data['orders'])
    assert identity['order'] in json.dumps(data['detail'])
    assert data['revisions'] and data['publications'], 'Require original nonempty business history'
    save(label + '-business.json', data)
    return data
