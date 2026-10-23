package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.RecoveryExportInput;
import com.acrylic.outbound.recovery.RecoveryRestoredDatabaseAdmission;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.*;

/** Actual full dump, offline projection/registration, product API/Session/nonweb worker/files. */
@EnabledIfEnvironmentVariable(named = "E2A_CROSS_VERIFY", matches = "true")
class E2aRestoredClosedAdmissionIT {
    private record Restored(E2aArchiveSnapshotRestore copier, JdbcTemplate target,
            ObjectNode before, RecoveryRestoredDatabaseAdmission supply) { }

    @Test void restoredUnboundSnapshotRegistersThenServesWhileOriginalSchemaRemainsRefused() throws Exception {
        var profile = new E2aCrossSourceProfile(); profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        try (var sourcePool = E2aPointInTimeCheckpoint.pool(profile, profile.url)) {
            var source = new JdbcTemplate(sourcePool);
            profile.migrate(source, "34");
            var seed = E2aV34Seed.seed(source, profile.attempt.resolve("storage"));
            profile.migrate(source); E2aCrossSourceHttpSetup.signing(profile.attempt);
            var pending = prepare(profile, source);
            var restored = restore(profile, source, seed);
            var authority = restored.supply().originals();
            wrongSchema(profile, source, restored);
            exercise(profile, pending, restored);
            assertThat(restored.supply().originals()).isEqualTo(authority);
            save(profile, "authority-originals.json", authority);
            var finalSource = restored.copier().snapshot(source, profile.attempt.resolve("storage"));
            assertThat(finalSource).isEqualTo(restored.before());
            save(profile, "source-final.json", finalSource);
        }
        Files.writeString(profile.attempt.resolve("test-completed.marker"),
            "Full unbound restore and product offline registration/API/worker/file combination; synthetic authority, no P5",
            StandardOpenOption.CREATE_NEW);
    }

    private static Restored restore(E2aCrossSourceProfile profile, JdbcTemplate source, Map<String, String> seed) throws Exception {
        var copier = new E2aArchiveSnapshotRestore(profile, source);
        var target = copier.restore();
        var before = copier.snapshot(target, copier.restored.resolve("storage"));
        save(profile, "restored-before.json", before);
        var supply = new RecoveryRestoredDatabaseAdmission(target.getDataSource(), profile.attempt, seed);
        supply.register();
        var admitted = copier.snapshot(target, copier.restored.resolve("storage"));
        save(profile, "restored-admitted.json", admitted);
        E2aRestoredAdmissionConservation.registration(before, admitted);
        return new Restored(copier, target, before, supply);
    }

    private static void wrongSchema(E2aCrossSourceProfile profile, JdbcTemplate source, Restored restored) throws Exception {
        var options = new ArrayList<>(List.of(restored.supply().options())); options.add("--server.port=0");
        Throwable failure = catchThrowable(() -> {
            try (var unexpected = E2aCrossSourceHttpIT.start(profile, options.toArray(String[]::new))) {
                fail("Unregistered original schema started with restored authority");
            }
        });
        assertThat(failure).hasStackTraceContaining("RECOVERY_DATABASE_ADMISSION_REFUSED");
        assertThat(restored.copier().snapshot(source, profile.attempt.resolve("storage"))).isEqualTo(restored.before());
        save(profile, "wrong-schema-startup.json", Map.of("exception", failure.toString(), "sourceUnchanged", true));
    }

    private static void exercise(E2aCrossSourceProfile profile, RecoveryExportInput input, Restored restored) throws Exception {
        var copier = restored.copier(); var target = restored.target();
        var admitted = copier.snapshot(target, copier.restored.resolve("storage"));
        try (var children = new E2aSplitWorkerProcesses(profile, restored.supply().options(), "restored-split-successor")) {
            children.start("api"); children.start("worker");
            new E2aRestoredGenerationExports(profile, input).consume(target);
            children.stop("worker");
            refuse(profile, input, restored);
            children.stop("api");
        }
        var after = copier.snapshot(target, copier.restored.resolve("storage"));
        E2aRestoredSchedulerConservation.verify(admitted, after);
        var originals = new ArrayList<com.fasterxml.jackson.databind.JsonNode>();
        admitted.at("/tables/background_job/rows").forEach(originals::add);
        var current = new ArrayList<com.fasterxml.jackson.databind.JsonNode>();
        after.at("/tables/background_job/rows").forEach(current::add);
        assertThat(current).containsAll(originals);
        save(profile, "restored-final.json", after);
    }

    private static void refuse(E2aCrossSourceProfile profile, RecoveryExportInput input, Restored restored) throws Exception {
        String delivered = com.acrylic.outbound.submissions.SubmissionJson.parse(
            Files.readString(profile.attempt.resolve("registered-restored-xlsx.json"))).path("id").asText();
        var http = new E2aRestoredStaleHttp(profile, input, delivered); http.pending(restored.target(), 5);
        restored.target().update("UPDATE server_state SET service_mode='RECOVERY_PENDING' WHERE id=1");
        var before = restored.copier().snapshot(restored.target(), restored.copier().restored.resolve("storage"));
        save(profile, "refusal-before.json", before);
        http.refuse("RECOVERY_DATABASE_ADMISSION_REFUSED");
        var after = restored.copier().snapshot(restored.target(), restored.copier().restored.resolve("storage"));
        assertThat(after).isEqualTo(before);
        save(profile, "refusal-after.json", after);
        restored.target().update("UPDATE server_state SET service_mode='NORMAL' WHERE id=1");
    }

    private static RecoveryExportInput prepare(E2aCrossSourceProfile profile, JdbcTemplate source) throws Exception {
        try (var web = E2aCrossSourceHttpIT.start(profile, "--server.servlet.session.cookie.secure=false",
                "--outbound.exports.sample-data=true", "--outbound.exports.renderer-home=" + System.getenv("E2A_SPLIT_RENDERER"),
                "--outbound.exports.browsers=" + System.getenv("E2A_SPLIT_BROWSERS"))) {
            var input = RecoveryExportInput.seed((WebApplicationContext) web);
            var exports = new E2aRestoredSchedulerExports(profile, input); exports.prepare(source);
            return input;
        }
    }

    private static void save(E2aCrossSourceProfile profile, String name, Object value) throws Exception {
        Files.writeString(profile.attempt.resolve("private/" + name),
            new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(value), StandardOpenOption.CREATE_NEW);
    }
}
