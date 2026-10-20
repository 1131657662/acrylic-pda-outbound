package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.CorrectionAttachmentAdmittedInput;
import com.acrylic.outbound.recovery.*;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/** Attachment bytes and original-key recovery through admitted HTTP roots and P5 retirement. */
@EnabledIfEnvironmentVariable(named = "E2A_CROSS_VERIFY", matches = "true")
class E2aAdmittedCorrectionAttachmentIT {
    private static final Set<String> SESSION = Set.of("spring_session", "spring_session_attributes");
    private SubmissionChunkFaultState state;
    private CorrectionAttachmentAdmittedInput input;
    private Path evidence;
    private final byte[] original = {10, 20, 30, 40};
    private final String key = UUID.randomUUID().toString();
    private JsonNode attachment, command;
    private byte[] receipt;

    @Test void originalAttachmentSurvivesAdmittedCreateAccessFailuresAndRetiredContextReplay() throws Exception {
        var profile = new E2aCrossSourceProfile(); var raw = profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(raw, "34"); E2aV34Seed.seed(raw, profile.attempt.resolve("storage")); profile.migrate(raw);
        E2aCrossSourceHttpSetup.signing(profile.attempt);
        try (var setup = E2aCrossSourceHttpIT.start(profile)) {
            input = CorrectionAttachmentAdmittedInput.prepare((WebApplicationContext) setup);
        }
        evidence = Files.createDirectory(profile.attempt.resolve("attachment-admission"));
        save("input", input.evidence().put("uploadKey", key));
        Files.write(evidence.resolve("original.bin"), original, StandardOpenOption.CREATE_NEW);
        state = new SubmissionChunkFaultState(raw, profile.attempt.resolve("storage"));
        var before = state.database(SESSION); save("source-before", before);
        var p5 = new RecoveryRetirementApplicationFixture(profile.attempt);
        var options = new ArrayList<String>(List.of(p5.options()));
        options.addAll(List.of("--server.servlet.session.cookie.secure=false", "--outbound.corrections.commands.enabled=true",
            "--outbound.corrections.attachments.enabled=true"));
        try (var app = E2aCrossSourceHttpIT.start(profile, options.toArray(String[]::new))) {
            try { consume(profile.attempt); } finally { drained(app); }
        }
        try (var app = E2aCrossSourceHttpIT.start(profile, options.toArray(String[]::new))) {
            try { restart(); } finally { drained(app); }
        }
        sourceUnchanged(before); p5.nextProcessStarts();
        Files.writeString(profile.attempt.resolve("test-completed.marker"), "Admitted attachment TCP lifecycle, access restoration and P5 successor passed");
    }

    private void consume(Path attempt) throws Exception {
        var owner = wire("owner", input.owner().username());
        var reviewer = wire("reviewer", input.reviewer().username());
        var foreign = wire("foreign", input.foreign().username());
        attachment = owner.upload(input.path(key, false), original, 200).path("data"); save("attachment", attachment);
        String id = attachment.path("attachmentId").asText(); assertThat(id).isNotBlank();
        unchanged("unbound-and-replay", () -> {
            assertThat(owner.upload(input.path(key, false), original, 200).path("data")).isEqualTo(attachment);
            assertThat(owner.upload(input.path(key, false), new byte[]{99}, 409).path("code").asText())
                .isEqualTo("IDEMPOTENCY_CONTENT_MISMATCH");
            assertThat(owner.get(input.path(id, false), 200)).isEqualTo(original);
            reviewer.get(input.path(id, true), 404); foreign.get(input.path(id, false), 404);
            reviewer.upload(input.path(UUID.randomUUID().toString(), false), original, 403);
        });
        command = input.command(attachment); save("command", command); receipt = owner.command(command);
        var verified = input.verifyReceipt(receipt, attempt.resolve("private/server-public.der")); save("verified-receipt", verified);
        unchanged("bound-reads", () -> {
            assertThat(reviewer.get(input.path(id, true), 200)).isEqualTo(original);
            assertThat(owner.get(input.path(id, false), 200)).isEqualTo(original);
            foreign.get(input.path(id, true), 403);
            reviewer.get(input.path(id, true).replace(input.warehouse(), input.otherWarehouse()), 404);
            wire("anonymous", null).get(input.path(id, false), 401);
        });
        unchanged("access-restored", () -> accessFailures(owner, reviewer, id));
        unchanged("recovered-reads", () -> assertThat(reviewer.get(input.path(id, true), 200)).isEqualTo(original));
    }

    private void accessFailures(CorrectionAttachmentAdmittedHttp owner, CorrectionAttachmentAdmittedHttp reviewer, String id) throws Exception {
        String reviewerId = input.reviewer().id();
        assertThat(state.raw.update("DELETE FROM user_role WHERE user_id=UUID_TO_BIN(?) AND role_id=(SELECT id FROM role WHERE code='SUPERVISOR')", reviewerId)).isOne();
        try { unchanged("review-permission-removed", () -> reviewer.get(input.path(id, true), 403)); }
        finally { assertThat(state.raw.update("INSERT INTO user_role SELECT UUID_TO_BIN(?),id FROM role WHERE code='SUPERVISOR'", reviewerId)).isOne(); }
        String ownerId = input.owner().id();
        assertThat(state.raw.update("DELETE FROM user_warehouse WHERE user_id=UUID_TO_BIN(?) AND warehouse_id=UUID_TO_BIN(?)", ownerId, input.warehouse())).isOne();
        try { unchanged("owner-warehouse-removed", () -> owner.get(input.path(id, false), 404)); }
        finally { assertThat(state.raw.update("INSERT INTO user_warehouse VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?))", ownerId, input.warehouse())).isOne(); }
        assertThat(state.raw.update("UPDATE app_user SET auth_version=auth_version+1 WHERE id=UUID_TO_BIN(?)", ownerId)).isOne();
        try { unchanged("owner-session-revoked", () -> owner.get(input.path(id, false), 401)); }
        finally { assertThat(state.raw.update("UPDATE app_user SET auth_version=auth_version-1 WHERE id=UUID_TO_BIN(?)", ownerId)).isOne(); }
    }

    private void restart() throws Exception {
        var owner = wire("restart-owner", input.owner().username());
        var reviewer = wire("restart-reviewer", input.reviewer().username());
        unchanged("restart-original-key", () -> {
            assertThat(owner.upload(input.path(key, false), original, 200).path("data")).isEqualTo(attachment);
            assertThat(owner.command(command)).isEqualTo(receipt);
            assertThat(reviewer.get(input.path(attachment.path("attachmentId").asText(), true), 200)).isEqualTo(original);
        });
    }

    private void sourceUnchanged(JsonNode before) throws Exception {
        var after = state.database(SESSION); save("source-after", after);
        for (String table : List.of("dispatch_record", "inventory_ledger", "order_result_version", "submission", "submission_payload")) {
            assertThat(before.path(table)).as(table + " nonempty fixture").isNotEmpty();
            assertThat(after.path(table)).as(table).isEqualTo(before.path(table));
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

    private CorrectionAttachmentAdmittedHttp wire(String label, String username) throws Exception {
        return new CorrectionAttachmentAdmittedHttp(evidence.resolve(label), username);
    }

    private void save(String name, JsonNode value) throws Exception {
        Files.writeString(evidence.resolve(name + ".json"), value.toPrettyString(), StandardOpenOption.CREATE_NEW);
    }

    private interface Checked { void run() throws Exception; }
}
