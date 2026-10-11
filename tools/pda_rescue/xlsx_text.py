"""Repair unsupported literal-text export and independently compare every workbook value."""
import json
import posixpath
from pathlib import Path
import zipfile
import xml.etree.ElementTree as ET

NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
ET.register_namespace("", NS)


def _column(index):
    return chr(65 + index)


def _repair_sheet(data, sheet):
    root = ET.fromstring(data)
    cells = {cell.attrib["r"]: cell for cell in root.findall(f".//{{{NS}}}c")}
    for row_index, values in enumerate(sheet["rows"], start=5):
        for column, value in enumerate(values):
            if not isinstance(value, str):
                continue
            cell = cells[f"{_column(column)}{row_index}"]
            for child in list(cell):
                cell.remove(child)
            cell.set("t", "inlineStr")
            text = ET.SubElement(ET.SubElement(cell, f"{{{NS}}}is"), f"{{{NS}}}t")
            text.set("{http://www.w3.org/XML/1998/namespace}space", "preserve")
            text.text = value
    return ET.tostring(root, encoding="utf-8", xml_declaration=True)


def repair_and_verify(path: Path):
    expected = json.loads(Path(str(path) + ".expected.json").read_text(encoding="utf-8"))
    temporary = Path(str(path) + ".literal-text.tmp")
    with zipfile.ZipFile(path) as original, zipfile.ZipFile(temporary, "w", zipfile.ZIP_DEFLATED) as target:
        targets = _sheet_targets(original)
        patches = {targets[sheet["name"]]: sheet for sheet in expected}
        for info in original.infolist():
            data = original.read(info)
            target.writestr(info, _repair_sheet(data, patches[info.filename]) if info.filename in patches else data)
    temporary.replace(path)
    _verify_values(path, expected)


def _sheet_targets(archive):
    relationships = ET.fromstring(archive.read("xl/_rels/workbook.xml.rels"))
    targets = {row.attrib["Id"]: posixpath.normpath("xl/" + row.attrib["Target"])
               if not row.attrib["Target"].startswith("/") else row.attrib["Target"].lstrip("/")
               for row in relationships}
    workbook = ET.fromstring(archive.read("xl/workbook.xml"))
    relation = "{http://schemas.openxmlformats.org/officeDocument/2006/relationships}id"
    return {row.attrib["name"]: targets[row.attrib[relation]]
            for row in workbook.findall(f"{{{NS}}}sheets/{{{NS}}}sheet")}


def _verify_values(path, expected):
    from openpyxl import load_workbook
    book = load_workbook(path, data_only=False, read_only=True)
    try:
        for sheet in expected:
            actual = book[sheet["name"]]
            if not sheet["rows"]:
                continue
            rows = actual.iter_rows(min_row=5, max_row=4 + len(sheet["rows"]), max_col=len(sheet["rows"][0]))
            for cells, row in zip(rows, sheet["rows"], strict=True):
                for cell, value in zip(cells, row, strict=True):
                    if cell.value != value or cell.data_type == "f" or (isinstance(value, str) and cell.data_type != "s"):
                        raise ValueError("Excel changed an original identifier, timestamp, text or integer")
        for sheet in book:
            sheet.calculate_dimension(force=True)
        if any(cell.data_type == "f" for sheet in book for row in sheet for cell in row):
            raise ValueError("Unexpected formula in rescue workbook")
    finally:
        book.close()
