package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Real chunked HTTP, maximum-capacity originals and original-key recovery in the fault transaction. */
final class HandoverFaultAttachmentChecks {
    final HandoverTemporaryFaultFenceChecks f;
    final HandoverFaultFacts facts;
    final List<JsonNode> refs = new ArrayList<>();
    private final Map<String, byte[]> originals = new LinkedHashMap<>();
    private final List<ObjectNode> browserUploads = new ArrayList<>();
    HandoverFaultAttachmentChecks(HandoverTemporaryFaultFenceChecks f) throws Exception {
        this.f = f; facts = new HandoverFaultFacts(f.source.jdbc, f.directory.resolve("attachment-facts"));
    }

    void prepare(ObjectNode command) throws Exception {
        new HandoverFaultAttachmentRefusals(this).uploads();
        for (int i = 0; i < 5; i++) {
            byte[] bytes = new byte[10 * 1024 * 1024]; bytes[0] = (byte) i; bytes[bytes.length - 1] = (byte) (i + 1);
            var key = UUID.randomUUID(); String sha = CorrectionAttachmentHttp.sha(bytes);
            var first = upload(f.faultReviewer, key, bytes, sha, 200, null);
            assertThat(SubmissionJson.parse(upload(f.faultReviewer, key, bytes, sha, 200, null)).path("data"))
                .isEqualTo(SubmissionJson.parse(first).path("data"));
            upload(f.faultReviewer, key, new byte[]{(byte) i}, CorrectionAttachmentHttp.sha(new byte[]{(byte) i}), 409, "IDEMPOTENCY_CONTENT_MISMATCH");
            var ref = SubmissionJson.parse(first).path("data"); refs.add(ref);
            browserUploads.add(SubmissionJson.object().put("key", key.toString()).put("name", "attachment-" + ref.path("attachmentId").asText() + ".bin")
                .put("sha", sha).put("size", bytes.length).put("epoch", "1").putNull("reference"));
            originals.put(ref.path("attachmentId").asText(), bytes);
            Files.write(f.directory.resolve("attachment-" + ref.path("attachmentId").asText() + ".bin"), bytes);
            assertThat(f.faultReviewer.get(readPath(ref))).isEqualTo(bytes);
        }
        command.set("attachments", SubmissionJson.object().putArray("items").addAll(refs));
        var browser = SubmissionJson.object().put("userId", f.supervisor.toString());
        browser.set("uploads", SubmissionJson.object().putArray("items").addAll(browserUploads));
        Files.writeString(f.directory.resolve("attachment-browser-fixture.json"), SubmissionJson.canonical(browser));
        new HandoverFaultAttachmentRefusals(this).commands(command);
        HandoverFaultRollbackChecks.verify(f, command, facts);
        assertThat(f.source.jdbc.queryForObject("SELECT SUM(size_bytes) FROM handover_attachment", Long.class)).isEqualTo(50L * 1024 * 1024);
    }

    void recorded(ObjectNode command, byte[] bytes) throws Exception {
        var receipt = SubmissionJson.parse(bytes);
        assertThat(receipt.path("businessDecision").path("attachments")).isEqualTo(command.path("attachments"));
        assertThat(f.source.jdbc.queryForObject("SELECT COUNT(*) FROM handover_attachment_use WHERE case_id=?", Long.class, Ids.bytes(f.source.kase))).isEqualTo(5);
        for (var ref : refs) {
            String id = ref.path("attachmentId").asText();
            assertThat(f.source.web.get(readPath(ref))).isEqualTo(originals.get(id));
            assertThatThrownBy(() -> f.source.jdbc.update("UPDATE handover_attachment SET size_bytes=1 WHERE id=UUID_TO_BIN(?)", id))
                .hasMessageContaining("attachment is immutable");
        }
        assertThatThrownBy(() -> f.source.jdbc.update("DELETE FROM handover_attachment_use WHERE case_id=?", Ids.bytes(f.source.kase)))
            .hasMessageContaining("attachment use is retained");
        new HandoverFaultAttachmentDamageChecks(this).verify(command);
        Files.writeString(f.directory.resolve("attachments-verified.marker"), "5 actual 10MiB files, 50MiB original command; same-key upload replay, signed refs, independent reviewer byte downloads and immutable uses.\n");
    }

    byte[] upload(HandoverApprovalHttp http, UUID key, byte[] bytes, String sha, int status, String code) throws Exception {
        return http.put("/handovers/" + f.source.kase + "/attachments/" + key + "?warehouseId=" + f.source.head.warehouse(), bytes, sha, status, code);
    }

    String readPath(JsonNode ref) {
        return "/handovers/" + f.source.kase + "/attachments/" + ref.path("attachmentId").asText() + "?warehouseId=" + f.source.head.warehouse();
    }
}
