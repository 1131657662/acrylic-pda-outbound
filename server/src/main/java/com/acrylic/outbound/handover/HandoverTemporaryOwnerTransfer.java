package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Transfers the frozen retained set in database bale order, without releasing it. */
@Component
final class HandoverTemporaryOwnerTransfer {
    private final JdbcTemplate jdbc;
    private final HandoverTemporaryActivationTicketStore tickets;
    private final HandoverStore cases;

    HandoverTemporaryOwnerTransfer(JdbcTemplate jdbc, HandoverTemporaryActivationTicketStore tickets, HandoverStore cases) {
        this.jdbc = jdbc; this.tickets = tickets; this.cases = cases;
    }

    void transfer(HandoverTemporaryActivationTicketStore.Ticket ticket, JsonNode facts) {
        var retained = new HashMap<UUID, Map<String, Object>>();
        for (var row : cases.checklist(ticket.caseId())) {
            if (!"RETAINED".equals(row.get("state"))) continue;
            if (retained.put(UUID.fromString(row.get("bale_id").toString()), row) != null) throw conflict();
        }
        var owners = tickets.owners(ticket.id());
        if (owners.size() != retained.size()) throw conflict();
        for (var owner : owners) {
            var row = retained.get(owner.baleId());
            if (row == null || owner.dispatchCycle() != ((Number) row.get("dispatch_cycle")).longValue()
                    || !owner.recordSha().equals(SubmissionJson.sha(HandoverTemporaryActivationTicketStore.line(row)))) throw conflict();
            lock(owner.baleId());
            transfer(new Transfer(ticket, owner, SubmissionJson.uuid(facts, "warehouseId")));
        }
    }

    private void transfer(Transfer context) {
        var ticket = context.ticket(); var owner = context.owner();
        int changed = jdbc.update("""
            UPDATE bale_commitment k JOIN bale b ON b.id=k.bale_id AND b.dispatch_cycle=k.dispatch_cycle
            SET k.state='TEMP_HANDOVER_RESERVED',k.temporary_handover_activation_id=?,k.source_evidence_set_id=?,
                k.submission_id=NULL,k.order_id=NULL
            WHERE k.bale_id=? AND k.dispatch_cycle=? AND k.source_scope_id=? AND k.source_warehouse_id=?
              AND k.temporary_continuation_id IS NULL AND k.disposition_id IS NULL AND k.temporary_handover_activation_id IS NULL
              AND ((k.state='SOURCE_RESERVED' AND k.source_evidence_set_id=? AND k.submission_id IS NULL)
                OR (k.state='REVIEW_RESERVED' AND k.submission_id=? AND k.source_evidence_set_id IS NULL AND k.order_id=?))
            """, Ids.bytes(ticket.id()), Ids.bytes(ticket.sourceEvidenceSetId()), Ids.bytes(owner.baleId()), owner.dispatchCycle(),
            Ids.bytes(ticket.scopeId()), Ids.bytes(context.warehouse()), Ids.bytes(ticket.sourceEvidenceSetId()),
            Ids.bytes(ticket.submissionId()), Ids.bytes(ticket.canonicalOrderId()));
        if (changed != 1) throw new BusinessException(409, "HANDOVER_TEMP_OWNER_CHANGED", "临时交接保留占用已变化");
    }

    private void lock(UUID bale) {
        jdbc.queryForList("SELECT id FROM bale WHERE id=? FOR UPDATE", Ids.bytes(bale));
        jdbc.queryForList("SELECT bale_id FROM inventory_position WHERE bale_id=? FOR UPDATE", Ids.bytes(bale));
        jdbc.queryForList("SELECT bale_id FROM bale_commitment WHERE bale_id=? FOR UPDATE", Ids.bytes(bale));
    }

    private static BusinessException conflict() {
        return new BusinessException(409, "HANDOVER_TEMP_OWNER_SET_CHANGED", "临时交接保留清单或占用证明已变化");
    }
    private record Transfer(HandoverTemporaryActivationTicketStore.Ticket ticket,
        HandoverTemporaryActivationTicketStore.Owner owner, UUID warehouse) { }
}
