package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.command.ContinuationDatabaseChecks;
import java.sql.SQLException;
import java.util.Collections;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

/** Injects exact bad child bindings and a final-write fault in the one-shot isolated schema. */
final class ContinuationConstraintChecks {
    private ContinuationConstraintChecks() { }

    static void rejectCorruptBinding(RetentionDatabaseFixture f, String mode) {
        String mutate = switch (mode) {
            case "scope" -> "SET NEW.scope_id=UUID_TO_BIN('" + f.id("abortScopeId") + "');"
                + "SET NEW.disposition_id=UUID_TO_BIN('" + f.legacyDisposition + "');"
                + "SET NEW.bale_id=(SELECT bale_id FROM temporary_physical_disposition_item WHERE disposition_id=NEW.disposition_id);";
            case "cycle" -> "SET NEW.expected_cycle=NEW.expected_cycle+1;";
            case "history" -> "SET NEW.disposition_id=(SELECT disposition_id FROM temporary_physical_disposition_history "
                + "WHERE disposition_id=UUID_TO_BIN('" + f.legacyDisposition + "'));";
            default -> throw new IllegalArgumentException(mode);
        };
        f.jdbc.execute("CREATE TRIGGER continuation_test_binding BEFORE INSERT ON temporary_source_continuation_bale "
            + "FOR EACH ROW BEGIN " + mutate + " END");
        try {
            String constraint = mode.equals("scope") ? "fk_temp_continuation_bale_context" : "fk_temp_continuation_bale_history";
            assertThatThrownBy(() -> transaction(f, mode)).hasRootCauseInstanceOf(SQLException.class)
                .hasStackTraceContaining(constraint);
            empty(f);
        } finally { f.jdbc.execute("DROP TRIGGER continuation_test_binding"); }
    }

    static void finalAuditFailureRollsBackTheTicketAndReceipt(RetentionDatabaseFixture f) {
        f.jdbc.execute("CREATE TRIGGER continuation_test_audit AFTER INSERT ON operation_event FOR EACH ROW "
            + "BEGIN IF NEW.event_type='TEMPORARY_REOPEN_WORK' THEN SIGNAL SQLSTATE '45000' "
            + "SET MESSAGE_TEXT='continuation final audit failure'; END IF; END");
        try {
            assertThatThrownBy(() -> transaction(f, "audit")).hasRootCauseInstanceOf(SQLException.class)
                .hasStackTraceContaining("continuation final audit failure");
            empty(f);
            assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM temporary_command_receipt WHERE command_type='REOPEN_WORK'", Integer.class)).isZero();
            assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM temporary_decision_event WHERE decision_kind='REOPEN_WORK'", Integer.class)).isZero();
        } finally { f.jdbc.execute("DROP TRIGGER continuation_test_audit"); }
    }

    static void immutableRowsRejectRewrite(RetentionDatabaseFixture f) {
        f.tx.executeWithoutResult(status -> {
            f.apply(f.verified(), Collections.nCopies(3, "RETAIN"));
            try {
                var command = new com.acrylic.outbound.temporary.command.ContinuationDatabaseCommand(f.jdbc, f.seed,
                    f.profile.attempt.resolve("private/continuation-immutable"));
                var receipt = command.execute();
                String ticket = SubmissionJson.parse(receipt.unsigned()).path("businessDecision").path("ticketId").asText();
                assertThatThrownBy(() -> f.jdbc.update("UPDATE temporary_source_continuation SET next_order_scan_seq=9 WHERE id=UUID_TO_BIN(?)", ticket))
                    .hasRootCauseInstanceOf(SQLException.class).hasStackTraceContaining("TEMP continuation source facts are immutable");
                assertThatThrownBy(() -> f.jdbc.update("DELETE FROM temporary_source_continuation WHERE id=UUID_TO_BIN(?)", ticket))
                    .hasRootCauseInstanceOf(SQLException.class).hasStackTraceContaining("TEMP continuation tickets are retained");
                assertThatThrownBy(() -> f.jdbc.update("UPDATE temporary_source_continuation_bale SET expected_cycle=9 WHERE continuation_id=UUID_TO_BIN(?)", ticket))
                    .hasRootCauseInstanceOf(SQLException.class).hasStackTraceContaining("TEMP continuation retention rows are immutable");
                assertThatThrownBy(() -> f.jdbc.update("DELETE FROM temporary_source_continuation_bale WHERE continuation_id=UUID_TO_BIN(?)", ticket))
                    .hasRootCauseInstanceOf(SQLException.class).hasStackTraceContaining("TEMP continuation retention rows are retained");
                status.setRollbackOnly();
            } catch (Exception failure) { throw new IllegalStateException(failure); }
        });
        empty(f);
    }

    static void lifecycleTransitionsRequireAnIssuedTicketAndReceipt(RetentionDatabaseFixture f) {
        f.tx.executeWithoutResult(status -> {
            f.apply(f.verified(), Collections.nCopies(3, "RETAIN"));
            UUID ticket = seedLifecycleTicket(f);
            UUID authorization = f.id("authorizationId");
            assertThatThrownBy(() -> f.jdbc.update("UPDATE temporary_source_continuation SET status='COMPLETED',receipt_text=? WHERE id=?",
                "{}", Ids.bytes(ticket))).hasRootCauseInstanceOf(SQLException.class)
                .hasStackTraceContaining("TEMP continuation status transition is not allowed");
            UUID activationReceipt = UUID.randomUUID();
            f.jdbc.update("""
                INSERT INTO temporary_source_continuation_activation
                (ticket_id,scope_id,from_work_round,new_authorization_id,new_client_order_id,target_device_id,
                 target_operator_id,command_receipt_id,receipt_text,activated_at)
                VALUES(?,?,?,?,?,?,?,?,?,UTC_TIMESTAMP(3))
                """, Ids.bytes(ticket), Ids.bytes(f.id("mappedScopeId")), 1, Ids.bytes(authorization),
                Ids.bytes(f.id("mappedClientOrderId")), Ids.bytes(f.id("deviceId")), Ids.bytes(f.id("operatorId")),
                Ids.bytes(activationReceipt), "{}");
            assertThat(f.jdbc.update("UPDATE temporary_source_continuation SET status='ACTIVATED',new_authorization_id=?,receipt_text=? WHERE id=?",
                Ids.bytes(authorization), "{}", Ids.bytes(ticket))).isEqualTo(1);
            assertThatThrownBy(() -> f.jdbc.update("UPDATE temporary_source_continuation SET status='COMPLETED',receipt_text=NULL WHERE id=?",
                Ids.bytes(ticket))).hasRootCauseInstanceOf(SQLException.class)
                .hasStackTraceContaining("TEMP continuation closure proof is incomplete");
            UUID closureReceipt = UUID.randomUUID();
            f.jdbc.update("""
                INSERT INTO temporary_source_continuation_closure
                (ticket_id,scope_id,from_work_round,status,new_authorization_id,target_submission_id,
                 closure_receipt_id,receipt_text,closed_at)
                VALUES(?,?,1,'COMPLETED',?,?,?, ?,UTC_TIMESTAMP(3))
                """, Ids.bytes(ticket), Ids.bytes(f.id("mappedScopeId")), Ids.bytes(authorization),
                Ids.bytes(f.id("mappedSubmissionId")), Ids.bytes(closureReceipt), "{}");
            assertThatThrownBy(() -> f.jdbc.update("UPDATE temporary_source_continuation_closure SET receipt_text=? WHERE ticket_id=?",
                "{\"changed\":true}", Ids.bytes(ticket))).hasRootCauseInstanceOf(SQLException.class)
                .hasStackTraceContaining("TEMP continuation closure proofs are immutable");
            assertThatThrownBy(() -> f.jdbc.update("DELETE FROM temporary_source_continuation_closure WHERE ticket_id=?",
                Ids.bytes(ticket))).hasRootCauseInstanceOf(SQLException.class)
                .hasStackTraceContaining("TEMP continuation closure proofs are retained");
            assertThat(f.jdbc.update("UPDATE temporary_source_continuation SET status='COMPLETED',receipt_text=? WHERE id=?",
                "{}", Ids.bytes(ticket))).isEqualTo(1);
            assertThatThrownBy(() -> f.jdbc.update("UPDATE temporary_source_continuation SET status='CANCELLED',receipt_text=? WHERE id=?",
                "{}", Ids.bytes(ticket))).hasRootCauseInstanceOf(SQLException.class)
                .hasStackTraceContaining("TEMP continuation status transition is not allowed");
            status.setRollbackOnly();
        });
        empty(f);
    }

    private static UUID seedLifecycleTicket(RetentionDatabaseFixture f) {
        UUID ticket = UUID.randomUUID();
        f.jdbc.update("""
            INSERT INTO temporary_source_continuation
            (id,warehouse_id,scope_id,previous_submission_id,previous_authorization_id,previous_operator_id,
             freeze_device_id,source_recovery_epoch,from_work_round,to_work_round,evidence_set_id,evidence_verification_id,
             retained_manifest_sha256,retained_manifest_version,next_order_scan_seq,command_receipt_id,command_sha256,
             status,new_authorization_id,command_text,receipt_text,created_at)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'TICKET_ISSUED',NULL,?,NULL,UTC_TIMESTAMP(3))
            """, Ids.bytes(ticket), Ids.bytes(f.id("warehouseId")), Ids.bytes(f.id("mappedScopeId")),
            Ids.bytes(f.id("mappedSubmissionId")), Ids.bytes(f.id("authorizationId")), Ids.bytes(f.id("operatorId")),
            Ids.bytes(f.id("deviceId")), 1, 1, 2, Ids.bytes(f.id("mappedSetId")), Ids.bytes(f.id("mappedVerificationId")),
            "b".repeat(64), 1, 5, Ids.bytes(UUID.randomUUID()), "c".repeat(64), "{}");
        var rows = f.jdbc.queryForList("""
            SELECT h.disposition_id,h.bale_id,h.dispatch_cycle,i.record_sha256
            FROM temporary_physical_disposition_history h
            JOIN temporary_physical_disposition d ON d.id=h.disposition_id
            JOIN source_evidence_item i ON i.set_id=d.evidence_set_id AND i.scope_id=h.scope_id
              AND i.submission_id=h.submission_id AND i.work_round=h.work_round AND i.bale_id=h.bale_id
              AND i.item_kind='SCAN' AND i.record_status='ACTIVE' AND i.original_cycle=h.dispatch_cycle
            WHERE h.scope_id=? AND h.submission_id=? AND h.work_round=1 AND d.evidence_set_id=?
              AND d.evidence_verification_id=? AND d.close_mode=FALSE AND h.action='RETAIN'
            """, Ids.bytes(f.id("mappedScopeId")), Ids.bytes(f.id("mappedSubmissionId")),
            Ids.bytes(f.id("mappedSetId")), Ids.bytes(f.id("mappedVerificationId")));
        assertThat(rows).hasSize(3);
        for (var row : rows) f.jdbc.update("""
            INSERT INTO temporary_source_continuation_bale
            (continuation_id,scope_id,from_work_round,disposition_id,bale_id,expected_cycle,record_sha256)
            VALUES(?,?,?,?,?,?,?)
            """, Ids.bytes(ticket), Ids.bytes(f.id("mappedScopeId")), 1, row.get("disposition_id"),
            row.get("bale_id"), row.get("dispatch_cycle"), row.get("record_sha256"));
        return ticket;
    }

    private static void transaction(RetentionDatabaseFixture f, String mode) {
        var before = f.stock();
        try {
            f.tx.executeWithoutResult(status -> {
                var proof = f.verified();
                f.apply(proof, Collections.nCopies(3, "RETAIN"));
                try { ContinuationDatabaseChecks.execute(f.jdbc, f.seed, f.profile.attempt.resolve("private/continuation-" + mode)); }
                catch (Exception failure) { throw new IllegalStateException(failure); }
            });
        } finally { assertThat(f.stock()).isEqualTo(before); }
    }

    private static void empty(RetentionDatabaseFixture f) {
        assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM temporary_source_continuation", Integer.class)).isZero();
        assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM temporary_source_continuation_bale", Integer.class)).isZero();
        assertThat(f.headers()).isEqualTo(1);
        assertThat(f.jdbc.queryForObject("SELECT decision_version FROM temporary_decision_state WHERE scope_id=UUID_TO_BIN(?)",
            Long.class, f.seed.get("mappedScopeId"))).isZero();
    }
}
