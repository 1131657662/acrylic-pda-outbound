package com.acrylic.outbound.exports;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.reports.Adjustment;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 更正凭据（{@code correction_line} ＋其 {@code correction_ledger} 行）→ 渲染用 {@link Adjustment} 列表。
 *
 * <p>**为什么单独一类**：这里承载业主 2026-09-28 决策④ 的"**边界层转换**"——引擎写
 * `WEIGHT_CORRECTION + target_count=0` 表示整行归零，而渲染侧 `AdjustmentValidator` 要求
 * `WEIGHT_CORRECTION` 必须保持 1 包。转换只在此发生：**不改引擎（R07-3）、不改渲染（R08 已验证面）**，
 * 且**只对已证明合法的形状**做转换，其余组合报 {@code CORRECTION_EXPORT_KIND_AMBIGUOUS}，
 * **绝不静默把合法组合解释成取消**。
 *
 * <p>同时这里取**从 v2 到目标版本的整条更正链**：渲染侧要求调整版本从 2 起连续
 * （`AdjustmentValidator` 的 `lastVersion` 必须走到 `resultVersion`），只给目标版本会在 v3+ 被拒。
 */
@Component
public class CorrectionAdjustmentSource {
    private final JdbcTemplate jdbc;
    public CorrectionAdjustmentSource(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    /** 该订单从 v2 到 {@code resultVersion} 的全部更正凭据（按版本、行号升序）。 */
    public List<Adjustment> read(UUID order, long resultVersion) {
        var rows = jdbc.queryForList("""
            SELECT BIN_TO_UUID(c.id) line_id,BIN_TO_UUID(c.document_id) document_id,BIN_TO_UUID(c.dispatch_id) dispatch_id,
              c.before_count,c.before_weight_g,c.target_count,c.target_weight_g,c.business_occurred_at,c.delivery_code,
              CAST(d.effective_result_version AS CHAR) applied_version,d.type,d.reason,BIN_TO_UUID(d.approved_by) approved_by,d.status,
              (SELECT COUNT(*) FROM correction_ledger l WHERE l.correction_line_id=c.id) ledger_rows
            FROM correction_line c JOIN correction_document d ON d.id=c.document_id
            WHERE d.order_id=? AND d.effective_result_version BETWEEN 2 AND ?
            ORDER BY d.effective_result_version,c.line_no
            """, Ids.bytes(order), resultVersion);
        var result = new ArrayList<Adjustment>();
        for (var row : rows) {
            if (((Number) row.get("ledger_rows")).longValue() == 0) continue; // 未写账的行不是净结果凭据
            result.add("HEADER_CORRECTION".equals(String.valueOf(row.get("type")))
                ? header(row) : physical(row));
        }
        ExportFacts.check(!result.isEmpty(), "该结果版本没有可追溯的更正凭据行");
        return result;
    }

    /** 实物更正（有出库行）：按**实际效果**归类，见类注释的边界层转换说明。 */
    private Adjustment physical(Map<String, Object> row) {
        String type = String.valueOf(row.get("type"));
        String dispatchId = row.get("dispatch_id") == null ? "" : String.valueOf(row.get("dispatch_id"));
        ExportFacts.check(!dispatchId.isEmpty(), "实物更正凭据缺少出库行引用");
        long beforeCount = ((Number) row.get("before_count")).longValue();
        long beforeWeight = ((Number) row.get("before_weight_g")).longValue();
        long targetCount = ((Number) row.get("target_count")).longValue();
        long targetWeight = ((Number) row.get("target_weight_g")).longValue();
        var kind = switch (type) {
            case "WEIGHT_CORRECTION" -> {
                if (targetCount == 0 && targetWeight == 0) yield Adjustment.Kind.DISPATCH_CANCELLATION;
                if (targetCount == 1 && targetWeight > 0) yield Adjustment.Kind.WEIGHT_CORRECTION;
                throw new BusinessException(409, "CORRECTION_EXPORT_KIND_AMBIGUOUS",
                    "重量更正的包数/重量组合无法归类（既不归零也未保持 1 包）：不得静默解释为取消");
            }
            case "PHYSICAL_RETURN" -> { requireZeroed(targetCount, targetWeight, "退货"); yield Adjustment.Kind.PHYSICAL_RETURN; }
            case "DISPATCH_CANCELLATION" -> { requireZeroed(targetCount, targetWeight, "出库取消"); yield Adjustment.Kind.DISPATCH_CANCELLATION; }
            default -> throw new BusinessException(409, "EXPORT_SOURCE_UNAVAILABLE", "更正类型无法映射到调整凭据");
        };
        boolean withdrawn = "WITHDRAWN".equals(String.valueOf(row.get("status")));
        if (withdrawn) { beforeCount = 0; beforeWeight = 0; targetCount = 0; targetWeight = 0; }
        ExportFacts.check(withdrawn || (beforeCount == 1 && beforeWeight > 0 && targetWeight >= 0),
            "更正凭据的 before/after 形状非法");
        return new Adjustment(id(row, "document_id"), id(row, "line_id"), version(row), kind, dispatchId,
            (int) beforeCount, (int) targetCount, Long.toString(beforeWeight), Long.toString(targetWeight),
            "", "", "", occurredAt(row), String.valueOf(row.get("reason")), approver(row),
            trace(row) + (withdrawn ? " / 已撤销（该版本净调整归零）" : ""));
    }

    /**
     * 抬头更正：无出库行。渲染侧 `AdjustmentValidator.header` 要求抬头字段名存在且前值匹配；
     * 撤销时净效果回到前值。
     */
    private Adjustment header(Map<String, Object> row) {
        String field = row.get("delivery_code") == null ? "" : String.valueOf(row.get("delivery_code"));
        String before = text(row.get("before_text"));
        String after = text(row.get("after_text"));
        ExportFacts.check(!field.isEmpty() && !before.isEmpty(), "抬头更正缺少字段名或前值（无法回放）");
        boolean withdrawn = "WITHDRAWN".equals(String.valueOf(row.get("status")));
        if (withdrawn) after = before;
        ExportFacts.check(withdrawn || !after.equals(before), "抬头更正的前后值相同");
        return new Adjustment(id(row, "document_id"), id(row, "line_id"), version(row),
            Adjustment.Kind.HEADER_CORRECTION, "", 0, 0, "0", "0", field, before, after,
            occurredAt(row), String.valueOf(row.get("reason")), approver(row),
            trace(row) + (withdrawn ? " / 已撤销（净效果回到前值）" : ""));
    }

    private static void requireZeroed(long targetCount, long targetWeight, String label) {
        ExportFacts.check(targetCount == 0 && targetWeight == 0, label + "的更正形状非法（应归零）");
    }
    private static String id(Map<String, Object> row, String column) { return String.valueOf(row.get(column)); }
    private static long version(Map<String, Object> row) { return Long.parseLong(String.valueOf(row.get("applied_version"))); }
    private static String approver(Map<String, Object> row) {
        return row.get("approved_by") == null ? "" : String.valueOf(row.get("approved_by"));
    }
    private static String text(Object value) { return value == null ? "" : String.valueOf(value); }
    private static String trace(Map<String, Object> row) {
        return "correction_document:" + row.get("document_id") + " / correction_line:" + row.get("line_id")
            + " / ledger_rows:" + row.get("ledger_rows");
    }
    private static Instant occurredAt(Map<String, Object> row) {
        Object value = row.get("business_occurred_at");
        if (value instanceof java.sql.Timestamp stamp) return stamp.toInstant();
        if (value instanceof LocalDateTime local) return local.toInstant(ZoneOffset.UTC);
        if (value instanceof Instant instant) return instant;
        throw new BusinessException(409, "EXPORT_SOURCE_UNAVAILABLE", "更正凭据的业务发生时刻不可解析");
    }
}
