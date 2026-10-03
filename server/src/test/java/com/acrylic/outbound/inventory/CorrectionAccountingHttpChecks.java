package com.acrylic.outbound.inventory;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.sync.SyncOrderStatus;
import java.nio.file.*;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/** Actual original debits, weight signs, per-entry zeroing and repeated withdrawal over real HTTP. */
public final class CorrectionAccountingHttpChecks {
    public static void verify(WebApplicationContext context, Path attempt) throws Exception {
        chain(context, attempt, true); chain(context, attempt, false);
        cycleReopening(context, attempt, "DISPATCH_CANCELLATION");
        cycleReopening(context, attempt, "PHYSICAL_RETURN");
        new CorrectionAccountingConflictChecks(context, attempt).verify();
    }

    private static void cycleReopening(WebApplicationContext context, Path attempt, String type) throws Exception {
        String evidenceName = type.equals("PHYSICAL_RETURN") ? "physical-return-cycle-reopened" : "cycle-reopened";
        var f = new CorrectionAccountingHttpFixture(context, attempt, true, evidenceName);
        String bale = f.facts(f.dispatch).path("baleId").asText();
        long before = f.jdbc.queryForObject("SELECT dispatch_cycle FROM bale WHERE id=UUID_TO_BIN(?)", Long.class, bale);
        assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM bale_commitment WHERE bale_id=UUID_TO_BIN(?)"
            + " AND order_id=UUID_TO_BIN(?) AND state='DISPATCHED' AND dispatch_cycle=?", Integer.class, bale, f.order, before)).isEqualTo(1);
        var created = f.signed(f.maker, type.equals("PHYSICAL_RETURN") ? f.physicalReturn(0) : f.cancellation(0));
        String document = created.path("documentId").asText();
        var approved = f.signed(f.checker, f.command(document, "APPROVE"));
        long resultVersion = approved.at("/resultVersion").asLong();
        assertThat(resultVersion).isGreaterThan(1);
        assertThat(f.jdbc.queryForObject("SELECT dispatch_cycle FROM bale WHERE id=UUID_TO_BIN(?)", Long.class, bale))
            .isEqualTo(before + 1);
        assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM bale_commitment WHERE bale_id=UUID_TO_BIN(?)", Integer.class, bale)).isZero();
        assertThat(f.jdbc.queryForObject("SELECT book_status FROM inventory_position WHERE bale_id=UUID_TO_BIN(?)", String.class, bale))
            .isEqualTo("DEDUCTED");
        assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM cycle_reopening WHERE original_dispatch_id=UUID_TO_BIN(?)",
            Integer.class, f.dispatch)).isEqualTo(1);
        assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM order_effective_scan WHERE order_id=UUID_TO_BIN(?)"
            + " AND result_version=? AND dispatch_id=UUID_TO_BIN(?) AND is_effective=0 AND effective_weight_g=0",
            Integer.class, f.order, resultVersion, f.dispatch)).isEqualTo(1);
        f.withdraw(document);
        assertThat(f.jdbc.queryForObject("SELECT dispatch_cycle FROM bale WHERE id=UUID_TO_BIN(?)", Long.class, bale)).isEqualTo(before + 1);
        assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM bale_commitment WHERE bale_id=UUID_TO_BIN(?)"
            + " AND order_id=UUID_TO_BIN(?) AND state='DISPATCHED' AND dispatch_cycle=?", Integer.class, bale, f.order, before + 1)).isEqualTo(1);
        assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM correction_occupancy_restoration r"
            + " JOIN correction_command_receipt c ON c.id=r.withdrawal_receipt_id WHERE r.original_dispatch_id=UUID_TO_BIN(?)"
            + " AND r.withdrawal_document_id=UUID_TO_BIN(?) AND r.correction_line_id=(SELECT id FROM correction_line"
            + " WHERE document_id=UUID_TO_BIN(?) LIMIT 1)", Integer.class, f.dispatch, document, document)).isEqualTo(1);
        assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM correction_occupancy_restoration WHERE original_dispatch_id=UUID_TO_BIN(?)",
            Integer.class, f.dispatch)).isEqualTo(1);
        String duplicate = f.signed(f.maker, type.equals("PHYSICAL_RETURN") ? f.physicalReturn(500) : f.cancellation(500)).path("documentId").asText();
        byte[] refused = f.checker.command(f.command(duplicate, "APPROVE"), 409);
        assertThat(SubmissionJson.parse(refused).path("code").asText()).isEqualTo("CYCLE_REOPENING_EXISTS");
        assertThat(f.jdbc.queryForObject("SELECT dispatch_cycle FROM bale WHERE id=UUID_TO_BIN(?)", Long.class, bale)).isEqualTo(before + 1);
        assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM cycle_reopening WHERE original_dispatch_id=UUID_TO_BIN(?)",
            Integer.class, f.dispatch)).isEqualTo(1);
        assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM order_sync_outbox WHERE order_id=UUID_TO_BIN(?) AND event_type='CYCLE_REOPENED'",
            Integer.class, f.order)).isEqualTo(1);
        Files.write(f.evidence.resolve("cycle-reopening-completed.json"), refused);
    }

    private static void chain(WebApplicationContext context, Path attempt, boolean deduct) throws Exception {
        var f = new CorrectionAccountingHttpFixture(context, attempt, deduct, deduct ? "deducted" : "not-deducted");
        var original = f.original(); long weight = f.originalDispatchWeight;
        String increase = f.approve(1, weight + 20);
        assertThat(f.correctionWeight()).isEqualTo(deduct ? -20 : 0);
        String decrease = f.approve(1, weight + 5);
        assertThat(f.correctionWeight()).isEqualTo(deduct ? -5 : 0);
        String zero = f.approve(0, 0);
        assertThat(f.correctionWeight()).isEqualTo(deduct ? weight : 0);
        assertThat(f.result().path("count").asLong()).isEqualTo(1);
        f.withdraw(zero); assertThat(f.correctionWeight()).isEqualTo(deduct ? -5 : 0);
        assertThat(f.facts(f.dispatch).path("weightG").asLong()).isEqualTo(weight + 5);
        String again = f.approve(0, 0); f.withdraw(again);
        assertThat(f.correctionWeight()).isEqualTo(deduct ? -5 : 0);
        f.withdraw(decrease); assertThat(f.correctionWeight()).isEqualTo(deduct ? -20 : 0);
        f.withdraw(increase); assertThat(f.correctionWeight()).isZero();
        assertThat(f.result().path("count").asLong()).isEqualTo(2);
        assertThat(f.result().path("weightG").asLong()).isEqualTo(f.originalWeight);
        assertThat(f.original()).isEqualTo(original);
        if (!deduct) assertThat(f.postings()).isZero();
        sync(f); CorrectionAccountingExportChecks.verify(f);
        Files.writeString(f.evidence.resolve("completed.json"), f.result().toPrettyString());
    }

    private static void sync(CorrectionAccountingHttpFixture f) {
        var rows = f.jdbc.queryForList("SELECT event_type,CAST(payload AS CHAR) payload FROM order_sync_outbox"
            + " WHERE order_id=UUID_TO_BIN(?) AND event_type IN ('CORRECTION_EFFECTIVE','CORRECTION_WITHDRAWN')", f.order);
        assertThat(rows).hasSize(8);
        var consumer = f.context.getBean(SyncOrderStatus.class);
        for (var row : rows) {
            String payload = row.get("payload").toString();
            assertThat(SubmissionJson.parse(payload).path("status").asText()).isEqualTo("COMPLETED");
            assertThat(consumer.from(row.get("event_type").toString(), payload)).isEqualTo("COMPLETED");
        }
    }
}
