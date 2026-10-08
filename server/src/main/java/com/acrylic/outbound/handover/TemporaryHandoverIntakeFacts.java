package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.pda.PdaSessions;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Locked, immutable activation bindings, separate from FORMAL assignments and R06 tickets. */
@Component
final class TemporaryHandoverIntakeFacts {
    private final JdbcTemplate jdbc;private final HandoverTemporaryActivationTicketStore tickets;private final HandoverStore cases;
    private final TemporaryHandoverActivationBinding activations;
    TemporaryHandoverIntakeFacts(JdbcTemplate jdbc,HandoverTemporaryActivationTicketStore tickets,HandoverStore cases,
        TemporaryHandoverActivationBinding activations) {
        this.jdbc=jdbc;this.tickets=tickets;this.cases=cases;this.activations=activations;
    }
    Facts read(TemporaryHandoverControl request) {
        var reference=request.predecessor();var ticket=tickets.lock(reference.caseId());
        var facts=cases.caseFacts(reference.caseId());HandoverTemporaryActivation.verifyTicket(ticket,facts);
        if(!"ACTIVATED".equals(text(facts,"status")) || !ticket.id().equals(reference.ticket())
            || !ticket.submissionId().equals(reference.submission()) || !ticket.payloadSha().equals(reference.payloadSha256())
            || !ticket.sourcePrefixSha().equals(reference.prefixSha256()))throw changed();
        var activation=activations.read(ticket,facts);var c=request.control();
        if(!ticket.targetDeviceId().equals(c.device()) || !uuid(activation,"newAuthorizationId").equals(c.temporary().authorization())
            || !uuid(activation,"newClientOrderId").equals(c.temporary().client()) || ticket.toRound()!=c.round()
            || !Long.toString(ticket.sourceRecoveryEpoch()).equals(c.epoch())
            || number(activation,"startOrderScanSeq")!=Math.addExact(ticket.inputHighWatermark(),1)
            || !ticket.sourcePrefixSha().equals(text(activation,"sourcePrefixSha256")))throw changed();
        return new Facts(ticket,activation,facts,uuid(facts,"warehouseId"));
    }
    void actor(Facts facts,PdaSessions.Principal actor) {
        var t=facts.ticket();
        if(!t.targetUserId().equals(actor.userId()) || !t.targetDeviceId().equals(actor.deviceId())
            || !facts.warehouse().equals(actor.warehouseId())
            || !uuid(facts.activation(),"targetSessionId").equals(actor.sessionId())
            || !Long.toString(t.targetUserAuthVersion()).equals(actor.userAuthVersion())
            || !Long.toString(t.targetDeviceVersion()).equals(actor.deviceVersion()))throw changed();
    }
    TemporaryHandoverControl.Reference reference(UUID submission) {
        var rows=jdbc.query("""
            SELECT BIN_TO_UUID(t.id),BIN_TO_UUID(t.case_id),BIN_TO_UUID(t.submission_id),r.predecessor_payload_sha256,t.source_prefix_sha256
            FROM temporary_handover_submission r JOIN temporary_handover_activation_ticket t ON t.id=r.ticket_id
            WHERE r.submission_id=?
            """,(rs,n)->new TemporaryHandoverControl.Reference(UUID.fromString(rs.getString(1)),UUID.fromString(rs.getString(2)),
                UUID.fromString(rs.getString(3)),rs.getString(4),rs.getString(5)),Ids.bytes(submission));
        if(rows.size()!=1)throw changed();return rows.getFirst();
    }
    static BusinessException changed() { return new BusinessException(409,"TEMP_HANDOVER_BINDING_CHANGED","临时交接激活或来源已变化，请保留原件"); }
    record Facts(HandoverTemporaryActivationTicketStore.Ticket ticket,JsonNode activation,JsonNode caseFacts,UUID warehouse) { }
}
