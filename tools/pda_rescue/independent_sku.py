"""Read the fixed GB18030 source independently; no inventory or operation authority is created."""
from decimal import Decimal
import re


def field(raw):
    # Java Character.isWhitespace excludes NBSP, FIGURE SPACE and NARROW NBSP.
    return raw.decode("gb18030").strip("\t\n\v\f\r\x1c\x1d\x1e\x1f \u1680\u2000\u2001\u2002\u2003\u2004\u2005\u2006\u2008\u2009\u200a\u2028\u2029\u205f\u3000")


def parse_row(raw, number):
    if len(raw) != 57:
        raise ValueError("Independent SKU byte column length mismatch")
    model, name, barcode, weight = [field(raw[a:b]) for a, b in [(0, 20), (20, 34), (34, 47), (47, 57)]]
    if not model or not name or not re.fullmatch("[A-Z0-9]{11}", barcode):
        raise ValueError("Invalid independent SKU identity fields")
    if not re.fullmatch(r"[0-9]+(?:\.[0-9]{1,3})?", weight):
        raise ValueError("Invalid independent original weight")
    grams = int(Decimal(weight) * 1000)
    if not 0 < grams <= 2**63 - 1:
        raise ValueError("Independent weight out of range")
    return {"model": model, "name": name, "barcode": barcode, "deliveryCode": barcode[:3],
            "rawWeight": weight, "weightGrams": str(grams), "skuLineNo": str(number),
            "skuRawText": raw.decode("gb18030")}


def catalog(original):
    if not 0 < len(original) <= 2 * 1024 * 1024:
        raise ValueError("Independent SKU exceeds original file limit")
    parts = original.removeprefix(b"\xef\xbb\xbf").split(b"\n")
    if parts[-1] == b"":
        parts.pop()
    if len(parts) > 40000:
        raise ValueError("Independent SKU exceeds row limit")
    result, products, duplicate = {}, {}, False
    for number, raw in enumerate(parts, 1):
        raw = raw.removesuffix(b"\r")
        if not raw:
            continue
        row = parse_row(raw, number)
        previous = result.setdefault(row["barcode"], row)
        if any(previous[k] != row[k] for k in ("model", "name", "barcode", "rawWeight", "weightGrams")):
            raise ValueError("Conflicting independent SKU barcode")
        duplicate |= previous is not row
        product = (row["model"], row["name"])
        if products.setdefault(row["deliveryCode"], product) != product:
            raise ValueError("Conflicting independent product mapping")
    if not result:
        raise ValueError("Independent SKU is empty")
    return result, products, duplicate
