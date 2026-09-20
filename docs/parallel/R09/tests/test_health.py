"""A real local HTTP socket with synthetic responses tests health semantics."""
from contextlib import contextmanager
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import threading
import time
import unittest
from backup_factory import test_directory
from monitor import http
from safety import OpsError


@contextmanager
def response_server(options):
    class Response(BaseHTTPRequestHandler):
        def do_GET(self):
            time.sleep(options.get('delay', 0))
            self.send_response(options.get('status', 200))
            self.send_header('Location', 'http://127.0.0.1:1/forbidden')
            self.end_headers()
            self.wfile.write(json.dumps(options.get('body', {})).encode())

        def log_message(self, *args):
            pass
    server = ThreadingHTTPServer(('127.0.0.1', 0), Response)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    try:
        yield {'url': f'http://127.0.0.1:{server.server_port}/api/v1/auth/csrf',
               'timeout_seconds': 2, 'max_latency_ms': 1000}
    finally:
        server.shutdown()
        server.server_close()
        thread.join(timeout=3)


class HttpTests(unittest.TestCase):
    def test_http_200_with_database_unavailable_is_failure(self):
        with response_server({'body': {'code': 'OK', 'serviceMode': 'UNAVAILABLE'}}) as config:
            with self.assertRaisesRegex(OpsError, 'SERVICE_NOT_NORMAL'):
                http(config)

    def test_http_healthy_and_latency_threshold(self):
        body = {'code': 'OK', 'serviceMode': 'NORMAL', 'serverRecoveryEpoch': '1'}
        with response_server({'body': body, 'delay': .04}) as config:
            self.assertEqual(http(config)['service_mode'], 'NORMAL')
            config['max_latency_ms'] = 1
            with self.assertRaisesRegex(OpsError, 'HTTP_LATENCY_HIGH'):
                http(config)

    def test_http_redirect_and_server_error_are_failures(self):
        for status in (302, 500):
            with self.subTest(status=status), response_server({'status': status}) as config:
                with self.assertRaises(Exception):
                    http(config)


if __name__ == '__main__':
    unittest.main()
