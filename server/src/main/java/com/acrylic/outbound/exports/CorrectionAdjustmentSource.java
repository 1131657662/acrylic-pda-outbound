package com.acrylic.outbound.exports;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.reports.Adjustment;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Every published result effect is exportable, including effects with no inventory postings. */
@Component
public class CorrectionAdjustmentSource {
    private final JdbcTemplate jdbc;
    private final CorrectionWithdrawalSource withdrawals;
    private final HeaderCorrectionSource headers;
    public CorrectionAdjustmentSource(JdbcTemplate jdbc, CorrectionWithdrawalSource withdrawals, HeaderCorrectionSource headers) {
        this.jdbc = jdbc; this.withdrawals = withdrawals; this.headers = headers;
    }

    public List<Adjustment> read(UUID order, long resultVersion) {
        var rows = jdbc.queryForList("""
            SELECT BIN_TO_UUID(c.id) line_id,BIN_TO_UUID(c.document_id) document_id,BIN_TO_UUID(c.dispatch_id) dispatch_id,
              c.before_count,c.before_weight_g,c.target_count,c.target_weight_g,c.business_occurred_at,c.delivery_code,
              CAST(r.result_version AS CHAR) applied_version,d.type,d.reason,BIN_TO_UUID(d.approved_by) approved_by,d.status,
              IF(r.result_version>d.effective_result_version,1,0) is_withdrawal,
              d.withdrawal_confirmed_at,BIN_TO_UUID(d.withdrawal_confirmed_by) withdrawal_confirmed_by,
              (SELECT COUNT(*) FROM correction_ledger l WHERE l.correction_line_id=c.id AND l.restores_entry_id IS NULL) ledger_rows
            FROM order_result_version r JOIN correction_document d ON d.id=r.source_correction_id AND d.order_id=r.order_id
            JOIN correction_line c ON c.document_id=d.id
            JOIN correction_effect e ON e.line_id=c.id AND e.result_version=d.effective_result_version AND e.order_id=r.order_id
            WHERE r.order_id=? AND r.result_version BETWEEN 2 AND ? AND d.type<>'HEADER_CORRECTION' AND (
              (r.result_version=d.effective_result_version AND d.status IN ('EFFECTIVE','WITHDRAW_PENDING','WITHDRAWN'))
              OR (r.result_version>d.effective_result_version AND d.status='WITHDRAWN'
                AND d.withdrawal_confirmed_by IS NOT NULL AND r.created_at=d.withdrawal_confirmed_at))
            ORDER BY r.result_version,c.line_no
            """, Ids.bytes(order), resultVersion);
        var result = new ArrayList<Adjustment>();
        for (var row : rows) result.add(((Number) row.get("is_withdrawal")).intValue() == 1
            ? withdrawals.read(order, row) : physical(row));
        result.addAll(headers.read(order, resultVersion));
        result.sort(Comparator.comparingLong(Adjustment::resultVersion).thenComparing(Adjustment::headerField));
        ExportFacts.check(!result.isEmpty(), "该结果版本没有可追溯的更正凭据行");
        return result;
    }

    private Adjustment physical(Map<String, Object> row) {
        String dispatch = row.get("dispatch_id") == null ? "" : row.get("dispatch_id").toString();
        ExportFacts.check(!dispatch.isEmpty(), "实物更正凭据缺少出库行引用");
        long beforeCount = number(row, "before_count"), beforeWeight = number(row, "before_weight_g");
        long targetCount = number(row, "target_count"), targetWeight = number(row, "target_weight_g");
        var kind = kind(row.get("type").toString(), targetCount, targetWeight);
        ExportFacts.check(beforeCount == 1 && beforeWeight > 0 && targetWeight >= 0, "更正凭据的 before/after 形状非法");
        return new Adjustment(row.get("document_id").toString(), row.get("line_id").toString(),
            Long.parseLong(row.get("applied_version").toString()), kind, dispatch, (int) beforeCount, (int) targetCount,
            Long.toString(beforeWeight), Long.toString(targetWeight), "", "", "", occurredAt(row.get("business_occurred_at")),
            row.get("reason").toString(), row.get("approved_by") == null ? "" : row.get("approved_by").toString(),
            "correction_document:" + row.get("document_id") + " / correction_line:" + row.get("line_id") + " / ledger_rows:" + row.get("ledger_rows"));
    }

    private static Adjustment.Kind kind(String type, long count, long weight) {
        if ("WEIGHT_CORRECTION".equals(type)) {
            if (count == 0 && weight == 0) return Adjustment.Kind.DISPATCH_CANCELLATION;
            if (count == 1 && weight > 0) return Adjustment.Kind.WEIGHT_CORRECTION;
            throw new BusinessException(409, "CORRECTION_EXPORT_KIND_AMBIGUOUS", "重量更正的包数/重量组合无法归类");
        }
        ExportFacts.check(count == 0 && weight == 0, "退货/出库取消的更正形状非法（应归零）");
        return switch (type) {
            case "PHYSICAL_RETURN" -> Adjustment.Kind.PHYSICAL_RETURN;
            case "DISPATCH_CANCELLATION" -> Adjustment.Kind.DISPATCH_CANCELLATION;
            default -> throw new BusinessException(409, "EXPORT_SOURCE_UNAVAILABLE", "更正类型无法映射到调整凭据");
        };
    }

    private static long number(Map<String, Object> row, String key) { return ((Number) row.get(key)).longValue(); }
    private static Instant occurredAt(Object value) {
        if (value instanceof java.sql.Timestamp stamp) return stamp.toInstant();
        if (value instanceof LocalDateTime local) return local.toInstant(ZoneOffset.UTC);
        if (value instanceof Instant instant) return instant;
        throw new BusinessException(409, "EXPORT_SOURCE_UNAVAILABLE", "更正凭据的业务发生时刻不可解析");
    }
}
