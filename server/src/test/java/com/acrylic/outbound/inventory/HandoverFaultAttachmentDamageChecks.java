package com.acrylic.outbound.inventory;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Isolated disk-corruption simulation; restore exact original bytes and immutable trigger in finally. */
final class HandoverFaultAttachmentDamageChecks {
    private final HandoverFaultAttachmentChecks a;
    HandoverFaultAttachmentDamageChecks(HandoverFaultAttachmentChecks a) { this.a = a; }

    void verify(ObjectNode command) throws Exception {
        var jdbc = a.f.source.jdbc; var ref = a.refs.getFirst(); String id = ref.path("attachmentId").asText();
        byte[] original = jdbc.queryForObject("SELECT content_bytes FROM handover_attachment WHERE id=UUID_TO_BIN(?)", byte[].class, id);
        byte[] damaged = original.clone(); damaged[0] ^= 7;
        Files.write(a.f.directory.resolve("attachment-corrupt-rejected.bin"), damaged);
        var trigger = jdbc.queryForMap("SHOW CREATE TRIGGER handover_attachment_no_update").get("SQL Original Statement").toString();
        Files.writeString(a.f.directory.resolve("attachment-trigger-original.sql"), trigger);
        var before = a.facts.snapshot("damage-original");
        jdbc.execute("DROP TRIGGER handover_attachment_no_update");
        try {
            jdbc.update("UPDATE handover_attachment SET content_bytes=? WHERE id=UUID_TO_BIN(?)", damaged, id);
            var corrupt = a.facts.snapshot("damage-before-reads");
            a.f.faultReviewer.get(a.readPath(ref), 409, "HANDOVER_RECEIPT_INTEGRITY_FAILURE");
            a.f.faultReviewer.get("/handovers/" + a.f.source.kase + "/context?warehouseId=" + a.f.source.head.warehouse()
                + "&commandType=FAULT_FENCE", 409, "HANDOVER_RECEIPT_INTEGRITY_FAILURE");
            a.f.faultReviewer.command(command, 409, "HANDOVER_RECEIPT_INTEGRITY_FAILURE");
            assertThat(a.facts.snapshot("damage-after-reads")).isEqualTo(corrupt);
        } finally {
            jdbc.update("UPDATE handover_attachment SET content_bytes=? WHERE id=UUID_TO_BIN(?)", original, id);
            jdbc.execute(trigger);
        }
        assertThat(a.facts.snapshot("damage-restored")).isEqualTo(before);
        assertThat(a.f.faultReviewer.get(a.readPath(ref))).isEqualTo(original);
        missing(command, id);
    }

    private void missing(ObjectNode command, String id) throws Exception {
        var jdbc = a.f.source.jdbc; var original = jdbc.queryForMap("SELECT * FROM handover_attachment WHERE id=UUID_TO_BIN(?)", id);
        var trigger = jdbc.queryForMap("SHOW CREATE TRIGGER handover_attachment_no_delete").get("SQL Original Statement").toString();
        Files.writeString(a.f.directory.resolve("attachment-delete-trigger-original.sql"), trigger);
        var before = a.facts.snapshot("missing-original"); jdbc.execute("DROP TRIGGER handover_attachment_no_delete");
        try {
            jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection -> {
                try (var statement = connection.createStatement()) {
                    statement.execute("SET FOREIGN_KEY_CHECKS=0");
                    try (var delete = connection.prepareStatement("DELETE FROM handover_attachment WHERE id=UUID_TO_BIN(?)")) {
                        delete.setString(1, id); delete.executeUpdate();
                    } finally { statement.execute("SET FOREIGN_KEY_CHECKS=1"); }
                } return null;
            });
            var missing = a.facts.snapshot("missing-before-reads");
            rejectedReads(command); assertThat(a.facts.snapshot("missing-after-reads")).isEqualTo(missing);
        } finally {
            jdbc.update("INSERT INTO handover_attachment(id,case_id,warehouse_id,uploaded_by,upload_key,content_sha256,size_bytes,content_bytes,created_at) VALUES(?,?,?,?,?,?,?,?,?)",
                original.get("id"), original.get("case_id"), original.get("warehouse_id"), original.get("uploaded_by"), original.get("upload_key"),
                original.get("content_sha256"), original.get("size_bytes"), original.get("content_bytes"), original.get("created_at"));
            jdbc.execute(trigger);
        }
        assertThat(a.facts.snapshot("missing-restored")).isEqualTo(before);
    }

    private void rejectedReads(ObjectNode command) throws Exception {
        a.f.faultReviewer.get(a.readPath(a.refs.getFirst()), 409, "HANDOVER_RECEIPT_INTEGRITY_FAILURE");
        a.f.faultReviewer.get("/handovers/" + a.f.source.kase + "/context?warehouseId=" + a.f.source.head.warehouse()
            + "&commandType=FAULT_FENCE", 409, "HANDOVER_RECEIPT_INTEGRITY_FAILURE");
        a.f.faultReviewer.command(command, 409, "HANDOVER_RECEIPT_INTEGRITY_FAILURE");
    }
}
