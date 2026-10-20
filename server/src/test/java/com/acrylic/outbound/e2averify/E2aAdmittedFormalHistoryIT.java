package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.recovery.*;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.ConfigurableApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/** Reads historical originals through admitted TCP without executing the canceled source chain. */
@EnabledIfEnvironmentVariable(named = "E2A_CROSS_VERIFY", matches = "true")
class E2aAdmittedFormalHistoryIT {
    private static final Set<String> SESSION = Set.of("spring_session", "spring_session_attributes");
    private FormalHistoryAdmittedInput input;
    private SubmissionChunkFaultState state;
    private Path evidence;

    @Test void historicalFormalOriginalsRemainReadableAcrossAdmittedRestart() throws Exception {
        input = new FormalHistoryAdmittedInput();
        Files.createFile(input.profile.attempt.resolve("test-started.marker"));
        input.profile.migrate(input.raw);
        E2aCrossSourceHttpSetup.signing(input.profile.attempt);
        evidence = Files.createDirectory(input.profile.attempt.resolve("formal-history-admission"));
        state = new SubmissionChunkFaultState(input.raw, input.profile.attempt.resolve("storage"));
        var before = state.database(SESSION); var files = state.files();
        save("database-before", before); save("files-before", files);
        var p5 = new RecoveryRetirementApplicationFixture(input.profile.attempt);
        var options = new ArrayList<String>(List.of(p5.options()));
        options.add("--server.servlet.session.cookie.secure=false");
        options.add("--outbound.corrections.commands.enabled=true");
        options.add("--outbound.signing.historical-public-keys=" + input.profile.attempt.resolve("private/historical-public.der"));
        consume(options, "initial"); consume(options, "restart");
        var after = state.database(SESSION); var afterFiles = state.files();
        save("database-after", after); save("files-after", afterFiles);
        loginAuditsOnly(before, after); assertThat(afterFiles).isEqualTo(files);
        p5.nextProcessStarts();
        Files.writeString(input.profile.attempt.resolve("test-completed.marker"),
            "Historical FORMAL GET originals, business/file conservation and P5 successor verified", StandardOpenOption.CREATE_NEW);
    }

    private void consume(List<String> options, String label) throws Exception {
        try (var app = E2aCrossSourceHttpIT.start(input.profile, options.toArray(String[]::new))) {
            try { read(label); } finally { drained(app); }
        }
    }

    private void read(String label) throws Exception {
        var maker = new CorrectionApprovalAdmittedHttp(evidence.resolve(label), input.browser.path("checkerUsername").asText());
        var before = state.database(SESSION); var files = state.files();
        try {
            var context = SubmissionJson.parse(maker.get(input.route("context"), 200));
            save(label + "-context", context);
            assertThat(context.at("/context/facts/origin/sourceKind").asText()).isEqualTo("FORMAL");
            for (JsonNode flag : context.path("capabilities")) assertThat(flag.asBoolean(true)).isFalse();
            byte[] original = maker.get(input.route("original-receipt"), 200);
            byte[] expected = Files.readAllBytes(input.profile.attempt.resolve("historical-original-formal-proof.json"));
            assertThat(original).isEqualTo(expected);
            assertThat(SubmissionJson.parse(original)).isEqualTo(context.at("/context/facts/origin/originalReceipt"));
        } finally {
            var after = state.database(SESSION); var afterFiles = state.files();
            save(label + "-database-after", after); save(label + "-files-after", afterFiles);
            assertThat(after).isEqualTo(before); assertThat(afterFiles).isEqualTo(files);
        }
    }

    private void drained(ConfigurableApplicationContext app) throws Exception {
        org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
            assertThat(app.getBean(RecoveryOperationRegistry.class).liveRoots()).isZero());
        app.getBean(RecoveryFenceAuthority.class).checkEffect();
    }

    private void loginAuditsOnly(JsonNode before, JsonNode after) {
        var prior = new ArrayList<JsonNode>(); before.path("operation_event").forEach(prior::add);
        var current = new ArrayList<JsonNode>(); after.path("operation_event").forEach(current::add);
        assertThat(current).hasSize(prior.size() + 2).containsAll(prior); current.removeAll(prior);
        String actor = Base64.getEncoder().encodeToString(input.raw.queryForObject(
            "SELECT id FROM app_user WHERE username=?", byte[].class, input.browser.path("checkerUsername").asText()));
        for (var row : current) {
            assertThat(row.at("/event_type/value").asText()).isEqualTo("WEB_LOGIN");
            assertThat(row.at("/entity_type/value").asText()).isEqualTo("USER");
            assertThat(row.at("/actor_id/value").asText()).isEqualTo(actor);
            assertThat(row.path("entity_id")).isEqualTo(row.path("actor_id"));
            assertThat(row.at("/details/value").asText()).isEqualTo("{}");
        }
        var old = (com.fasterxml.jackson.databind.node.ObjectNode) before.deepCopy();
        var now = (com.fasterxml.jackson.databind.node.ObjectNode) after.deepCopy();
        old.remove("operation_event"); now.remove("operation_event"); assertThat(now).isEqualTo(old);
    }

    private void save(String name, JsonNode value) throws Exception {
        Files.writeString(evidence.resolve(name + ".json"), value.toPrettyString(), StandardOpenOption.CREATE_NEW);
    }
}
