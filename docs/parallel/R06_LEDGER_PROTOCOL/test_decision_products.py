"""Independent arithmetic and candidate-window boundary tests without a server fixture."""
from copy import deepcopy
import unittest
from decision_products import candidate_snapshot, products


def body():
    return {"submissionId": "s", "temporaryOrder": {"vehicleNo": "车A", "lines": [
        {"deliveryCode": "A", "productId": "p1", "model": "M", "name": "一", "plannedCount": "2"},
        {"deliveryCode": "B", "productId": "p2", "model": "N", "name": "二", "plannedCount": "1"}]},
        "scans": [{"deliveryCode": "A", "status": "ACTIVE", "weightGrams": "123"},
                  {"deliveryCode": "A", "status": "VOID", "weightGrams": "999"}]}


def facts():
    return {"payload": {"received_at": "2026-09-25T16:00:00Z"}, "scope": {"warehouse_id": "w"}, "formalOrders": []}


def order(when, vehicle="车A", ids=None):
    return {"id": "60000000-0000-4000-8000-000000000001", "created_at": when, "updated_at": when,
            "warehouse_id": "w", "source": "WEB",
            "vehicle_no": vehicle, "products": ids or [], "order_no": "FH1", "status": "READY",
            "current_revision": "1", "row_version": "2", "revision_id": "revision", "container_no": "",
            "inventory_snapshot_id": "snapshot", "mapping_revision": "1", "hasAssignment": False,
            "hasPcWork": False, "hasSubmission": False, "hasResult": False}


class DecisionProductTests(unittest.TestCase):
    def test_void_ignored_and_zero_product_retained(self):
        rows = products(body())
        self.assertEqual([(r["actualCount"], r["weightGrams"], r["shortCount"]) for r in rows],
                         [("1", "123", "1"), ("0", "0", "1")])

    def test_duplicates_unknown_overcount_empty_and_number_types_fail(self):
        cases = []
        row = body(); row["temporaryOrder"]["lines"].append(row["temporaryOrder"]["lines"][0]); cases.append(row)
        row = body(); row["scans"][0]["deliveryCode"] = "UNKNOWN"; cases.append(row)
        row = body(); row["temporaryOrder"]["lines"][0]["plannedCount"] = "0"; cases.append(row)
        row = body(); row["scans"][0]["status"] = "VOID"; cases.append(row)
        for weight in (123, True, "01", "-1", "9223372036854775808"):
            row = body(); row["scans"][0]["weightGrams"] = weight; cases.append(row)
        for index, row in enumerate(cases):
            with self.subTest(index=index), self.assertRaises(ValueError):
                products(row)

    def test_default_window_uses_received_beijing_day(self):
        value = candidate_snapshot(body(), facts())
        self.assertEqual((value["fromDate"], value["throughDate"]), ("2026-09-19", "2026-10-03"))

    def test_full_window_edges_are_inclusive_and_signal_is_or(self):
        for when in ("2026-09-18T16:00:00Z", "2026-10-03T15:59:59.999Z"):
            for vehicle, ids in (("车A", []), ("other", ["p2"])):
                data = facts(); data["formalOrders"] = [order(when, vehicle, ids)]
                with self.subTest(when=when, vehicle=vehicle):
                    self.assertEqual(candidate_snapshot(body(), data)["total"], "1")

    def test_outside_window_and_binary_vehicle_mismatch_do_not_match(self):
        for when in ("2026-09-18T15:59:59.999Z", "2026-10-03T16:00:00Z"):
            data = facts(); data["formalOrders"] = [order(when, "车A", ["p1"])]
            self.assertEqual(candidate_snapshot(body(), data)["total"], "0")
        data = facts(); data["formalOrders"] = [order("2026-09-25T00:00:00Z", "车a")]
        self.assertEqual(candidate_snapshot(body(), data)["total"], "0")

    def test_foreign_warehouse_and_temporary_orders_are_excluded(self):
        for field, value in (("warehouse_id", "foreign"), ("source", "TEMP")):
            data = facts(); row = order("2026-09-25T00:00:00Z"); row[field] = value
            data["formalOrders"] = [row]
            with self.subTest(field=field):
                self.assertEqual(candidate_snapshot(body(), data)["total"], "0")

    def test_product_change_changes_candidate_context_digest(self):
        modified = deepcopy(body()); modified["scans"][0]["weightGrams"] = "124"
        self.assertNotEqual(candidate_snapshot(body(), facts())["contextSha256"], candidate_snapshot(modified, facts())["contextSha256"])

    def test_revision_work_flags_and_ordering_change_full_fingerprint(self):
        data = facts(); first = order("2026-09-25T00:00:00Z")
        second = deepcopy(first); second["id"] = "60000000-0000-4000-8000-000000000002"
        data["formalOrders"] = [first, second]
        expected = candidate_snapshot(body(), data)
        data["formalOrders"].reverse(); self.assertEqual(expected, candidate_snapshot(body(), data))
        second["hasPcWork"] = True; self.assertNotEqual(expected, candidate_snapshot(body(), data))
        second["hasPcWork"] = False; second["row_version"] = "3"
        self.assertNotEqual(expected, candidate_snapshot(body(), data))

    def test_duplicate_candidate_identity_rejected(self):
        data = facts(); data["formalOrders"] = [order("2026-09-25T00:00:00Z")] * 2
        with self.assertRaisesRegex(ValueError, "DUPLICATE_CANDIDATE"):
            candidate_snapshot(body(), data)
