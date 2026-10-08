package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

/** Refusals consume real archived origins without changing any durable business/identity table. */
final class HandoverFaultRejectionChecks {
    private final HandoverTemporaryFaultFenceChecks f;
    private final HandoverFaultFacts facts;
    HandoverFaultRejectionChecks(HandoverTemporaryFaultFenceChecks f) throws Exception {
        this.f = f; facts = new HandoverFaultFacts(f.source.jdbc, f.directory.resolve("negative-facts"));
    }
    void verify() throws Exception {
        controls(); archives(); self(); key(); files(); permission();
        HandoverFaultRollbackChecks.verify(f, f.faultCommand(), facts);
    }
    private void controls() throws Exception {
        for (String state : java.util.List.of("ONLINE", "LOST")) {
            var command = f.faultCommand(); ((ObjectNode) command.path("physicalControl")).put("deviceState", state);
            refusal(command, "HANDOVER_FAULT_NOT_CONTROLLED", "control-" + state);
        }
        var pending = f.faultCommand(); ((ObjectNode) pending.path("physicalControl")).put("unresolvedInputCount", "1");
        refusal(pending, "HANDOVER_FAULT_NOT_CONTROLLED", "pending");
        var future = f.faultCommand(); ((ObjectNode) future.path("physicalControl")).put("controlledAt", Instant.now().plusSeconds(60).toString());
        refusal(future, "HANDOVER_FAULT_CUTOFF_INVALID", "future");
        var past = f.faultCommand(); ((ObjectNode) past.path("physicalControl")).put("controlledAt", "2000-01-01T00:00:00Z");
        refusal(past, "HANDOVER_EVIDENCE_CHANGED", "before-archive");
        refusal(f.faultCommand().put("sourceDeviceId", UUID.randomUUID().toString()), "HANDOVER_CONTEXT_CHANGED", "wrong-device");
    }
    private void archives() throws Exception {
        for (String field : java.util.List.of("captureHighWatermark", "auditThroughSeq", "orderScanHighWatermark")) {
            var command = f.faultCommand(); ((ObjectNode) command.path("archivedEvidence")).put(field, "99999");
            refusal(command, "HANDOVER_STOP_CONTEXT_CHANGED", field);
        }
        var absent = f.faultCommand(); ((ObjectNode) absent.path("archivedEvidence")).put("verificationId", UUID.randomUUID().toString());
        refusal(absent, "HANDOVER_EVIDENCE_CHANGED", "missing-cutoff");
        var hash = f.faultCommand(); ((ObjectNode) hash.path("archivedEvidence")).put("setSha256", "0".repeat(64));
        refusal(hash, "TEMP_CONTINUATION_STOP_PROOF_REQUIRED", "wrong-set");
    }
    private void self() throws Exception {
        var before = facts.snapshot("self-before");
        f.source.web.command(f.faultCommand(), 403, "HANDOVER_SELF_APPROVAL_DENIED");
        assertThat(facts.snapshot("self-after")).isEqualTo(before);
    }
    private void key() throws Exception {
        var id = Ids.bytes(f.source.head.control().device());
        String original = f.source.jdbc.queryForObject("SELECT key_sha256 FROM device WHERE id=?", String.class, id);
        f.source.jdbc.update("UPDATE device SET key_sha256=? WHERE id=?", "0".repeat(64), id);
        try { refusal(f.faultCommand(), null, "untrusted-key"); }
        finally { f.source.jdbc.update("UPDATE device SET key_sha256=? WHERE id=?", original, id); }
    }
    private void files() throws Exception {
        String hash = SubmissionJson.sha(f.body);
        Path original = f.source.directory.getParent().resolve("storage/submissions/" + hash + ".json");
        unavailable(original, false, "missing-body"); unavailable(original, true, "corrupt-body");
        String page = f.source.jdbc.queryForObject("SELECT file_key FROM source_evidence_object "
            + "WHERE submission_id=? AND kind='MANIFEST_PAGE' LIMIT 1", String.class, Ids.bytes(f.source.head.control().id()));
        unavailable(f.source.directory.getParent().resolve("storage").resolve(page), false, "missing-page");
        String stop = f.source.jdbc.queryForObject("SELECT o.file_key FROM source_evidence_set e JOIN source_evidence_object o "
            + "ON o.id=e.stop_object_id WHERE e.id=UUID_TO_BIN(?)", String.class, f.source.command.path("sourceEvidenceSetId").asText());
        unavailable(f.source.directory.getParent().resolve("storage").resolve(stop), false, "missing-stop");
    }
    private void unavailable(Path original, boolean corrupt, String label) throws Exception {
        Path saved = original.resolveSibling(original.getFileName() + ".d07-" + label);
        Files.move(original, saved);
        try {
            if (corrupt) Files.writeString(original, "{}");
            refusal(f.faultCommand(), null, label);
        } finally {
            if (corrupt) Files.move(original, original.resolveSibling(original.getFileName() + ".d07-corrupt-rejected"));
            Files.copy(saved, original);
        }
    }
    private void permission() throws Exception {
        var id = Ids.bytes(f.supervisor);
        var roles = f.source.jdbc.queryForList("SELECT role_id FROM user_role WHERE user_id=?", byte[].class, id);
        var command = f.faultCommand(); f.source.jdbc.update("DELETE FROM user_role WHERE user_id=?", id);
        try {
            var before = facts.snapshot("revoked-before"); f.faultReviewer.command(command, 403, "PERMISSION_DENIED");
            assertThat(facts.snapshot("revoked-after")).isEqualTo(before);
        } finally { for (var role : roles) f.source.jdbc.update("INSERT INTO user_role VALUES(?,?)", id, role); }
    }
    private void refusal(ObjectNode command, String code, String label) throws Exception {
        var before = facts.snapshot(label + "-before"); f.faultReviewer.command(command, 409, code);
        assertThat(facts.snapshot(label + "-after")).isEqualTo(before);
    }
}
