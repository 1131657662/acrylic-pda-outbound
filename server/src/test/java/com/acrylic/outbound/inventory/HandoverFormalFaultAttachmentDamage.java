package com.acrylic.outbound.inventory;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Files;
import static org.assertj.core.api.Assertions.assertThat;

/** New synthetic isolated database only: simulate lost/corrupt blob, restore exact bytes and guards. */
final class HandoverFormalFaultAttachmentDamage {
    private final HandoverFormalFaultSource f;
    private final String id;
    private final String read;

    HandoverFormalFaultAttachmentDamage(HandoverFormalFaultSource f, JsonNode ref) {
        this.f = f; id = ref.path("attachmentId").asText();
        read = "/handovers/" + f.source.kase + "/attachments/" + id + "?warehouseId=" + f.source.r.f.web.identity().warehouse();
    }

    void verify() throws Exception { damaged(); missing(); }

    private void damaged() throws Exception {
        var jdbc = f.source.jdbc;
        byte[] original = jdbc.queryForObject("SELECT content_bytes FROM handover_attachment WHERE id=UUID_TO_BIN(?)", byte[].class, id);
        byte[] damaged = original.clone(); damaged[0] ^= 7;
        Files.write(f.source.directory.resolve("formal-attachment-corrupt-rejected.bin"), damaged);
        String trigger = retainTrigger("handover_attachment_no_update");
        var before = f.facts.snapshot("formal-attachment-damage-restored-before");
        jdbc.execute("DROP TRIGGER handover_attachment_no_update");
        try {
            jdbc.update("UPDATE handover_attachment SET content_bytes=? WHERE id=UUID_TO_BIN(?)", damaged, id);
            rejectedReads("formal-attachment-damage");
        } finally {
            jdbc.update("UPDATE handover_attachment SET content_bytes=? WHERE id=UUID_TO_BIN(?)", original, id);
            jdbc.execute(trigger);
        }
        assertThat(f.facts.snapshot("formal-attachment-damage-restored-after")).isEqualTo(before);
        assertThat(f.workflow.web.get(read)).isEqualTo(original);
    }

    private void missing() throws Exception {
        var jdbc = f.source.jdbc;
        var row = jdbc.queryForMap("SELECT * FROM handover_attachment WHERE id=UUID_TO_BIN(?)", id);
        String trigger = retainTrigger("handover_attachment_no_delete");
        var before = f.facts.snapshot("formal-attachment-missing-restored-before");
        jdbc.execute("DROP TRIGGER handover_attachment_no_delete");
        try {
            removeSyntheticBlob(); rejectedReads("formal-attachment-missing");
        } finally {
            jdbc.update("INSERT INTO handover_attachment(id,case_id,warehouse_id,uploaded_by,upload_key,content_sha256,size_bytes,content_bytes,created_at) VALUES(?,?,?,?,?,?,?,?,?)",
                row.get("id"), row.get("case_id"), row.get("warehouse_id"), row.get("uploaded_by"), row.get("upload_key"),
                row.get("content_sha256"), row.get("size_bytes"), row.get("content_bytes"), row.get("created_at"));
            jdbc.execute(trigger);
        }
        assertThat(f.facts.snapshot("formal-attachment-missing-restored-after")).isEqualTo(before);
    }

    private void removeSyntheticBlob() {
        f.source.jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection -> {
            try (var statement = connection.createStatement()) {
                statement.execute("SET FOREIGN_KEY_CHECKS=0");
                try (var delete = connection.prepareStatement("DELETE FROM handover_attachment WHERE id=UUID_TO_BIN(?)")) {
                    delete.setString(1, id); delete.executeUpdate();
                } finally { statement.execute("SET FOREIGN_KEY_CHECKS=1"); }
            }
            return null;
        });
    }

    private void rejectedReads(String label) throws Exception {
        var before = f.facts.snapshot(label + "-before");
        f.workflow.web.get(read, 409, "HANDOVER_RECEIPT_INTEGRITY_FAILURE");
        f.workflow.web.get(f.contextPath("FAULT_FENCE"), 409, "HANDOVER_RECEIPT_INTEGRITY_FAILURE");
        f.workflow.web.command(f.command, 409, "HANDOVER_RECEIPT_INTEGRITY_FAILURE");
        assertThat(f.facts.snapshot(label + "-after")).isEqualTo(before);
    }

    private String retainTrigger(String name) throws Exception {
        checkTriggerName(name);
        String ddl = f.source.jdbc.queryForMap("SHOW CREATE TRIGGER " + name).get("SQL Original Statement").toString();
        Files.writeString(f.source.directory.resolve("formal-" + name + ".sql"), ddl);
        return ddl;
    }

    private static void checkTriggerName(String name) {
        if (!java.util.Set.of("handover_attachment_no_update", "handover_attachment_no_delete").contains(name))
            throw new IllegalArgumentException("Only the isolated attachment guard may be retained");
    }
}
