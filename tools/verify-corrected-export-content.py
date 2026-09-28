"""Read exported XLSX/PDF values against the DB-checked boundary snapshot.
Usage: python tools/verify-corrected-export-content.py <evidence-folder> <file-prefix>
Requires openpyxl and pypdf. Input artifacts are never rewritten.
"""
import argparse
from collections import Counter
from datetime import datetime, timedelta
from decimal import Decimal
import json
from pathlib import Path
import re
import unicodedata
import warnings

import openpyxl
from pypdf import PdfReader


def kg(grams):
    return format(Decimal(str(grams)) / 1000, 'f').rstrip('0').rstrip('.') if int(grams) % 1000 else str(int(grams) // 1000)


def compact(value):
    return re.sub(r'\s+', '', unicodedata.normalize('NFKC', str(value)))


def rows(book, name):
    values = list(book[name].values)
    return [dict(zip(values[2], row)) for row in values[3:]]


def match(row, expected):
    for key, value in expected.items():
        assert row[key] == value, f'{key}: {row[key]!r} != {value!r}'


def totals(order, code=None):
    original = [r for r in order['originalLines'] if code is None or r['productCode'] == code]
    ids = {r['dispatchId'] for r in original}
    net = [r for r in order['effectiveLines'] if r['dispatchId'] in ids]
    counts = (len(original), sum(r['count'] for r in net))
    weights = (sum(int(r['weightGrams']) for r in original), sum(int(r['weightGrams']) for r in net))
    return (counts[0], counts[1] - counts[0], counts[1]), (weights[0], weights[1] - weights[0], weights[1])


def check_totals(row, order, code=None):
    counts, weights = totals(order, code)
    match(row, dict(zip(['原包数', '调整包数', '净包数'], counts)))
    for number, exact, value in zip(['原重量kg', '调整kg', '净重kg'], ['原精确kg', '调整精确kg', '净精确kg'], weights):
        match(row, {number: float(kg(value)), exact: kg(value)})


def check_details(book, order):
    original = {r['dispatchId']: r for r in order['originalLines']}
    expected = [r for r in order['effectiveLines'] if r['count'] == 1]
    actual = rows(book, '版本有效明细')
    assert len(actual) == len(expected)
    for row, effect in zip(actual, expected):
        source = original[effect['dispatchId']]
        match(row, {'订单': order['number'], '结果版本': order['resultVersion'], '出库ID': effect['dispatchId'],
                    '订单序号': str(source['orderScanSeq']), '条码': source['barcode'], '代码': source['productCode'],
                    '净重kg': float(kg(effect['weightGrams'])), '净精确kg': kg(effect['weightGrams']),
                    '原设备ID': source['originDeviceId'], '操作人': source['operatorName']})
    return len(actual)


def check_adjustments(book, order):
    actual = rows(book, '调整凭据')
    assert len(actual) == len(order['adjustments'])
    fields = {'更正号': 'correctionId', '行ID': 'lineId', '结果版本': 'resultVersion', '类型': 'kind',
              '出库ID': 'dispatchId', '前包数': 'beforeCount', '后包数': 'afterCount', '抬头字段': 'headerField',
              '前文本': 'beforeText', '后文本': 'afterText', '原因': 'reason', '审批人': 'approver',
              '凭据依据': 'evidenceReference', '发生时间UTC': 'occurredAt'}
    for row, adjustment in zip(actual, order['adjustments']):
        match(row, {key: adjustment[field] for key, field in fields.items()})
        for prefix, field in [('前', 'beforeWeightGrams'), ('后', 'afterWeightGrams')]:
            match(row, {prefix + '重量kg': float(kg(adjustment[field])), prefix + '精确kg': kg(adjustment[field])})
    return len(actual)


def verify_xlsx(folder, prefix, order):
    with warnings.catch_warnings():
        warnings.filterwarnings('ignore', message='Workbook contains no default style')
        book = openpyxl.load_workbook(folder / (prefix + '.xlsx'), read_only=True, data_only=True)
    try:
        summary = rows(book, '订单汇总')
        assert len(summary) == 2 and summary[1]['订单'] == '合计'
        match(summary[0], {'订单': order['number'], '来源': 'CORRECTED', '结果版本': order['resultVersion'],
                          '原车号': order['originalHeader']['vehicle'], '净车号': order['effectiveHeader']['vehicle'],
                          '原柜号': order['originalHeader']['container'], '净柜号': order['effectiveHeader']['container']})
        for row in summary:
            check_totals(row, order)
        products = rows(book, '产品汇总')
        assert len(products) == len(order['products'])
        for row, product in zip(products, order['products']):
            match(row, {'代码': product['code'], '名称': product['name'], '型号': product['model'], '计划包数': product['plannedCount']})
            check_totals(row, order, product['code'])
        return {'effective_rows': check_details(book, order), 'adjustment_rows': check_adjustments(book, order)}
    finally:
        book.close()


def verify_pdf_adjustments(text, order):
    section = text.split('调整凭据', 1)[1].split('异常记录', 1)[0]
    approvals = re.findall(r'审批人:([0-9a-f-]{36})', section)
    assert Counter(approvals) == Counter(a['approver'] for a in order['adjustments'])
    for item in order['adjustments']:
        start = compact(f"{item['correctionId']}/{item['lineId']};结果版本{item['resultVersion']};")
        block = section.split(start, 1)[1].split('凭据依据:', 1)[0]
        for field in ['dispatchId', 'kind', 'reason', 'approver']:
            assert compact(item[field]) in block, field
        assert compact(f"包数{item['beforeCount']}→{item['afterCount']};重量{kg(item['beforeWeightGrams'])}→{kg(item['afterWeightGrams'])}kg") in block
        occurred = datetime.fromisoformat(item['occurredAt'].replace('Z', '+00:00')) + timedelta(hours=8)
        assert compact(occurred.strftime('%Y-%m-%d %H:%M:%S.%f').rstrip('0').rstrip('.')) in block
        assert compact(item['evidenceReference']) in section
        if item['kind'] == 'HEADER_CORRECTION':
            assert compact(f"{item['headerField']}:{item['beforeText']}→{item['afterText']}") in block


def verify_pdf(folder, prefix, order):
    pdf = PdfReader(folder / (prefix + '.pdf'))
    text = '\n'.join(page.extract_text() or '' for page in pdf.pages)
    (folder / 'pdf-extracted.txt').write_text(text, encoding='utf-8')
    value = compact(text)
    assert '[B@' not in value
    assert compact(f"结果版本{order['resultVersion']}") in value
    counts, weights = totals(order)
    expected = f"合计:原确认{counts[0]}包/{kg(weights[0])}kg;累计调整{counts[1]}包/{kg(weights[1])}kg;本版本净结果{counts[2]}包/{kg(weights[2])}kg"
    assert compact(expected) in value, 'PDF order totals'
    section = value.split('版本有效明细(指定版本净贡献)', 1)[1].split('调整凭据', 1)[0]
    effective = {r['dispatchId']: r for r in order['effectiveLines']}
    for source in order['originalLines']:
        effect = effective[source['dispatchId']]
        if effect['count'] == 0:
            assert source['barcode'] not in section
        else:
            expected = f"{source['orderScanSeq']}{source['barcode']}{source['productCode']}{kg(effect['weightGrams'])}{source['originDeviceId']}/{source['operatorName']}"
            assert compact(expected) in section, 'PDF effective row values'
    verify_pdf_adjustments(value, order)
    return {'pages': len(pdf.pages), 'text_chars': len(text), 'adjustment_rows': len(order['adjustments'])}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('folder', type=Path)
    parser.add_argument('prefix')
    args = parser.parse_args()
    result = {'content_acceptance': 'FAIL'}
    try:
        snapshots = [json.loads((args.folder / f'snapshot-{ext}.json').read_text(encoding='utf-8')) for ext in ['xlsx', 'pdf']]
        assert all(s['metadata']['basis'] == 'CORRECTED' for s in snapshots)
        assert snapshots[0]['orders'] == snapshots[1]['orders']
        order = snapshots[0]['orders'][0]
        result['xlsx'] = verify_xlsx(args.folder, args.prefix, order)
        result['pdf'] = verify_pdf(args.folder, args.prefix, order)
        result['content_acceptance'] = 'PASS'
    except Exception as error:
        result['error'] = f'{type(error).__name__}: {error}'
        raise
    finally:
        (args.folder / 'content-readback.json').write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
        print(json.dumps(result, ensure_ascii=False))


if __name__ == '__main__':
    main()
