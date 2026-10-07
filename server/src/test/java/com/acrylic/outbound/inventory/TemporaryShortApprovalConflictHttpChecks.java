package com.acrylic.outbound.inventory;

import com.acrylic.outbound.submissions.SubmissionJson;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/** Isolated fault injection tests a short approval's effect without authorizing stock resolution. */
public final class TemporaryShortApprovalConflictHttpChecks {
    private TemporaryShortApprovalConflictHttpChecks() { }

    public static void verify(WebApplicationContext context, Path attempt) throws Exception {
        for (String conflict : List.of("INVENTORY_UNAVAILABLE", "BALE_ALREADY_COMMITTED")) {
            var directory = Files.createDirectory(attempt.resolve(conflict));
            var fixture = new TemporaryConfirmationFixture(context, true);
            inject(fixture, conflict);
            verifyConflict(fixture, directory, conflict);
        }
    }

    private static void inject(TemporaryConfirmationFixture fixture, String conflict) throws Exception {
        String bale = fixture.source.body.at("/scans/0/baleId").asText();
        if (conflict.equals("INVENTORY_UNAVAILABLE")) {
            assertThat(fixture.jdbc.update("UPDATE inventory_position SET blocked_reason=? WHERE bale_id=UUID_TO_BIN(?)",
                "D05_ISOLATED_HOLD", bale)).isEqualTo(1);
            return;
        }
        var foreign = new TemporarySubmissionFixture(new SubmissionFixture(fixture.source.f));
        fixture.jdbc.update("UPDATE outbound_order SET created_at=created_at-INTERVAL 30 DAY WHERE id=UUID_TO_BIN(?)", foreign.f.order);
        var received = foreign.submit();
        byte[] scope = fixture.jdbc.queryForObject("SELECT scope_id FROM submission WHERE id=UUID_TO_BIN(?)", byte[].class, foreign.id());
        assertThat(fixture.jdbc.update("UPDATE bale_commitment SET submission_id=UUID_TO_BIN(?),source_scope_id=?,"
            + "order_id=UUID_TO_BIN(?) WHERE bale_id=UUID_TO_BIN(?)", foreign.id(), scope, received.path("orderId").asText(), bale)).isEqualTo(1);
    }

    private static void verifyConflict(TemporaryConfirmationFixture fixture, Path directory, String conflict) throws Exception {
        var supervisor = fixture.supervisor();
        var http = new TemporaryDispositionHttp(directory.resolve("http"));
        http.login(supervisor.username());
        var sql = new TemporaryDispositionSqlChecks(fixture.jdbc, directory.resolve("sql"));
        var beforeApproval = sql.snapshot("before-approval");
        var approval = fixture.command("SHORT_APPROVE", supervisor);
        byte[] receipt = http.command(approval, 200);
        var approved = sql.snapshot("after-approval");
        for (String table : List.of("bale_commitment", "inventory_position", "inventory_ledger", "order_result_version",
            "submission", "submission_payload", "submitted_capture", "submitted_event"))
            assertThat(approved.get(table)).as(table).isEqualTo(beforeApproval.get(table));
        var review = http.data("/temporary-reviews/" + fixture.source.id() + "?warehouseId=" + fixture.source.f.web.identity().warehouse());
        assertThat(review.path("currentIssues").toString()).contains("SHORT_SHIPMENT_APPROVAL_REQUIRED", conflict);
        assertThat(review.path("writeConfirmationAvailable").asBoolean()).isFalse();
        Files.writeString(directory.resolve("review.json"), SubmissionJson.canonical(review));
        var command = fixture.command("NORMAL_CONFIRM", supervisor);
        assertThat(SubmissionJson.parse(http.command(command, 409)).path("code").asText()).isEqualTo("TEMPORARY_STOCK_CONFLICT");
        assertThat(sql.snapshot("after-refusal")).isEqualTo(approved);
        assertThat(http.command(approval, 200)).isEqualTo(receipt);
        assertThat(sql.snapshot("after-replay")).isEqualTo(approved);
    }
}
