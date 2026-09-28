package com.acrylic.outbound.reports;

import java.util.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import static com.acrylic.outbound.reports.ExcelRenderer.weight;

final class ExcelDetails {
    static void write(SXSSFWorkbook book, ReportSnapshot s, int limit) {
        var original = table(book, s, limit, "原始扫码明细", "订单", "原出库日", "订单序号", "条码", "代码", "重量kg", "精确kg", "原始重量文本",
                "原设备ID", "设备序号", "操作人", "输入来源", "接收时间UTC", "保存时间UTC", "时间质量", "出库周期", "出库ID", "输入ID", "备注");
        var net = table(book, s, limit, "版本有效明细", "订单", "原出库日", "结果版本", "订单序号", "条码", "代码", "净重kg", "净精确kg", "原设备ID", "操作人", "出库ID");
        var adjustments = table(book, s, limit, "调整凭据", "订单", "归属原出库日", "发生时间UTC", "更正号", "行ID", "结果版本", "类型", "出库ID",
                "前包数", "后包数", "前重量kg", "前精确kg", "后重量kg", "后精确kg", "抬头字段", "前文本", "后文本", "原因", "审批人", "凭据依据");
        var exceptions = table(book, s, limit, "异常记录", "订单", "事件ID", "订单序号", "原设备ID", "类型", "条码", "说明", "发生时间UTC");
        for (var o : s.orders()) {
            lines(o, original, net);
            for (var a : o.adjustments()) adjustment(adjustments, o, a);
            for (var e : o.exceptions()) exceptions.add(Values.displayNumber(o), e.eventId(), e.orderScanSeq() == null ? "" : e.orderScanSeq().toString(),
                    e.originDeviceId(), e.kind(), e.barcode(), e.detail(), e.occurredAt());
        }
        original.finish(); net.finish(); adjustments.finish(); exceptions.finish();
    }
    private static void lines(OrderSnapshot o, WorkbookTable original, WorkbookTable net) {
        var effective = ReportTotals.effective(o);
        for (var r : o.originalLines()) {
            original.add(Values.displayNumber(o), Values.shipmentDay(o), Long.toString(r.orderScanSeq()), r.barcode(), r.productCode(),
                    weight(Values.grams(r.weightGrams())), r.rawWeight(), r.originDeviceId(), Long.toString(r.deviceCaptureSeq()),
                    r.operatorName(), r.inputSource(), r.receivedAt(), r.savedAt(), r.timeQuality(), r.dispatchCycle(), r.dispatchId(), r.captureId(), r.remark());
            var e = effective.get(r.dispatchId());
            if (e.count() == 1) net.add(Values.displayNumber(o), Values.shipmentDay(o), o.resultVersion(), Long.toString(r.orderScanSeq()),
                    r.barcode(), r.productCode(), weight(Values.grams(e.weightGrams())), r.originDeviceId(), r.operatorName(), r.dispatchId());
        }
    }
    private static void adjustment(WorkbookTable table, OrderSnapshot o, Adjustment a) {
        table.add(Values.displayNumber(o), Values.shipmentDay(o), a.occurredAt(), a.correctionId(), a.lineId(), a.resultVersion(), a.kind(), a.dispatchId(),
                a.beforeCount(), a.afterCount(), weight(Values.grams(a.beforeWeightGrams())), weight(Values.grams(a.afterWeightGrams())),
                a.headerField(), a.beforeText(), a.afterText(), a.reason(), a.approver(), a.evidenceReference());
    }
    private static WorkbookTable table(SXSSFWorkbook book, ReportSnapshot s, int limit, String name, String... columns) {
        var list = new ArrayList<>(List.of(name, ReportText.notice(s))); list.addAll(Arrays.asList(columns));
        return new WorkbookTable(book, list.toArray(String[]::new), limit);
    }
    private ExcelDetails() { }
}
