"""Read explicitly frozen public vectors and attach independent trusted facts."""
import json
from ledger_primitives import ROOT
from trusted_facts import fixtures


def load(name="signed_vectors.json"):
    return json.loads((ROOT / name).read_text(encoding="utf-8"))


def trusted(row, facts, public_key):
    return {
        "originalResponseSha256": row["originalResponseSha256"],
        "unsignedReceiptSha256": row["unsignedReceiptSha256"],
        "facts": facts, "keys": {public_key["keyId"]: public_key},
    }


def positive(index=0):
    vectors = load()
    facts = fixtures()[index]
    row = vectors["codec"][index]
    if row["id"] != facts["id"]:
        raise ValueError("FIXTURE_ORDER_CHANGED")
    return row, trusted(row, facts, vectors["publicKey"])
