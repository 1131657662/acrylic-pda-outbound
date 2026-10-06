package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.command.ContinuationDatabaseCommand;
import com.acrylic.outbound.temporary.command.TemporaryContinuationLifecycleStore;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Real MySQL proof for completion binding, owner settlement and replay. */
@EnabledIfEnvironmentVariable(named = "B4_RETENTION_VERIFY", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TemporaryContinuationCompletionMigrationIT {
    private RetentionDatabaseFixture fixture;

    @BeforeAll void provision() throws Exception {
        fixture = new RetentionDatabaseFixture();
        fixture.profile.migrate(fixture.jdbc, "65");
    }

    @Test void completionRequiresTheNewRoundAndSettledOwnersAndReplays() throws Exception {
        fixture.tx.executeWithoutResult(status -> {
            fixture.apply(fixture.verified(), java.util.Collections.nCopies(3, "RETAIN"));
            try {
                var command = new ContinuationDatabaseCommand(fixture.jdbc, fixture.seed,
                    fixture.profile.attempt.resolve("private/completion"));
                var issued = command.execute();
                UUID ticket = UUID.fromString(SubmissionJson.parse(issued.unsigned()).path("businessDecision").path("ticketId").asText());
                var activated = command.activate(ticket, fixture.profile.attempt.resolve("private/completion-activation"));
                var decision = SubmissionJson.parse(activated.unsigned()).path("businessDecision");
                UUID authorization = UUID.fromString(decision.path("newAuthorizationId").asText());
                UUID target = UUID.randomUUID();
                String proof = SubmissionJson.canonical(SubmissionJson.object().put("status", "SUCCEEDED"));
                createTarget(target, authorization, proof);

                assertThatThrownBy(() -> insertClosure(ticket, authorization, target, proof))
                    .hasRootCauseMessage("TEMP continuation completion target is not settled");
                fixture.jdbc.update("UPDATE submission SET status='SUCCEEDED',receipt_text=? WHERE id=?", proof, Ids.bytes(target));
                assertThatThrownBy(() -> insertClosure(ticket, authorization, target, proof))
                    .hasRootCauseMessage("TEMP continuation completion owners are not settled");

                settleOwners(ticket, target);
                var store = new TemporaryContinuationLifecycleStore(fixture.jdbc);
                var completion = new TemporaryContinuationLifecycleStore.Completion(ticket,
                    fixture.id("mappedScopeId"), 1, authorization, target, UUID.randomUUID(), proof, Instant.now());
                store.complete(completion);
                store.complete(completion);
                assertThat(fixture.jdbc.queryForObject("SELECT status FROM temporary_source_continuation WHERE id=?",
                    String.class, Ids.bytes(ticket))).isEqualTo("COMPLETED");
                assertThat(fixture.jdbc.queryForObject("SELECT COUNT(*) FROM temporary_source_continuation_closure WHERE ticket_id=?",
                    Integer.class, Ids.bytes(ticket))).isEqualTo(1);
                assertThat(fixture.jdbc.queryForObject("SELECT COUNT(*) FROM bale_commitment WHERE temporary_continuation_id=?",
                    Integer.class, Ids.bytes(ticket))).isZero();
                assertThatThrownBy(() -> store.cancel(ticket))
                    .hasMessage("TEMP continuation lifecycle cannot be committed");
            } catch (AssertionError failure) { throw failure; }
            catch (Exception failure) { throw new IllegalStateException(failure); }
        });
    }

    private void createTarget(UUID target, UUID authorization, String receipt) {
        fixture.jdbc.update("""
            INSERT INTO submission(id,scope_id,assignment_id,temporary_authorization_id,freeze_device_id,operator_id,
              work_round,source_recovery_epoch,status,receipt_text,created_at,updated_at)
            VALUES(?, ?, NULL, ?, ?, ?, 2, 1, 'REVIEW_REQUIRED', ?, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
            """, Ids.bytes(target), Ids.bytes(fixture.id("mappedScopeId")), Ids.bytes(authorization),
            Ids.bytes(fixture.id("deviceId")), Ids.bytes(fixture.id("operatorId")), receipt);
        fixture.jdbc.update("UPDATE submission_scope SET work_round=2,current_submission_id=? WHERE id=?",
            Ids.bytes(target), Ids.bytes(fixture.id("mappedScopeId")));
    }

    private void settleOwners(UUID ticket, UUID target) {
        fixture.jdbc.update("""
            UPDATE bale_commitment k JOIN temporary_source_continuation_bale r ON r.bale_id=k.bale_id
            SET k.state='DISPATCHED',k.submission_id=?,k.order_id=(SELECT canonical_order_id FROM submission_scope WHERE id=?),
                k.temporary_continuation_id=NULL,k.source_evidence_set_id=NULL
            WHERE r.continuation_id=? AND k.state='TEMP_CONTINUATION_RESERVED'
            """, Ids.bytes(target), Ids.bytes(fixture.id("mappedScopeId")), Ids.bytes(ticket));
    }

    private void insertClosure(UUID ticket, UUID authorization, UUID target, String receipt) {
        fixture.jdbc.update("""
            INSERT INTO temporary_source_continuation_closure
              (ticket_id,scope_id,from_work_round,status,new_authorization_id,target_submission_id,
               closure_receipt_id,receipt_text,closed_at)
            VALUES(?,?,1,'COMPLETED',?,?,?, ?,UTC_TIMESTAMP(3))
            """, Ids.bytes(ticket), Ids.bytes(fixture.id("mappedScopeId")), Ids.bytes(authorization),
            Ids.bytes(target), Ids.bytes(UUID.randomUUID()), receipt);
    }
}
