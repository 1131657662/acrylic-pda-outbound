"""First mapping/round facts computed from original TEMP payload and database snapshot."""
import re
from ledger_primitives import canonical, raw_digest, require
from candidate_facts import matching_orders, fingerprint


def decimal(value):
    require(isinstance(value, str) and re.fullmatch(r"0|[1-9][0-9]*", value), "DECIMAL_TYPE")
    number = int(value)
    require(number <= 9223372036854775807, "DECIMAL_RANGE")
    return number


def products(body):
    result = []
    codes = set()
    for line in body["temporaryOrder"]["lines"]:
        code = line["deliveryCode"]
        require(code not in codes, "DUPLICATE_PLAN_PRODUCT")
        codes.add(code)
        scans = [s for s in body["scans"] if s["status"] == "ACTIVE" and s["deliveryCode"] == code]
        weight = sum(decimal(s["weightGrams"]) for s in scans)
        short = decimal(line["plannedCount"]) - len(scans)
        require(short >= 0 and weight <= 9223372036854775807, "PRODUCT_TOTAL")
        row = {key: line[key] for key in ("deliveryCode", "productId", "model", "name", "plannedCount")}
        row.update(actualCount=str(len(scans)), weightGrams=str(weight), shortCount=str(short))
        result.append(row)
    require(all(s["deliveryCode"] in codes for s in body["scans"] if s["status"] == "ACTIVE"), "UNKNOWN_PRODUCT")
    require(sum(decimal(p["actualCount"]) for p in result) > 0, "EMPTY_SUBMISSION")
    return result


def candidate_snapshot(body, facts):
    first, last, matching = matching_orders(body, facts)
    result = {"policyVersion": "REVIEW_DEFAULT_V1", "fromDate": str(first), "throughDate": str(last),
              "total": str(len(matching)), "fingerprint": fingerprint(matching)}
    context = {"products": products(body), "vehicleNo": body["temporaryOrder"]["vehicleNo"],
               "submissionId": body["submissionId"], "payloadSha256": raw_digest(canonical(body))}
    result["contextSha256"] = raw_digest(canonical({"warehouseId": facts["scope"]["warehouse_id"],
                                                  "context": context, "candidates": dict(result)}))
    return result
