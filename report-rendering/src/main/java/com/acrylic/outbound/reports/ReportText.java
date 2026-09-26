package com.acrylic.outbound.reports;

final class ReportText {
    static String notice(ReportSnapshot s) {
        return (s.metadata().provenance() == ReportSnapshot.Provenance.TEST_SAMPLE ? "测试样例 / 非业务凭据 | " : "固定数据快照 | ")
                + (s.metadata().basis() == ReportSnapshot.Basis.ORIGINAL_CONFIRMED
                ? "按原出库日归属 | 原确认V1（不含后续更正）" : "按原出库日归属净值 | 指定版本净结果") + " | 模板 " + ReportSnapshot.TEMPLATE;
    }
    static String identity(ReportSnapshot s) {
        var m = s.metadata();
        return notice(s) + " | 快照 " + m.snapshotId() + " | 数据版本 " + m.dataVersion()
                + " | 截点 " + m.cutoff() + " | 生成 " + m.generatedAt();
    }
    static String escape(Object value) {
        return String.valueOf(value).replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
    private ReportText() { }
}
