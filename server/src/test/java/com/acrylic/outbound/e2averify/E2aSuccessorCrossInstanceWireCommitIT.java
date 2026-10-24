package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.recovery.RecoverySuccessorRuntimeSupply;
import com.acrylic.outbound.recovery.RecoveryBoundWireCommitProof;
import java.time.Duration;
import static org.awaitility.Awaitility.await;
import com.acrylic.outbound.inventory.RecoveryExportInput;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

/** Bound full restore on a different physical server, consumed by actual API/worker/P5/files. */
@EnabledIfEnvironmentVariable(named = "E2A_COPY_VERIFY", matches = "true")
class E2aSuccessorCrossInstanceWireCommitIT {
    private record Restored(E2aArchiveSnapshotRestore copier, JdbcTemplate target,
            RecoverySuccessorRuntimeSupply supply, E2aArchiveRestoreTarget destination) { }

    @Test void realWireLostCommitAckRefusesFreshApi() throws Exception {
        var profile = new E2aCrossSourceProfile(); profile.connect();
        var destination = E2aArchiveRestoreTarget.crossInstance(profile);
        for (Path home : List.of(profile.attempt, destination.attempt)) Files.createFile(home.resolve("test-started.marker"));
        try (var pool = E2aPointInTimeCheckpoint.pool(profile, profile.url)) {
            verify(profile, new JdbcTemplate(pool), destination);
        }
        for (Path home : List.of(profile.attempt, destination.attempt))
            Files.writeString(home.resolve("test-completed.marker"),
                "Bound cross-instance successor, real product commit with actual TCP acknowledgement loss, actual halt70, retained UNKNOWN and fresh API refusal; synthetic test-only",
                StandardOpenOption.CREATE_NEW);
    }

    private static void verify(E2aCrossSourceProfile profile, JdbcTemplate source,
            E2aArchiveRestoreTarget destination) throws Exception {
        profile.migrate(source, "34"); E2aV34Seed.seed(source, profile.attempt.resolve("storage"));
        profile.migrate(source); E2aCrossSourceHttpSetup.signing(profile.attempt);
        var input = E2aRestoredClosedAdmissionIT.prepare(profile, source);
        var restored = restore(profile, source, destination);
        var before = restored.copier().snapshot(source, profile.attempt.resolve("storage"));
        restored.supply().enableP5(restored.target(), profile.attempt);
        var authority = restored.supply().originals();
        assertThat(restored.copier().snapshot(restored.target(), restored.copier().restored.resolve("storage")))
            .isEqualTo(com.acrylic.outbound.submissions.SubmissionJson.parse(
                Files.readString(profile.attempt.resolve("private/restored-admitted.json"))));
        wrongSource(profile, restored.supply());
        exercise(profile, input, restored);
        RecoveryBoundWireCommitProof.finish(profile.attempt); assertThat(restored.supply().originals()).isEqualTo(authority);
        E2aRestoredClosedAdmissionIT.save(profile, "authority-originals.json", authority);
        var finalSource = restored.copier().snapshot(source, profile.attempt.resolve("storage"));
        assertThat(finalSource).isEqualTo(before);
        E2aRestoredClosedAdmissionIT.save(profile, "source-final.json", finalSource);
        var finalTriggers = triggers(restored.target());
        assertThat(finalTriggers).isEqualTo(triggers(source));
        E2aRestoredClosedAdmissionIT.save(profile, "target-trigger-final.json", finalTriggers);
    }

    private static Restored restore(E2aCrossSourceProfile profile, JdbcTemplate source,
            E2aArchiveRestoreTarget destination) throws Exception {
        var copier = new E2aArchiveSnapshotRestore(profile, source, destination);
        var roots = List.of(profile.attempt.resolve("data"), profile.attempt.resolve("storage"),
            destination.attempt.resolve("data"), copier.restored.resolve("storage"));
        var supply = new RecoverySuccessorRuntimeSupply(source, profile.attempt, roots);
        var target = copier.restore(); destination.requireIdentity(target); identities(profile, source, target);
        var originalTriggers = triggers(source); assertThat(triggers(target)).isEqualTo(originalTriggers).isNotEmpty();
        E2aRestoredClosedAdmissionIT.save(profile, "source-trigger-facts.json", originalTriggers);
        E2aRestoredClosedAdmissionIT.save(profile, "target-trigger-facts.json", triggers(target));
        var before = copier.snapshot(target, copier.restored.resolve("storage"));
        E2aRestoredClosedAdmissionIT.save(profile, "restored-before.json", before);
        var refused = supply.admission(target);
        assertThat(refused.admitted()).isFalse(); assertThat(refused.gaps()).contains("DATABASE_SERVER_UUID", "DATABASE_SCHEMA");
        E2aRestoredClosedAdmissionIT.save(profile, "copied-before-admission.json", refused);
        assertThat(copier.snapshot(target, copier.restored.resolve("storage"))).isEqualTo(before);
        supply.register(target, profile.attempt);
        var admitted = copier.snapshot(target, copier.restored.resolve("storage"));
        E2aSuccessorCompositionConservation.registration(before, admitted);
        E2aRestoredClosedAdmissionIT.save(profile, "restored-admitted.json", admitted);
        var denied = supply.admission(source);
        assertThat(denied.admitted()).isFalse(); assertThat(denied.gaps()).containsExactly("DATABASE_EVIDENCE_UNAVAILABLE");
        E2aRestoredClosedAdmissionIT.save(profile, "source-after-admission.json", denied);
        return new Restored(copier, target, supply, destination);
    }

    private static void exercise(E2aCrossSourceProfile profile, RecoveryExportInput input, Restored restored) throws Exception {
        var copier = restored.copier(); var target = restored.target();
        var before = copier.snapshot(target, copier.restored.resolve("storage"));
        try (var children = E2aSplitWorkerProcesses.boundWireCommit(profile, restored.supply().options(), restored.destination())) {
            children.start("api"); children.start("worker"); waitFor(children.home.resolve("api-p5-live.json"));
            new E2aRestoredGenerationExports(profile, input).consume(target);
            String delivered = com.acrylic.outbound.submissions.SubmissionJson.parse(
                Files.readString(profile.attempt.resolve("registered-restored-xlsx.json"))).path("id").asText();
            var http = new E2aWireCommitHttp(profile, input, delivered);
            marker(children.home, "arm-commit.marker"); http.create();
            refusal(profile, restored, new Fault(http, children));
            children.stop("api");
        }
        fresh(profile, restored);
        var after = copier.snapshot(target, copier.restored.resolve("storage"));
        E2aRestoredSchedulerConservation.verify(before, after);
        var originals = new ArrayList<com.fasterxml.jackson.databind.JsonNode>();
        before.at("/tables/background_job/rows").forEach(originals::add);
        var current = new ArrayList<com.fasterxml.jackson.databind.JsonNode>();
        after.at("/tables/background_job/rows").forEach(current::add);
        assertThat(current).containsAll(originals);
        E2aRestoredClosedAdmissionIT.save(profile, "restored-final.json", after);
    }

    private record Fault(E2aWireCommitHttp http, E2aSplitWorkerProcesses children) { }

    private static void refusal(E2aCrossSourceProfile profile, Restored restored, Fault fault) throws Exception {
        var children = fault.children();
        var copier = restored.copier(); var target = restored.target();
        waitFor(children.home.resolve("jdbc-before-commit.json"));
        fault.http().observe(target, copier, false);
        marker(children.home, "check-live.marker"); waitFor(children.home.resolve("api-live-accepted.marker"));
        RecoveryBoundWireCommitProof.owner(profile.attempt, true);
        RecoveryBoundWireCommitProof.snapshot(profile.attempt, "before-commit");
        marker(children.home, "commit-release.marker"); waitFor(children.home.resolve("wire-ack-held.json"));
        var stable = fault.http().observe(target, copier, true);
        E2aRestoredClosedAdmissionIT.save(profile, "refusal-before.json", stable);
        children.loseCommitAck(); RecoveryBoundWireCommitProof.owner(profile.attempt, false);
        RecoveryBoundWireCommitProof.unknown(profile.attempt);
        fault.http().refuse();
        marker(children.home, "check-commit.marker"); waitFor(children.home.resolve("api-commit-refused.json"));
        RecoveryBoundWireCommitProof.retirement(profile.attempt);
        var after = copier.snapshot(target, copier.restored.resolve("storage"));
        assertThat(after).isEqualTo(stable);
        E2aRestoredClosedAdmissionIT.save(profile, "refusal-after.json", after);
    }

    private static void fresh(E2aCrossSourceProfile profile, Restored restored) throws Exception {
        var before = restored.copier().snapshot(restored.target(), restored.copier().restored.resolve("storage"));
        try (var api = new E2aSplitWorkerProcesses(profile, restored.supply().options(), restored.destination())) {
            api.refuseApi();
        }
        var after = restored.copier().snapshot(restored.target(), restored.copier().restored.resolve("storage"));
        assertThat(after).isEqualTo(before);
        RecoveryBoundWireCommitProof.snapshot(profile.attempt, "after-successor");
    }

    private static void marker(Path home, String name) throws Exception {
        Files.writeString(home.resolve(name), "Owned bound worker handshake", StandardOpenOption.CREATE_NEW);
    }

    private static void waitFor(Path marker) {
        await().atMost(Duration.ofSeconds(30)).until(() -> Files.isRegularFile(marker));
    }

    private static void wrongSource(E2aCrossSourceProfile profile, RecoverySuccessorRuntimeSupply supply) throws Exception {
        var options = new ArrayList<>(List.of(supply.options())); options.add("--server.port=0");
        Throwable failure = catchThrowable(() -> {
            try (var unexpected = E2aCrossSourceHttpIT.start(profile, options.toArray(String[]::new))) {
                fail("Original source server/schema must not start with successor authority");
            }
        });
        assertThat(failure).hasStackTraceContaining("RECOVERY_DATABASE_ADMISSION_REFUSED:DATABASE_EVIDENCE_UNAVAILABLE");
        E2aRestoredClosedAdmissionIT.save(profile, "wrong-schema-startup.json", Map.of("exception", failure.toString()));
    }

    private static void identities(E2aCrossSourceProfile profile, JdbcTemplate source, JdbcTemplate target) throws Exception {
        String sql = "SELECT @@server_uuid AS uuid,DATABASE() AS schemaName,@@port AS port,@@datadir AS datadir,CURRENT_USER() AS userName";
        var original = source.queryForMap(sql); var copied = target.queryForMap(sql);
        assertThat(copied.get("uuid")).isNotEqualTo(original.get("uuid"));
        assertThat(copied.get("port")).isNotEqualTo(original.get("port"));
        assertThat(copied.get("datadir")).isNotEqualTo(original.get("datadir"));
        E2aRestoredClosedAdmissionIT.save(profile, "source-identity.json", original);
        E2aRestoredClosedAdmissionIT.save(profile, "target-identity.json", copied);
    }

    private static List<Map<String, Object>> triggers(JdbcTemplate jdbc) {
        return jdbc.queryForList("SELECT TRIGGER_NAME,EVENT_MANIPULATION,EVENT_OBJECT_TABLE,ACTION_ORDER,ACTION_CONDITION,"
            + "ACTION_STATEMENT,ACTION_ORIENTATION,ACTION_TIMING,SQL_MODE,DEFINER,CHARACTER_SET_CLIENT,"
            + "COLLATION_CONNECTION,DATABASE_COLLATION FROM information_schema.TRIGGERS "
            + "WHERE TRIGGER_SCHEMA=DATABASE() ORDER BY TRIGGER_NAME");
    }
}
