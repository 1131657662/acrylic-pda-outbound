package com.acrylic.outbound.handover;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Only an independent Web supervisor may upload evidence for an existing pending fault case. */
@Component
final class HandoverAttachmentAccess {
    private final HandoverCommand.Identity identity;
    private final HandoverStore cases;
    private final HandoverStopStore origins;
    HandoverAttachmentAccess(HandoverCommand.Identity identity, HandoverStore cases, HandoverStopStore origins) {
        this.identity = identity; this.cases = cases; this.origins = origins;
    }

    HandoverCommand.Actor current(UUID warehouse) {
        var actor = identity.current(warehouse);
        if (actor.pda() != null || !actor.permissions().contains("order:handover")) throw HandoverCommand.denied();
        return actor;
    }

    JsonNode caseFacts(UUID kase, UUID warehouse) {
        var facts = cases.caseFacts(kase);
        if (!warehouse.toString().equals(text(facts, "warehouseId"))) throw HandoverCommand.missing();
        return facts;
    }

    void upload(UUID kase, HandoverCommand.Actor actor) {
        var facts = caseFacts(kase, actor.warehouse());
        if (!"TEMP".equals(text(facts, "sourceKind")) || !"REQUESTED".equals(text(facts, "stopState"))
                || !"EVIDENCE_PENDING".equals(text(facts, "status"))) throw HandoverCommand.stale();
        if (actor.id().toString().equals(text(facts, "requestedBy")) || actor.id().equals(origins.origin(kase).operator()))
            throw HandoverCommand.denied();
    }
}
