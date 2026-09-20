package com.acrylic.outbound.reports;

import java.io.IOException;
import java.util.*;
import static com.acrylic.outbound.reports.ReportText.escape;

final class PdfHtml {
    static String document(ReportSnapshot s) throws IOException {
        var html = new StringBuilder("<!doctype html><html lang='zh-CN'><meta charset='utf-8'><title>装车单</title><style>");
        try (var font = PdfHtml.class.getResourceAsStream("/NotoSansCJKsc-Regular.otf")) {
            if (font == null) throw new IOException("缺少固定中文字体");
            html.append("@font-face{font-family:ReportCJK;src:url(data:font/otf;base64,")
                    .append(Base64.getEncoder().encodeToString(font.readAllBytes())).append(") format('opentype');}");
        }
        try (var css = PdfHtml.class.getResourceAsStream("/report.css")) {
            html.append(new String(Objects.requireNonNull(css).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        }
        html.append("@page { @top-left { content: \"").append(ReportText.notice(s)).append("\"; } }");
        html.append("</style><body>");
        html.append("<h1>装车单</h1><p>").append(escape(ReportText.identity(s))).append("</p>");
        html.append("<p>期间（Asia/Shanghai）：").append(s.metadata().fromDate()).append(" 至 ")
                .append(s.metadata().throughDate()).append("；调整按原出库日归属，重量单位 kg。</p>");
        html.append("<p>来源：").append(escape(s.metadata().sourceDescription())).append("</p>");
        html.append("<p>订单排序依据：").append(escape(s.metadata().orderSortDescription())).append("</p>");
        if (s.orders().isEmpty()) html.append("<p class='empty'>筛选结果为空，无已确认出库记录。</p>");
        if (s.orders().size() != 1) total(html, "全部订单合计", ReportTotals.all(s));
        for (var order : s.orders()) order(html, order);
        return html.append("</body></html>").toString();
    }
    private static void order(StringBuilder h, OrderSnapshot o) {
        h.append("<section><h2>订单 ").append(escape(o.number())).append("</h2><p>")
                .append("修订 ").append(o.revision()).append(" / 结果版本 ").append(o.resultVersion())
                .append(" / 原出库日 ").append(Values.shipmentDay(o)).append(" / 来源 ").append(escape(o.source())).append("</p>");
        h.append("<p>原车号：").append(escape(o.originalHeader().vehicle())).append("；原柜号：")
                .append(escape(o.originalHeader().container())).append("；本版本车号：").append(escape(o.effectiveHeader().vehicle()))
                .append("；本版本柜号：").append(escape(o.effectiveHeader().container())).append("</p>");
        h.append("<p>确认时间（上海）：").append(escape(Values.time(o.confirmedAt()))).append("</p>");
        h.append("<p>顺序依据：").append(escape(o.sequenceProof().kind())).append(" / ")
                .append(escape(o.sequenceProof().reference())).append("</p>");
        h.append("<p>原备注：").append(escape(o.originalHeader().remark())).append("</p><p>本版本备注：")
                .append(escape(o.effectiveHeader().remark())).append("</p>");
        products(h, o);
        PdfDetails.write(h, o);
        h.append("<div class='closing'>");
        total(h, "订单 " + o.number() + " 合计", ReportTotals.order(o));
        h.append("<div class='signature'>操作员签字：________________　复核签字：________________</div></div></section>");
    }
    private static void products(StringBuilder h, OrderSnapshot o) {
        heading(h, "产品汇总", "代码 / 名称 / 型号", "计划包数", "原确认包数 / kg", "累计调整包数 / kg", "本版本净包数 / kg", "净减计划");
        var totals = ReportTotals.products(o);
        for (var p : o.products()) {
            var t = totals.get(p.code());
            row(h, p.code() + " / " + p.name() + " / " + p.model(), p.plannedCount(),
                    t.originalCount() + " 包\n" + Values.kg(t.originalGrams()) + " kg",
                    t.adjustedCount() + " 包\n" + Values.kg(t.adjustedGrams()) + " kg",
                    t.netCount() + " 包\n" + Values.kg(t.netGrams()) + " kg",
                    java.math.BigInteger.valueOf(t.netCount()).subtract(java.math.BigInteger.valueOf(p.plannedCount())));
        }
        h.append("</tbody></table>");
    }
    static void total(StringBuilder h, String title, ReportTotals.Total t) {
        h.append("<p class='total'>").append(escape(title)).append("：原确认 ").append(t.originalCount())
                .append(" 包 / ").append(Values.kg(t.originalGrams())).append(" kg；累计调整 ").append(t.adjustedCount())
                .append(" 包 / ").append(Values.kg(t.adjustedGrams())).append(" kg；本版本净结果 ").append(t.netCount())
                .append(" 包 / ").append(Values.kg(t.netGrams())).append(" kg。</p>");
    }
    static void heading(StringBuilder h, String title, String... columns) {
        h.append("<h3>").append(escape(title)).append("</h3><table class='")
                .append(title.equals("产品汇总") ? "products" : "details").append("'><thead><tr>");
        for (String column : columns) h.append("<th>").append(escape(column)).append("</th>");
        h.append("</tr></thead><tbody>");
    }
    static void row(StringBuilder h, Object... cells) {
        h.append("<tr>");
        for (Object cell : cells) h.append("<td>").append(escape(cell)).append("</td>");
        h.append("</tr>");
    }
    private PdfHtml() { }
}
