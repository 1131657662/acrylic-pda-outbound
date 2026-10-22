package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.RecoveryExportInput;
import com.acrylic.outbound.recovery.RecoveryRetirementApplicationFixture;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.*;

/** Fresh complete restore, original PENDING HTTP jobs, production scheduler and full successor consumers. */
@EnabledIfEnvironmentVariable(named = "E2A_CROSS_VERIFY", matches = "true")
class E2aRestoredSchedulerIT {
    private record Runtime(E2aCrossSourceProfile profile, RecoveryRetirementApplicationFixture fixture,
            E2aRestoredSchedulerExports exports, org.springframework.jdbc.core.JdbcTemplate target) { }
    private record Snapshots(ObjectNode before, ObjectNode first, ObjectNode last) { }
    @Test void originalPendingJobsRunOnlyOnRestoredSchedulerAndSurviveFullSuccessor() throws Exception {
        var profile = new E2aCrossSourceProfile(); var source = profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(source, "34"); E2aV34Seed.seed(source, profile.attempt.resolve("storage"));
        profile.migrate(source); E2aCrossSourceHttpSetup.signing(profile.attempt);
        var exports = pending(profile, source);
        var copier = new E2aArchiveSnapshotRestore(profile, source);
        var target = copier.restore();
        var before = copier.snapshot(target, copier.restored.resolve("storage"));
        E2aRestoredSchedulerConservation.save(profile.attempt, "restored-before.json", before);
        assertThat(copier.snapshot(source, profile.attempt.resolve("storage"))).isEqualTo(before);
        var fixture = new RecoveryRetirementApplicationFixture(profile.attempt);
        var runtime = new Runtime(profile, fixture, exports, target);
        consume(runtime, "restored-split");
        var first = copier.snapshot(target, copier.restored.resolve("storage"));
        E2aRestoredSchedulerConservation.retirement(profile.attempt, 2);
        E2aRestoredSchedulerConservation.save(profile.attempt, "restored-first.json", first);
        consume(runtime, "restored-split-successor");
        var last = copier.snapshot(target, copier.restored.resolve("storage"));
        E2aRestoredSchedulerConservation.retirement(profile.attempt, 4);
        finish(profile, copier, new Snapshots(before, first, last));
        assertThat(copier.snapshot(source, profile.attempt.resolve("storage"))).isEqualTo(before);
        E2aRestoredSchedulerConservation.save(profile.attempt, "source-final.json", copier.snapshot(source, profile.attempt.resolve("storage")));
        Files.writeString(profile.attempt.resolve("test-completed.marker"),
            "Full SQL/files restore; distinct admitted API/nonweb scheduler; original jobs; full successor HTTP consumers; source invariant",
            StandardOpenOption.CREATE_NEW);
    }

    private static E2aRestoredSchedulerExports pending(E2aCrossSourceProfile profile, org.springframework.jdbc.core.JdbcTemplate source) throws Exception {
        try (var context = E2aCrossSourceHttpIT.start(profile, "--server.servlet.session.cookie.secure=false",
                "--outbound.exports.sample-data=true", "--outbound.exports.renderer-home=" + System.getenv("E2A_SPLIT_RENDERER"),
                "--outbound.exports.browsers=" + System.getenv("E2A_SPLIT_BROWSERS"))) {
            var exports = new E2aRestoredSchedulerExports(profile, RecoveryExportInput.seed((WebApplicationContext) context));
            exports.prepare(source); return exports;
        }
    }

    private static void consume(Runtime runtime, String scope) throws Exception {
        try (var children = new E2aSplitWorkerProcesses(runtime.profile(), runtime.fixture().options(), scope)) {
            children.start("api"); children.start("worker"); runtime.exports().consume(runtime.target(), scope);
            children.stop("worker"); children.stop("api");
        }
    }

    private static void finish(E2aCrossSourceProfile profile, E2aArchiveSnapshotRestore copier, Snapshots snapshots) throws Exception {
        var before = snapshots.before(); var first = snapshots.first(); var last = snapshots.last();
        E2aRestoredSchedulerConservation.save(profile.attempt, "restored-final.json", last);
        E2aRestoredSchedulerConservation.verify(before, first);
        E2aRestoredSchedulerConservation.verify(before, last);
        assertThat(last.path("tables").path("background_job")).isEqualTo(first.path("tables").path("background_job"));
        assertThat(last.path("files")).isEqualTo(first.path("files"));
        for (String name : java.util.List.of("server-private.der", "server-public.der"))
            assertThat(Files.readAllBytes(copier.restored.resolve("config/" + name)))
                .isEqualTo(Files.readAllBytes(profile.attempt.resolve("private/" + name)));
    }
}
