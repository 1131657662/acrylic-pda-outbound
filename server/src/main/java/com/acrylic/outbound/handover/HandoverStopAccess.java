package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.pda.PdaSessions;
import com.acrylic.outbound.recall.RecallAccess;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Reuses original-person/device recovery authority; never grants supervisor impersonation or scan authority. */
@Component
final class HandoverStopAccess {
    private final RecallAccess access;
    private final HandoverStopStore stops;
    private final HandoverStore cases;
    private final HandoverCommand.Locks locks;
    private final HandoverSourceGuard sources;

    HandoverStopAccess(RecallAccess access, HandoverStopStore stops, HandoverStore cases,
            HandoverCommand.Locks locks, HandoverSourceGuard sources) {
        this.access = access; this.stops = stops; this.cases = cases; this.locks = locks; this.sources = sources;
    }

    Context read(UUID caseId) {
        var origin = stops.origin(caseId);
        var actor = access.pda(origin.assignment(), origin.epoch(), false);
        requireIdentity(origin, actor);
        return new Context(origin, actor, cases.caseFacts(caseId));
    }

    Context lock(HandoverStopCommand command) {
        var hint = stops.origin(command.caseId());
        var actor = access.pda(hint.assignment(), command.epoch(), true);
        requireIdentity(hint, actor);
        locks.scope(hint.scope()); locks.kase(command.caseId());
        var origin = stops.origin(command.caseId());
        if (!origin.equals(hint)) throw HandoverCommand.stale();
        return new Context(origin, actor, cases.caseFacts(command.caseId()));
    }

    void requireCurrent(HandoverStopCommand command, Context context) {
        var facts = context.facts();
        if (facts.path("caseVersion").asLong() != command.caseVersion()) throw HandoverCommand.stale();
        if (!Set.of("REQUESTED", "EVIDENCE_PENDING", "REVIEW_READY").contains(facts.path("status").asText()))
            throw new BusinessException(409, "HANDOVER_STATE_INVALID", "此案件不能再登记停止证据");
        if (!command.epoch().equals(context.origin().epoch())) throw HandoverCommand.stale();
        if (sources.lock(facts).path("status").asText().equals("SUCCEEDED"))
            throw new BusinessException(409, "HANDOVER_ALREADY_SUCCEEDED", "原批次已成功，请走更正入口");
    }

    private void requireIdentity(HandoverStopStore.Origin origin, PdaSessions.Principal actor) {
        if (!origin.warehouse().equals(actor.warehouseId()) || !origin.device().equals(actor.deviceId())
                || !origin.operator().equals(actor.userId())) throw HandoverCommand.missing();
    }

    record Context(HandoverStopStore.Origin origin, PdaSessions.Principal actor, JsonNode facts) { }
}
