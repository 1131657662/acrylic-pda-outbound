package com.acrylic.outbound.inventory;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/** Synthetic initial dispatch; all later physical return, independent approval and replays use real HTTP. */
public final class ReturnedStockCheckpointScenario {
    private final CorrectionAccountingHttpFixture original;
    private final CorrectionAttachmentHttp maker, checker;
    private final String makerName, checkerName;
    private final List<Step> steps = new ArrayList<>();
    private JsonNode attachment;
    private String document, caseId;

    public ReturnedStockCheckpointScenario(WebApplicationContext context, Path attempt) throws Exception {
        original = new CorrectionAccountingHttpFixture(context, attempt, true, "return-checkpoint");
        makerName = original.makerIdentity.user().username();
        checkerName = original.jdbc.queryForObject("SELECT username FROM app_user u JOIN user_role ur ON ur.user_id=u.id"
            + " JOIN role r ON r.id=ur.role_id JOIN user_warehouse uw ON uw.user_id=u.id"
            + " WHERE r.code='SUPERVISOR' AND uw.warehouse_id=UUID_TO_BIN(?) ORDER BY u.id DESC LIMIT 1", String.class, original.warehouse);
        maker = actor(makerName, original.evidence.resolve("source-maker.log"));
        checker = actor(checkerName, original.evidence.resolve("source-checker.log"));
        stock(original.jdbc, new Stock("DEDUCTED", 1, 0));
    }

    public void complete() throws Exception {
        var physical = original.physicalReturn(0);
        attachment = physical.path("lines").get(0).path("attachments").get(0).deepCopy();
        document = send("physical-create", physical, false).at("/businessDecision/documentId").asText();
        send("physical-approve", original.command(document, "APPROVE"), true);
        stock(original.jdbc, new Stock("DEDUCTED", 2, 0));
        caseId = send("return-discover", command("DISCOVER", maker), false).at("/businessDecision/caseId").asText();
        send("return-submit", command("SUBMIT", maker), false);
        send("return-approve", command("APPROVE", checker), true);
        stock(original.jdbc, new Stock("NOT_DEDUCTED", 2, 1));
        var summary = SubmissionJson.object().put("orderId", original.order).put("dispatchId", original.dispatch)
            .put("documentId", document).put("caseId", caseId).put("makerName", makerName).put("checkerName", checkerName);
        var list = summary.putArray("steps");
        for (var step : steps) list.addObject().put("name", step.name()).put("checker", step.checker())
            .put("receiptBase64", Base64.getEncoder().encodeToString(step.receipt())).set("command", step.command());
        save(original.evidence.resolve("scenario.json"), summary);
    }

    public void verifyMissing(WebApplicationContext context, Path evidence) throws Exception {
        Files.createDirectories(evidence);
        var maker = actor(makerName, evidence.resolve("maker-http.log"));
        var checker = actor(checkerName, evidence.resolve("checker-http.log"));
        var jdbc = context.getBean(JdbcTemplate.class);
        var facts = new ReturnedStockCheckpointEvidence(jdbc, evidence.resolve("facts"));
        saveBytes(evidence.resolve("context-refusal.json"), maker.get(contextPath("DISCOVER"), 409));
        for (String name : List.of("physical-approve", "return-approve")) {
            var step = step(name);
            saveBytes(evidence.resolve(name + "-refusal.json"), checker.command(step.command(), 404));
        }
        String query = "/corrections/commands/" + step("return-approve").command().path("commandId").asText()
            + "?warehouseId=" + original.warehouse + "&commandType=RECONCILE_RETURN_APPROVE";
        saveBytes(evidence.resolve("receipt-refusal.json"), checker.get(query, 404));
        facts.preserved(); stock(jdbc, new Stock("DEDUCTED", 1, 0));
        for (String table : List.of("correction_document", "cycle_reopening", "returned_stock_release"))
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class)).isZero();
    }

    public void verifyRestored(WebApplicationContext context, Path evidence) throws Exception {
        Files.createDirectories(evidence);
        var maker = actor(makerName, evidence.resolve("maker-http.log"));
        var checker = actor(checkerName, evidence.resolve("checker-http.log"));
        var jdbc = context.getBean(JdbcTemplate.class);
        var facts = new ReturnedStockCheckpointEvidence(jdbc, evidence.resolve("facts"));
        for (var step : steps) {
            byte[] actual = (step.checker() ? checker : maker).command(step.command(), 200);
            assertThat(actual).containsExactly(step.receipt());
            SupervisorCorrectionReceipt.verify(actual, original.publicKey);
            saveBytes(evidence.resolve(step.name() + "-replay.json"), actual);
        }
        var changed = step("return-approve").command().deepCopy().put("reason", "ISOLATED changed intent");
        saveBytes(evidence.resolve("changed-refusal.json"), checker.command(changed, 409));
        facts.preserved(); stock(jdbc, new Stock("NOT_DEDUCTED", 2, 1));
    }

    public void verifyDefaultOff(WebApplicationContext context, Path evidence) throws Exception {
        Files.createDirectories(evidence);
        assertThat(context.getBeansOfType(com.acrylic.outbound.correction.CorrectionCommandController.class)).isEmpty();
        var maker = actor(makerName, evidence.resolve("maker-http.log"));
        var checker = actor(checkerName, evidence.resolve("checker-http.log"));
        var jdbc = context.getBean(JdbcTemplate.class);
        var facts = new ReturnedStockCheckpointEvidence(jdbc, evidence.resolve("facts"));
        saveBytes(evidence.resolve("maker-refusal.json"), maker.command(step("return-approve").command(), 404));
        saveBytes(evidence.resolve("checker-refusal.json"), checker.command(step("return-approve").command(), 404));
        saveBytes(evidence.resolve("context-refusal.json"), maker.get(contextPath("DISCOVER"), 404));
        facts.preserved(); stock(jdbc, new Stock("NOT_DEDUCTED", 2, 1));
    }

    private ObjectNode command(String kind, CorrectionAttachmentHttp actor) throws Exception {
        var response = SubmissionJson.parse(actor.get(contextPath(kind), 200));
        var body = SubmissionJson.object().put("commandVersion", 1).put("commandType", "RECONCILE_RETURN_" + kind)
            .put("commandId", UUID.randomUUID().toString()).put("warehouseId", original.warehouse)
            .put("transportRecoveryEpoch", "1").put("reason", "ISOLATED return checkpoint evidence");
        body.set("expected", response.path("expected"));
        if (kind.equals("DISCOVER")) body.put("dispatchId", original.dispatch); else body.put("caseId", caseId);
        if (kind.equals("SUBMIT")) {
            var evidence = body.putObject("evidence").put("observedAt", Instant.now().minusSeconds(10).toString())
                .put("place", "ISOLATED returned bay").put("observation", "RETURNED_IN_WAREHOUSE")
                .put("reference", "ISOLATED original physical return inspection");
            evidence.putArray("attachments").add(attachment.deepCopy());
        }
        return body;
    }

    private String contextPath(String kind) {
        return "/corrections/reconciliation/context?warehouseId=" + original.warehouse + "&commandType=RECONCILE_RETURN_" + kind
            + (kind.equals("DISCOVER") ? "&dispatchId=" + original.dispatch : "&caseId=" + caseId);
    }

    private JsonNode send(String name, ObjectNode command, boolean review) throws Exception {
        byte[] bytes = (review ? checker : maker).command(command, 200);
        var receipt = SupervisorCorrectionReceipt.verify(bytes, original.publicKey);
        saveBytes(original.evidence.resolve(name + "-receipt.json"), bytes);
        save(original.evidence.resolve(name + "-command.json"), command);
        steps.add(new Step(name, command.deepCopy(), bytes.clone(), review));
        return receipt;
    }

    private void stock(JdbcTemplate jdbc, Stock expected) {
        assertThat(jdbc.queryForObject("SELECT book_status FROM inventory_position WHERE bale_id="
            + "(SELECT bale_id FROM dispatch_record WHERE id=UUID_TO_BIN(?))", String.class, original.dispatch)).isEqualTo(expected.status());
        assertThat(jdbc.queryForObject("SELECT dispatch_cycle FROM bale WHERE id="
            + "(SELECT bale_id FROM dispatch_record WHERE id=UUID_TO_BIN(?))", Integer.class, original.dispatch)).isEqualTo(expected.cycle());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM returned_stock_release WHERE original_dispatch_id=UUID_TO_BIN(?)",
            Integer.class, original.dispatch)).isEqualTo(expected.releases());
    }

    private Step step(String name) { return steps.stream().filter(step -> step.name().equals(name)).findFirst().orElseThrow(); }
    private CorrectionAttachmentHttp actor(String name, Path path) throws Exception {
        var actor = new CorrectionAttachmentHttp(name, path);
        actor.get("/auth/csrf", 200); return actor;
    }
    private void save(Path path, JsonNode value) throws Exception { Files.writeString(path, value.toPrettyString(), StandardOpenOption.CREATE_NEW); }
    private void saveBytes(Path path, byte[] value) throws Exception { Files.write(path, value, StandardOpenOption.CREATE_NEW); }
    private record Step(String name, ObjectNode command, byte[] receipt, boolean checker) { }
    private record Stock(String status, int cycle, int releases) { }
}
