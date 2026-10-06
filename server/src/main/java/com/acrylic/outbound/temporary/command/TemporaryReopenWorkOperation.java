package com.acrylic.outbound.temporary.command;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;

/** Issues an ABORTED TEMP continuation ticket; activation and scanning stay separate. */
public final class TemporaryReopenWorkOperation implements CommandOperation {
    private final PhysicalDispositionVerification verification;
    private final TemporaryContinuationTicketStore tickets;

    public TemporaryReopenWorkOperation(PhysicalDispositionVerification verification,
        TemporaryContinuationTicketStore tickets) {
        this.verification = verification; this.tickets = tickets;
    }

    @Override public String commandType() { return "REOPEN_WORK"; }
    @Override public boolean recordsDecision() { return true; }
    @Override public boolean requiresCandidateContext() { return false; }
    @Override public void apply(CommandContext context) { throw new IllegalStateException("Full command required"); }

    @Override public JsonNode execute(CommandWrite write) {
        var context = write.context(); var expected = write.command().expected();
        var proof = verification.readForContinuation(context.id("warehouseId"), context.id("scopeId"),
            context.id("submissionId"), context.version("workRound"), UUID.fromString(expected.path("evidenceSetId").asText()),
            UUID.fromString(expected.path("verificationId").asText()), expected.path("evidenceSetSha256").asText(),
            expected.path("verificationVersion").asLong(), expected.path("verificationAlgorithmVersion").asLong());
        var body = write.command().body();
        var items = TemporaryRetainedManifest.parse(body.path("retainedManifest"),
            expected.path("retainedManifestSha256").asText(), expected.path("retainedManifestVersion").asLong());
        long next = Long.parseLong(body.path("nextOrderScanSeq").asText());
        var ticket = tickets.issue(write, proof, items, expected.path("retainedManifestSha256").asText(),
            expected.path("retainedManifestVersion").asLong(), next, write.recordedAt());
        var products = SubmissionJson.object().putArray("items");
        for (var item : items) products.add(SubmissionJson.object().put("baleId", item.baleId().toString())
            .put("expectedDispatchCycle", Long.toString(item.cycle())).put("recordSha256", item.recordSha256()).put("disposition", "RETAIN"));
        return SubmissionJson.object().put("kind", commandType()).put("status", "TICKET_ISSUED")
            .put("ticketId", ticket.id().toString()).put("fromWorkRound", Long.toString(ticket.fromRound()))
            .put("toWorkRound", Long.toString(ticket.toRound())).put("evidenceSetId", proof.set().toString())
            .put("verificationId", proof.verification().toString()).put("retainedManifestSha256", expected.path("retainedManifestSha256").asText())
            .put("retainedManifestVersion", expected.path("retainedManifestVersion").asText())
            .put("nextOrderScanSeq", Long.toString(next)).set("products", products);
    }
}
