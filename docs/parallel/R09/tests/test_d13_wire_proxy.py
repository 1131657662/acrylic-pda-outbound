"""Exercise framing, identity selection and actual TCP acknowledgement withholding without business data."""
import json
from pathlib import Path
import socket
import tempfile
import threading
import time
import unittest

from d13_wire_packets import packet, thread_id, is_commit, commit_ok
from d13_wire_proxy import CommitAckProxy, Config, save


def framed(body, sequence=0):
    return len(body).to_bytes(3, 'little') + bytes([sequence]) + body


GREETING = framed(b'\x0a8.0.fixture\0' + (481).to_bytes(4, 'little') + b'public-fixture')
COMMAND = framed(b'\x03commit')
OK = framed(b'\x00\x00\x00\x02\x00\x00\x00', 1)


def wait_for(path):
    deadline = time.monotonic() + 5
    while not path.is_file():
        if time.monotonic() > deadline:
            raise AssertionError('Fixture deadline: ' + str(path))
        time.sleep(.01)


class WireProtocolTests(unittest.TestCase):
    def test_fragmented_packet_reassembles_original_bytes(self):
        with socket.socket() as listener:
            listener.bind(('127.0.0.1', 0)); listener.listen()
            with socket.create_connection(listener.getsockname()) as client:
                server, _ = listener.accept()
                with server:
                    for byte in GREETING:
                        client.sendall(bytes([byte]))
                    self.assertEqual(packet(server), GREETING)

    def test_truncated_packet_refuses_success(self):
        with socket.socket() as listener:
            listener.bind(('127.0.0.1', 0)); listener.listen()
            with socket.create_connection(listener.getsockname()) as client:
                server, _ = listener.accept()
                with server:
                    client.sendall(GREETING[:-1]); client.shutdown(socket.SHUT_WR)
                    with self.assertRaises(EOFError):
                        packet(server)

    def test_only_exact_commit_commands_are_selected(self):
        for value in (COMMAND, framed(b'\x03\x00\x01COMMIT')):
            self.assertTrue(is_commit(value))
        for value in (framed(b'\x03select 1'), framed(b'\x03commit; delete from x'), framed(b'\x03commit', 1), framed(b'\x16commit')):
            self.assertFalse(is_commit(value))

    def test_real_server_identity_and_ok_are_required(self):
        self.assertEqual(thread_id(GREETING), 481)
        self.assertEqual(commit_ok(OK), dict(sequence=1, statusFlags=2, warnings=0))
        for value in (framed(b'\xfferror', 1), framed(b'\x00\x00\x00\x01\x00\x00\x00', 1), framed(OK[4:], 2)):
            with self.assertRaises(ValueError):
                commit_ok(value)
        with self.assertRaises(ValueError):
            thread_id(framed(b'bad-greeting'))


class WireTransportTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='wire-proxy-')
        self.addCleanup(self.temp.cleanup)
        self.home = Path(self.temp.name) / 'home'; self.home.mkdir()
        self.server = socket.socket(); self.server.bind(('127.0.0.1', 0)); self.server.listen()
        self.server.settimeout(5); self.addCleanup(self.server.close)
        self.received, self.failures = [], []

    def backend(self):
        try:
            stream, _ = self.server.accept()
            with stream:
                stream.settimeout(5); stream.sendall(GREETING)
                self.received.append(packet(stream)); stream.sendall(OK)
                stream.recv(1)
        except Exception as failure:
            self.failures.append(repr(failure))

    def exercise(self, selected):
        target = threading.Thread(target=self.backend); target.start()
        save(self.home / 'jdbc-before-commit.json', dict(database=dict(connectionId=481 if selected else 482)))
        output = self.home.parent / 'proxy'
        with CommitAckProxy(Config(self.server.getsockname()[1], self.home, output)) as proxy:
            with socket.create_connection(('127.0.0.1', proxy.port)) as client:
                client.settimeout(5); self.assertEqual(packet(client), GREETING)
                client.sendall(COMMAND)
                if selected:
                    self.assert_held_then_closed(client)
                else:
                    self.assertEqual(packet(client), OK)
                    self.assertFalse((self.home / 'wire-ack-held.json').exists())
        target.join(5)
        self.assertFalse(target.is_alive()); self.assertFalse(self.failures)
        self.assertEqual(self.received, [COMMAND])
        closed = json.loads((output / 'proxy-closed.json').read_text())
        self.assertEqual(closed['selectedCount'], int(selected))
        self.assertEqual(closed['threadsAlive'], 0); self.assertEqual(closed['errors'], [])

    def assert_held_then_closed(self, client):
        wait_for(self.home / 'wire-ack-held.json')
        self.assertEqual((self.home / 'wire-commit-ok.bin').read_bytes(), OK)
        client.settimeout(.15)
        with self.assertRaises(socket.timeout):
            client.recv(1)
        (self.home / 'lose-ack.marker').write_text('close fixture stream')
        client.settimeout(5); self.assertEqual(client.recv(1), b'')
        wait_for(self.home / 'wire-drop-receipt.json')

    def test_selected_identity_gets_eof_without_original_ok(self):
        self.exercise(True)

    def test_other_connection_retains_normal_original_ok(self):
        self.exercise(False)


if __name__ == '__main__':
    unittest.main()
