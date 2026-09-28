"""Read HTTP export artifacts and compare approver UUIDs with captured binary DB facts.
Usage: python tools/verify-corrected-export-approvers.py <storage/corrected-http>
Requires openpyxl and pypdf; does not rewrite the XLSX/PDF inputs.
"""
import argparse
from collections import Counter
import json
from pathlib import Path
import re
import unicodedata
from uuid import UUID
import warnings

import openpyxl
from pypdf import PdfReader


def expected_approvals(folder, extension):
    facts = json.loads((folder / f"approval-facts-{extension}.json").read_text(encoding="utf-8"))
    expected = {}
    for fact in facts:
        key = (fact["correctionId"], fact["lineId"])
        assert key not in expected, f"Duplicate database approval fact: {key}"
        raw = bytes.fromhex(fact["approvedByHex"])
        assert len(raw) == 16, "Database approver is not a 16-byte UUID"
        expected[key] = str(UUID(bytes=raw))
    assert expected, "No database approval facts"
    return expected


def verify_snapshot(folder, extension, expected):
    snapshot = json.loads((folder / f"snapshot-{extension}.json").read_text(encoding="utf-8"))
    assert snapshot["metadata"]["basis"] == "CORRECTED"
    order = snapshot["orders"][0]
    assert order["resultVersion"] == 2
    actual = {(item["correctionId"], item["lineId"]): item["approver"] for item in order["adjustments"]}
    assert len(actual) == len(order["adjustments"]), "Duplicate adjustment key"
    assert actual == expected, f"{extension} snapshot approval identities differ from DB facts"


def verify_xlsx(folder, expected):
    with warnings.catch_warnings():
        warnings.filterwarnings("ignore", message="Workbook contains no default style")
        workbook = openpyxl.load_workbook(folder / "corrected.xlsx", read_only=True, data_only=True)
    try:
        rows = list(workbook["调整凭据"].values)
        actual = {}
        for values in rows[3:]:
            row = dict(zip(rows[2], values))
            key = (row["更正号"], row["行ID"])
            assert key not in actual, f"Duplicate XLSX adjustment: {key}"
            actual[key] = row["审批人"]
        assert actual == expected, "XLSX approval identities differ from DB facts"
        assert not any("[B@" in str(value) for row in rows for value in row), "Java array identity in XLSX"
        summary = list(workbook["订单汇总"].values)[3]
        assert summary[9:12] == (2, -2, 0)
        assert (summary[12], summary[14], summary[16]) == (601.5, -601.5, 0)
        return {"approval_rows": len(actual), "approvers": list(actual.values()), "sheets": workbook.sheetnames}
    finally:
        workbook.close()


def verify_pdf(folder, expected):
    pdf = PdfReader(folder / "corrected.pdf")
    text = unicodedata.normalize("NFKC", chr(10).join(page.extract_text() or "" for page in pdf.pages))
    compact = re.sub(r"\s+", "", text)
    pattern = r"审批人:([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})"
    actual = re.findall(pattern, compact)
    assert Counter(actual) == Counter(expected.values()), "PDF approval identities/count differ from DB facts"
    assert "[B@" not in compact, "Java array identity in PDF"
    for correction, line in expected:
        assert correction in compact and line in compact, "Missing PDF adjustment reference"
    assert all(value in compact for value in ["CORRECTED", "结果版本2", "601.5", "0包/0kg"])
    (folder / "pdf-extracted.txt").write_text(text, encoding="utf-8")
    return {"pages": len(pdf.pages), "text_chars": len(text), "approvers": actual}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("folder", type=Path)
    folder = parser.parse_args().folder
    result = {"content_acceptance": "FAIL"}
    try:
        xlsx = expected_approvals(folder, "xlsx")
        pdf = expected_approvals(folder, "pdf")
        assert xlsx == pdf, "Separate exports disagree on database approval facts"
        verify_snapshot(folder, "xlsx", xlsx)
        verify_snapshot(folder, "pdf", pdf)
        result["xlsx"] = verify_xlsx(folder, xlsx)
        result["pdf"] = verify_pdf(folder, pdf)
        result["content_acceptance"] = "PASS"
    except Exception as error:
        result["error"] = f"{type(error).__name__}: {error}"
        raise
    finally:
        (folder / "approval-content-readback.json").write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
        print(json.dumps(result, ensure_ascii=False))


if __name__ == "__main__":
    main()
