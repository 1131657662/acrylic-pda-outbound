package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Read-only snapshot of command context or verified original response; never executes or signs a command. */
@Service
@ConditionalOnProperty(prefix="outbound.handover.commands", name="enabled", havingValue="true")
@Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
public class HandoverReadService {
    private final HandoverCommand.Identity identity;
    private final HandoverStore facts;
    private final HandoverReadStore store;
    private final HandoverReadReceipt verifier;

    HandoverReadService(HandoverCommand.Identity identity, HandoverStore facts, HandoverReadStore store,
            HandoverReadReceipt verifier) {
        this.identity = identity; this.facts = facts; this.store = store; this.verifier = verifier;
    }

    public JsonNode scope(UUID warehouse, UUID scope) {
        var actor = identity.current(warehouse); var context = facts.scopeFacts(scope);
        requireWarehouse(actor, context);
        var result = SubmissionJson.object(); result.set("context", context);
        result.set("expected", HandoverCommand.Expected.forRequest(context, Long.parseLong(actor.authVersion())));
        result.set("capabilities", HandoverReceiptCodec.capabilities()); return result;
    }

    public JsonNode kase(UUID warehouse, UUID caseId, String kind) {
        if (!Set.of("APPROVE", "PREPARE", "ACTIVATE", "FAULT_FENCE").contains(kind)) throw HandoverCommand.missing();
        var actor = identity.current(warehouse); var context = facts.caseFacts(caseId);
        requireWarehouse(actor, context);
        var original = verifier.verify(store.originalRequest(caseId));
        HandoverCommand.corruptUnless(original.command().path("commandType").asText().equals("REQUEST")
            && original.receipt().path("businessDecision").path("caseId").asText().equals(caseId.toString())
            && original.command().path("scopeId").asText().equals(context.path("scopeId").asText())
            && original.receipt().path("actor").path("principalId").asText().equals(context.path("requestedBy").asText())
            && original.command().path("warehouseId").asText().equals(warehouse.toString()));
        var result = SubmissionJson.object(); result.set("context", context);
        result.set("expected", HandoverCommand.Expected.forCase(context, Long.parseLong(actor.authVersion())));
        result.set("originalCommand", original.command()); result.set("capabilities", HandoverReceiptCodec.capabilities());
        return result;
    }

    public byte[] receipt(ReceiptQuery query) {
        if (!HandoverCommand.KINDS_SET.contains(query.kind())) throw HandoverCommand.missing();
        var actor = identity.current(query.warehouse());
        var row = facts.receipt(query.key().toString());
        if (row == null || !query.kind().equals(row.get("command_type"))
                || !(row.get("actor_id") instanceof byte[] owner) || !Arrays.equals(owner, Ids.bytes(actor.id()))
                || !(row.get("case_id") instanceof byte[] kase) || !Arrays.equals(kase, Ids.bytes(query.caseId())))
            throw HandoverCommand.missing();
        var verified = verifier.verify(row); var recorded = verified.receipt().path("actor");
        if (!query.key().toString().equals(verified.command().path("commandId").asText())
                || !actor.id().toString().equals(recorded.path("principalId").asText())
                || !query.warehouse().toString().equals(recorded.path("warehouseId").asText())) throw HandoverCommand.missing();
        if (recorded.hasNonNull("deviceId") && (actor.pda() == null
                || !actor.pda().deviceId().toString().equals(recorded.path("deviceId").asText()))) throw HandoverCommand.missing();
        return verified.bytes();
    }

    private void requireWarehouse(HandoverCommand.Actor actor, JsonNode context) {
        if (!actor.warehouse().toString().equals(context.path("warehouseId").asText())) throw HandoverCommand.missing();
    }

    public record ReceiptQuery(UUID warehouse, UUID caseId, UUID key, String kind) { }
}
