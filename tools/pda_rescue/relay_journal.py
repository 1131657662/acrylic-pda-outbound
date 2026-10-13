"""Fsync request keys before transport; preserve immutable original/result bytes."""
import hashlib
import json
import os
from pathlib import Path
from uuid import UUID, uuid4

from .integrity import parse


def durable(path, data):
    if path.exists():
        if path.read_bytes() != data:
            raise ValueError("Existing original differs; no overwrite permitted")
        return
    with path.open("xb") as stream:
        stream.write(data)
        stream.flush()
        os.fsync(stream.fileno())
    sync_directory(path.parent)


def sync_directory(path):
    if os.name == "nt":
        return
    descriptor = os.open(path, os.O_RDONLY)
    try:
        os.fsync(descriptor)
    finally:
        os.close(descriptor)


class RelayJournal:
    def __init__(self, directory, original, context):
        self.directory = Path(directory)
        self.directory.mkdir(parents=True, exist_ok=True)
        durable(self.directory / "original.zip", original)
        self.path = self.directory / "request.json"
        expected = {**context, "archiveSha256": hashlib.sha256(original).hexdigest()}
        if self.path.exists():
            self.state = parse(self.path.read_bytes())
            if self.state["input"] != expected:
                raise ValueError("Journal belongs to a different original, server or warehouse")
        else:
            self.state = {"input": expected, "receiveKey": str(uuid4()), "processKey": str(uuid4()), "relayId": None}
            self.save()

    def save(self):
        data = json.dumps(self.state, ensure_ascii=False, sort_keys=True).encode("utf-8")
        temporary = self.directory / ("request-" + str(uuid4()) + ".tmp")
        durable(temporary, data)
        temporary.replace(self.path)
        sync_directory(self.directory)

    def received(self, receipt):
        if receipt["archiveSha256"] != self.state["input"]["archiveSha256"]:
            raise ValueError("Received archive digest differs")
        if self.state["relayId"] not in (None, receipt["relayId"]):
            raise ValueError("Original receive key changed meaning")
        self.state["relayId"] = receipt["relayId"]
        self.save()
        durable(self.directory / "received.json", json.dumps(receipt, sort_keys=True).encode("utf-8"))

    def prepare_process(self, current_state):
        # A completed immutable result may later progress after independent review.
        # Rotate only after saving that result; an unresolved response always retains its original key.
        previous = self.state.get("lastResultState")
        if previous and previous != current_state and not self.state.get("pendingRefresh"):
            self.state["processKey"] = str(uuid4())
            self.state["pendingRefresh"] = True
            self.save()

    def result(self, original):
        receipt = parse(original)
        if receipt["archiveSha256"] != self.state["input"]["archiveSha256"] or receipt["relayId"] != self.state["relayId"]:
            raise ValueError("Returned file belongs to a different original")
        if receipt["localDeletionAllowed"] is not False:
            raise ValueError("Returned file must preserve deletion boundary")
        path = self.directory / (str(UUID(receipt["resultId"])) + ".pda-receipt.json")
        durable(path, original)
        self.state["lastResultState"] = receipt["businessReceipt"]["status"]
        self.state["pendingRefresh"] = False
        self.save()
        return path
