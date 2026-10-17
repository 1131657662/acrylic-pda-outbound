package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.RecoveryExportInput;
import com.acrylic.outbound.recovery.*;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.command.CommandOperation;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

/** Same isolated nonempty input: HTTP/session -> production scheduler -> renderer/file -> HTTP. */
@EnabledIfEnvironmentVariable(named = "E2A_CROSS_VERIFY", matches = "true")
class E2aAdmittedWorkerExportIT {
    @Test void productionSchedulerRetainsAdmissionAcrossExportAndRefusesChangedAuthority() throws Exception {
        var profile = new E2aCrossSourceProfile(); var raw = profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(raw, "34");
        E2aV34Seed.seed(raw, profile.attempt.resolve("storage"));
        profile.migrate(raw);
        E2aCrossSourceHttpSetup.signing(profile.attempt);
        RecoveryExportInput input;
        try (var web = start(profile, false, null)) {
            assertThat(web.getBeansOfType(CommandOperation.class)).isEmpty();
            input = RecoveryExportInput.seed((WebApplicationContext) web);
            try (var worker = start(profile, true, null)) {
                assertThat(worker.getBeansOfType(RecoveryOperationRegistry.class)).isEmpty();
                export(profile, raw, input, "default-off", "XLSX");
            }
        }
        admitted(profile, raw, input);
        p5(profile, raw, input);
        assertThat(raw.queryForObject("SELECT COUNT(*) FROM recovery_normal_admission", Integer.class)).isZero();
        Files.writeString(profile.attempt.resolve("test-completed.marker"),
            "Default-off and admitted real HTTP/session -> nonweb production scheduler -> renderer/files -> download; "
            + "held writer, rootless refusal, last-SQL rollback, retirement and stale HTTP/worker refusal; no production release\n");
    }

    private static void admitted(E2aCrossSourceProfile profile, JdbcTemplate raw, RecoveryExportInput input) throws Exception {
        var authority = new RecoveryAdmissionEntryFixture(profile.attempt.resolve("worker-authority"));
        assertThat(raw.queryForObject("SELECT COUNT(*) FROM background_job WHERE job_type='REVIEW_REMINDER_SWEEP'", Integer.class)).isZero();
        try (var web = start(profile, false, authority.options()); var worker = start(profile, true, authority.options())) {
            assertThat(web.getBeansOfType(CommandOperation.class)).isEmpty();
            assertThat(worker.getBean(javax.sql.DataSource.class)).isInstanceOf(RecoveryRequestAdmittedDataSource.class);
            for (String format : List.of("XLSX", "PDF")) export(profile, raw, input, "admitted", format);
            reminder(raw);
            RecoveryWorkerChecks.verify(worker, raw, authority, profile.attempt);
            var http = new RecoveryWorkerHttp(profile.attempt.resolve("private/http-stale"));
            assertThat(http.get("/auth/csrf", 503).path("code").asText()).startsWith("CONTROL_");
            assertThat(web.getBean(RecoveryOperationRegistry.class).liveRoots()).isZero();
        }
    }

    private static void p5(E2aCrossSourceProfile profile, JdbcTemplate raw, RecoveryExportInput input) throws Exception {
        var fixture = new RecoveryRetirementApplicationFixture(profile.attempt);
        try (var web = start(profile, false, fixture.options()); var worker = start(profile, true, fixture.options())) {
            assertThat(web.getBeansOfType(CommandOperation.class)).isEmpty();
            for (String format : List.of("XLSX", "PDF")) export(profile, raw, input, "p5", format);
            assertThat(raw.queryForObject("SELECT COUNT(*) FROM SPRING_SESSION", Integer.class)).isPositive();
            fixture.drain(web, worker);
        }
        fixture.nextProcessStarts();
    }

    private static void export(E2aCrossSourceProfile profile, JdbcTemplate raw, RecoveryExportInput input,
            String mode, String format) throws Exception {
        String prefix = mode + "-" + format.toLowerCase(Locale.ROOT);
        var http = new RecoveryWorkerHttp(profile.attempt.resolve("private/http-" + prefix));
        http.get(input.exportPath(), 401); http.login(input.username());
        assertThat(http.get("/orders/" + input.order() + "/exports?warehouseId=" + UUID.randomUUID(), 404)
            .path("code").asText()).isEqualTo("WAREHOUSE_NOT_FOUND");
        String key = UUID.randomUUID().toString();
        var created = http.create(input.exportPath(), format, key, 200).path("data");
        String id = created.path("id").asText(); assertThat(id).isNotBlank();
        assertThat(http.create(input.exportPath(), format, key, 200).path("data").path("id").asText()).isEqualTo(id);
        http.create(input.exportPath(), format.equals("PDF") ? "XLSX" : "PDF", key, 409);
        await().atMost(Duration.ofSeconds(120)).pollInterval(Duration.ofMillis(250))
            .untilAsserted(() -> assertThat(raw.queryForObject(
                "SELECT status FROM background_job WHERE id=UUID_TO_BIN(?)", String.class, id)).isEqualTo("SUCCEEDED"));
        var view = http.get("/exports/" + id + "?warehouseId=" + input.warehouse(), 200).path("data");
        assertThat(view.path("downloadable").asBoolean()).isTrue();
        var target = profile.attempt.resolve(prefix + (format.equals("PDF") ? ".pdf" : ".xlsx"));
        byte[] bytes = http.download(id, input.warehouse(), target);
        assertThat(bytes).startsWith(format.equals("PDF") ? new byte[]{'%','P','D','F'} : new byte[]{'P','K'});
        assertThat(java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)))
            .isEqualTo(view.path("sha256").asText());
        preserve(profile, raw, id, prefix, view);
    }

    private static void preserve(E2aCrossSourceProfile profile, JdbcTemplate raw, String id, String prefix,
            JsonNode view) throws Exception {
        String payload = raw.queryForObject("SELECT payload FROM background_job WHERE id=UUID_TO_BIN(?)", String.class, id);
        var facts = SubmissionJson.object().set("view", view);
        ((com.fasterxml.jackson.databind.node.ObjectNode) facts).set("payload", SubmissionJson.parse(payload));
        Files.writeString(profile.attempt.resolve(prefix + ".json"), facts.toPrettyString(), StandardOpenOption.CREATE_NEW);
        String hash = SubmissionJson.parse(payload).path("snapshotHash").asText();
        Files.copy(profile.attempt.resolve("storage/exports/snapshots/" + hash + ".json"),
            profile.attempt.resolve(prefix + ".snapshot.json"));
    }

    private static void reminder(JdbcTemplate raw) {
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(raw.queryForObject(
            "SELECT COUNT(*) FROM background_job WHERE job_type='REVIEW_REMINDER_SWEEP'", Integer.class)).isPositive());
    }

    private static ConfigurableApplicationContext start(E2aCrossSourceProfile profile, boolean worker,
            String[] admission) {
        var options = new ArrayList<String>(List.of("--server.servlet.session.cookie.secure=false",
            "--outbound.exports.sample-data=true", "--outbound.exports.renderer-home="
                + E2aWorktree.ROOT.resolve("report-rendering/build/install/acrylic-report-rendering"),
            "--outbound.exports.browsers=" + E2aWorktree.ROOT.resolve("report-rendering/build/browsers")));
        if (admission != null) options.addAll(List.of(admission));
        if (worker) options.addAll(List.of("--spring.main.web-application-type=none", "--spring.profiles.active=worker",
            "--outbound.worker.enabled=true", "--outbound.worker.poll-ms=250",
            "--outbound.worker.reminder-poll-ms=" + (admission == null ? "3600000" : "1000")));
        var sources = worker && admission != null ? new Class<?>[]{RecoveryWorkerProbe.Configuration.class} : new Class<?>[0];
        return E2aCrossSourceHttpIT.start(profile, sources, options.toArray(String[]::new));
    }
}
