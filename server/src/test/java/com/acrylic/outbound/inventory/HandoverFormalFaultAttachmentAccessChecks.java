package com.acrylic.outbound.inventory;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

/** FORMAL case upload/read authority uses real Web sessions and compares every durable table. */
final class HandoverFormalFaultAttachmentAccessChecks {
    private final HandoverFormalFaultSource f;
    private final String base;
    private final String warehouse;
    private final String read;

    HandoverFormalFaultAttachmentAccessChecks(HandoverFormalFaultSource f, JsonNode ref) {
        this.f = f;
        warehouse = f.source.r.f.web.identity().warehouse().toString();
        base = "/handovers/" + f.source.kase;
        read = base + "/attachments/" + ref.path("attachmentId").asText() + "?warehouseId=" + warehouse;
    }

    void verify() throws Exception {
        var source = new HandoverApprovalHttp(f.source.r.f.web.identity().user().username(), f.source.directory);
        var before = f.facts.snapshot("formal-attachment-access-before");
        source.put(upload(), new byte[]{1}, CorrectionAttachmentHttp.sha(new byte[]{1}), 403, "PERMISSION_DENIED");
        source.get(read, 404, "HANDOVER_ATTACHMENT_NOT_FOUND");
        f.workflow.web.get(read.replace(f.source.kase.toString(), UUID.randomUUID().toString()), 404, "HANDOVER_CASE_NOT_FOUND");
        f.workflow.web.get(read.replace(warehouse, UUID.randomUUID().toString()), 404, "HANDOVER_CASE_NOT_FOUND");
        assertThat(f.facts.snapshot("formal-attachment-access-after")).isEqualTo(before);
        revoked();
    }

    private void revoked() throws Exception {
        var jdbc = f.source.jdbc;
        var user = jdbc.queryForObject("SELECT id FROM app_user WHERE username=?", byte[].class, f.workflow.web.username());
        var roles = jdbc.queryForList("SELECT role_id FROM user_role WHERE user_id=?", byte[].class, user);
        assertThat(roles).isNotEmpty();
        jdbc.update("DELETE FROM user_role WHERE user_id=?", user);
        try {
            var before = f.facts.snapshot("formal-attachment-revoked-before");
            f.workflow.web.put(upload(), new byte[]{1}, CorrectionAttachmentHttp.sha(new byte[]{1}), 403, "PERMISSION_DENIED");
            f.workflow.web.get(read, 403, "PERMISSION_DENIED");
            assertThat(f.facts.snapshot("formal-attachment-revoked-after")).isEqualTo(before);
        } finally { for (var role : roles) jdbc.update("INSERT INTO user_role VALUES(?,?)", user, role); }
    }

    private String upload() { return base + "/attachments/" + UUID.randomUUID() + "?warehouseId=" + warehouse; }
}
