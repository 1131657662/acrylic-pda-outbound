package com.acrylic.outbound.exports;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.reports.Adjustment;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 实物明细/账务与抬头文档/冻结CREATE回执 → 按版本排列的渲染用 {@link Adjustment} 列表。
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
    private final CorrectionWithdrawalSource withdrawals;
    private final HeaderCorrectionSource headers;
    public CorrectionAdjustmentSource(JdbcTemplate jdbc, CorrectionWithdrawalSource withdrawals, HeaderCorrectionSource headers) {
        this.jdbc=jdbc; this.withdrawals=withdrawals; this.headers=headers;
    }

    /** 该订单从 v2 到 {@code resultVersion} 的全部更正凭据（按版本、行号升序）。 */
    public List<Adjustment> read(UUID order, long resultVersion) {
        var rows = jdbc.queryForList("""
            SELECT BIN_TO_UUID(c.id) line_id,BIN_TO_UUID(c.document_id) document_id,BIN_TO_UUID(c.dispatch_id) dispatch_id,
              c.before_count,c.before_weight_g,c.target_count,c.target_weight_g,c.business_occurred_at,c.delivery_code,
              CAST(r.result_version AS CHAR) applied_version,d.type,d.reason,BIN_TO_UUID(d.approved_by) approved_by,d.status,
              IF(r.result_version>d.effective_result_version,1,0) is_withdrawal,
              d.withdrawal_confirmed_at,BIN_TO_UUID(d.withdrawal_confirmed_by) withdrawal_confirmed_by,
              (SELECT COUNT(*) FROM correction_ledger l WHERE l.correction_line_id=c.id AND l.reverses_entry_id IS NULL) ledger_rows
            FROM order_result_version r JOIN correction_document d ON d.id=r.source_correction_id AND d.order_id=r.order_id
            JOIN correction_line c ON c.document_id=d.id
            WHERE r.order_id=? AND r.result_version BETWEEN 2 AND ? AND d.type<>'HEADER_CORRECTION' AND (
              (r.result_version=d.effective_result_version AND d.status IN ('EFFECTIVE','WITHDRAW_PENDING','WITHDRAWN'))
              OR (r.result_version>d.effective_result_version AND d.status='WITHDRAWN'
                AND d.withdrawal_confirmed_by IS NOT NULL AND r.created_at=d.withdrawal_confirmed_at))
            ORDER BY r.result_version,c.line_no
            """, Ids.bytes(order), resultVersion);
        var result = new ArrayList<Adjustment>();
        for (var row : rows) {
            if (((Number) row.get("ledger_rows")).longValue() == 0) continue; // 未写账的行不是净结果凭据
            if (((Number) row.get("is_withdrawal")).intValue() == 1) {
                result.add(withdrawals.read(order,row)); continue;
            }
            result.add(physical(row));
        }
        result.addAll(headers.read(order,resultVersion));
        result.sort(Comparator.comparingLong(Adjustment::resultVersion).thenComparing(Adjustment::headerField));
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
        ExportFacts.check(beforeCount == 1 && beforeWeight > 0 && targetWeight >= 0,
            "更正凭据的 before/after 形状非法");
        return new Adjustment(id(row, "document_id"), id(row, "line_id"), version(row), kind, dispatchId,
            (int) beforeCount, (int) targetCount, Long.toString(beforeWeight), Long.toString(targetWeight),
            "", "", "", occurredAt(row), String.valueOf(row.get("reason")), approver(row),
            trace(row));
    }

    private static void requireZeroed(long targetCount, long targetWeight, String label) {
        ExportFacts.check(targetCount == 0 && targetWeight == 0, label + "的更正形状非法（应归零）");
    }
    private static String id(Map<String, Object> row, String column) { return String.valueOf(row.get(column)); }
    private static long version(Map<String, Object> row) { return Long.parseLong(String.valueOf(row.get("applied_version"))); }
    private static String approver(Map<String, Object> row) {
        return row.get("approved_by") == null ? "" : String.valueOf(row.get("approved_by"));
    }
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
