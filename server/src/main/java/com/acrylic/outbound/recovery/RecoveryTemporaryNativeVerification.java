package com.acrylic.outbound.recovery;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Base64;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Locks an existing native verification. It never inserts or reconstructs one from recovered E2A. */
final class RecoveryTemporaryNativeVerification {
    private final JdbcTemplate jdbc;
    RecoveryTemporaryNativeVerification(JdbcTemplate jdbc) {
        require(jdbc != null, "RECOVERY_NATIVE_DATABASE_REQUIRED"); this.jdbc = jdbc;
    }

    JsonNode verify(RecoveryRecoveredTemporaryArchive archive, RecoveryTemporaryNativeHandoverFacts.Facts facts) {
        require(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()
            && org.springframework.transaction.support.TransactionSynchronizationManager.hasResource(jdbc.getDataSource()),
            "RECOVERY_NATIVE_TRANSACTION_REQUIRED");
        var row = read(facts); var source = archive.source();
        require(uuid(row, "set_id").equals(facts.evidenceSetId()) && uuid(row, "scope_id").equals(source.scopeId())
            && uuid(row, "submission_id").equals(source.submissionId()) && revision(row, "work_round") == source.workRound()
            && revision(row, "verification_version") == 1 && revision(row, "algorithm_version") == 1
            && text(row, "set_sha256").equals(facts.setSha256())
            && text(row, "result").equals("VERIFIED_COMPLETE_AT_STOP"), "RECOVERY_NATIVE_VERIFICATION_MISMATCH");
        // Native findings may exceed the external control protocol's small JSON envelope.
        byte[] canonical=(SubmissionJson.canonical(row)+"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        require(sha256(canonical).equals(facts.verificationSha256()), "RECOVERY_NATIVE_VERIFICATION_CHANGED");
        return row;
    }

    private JsonNode read(RecoveryTemporaryNativeHandoverFacts.Facts facts) {
        var rows = jdbc.query("""
            SELECT BIN_TO_UUID(id) id,BIN_TO_UUID(set_id) set_id,BIN_TO_UUID(scope_id) scope_id,
                BIN_TO_UUID(submission_id) submission_id,CAST(work_round AS CHAR) work_round,
                CAST(verification_version AS CHAR) verification_version,CAST(algorithm_version AS CHAR) algorithm_version,
                set_sha256,result,known_facts_sha256,findings_bytes,
                DATE_FORMAT(verified_at,'%Y-%m-%dT%H:%i:%s.%f') verified_at
            FROM source_evidence_verification WHERE id=? FOR UPDATE
            """, (rs, index) -> {
                var row = SubmissionJson.object();
                for (int column = 1; column <= rs.getMetaData().getColumnCount(); column++) {
                    String name = rs.getMetaData().getColumnLabel(column);
                    if (name.equals("findings_bytes")) row.put(name, Base64.getEncoder().encodeToString(rs.getBytes(column)));
                    else row.put(name, rs.getString(column));
                }
                return row;
            }, Ids.bytes(facts.verificationId()));
        require(rows.size() == 1, "RECOVERY_NATIVE_VERIFICATION_MISSING");
        return rows.getFirst();
    }
}
