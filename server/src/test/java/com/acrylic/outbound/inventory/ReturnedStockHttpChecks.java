package com.acrylic.outbound.inventory;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.concurrent.*;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

public final class ReturnedStockHttpChecks {
    public static void verify(WebApplicationContext context, Path attempt) throws Exception {
        positive(context, attempt); new ReturnedStockRefusalChecks(context, attempt).verify();
        ReturnedStockBrowserSeed.prepare(context,attempt);
    }
    private static void positive(WebApplicationContext context, Path attempt) throws Exception {
        var f = new ReturnedStockHttpFixture(context, attempt, "positive"); var before = f.state();
        f.discover(); f.submit(); assertThat(f.state()).isEqualTo(before);
        assertThat(f.checker.read(f.original.warehouse, f.attachment.path("attachmentId").asText(), true, 200))
            .containsExactly(Files.readAllBytes(f.original.evidence.resolve("physical-original.bin")));
        var command = f.command("APPROVE", true); byte[] receipt = concurrent(f, command);
        var after = f.state(); assertThat(after.path("result")).isEqualTo(before.path("result"));
        assertThat(after.path("original")).isEqualTo(before.path("original"));
        assertThat(after.path("ledgerCount")).isEqualTo(before.path("ledgerCount"));
        assertThat(after.path("ledgerWeight")).isEqualTo(before.path("ledgerWeight"));
        assertThat(after.at("/stock/cycle")).isEqualTo(before.at("/stock/cycle"));
        assertThat(after.at("/stock/bookStatus").asText()).isEqualTo("NOT_DEDUCTED");
        assertThat(after.at("/stock/stockVersion").asLong()).isEqualTo(before.at("/stock/stockVersion").asLong()+1);
        assertThat(f.original.jdbc.queryForObject("SELECT COUNT(*) FROM returned_stock_release WHERE original_dispatch_id=UUID_TO_BIN(?)",
            Integer.class, f.original.dispatch)).isEqualTo(1);
        assertThat(f.checker.get("/corrections/commands/" + command.path("commandId").asText() + "?warehouseId="
            + f.original.warehouse + "&commandType=RECONCILE_RETURN_APPROVE", 200)).containsExactly(receipt);
        var changed = command.deepCopy().put("reason", "changed intent"); f.checker.command(changed, 409);
        Files.writeString(f.original.evidence.resolve("completed.json"), after.toPrettyString());
        com.acrylic.outbound.recovery.ReconciliationRecoveryCoverage.verify(context,f.original.warehouse,f.caseId);
        new ReturnedStockRedispatchHttp(f).verify();
        withdrawalAfterRelease(f);
    }
    private static byte[] concurrent(ReturnedStockHttpFixture f, ObjectNode command) throws Exception {
        f.checker.get("/auth/csrf", 200);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> f.checker.command(command, 200));
            var second = pool.submit(() -> f.checker.command(command, 200));
            byte[] receipt = first.get(40, TimeUnit.SECONDS); assertThat(second.get(40, TimeUnit.SECONDS)).containsExactly(receipt);
            SupervisorCorrectionReceipt.verify(receipt, f.original.publicKey);
            Files.writeString(f.original.evidence.resolve("approval-command.json"), SubmissionJson.canonical(command));
            Files.write(f.original.evidence.resolve("approval-receipt.json"), receipt); return receipt;
        }
    }
    private static void withdrawalAfterRelease(ReturnedStockHttpFixture f) throws Exception {
        f.signed(f.checker, f.original.command(f.document, "WITHDRAW")); var before = f.state();
        var command = f.original.command(f.document, "WITHDRAW_CONFIRM");
        var refused = SubmissionJson.parse(f.maker.command(command, 409));
        assertThat(refused.path("code").asText()).isEqualTo("CORRECTION_LEDGER_CONFLICT");
        assertThat(f.state()).isEqualTo(before);
    }
}
