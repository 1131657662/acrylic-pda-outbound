package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Timestamp;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Consumes a TEMP handover ticket and transfers only its retained owners. */
@Component
final class HandoverTemporaryActivation {
    private final JdbcTemplate jdbc;
    private final HandoverTemporaryActivationTicketStore tickets;
    private final HandoverTemporaryConfirmationStore confirmations;
    private final HandoverTemporaryActivationGuard guard;
    private final HandoverTemporaryActivationIssuer issuer;
    private final HandoverTemporaryOwnerTransfer owners;

    HandoverTemporaryActivation(JdbcTemplate jdbc, HandoverTemporaryActivationTicketStore tickets,
            HandoverTemporaryConfirmationStore confirmations, HandoverTemporaryActivationGuard guard,
            HandoverTemporaryActivationIssuer issuer, HandoverTemporaryOwnerTransfer owners) {
        this.jdbc = jdbc; this.tickets = tickets; this.confirmations = confirmations; this.guard = guard;
        this.issuer = issuer; this.owners = owners;
    }

    JsonNode activate(HandoverCommand.Write write) {
        var facts = write.caseFacts();
        HandoverTemporaryActivationTicketStore.Ticket ticket = tickets.lock(write.caseId());
        verifyTicket(ticket, facts);
        var confirmation = confirmations.get(write.caseId());
        UUID session = guard.require(write, ticket, confirmation).sessionId();
        var issued = issuer.issue(ticket, session);
        UUID proof = Ids.next();
        long retained = tickets.owners(ticket.id()).size();
        var decision = decision(new Activated(ticket, issued, proof, session, retained));
        save(new Saved(ticket, issued, proof, session, retained, confirmation, decision, write));
        owners.transfer(ticket, facts);
        return decision;
    }

    private JsonNode decision(Activated activated) {
        var ticket = activated.ticket(); var issued = activated.issued();
        long start = next(ticket.inputHighWatermark());
        var decision = SubmissionJson.object().put("kind", "TEMP_HANDOVER_ACTIVATE").put("status", "ACTIVATED")
            .put("caseId", ticket.caseId().toString()).put("ticketId", ticket.id().toString()).put("proofId", activated.proof().toString())
            .put("fromWorkRound", Long.toString(ticket.fromRound())).put("toWorkRound", Long.toString(ticket.toRound()))
            .put("newAuthorizationId", issued.authorization().toString()).put("newClientOrderId", issued.clientOrder().toString())
            .put("targetDeviceId", ticket.targetDeviceId().toString()).put("targetUserId", ticket.targetUserId().toString())
            .put("targetSessionId", activated.session().toString()).put("startOrderScanSeq", Long.toString(start))
            .put("retainedCount", Long.toString(activated.retained())).put("sourcePrefixSha256", ticket.sourcePrefixSha());
        decision.set("authorization", authorization(issued));
        return decision;
    }

    private void save(Saved saved) {
        var ticket = saved.ticket(); var issued = saved.issued();
        jdbc.update("""
            INSERT INTO temporary_handover_activation(ticket_id,proof_id,confirmation_id,new_authorization_id,new_client_order_id,
              target_device_id,target_user_id,target_session_id,from_work_round,to_work_round,start_order_scan_seq,retained_count,receipt_text,activated_at)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """, Ids.bytes(ticket.id()), Ids.bytes(saved.proof()), Ids.bytes(UUID.fromString((String) saved.confirmation().get("id"))),
            Ids.bytes(issued.authorization()), Ids.bytes(issued.clientOrder()), Ids.bytes(ticket.targetDeviceId()), Ids.bytes(ticket.targetUserId()),
            Ids.bytes(saved.session()), ticket.fromRound(), ticket.toRound(), next(ticket.inputHighWatermark()), saved.retained(),
            SubmissionJson.canonical(saved.decision()), Timestamp.from(saved.write().recordedAt()));
    }

    static void verifyTicket(HandoverTemporaryActivationTicketStore.Ticket ticket, JsonNode facts) {
        if (!ticket.caseId().equals(SubmissionJson.uuid(facts, "caseId")) || !ticket.scopeId().equals(SubmissionJson.uuid(facts, "scopeId"))
                || !ticket.submissionId().equals(SubmissionJson.uuid(facts, "submissionId"))
                || !ticket.canonicalOrderId().equals(SubmissionJson.uuid(facts, "canonicalOrderId"))
                || !ticket.targetUserId().equals(SubmissionJson.uuid(facts, "targetUserId"))
                || !ticket.targetDeviceId().equals(SubmissionJson.uuid(facts, "targetDeviceId"))
                || ticket.inputHighWatermark() != SubmissionJson.number(facts, "inputHighWatermark")
                || ticket.fromRound() != SubmissionJson.number(facts, "fromWorkRound")
                || ticket.toRound() != SubmissionJson.number(facts, "toWorkRound")
                || ticket.fromRound() <= 0 || ticket.toRound() != ticket.fromRound() + 1
                || !ticket.sourcePrefixSha().equals(HandoverTemporaryActivationTicketStore.prefixSha(ticket))
                || !ticket.preparedTicketSha().equals(facts.path("preparedTicketSha256").asText())
                || !ticket.preparedTicketSha().equals(HandoverCommand.sha(ticket.ticketBytes()))) throw conflict("HANDOVER_TEMP_TICKET_CHANGED");
    }

    private JsonNode authorization(HandoverTemporaryActivationIssuer.Issued issued) {
        var r = issued.receipt(); return SubmissionJson.object().put("authorizationId", r.authorizationId()).put("bundleId", r.bundleId())
            .put("offlineToken", r.offlineToken()).put("manifest", r.manifest()).put("manifestSha256", r.manifestSha256()).put("signedManifest", r.signedManifest());
    }
    private long next(long high) { if (high < 0 || high == Long.MAX_VALUE) throw conflict("HANDOVER_TEMP_HIGH_WATERMARK_INVALID"); return high + 1; }
    private static BusinessException conflict(String code) { return new BusinessException(409, code, "临时交接激活资料已变化，请保留原件"); }
    private record Activated(HandoverTemporaryActivationTicketStore.Ticket ticket, HandoverTemporaryActivationIssuer.Issued issued,
        UUID proof, UUID session, long retained) { }
    private record Saved(HandoverTemporaryActivationTicketStore.Ticket ticket, HandoverTemporaryActivationIssuer.Issued issued,
        UUID proof, UUID session, long retained, Map<String, Object> confirmation, JsonNode decision, HandoverCommand.Write write) { }
}
