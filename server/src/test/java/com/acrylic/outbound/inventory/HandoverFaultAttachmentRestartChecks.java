package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import java.nio.file.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.*;

/** Recorded originals remain revalidated and downloadable after the opt-in writers close. */
public final class HandoverFaultAttachmentRestartChecks {
    public static void verify(WebApplicationContext app, Path attempt) throws Exception {
        var jdbc = app.getBean(JdbcTemplate.class);
        var row = jdbc.queryForMap("""
            SELECT BIN_TO_UUID(c.id) case_id,BIN_TO_UUID(c.warehouse_id) warehouse_id,u.username
            FROM handover_case c JOIN handover_fault_fence f ON f.case_id=c.id JOIN app_user u ON u.id=f.recorded_by
            """);
        var directory = Files.createDirectory(attempt.resolve("fault-attachment-restart"));
        var http = new HandoverApprovalHttp(row.get("username").toString(), directory);
        String base = "/handovers/" + row.get("case_id"), warehouse = row.get("warehouse_id").toString();
        var context = SubmissionJson.parse(http.get(base + "/context?warehouseId=" + warehouse + "&commandType=FAULT_FENCE"));
        assertThat(context.path("fault").path("entryAvailable").asBoolean()).isFalse();
        var refs = context.path("fault").path("receipt").path("businessDecision").path("attachments");
        assertThat(refs).hasSize(5);
        for (var ref : refs) {
            String id = ref.path("attachmentId").asText();
            byte[] bytes = http.get(base + "/attachments/" + id + "?warehouseId=" + warehouse);
            assertThat(CorrectionAttachmentHttp.sha(bytes)).isEqualTo(ref.path("evidenceSha256").asText());
            assertThat(bytes.length).isEqualTo(Integer.parseInt(ref.path("sizeBytes").asText()));
            Files.write(directory.resolve(id + ".bin"), bytes);
        }
        http.put(base + "/attachments/" + java.util.UUID.randomUUID() + "?warehouseId=" + warehouse, new byte[]{1},
            CorrectionAttachmentHttp.sha(new byte[]{1}), 405, "METHOD_NOT_ALLOWED");
        Files.writeString(directory.resolve("verified.marker"), "Closed write gates, signed fault read and all actual attachment hashes/lengths re-proved after restart.\n");
    }
}
