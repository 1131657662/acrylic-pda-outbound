package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/** Consumes product GET contexts through CREATE/APPROVE and proves reads preserve business rows. */
public final class CorrectionReadChecks {
    private final SupervisorCorrectionFixture f;
    private final CorrectionReadHttp maker, checker, other;
    private final IdentityFixture outsider;
    private final Path attempt, http;

    public CorrectionReadChecks(WebApplicationContext app, Path attempt) throws Exception {
        this.attempt = attempt; http = Files.createDirectory(attempt.resolve("read-http"));
        f = new SupervisorCorrectionFixture(app);
        outsider = IdentityFixture.create(f.jdbc, app.getBean(PasswordEncoder.class), "WAREHOUSE_ADMIN");
        f.jdbc.update("INSERT INTO user_warehouse VALUES(?,UUID_TO_BIN(?))", Ids.bytes(outsider.user().id()), f.warehouse);
        maker = new CorrectionReadHttp(f.source.operator().username(), http, "maker");
        checker = new CorrectionReadHttp(f.checker.user().username(), http, "checker");
        other = new CorrectionReadHttp(outsider.user().username(), http, "other");
    }

    public void verify() throws Exception {
        var original = f.immutableSource(); var before = snapshot(f.jdbc);
        var context = SubmissionJson.parse(maker.get(orderPath(), 200));
        checker.get(orderPath(), 403);
        other.get("/corrections/orders/" + f.order + "/context?warehouseId=" + outsider.warehouse(), 404);
        assertThat(snapshot(f.jdbc)).isEqualTo(before);
        var create = f.create(); create.set("expected", context.path("expected"));
        byte[] created = maker.command(create); var receipt = verifyReceipt(created);
        String document = receipt.path("documentId").asText();
        before = snapshot(f.jdbc);
        var review = SubmissionJson.parse(checker.get(documentPath(document, "APPROVE"), 200));
        assertThat(review.path("originalCommand")).isEqualTo(create);
        verifyReads(create, created, document);
        assertThat(snapshot(f.jdbc)).isEqualTo(before);
        var approve = f.approve(document); approve.set("expected", review.path("expected"));
        byte[] approved = checker.command(approve); verifyReceipt(approved);
        before = snapshot(f.jdbc);
        assertThat(checker.get(receiptPath(approve), 200)).isEqualTo(approved);
        maker.get(receiptPath(approve), 403);
        assertThat(maker.get(receiptPath(create), 200)).isEqualTo(created);
        assertThat(snapshot(f.jdbc)).isEqualTo(before);
        assertThat(f.immutableSource()).isEqualTo(original);
        persist(create, created, approved, approve);
    }

    private void verifyReads(ObjectNode create, byte[] created, String document) throws Exception {
        assertThat(maker.get(receiptPath(create), 200)).isEqualTo(created);
        other.get(receiptPath(create), 404); checker.get(receiptPath(create), 403);
        maker.get(receiptPath(create).replace("commandType=CREATE", "commandType=WITHDRAW_CONFIRM"), 404);
        other.get(receiptPath(create).replace(f.warehouse, outsider.warehouse().toString()), 404);
        maker.get("/corrections/commands/" + UUID.randomUUID() + "?warehouseId=" + f.warehouse + "&commandType=CREATE", 404);
        maker.get(documentPath(document, "APPROVE"), 403);
        other.get(documentPath(document, "WITHDRAW_CONFIRM"), 404);
        assertThat(SubmissionJson.parse(maker.get(documentPath(document, "WITHDRAW_CONFIRM"), 200)).path("originalCommand")).isEqualTo(create);
        checker.get(documentPath(UUID.randomUUID().toString(), "APPROVE"), 404);
    }

    private void persist(ObjectNode create, byte[] created, byte[] approved, ObjectNode approve) throws Exception {
        Files.write(attempt.resolve("read-create-original.json"), created);
        Files.write(attempt.resolve("read-approve-original.json"), approved);
        var facts = SubmissionJson.object().put("maker", f.source.operator().username()).put("checker", f.checker.user().username())
            .put("createPath", receiptPath(create)).put("approvePath", receiptPath(approve));
        Files.writeString(attempt.resolve("read-restart.json"), facts.toString());
        Files.writeString(attempt.resolve("real-records.tsv"), f.warehouse + "\t" + f.order + "\t" + create.path("commandId").asText() + "\n");
        Files.writeString(attempt.resolve("read-business-snapshot.json"), new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(snapshot(f.jdbc)));
    }

    public static void restart(WebApplicationContext app, Path attempt) throws Exception {
        var data = SubmissionJson.parse(Files.readAllBytes(attempt.resolve("read-restart.json")));
        var maker = new CorrectionReadHttp(data.path("maker").asText(), attempt.resolve("read-http"), "restart-maker");
        var checker = new CorrectionReadHttp(data.path("checker").asText(), attempt.resolve("read-http"), "restart-checker");
        var jdbc = app.getBean(JdbcTemplate.class); var before = snapshot(jdbc);
        assertThat(maker.get(data.path("createPath").asText(), 200)).isEqualTo(Files.readAllBytes(attempt.resolve("read-create-original.json")));
        assertThat(checker.get(data.path("approvePath").asText(), 200)).isEqualTo(Files.readAllBytes(attempt.resolve("read-approve-original.json")));
        assertThat(snapshot(jdbc)).isEqualTo(before);
        Files.writeString(attempt.resolve("read-restart-completed.txt"), "Original CREATE/APPROVE bytes recovered by GET; business rows unchanged");
    }

    private JsonNode verifyReceipt(byte[] bytes) throws Exception {
        return SupervisorCorrectionReceipt.verify(bytes, attempt.resolve("private/server-public.der"));
    }

    private String orderPath() { return "/corrections/orders/" + f.order + "/context?warehouseId=" + f.warehouse; }
    private String documentPath(String document, String kind) {
        return "/corrections/" + document + "/context?warehouseId=" + f.warehouse + "&commandType=" + kind;
    }
    private String receiptPath(JsonNode command) {
        return "/corrections/commands/" + command.path("commandId").asText() + "?warehouseId=" + f.warehouse
            + "&commandType=" + command.path("commandType").asText();
    }

    private static Map<String, List<String>> snapshot(JdbcTemplate jdbc) {
        var result = new TreeMap<String, List<String>>();
        for (String table : List.of("correction_document", "correction_line", "correction_command_receipt", "correction_effect",
                "correction_ledger", "api_idempotency_request", "operation_event", "outbound_order", "order_result_version",
                "order_effective_scan", "order_effective_line", "order_current_result", "order_sync_outbox", "inventory_ledger", "bale_commitment")) {
            result.put(table, jdbc.queryForList("SELECT * FROM " + table).stream().map(row -> {
                var encoded = new TreeMap<String, String>();
                row.forEach((key, value) -> encoded.put(key, value instanceof byte[] bytes ? "bytes:" + Base64.getEncoder().encodeToString(bytes)
                    : value == null ? "null" : value.getClass().getName() + ":" + value));
                return encoded.toString();
            }).sorted().toList());
        }
        return result;
    }
}
