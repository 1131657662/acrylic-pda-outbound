package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.RecoveryExportInput;
import com.acrylic.outbound.recovery.*;
import com.acrylic.outbound.submissions.SubmissionJson;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

/** Distinct API/scheduler OS processes share one synthetic P5 authority and actual job/files. */
@EnabledIfEnvironmentVariable(named = "E2A_CROSS_VERIFY", matches = "true")
class E2aSplitWorkerExportIT {
    @Test void separateApiAndNonwebWorkerExportRollbackRetireAndAdmitSuccessor() throws Exception {
        var profile = new E2aCrossSourceProfile(); var raw = profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(raw, "34");
        E2aV34Seed.seed(raw, profile.attempt.resolve("storage"));
        profile.migrate(raw); E2aCrossSourceHttpSetup.signing(profile.attempt);
        RecoveryExportInput input;
        try (var seed = E2aCrossSourceHttpIT.start(profile)) { input = RecoveryExportInput.seed((WebApplicationContext) seed); }
        var fixture = new RecoveryRetirementApplicationFixture(profile.attempt);
        try (var children = new E2aSplitWorkerProcesses(profile, fixture.options())) {
            children.start("api"); children.start("worker");
            distinctProcesses(children.home);
            for (String format : List.of("XLSX", "PDF")) export(profile, raw, input, format);
            probe(profile, raw, children.home);
            children.stop("worker"); children.stop("api");
            retired(profile);
            children.successor(profile.attempt.resolve("private/p5-retirement/config.json"));
        }
        assertThat(raw.queryForObject("SELECT COUNT(*) FROM recovery_normal_admission", Integer.class)).isZero();
        Files.writeString(profile.attempt.resolve("test-completed.marker"),
            "Separate API/nonweb scheduler P5 export/download; held root; last SQL rollback; clean retirement; successor",
            StandardOpenOption.CREATE_NEW);
    }

    private static void distinctProcesses(Path home) throws Exception {
        long api = SubmissionJson.parse(Files.readString(home.resolve("api-ready.json"))).path("pid").asLong();
        var worker = SubmissionJson.parse(Files.readString(home.resolve("worker-ready.json")));
        assertThat(worker.path("pid").asLong()).isNotEqualTo(api).isNotEqualTo(ProcessHandle.current().pid());
        assertThat(worker.path("web").asBoolean()).isFalse();
    }

    private static void export(E2aCrossSourceProfile profile, JdbcTemplate raw, RecoveryExportInput input, String format) throws Exception {
        String prefix = "split-" + format.toLowerCase(Locale.ROOT);
        var http = new RecoveryWorkerHttp(profile.attempt.resolve("private/http-" + prefix));
        http.get(input.exportPath(), 401); http.login(input.username());
        http.get("/orders/" + input.order() + "/exports?warehouseId=" + UUID.randomUUID(), 404);
        String key = UUID.randomUUID().toString();
        String id = http.create(input.exportPath(), format, key, 200).path("data").path("id").asText();
        assertThat(id).isNotBlank();
        assertThat(http.create(input.exportPath(), format, key, 200).path("data").path("id").asText()).isEqualTo(id);
        http.create(input.exportPath(), format.equals("PDF") ? "XLSX" : "PDF", key, 409);
        await().atMost(Duration.ofSeconds(120)).untilAsserted(() -> assertThat(raw.queryForObject(
            "SELECT status FROM background_job WHERE id=UUID_TO_BIN(?)", String.class, id)).isEqualTo("SUCCEEDED"));
        var view = http.get("/exports/" + id + "?warehouseId=" + input.warehouse(), 200).path("data");
        assertThat(view.path("downloadable").asBoolean()).isTrue();
        byte[] bytes = http.download(id, input.warehouse(), profile.attempt.resolve(prefix + (format.equals("PDF") ? ".pdf" : ".xlsx")));
        assertThat(HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)))
            .isEqualTo(view.path("sha256").asText());
        var payload = SubmissionJson.parse(raw.queryForObject("SELECT payload FROM background_job WHERE id=UUID_TO_BIN(?)", String.class, id));
        var record = SubmissionJson.object(); record.set("view", view); record.set("payload", payload);
        Files.writeString(profile.attempt.resolve(prefix + ".json"), record.toPrettyString(), StandardOpenOption.CREATE_NEW);
        Files.copy(profile.attempt.resolve("storage/exports/snapshots/" + payload.path("snapshotHash").asText() + ".json"),
            profile.attempt.resolve(prefix + ".snapshot.json"));
    }

    private static void probe(E2aCrossSourceProfile profile, JdbcTemplate raw, Path home) throws Exception {
        raw.execute("CREATE TABLE recovery_worker_probe(id INT PRIMARY KEY)");
        String id = UUID.randomUUID().toString();
        raw.update("INSERT INTO background_job(id,job_type,dedupe_key,status,payload,max_attempts,available_at,created_at,updated_at,recovery_epoch) "
            + "VALUES(UUID_TO_BIN(?),?,?,'PENDING','{}',1,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3),UTC_TIMESTAMP(3),1)",
            id, RecoveryWorkerProbe.TYPE, id);
        Files.createFile(home.resolve("probe-start.marker"));
        await().atMost(Duration.ofSeconds(20)).until(() -> Files.exists(home.resolve("probe-held.marker")));
        RecoverySplitWorkerChecks.writerBlocked(profile.attempt.resolve("private/p5-retirement/config.json"));
        Files.writeString(home.resolve("writer-blocked.marker"), "independent JVM exclusive writer refused", StandardOpenOption.CREATE_NEW);
        Files.createFile(home.resolve("release-probe.marker"));
        await().atMost(Duration.ofSeconds(30)).until(() -> Files.exists(home.resolve("probe-released.marker")));
        assertThat(raw.queryForObject("SELECT status FROM background_job WHERE id=UUID_TO_BIN(?)", String.class, id)).isEqualTo("FAILED");
        assertThat(raw.queryForObject("SELECT COUNT(*) FROM recovery_worker_probe", Integer.class)).isZero();
    }

    private static void retired(E2aCrossSourceProfile profile) throws Exception {
        Path home = profile.attempt.resolve("private/p5-retirement");
        var index = SubmissionJson.parse(Files.readString(home.resolve("p5/leases/index.json")));
        assertThat(index.path("entries")).isEmpty();
        try (var files = Files.list(home.resolve("p5/leases"))) {
            assertThat(files.map(path -> path.getFileName().toString()).toList()).containsExactly("index.json");
        }
        try (var files = Files.list(home.resolve("p5/processes"))) { assertThat(files.count()).isEqualTo(2); }
        Files.writeString(home.resolve("drained-index.json"), index.toPrettyString(), StandardOpenOption.CREATE_NEW);
    }
}
