package com.acrylic.outbound.temporary.command;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

/** Closes a rejected or aborted TEMP source after its complete physical disposition. */
public final class CloseDispositionOperation implements CommandOperation {
    private final PhysicalDispositionVerification verification;
    private final PhysicalDispositionStore store;

    public CloseDispositionOperation(PhysicalDispositionVerification verification, PhysicalDispositionStore store) {
        this.verification = verification;
        this.store = store;
    }

    @Override public String commandType() { return "CLOSE_DISPOSITION"; }
    @Override public boolean recordsDecision() { return true; }
    @Override public boolean requiresCandidateContext() { return false; }
    @Override public void apply(CommandContext context) { throw new IllegalStateException("Full command required"); }

    @Override public JsonNode execute(CommandWrite write) {
        var expected = write.command().expected();
        var verified = verification.readForClose(write.context().id("warehouseId"), write.context().id("scopeId"),
            write.context().id("submissionId"), write.context().version("workRound"), UUID.fromString(expected.path("evidenceSetId").asText()),
            UUID.fromString(expected.path("verificationId").asText()), expected.path("evidenceSetSha256").asText(),
            expected.path("verificationVersion").asLong(), expected.path("verificationAlgorithmVersion").asLong());
        var manifest = SubmissionJson.parse(write.command().body().path("dispositionManifest").toString());
        var items = PhysicalDispositionManifest.require(manifest, verified.activeBales(), expected.path("dispositionManifestSha256").asText(),
            expected.path("dispositionManifestVersion").asLong());
        UUID id = store.apply(verified, expected.path("dispositionManifestSha256").asText(),
            expected.path("dispositionManifestVersion").asLong(), items, Instant.now(), true, write.command().text("reason"));
        return SubmissionJson.object().put("kind", commandType()).put("status", "CANCELLED").put("scopeClosed", true)
            .put("slotClosed", write.context().id("canonicalOrderId") != null).put("dispositionId", id.toString())
            .put("evidenceSetId", verified.set().toString()).put("verificationId", verified.verification().toString())
            .put("closeReason", write.command().text("reason"))
            .put("manifestSha256", expected.path("dispositionManifestSha256").asText())
            .put("manifestVersion", expected.path("dispositionManifestVersion").asText())
            .set("products", manifest.path("items").deepCopy());
    }
}
