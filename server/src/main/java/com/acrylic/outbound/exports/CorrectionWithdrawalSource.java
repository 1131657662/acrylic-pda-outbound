package com.acrylic.outbound.exports;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.reports.Adjustment;
import com.acrylic.outbound.submissions.SubmissionJson;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Read-only proof of result restoration and the independent entry-by-entry accounting restoration. */
@Component
public final class CorrectionWithdrawalSource {
    private final JdbcTemplate jdbc;
    private final CorrectionWithdrawalLedgerProof ledger;
    public CorrectionWithdrawalSource(JdbcTemplate jdbc) { this.jdbc = jdbc; ledger = new CorrectionWithdrawalLedgerProof(jdbc); }

    Adjustment read(UUID order, Map<String, Object> row) {
        String document = row.get("document_id").toString(), line = row.get("line_id").toString();
        long version = Long.parseLong(row.get("applied_version").toString());
        var receipt = receipt(order, document, version, row.get("withdrawal_confirmed_by").toString());
        String references = ledger.read(row);
        var decision = SubmissionJson.parse(receipt.response()).path("businessDecision");
        ExportFacts.check("WITHDRAWN".equals(decision.path("status").asText()), "撤销确认回执状态不符");
        resultProof(order, version, decision);
        return new Adjustment(document, line, version, Adjustment.Kind.CORRECTION_WITHDRAWAL,
            row.get("dispatch_id").toString(), number(row, "target_count").intValue(), number(row, "before_count").intValue(),
            number(row, "target_weight_g").toString(), number(row, "before_weight_g").toString(), "", "", "",
            confirmedAt(row.get("withdrawal_confirmed_at")), SubmissionJson.parse(receipt.command()).path("reason").asText(),
            row.get("approved_by").toString(), "correction_document:" + document + " / correction_line:" + line
            + " / withdrawal_receipt:" + receipt.id() + " / confirmed_by:" + receipt.actor() + " / " + references);
    }

    private Receipt receipt(UUID order, String document, long version, String confirmer) {
        var rows = jdbc.query("""
            SELECT BIN_TO_UUID(id),canonical_command_bytes,response_bytes,BIN_TO_UUID(actor_id)
            FROM correction_command_receipt WHERE order_id=? AND document_id=UUID_TO_BIN(?)
              AND command_type='WITHDRAW_CONFIRM' AND status='RECORDED'
            """, (rs, n) -> new Receipt(rs.getString(1), rs.getBytes(2), rs.getBytes(3), rs.getString(4)), Ids.bytes(order), document);
        ExportFacts.check(rows.size() == 1, "撤销确认回执缺失或不唯一");
        var receipt = rows.getFirst(); var decision = SubmissionJson.parse(receipt.response()).path("businessDecision");
        ExportFacts.check(receipt.actor().equals(confirmer) && decision.path("resultVersion").asLong() == version
            && document.equals(decision.path("documentId").asText()), "撤销确认回执与版本不一致");
        return receipt;
    }

    private void resultProof(UUID order, long version, com.fasterxml.jackson.databind.JsonNode decision) {
        var result = jdbc.queryForMap("SELECT actual_count,weight_g FROM order_result_version WHERE order_id=? AND result_version=?",
            Ids.bytes(order), version);
        ExportFacts.check(number(result, "actual_count").longValue() == decision.path("effectiveCount").asLong()
            && number(result, "weight_g").longValue() == decision.path("effectiveWeightG").asLong(), "撤销结果与回执不一致");
    }
    private static Number number(Map<String, Object> row, String key) { return (Number) row.get(key); }
    private static Instant confirmedAt(Object value) {
        if (value instanceof java.sql.Timestamp timestamp) return timestamp.toInstant();
        if (value instanceof java.time.LocalDateTime local) return local.toInstant(java.time.ZoneOffset.UTC);
        if (value instanceof Instant instant) return instant;
        throw new com.acrylic.outbound.common.BusinessException(409, "EXPORT_SOURCE_UNAVAILABLE", "撤销确认时刻不可解析");
    }
    private record Receipt(String id, byte[] command, byte[] response, String actor) { }
}
