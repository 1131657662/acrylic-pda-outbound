package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.CorrectionApprovalAdmittedInput;
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

/** Maker-checker accounting and original-key replay through admitted HTTP roots and P5 retirement. */
@EnabledIfEnvironmentVariable(named = "E2A_CROSS_VERIFY", matches = "true")
class E2aAdmittedCorrectionApprovalIT {
    private static final Set<String> SESSION = Set.of("spring_session", "spring_session_attributes");
    private SubmissionChunkFaultState state;
    private CorrectionApprovalAdmittedInput input;
    private Path evidence, publicKey;
    private ObjectNode approve;
    private String document;
    private byte[] approved;

    @Test void admittedSupervisorApprovalPostsExactlyOnceAcrossRetiredContextReplay() throws Exception {
        var profile = new E2aCrossSourceProfile(); var raw = profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(raw, "34"); E2aV34Seed.seed(raw, profile.attempt.resolve("storage")); profile.migrate(raw);
        E2aCrossSourceHttpSetup.signing(profile.attempt);
        try (var setup = E2aCrossSourceHttpIT.start(profile)) {
            input = CorrectionApprovalAdmittedInput.prepare((WebApplicationContext) setup);
        }
        evidence = Files.createDirectory(profile.attempt.resolve("approval-admission"));
        publicKey = profile.attempt.resolve("private/server-public.der"); save("input", input.evidence());
        state = new SubmissionChunkFaultState(raw, profile.attempt.resolve("storage"));
        var before = state.database(SESSION); save("source-before", before);
        var p5 = new RecoveryRetirementApplicationFixture(profile.attempt);
        var options = new ArrayList<String>(List.of(p5.options()));
        options.addAll(List.of("--server.servlet.session.cookie.secure=false", "--outbound.corrections.commands.enabled=true"));
        try (var app = E2aCrossSourceHttpIT.start(profile, options.toArray(String[]::new))) {
            try { consume(); } finally { drained(app); }
        }
        try (var app = E2aCrossSourceHttpIT.start(profile, options.toArray(String[]::new))) {
            try { restart(); } finally { drained(app); }
        }
        sourceUnchanged(before); p5.nextProcessStarts();
        Files.writeString(profile.attempt.resolve("test-completed.marker"), "Admitted approval TCP accounting and P5 successor passed");
    }

    private void consume() throws Exception {
        var maker = wire("maker", input.maker().username()); var checker = wire("checker", input.checker().username());
        save("create-command", input.create());
        var created = input.verifyReceipt(maker.command(input.create(), 200), publicKey); save("create-receipt", created);
        document = created.at("/businessDecision/documentId").asText(); assertThat(document).isNotBlank();
        approve = input.approve(created); save("approve-command", approve);
        unchanged("permissions", () -> {
            denied(maker, approve, "CORRECTION_PERMISSION_DENIED");
            denied(checker, input.create().deepCopy().put("commandId", UUID.randomUUID().toString()), "CORRECTION_PERMISSION_DENIED");
        });
        unchanged("role-restored", () -> {
            makerApprovalRole(true);
            try { unchanged("self-approval", () -> denied(maker, approve, "CORRECTION_SELF_APPROVAL_DENIED")); }
            finally { makerApprovalRole(false); }
        });
        approved = checker.command(approve, 200); var signed = input.verifyReceipt(approved, publicKey); save("approved-receipt", signed);
        assertThat(signed.at("/businessDecision/status").asText()).isEqualTo("EFFECTIVE");
        assertThat(signed.at("/actor/principalId").asText()).isEqualTo(input.checker().id());
        save("accounting", input.accounting(state.raw, document)); save("approved-database", state.database(SESSION));
        unchanged("original-key", () -> {
            assertThat(checker.command(approve, 200)).isEqualTo(approved);
            var changed = approve.deepCopy().put("reason", "changed bytes under original key");
            assertThat(SubmissionJson.parse(checker.command(changed, 409)).path("code").asText()).isEqualTo("IDEMPOTENCY_CONTENT_MISMATCH");
        });
    }

    private void restart() throws Exception {
        var checker = wire("restart-checker", input.checker().username());
        unchanged("restart-original-key", () -> {
            assertThat(checker.command(approve, 200)).isEqualTo(approved);
            assertThat(input.verifyReceipt(approved, publicKey).at("/businessDecision/status").asText()).isEqualTo("EFFECTIVE");
            save("restart-accounting", input.accounting(state.raw, document));
        });
    }

    private void makerApprovalRole(boolean enabled) {
        String sql = enabled ? "INSERT INTO user_role SELECT UUID_TO_BIN(?),id FROM role WHERE code='SUPERVISOR'"
            : "DELETE FROM user_role WHERE user_id=UUID_TO_BIN(?) AND role_id=(SELECT id FROM role WHERE code='SUPERVISOR')";
        assertThat(state.raw.update(sql, input.maker().id())).isOne();
    }

    private void denied(CorrectionApprovalAdmittedHttp wire, JsonNode command, String code) throws Exception {
        assertThat(SubmissionJson.parse(wire.command(command, 403)).path("code").asText()).isEqualTo(code);
    }

    private void sourceUnchanged(JsonNode before) throws Exception {
        var after = state.database(SESSION); save("source-after", after);
        for (String table : List.of("dispatch_record", "inventory_ledger", "submission", "submission_payload")) {
            assertThat(before.path(table)).as(table + " nonempty fixture").isNotEmpty();
            assertThat(after.path(table)).as(table).isEqualTo(before.path(table));
        }
        var original = before.path("order_result_version"); var versions = after.path("order_result_version");
        assertThat(original).isNotEmpty(); assertThat(versions).hasSize(original.size() + 1);
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
