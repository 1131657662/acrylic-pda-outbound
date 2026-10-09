package com.acrylic.outbound.inventory;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

/** Actual opaque bytes on a FORMAL case use the shared attachment consumer and immutable fault receipt. */
final class HandoverFormalFaultAttachments {
    private final HandoverFormalFaultSource f;
    private final byte[] original = "FORMAL custody evidence: synthetic only\n".repeat(1000).getBytes(java.nio.charset.StandardCharsets.UTF_8);
    private JsonNode ref;
    HandoverFormalFaultAttachments(HandoverFormalFaultSource f) { this.f = f; }

    void prepare() throws Exception {
        String sha = CorrectionAttachmentHttp.sha(original); var key = UUID.randomUUID();
        var before = f.facts.snapshot("attachment-sha-before");
        upload(key, original, "0".repeat(64), 400, "ATTACHMENT_HASH_MISMATCH");
        assertThat(f.facts.snapshot("attachment-sha-after")).isEqualTo(before);
        ref = SubmissionJson.parse(upload(key, original, sha, 200, null)).path("data");
        assertThat(SubmissionJson.parse(upload(key, original, sha, 200, null)).path("data")).isEqualTo(ref);
        before = f.facts.snapshot("attachment-key-before");
        upload(key, new byte[]{1}, CorrectionAttachmentHttp.sha(new byte[]{1}), 409, "IDEMPOTENCY_CONTENT_MISMATCH");
        assertThat(f.facts.snapshot("attachment-key-after")).isEqualTo(before);
        Files.write(f.source.directory.resolve("formal-attachment.bin"), original, StandardOpenOption.CREATE_NEW);
        f.command.putArray("attachments").add(ref);
        assertThat(f.workflow.web.get(readPath())).isEqualTo(original);
        refusedReferences();
        new HandoverFormalFaultAttachmentAccessChecks(f, ref).verify();
    }

    void recorded() throws Exception {
        assertThat(SubmissionJson.parse(f.receipt).at("/businessDecision/attachments")).isEqualTo(f.command.path("attachments"));
        assertThat(f.source.jdbc.queryForObject("SELECT COUNT(*) FROM handover_attachment_use WHERE case_id=UUID_TO_BIN(?)", Long.class,
            f.source.kase.toString())).isEqualTo(1);
        assertThat(f.workflow.web.get(readPath())).isEqualTo(original);
        Files.writeString(f.source.directory.resolve("formal-attachment-reference.json"), SubmissionJson.canonical(ref), StandardOpenOption.CREATE_NEW);
        new HandoverFormalFaultAttachmentDamage(f, ref).verify();
    }

    void download() throws Exception {
        assertThat(f.workflow.web.get(readPath())).isEqualTo(original);
        Files.writeString(f.source.directory.resolve("formal-attachment-restart.marker"), "Original FORMAL attachment downloads byte-for-byte after restarted final success.\n");
    }

    private void refusedReferences() throws Exception {
        for (String field : java.util.List.of("attachmentId", "evidenceSha256", "sizeBytes")) {
            var wrong = f.command.deepCopy();
            ((com.fasterxml.jackson.databind.node.ObjectNode) wrong.path("attachments").get(0)).put(field,
                field.equals("attachmentId") ? UUID.randomUUID().toString() : field.equals("evidenceSha256") ? "0".repeat(64) : "1");
            var before = f.facts.snapshot("formal-ref-" + field + "-before");
            f.workflow.web.command(wrong, field.equals("attachmentId") ? 404 : 400,
                field.equals("attachmentId") ? "HANDOVER_ATTACHMENT_NOT_FOUND" : "INVALID_HANDOVER_ATTACHMENT");
            assertThat(f.facts.snapshot("formal-ref-" + field + "-after")).isEqualTo(before);
        }
    }

    private byte[] upload(UUID key, byte[] bytes, String sha, int status, String code) throws Exception {
        return f.workflow.web.put("/handovers/" + f.source.kase + "/attachments/" + key + "?warehouseId="
            + f.source.r.f.web.identity().warehouse(), bytes, sha, status, code);
    }

    private String readPath() {
        return "/handovers/" + f.source.kase + "/attachments/" + ref.path("attachmentId").asText()
            + "?warehouseId=" + f.source.r.f.web.identity().warehouse();
    }
}
