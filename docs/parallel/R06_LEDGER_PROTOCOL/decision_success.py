"""Verify nested success against original body, independent target and separate HTTP receipt."""
from datetime import datetime
from ledger_primitives import canonical, parse, raw_digest, require
from decision_products import decimal, products
from decision_signature import verify_signed


def success_receipt(vector, body, keys):
    envelope = parse(vector["successHttpUtf8"])
    require(envelope["code"] == "OK", "SUCCESS_HTTP")
    receipt = envelope["data"]
    unsigned = verify_signed(receipt, keys, "acrylic-pda-receipt")
    actual = [s for s in body["scans"] if s["status"] == "ACTIVE"]
    rows = products(body)
    expected = {"submissionId": body["submissionId"], "orderId": vector["facts"]["order"]["id"],
                "status": "SUCCEEDED", "payloadSha256": raw_digest(canonical(body)), "source": body["source"],
                "freezeDeviceId": body["freezeDeviceId"], "workRound": "1", "issuedRecoveryEpoch": body["sourceRecoveryEpoch"],
                "mayResumeScanning": False, "stockReleased": False, "orderStatus": "COMPLETED", "inventoryDeducted": body["autoDeductInventory"],
                "actualCount": str(len(actual)), "actualWeightGrams": str(sum(decimal(s["weightGrams"]) for s in actual)),
                "resultVersion": "1", "receiptVersion": 1, "captureHighWatermark": body["captureHighWatermark"],
                "firstOrderScanSeq": str(min(decimal(s["orderScanSeq"]) for s in actual)),
                "lastOrderScanSeq": str(max(decimal(s["orderScanSeq"]) for s in actual)),
                "orderNo": vector["facts"]["order"]["order_no"], "completedAt": unsigned["completedAt"],
                "items": [{"deliveryCode": p["deliveryCode"], "actualCount": p["actualCount"],
                           "actualWeightGrams": p["weightGrams"]} for p in rows]}
    require(canonical(unsigned) == canonical(expected), "SUCCESS_FACTS")
    require(unsigned["actualCount"] == body["expectedCount"] and unsigned["actualWeightGrams"] == body["expectedWeightGrams"], "SUCCESS_TOTALS")
    # Timestamp is independently bounded by the captured execution window, not invented by the oracle.
    time = datetime.fromisoformat(unsigned["completedAt"])
    require(time.tzinfo is not None and datetime.fromisoformat(vector["startedAt"]) <= time <=
            datetime.fromisoformat(vector["finishedAt"]), "SUCCESS_TIME")
    return receipt
