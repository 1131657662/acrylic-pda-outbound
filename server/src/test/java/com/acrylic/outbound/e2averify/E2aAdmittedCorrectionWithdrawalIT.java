package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.CorrectionWithdrawalAdmittedInput;
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

/** Existing approver/requester withdrawal consumed through admitted TCP roots without widening gates. */
@EnabledIfEnvironmentVariable(named = "E2A_CROSS_VERIFY", matches = "true")
class E2aAdmittedCorrectionWithdrawalIT {
    private static final Set<String> SESSION = Set.of("spring_session", "spring_session_attributes");
    private SubmissionChunkFaultState state;
    private CorrectionWithdrawalAdmittedInput input;
    private Path evidence, publicKey;
    private ObjectNode withdraw, confirm;
    private String document;
    private byte[] pending, confirmed;
    private JsonNode forward;

    @Test void originalApproverAndMakerReverseExactlyOnceAcrossAdmittedContextRestart() throws Exception {
        var profile = new E2aCrossSourceProfile(); var raw = profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(raw, "34"); E2aV34Seed.seed(raw, profile.attempt.resolve("storage")); profile.migrate(raw);
        E2aCrossSourceHttpSetup.signing(profile.attempt);
        try (var setup = E2aCrossSourceHttpIT.start(profile)) {
            input = CorrectionWithdrawalAdmittedInput.prepare((WebApplicationContext) setup);
        }
        evidence = Files.createDirectory(profile.attempt.resolve("withdrawal-admission"));
        publicKey = profile.attempt.resolve("private/server-public.der"); save("input", input.source().evidence());
        state = new SubmissionChunkFaultState(raw, profile.attempt.resolve("storage"));
        var before = state.database(SESSION); save("source-before", before);
        var p5 = new RecoveryRetirementApplicationFixture(profile.attempt);
        var options = new ArrayList<String>(List.of(p5.options()));
        options.addAll(List.of("--server.servlet.session.cookie.secure=false", "--outbound.corrections.commands.enabled=true"));
        try (var app = E2aCrossSourceHttpIT.start(profile, options.toArray(String[]::new))) {
            try { consume(); } finally { drained(app); }
        }
        try (var app = E2aCrossSourceHttpIT.start(profile, options.toArray(String[]::new))) {
            try { replay("restart"); } finally { drained(app); }
        }
        sourceUnchanged(before); p5.nextProcessStarts();
        Files.writeString(profile.attempt.resolve("test-completed.marker"), "Admitted two-phase withdrawal and P5 successor passed");
    }

    private void consume() throws Exception {
        var maker = wire("maker", input.source().maker().username());
        var checker = wire("checker", input.source().checker().username());
        var otherMaker = wire("other-maker", input.otherMaker().username());
        var otherChecker = wire("other-checker", input.otherChecker().username());
        var created = signed(maker.command(input.source().create(), 200), "created");
        document = created.at("/businessDecision/documentId").asText(); assertThat(document).isNotBlank();
        signed(checker.command(input.source().approve(created), 200), "approved");
        forward = input.accounting(state.raw, document); save("forward-accounting", forward);
        withdraw = input.command(created, "WITHDRAW", "EFFECTIVE"); save("withdraw-command", withdraw);
        unchanged("initiation-denials", () -> { denied(maker, withdraw); denied(otherChecker, withdraw); });
        pending = checker.command(withdraw, 200); var receipt = signed(pending, "pending");
        assertThat(receipt.at("/businessDecision/status").asText()).isEqualTo("WITHDRAW_PENDING");
        assertThat(receipt.at("/businessDecision/rolledBack").asBoolean()).isFalse();
        var current = input.accounting(state.raw, document); save("pending-accounting", current);
        assertThat(current.path("result")).isEqualTo(forward.path("result"));
        assertThat(current.path("ledger")).isEqualTo(forward.path("ledger"));
        confirm = input.command(created, "WITHDRAW_CONFIRM", "WITHDRAW_PENDING"); save("confirm-command", confirm);
        unchanged("confirmation-denials", () -> { denied(checker, confirm); denied(otherMaker, confirm); });
        failedConfirmation(maker);
        confirmed = maker.command(confirm, 200); receipt = signed(confirmed, "confirmed");
        assertThat(receipt.at("/businessDecision/status").asText()).isEqualTo("WITHDRAWN");
        assertThat(receipt.at("/actor/principalId").asText()).isEqualTo(input.source().maker().id());
        var restored = input.accounting(state.raw, document); input.assertRestored(forward, restored); save("restored-accounting", restored);
        replays(maker, checker, "original-keys");
    }

    private void failedConfirmation(CorrectionApprovalAdmittedHttp maker) throws Exception {
        state.raw.execute("CREATE TRIGGER test_admitted_withdraw_failure BEFORE INSERT ON order_sync_outbox FOR EACH ROW SIGNAL SQLSTATE '42000' SET MESSAGE_TEXT='injected admitted withdrawal outbox failure'");
        try { unchanged("outbox-failure", () -> maker.command(confirm, 500)); }
        finally { state.raw.execute("DROP TRIGGER test_admitted_withdraw_failure"); }
    }

    private void replay(String label) throws Exception {
        var maker = wire(label + "-maker", input.source().maker().username());
        var checker = wire(label + "-checker", input.source().checker().username());
        replays(maker, checker, label + "-original-keys");
        var restored = input.accounting(state.raw, document); input.assertRestored(forward, restored); save(label + "-accounting", restored);
    }

    private void replays(CorrectionApprovalAdmittedHttp maker, CorrectionApprovalAdmittedHttp checker, String label) throws Exception {
        unchanged(label, () -> {
            assertThat(checker.command(withdraw, 200)).isEqualTo(pending);
            assertThat(maker.command(confirm, 200)).isEqualTo(confirmed);
            mismatch(checker, withdraw); mismatch(maker, confirm);
        });
    }

    private void mismatch(CorrectionApprovalAdmittedHttp actor, ObjectNode command) throws Exception {
        var body = command.deepCopy().put("reason", "changed bytes under original key");
        assertThat(SubmissionJson.parse(actor.command(body, 409)).path("code").asText()).isEqualTo("IDEMPOTENCY_CONTENT_MISMATCH");
    }

    private void denied(CorrectionApprovalAdmittedHttp actor, ObjectNode command) throws Exception {
        assertThat(SubmissionJson.parse(actor.command(command, 403)).path("code").asText()).isEqualTo("CORRECTION_PERMISSION_DENIED");
    }

    private JsonNode signed(byte[] bytes, String label) throws Exception {
        var receipt = input.source().verifyReceipt(bytes, publicKey); save(label + "-receipt", receipt); return receipt;
    }

    private void sourceUnchanged(JsonNode before) throws Exception {
        var after = state.database(SESSION); save("source-after", after);
        for (String table : List.of("dispatch_record", "inventory_ledger", "submission", "submission_payload")) {
            assertThat(before.path(table)).isNotEmpty(); assertThat(after.path(table)).isEqualTo(before.path(table));
        }
        var original = before.path("order_result_version"); var versions = after.path("order_result_version");
        assertThat(original).isNotEmpty(); assertThat(versions).hasSize(original.size() + 2);
        for (JsonNode row : original) assertThat(versions).contains(row);
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
