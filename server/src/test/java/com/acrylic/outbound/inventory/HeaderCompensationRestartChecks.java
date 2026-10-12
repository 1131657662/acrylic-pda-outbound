package com.acrylic.outbound.inventory;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/** Consumes committed originals in a later JVM; it neither reseeds nor generates replacement identities. */
public final class HeaderCompensationRestartChecks {
    private HeaderCompensationRestartChecks() { }

    public static void verify(WebApplicationContext context, Path attempt) throws Exception {
        var jdbc = context.getBean(JdbcTemplate.class);
        for (String name : new String[]{"header-positive-true", "header-positive-false"}) {
            var evidence = attempt.resolve("accounting/" + name);
            var input = SubmissionJson.parse(Files.readString(evidence.resolve("restart-input.json")));
            byte[] original = Files.readAllBytes(evidence.resolve("withdrawal.json"));
            assertThat(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(original)))
                .isEqualTo(input.path("responseSha256").asText());
            var receipt = SupervisorCorrectionReceipt.verify(original, attempt.resolve("private/server-public.der"));
            var wire = new CorrectionReadHttp(input.path("username").asText(), evidence, "restart");
            var before = facts(jdbc, input.path("orderId").asText());
            byte[] read = wire.get("/corrections/commands/" + input.at("/command/commandId").asText()
                + "?warehouseId=" + input.path("warehouseId").asText() + "&commandType=WITHDRAW_CONFIRM", 200);
            assertThat(read).containsExactly(original);
            assertThat(wire.command(input.path("command"))).containsExactly(original);
            assertThat(facts(jdbc, input.path("orderId").asText())).isEqualTo(before);
            assertThat(receipt.at("/businessDecision/headerRestoration/restoredFields")).hasSize(1);
            Files.writeString(evidence.resolve("restart-business-facts.json"), before.toPrettyString());
        }
    }

    private static JsonNode facts(JdbcTemplate jdbc, String order) {
        var result = SubmissionJson.object();
        for (String table : new String[]{"correction_document", "correction_command_receipt", "order_result_version",
            "order_effective_scan", "inventory_ledger", "correction_ledger", "correction_effect", "bale_commitment",
            "cycle_reopening", "order_sync_outbox", "operation_event", "api_idempotency_request"})
            result.put(table, jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class));
        result.put("version", jdbc.queryForObject("SELECT current_result_version FROM outbound_order WHERE id=UUID_TO_BIN(?)", Long.class, order));
        result.put("header", jdbc.queryForObject("SELECT CAST(header_snapshot AS CHAR) FROM order_result_version"
            + " WHERE order_id=UUID_TO_BIN(?) ORDER BY result_version DESC LIMIT 1", String.class, order));
        return result;
    }
}
