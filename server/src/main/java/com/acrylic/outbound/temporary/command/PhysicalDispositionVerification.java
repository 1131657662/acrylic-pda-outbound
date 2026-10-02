package com.acrylic.outbound.temporary.command;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Reads the trusted stop verification and the exact source reservation set under one transaction. */
@Repository
public class PhysicalDispositionVerification {
    private final JdbcTemplate jdbc;
    public PhysicalDispositionVerification(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(propagation = Propagation.MANDATORY)
    public Verified read(UUID warehouse, UUID scope, UUID submission, long round, UUID set, UUID verification,
        String setHash, long verificationVersion, long algorithmVersion) {
        var rows = jdbc.queryForList("""
            SELECT result,set_sha256,verification_version,algorithm_version
            FROM source_evidence_verification
            WHERE id=? AND set_id=? AND scope_id=? AND submission_id=? AND work_round=? FOR UPDATE
            """, Ids.bytes(verification), Ids.bytes(set), Ids.bytes(scope), Ids.bytes(submission), round);
        if (rows.size() != 1) throw conflict("DISPOSITION_VERIFICATION_MISSING");
        var row = rows.getFirst();
        if (!"VERIFIED_COMPLETE_AT_STOP".equals(row.get("result"))
            || !setHash.equals(row.get("set_sha256"))
            || number(row.get("verification_version")) != verificationVersion
            || number(row.get("algorithm_version")) != algorithmVersion)
            throw conflict("DISPOSITION_VERIFICATION_NOT_COMPLETE");
        var bales = jdbc.query("""
            SELECT k.bale_id
            FROM bale_commitment k JOIN bale b ON b.id=k.bale_id
            WHERE k.state='SOURCE_RESERVED' AND k.source_scope_id=? AND k.source_evidence_set_id=?
              AND k.source_warehouse_id=? AND b.warehouse_id=? AND b.dispatch_cycle=?
            ORDER BY k.bale_id
            FOR UPDATE
            """, (rs, n) -> uuid(rs.getBytes(1)), Ids.bytes(scope), Ids.bytes(set), Ids.bytes(warehouse),
            Ids.bytes(warehouse), round);
        if (bales.isEmpty()) throw conflict("DISPOSITION_RESERVATIONS_MISSING");
        return new Verified(warehouse, scope, submission, round, set, verification, setHash,
            verificationVersion, algorithmVersion, List.copyOf(bales));
    }

    private static long number(Object value) { return ((Number) value).longValue(); }
    private static UUID uuid(byte[] value) {
        if (value == null || value.length != 16) throw conflict("DISPOSITION_RESERVATION_CORRUPT");
        var b = java.nio.ByteBuffer.wrap(value); return new UUID(b.getLong(), b.getLong());
    }
    private static BusinessException conflict(String code) { return new BusinessException(409, code, "处置验证事实不可核验"); }
    public record Verified(UUID warehouse, UUID scope, UUID submission, long round, UUID set, UUID verification,
        String setHash, long verificationVersion, long algorithmVersion, List<UUID> activeBales) { }
}
