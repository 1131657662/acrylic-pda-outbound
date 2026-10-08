package com.acrylic.outbound.temporary.command;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.common.Ids;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Binds a continuation's first scan sequence to the trusted stopped source evidence. */
@Repository
public class TemporaryTrustedScanHighWatermark {
    private final JdbcTemplate jdbc;

    public TemporaryTrustedScanHighWatermark(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public long requireNext(UUID set, UUID scope, UUID submission, long round, long next) {
        final List<Watermark> rows;
        try {
            rows = jdbc.query("""
                SELECT e.order_scan_high_watermark,e.capture_high_watermark,
                  COALESCE(ha.start_order_scan_seq,t.next_order_scan_seq,1)-1
                FROM source_evidence_set e JOIN submission s ON s.id=e.submission_id AND s.scope_id=e.scope_id AND s.work_round=e.work_round
                LEFT JOIN temporary_handover_submission hr ON hr.submission_id=s.id AND hr.scope_id=s.scope_id AND hr.to_work_round=s.work_round
                LEFT JOIN temporary_handover_activation ha ON ha.ticket_id=hr.ticket_id AND ha.to_work_round=s.work_round
                  AND ha.new_authorization_id=s.temporary_authorization_id AND ha.target_device_id=s.freeze_device_id AND ha.target_user_id=s.operator_id
                LEFT JOIN temporary_source_continuation_submission r ON r.submission_id=s.id AND r.scope_id=s.scope_id AND r.to_work_round=s.work_round
                LEFT JOIN temporary_source_continuation_activation a ON a.ticket_id=r.ticket_id AND a.scope_id=r.scope_id AND a.from_work_round=r.from_work_round
                  AND a.new_authorization_id=s.temporary_authorization_id AND a.target_device_id=s.freeze_device_id AND a.target_operator_id=s.operator_id
                LEFT JOIN temporary_source_continuation t ON t.id=a.ticket_id AND t.scope_id=s.scope_id AND t.to_work_round=s.work_round
                WHERE e.id=? AND e.scope_id=? AND e.submission_id=? AND e.work_round=?
                  AND (s.work_round=1 OR ((ha.ticket_id IS NOT NULL AND a.ticket_id IS NULL) OR (ha.ticket_id IS NULL AND a.ticket_id IS NOT NULL)))
                FOR UPDATE
                """, (rs, number) -> rs.getObject(1) == null
                    ? new Watermark(null, rs.getLong(2), rs.getLong(3))
                    : new Watermark(rs.getLong(1), rs.getLong(2), rs.getLong(3)),
                Ids.bytes(set), Ids.bytes(scope), Ids.bytes(submission), round);
        } catch (DataAccessException failure) {
            throw conflict("CONTINUATION_HIGH_WATERMARK_UNTRUSTED");
        }
        if (rows.size() != 1 || rows.getFirst().value() == null) throw conflict("CONTINUATION_HIGH_WATERMARK_UNTRUSTED");
        long high = rows.getFirst().value();
        long prefix = rows.getFirst().prefix();
        if (prefix < 0 || high < prefix || rows.getFirst().captureHigh() < 0 || high - prefix > rows.getFirst().captureHigh())
            throw conflict("CONTINUATION_HIGH_WATERMARK_UNTRUSTED");
        final long expected;
        try { expected = Math.addExact(high, 1); }
        catch (ArithmeticException overflow) { throw conflict("CONTINUATION_SEQUENCE_INVALID"); }
        if (next != expected) throw conflict("CONTINUATION_HIGH_WATERMARK_CHANGED");
        return high;
    }

    private static BusinessException conflict(String code) {
        return new BusinessException(409, code, "TEMP continuation input high watermark cannot be verified");
    }
    record Watermark(Long value, long captureHigh, long prefix) {
        Watermark(Long value, long captureHigh) { this(value,captureHigh,0); }
    }
}
