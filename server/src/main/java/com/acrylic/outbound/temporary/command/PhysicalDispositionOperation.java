package com.acrylic.outbound.temporary.command;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

/** Opt-in physical disposition; retaining an aborted source never grants scanning authority. */
public final class PhysicalDispositionOperation implements CommandOperation {
    private final PhysicalDispositionVerification verification;
    private final PhysicalDispositionStore store;
    public PhysicalDispositionOperation(PhysicalDispositionVerification verification, PhysicalDispositionStore store) {
        this.verification = verification; this.store = store;
    }
    @Override public String commandType() { return "PHYSICAL_DISPOSITION"; }
    @Override public boolean recordsDecision() { return true; }
    @Override public boolean requiresCandidateContext() { return false; }
    @Override public void apply(CommandContext context) { throw new IllegalStateException("Full command required"); }
    @Override public JsonNode execute(CommandWrite write) {
        var expected = write.command().expected();
        var verified = verified(write);
        var manifest = SubmissionJson.parse(write.command().body().path("dispositionManifest").toString());
        var items = PhysicalDispositionManifest.require(manifest, verified.activeBales(),
            expected.path("dispositionManifestSha256").asText(), expected.path("dispositionManifestVersion").asLong());
        UUID id = store.apply(verified, expected.path("dispositionManifestSha256").asText(),
            expected.path("dispositionManifestVersion").asLong(), items, Instant.now());
        return SubmissionJson.object().put("kind", commandType()).put("shortShipmentReason", "")
            .putNull("shortApprovalReceiptId").putNull("shortApprovalSha256").putNull("submissionReceipt")
            .put("dispositionId", id.toString()).put("evidenceSetId", verified.set().toString())
            .put("verificationId", verified.verification().toString()).put("manifestSha256", expected.path("dispositionManifestSha256").asText())
            .put("manifestVersion", expected.path("dispositionManifestVersion").asText()).set("products", manifest.path("items").deepCopy());
    }

    private PhysicalDispositionVerification.Verified verified(CommandWrite write) {
        var c = write.context(); var e = write.command().expected();
        UUID set = UUID.fromString(e.path("evidenceSetId").asText()), proof = UUID.fromString(e.path("verificationId").asText());
        if ("ABORTED".equals(c.text("status")))
            return verification.readForContinuation(c.id("warehouseId"), c.id("scopeId"), c.id("submissionId"),
                c.version("workRound"), set, proof, e.path("evidenceSetSha256").asText(),
                e.path("verificationVersion").asLong(), e.path("verificationAlgorithmVersion").asLong());
        return verification.read(c.id("warehouseId"), c.id("scopeId"), c.id("submissionId"), c.version("workRound"),
            set, proof, e.path("evidenceSetSha256").asText(), e.path("verificationVersion").asLong(),
            e.path("verificationAlgorithmVersion").asLong());
    }
}
