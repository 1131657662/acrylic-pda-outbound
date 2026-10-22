package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.RecoveryExportInput;
import com.acrylic.outbound.recovery.RecoveryRetirementApplicationFixture;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.*;

/** Restored jobs exhaust a renderer dependency failure before a full successor retries. */
@EnabledIfEnvironmentVariable(named = "E2A_CROSS_VERIFY", matches = "true")
class E2aRestoredRetryIT {
    private record Runtime(E2aCrossSourceProfile profile, RecoveryRetirementApplicationFixture authority,
            E2aRestoredRetryExports exports, JdbcTemplate target) { }

    @Test void restoredFailedJobsKeepOriginalFactsWhenFullSuccessorRetries() throws Exception {
        var profile = new E2aCrossSourceProfile(); var source = profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(source, "34"); E2aV34Seed.seed(source, profile.attempt.resolve("storage"));
        profile.migrate(source); E2aCrossSourceHttpSetup.signing(profile.attempt);
        var exports = pending(profile, source);
        var copier = new E2aArchiveSnapshotRestore(profile, source); var target = copier.restore();
        var before = copier.snapshot(target, copier.restored.resolve("storage"));
        E2aRestoredSchedulerConservation.save(profile.attempt, "restored-before.json", before);
        var runtime = new Runtime(profile, new RecoveryRetirementApplicationFixture(profile.attempt), exports, target);
        fail(runtime);
        var failed = copier.snapshot(target, copier.restored.resolve("storage"));
        E2aRestoredSchedulerConservation.save(profile.attempt, "restored-failed.json", failed);
        succeed(runtime);
        var last = copier.snapshot(target, copier.restored.resolve("storage"));
        E2aRestoredSchedulerConservation.save(profile.attempt, "restored-final.json", last);
        E2aRestoredSchedulerConservation.verify(before, failed);
        E2aRestoredSchedulerConservation.verify(before, last);
        E2aRestoredRetryChecks.originalRows(failed, last);
        E2aRestoredRetryChecks.dependencies(profile, copier);
        var sourceFinal = copier.snapshot(source, profile.attempt.resolve("storage"));
        assertThat(sourceFinal).isEqualTo(before);
        E2aRestoredSchedulerConservation.save(profile.attempt, "source-final.json", sourceFinal);
        Files.writeString(profile.attempt.resolve("test-completed.marker"),
            "Restored originals fail actual renderer dependency; full API/scheduler successor retries same snapshots; source invariant",
            StandardOpenOption.CREATE_NEW);
    }

    private static E2aRestoredRetryExports pending(E2aCrossSourceProfile profile, JdbcTemplate source) throws Exception {
        try (var web = E2aCrossSourceHttpIT.start(profile, "--server.servlet.session.cookie.secure=false",
                "--outbound.exports.sample-data=true", "--outbound.exports.renderer-home=" + System.getenv("E2A_SPLIT_RENDERER"),
                "--outbound.exports.browsers=" + System.getenv("E2A_SPLIT_BROWSERS"))) {
            var exports = new E2aRestoredRetryExports(profile, RecoveryExportInput.seed((WebApplicationContext) web));
            exports.prepare(source); return exports;
        }
    }

    private static void fail(Runtime runtime) throws Exception {
        try (var children = new E2aSplitWorkerProcesses(runtime.profile(), runtime.authority().options(), "restored-split")) {
            E2aRestoredRetryChecks.brokenRenderer(runtime.profile(), children.home);
            children.start("api"); children.start("worker"); runtime.exports().failed(runtime.target());
            children.stop("worker"); children.stop("api");
        }
        E2aRestoredSchedulerConservation.retirement(runtime.profile().attempt, 2);
    }

    private static void succeed(Runtime runtime) throws Exception {
        try (var children = new E2aSplitWorkerProcesses(runtime.profile(), runtime.authority().options(), "restored-split-successor")) {
            children.start("api"); children.start("worker"); runtime.exports().retry(runtime.target());
            children.stop("worker"); children.stop("api");
        }
        E2aRestoredSchedulerConservation.retirement(runtime.profile().attempt, 4);
    }
}
