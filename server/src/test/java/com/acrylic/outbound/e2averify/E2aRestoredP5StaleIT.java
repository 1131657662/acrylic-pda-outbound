package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.RecoveryExportInput;
import com.acrylic.outbound.recovery.RecoveryRestoredP5StaleAuthority;
import com.acrylic.outbound.submissions.SubmissionJson;
import java.nio.file.*;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

/** Full restored input, separate real API/scheduler, independent authority advance and refusal. */
@EnabledIfEnvironmentVariable(named = "E2A_CROSS_VERIFY", matches = "true")
class E2aRestoredP5StaleIT {
    private record Pending(RecoveryExportInput input, E2aRestoredSchedulerExports exports) { }
    private record Restored(E2aCrossSourceProfile profile, E2aArchiveSnapshotRestore copier, JdbcTemplate target) { }

    @Test void restoredApiAndWorkerRefuseAfterIndependentControlAdvanceWithoutChangingFacts() throws Exception {
        var profile = new E2aCrossSourceProfile(); profile.connect();
        try (var sourcePool = E2aPointInTimeCheckpoint.pool(profile, profile.url)) {
            verify(profile, new JdbcTemplate(sourcePool));
        }
    }

    private static void verify(E2aCrossSourceProfile profile, JdbcTemplate source) throws Exception {
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(source, "34"); E2aV34Seed.seed(source, profile.attempt.resolve("storage"));
        profile.migrate(source); E2aCrossSourceHttpSetup.signing(profile.attempt);
        var pending = prepare(profile, source);
        var copier = new E2aArchiveSnapshotRestore(profile, source); copier.restore();
        try (var targetPool = E2aPointInTimeCheckpoint.pool(profile, copier.url)) {
            restored(new Restored(profile, copier, new JdbcTemplate(targetPool)), pending);
        }
        var before = SubmissionJson.parse(Files.readString(profile.attempt.resolve("restored-before.json")));
        var sourceLast = copier.snapshot(source, profile.attempt.resolve("storage"));
        assertThat(sourceLast).isEqualTo(before);
        E2aRestoredSchedulerConservation.save(profile.attempt, "source-final.json", sourceLast);
        Files.writeString(profile.attempt.resolve("test-completed.marker"), "Restored P5 API/worker stale refused; all stable business and P5 facts invariant",
            StandardOpenOption.CREATE_NEW);
    }

    private static void restored(Restored restored, Pending pending) throws Exception {
        var profile = restored.profile(); var copier = restored.copier(); var target = restored.target();
        var before = copier.snapshot(target, copier.restored.resolve("storage"));
        E2aRestoredSchedulerConservation.save(profile.attempt, "restored-before.json", before);
        exercise(restored, pending);
        var last = copier.snapshot(target, copier.restored.resolve("storage"));
        E2aRestoredSchedulerConservation.save(profile.attempt, "restored-final.json", last);
        assertThat(last).isEqualTo(SubmissionJson.parse(Files.readString(profile.attempt.resolve("restored-stable.json"))));
        E2aRestoredSchedulerConservation.verify(before, last);
    }

    private static void exercise(Restored restored, Pending pending) throws Exception {
        var profile = restored.profile(); var copier = restored.copier(); var target = restored.target();
        var authority = new RecoveryRestoredP5StaleAuthority(profile.attempt);
        try (var children = new E2aSplitWorkerProcesses(profile, authority.options(), "restored-p5-stale")) {
            children.start("api"); children.start("worker");
            await().atMost(Duration.ofSeconds(30)).until(() -> Files.exists(children.home.resolve("api-p5-live.json"))
                && Files.exists(children.home.resolve("worker-p5-live.json")));
            pending.exports().consume(target, "restored-stale");
            Files.writeString(children.home.resolve("pause-worker.marker"), "pause owned scheduler", StandardOpenOption.CREATE_NEW);
            await().atMost(Duration.ofSeconds(30)).until(() -> Files.exists(children.home.resolve("worker-paused.marker")));
            var http = new E2aRestoredStaleHttp(profile, pending.input()); http.pending(target);
            var stable = copier.snapshot(target, copier.restored.resolve("storage"));
            E2aRestoredSchedulerConservation.save(profile.attempt, "restored-stable.json", stable);
            var p5 = authority.snapshot("before"); authority.advance(); http.refuse();
            Files.writeString(children.home.resolve("check-stale.marker"), "invoke old worker", StandardOpenOption.CREATE_NEW);
            await().atMost(Duration.ofSeconds(30)).until(() -> Files.exists(children.home.resolve("worker-stale.json")));
            assertThat(copier.snapshot(target, copier.restored.resolve("storage"))).isEqualTo(stable);
            assertThat(authority.snapshot("after")).isEqualTo(p5);
            children.stop("worker"); children.stop("api");
        }
    }

    private static Pending prepare(E2aCrossSourceProfile profile, JdbcTemplate source) throws Exception {
        try (var web = E2aCrossSourceHttpIT.start(profile, "--server.servlet.session.cookie.secure=false",
                "--outbound.exports.sample-data=true", "--outbound.exports.renderer-home=" + System.getenv("E2A_SPLIT_RENDERER"),
                "--outbound.exports.browsers=" + System.getenv("E2A_SPLIT_BROWSERS"))) {
            var input = RecoveryExportInput.seed((WebApplicationContext) web);
            var exports = new E2aRestoredSchedulerExports(profile, input); exports.prepare(source);
            return new Pending(input, exports);
        }
    }
}
