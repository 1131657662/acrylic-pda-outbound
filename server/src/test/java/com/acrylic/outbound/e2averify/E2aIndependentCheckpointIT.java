package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.IndependentCheckpointScenario;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/** A pre-association restore retains independent originals; exact rebuild never manufactures dispatch. */
@EnabledIfEnvironmentVariable(named = "E2A_CROSS_VERIFY", matches = "true")
class E2aIndependentCheckpointIT {
    @Test void independentAssociationRestoresOriginalEvidenceWithoutCreatingDispatch() throws Exception {
        var profile = new E2aCrossSourceProfile(); profile.connect();
        try (var pool = E2aPointInTimeCheckpoint.pool(profile, profile.url)) {
            var source = new JdbcTemplate(pool);
            initialize(profile, source);
            var flow = sourceFlow(profile, source);
            try (var checkpoint = flow.checkpoint()) {
                var frozen = freeze(profile, source);
                prefix(profile, checkpoint, flow.scenario());
                rebuilt(profile, checkpoint, new Replay(flow.scenario(), frozen));
                assertThat(new E2aArchiveSnapshotRestore(profile, source).snapshot(source, profile.attempt.resolve("storage")))
                    .isEqualTo(frozen.path("facts"));
            }
            Files.writeString(profile.attempt.resolve("test-completed.marker"),
                "Pre-association restore has no association; original native package, signed evidence and business-free linkage survive original binlog rebuild.\n",
                StandardOpenOption.CREATE_NEW);
        }
    }

    private void initialize(E2aCrossSourceProfile profile, JdbcTemplate source) throws Exception {
        IndependentCheckpointSeed.initialize(profile, source);
    }

    private Flow sourceFlow(E2aCrossSourceProfile profile, JdbcTemplate source) throws Exception {
        try (var context = start(profile)) {
            var scenario = new IndependentCheckpointScenario((WebApplicationContext) context, profile.attempt);
            var checkpoint = new E2aPointInTimeCheckpoint(profile, source, "before-association", "_restore");
            scenario.complete((WebApplicationContext) context); return new Flow(checkpoint, scenario);
        }
    }

    private JsonNode freeze(E2aCrossSourceProfile profile, JdbcTemplate source) throws Exception {
        var result = SubmissionJson.object();
        result.set("facts", new E2aArchiveSnapshotRestore(profile, source).snapshot(source, profile.attempt.resolve("storage")));
        result.set("triggers", E2aPointInTimeCheckpoint.triggers(source));
        E2aPointInTimeCommands.copyTree(profile.attempt.resolve("storage"), profile.attempt.resolve("final-storage"), profile.attempt);
        var end = source.queryForMap("SHOW MASTER STATUS"); String file = end.get("File").toString();
        assertThat(Path.of(file).getNameCount()).isEqualTo(1);
        result.put("file", file).put("position", ((Number) end.get("Position")).longValue());
        source.execute("FLUSH BINARY LOGS");
        Path binlog = profile.attempt.resolve("original-binlog.bin");
        Files.copy(profile.attempt.resolve("data").resolve(file), binlog);
        result.put("binlogSha256", E2aPointInTimeCheckpoint.sha(binlog));
        Files.writeString(profile.attempt.resolve("final-facts.json"), result.toPrettyString(), StandardOpenOption.CREATE_NEW);
        return result;
    }

    private void prefix(E2aCrossSourceProfile profile, E2aPointInTimeCheckpoint checkpoint,
            IndependentCheckpointScenario scenario) throws Exception {
        String schema = profile.database + "_missing";
        String url = profile.url.replace("/" + profile.database + "?", "/" + schema + "?");
        try (var pool = E2aPointInTimeCheckpoint.pool(profile, url)) {
            var target = new JdbcTemplate(pool); requireEmpty(profile, target);
            target.execute("ALTER DATABASE `" + schema + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs");
            new E2aPointInTimeCommands(profile).restore(schema, checkpoint.directory.resolve("database.sql"),
                checkpoint.directory.resolve("prefix-import.stdout"));
            var before = SubmissionJson.parse(Files.readAllBytes(checkpoint.directory.resolve("before.json")));
            var snapshots = new E2aArchiveSnapshotRestore(profile, target);
            assertThat(snapshots.snapshot(target, checkpoint.directory.resolve("storage"))).isEqualTo(before);
            try (var context = start(profile, "--spring.datasource.url=" + url,
                    "--outbound.storage=" + checkpoint.directory.resolve("storage"),
                    "--outbound.rescue.independent-shortfall-enabled=false")) {
                scenario.verifyShortfallOff((WebApplicationContext) context, checkpoint.directory.resolve("shortfall-off-http"));
                scenario.verifyMissing((WebApplicationContext) context, checkpoint.directory.resolve("missing-http"));
            }
        }
    }

    private void rebuilt(E2aCrossSourceProfile profile, E2aPointInTimeCheckpoint checkpoint, Replay replay) throws Exception {
        var frozen = replay.frozen();
        var target = checkpoint.rebuild(profile.attempt.resolve("original-binlog.bin"), frozen.path("binlogSha256").asText(),
            frozen.path("file").asText(), frozen.path("position").asLong(), frozen.path("facts"));
        assertThat(E2aPointInTimeCheckpoint.triggers(target)).isEqualTo(frozen.path("triggers"));
        String storage = "--outbound.storage=" + checkpoint.directory.resolve("final-storage");
        String url = "--spring.datasource.url=" + checkpoint.url;
        try (var context = start(profile, url, storage)) {
            replay.scenario().verifyRestored((WebApplicationContext) context, checkpoint.directory.resolve("rebuilt-http"));
        }
        try (var context = start(profile, url, storage, "--outbound.rescue.independent-association-enabled=false",
                "--outbound.rescue.independent-physical-enabled=false")) {
            replay.scenario().verifyDefaultOff((WebApplicationContext) context, checkpoint.directory.resolve("default-off-http"));
        }
    }

    private void requireEmpty(E2aCrossSourceProfile profile, JdbcTemplate jdbc) {
        assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).isEqualTo(profile.database + "_missing");
        assertThat(jdbc.queryForObject("SELECT CURRENT_USER()", String.class)).isEqualTo(profile.user + "@127.0.0.1");
        assertThat(jdbc.queryForObject("SELECT @@port", Integer.class)).isEqualTo(E2aTestPorts.database());
        assertThat(Path.of(jdbc.queryForObject("SELECT @@datadir", String.class)).toAbsolutePath().normalize())
            .isEqualTo(profile.attempt.resolve("data").toAbsolutePath().normalize());
        assertThat(jdbc.queryForObject("SELECT @@log_bin", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForList("SHOW TABLES")).isEmpty();
    }

    private org.springframework.context.ConfigurableApplicationContext start(E2aCrossSourceProfile profile, String... overrides) {
        var options = new ArrayList<>(List.of("--server.servlet.session.cookie.secure=false",
            "--outbound.rescue.relay-enabled=true",
            "--outbound.rescue.independent-shortfall-enabled=true",
            "--outbound.rescue.independent-physical-enabled=true", "--outbound.rescue.independent-association-enabled=true"));
        options.addAll(List.of(overrides)); return E2aCrossSourceHttpIT.start(profile, options.toArray(String[]::new));
    }

    private record Flow(E2aPointInTimeCheckpoint checkpoint, IndependentCheckpointScenario scenario) { }
    private record Replay(IndependentCheckpointScenario scenario, JsonNode frozen) { }
}
