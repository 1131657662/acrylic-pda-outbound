package com.acrylic.outbound.reports;

import java.time.*;
import java.util.List;

/** Module-local immutable input; deliberately not an HTTP or database contract. */
public record ReportSnapshot(Metadata metadata, List<OrderSnapshot> orders) {
    public ReportSnapshot { orders = List.copyOf(orders); }
    public static final String TEMPLATE = "r08-general-2";
    /**
     * 报告口径。{@code ORIGINAL_CONFIRMED}＝原确认 V1；{@code CURRENT_EFFECTIVE}＝按固定版本的有效结果；
     * {@code CORRECTED}＝**更正净结果**（D 片2 新增：由更正凭据推出的指定版本净结果）。
     * 各消费点语义：{@code PdfHtml}/{@code ReportText} 对非原确认口径用"净结果"文案；
     * {@code ScanRecordExcelRenderer} 仅支持原确认并**明确拒绝**更正净结果（更正净结果没有原始扫码序号语义）。
     */
    public enum Basis { ORIGINAL_CONFIRMED, CURRENT_EFFECTIVE, CORRECTED }
    public enum Provenance { TEST_SAMPLE, SOURCE_SNAPSHOT }
    public record Metadata(String snapshotId, String dataVersion, Instant cutoff,
                           Instant generatedAt, LocalDate fromDate, LocalDate throughDate,
                           Basis basis, Provenance provenance, String sourceDescription,
                           String orderSortDescription) { }
}
