package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.recovery.RecoverySuccessorRuntimeSupply;
import com.acrylic.outbound.inventory.RecoveryExportInput;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

/** Real bound nonempty dump and successor admission in the actual API/worker/P5/file combination. */
@EnabledIfEnvironmentVariable(named = "E2A_CROSS_VERIFY", matches = "true")
class E2aSuccessorRestoredP5IT {
    private record Restored(E2aArchiveSnapshotRestore copier, JdbcTemplate target,
            RecoverySuccessorRuntimeSupply supply) { }

    @Test void retainedBindingRestoreServesNewGenerationExportsWithLiveP5() throws Exception {
        var profile = new E2aCrossSourceProfile(); profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        try (var pool = E2aPointInTimeCheckpoint.pool(profile, profile.url)) {
            verify(profile, new JdbcTemplate(pool));
        }
        Files.writeString(profile.attempt.resolve("test-completed.marker"),
            "Bound nonempty restore/successor admission/API/separate worker/live P5/files; synthetic test-only promotion",
            StandardOpenOption.CREATE_NEW);
    }

    private static void verify(E2aCrossSourceProfile profile, JdbcTemplate source) throws Exception {
        profile.migrate(source, "34"); E2aV34Seed.seed(source, profile.attempt.resolve("storage"));
        profile.migrate(source); E2aCrossSourceHttpSetup.signing(profile.attempt);
        var input = E2aRestoredClosedAdmissionIT.prepare(profile, source);
        var copier = new E2aArchiveSnapshotRestore(profile, source);
        var supply = new RecoverySuccessorRuntimeSupply(source, profile.attempt);
        var target = copier.restore(); var before = copier.snapshot(target, copier.restored.resolve("storage"));
        E2aRestoredClosedAdmissionIT.save(profile, "restored-before.json", before);
        supply.register(target, profile.attempt);
        var admitted = copier.snapshot(target, copier.restored.resolve("storage"));
        E2aSuccessorCompositionConservation.registration(before, admitted);
        E2aRestoredClosedAdmissionIT.save(profile, "restored-admitted.json", admitted);
        var p5 = supply.enableP5(target, profile.attempt); var authority = supply.originals();
        assertThat(copier.snapshot(target, copier.restored.resolve("storage"))).isEqualTo(admitted);
        wrongSchema(profile, supply);
        exercise(profile, input, new Restored(copier, target, supply));
        p5.finish(); assertThat(supply.originals()).isEqualTo(authority);
        E2aRestoredClosedAdmissionIT.save(profile, "authority-originals.json", authority);
        var finalSource = copier.snapshot(source, profile.attempt.resolve("storage"));
        assertThat(finalSource).isEqualTo(before);
        E2aRestoredClosedAdmissionIT.save(profile, "source-final.json", finalSource);
    }

    private static void wrongSchema(E2aCrossSourceProfile profile, RecoverySuccessorRuntimeSupply supply) throws Exception {
        var options = new ArrayList<>(List.of(supply.options())); options.add("--server.port=0");
        Throwable failure = catchThrowable(() -> {
            try (var unexpected = E2aCrossSourceHttpIT.start(profile, options.toArray(String[]::new))) {
                fail("Original source schema must not start with successor authority");
            }
        });
        assertThat(failure).hasStackTraceContaining("RECOVERY_DATABASE_ADMISSION_REFUSED");
        E2aRestoredClosedAdmissionIT.save(profile, "wrong-schema-startup.json", Map.of("exception", failure.toString()));
    }

    private static void exercise(E2aCrossSourceProfile profile, RecoveryExportInput input, Restored restored) throws Exception {
        var copier = restored.copier(); var target = restored.target();
        var before = copier.snapshot(target, copier.restored.resolve("storage"));
        try (var children = new E2aSplitWorkerProcesses(profile, restored.supply().options(), "restored-closed-p5")) {
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
}
