package com.acrylic.outbound.inventory;

import com.acrylic.outbound.e2averify.E2aMigrationSnapshot;
import com.acrylic.outbound.submissions.SubmissionJson;
import java.nio.file.*;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/** Actual TEMP fault receipt and original private bytes survive the subsequent FORMAL fault migration. */
public final class HandoverFormalFaultMigrationChecks {
    public static void verify(WebApplicationContext app, Path attempt, Runnable migrate) throws Exception {
        var f = new HandoverTemporaryFaultFenceChecks(app, attempt);
        var command = f.faultCommand();
        var ref = attachment(f);
        command.putArray("attachments").add(ref);
        byte[] receipt = f.faultReviewer.command(command, 200, null);
        Files.write(f.directory.resolve("pre-formal-migration-fault-receipt.json"), receipt, StandardOpenOption.CREATE_NEW);
        assertThat(f.source.jdbc.queryForObject("SELECT COUNT(*) FROM handover_fault_fence", Long.class)).isEqualTo(1);
        var snapshots = new E2aMigrationSnapshot(f.source.jdbc, attempt, attempt.resolve("storage"));
        var before = snapshots.capture();
        Files.writeString(attempt.resolve("formal-migration-before.json"), before.toString(), StandardOpenOption.CREATE_NEW);
        migrate.run(); var after = snapshots.capture();
        Files.writeString(attempt.resolve("formal-migration-after.json"), after.toString(), StandardOpenOption.CREATE_NEW);
        before.path("tables").fields().forEachRemaining(row -> assertThat(after.path("tables").path(row.getKey())).as(row.getKey()).isEqualTo(row.getValue()));
        assertThat(after.path("files")).isEqualTo(before.path("files"));
        assertThat(f.faultReviewer.command(command, 200, null)).isEqualTo(receipt);
        assertThat(f.faultReviewer.get("/handovers/" + f.source.kase + "/attachments/" + ref.path("attachmentId").asText()
            + "?warehouseId=" + f.source.head.warehouse())).isEqualTo(Files.readAllBytes(f.directory.resolve("pre-formal-attachment.bin")));
        assertThat(f.source.jdbc.queryForObject("SELECT COUNT(*) FROM handover_formal_fault_fence", Long.class)).isZero();
        Files.writeString(attempt.resolve("formal-migration-preserved.marker"), "Every pre-existing table/private byte, TEMP fence and signed receipt retained; original-key replay verified after migration.\n");
    }

    private static com.fasterxml.jackson.databind.JsonNode attachment(HandoverTemporaryFaultFenceChecks f) throws Exception {
        byte[] bytes = "TEMP preserved original: synthetic only\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(f.directory.resolve("pre-formal-attachment.bin"), bytes, StandardOpenOption.CREATE_NEW);
        return SubmissionJson.parse(f.faultReviewer.put("/handovers/" + f.source.kase + "/attachments/" + java.util.UUID.randomUUID()
            + "?warehouseId=" + f.source.head.warehouse(), bytes, CorrectionAttachmentHttp.sha(bytes), 200, null)).path("data");
    }
}
