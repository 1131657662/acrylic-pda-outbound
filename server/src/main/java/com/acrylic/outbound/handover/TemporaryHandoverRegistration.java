package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.pda.PdaSessions;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

@Component
@Transactional(propagation=Propagation.MANDATORY)
class TemporaryHandoverRegistration {
    private final JdbcTemplate jdbc;private final TemporaryHandoverIntakeFacts facts;
    TemporaryHandoverRegistration(JdbcTemplate jdbc,TemporaryHandoverIntakeFacts facts) { this.jdbc=jdbc;this.facts=facts; }
    TemporaryHandoverIntakeFacts.Facts register(TemporaryHandoverControl request,PdaSessions.Principal actor) {
        lock(request.predecessor());var value=facts.read(request);var t=value.ticket();facts.actor(value,actor);
        var existing=jdbc.queryForList("SELECT BIN_TO_UUID(submission_id) FROM temporary_handover_submission WHERE ticket_id=?",
            String.class,Ids.bytes(t.id()));
        if(!existing.isEmpty()) {
            if(existing.size()!=1 || !request.control().id().toString().equals(existing.getFirst()))throw TemporaryHandoverIntakeFacts.changed();
            return value;
        }
        requireOpen(t);insert(request,value);return value;
    }
    private void lock(TemporaryHandoverControl.Reference reference) {
        var rows=jdbc.queryForList("SELECT scope_id,canonical_order_id,submission_id FROM temporary_handover_activation_ticket WHERE id=? AND case_id=?",
            Ids.bytes(reference.ticket()),Ids.bytes(reference.caseId()));
        if(rows.size()!=1)throw TemporaryHandoverIntakeFacts.changed();var row=rows.getFirst();
        jdbc.queryForList("SELECT id FROM submission_scope WHERE id=? FOR UPDATE",row.get("scope_id"));
        jdbc.queryForList("SELECT id FROM outbound_order WHERE id=? FOR UPDATE",row.get("canonical_order_id"));
        jdbc.queryForList("SELECT order_id FROM order_submission_slot WHERE order_id=? FOR UPDATE",row.get("canonical_order_id"));
        jdbc.queryForList("SELECT id FROM submission WHERE id=? FOR UPDATE",row.get("submission_id"));
    }
    void requireOpen(HandoverTemporaryActivationTicketStore.Ticket t) {
        int valid=jdbc.queryForObject("""
            SELECT COUNT(*) FROM submission_scope c JOIN order_submission_slot l ON l.scope_id=c.id AND l.order_id=c.canonical_order_id
            JOIN outbound_order o ON o.id=l.order_id JOIN submission p ON p.id=? AND p.scope_id=c.id
            WHERE c.id=? AND c.source_order_id IS NULL AND c.work_round=? AND c.current_submission_id IS NULL
              AND l.work_round=c.work_round AND l.current_submission_id IS NULL AND l.phase='CONTINUING'
              AND o.status='CONTINUING' AND o.current_result_version IS NULL AND o.id=?
              AND p.work_round=? AND p.status='ABORTED' AND p.temporary_authorization_id=?
            """,Integer.class,Ids.bytes(t.submissionId()),Ids.bytes(t.scopeId()),t.toRound(),Ids.bytes(t.canonicalOrderId()),
            t.fromRound(),Ids.bytes(t.sourceAuthorizationId()));
        if(valid!=1)throw TemporaryHandoverIntakeFacts.changed();
    }
    private void insert(TemporaryHandoverControl request,TemporaryHandoverIntakeFacts.Facts value) {
        var t=value.ticket();var c=request.control();
        try {
            jdbc.update("""
                INSERT INTO submission(id,scope_id,freeze_device_id,operator_id,work_round,source_recovery_epoch,
                  status,temporary_authorization_id,created_at,updated_at)
                VALUES(?,?,?,?,?,?,'REGISTERED',?,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))
                """,Ids.bytes(c.id()),Ids.bytes(t.scopeId()),Ids.bytes(c.device()),Ids.bytes(t.targetUserId()),c.round(),c.epoch(),
                Ids.bytes(c.temporary().authorization()));
            jdbc.update("""
                INSERT INTO temporary_handover_submission(ticket_id,scope_id,from_work_round,to_work_round,submission_id,predecessor_payload_sha256,registered_at)
                VALUES(?,?,?,?,?,?,UTC_TIMESTAMP(3))
                """,Ids.bytes(t.id()),Ids.bytes(t.scopeId()),t.fromRound(),t.toRound(),Ids.bytes(c.id()),t.payloadSha());
        } catch(org.springframework.dao.DuplicateKeyException collision) { throw TemporaryHandoverIntakeFacts.changed(); }
    }
}
