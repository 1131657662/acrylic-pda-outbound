package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.recovery.RecoverySuccessorRuntimeSupply;
import com.acrylic.outbound.inventory.RecoveryExportInput;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

/** Bound full restore on a different physical server, consumed by actual API/worker/P5/files. */
@EnabledIfEnvironmentVariable(named = "E2A_COPY_VERIFY", matches = "true")
class E2aSuccessorCrossInstanceDeathIT {
    private record Restored(E2aArchiveSnapshotRestore copier, JdbcTemplate target,
            RecoverySuccessorRuntimeSupply supply, E2aArchiveRestoreTarget destination) { }

    @Test void interruptedSuccessorResumesOnlyOriginalBeforeCrossInstanceProductsServe() throws Exception {
        var profile = new E2aCrossSourceProfile(); profile.connect();
        var destination = E2aArchiveRestoreTarget.crossInstance(profile);
        for (Path home : List.of(profile.attempt, destination.attempt)) Files.createFile(home.resolve("test-started.marker"));
        try (var pool = E2aPointInTimeCheckpoint.pool(profile, profile.url)) {
            verify(profile, new JdbcTemplate(pool), destination);
        }
        for (Path home : List.of(profile.attempt, destination.attempt))
            Files.writeString(home.resolve("test-completed.marker"),
                "Actual successor death/resume on bound cross-instance restore/API/separate worker/P5/files; synthetic test-only",
                StandardOpenOption.CREATE_NEW);
    }

    private static void verify(E2aCrossSourceProfile profile, JdbcTemplate source,
            E2aArchiveRestoreTarget destination) throws Exception {
        profile.migrate(source, "34"); E2aV34Seed.seed(source, profile.attempt.resolve("storage"));
        profile.migrate(source); E2aCrossSourceHttpSetup.signing(profile.attempt);
        var input = E2aRestoredClosedAdmissionIT.prepare(profile, source);
        var restored = restore(profile, source, destination);
        var before = restored.copier().snapshot(source, profile.attempt.resolve("storage"));
        var p5 = restored.supply().enableP5(restored.target(), profile.attempt);
        var authority = restored.supply().originals();
        assertThat(restored.copier().snapshot(restored.target(), restored.copier().restored.resolve("storage")))
            .isEqualTo(com.acrylic.outbound.submissions.SubmissionJson.parse(
                Files.readString(profile.attempt.resolve("private/restored-admitted.json"))));
        wrongSource(profile, restored.supply());
        exercise(profile, input, restored);
        p5.finish(); assertThat(restored.supply().originals()).isEqualTo(authority);
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
        supply.registerAfterDeath(target, profile.attempt,
            () -> pending(profile, new Restored(copier, target, supply, destination)));
        var admitted = copier.snapshot(target, copier.restored.resolve("storage"));
        E2aSuccessorCompositionConservation.registration(before, admitted);
        E2aRestoredClosedAdmissionIT.save(profile, "restored-admitted.json", admitted);
        var denied = supply.admission(source);
        assertThat(denied.admitted()).isFalse(); assertThat(denied.gaps()).containsExactly("DATABASE_EVIDENCE_UNAVAILABLE");
        E2aRestoredClosedAdmissionIT.save(profile, "source-after-admission.json", denied);
        return new Restored(copier, target, supply, destination);
    }

    private static void pending(E2aCrossSourceProfile profile, Restored restored) throws Exception {
        var before = restored.copier().snapshot(restored.target(), restored.copier().restored.resolve("storage"));
        E2aRestoredClosedAdmissionIT.save(profile, "death-database-before.json", before);
        var refused = restored.supply().admission(restored.target());
        assertThat(refused.admitted()).isFalse();
        E2aRestoredClosedAdmissionIT.save(profile, "death-runtime-admission.json", refused);
        E2aSuccessorPendingStarts.verify(profile, restored.supply().options(), restored.destination());
        var after = restored.copier().snapshot(restored.target(), restored.copier().restored.resolve("storage"));
        assertThat(after).isEqualTo(before);
        E2aRestoredClosedAdmissionIT.save(profile, "death-database-after.json", after);
    }

    private static void exercise(E2aCrossSourceProfile profile, RecoveryExportInput input, Restored restored) throws Exception {
        var copier = restored.copier(); var target = restored.target();
        var before = copier.snapshot(target, copier.restored.resolve("storage"));
        try (var children = new E2aSplitWorkerProcesses(profile, restored.supply().options(), restored.destination())) {
            children.start("api"); children.start("worker");
            new E2aRestoredGenerationExports(profile, input).consume(target);
            children.stop("worker"); refuse(profile, input, restored); children.stop("api");
        }
        var after = copier.snapshot(target, copier.restored.resolve("storage"));
        E2aRestoredSchedulerConservation.verify(before, after);
        var originals = new ArrayList<com.fasterxml.jackson.databind.JsonNode>();
        before.at("/tables/background_job/rows").forEach(originals::add);
        var current = new ArrayList<com.fasterxml.jackson.databind.JsonNode>();
        after.at("/tables/background_job/rows").forEach(current::add);
        assertThat(current).containsAll(originals);
        E2aRestoredClosedAdmissionIT.save(profile, "restored-final.json", after);
    }

    private static void refuse(E2aCrossSourceProfile profile, RecoveryExportInput input, Restored restored) throws Exception {
        var copier = restored.copier(); var target = restored.target();
        String delivered = com.acrylic.outbound.submissions.SubmissionJson.parse(
            Files.readString(profile.attempt.resolve("registered-restored-xlsx.json"))).path("id").asText();
        var http = new E2aRestoredStaleHttp(profile, input, delivered); http.pending(target, 5);
        target.update("UPDATE server_state SET service_mode='RECOVERY_PENDING' WHERE id=1");
        var before = copier.snapshot(target, copier.restored.resolve("storage"));
        E2aRestoredClosedAdmissionIT.save(profile, "refusal-before.json", before);
        http.refuse("RECOVERY_DATABASE_ADMISSION_REFUSED");
        var after = copier.snapshot(target, copier.restored.resolve("storage"));
        assertThat(after).isEqualTo(before);
        E2aRestoredClosedAdmissionIT.save(profile, "refusal-after.json", after);
        target.update("UPDATE server_state SET service_mode='NORMAL' WHERE id=1");
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
