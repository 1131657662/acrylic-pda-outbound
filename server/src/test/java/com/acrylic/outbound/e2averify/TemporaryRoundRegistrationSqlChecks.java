package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.temporary.continuation.TemporaryRoundControl;
import java.util.UUID;
import static com.acrylic.outbound.submissions.SubmissionJson.*;
import static org.assertj.core.api.Assertions.*;

/** Direct SQL counterexamples must be rejected by the new trigger, not unrelated foreign keys. */
final class TemporaryRoundRegistrationSqlChecks {
    private TemporaryRoundRegistrationSqlChecks() { }
    static void verify(TemporaryRoundRegistrationFixture f) {
        for(String change:java.util.List.of("authorization","operator","round","digest","watermark","verification")) {
            f.tx.executeWithoutResult(status->{
                status.setRollbackOnly();
                var c=TemporaryRoundControl.read(f.request);var db=f.http.database;
                if(change.equals("watermark"))f.jdbc.update("UPDATE source_evidence_set SET order_scan_high_watermark=NULL WHERE id=?",Ids.bytes(db.id("mappedSetId")));
                if(change.equals("verification"))f.jdbc.update("UPDATE source_evidence_verification SET result='CONFLICT' WHERE id=?",Ids.bytes(db.id("mappedVerificationId")));
                UUID target=UUID.randomUUID();
                f.jdbc.update("""
                    INSERT INTO submission(id,scope_id,freeze_device_id,operator_id,work_round,source_recovery_epoch,
                      status,temporary_authorization_id,created_at,updated_at)
                    VALUES(?,?,?,?,?,1,'REGISTERED',?,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))
                    """,Ids.bytes(target),Ids.bytes(db.id("mappedScopeId")),Ids.bytes(db.id("deviceId")),
                    Ids.bytes(db.id(change.equals("operator")?"mismatchUserId":"operatorId")),change.equals("round")?3:2,
                    Ids.bytes(change.equals("authorization")?db.id("authorizationId"):c.control().temporary().authorization()));
                assertThatThrownBy(()->f.jdbc.update("""
                    INSERT INTO temporary_source_continuation_submission
                      (ticket_id,scope_id,from_work_round,to_work_round,submission_id,predecessor_payload_sha256,registered_at)
                    VALUES(?,?,1,2,?,?,UTC_TIMESTAMP(3))
                    """,Ids.bytes(c.predecessor().ticket()),Ids.bytes(db.id("mappedScopeId")),Ids.bytes(target),
                    change.equals("digest")?"a".repeat(64):f.predecessorHash))
                    .as(change).hasRootCauseMessage("TEMP continuation submission binding is invalid");
            });
        }
        assertThat(f.count("temporary_source_continuation_submission")).isZero();
    }
}
