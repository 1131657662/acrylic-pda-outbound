"""Independent ZIP/XML consumer for the six-column SCAN_XLSX contract."""
from datetime import datetime, timedelta, timezone
from decimal import Decimal
from pathlib import Path
from zipfile import ZipFile
import xml.etree.ElementTree as ET

NS = {'s': 'http://schemas.openxmlformats.org/spreadsheetml/2006/main'}
LOCAL = timezone(timedelta(hours=8))


def cell_value(cell, strings):
    assert cell.find('s:f', NS) is None, 'Formula in scan export'
    value = cell.find('s:v', NS)
    if value is None:
        return ''.join(cell.itertext())
    return strings[int(value.text)] if cell.get('t') == 's' else value.text


def rows(path):
    with ZipFile(path) as archive:
        assert archive.testzip() is None
        assert not any('externalLink' in name for name in archive.namelist())
        shared = ET.fromstring(archive.read('xl/sharedStrings.xml'))
        strings = [''.join(item.itertext()) for item in shared.findall('s:si', NS)]
        workbook = ET.fromstring(archive.read('xl/workbook.xml'))
        names = [item.get('name') for item in workbook.findall('s:sheets/s:sheet', NS)]
        assert names == ['out']
        sheet = ET.fromstring(archive.read('xl/worksheets/sheet1.xml'))
        return [[cell_value(cell, strings) for cell in row.findall('s:c', NS)]
                for row in sheet.findall('s:sheetData/s:row', NS)]


def excel_serial(value):
    return Decimal(value)


def expected_serial(received_at):
    instant = datetime.fromisoformat(received_at.replace('Z', '+00:00')).astimezone(LOCAL)
    epoch = datetime(1899, 12, 30, tzinfo=LOCAL)
    return Decimal(str((instant - epoch).total_seconds())) / Decimal(86400)


def consume(path: Path, snapshot):
    order = snapshot['orders'][0]
    assert snapshot['metadata']['basis'] == 'ORIGINAL_CONFIRMED'
    actual = rows(path)
    lines = order['originalLines']
    assert len(actual) == len(lines)
    models = {product['code']: product['model'] for product in order['products']}
    for index, (row, line) in enumerate(zip(actual, lines), 1):
        assert len(row) == 6
        assert Decimal(row[0]) == index
        assert row[1] == models[line['productCode']]
        assert row[2] == line['barcode']
        weight = Decimal(row[3])
        assert weight * 1000 == Decimal(line['weightGrams'])
        assert row[4] == line['operatorName']
        assert abs(excel_serial(row[5]) - expected_serial(line['receivedAt'])) < Decimal('1e-8')
    return dict(sheet='out', rows=len(actual), columns=6, formulas=0,
                leadingZeroBarcode=lines[0]['barcode'])
