"""Read back XLSX XML independently, preserving text identifiers and decimal values."""
import argparse
import json
from decimal import Decimal
from pathlib import Path
from zipfile import ZipFile
from xml.etree import ElementTree as ET
from evidence import rows

NS = {"s": "http://schemas.openxmlformats.org/spreadsheetml/2006/main"}


def cells(archive, name):
    strings = []
    if "xl/sharedStrings.xml" in archive.namelist():
        strings = ["".join(si.itertext()) for si in ET.fromstring(archive.read("xl/sharedStrings.xml"))]
    root = ET.fromstring(archive.read(name))
    result = {}
    for cell in root.findall(".//s:sheetData/s:row/s:c", NS):
        value = cell.find("s:v", NS)
        text = value.text if value is not None else ""
        if cell.get("t") == "s":
            text = strings[int(text)]
        elif cell.get("t") == "inlineStr":
            text = "".join(cell.find("s:is", NS).itertext())
        result[cell.get("r")] = (text, cell.get("t", "n"))
    return result


def check_records(sheet, scans):
    for index, scan in enumerate(scans, 6):
        assert Decimal(sheet[f"A{index}"][0]) == scan["orderScanSeq"], "sequence mismatch"
        assert sheet[f"B{index}"][0] == scan["barcode"], "barcode changed"
        assert sheet[f"B{index}"][1] in {"s", "inlineStr", "str"}, "barcode is not text"
        assert sheet[f"C{index}"][0] == scan["deliveryCode"], "leading zero lost"
        assert sheet[f"D{index}"][0] == scan["rawWeight"], "raw decimal changed"
        assert Decimal(sheet[f"E{index}"][0]) * 1000 == int(scan["weightGrams"]), "kg precision lost"
        assert Decimal(sheet[f"F{index}"][0]) == int(scan["weightGrams"]), "grams changed"
        assert sheet[f"G{index}"][0] == scan["originDeviceId"], "origin lost"
        assert Decimal(sheet[f"J{index}"][0]) == scan["expectedDispatchCycle"], "cycle changed"
        assert sheet[f"K{index}"][0] == scan["inputSource"], "input source changed"
        assert sheet[f"L{index}"][0] == scan["status"], "record status changed"
        if "confirmationPolicy" in scan:
            assert sheet.get(f"M{index}", ("", "str"))[0] == scan["confirmationPolicy"], "confirmation policy changed"
    actual = [ref for ref, value in sheet.items() if ref.startswith("A") and ref[1:].isdigit()
              and int(ref[1:]) >= 6 and value[0] != ""]
    assert len(actual) == len(scans), "unexpected row count"


def check_audit(sheet, audit):
    for index, event in enumerate(audit, 6):
        expected = [event["originDeviceId"], str(event["localSeq"]), event["captureId"] or "", event["type"], event["reason"]]
        for column, wanted in zip("ABCDE", expected):
            actual = sheet.get(f"{column}{index}", ("", "str"))[0]
            assert actual == wanted, f"audit mismatch at {column}{index}"
    ids = [key for key, value in sheet.items() if key.startswith("A") and key[1:].isdigit()
           and int(key[1:]) >= 6 and value[0] != ""]
    assert len(ids) == len(audit), "audit row count mismatch"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    parser.add_argument("xlsx", type=Path)
    args = parser.parse_args()
    scans = sorted(rows(args.directory / "scans.ndjson"), key=lambda s: s["orderScanSeq"])
    with ZipFile(args.xlsx) as archive:
        check_records(cells(archive, "xl/worksheets/sheet1.xml"), [s for s in scans if s["status"] == "ACTIVE"])
        check_records(cells(archive, "xl/worksheets/sheet2.xml"), scans)
        check_audit(cells(archive, "xl/worksheets/sheet3.xml"), rows(args.directory / "audit.ndjson"))
        for name in archive.namelist():
            if name.startswith("xl/worksheets/") and name.endswith(".xml"):
                root = ET.fromstring(archive.read(name))
                assert not root.findall(".//s:c[@t='e']", NS), "spreadsheet error cells"
    print(json.dumps({"xlsxReadbackPassed": True, "originalRows": len(scans), "hardwarePassed": False}))


if __name__ == "__main__":
    main()
