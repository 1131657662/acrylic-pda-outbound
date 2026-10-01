package com.acrylic.outbound.correction;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Supplies command contexts and original responses without executing or signing a command. */
@Service
@ConditionalOnProperty(prefix="outbound.corrections.commands", name="enabled", havingValue="true")
@Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
public class CorrectionReadService {
    private final CorrectionCommand.Identity identity;
    private final CorrectionStore facts;
    private final CorrectionReadStore store;
    private final CorrectionReadReceipt verifier;

    CorrectionReadService(CorrectionCommand.Identity identity, CorrectionStore facts, CorrectionReadStore store,
            CorrectionReadReceipt verifier) {
        this.identity = identity; this.facts = facts; this.store = store; this.verifier = verifier;
    }

    public JsonNode order(UUID warehouse, UUID order) {
        var actor = identity.current(warehouse, false); var context = facts.orderFacts(order);
        requireWarehouse(actor, context);
        var result = SubmissionJson.object(); result.set("context", context);
        result.set("expected", CorrectionCommand.Expected.forCreate(context, Long.parseLong(actor.authVersion())));
        result.set("capabilities", CorrectionReceiptCodec.capabilities()); return result;
    }

    public JsonNode document(UUID warehouse, UUID document, String kind) {
        if (!Set.of("APPROVE", "WITHDRAW", "WITHDRAW_CONFIRM").contains(kind)) throw CorrectionCommand.missing();
        var actor = identity.current(warehouse, CorrectionCommand.requiresApproval(kind));
        var context = facts.documentFacts(document); requireWarehouse(actor, context);
        if (!CorrectionCommand.requiresApproval(kind) && !actor.id().toString().equals(context.path("requestedBy").asText()))
            throw CorrectionCommand.missing();
        var original = verifier.verify(store.originalCreate(document));
        requireDocument(original.receipt(), context);
        var result = SubmissionJson.object(); result.set("context", context);
        result.set("expected", CorrectionCommand.Expected.forDocument(context, Long.parseLong(actor.authVersion())));
        result.set("originalCommand", original.command()); result.set("capabilities", CorrectionReceiptCodec.capabilities());
        return result;
    }

    public byte[] receipt(UUID warehouse, UUID key, String kind) {
        if (!CorrectionCommand.KINDS_SET.contains(kind)) throw CorrectionCommand.missing();
        var actor = identity.current(warehouse, CorrectionCommand.requiresApproval(kind));
        var row = facts.receipt(key.toString());
        if (row == null || !kind.equals(row.get("command_type"))
                || !(row.get("actor_id") instanceof byte[] owner) || !Arrays.equals(owner, Ids.bytes(actor.id())))
            throw CorrectionCommand.missing();
        var verified = verifier.verify(row); var recorded = verified.receipt().path("actor");
        if (!actor.id().toString().equals(recorded.path("principalId").asText())
                || !warehouse.toString().equals(recorded.path("warehouseId").asText())) throw CorrectionCommand.missing();
        if (recorded.hasNonNull("deviceId") && (actor.pda() == null
                || !actor.pda().deviceId().toString().equals(recorded.path("deviceId").asText()))) throw CorrectionCommand.missing();
        return verified.bytes();
    }

    private void requireDocument(JsonNode receipt, JsonNode context) {
        for (String field : Set.of("documentId", "orderId"))
            CorrectionReadSignature.require(receipt.path(field).asText().equals(context.path(field).asText()));
        CorrectionReadSignature.require(receipt.path("commandSha256").asText().equals(context.path("documentSha256").asText())
            && receipt.path("actor").path("principalId").asText().equals(context.path("requestedBy").asText())
            && receipt.path("actor").path("warehouseId").asText().equals(context.path("warehouseId").asText()));
    }

    private void requireWarehouse(CorrectionCommand.Actor actor, JsonNode context) {
        if (!actor.warehouse().toString().equals(context.path("warehouseId").asText())) throw CorrectionCommand.missing();
    }
}
