"""New production primitive checks; author/SQL/HTTP validation is separate."""
import unittest
from canonical import canonical, digest, parse, raw_digest
from fixtures import load
from java_driver import ProductionDriver
from scenarios import binding_commands, evidence_command, primitive_cases, valid_commands

DRIVER = None
OUTPUT = {}


def setUpModule():
    global DRIVER, OUTPUT
    DRIVER = ProductionDriver()
    try:
        DRIVER.compile()
        OUTPUT = {row["id"]: row for row in DRIVER.invoke(primitive_cases())}
    except Exception:
        DRIVER.close()
        raise


def tearDownModule():
    if DRIVER:
        DRIVER.close()


class ProductionPrimitives(unittest.TestCase):
    def test_normal_confirm_empty_reason_is_accepted(self):
        value = parse(OUTPUT["normal_confirm"]["value"])
        self.assertEqual("", value["reason"])

    def test_null_and_omitted_members_remain_distinct(self):
        self.assertNotEqual(OUTPUT["null-member"]["value"], OUTPUT["missing-member"]["value"])

    def test_original_response_hash_uses_exact_non_json_bytes(self):
        self.assertEqual(raw_digest(b"  not JSON\n"), OUTPUT["raw-is-exact"]["value"])

    def test_canonical_boundary_accepts_exact_limit(self):
        self.assertEqual(262144, len(OUTPUT["input-limit"]["value"].encode()))

    def test_production_constant_set_is_new_version(self):
        expected = {"routeScope": "r06.command.v1", "receiptAudience": "acrylic-r06-command-receipt-v1",
                    "issuer": "acrylic-outbound", "versions": "1:1"}
        self.assertEqual(expected, {key: OUTPUT[key]["value"] for key in expected})


def canonical_case(row):
    def test(self):
        actual = OUTPUT[row["id"]]
        if row.get("reject"):
            self.assertTrue(actual.get("reject"), actual)
            with self.assertRaises((ValueError, UnicodeError)):
                parse(row["raw"])
            return
        expected = row["canonical"].encode("utf-8")
        self.assertEqual(expected, canonical(parse(row["raw"])))
        self.assertEqual(expected.decode("utf-8"), actual["value"])
    return test


def accepted_command(row):
    def test(self):
        self.assertEqual(row["raw"], OUTPUT[row["id"]]["value"])
    return test


def rejected_input(row):
    def test(self):
        self.assertTrue(OUTPUT[row["id"]].get("reject"), OUTPUT[row["id"]])
    return test


def changed_binding(row):
    def test(self):
        body = parse(row["raw"])
        expected = digest(body, "command")
        unchanged = evidence_command(body["commandType"])
        self.assertNotEqual(digest(unchanged, "command"), expected)
        self.assertEqual(expected, OUTPUT[row["id"]]["value"])
    return test


for row in load("canonical_vectors.json")["vectors"]:
    setattr(ProductionPrimitives, "test_canonical_" + row["id"], canonical_case(row))
for row in valid_commands():
    setattr(ProductionPrimitives, "test_command_" + row["id"], accepted_command(row))
for row in primitive_cases():
    if row.get("reject") and row["id"] not in {x["id"] for x in load("canonical_vectors.json")["vectors"]}:
        setattr(ProductionPrimitives, "test_reject_" + row["id"], rejected_input(row))
for row in binding_commands():
    setattr(ProductionPrimitives, "test_binding_" + row["id"], changed_binding(row))
