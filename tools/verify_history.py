"""Independently validate the full synthetic 90-day fixture in a stopped database backup."""
import argparse
import json
from contextlib import ExitStack
from pathlib import Path
from verify_retention import open_backup


def check_order(db, order):
    order_id = order["id"]
    assert order["state"] == "CONFIRMED_TEST_FIXTURE"
    assert (order["actualCount"], order["weightGrams"], order["nextOrderScanSeq"]) == (400, 120200000, 401)
    rows = db.execute("""SELECT s.orderScanSeq, s.originDeviceId, s.deviceCaptureSeq, s.barcode,
        s.weightGrams, s.rawWeight, s.status, c.state, c.originDeviceId AS c_origin,
        c.deviceCaptureSeq AS c_seq, c.orderScanSeq AS c_order_seq, c.barcode AS c_barcode,
        c.expectedDispatchCycle AS c_cycle, s.expectedDispatchCycle AS s_cycle,
        g.scanId AS guard_scan, s.id, g.dispatchCycle, g.orderId AS guard_order
        FROM local_scan s JOIN local_input_capture c ON c.id=s.id
        LEFT JOIN local_barcode_guard g ON g.barcode=s.barcode
        WHERE s.orderId=? ORDER BY s.orderScanSeq""", (order_id,))
    count = 0
    for row in rows:
        count += 1
        assert row["orderScanSeq"] == count == row["c_order_seq"]
        assert row["originDeviceId"] == row["c_origin"] and row["deviceCaptureSeq"] == row["c_seq"]
        assert row["barcode"] == row["c_barcode"] and row["s_cycle"] == row["c_cycle"] == row["dispatchCycle"] == 1
        assert row["guard_scan"] == row["id"] and row["guard_order"] == order_id
        assert (row["weightGrams"], row["rawWeight"], row["status"], row["state"]) == (300500, "300.5", "ACTIVE", "COMMITTED")
    assert count == 400
    audit = dict(db.execute("SELECT type,COUNT(*) FROM local_operation_event WHERE orderId=? GROUP BY type", (order_id,)))
    assert audit == {"RECEIVED": 400, "CONFIRM_REQUESTED": 400, "SAVED": 400}
    lines = db.execute("SELECT deliveryCode,plannedCount,actualCount,weightGrams FROM local_order_line WHERE orderId=? ORDER BY deliveryCode", (order_id,)).fetchall()
    assert [tuple(r) for r in lines] == [("003", 200, 200, 60100000), ("571", 200, 200, 60100000)]


def check_sources(db):
    captures = db.execute("""SELECT originDeviceId,COUNT(*),MIN(deviceCaptureSeq),MAX(deviceCaptureSeq)
        FROM local_input_capture WHERE inputSource='HISTORY_FIXTURE' GROUP BY originDeviceId""").fetchall()
    assert len(captures) == 1
    origin, count, first, last = captures[0]
    assert (count, first, last) == (360000, 1, 360000)
    audit = db.execute("SELECT COUNT(*),MIN(localSeq),MAX(localSeq) FROM local_operation_event WHERE originDeviceId=?", (origin,)).fetchone()
    assert tuple(audit) == (1080000, 1, 1080000)
    return origin


def verify(archive):
    with ExitStack() as stack:
        db, directory = open_backup(stack, archive)
        assert [r[0] for r in db.execute("PRAGMA integrity_check")] == ["ok"]
        assert db.execute("PRAGMA foreign_key_check").fetchall() == []
        orders = db.execute("SELECT * FROM local_order WHERE source='HISTORY_FIXTURE' ORDER BY createdAt,id").fetchall()
        assert len(orders) == 900
        for order in orders:
            check_order(db, order)
        origin = check_sources(db)
        return {"syntheticHistoryPassed": True, "orders": 900, "captures": 360000,
                "scans": 360000, "guards": 360000, "auditEvents": 1080000, "originDeviceId": origin,
                "spanDays": (orders[-1]["createdAt"] - orders[0]["createdAt"]) / 86400000,
                "databaseFiles": {p.name: p.stat().st_size for p in (directory / "databases").iterdir()},
                "sampleOrderIds": [orders[i]["id"] for i in (0, 450, 899)],
                "integrity": "ok", "foreignKeys": [], "measuredRealScans": False}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("archive", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    result = verify(args.archive)
    args.output.write_text(json.dumps(result, indent=2))
    print(json.dumps(result))
