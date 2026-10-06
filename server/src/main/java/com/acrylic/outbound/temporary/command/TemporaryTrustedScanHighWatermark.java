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
                SELECT order_scan_high_watermark,capture_high_watermark
                FROM source_evidence_set
                WHERE id=? AND scope_id=? AND submission_id=? AND work_round=?
                FOR UPDATE
                """, (rs, number) -> rs.getObject(1) == null
                    ? new Watermark(null, rs.getLong(2))
                    : new Watermark(rs.getLong(1), rs.getLong(2)),
                Ids.bytes(set), Ids.bytes(scope), Ids.bytes(submission), round);
        } catch (DataAccessException failure) {
            throw conflict("CONTINUATION_HIGH_WATERMARK_UNTRUSTED");
        }
        if (rows.size() != 1 || rows.getFirst().value() == null) throw conflict("CONTINUATION_HIGH_WATERMARK_UNTRUSTED");
        long high = rows.getFirst().value();
        if (high < 0 || rows.getFirst().captureHigh() < 0 || high > rows.getFirst().captureHigh())
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
    record Watermark(Long value, long captureHigh) { }
}
