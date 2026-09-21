"""Fixed Node hashes cross-check independent Python and actual Java production domains."""
import unittest
from canonical import digest, parse
from fixtures import load
from java_driver import ProductionDriver

OUTPUT = {}
DRIVER = None
MODES = {"command": "commandHash", "receipt": "receiptHash", "control": "controlHash"}


def setUpModule():
    global DRIVER, OUTPUT
    DRIVER = ProductionDriver()
    try:
        DRIVER.compile()
        samples = [{"id": row["id"], "mode": MODES[row["domain"]], "raw": row["canonical"]}
                   for row in load("hash_vectors.json")["vectors"]]
        OUTPUT = {row["id"]: row for row in DRIVER.invoke(samples)}
    except Exception:
        DRIVER.close()
        raise


def tearDownModule():
    if DRIVER:
        DRIVER.close()


class FixedHashes(unittest.TestCase):
    pass


def fixed_hash(row):
    def test(self):
        expected = row["sha256"]
        self.assertEqual(expected, digest(parse(row["canonical"]), row["domain"]))
        self.assertEqual(expected, OUTPUT[row["id"]]["value"])
    return test


for row in load("hash_vectors.json")["vectors"]:
    setattr(FixedHashes, "test_" + row["id"], fixed_hash(row))
