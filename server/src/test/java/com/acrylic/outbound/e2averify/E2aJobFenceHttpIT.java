package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.jobs.*;
import java.nio.file.Files;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "E2A_CROSS_VERIFY", matches = "true")
class E2aJobFenceHttpIT {
    @Test void nonemptyMigrationFencesEveryWorkerWriteAcrossRecoveryAndRestart() throws Exception {
        var profile = new E2aCrossSourceProfile(); var jdbc = profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc, "34");
        var seed = E2aV34Seed.seed(jdbc, profile.attempt.resolve("storage"));
        profile.migrate(jdbc, "37");
        var legacy = new JobFenceMigrationSeed(jdbc); legacy.seed();
        profile.migrate(jdbc, "38"); legacy.verify();
        E2aCrossSourceHttpSetup.signing(profile.attempt);
        var http = new JobFenceHttp(jdbc, seed, Files.createDirectory(profile.attempt.resolve("http")));
        List<BackgroundJob> jobsBefore;
        try (var context = E2aCrossSourceHttpIT.start(profile)) {
            var f = new JobFenceFixture(context);
            var checks = new JobFenceChecks(f); checks.verify(); new JobFenceRaces(f).verify();
            JobFenceExportChecks.verify(context);
            http.verify(f); legacy.verify(); checks.oldEpoch();
            jobsBefore = f.jdbc.queryForList("SELECT BIN_TO_UUID(id) FROM background_job", String.class)
                .stream().map(id -> f.jobs.find(UUID.fromString(id)).orElseThrow()).toList();
        }
        restart(profile, jobsBefore); legacy.verify();
        Files.writeString(profile.attempt.resolve("job-fence-query-plan.json"), new com.fasterxml.jackson.databind.ObjectMapper()
            .writeValueAsString(jdbc.queryForList("""
                EXPLAIN SELECT id FROM background_job WHERE recovery_epoch=2 AND job_type='LEGACY.FENCE'
                AND status IN ('PENDING','RETRY') AND available_at<=UTC_TIMESTAMP(3)
                ORDER BY available_at,id LIMIT 1
                """)));
        Files.writeString(profile.attempt.resolve("test-completed.marker"),
            "Nonempty V34->V37->V38; five legacy job states immutable; worker epoch/mode fences; real DB lock races; HTTP block; restart\n");
    }

    private void restart(E2aCrossSourceProfile profile, List<BackgroundJob> before) {
        try (var context = E2aCrossSourceHttpIT.start(profile)) {
            var f = new JobFenceFixture(context);
            before.forEach(job -> assertThat(f.current(job)).isEqualTo(job));
            var checks = new JobFenceChecks(f);
            before.stream().filter(job -> job.status() == BackgroundJob.Status.RUNNING).forEach(checks::restartedOldJob);
            assertThat(f.leases.claim("worker", Set.of("LEGACY.FENCE"), Duration.ofMinutes(1))).isEmpty();
            var original = f.jobs.findByDedupeKey("legacy-fence:RUNNING").orElseThrow();
            assertThat(f.leases.complete(original, () -> fail("restart cannot revive legacy"))).isFalse();
            var created = f.enqueue(); var claim = f.claim();
            assertThat(created.recoveryEpoch()).isEqualTo("2");
            assertThat(f.leases.complete(claim, () -> {})).isTrue();
            assertThatThrownBy(() -> f.jdbc.update("UPDATE background_job SET recovery_epoch=3 WHERE id=?",
                com.acrylic.outbound.common.Ids.bytes(created.id()))).isInstanceOf(org.springframework.dao.DataAccessException.class);
        }
    }
}
