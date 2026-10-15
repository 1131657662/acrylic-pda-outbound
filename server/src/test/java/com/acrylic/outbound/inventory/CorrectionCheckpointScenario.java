package com.acrylic.outbound.inventory;

import com.acrylic.outbound.e2averify.E2aSnapshotRows;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.*;

/** Synthetic initial confirmation; correction, attachment and replay use the actual HTTP routes. */
public final class CorrectionCheckpointScenario {
    private final CorrectionAccountingHttpFixture fixture;
    private final List<Step> steps = new ArrayList<>();
    private final JsonNode original;
    private String document, checkerName;

    public CorrectionCheckpointScenario(WebApplicationContext context, Path attempt) throws Exception {
        fixture = new CorrectionAccountingHttpFixture(context, attempt, true, "checkpoint");
        original = immutable(fixture.jdbc);
        assertThat(fixture.postings()).isZero();
    }

    public void create() throws Exception {
        var receipt = send("create", fixture.maker, fixture.cancellation(0), false);
        document = receipt.at("/businessDecision/documentId").asText();
        assertThat(receipt.at("/businessDecision/status").asText()).isEqualTo("SUBMITTED");
        assertThat(fixture.postings()).isZero();
        assertThat(fixture.jdbc.queryForObject("SELECT COUNT(*) FROM cycle_reopening", Integer.class)).isZero();
    }

    public void approve() throws Exception {
        var receipt = send("approve", fixture.checker, fixture.command(document, "APPROVE"), true);
        assertThat(receipt.at("/businessDecision/status").asText()).isEqualTo("EFFECTIVE");
        assertThat(fixture.correctionWeight()).isEqualTo(fixture.originalDispatchWeight);
        checkerName = fixture.jdbc.queryForObject("SELECT username FROM app_user WHERE id=UUID_TO_BIN(?)",
            String.class, receipt.at("/actor/principalId").asText());
    }

    public void withdraw() throws Exception {
        var receipt = send("withdraw", fixture.checker, fixture.command(document, "WITHDRAW"), true);
        assertThat(receipt.at("/businessDecision/status").asText()).isEqualTo("WITHDRAW_PENDING");
        assertThat(fixture.correctionWeight()).isEqualTo(fixture.originalDispatchWeight);
    }

    public void confirm() throws Exception {
        var receipt = send("confirm", fixture.maker, fixture.command(document, "WITHDRAW_CONFIRM"), false);
        assertThat(receipt.at("/businessDecision/status").asText()).isEqualTo("WITHDRAWN");
        assertThat(fixture.correctionWeight()).isZero();
        assertThat(fixture.result().path("weightG").asLong()).isEqualTo(fixture.originalWeight);
        assertThat(immutable(fixture.jdbc)).isEqualTo(original);
    }

    public void save(Path path) throws Exception {
        var result = SubmissionJson.object().put("orderId", fixture.order).put("dispatchId", fixture.dispatch)
            .put("documentId", document).put("makerName", fixture.makerIdentity.user().username()).put("checkerName", checkerName);
        var array = result.putArray("steps");
        for (var step : steps) array.addObject().put("name", step.name()).put("checker", step.checker())
            .put("receiptBase64", Base64.getEncoder().encodeToString(step.receipt())).set("command", step.command());
        result.set("original", original);
        Files.writeString(path, result.toPrettyString(), StandardOpenOption.CREATE_NEW);
    }

    public void verifyRestored(WebApplicationContext context, Path evidence) throws Exception {
        var jdbc = context.getBean(JdbcTemplate.class);
        var before = accounting(jdbc);
        var maker = new SupervisorCorrectionHttp(fixture.makerIdentity.user().username());
        var checker = new SupervisorCorrectionHttp(checkerName);
        for (var step : steps) {
            byte[] actual = (step.checker() ? checker : maker).command(step.command(), 200);
            assertThat(actual).containsExactly(step.receipt());
            SupervisorCorrectionReceipt.verify(actual, fixture.publicKey);
            Files.write(evidence.resolveSibling(step.name() + "-replay.json"), actual, StandardOpenOption.CREATE_NEW);
        }
        var changed = steps.getFirst().command().deepCopy().put("reason", "same command id with different text");
        byte[] refused = maker.command(changed, 409);
        Files.write(evidence.resolveSibling("changed-command.json"), refused, StandardOpenOption.CREATE_NEW);
        assertThat(accounting(jdbc)).isEqualTo(before);
        assertThat(immutable(jdbc)).isEqualTo(original);
        Files.writeString(evidence, SubmissionJson.object().put("originalReceipts", steps.size())
            .put("changedCommandStatus", 409).set("accounting", before).toPrettyString(), StandardOpenOption.CREATE_NEW);
    }

    private JsonNode send(String name, SupervisorCorrectionHttp actor, ObjectNode body, boolean checker) throws Exception {
        byte[] bytes = actor.command(body, 200);
        var receipt = SupervisorCorrectionReceipt.verify(bytes, fixture.publicKey);
        Files.write(fixture.evidence.resolve(name + "-original.json"), bytes, StandardOpenOption.CREATE_NEW);
        steps.add(new Step(name, body.deepCopy(), bytes.clone(), checker));
        return receipt;
    }

    private JsonNode immutable(JdbcTemplate jdbc) {
        var all = new E2aSnapshotRows(jdbc).capture(); var result = SubmissionJson.object();
        for (String name : List.of("dispatch_record", "inventory_ledger")) result.set(name, all.path(name));
        return result;
    }

    private JsonNode accounting(JdbcTemplate jdbc) {
        var all = new E2aSnapshotRows(jdbc).capture(); var result = SubmissionJson.object();
        for (String name : List.of("dispatch_record", "inventory_ledger", "correction_document", "correction_line",
                "correction_ledger", "correction_command_receipt", "correction_effect", "cycle_reopening",
                "bale_commitment", "correction_occupancy_restoration", "order_result_version", "order_effective_scan")) {
            assertThat(all.has(name)).as("required accounting table %s", name).isTrue(); result.set(name, all.path(name));
        }
        return result;
    }

    private record Step(String name, ObjectNode command, byte[] receipt, boolean checker) { }
}
