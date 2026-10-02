package com.acrylic.outbound.temporary.command;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Reads the trusted stop verification and exact rejected-source reservations under one transaction. */
@Repository
public class PhysicalDispositionVerification {
    private final JdbcTemplate jdbc;
    public PhysicalDispositionVerification(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public Verified read(UUID warehouse, UUID scope, UUID submission, long round, UUID set, UUID verification,
        String setHash, long verificationVersion, long algorithmVersion) {
        var rows = jdbc.queryForList("""
            SELECT s.status AS submission_status,s.issues_json,result,set_sha256,verification_version,algorithm_version
            FROM source_evidence_verification v JOIN submission s ON s.id=v.submission_id
            WHERE v.id=? AND v.set_id=? AND v.scope_id=? AND v.submission_id=? AND v.work_round=? FOR UPDATE
            """, Ids.bytes(verification), Ids.bytes(set), Ids.bytes(scope), Ids.bytes(submission), round);
        if (rows.size() != 1) throw conflict("DISPOSITION_VERIFICATION_MISSING");
        var row = rows.getFirst();
        requireRejected(row);
        if (!"VERIFIED_COMPLETE_AT_STOP".equals(row.get("result"))
            || !Objects.equals(setHash, row.get("set_sha256")) || number(row.get("verification_version")) != verificationVersion
            || number(row.get("algorithm_version")) != algorithmVersion)
            throw conflict("DISPOSITION_VERIFICATION_NOT_COMPLETE");
        var bales = jdbc.query("""
            SELECT k.bale_id
            FROM bale_commitment k JOIN bale b ON b.id=k.bale_id
            WHERE k.state='SOURCE_RESERVED' AND k.source_scope_id=? AND k.source_evidence_set_id=?
              AND k.source_warehouse_id=? AND b.warehouse_id=? AND b.dispatch_cycle=?
            ORDER BY k.bale_id FOR UPDATE
            """, (rs, n) -> uuid(rs.getBytes(1)), Ids.bytes(scope), Ids.bytes(set), Ids.bytes(warehouse),
            Ids.bytes(warehouse), round);
        if (bales.isEmpty()) throw conflict("DISPOSITION_RESERVATIONS_MISSING");
        return new Verified(warehouse, scope, submission, round, set, verification, setHash,
            verificationVersion, algorithmVersion, List.copyOf(bales));
    }

    private static void requireRejected(Map<String, Object> row) {
        if (!"REJECTED".equals(row.get("submission_status")) || !hasSupervisorRejection(row.get("issues_json")))
            throw conflict("DISPOSITION_REJECTION_REQUIRED");
    }

    private static boolean hasSupervisorRejection(Object value) {
        if (!(value instanceof CharSequence text)) return false;
        try {
            var issues = SubmissionJson.parse(text.toString()).path("issues");
            if (!issues.isArray()) return false;
            for (var issue : issues) {
                if (issue.isObject() && "SUPERVISOR_REJECTED".equals(issue.path("code").asText())) return true;
            }
        } catch (RuntimeException invalidJson) {
            return false;
        }
        return false;
    }

    private static long number(Object value) { return ((Number) value).longValue(); }
    private static UUID uuid(byte[] value) {
        if (value == null || value.length != 16) throw conflict("DISPOSITION_RESERVATION_CORRUPT");
        var b = java.nio.ByteBuffer.wrap(value); return new UUID(b.getLong(), b.getLong());
    }
    private static BusinessException conflict(String code) { return new BusinessException(409, code, "disposition verification facts cannot be verified"); }
    public record Verified(UUID warehouse, UUID scope, UUID submission, long round, UUID set, UUID verification,
        String setHash, long verificationVersion, long algorithmVersion, List<UUID> activeBales) { }
}
