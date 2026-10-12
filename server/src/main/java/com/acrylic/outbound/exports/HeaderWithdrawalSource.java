package com.acrylic.outbound.exports;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.correction.CorrectionHeaderEvidence;
import com.acrylic.outbound.reports.Adjustment;
import com.acrylic.outbound.submissions.SubmissionJson;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Reads signed scalar compensation without inventing dispatch, ledger or numeric correction lines. */
@Component
public final class HeaderWithdrawalSource {
    private final JdbcTemplate jdbc;
    private final CorrectionHeaderEvidence evidence;
    public HeaderWithdrawalSource(JdbcTemplate jdbc, CorrectionHeaderEvidence evidence) { this.jdbc = jdbc; this.evidence = evidence; }

    List<Adjustment> read(UUID order, long targetVersion) {
        var rows = jdbc.queryForList("""
            SELECT BIN_TO_UUID(d.id) document,r.result_version,CAST(p.header_snapshot AS CHAR) before_header,
              CAST(r.header_snapshot AS CHAR) after_header,
              r.actual_count=p.actual_count AND r.weight_g=p.weight_g AND r.inventory_deducted=p.inventory_deducted same_totals
            FROM order_result_version r JOIN correction_document d ON d.id=r.source_correction_id AND d.order_id=r.order_id
            JOIN order_result_version p ON p.order_id=r.order_id AND p.result_version=r.result_version-1
            WHERE r.order_id=? AND r.result_version BETWEEN 2 AND ? AND d.type='HEADER_CORRECTION'
              AND r.result_version>d.effective_result_version AND d.status='WITHDRAWN'
              AND r.created_at=d.withdrawal_confirmed_at
            ORDER BY r.result_version
            """, Ids.bytes(order), targetVersion);
        var result = new ArrayList<Adjustment>();
        for (var row : rows) result.addAll(adjustments(order, row));
        return result;
    }

    private List<Adjustment> adjustments(UUID order, java.util.Map<String, Object> row) {
        ExportFacts.check(Boolean.TRUE.equals(row.get("same_totals")) || "1".equals(row.get("same_totals").toString()),
            "抬头补偿改变数重或库存政策");
        UUID document = UUID.fromString(row.get("document").toString()); long version = ((Number) row.get("result_version")).longValue();
        var receipt = evidence.withdrawal(order, document, version); var before = SubmissionJson.parse(row.get("before_header").toString());
        var after = SubmissionJson.parse(row.get("after_header").toString()); var result = new ArrayList<Adjustment>();
        for (var field : receipt.at("/businessDecision/headerRestoration/restoredFields")) {
            String key = field.asText(); var old = before.get(key); var value = after.get(key);
            ExportFacts.check(old != null && value != null && old.isValueNode() && value.isValueNode() && !old.equals(value),
                "抬头补偿字段与结果快照不符");
            result.add(new Adjustment(document.toString(), "", version, Adjustment.Kind.CORRECTION_WITHDRAWAL, "", 0, 0, "0", "0",
                key, scalar(old), scalar(value), java.time.Instant.parse(receipt.path("recordedAt").asText()),
                "抬头更正撤销补偿", receipt.at("/actor/principalId").asText(), "correction_document:" + document
                + " / withdrawal_receipt:" + receipt.path("receiptId").asText() + " / headerRestoration:/" + key));
        }
        ExportFacts.check(!result.isEmpty(), "抬头补偿没有可证明的字段恢复"); return result;
    }

    private static String scalar(com.fasterxml.jackson.databind.JsonNode value) { return value.isNull() ? "null" : value.asText(); }
}
