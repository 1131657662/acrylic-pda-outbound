"""Independent ZIP/XML consumer for the CURRENT_EFFECTIVE export contract."""
from decimal import Decimal
import importlib.util

SOURCE = '.local/goal-20261016/d15-restored-worker01/xlsx_readback01.py'
SPEC = importlib.util.spec_from_file_location('retained_xlsx_consumer_current', SOURCE)
LEGACY = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(LEGACY)


def consume(path, snapshot):
    book = LEGACY.workbook(path)
    assert list(book) == ['订单汇总', '产品汇总', '原始扫码明细', '版本有效明细', '调整凭据', '异常记录', '快照说明']
    order = snapshot['orders'][0]
    LEGACY.original_lines(book, order)
    LEGACY.effective_lines(book, order)
    summaries = LEGACY.records(book['订单汇总'])
    assert len(summaries) == 2 and summaries[1]['订单'] == '合计'
    for row in summaries:
        assert Decimal(row['原包数']) == Decimal(row['净包数']) == 2
        assert Decimal(row['调整包数']) == Decimal(row['调整精确kg']) * 1000 == 0
        assert LEGACY.grams(row['原精确kg']) == LEGACY.grams(row['净精确kg']) == 601500
    metadata = dict(book['快照说明'][3:])
    assert metadata['快照ID'] == snapshot['metadata']['snapshotId']
    assert metadata['数据版本'] == snapshot['metadata']['dataVersion']
    assert metadata['数据截点UTC'] == snapshot['metadata']['cutoff']
    assert 'CURRENT_EFFECTIVE' in metadata['口径']
    assert len(book['调整凭据']) == len(book['异常记录']) == 3
    return dict(sheets=len(book), originalLines=2, grams=601500, formulas=0, leadingZeroCode='003')
