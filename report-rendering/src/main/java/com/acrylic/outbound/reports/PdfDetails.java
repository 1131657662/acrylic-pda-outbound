package com.acrylic.outbound.reports;

import static com.acrylic.outbound.reports.PdfHtml.*;
import static com.acrylic.outbound.reports.ReportText.escape;

final class PdfDetails {
    static void write(StringBuilder h, OrderSnapshot o) {
        heading(h, "原始扫码明细（确认时原事实）", "原序号", "条码 / 产品代码", "原重kg", "原设备 / 操作人", "采集时间信息", "备注");
        for (var r : o.originalLines()) row(h, r.orderScanSeq(), r.barcode() + "\n" + r.productCode(), Values.kg(Values.grams(r.weightGrams())),
                r.originDeviceId() + " / " + r.operatorName(), "接收 " + r.receivedAt() + "\n保存 " + r.savedAt() + "\n" + r.timeQuality(), r.remark());
        h.append("</tbody></table>");
        heading(h, "版本有效明细（含归零行）", "原序号", "条码 / 产品代码", "净包数", "净重kg", "原设备 / 操作人");
        var effective = ReportTotals.effective(o);
        for (var r : o.originalLines()) {
            var e = effective.get(r.dispatchId());
            row(h, r.orderScanSeq(), r.barcode() + "\n" + r.productCode(), e.count(),
                    Values.kg(Values.grams(e.weightGrams())), r.originDeviceId() + " / " + r.operatorName());
        }
        h.append("</tbody></table>");
        adjustments(h, o);
        heading(h, "异常记录（不计入实绩）", "原序号", "类型 / 条码", "原设备", "说明 / 发生时间");
        for (var e : o.exceptions()) row(h, e.orderScanSeq() == null ? "未提供" : e.orderScanSeq(),
                e.kind() + " / " + e.barcode(), e.originDeviceId(), e.detail() + "\n" + e.occurredAt());
        if (o.exceptions().isEmpty()) row(h, "", "无异常记录", "", "");
        h.append("</tbody></table>");
    }
    private static void adjustments(StringBuilder h, OrderSnapshot o) {
        h.append("<h3>调整凭据</h3>");
        if (o.adjustments().isEmpty()) h.append("<p>本快照无已生效调整。</p>");
        for (var a : o.adjustments()) {
            h.append("<article><p><b>").append(escape(a.correctionId())).append(" / ").append(escape(a.lineId()))
                    .append("</b>；结果版本 ").append(a.resultVersion()).append("；").append(escape(a.kind())).append("</p>");
            h.append("<p>发生时间（上海）：").append(escape(Values.time(a.occurredAt())))
                    .append("；仍归属原出库日 ").append(Values.shipmentDay(o)).append("；原出库ID：").append(escape(a.dispatchId())).append("</p>");
            h.append("<p>包数 ").append(a.beforeCount()).append(" → ").append(a.afterCount())
                    .append("；重量 ").append(Values.kg(Values.grams(a.beforeWeightGrams()))).append(" → ")
                    .append(Values.kg(Values.grams(a.afterWeightGrams()))).append(" kg。</p>");
            if (a.headerChange()) h.append("<p>").append(escape(a.headerField()))
                    .append("：").append(escape(a.beforeText())).append(" → ").append(escape(a.afterText())).append("</p>");
            h.append("<p>原因：").append(escape(a.reason())).append("；审批人：").append(escape(a.approver()))
                    .append("；凭据依据：").append(escape(a.evidenceReference())).append("</p></article>");
        }
    }
    private PdfDetails() { }
}
