package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Deterministic external PREPARE and DB commit bytes, derived only from a verified operations approval. */
final class RecoveryScopeReleaseIntent {
    final RecoveryOperationsApproval approval;
    private final JsonNode prepare,commit;
    RecoveryScopeReleaseIntent(RecoveryOperationsApproval approval,Path root,String lockIdentity) {
        this.approval=approval; var facts=approval.facts(); var control=facts.path("control"); var physical=facts.path("physicalReview");
        var body=SubmissionJson.object().put("formatVersion",1).put("phase","PREPARE").put("storeRoot",root.toString())
            .put("storeLockIdentity",lockIdentity).put("approvalSha256",approval.sha256()); body.set("approval",facts); prepare=body;
        var receipt=SubmissionJson.object().put("formatVersion",1).put("kind","RECOVERED_TEMP_SCOPE_COMMIT")
            .put("commandId",text(facts,"commandId")).put("recoverySessionId",text(facts,"recoverySessionId"))
            .put("warehouseId",text(physical,"warehouseId")).put("scopeId",text(physical,"scopeId")).put("caseId",text(facts,"caseId"))
            .put("authoritySha256",text(facts,"authoritySha256")).put("approvalSha256",approval.sha256())
            .put("physicalReviewSha256",hash(physical)).put("prepareSha256",hash(prepare));
        receipt.set("control",control.deepCopy()); commit=receipt;
    }
    byte[] prepareBytes() { return RecoveryControlJson.encode(prepare); }
    byte[] commitBytes() { return RecoveryControlJson.encode(commit); }
    JsonNode commit() { return commit.deepCopy(); }
    String prepareSha256() { return hash(prepare); }
    String commitSha256() { return hash(commit); }
}
