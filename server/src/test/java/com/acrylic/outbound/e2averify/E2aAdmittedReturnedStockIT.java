package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.ReturnedStockAdmittedInput;
import com.acrylic.outbound.recovery.*;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/** Existing returned-stock fact/attachment/approval commands under real admitted TCP roots. */
@EnabledIfEnvironmentVariable(named = "E2A_CROSS_VERIFY", matches = "true")
class E2aAdmittedReturnedStockIT {
    private static final Set<String> SESSION = Set.of("spring_session", "spring_session_attributes");
    private ReturnedStockAdmittedInput input;
    private SubmissionChunkFaultState state;
    private Path evidence;
    private String caseId;
    private final List<ObjectNode> commands = new ArrayList<>();
    private final List<byte[]> originals = new ArrayList<>();

    @Test void originalReturnAndIndependentApprovalRestoreProjectionAcrossAdmittedRestart() throws Exception {
        var profile = new E2aCrossSourceProfile(); var raw = profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(raw, "34"); E2aV34Seed.seed(raw, profile.attempt.resolve("storage")); profile.migrate(raw);
        E2aCrossSourceHttpSetup.signing(profile.attempt);
        var flags = List.of("--server.servlet.session.cookie.secure=false", "--outbound.corrections.commands.enabled=true",
            "--outbound.corrections.attachments.enabled=true");
        try (var app = E2aCrossSourceHttpIT.start(profile, flags.toArray(String[]::new))) {
            input = ReturnedStockAdmittedInput.prepare((WebApplicationContext) app, profile.attempt);
        }
        evidence = Files.createDirectory(profile.attempt.resolve("returned-stock-admission"));
        state = new SubmissionChunkFaultState(raw, profile.attempt.resolve("storage"));
        var before = state.database(SESSION); save("source-before", before); save("accounting-before", input.accounting(raw));
        var p5 = new RecoveryRetirementApplicationFixture(profile.attempt);
        var options = new ArrayList<String>(List.of(p5.options())); options.addAll(flags);
        try (var app = E2aCrossSourceHttpIT.start(profile, options.toArray(String[]::new))) {
            try { consume(); } finally { drained(app); }
        }
        try (var app = E2aCrossSourceHttpIT.start(profile, options.toArray(String[]::new))) {
            try { replay("restart"); } finally { drained(app); }
        }
        sourceUnchanged(before); p5.nextProcessStarts();
        Files.writeString(profile.attempt.resolve("test-completed.marker"), "Admitted returned-stock and original-key restart passed");
    }

    private void consume() throws Exception {
        var maker = wire("maker", input.maker()); var checker = wire("checker", input.checker());
        var discover = prepared(maker, "RECONCILE_RETURN_DISCOVER");
        unchanged("discovery-denied", () -> checker.command(discover, 403));
        caseId = signed(maker, discover, "discovered").at("/businessDecision/caseId").asText();
        assertThat(caseId).isNotBlank();
        var submit = prepared(maker, "RECONCILE_RETURN_SUBMIT");
        unchanged("submit-denied", () -> checker.command(submit, 403));
        assertThat(signed(maker, submit, "submitted").at("/businessDecision/status").asText()).isEqualTo("REVIEW_READY");
        var approve = prepared(checker, "RECONCILE_RETURN_APPROVE");
        unchanged("approval-denied", () -> maker.command(approve, 403));
        var stale = approve.deepCopy().put("commandId", UUID.randomUUID().toString());
        ((ObjectNode) stale.path("expected")).put("factsSha256", "0".repeat(64));
        unchanged("facts-mismatch", () -> checker.command(stale, 409));
        failedApproval(checker, approve);
        assertThat(signed(checker, approve, "approved").at("/businessDecision/status").asText()).isEqualTo("RESOLVED");
        assertPosted(); replays(maker, checker, "original-keys");
    }

    private ObjectNode prepared(CorrectionApprovalAdmittedHttp actor, String kind) throws Exception {
        final JsonNode[] result = new JsonNode[1];
        unchanged(kind + "-context", () -> result[0] = SubmissionJson.parse(actor.get(input.context(kind, caseId), 200)));
        save(kind + "-context", result[0]); return input.command(kind, caseId, result[0]);
    }

    private JsonNode signed(CorrectionApprovalAdmittedHttp actor, ObjectNode command, String label) throws Exception {
        byte[] bytes = actor.command(command, 200); var receipt = input.verify(bytes);
        commands.add(command); originals.add(bytes); save(label + "-command", command); save(label + "-receipt", receipt);
        assertThat(SubmissionJson.parse(bytes).path("commandSha256")).isEqualTo(receipt.path("commandSha256")); return receipt;
    }

    private void failedApproval(CorrectionApprovalAdmittedHttp actor, ObjectNode command) throws Exception {
        state.raw.execute("CREATE TRIGGER test_admitted_returned_failure BEFORE INSERT ON operation_event FOR EACH ROW SIGNAL SQLSTATE '42000' SET MESSAGE_TEXT='injected admitted returned stock audit failure'");
        try { unchanged("tail-sql-failure", () -> actor.command(command, 500)); }
        finally { state.raw.execute("DROP TRIGGER test_admitted_returned_failure"); }
    }

    private void assertPosted() throws Exception {
        var current = input.accounting(state.raw); save("accounting-after", current);
        var before = SubmissionJson.parse(Files.readAllBytes(evidence.resolve("accounting-before.json")));
        assertThat(current.path("result")).isEqualTo(before.path("result"));
        assertThat(current.path("ledger")).isNotEmpty().isEqualTo(before.path("ledger"));
        var stock = (ObjectNode) current.path("stock").deepCopy(); var old = (ObjectNode) before.path("stock").deepCopy();
        assertThat(old.path("status").asText()).isEqualTo("DEDUCTED");
        assertThat(stock.path("status").asText()).isEqualTo("NOT_DEDUCTED");
        assertThat(stock.path("version").asLong()).isEqualTo(old.path("version").asLong() + 1);
        assertThat(stock.path("cycle").asInt()).isEqualTo(2);
        stock.remove(List.of("status", "version")); old.remove(List.of("status", "version")); assertThat(stock).isEqualTo(old);
        assertThat(state.raw.queryForObject("SELECT COUNT(*) FROM returned_stock_release WHERE original_dispatch_id=UUID_TO_BIN(?)",
            Integer.class, input.dispatch())).isEqualTo(1);
    }

    private void replay(String label) throws Exception {
        replays(wire(label + "-maker", input.maker()), wire(label + "-checker", input.checker()), label + "-original-keys");
    }

    private void replays(CorrectionApprovalAdmittedHttp maker, CorrectionApprovalAdmittedHttp checker, String label) throws Exception {
        unchanged(label, () -> {
            for (int i = 0; i < commands.size(); i++) {
                var actor = i == 2 ? checker : maker;
                assertThat(actor.command(commands.get(i), 200)).isEqualTo(originals.get(i));
                var changed = commands.get(i).deepCopy().put("reason", "altered under original key");
                assertThat(SubmissionJson.parse(actor.command(changed, 409)).path("code").asText()).isEqualTo("IDEMPOTENCY_CONTENT_MISMATCH");
            }
        });
    }

    private void sourceUnchanged(JsonNode before) throws Exception {
        var after = state.database(SESSION); save("source-after", after);
        for (String table : List.of("submission", "submission_payload", "dispatch_record", "inventory_ledger",
                "order_result_version", "bale_commitment", "correction_effect", "correction_ledger",
                "cycle_reopening", "correction_document", "correction_line", "correction_attachment")) {
            assertThat(after.path(table)).isEqualTo(before.path(table));
        }
    }

    private void unchanged(String label, Checked action) throws Exception {
        var db = state.database(SESSION); var files = state.files(); save(label + "-database-before", db); save(label + "-files-before", files);
        try { action.run(); }
        finally {
            var after = state.database(SESSION); var afterFiles = state.files();
            save(label + "-database-after", after); save(label + "-files-after", afterFiles);
            assertThat(after).isEqualTo(db); assertThat(afterFiles).isEqualTo(files);
        }
    }

    private void drained(ConfigurableApplicationContext app) throws Exception {
        org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
            assertThat(app.getBean(RecoveryOperationRegistry.class).liveRoots()).isZero());
        app.getBean(RecoveryFenceAuthority.class).checkEffect();
    }

    private CorrectionApprovalAdmittedHttp wire(String label, String username) throws Exception {
        return new CorrectionApprovalAdmittedHttp(evidence.resolve(label), username);
    }

    private void save(String name, JsonNode value) throws Exception {
        Files.writeString(evidence.resolve(name + ".json"), value.toPrettyString(), StandardOpenOption.CREATE_NEW);
    }

    private interface Checked { void run() throws Exception; }
}
