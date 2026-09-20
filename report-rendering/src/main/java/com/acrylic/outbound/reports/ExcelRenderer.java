package com.acrylic.outbound.reports;

import java.io.*;
import java.math.BigInteger;
import java.util.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

public final class ExcelRenderer {
    private final int dataRowsPerSheet;
    public ExcelRenderer() { this(1_048_573); }
    public ExcelRenderer(int dataRowsPerSheet) {
        Values.require(dataRowsPerSheet >= 1 && dataRowsPerSheet <= 1_048_573, "工作表数据行上限错误");
        this.dataRowsPerSheet = dataRowsPerSheet;
    }
    public void render(ReportSnapshot snapshot, OutputStream output) throws IOException {
        SnapshotValidator.validate(snapshot);
        try (var book = new SXSSFWorkbook(100)) {
            book.setCompressTempFiles(true);
            book.getXSSFWorkbook().getProperties().getCoreProperties().setTitle("装车台账");
            book.getXSSFWorkbook().getProperties().getCoreProperties().setDescription(ReportText.identity(snapshot));
            summaries(book, snapshot);
            ExcelDetails.write(book, snapshot, dataRowsPerSheet);
            metadata(book, snapshot);
            book.write(output);
        }
    }
    private void summaries(SXSSFWorkbook book, ReportSnapshot s) {
        var orders = table(book, s, "订单汇总", "订单", "原出库日", "来源", "修订", "结果版本", "原车号", "净车号",
                "原柜号", "净柜号", "原包数", "调整包数", "净包数", "原重量kg", "原精确kg", "调整kg", "调整精确kg", "净重kg", "净精确kg");
        var products = table(book, s, "产品汇总", "订单", "原出库日", "代码", "名称", "型号", "计划包数", "原包数", "调整包数", "净包数",
                "净减计划", "原重量kg", "原精确kg", "调整kg", "调整精确kg", "净重kg", "净精确kg");
        for (var o : s.orders()) {
            var t = ReportTotals.order(o);
            orders.add(o.number(), Values.shipmentDay(o), o.source(), o.revision(), o.resultVersion(),
                    o.originalHeader().vehicle(), o.effectiveHeader().vehicle(), o.originalHeader().container(),
                    o.effectiveHeader().container(), t.originalCount(), t.adjustedCount(), t.netCount(),
                    weight(t.originalGrams()), weight(t.adjustedGrams()), weight(t.netGrams()));
            var totals = ReportTotals.products(o);
            for (var p : o.products()) product(products, o, p, totals.get(p.code()));
        }
        var t = ReportTotals.all(s);
        orders.add("合计", "", "", "", "", "", "", "", "", t.originalCount(), t.adjustedCount(), t.netCount(),
                weight(t.originalGrams()), weight(t.adjustedGrams()), weight(t.netGrams()));
        orders.finish(); products.finish();
    }
    private void product(WorkbookTable table, OrderSnapshot o, OrderSnapshot.Product p, ReportTotals.Total t) {
        table.add(o.number(), Values.shipmentDay(o), p.code(), p.name(), p.model(), p.plannedCount(),
                t.originalCount(), t.adjustedCount(), t.netCount(), BigInteger.valueOf(t.netCount()).subtract(BigInteger.valueOf(p.plannedCount())),
                weight(t.originalGrams()), weight(t.adjustedGrams()), weight(t.netGrams()));
    }
    private void metadata(SXSSFWorkbook book, ReportSnapshot s) {
        var m = s.metadata(); var table = table(book, s, "快照说明", "项目", "值");
        table.add("快照ID", m.snapshotId()); table.add("数据版本", m.dataVersion());
        table.add("模板版本", ReportSnapshot.TEMPLATE); table.add("口径", "SHIPMENT_DATE_NET / " + m.basis());
        table.add("期间（上海）", m.fromDate() + " 至 " + m.throughDate());
        table.add("数据截点UTC", m.cutoff()); table.add("生成时间UTC", m.generatedAt());
        table.add("来源", m.provenance() + " / " + m.sourceDescription());
        table.add("订单顺序依据", m.orderSortDescription());
        table.add("明细顺序", "固定成员顺序；每单持久化 orderScanSeq；不声称不同离线设备存在全局物理顺序。");
        table.add("重量", "整数克精确求和；超过15位克数的数值kg列留空，使用精确kg文本列；原始重量文本独立保留。");
        for (var o : s.orders()) {
            table.add("顺序依据 / " + o.number(), o.sequenceProof().kind() + " / " + o.sequenceProof().reference());
            table.add("原备注 / " + o.number(), o.originalHeader().remark());
            table.add("净备注 / " + o.number(), o.effectiveHeader().remark());
        }
        table.finish();
    }
    private WorkbookTable table(SXSSFWorkbook b, ReportSnapshot s, String name, String... headers) {
        var description = new ArrayList<>(List.of(name, ReportText.notice(s)));
        description.addAll(Arrays.asList(headers));
        return new WorkbookTable(b, description.toArray(String[]::new), dataRowsPerSheet);
    }
    static WorkbookTable.Weight weight(BigInteger value) { return new WorkbookTable.Weight(value); }
}
