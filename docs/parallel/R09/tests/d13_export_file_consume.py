"""Read fixed exports independently of Java DTOs and renderers."""
from decimal import Decimal
import json
from pathlib import Path
import unicodedata
import xml.etree.ElementTree as ET
from zipfile import ZipFile

from pypdf import PdfReader

NS = {'s': 'http://schemas.openxmlformats.org/spreadsheetml/2006/main'}


def workbook(path):
    with ZipFile(path) as archive:
        assert archive.testzip() is None
        assert not any('externalLink' in name for name in archive.namelist())
        strings = [''.join(row.itertext()) for row in
                   ET.fromstring(archive.read('xl/sharedStrings.xml')).findall('s:si', NS)]
        sheets = ET.fromstring(archive.read('xl/workbook.xml')).findall('s:sheets/s:sheet', NS)
        result = {}
        for index, sheet in enumerate(sheets, 1):
            root = ET.fromstring(archive.read(f'xl/worksheets/sheet{index}.xml'))
            result[sheet.get('name')] = [[cell_value(cell, strings) for cell in row]
                                        for row in root.findall('s:sheetData/s:row', NS)]
    return result


def cell_value(cell, strings):
    assert cell.find('s:f', NS) is None, 'Fixed export must contain no formulas'
    value = cell.find('s:v', NS)
    if value is None:
        return ''.join(cell.itertext())
    return strings[int(value.text)] if cell.get('t') == 's' else value.text


def rows(sheet):
    return [dict(zip(sheet[2], row, strict=True)) for row in sheet[3:]]


def grams(value):
    number = Decimal(value) * 1000
    assert number == number.to_integral_value()
    return int(number)


def xlsx(path, snapshot):
    book, order = workbook(path), snapshot['orders'][0]
    assert list(book) == ['订单汇总', '产品汇总', '原始扫码明细', '版本有效明细', '调整凭据', '异常记录', '快照说明']
    original, effective = rows(book['原始扫码明细']), rows(book['版本有效明细'])
    assert len(original) == len(effective) == len(order['originalLines'])
    fields = {'条码': 'barcode', '代码': 'productCode', '原设备ID': 'originDeviceId',
              '出库ID': 'dispatchId', '输入ID': 'captureId', '原始重量文本': 'rawWeight'}
    for row, line, net, expected in zip(original, order['originalLines'], effective, order['effectiveLines'], strict=True):
        assert all(row[key] == line[value] for key, value in fields.items())
        assert row['订单序号'] == str(line['orderScanSeq']) and row['设备序号'] == str(line['deviceCaptureSeq'])
        assert grams(row['精确kg']) == int(line['weightGrams'])
        assert net['出库ID'] == expected['dispatchId'] and net['条码'] == line['barcode']
        assert grams(net['净精确kg']) == int(expected['weightGrams'])
        assert Decimal(net['净包数']) == expected['count']
    for row in rows(book['订单汇总']):
        assert grams(row['原精确kg']) == sum(int(line['weightGrams']) for line in order['originalLines'])
        assert grams(row['净精确kg']) == sum(int(line['weightGrams']) for line in order['effectiveLines'])
    return dict(sheets=len(book), lines=len(original), formulas=0, originalOrder=True, exactGrams=True)


def pdf(path, snapshot):
    reader = PdfReader(path)
    text = ''.join(unicodedata.normalize('NFKC', '\n'.join(page.extract_text() for page in reader.pages)).split())
    order = snapshot['orders'][0]
    original = text.split('原始扫码明细(确认时原事实)', 1)[1].split('版本有效明细(含归零行)', 1)[0]
    previous = -1
    for line in order['originalLines']:
        position = original.index(line['barcode'])
        assert position > previous
        previous = position
        value = format(Decimal(line['weightGrams']) / 1000, 'f').rstrip('0').rstrip('.')
        assert line['barcode'] + line['productCode'] + value in original
    assert order['number'] in text and len(reader.pages) > 0
    assert all(not page.get('/Annots') for page in reader.pages)
    assert snapshot['metadata']['snapshotId'] in reader.metadata.subject
    return dict(pages=len(reader.pages), lines=len(order['originalLines']), originalOrder=True, annotations=0)
