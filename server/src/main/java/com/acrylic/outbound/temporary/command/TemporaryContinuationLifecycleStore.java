package com.acrylic.outbound.temporary.command;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Closes an activated TEMP continuation only after the new round is durably settled. */
@Component
public class TemporaryContinuationLifecycleStore {
    private final JdbcTemplate jdbc;

    public TemporaryContinuationLifecycleStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(propagation = Propagation.MANDATORY)
    public void complete(Completion proof) {
        requireProof(proof);
        var ticket = ticket(proof);
        if ("COMPLETED".equals(ticket.status())) {
            replayCompleted(proof);
            return;
        }
        if (!"ACTIVATED".equals(ticket.status())) throw conflict("CONTINUATION_NOT_ACTIVATED");
        requireTargetSubmission(proof, ticket.toRound());
        requireSettledOwners(proof);
        insertClosure(proof, ticket);
        if (jdbc.update("""
            UPDATE temporary_source_continuation
            SET status='COMPLETED',receipt_text=?
            WHERE id=? AND status='ACTIVATED' AND new_authorization_id=?
            """, proof.receiptText(), Ids.bytes(proof.ticketId()), Ids.bytes(proof.newAuthorizationId())) != 1)
            throw conflict("CONTINUATION_TICKET_CHANGED");
    }

    /** Cancellation would release or quarantine retained stock; policy is not defined yet. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void cancel(UUID ticketId) {
        throw conflict("CONTINUATION_CANCEL_POLICY_UNDEFINED");
    }

    private Ticket ticket(Completion proof) {
        var rows = jdbc.query("""
            SELECT status,new_authorization_id,to_work_round
            FROM temporary_source_continuation
            WHERE id=? AND scope_id=? AND from_work_round=? FOR UPDATE
            """, (rs, n) -> new Ticket(rs.getString(1), uuid(rs.getBytes(2)), rs.getLong(3)),
            Ids.bytes(proof.ticketId()), Ids.bytes(proof.scopeId()), proof.fromWorkRound());
        if (rows.size() != 1) throw conflict("CONTINUATION_NOT_FOUND");
        var row = rows.getFirst();
        if (!proof.newAuthorizationId().equals(row.authorizationId())) throw conflict("CONTINUATION_AUTHORIZATION_CHANGED");
        return row;
    }

    private void replayCompleted(Completion proof) {
        var rows = jdbc.queryForList("""
            SELECT status,new_authorization_id,target_submission_id,closure_receipt_id,receipt_text
            FROM temporary_source_continuation_closure
            WHERE ticket_id=? AND scope_id=? AND from_work_round=?
            """, Ids.bytes(proof.ticketId()), Ids.bytes(proof.scopeId()), proof.fromWorkRound());
        if (rows.size() != 1 || !"COMPLETED".equals(rows.getFirst().get("status"))
            || !proof.newAuthorizationId().equals(uuid((byte[]) rows.getFirst().get("new_authorization_id")))
            || !proof.targetSubmissionId().equals(uuid((byte[]) rows.getFirst().get("target_submission_id")))
            || !proof.closureReceiptId().equals(uuid((byte[]) rows.getFirst().get("closure_receipt_id")))
            || !proof.receiptText().equals(rows.getFirst().get("receipt_text")))
            throw conflict("CONTINUATION_CLOSURE_CONFLICT");
    }

    private void requireTargetSubmission(Completion proof, long toRound) {
        var rows = jdbc.queryForList("""
            SELECT s.receipt_text,BIN_TO_UUID(c.canonical_order_id) order_id
            FROM submission s JOIN submission_scope c ON c.id=s.scope_id
            WHERE s.id=? AND s.scope_id=? AND s.work_round=? AND s.temporary_authorization_id=?
              AND s.status='SUCCEEDED' AND c.canonical_order_id IS NOT NULL
            """, Ids.bytes(proof.targetSubmissionId()), Ids.bytes(proof.scopeId()), toRound,
            Ids.bytes(proof.newAuthorizationId()));
        if (rows.size() != 1 || !proof.receiptText().equals(rows.getFirst().get("receipt_text")))
            throw conflict("CONTINUATION_TARGET_NOT_SETTLED");
    }

    private void requireSettledOwners(Completion proof) {
        var rows = jdbc.queryForList("""
            SELECT COUNT(*) total,
              COALESCE(SUM(CASE WHEN k.state='DISPATCHED' AND k.submission_id=?
                 AND k.order_id=(SELECT canonical_order_id FROM submission_scope WHERE id=?)
                 AND k.temporary_continuation_id IS NULL AND k.source_evidence_set_id IS NULL THEN 1 ELSE 0 END),0) settled
            FROM temporary_source_continuation_bale r
            LEFT JOIN bale_commitment k ON k.bale_id=r.bale_id AND k.dispatch_cycle=r.expected_cycle
            WHERE r.continuation_id=? AND r.scope_id=? AND r.from_work_round=?
            """, Ids.bytes(proof.targetSubmissionId()), Ids.bytes(proof.scopeId()), Ids.bytes(proof.ticketId()),
            Ids.bytes(proof.scopeId()), proof.fromWorkRound());
        Map<String, Object> row = rows.getFirst();
        long total = number(row.get("total"));
        long settled = number(row.get("settled"));
        if (total == 0 || total != settled) throw conflict("CONTINUATION_OWNER_NOT_SETTLED");
    }

    private void insertClosure(Completion proof, Ticket ticket) {
        try {
            jdbc.update("""
                INSERT INTO temporary_source_continuation_closure
                (ticket_id,scope_id,from_work_round,status,new_authorization_id,target_submission_id,
                 closure_receipt_id,receipt_text,closed_at)
                VALUES(?,?,?,'COMPLETED',?,?,?,?,?)
                """, Ids.bytes(proof.ticketId()), Ids.bytes(proof.scopeId()), proof.fromWorkRound(),
                Ids.bytes(proof.newAuthorizationId()), Ids.bytes(proof.targetSubmissionId()),
                Ids.bytes(proof.closureReceiptId()), proof.receiptText(), java.sql.Timestamp.from(proof.closedAt()));
        } catch (org.springframework.dao.DuplicateKeyException duplicate) {
            throw conflict("CONTINUATION_CLOSURE_CONFLICT");
        }
    }

    private static void requireProof(Completion proof) {
        if (proof == null || proof.ticketId() == null || proof.scopeId() == null || proof.newAuthorizationId() == null
            || proof.targetSubmissionId() == null || proof.closureReceiptId() == null || proof.receiptText() == null
            || proof.receiptText().isBlank() || proof.fromWorkRound() <= 0 || proof.closedAt() == null)
            throw conflict("CONTINUATION_CLOSURE_PROOF_INVALID");
        try {
            if (!proof.receiptText().equals(SubmissionJson.canonical(SubmissionJson.parse(proof.receiptText()))))
                throw conflict("CONTINUATION_CLOSURE_PROOF_INVALID");
        } catch (RuntimeException malformed) {
            throw conflict("CONTINUATION_CLOSURE_PROOF_INVALID");
        }
    }

    private static long number(Object value) {
        if (value instanceof Number number) return number.longValue();
        return Long.parseLong(String.valueOf(value));
    }

    private static UUID uuid(byte[] value) {
        if (value == null || value.length != 16) throw conflict("CONTINUATION_CLOSURE_CORRUPT");
        var buffer = java.nio.ByteBuffer.wrap(value);
        return new UUID(buffer.getLong(), buffer.getLong());
    }

    private static BusinessException conflict(String code) {
        return new BusinessException(409, code, "TEMP continuation lifecycle cannot be committed");
    }

    private record Ticket(String status, UUID authorizationId, long toRound) { }

    public record Completion(UUID ticketId, UUID scopeId, long fromWorkRound, UUID newAuthorizationId,
        UUID targetSubmissionId, UUID closureReceiptId, String receiptText, Instant closedAt) { }
}
