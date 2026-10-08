package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Refusals compare all durable facts; actual blob digests prevent a size-only assertion. */
final class HandoverFaultAttachmentRefusals {
    private final HandoverFaultAttachmentChecks a;
    HandoverFaultAttachmentRefusals(HandoverFaultAttachmentChecks a) { this.a = a; }

    void uploads() throws Exception {
        byte[] bytes = {0, -1, 7}; var key = UUID.randomUUID();
        var before = a.facts.snapshot("upload-denials-before");
        a.upload(a.f.faultReviewer, key, bytes, "0".repeat(64), 400, "ATTACHMENT_HASH_MISMATCH");
        a.upload(a.f.faultReviewer, key, new byte[0], CorrectionAttachmentHttp.sha(new byte[0]), 400, "INVALID_HANDOVER_ATTACHMENT");
        a.upload(a.f.faultReviewer, key, new byte[10 * 1024 * 1024 + 1], "0".repeat(64), 400, "ATTACHMENT_LIMIT_EXCEEDED");
        a.upload(a.f.source.web, key, bytes, CorrectionAttachmentHttp.sha(bytes), 403, "PERMISSION_DENIED");
        assertThat(a.facts.snapshot("upload-denials-after")).isEqualTo(before);
        revoked(key, bytes); rollback(key, bytes);
    }

    void commands(ObjectNode valid) throws Exception {
        var wrongSha = valid.deepCopy(); ((ObjectNode) wrongSha.path("attachments").get(0)).put("evidenceSha256", "0".repeat(64));
        refusal(wrongSha, 400, "INVALID_HANDOVER_ATTACHMENT", "sha");
        var wrongSize = valid.deepCopy(); ((ObjectNode) wrongSize.path("attachments").get(0)).put("sizeBytes", "1");
        refusal(wrongSize, 400, "INVALID_HANDOVER_ATTACHMENT", "size");
        var missing = valid.deepCopy(); ((ObjectNode) missing.path("attachments").get(0)).put("attachmentId", UUID.randomUUID().toString());
        refusal(missing, 404, "HANDOVER_ATTACHMENT_NOT_FOUND", "missing");
        var empty = valid.deepCopy(); empty.putArray("attachments"); refusal(empty, 409, "INVALID_HANDOVER_FAULT", "empty");
        var duplicate = valid.deepCopy(); duplicate.withArray("attachments").remove(4);
        duplicate.withArray("attachments").add(duplicate.path("attachments").get(0).deepCopy());
        refusal(duplicate, 409, "INVALID_HANDOVER_FAULT", "duplicate");
        var six = valid.deepCopy(); six.withArray("attachments").add(valid.path("attachments").get(0).deepCopy());
        refusal(six, 409, "INVALID_HANDOVER_FAULT", "six");
        a.f.source.web.get(a.readPath(a.refs.getFirst()), 404, "HANDOVER_ATTACHMENT_NOT_FOUND");
        a.f.faultReviewer.get(a.readPath(a.refs.getFirst()).replace(a.f.source.kase.toString(), UUID.randomUUID().toString()), 404, "HANDOVER_CASE_NOT_FOUND");
        a.f.faultReviewer.get(a.readPath(a.refs.getFirst()).replace(a.f.source.head.warehouse().toString(), UUID.randomUUID().toString()), 404, "HANDOVER_CASE_NOT_FOUND");
    }

    private void revoked(UUID key, byte[] bytes) throws Exception {
        var id = Ids.bytes(a.f.supervisor); var jdbc = a.f.source.jdbc;
        var roles = jdbc.queryForList("SELECT role_id FROM user_role WHERE user_id=?", byte[].class, id);
        jdbc.update("DELETE FROM user_role WHERE user_id=?", id);
        try {
            var before = a.facts.snapshot("upload-revoked-before");
            a.upload(a.f.faultReviewer, key, bytes, CorrectionAttachmentHttp.sha(bytes), 403, "PERMISSION_DENIED");
            assertThat(a.facts.snapshot("upload-revoked-after")).isEqualTo(before);
        } finally { for (var role : roles) jdbc.update("INSERT INTO user_role VALUES(?,?)", id, role); }
    }

    private void rollback(UUID key, byte[] bytes) throws Exception {
        var jdbc = a.f.source.jdbc; var before = a.facts.snapshot("upload-tail-before");
        jdbc.execute("CREATE TRIGGER d07_upload_tail AFTER INSERT ON operation_event FOR EACH ROW BEGIN "
            + "IF NEW.event_type='HANDOVER_ATTACHMENT_UPLOAD' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='D07_ATTACHMENT_UPLOAD_TAIL'; END IF; END");
        try { a.upload(a.f.faultReviewer, key, bytes, CorrectionAttachmentHttp.sha(bytes), 500, "INTERNAL_ERROR"); }
        finally { jdbc.execute("DROP TRIGGER d07_upload_tail"); }
        assertThat(a.facts.snapshot("upload-tail-after")).isEqualTo(before);
    }

    private void refusal(ObjectNode command, int status, String code, String label) throws Exception {
        command.put("commandId", UUID.randomUUID().toString());
        var before = a.facts.snapshot("ref-" + label + "-before"); a.f.faultReviewer.command(command, status, code);
        assertThat(a.facts.snapshot("ref-" + label + "-after")).isEqualTo(before);
    }
}
