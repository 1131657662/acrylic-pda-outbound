package com.acrylic.outbound.inventory;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.sync.CorrectionSyncProbe;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.*;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/** Real separated correction HTTP writers and the database-backed sync publisher share the order version. */
public final class CorrectionOutboxChecks {
    public static void verify(WebApplicationContext app, Path attempt) throws Exception {
        for (String kind : List.of("WEIGHT_CORRECTION", "HEADER_CORRECTION", "DISPATCH_CANCELLATION", "PHYSICAL_RETURN"))
            chain(app, attempt, kind);
    }

    private static void chain(WebApplicationContext app, Path attempt, String kind) throws Exception {
        var f = new CorrectionAccountingHttpFixture(app, attempt, true, "outbox-" + kind);
        if (kind.equals("WEIGHT_CORRECTION")) new CorrectionOutboxVersionChecks(f).verify();
        var create = switch (kind) {
            case "HEADER_CORRECTION" -> f.headerCreate();
            case "DISPATCH_CANCELLATION" -> f.cancellation(0);
            case "PHYSICAL_RETURN" -> f.physicalReturn(0);
            default -> f.create(f.dispatch, 1, f.originalDispatchWeight + 10);
        };
        String document = f.signed(f.maker, create).path("documentId").asText();
        var approve = f.command(document, "APPROVE");
        failedWrite(f, f.checker, approve, "approve");
        f.signed(f.checker, approve); versions(f, "approved");
        byte[] original = receipt(f, approve);
        assertThat(f.checker.command(approve, 200)).containsExactly(original);
        if (kind.equals("HEADER_CORRECTION")) return;
        f.signed(f.checker, f.command(document, "WITHDRAW"));
        var confirm = f.command(document, "WITHDRAW_CONFIRM");
        failedWrite(f, f.maker, confirm, "withdraw");
        f.signed(f.maker, confirm); versions(f, "withdrawn");
        var facts = new CorrectionOutboxFacts(f); var before = facts.snapshot();
        assertThat(f.checker.command(approve, 200)).containsExactly(original);
        assertThat(f.maker.command(confirm, 200)).containsExactly(receipt(f, confirm)); facts.unchanged(before, "replays");
    }

    private static byte[] receipt(CorrectionAccountingHttpFixture f, ObjectNode command) throws Exception {
        return Files.readAllBytes(f.evidence.resolve(command.path("commandId").asText() + ".json"));
    }

    private static void failedWrite(CorrectionAccountingHttpFixture f, SupervisorCorrectionHttp actor,
            ObjectNode command, String stage) throws Exception {
        f.jdbc.execute("CREATE TRIGGER test_correction_outbox_failure BEFORE INSERT ON order_sync_outbox FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected correction outbox failure'");
        try {
            var facts = new CorrectionOutboxFacts(f); var before = facts.snapshot();
            Files.write(f.evidence.resolve(stage + "-write-failure.json"), actor.command(command, 500), StandardOpenOption.CREATE_NEW);
            facts.unchanged(before, stage + "-rollback");
        } finally { f.jdbc.execute("DROP TRIGGER test_correction_outbox_failure"); }
    }

    private static void versions(CorrectionAccountingHttpFixture f, String stage) throws Exception {
        CorrectionSyncProbe.publish(f.context, f.warehouse);
        long order = f.jdbc.queryForObject("SELECT row_version FROM outbound_order WHERE id=UUID_TO_BIN(?)", Long.class, f.order);
        long outbox = f.jdbc.queryForObject("SELECT MAX(entity_version) FROM order_sync_outbox WHERE order_id=UUID_TO_BIN(?) AND recovery_epoch=1", Long.class, f.order);
        long projection = f.jdbc.queryForObject("SELECT row_version FROM sync_order_projection WHERE order_id=UUID_TO_BIN(?) AND recovery_epoch=1", Long.class, f.order);
        var evidence = SubmissionJson.object().put("orderVersion", order).put("outboxVersion", outbox).put("projectionVersion", projection);
        Files.writeString(f.evidence.resolve(stage + "-versions.json"), evidence.toPrettyString(), StandardOpenOption.CREATE_NEW);
        assertThat(order).as("authoritative order and published correction use the same version").isEqualTo(outbox);
        assertThat(projection).isEqualTo(order);
    }
}
