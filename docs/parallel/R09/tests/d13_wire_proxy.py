"""Loopback-only transparent TCP proxy: retain one real COMMIT OK then close that stream."""
from dataclasses import dataclass
import hashlib
import json
import os
from pathlib import Path
import socket
import threading
import time

from d13_wire_packets import packet, thread_id, is_commit, commit_ok


def save(path, value):
    with path.open('x', encoding='utf-8') as output:
        json.dump(value, output, indent=2)


@dataclass(frozen=True)
class Config:
    target_port: int
    home: Path
    output: Path


class CommitAckProxy:
    def __init__(self, config):
        if not 1024 <= config.target_port <= 65535:
            raise ValueError('Only an explicitly allocated isolated target port is accepted')
        self.config = config
        self.stop = threading.Event()
        self.lock = threading.Lock()
        self.threads, self.sockets, self.errors = [], [], []
        self.selected, self.connections = 0, 0

    def __enter__(self):
        self.config.output.mkdir(exist_ok=False)
        self.listener = socket.socket()
        self.listener.setsockopt(socket.SOL_SOCKET, socket.SO_EXCLUSIVEADDRUSE, 1)
        self.listener.bind(('127.0.0.1', 0))
        self.listener.listen()
        self.listener.settimeout(.25)
        self.port = self.listener.getsockname()[1]
        self.start(self.accept)
        save(self.config.output / 'proxy-config.json', dict(host='127.0.0.1', port=self.port,
            targetPort=self.config.target_port, home=str(self.config.home), pid=os.getpid()))
        return self

    def start(self, function, *args):
        worker = threading.Thread(target=self.guard, args=(function, args), daemon=True)
        self.threads.append(worker)
        worker.start()

    def guard(self, function, args):
        try:
            function(*args)
        except (EOFError, ConnectionError, OSError) as error:
            if not self.stop.is_set() and not isinstance(error, (EOFError, ConnectionResetError, ConnectionAbortedError)):
                self.errors.append(type(error).__name__ + ': ' + str(error))
        except Exception as error:
            self.errors.append(type(error).__name__ + ': ' + str(error))

    def accept(self):
        while not self.stop.is_set():
            try:
                client, _ = self.listener.accept()
            except socket.timeout:
                continue
            server = socket.create_connection(('127.0.0.1', self.config.target_port), 10)
            server.settimeout(None)
            self.sockets.extend((client, server))
            self.connections += 1
            greeting = packet(server)
            connection = dict(id=thread_id(greeting), number=self.connections, pending=False)
            save(self.config.output / ('connection-' + str(self.connections) + '.json'), dict(
                connectionId=connection['id'], peer=list(server.getpeername()), local=list(server.getsockname())))
            client.sendall(greeting)
            self.start(self.upstream, (client, server), connection)
            self.start(self.downstream, (client, server), connection)

    def select(self, connection):
        path = self.config.home / 'jdbc-before-commit.json'
        if not path.is_file():
            return False
        observer = json.loads(path.read_text('utf-8'))
        if observer['database']['connectionId'] != connection['id']:
            return False
        with self.lock:
            if self.selected:
                raise ValueError('Refuse a second selected COMMIT')
            self.selected += 1
        return True

    def upstream(self, sockets, connection):
        client, server = sockets
        try:
            while not self.stop.is_set():
                value = packet(client)
                if is_commit(value) and self.select(connection):
                    connection['pending'] = True
                    path = self.config.home / 'wire-commit-command.bin'
                    with path.open('xb') as output:
                        output.write(value)
                server.sendall(value)
        except OSError:
            if not connection.get('closing'):
                raise

    def downstream(self, sockets, connection):
        client, server = sockets
        while not self.stop.is_set():
            value = packet(server)
            if connection['pending']:
                self.hold(sockets, connection, value)
                return
            client.sendall(value)

    def hold(self, sockets, connection, value):
        metadata = commit_ok(value)
        home = self.config.home
        command = (home / 'wire-commit-command.bin').read_bytes()
        with (home / 'wire-commit-ok.bin').open('xb') as output:
            output.write(value)
        fact = dict(**metadata, connectionId=connection['id'], proxyPid=os.getpid(),
            targetPort=self.config.target_port, proxyPort=self.port, selectedCount=self.selected,
            commandSha256=hashlib.sha256(command).hexdigest(), ackSha256=hashlib.sha256(value).hexdigest(),
            originalServerOkReceived=True, forwardedToClient=False, driverCommitReturned=False,
            syntheticAckLoss=False, heldAtMonotonicNs=time.monotonic_ns())
        save(home / 'wire-ack-held.json', fact)
        deadline = time.monotonic() + 150
        while not (home / 'lose-ack.marker').exists():
            if self.stop.wait(.02) or time.monotonic() >= deadline:
                raise TimeoutError('Retained real ACK was not released for owned stream closure')
        connection['closing'] = True
        for stream in sockets:
            stream.shutdown(socket.SHUT_RDWR)
            stream.close()
        save(home / 'wire-drop-receipt.json', dict(connectionId=connection['id'],
            ackSha256=fact['ackSha256'], forwardedToClient=False, socketsClosed=True,
            droppedAtMonotonicNs=time.monotonic_ns()))

    def __exit__(self, *_):
        self.stop.set()
        self.listener.close()
        for stream in self.sockets:
            try:
                stream.shutdown(socket.SHUT_RDWR)
            except OSError:
                pass
            stream.close()
        for worker in self.threads:
            worker.join(5)
        save(self.config.output / 'proxy-closed.json', dict(selectedCount=self.selected,
            connections=self.connections, errors=self.errors, threadsAlive=sum(t.is_alive() for t in self.threads)))
