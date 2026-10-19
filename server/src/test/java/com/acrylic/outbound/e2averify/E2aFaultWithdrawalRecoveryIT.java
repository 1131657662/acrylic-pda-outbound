package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.FaultWithdrawalRecoveryScenario;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "E2A_CROSS_VERIFY", matches = "true")
class E2aFaultWithdrawalRecoveryIT {
    @Test void originalWithdrawalFactsSurviveFiveCheckpointsWithoutDuplicateAccounting() throws Exception {
        var profile = new E2aCrossSourceProfile(); profile.connect();
        try (var pool = E2aPointInTimeCheckpoint.pool(profile, profile.url)) {
            verify(profile, new JdbcTemplate(pool));
        }
    }

    private void verify(E2aCrossSourceProfile profile, JdbcTemplate source) throws Exception {
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(source); E2aCrossSourceHttpSetup.signing(profile.attempt);
        saveSigning(profile);
        var checkpoints = new ArrayList<E2aPointInTimeCheckpoint>();
        var scenario = sourceFlow(profile, source, checkpoints);
        checkpoints.add(new E2aPointInTimeCheckpoint(profile, source, "after-success", "_restore"));
        var expected = new E2aArchiveSnapshotRestore(profile, source).snapshot(source, profile.attempt.resolve("storage"));
        var triggers = E2aPointInTimeCheckpoint.triggers(source);
        Files.writeString(profile.attempt.resolve("final-facts.json"), expected.toPrettyString(), StandardOpenOption.CREATE_NEW);
        Files.writeString(profile.attempt.resolve("final-triggers.json"), triggers.toPrettyString(), StandardOpenOption.CREATE_NEW);
        E2aPointInTimeCommands.copyTree(profile.attempt.resolve("storage"), profile.attempt.resolve("final-storage"), profile.attempt);
        replay(profile, source, checkpoints, expected, triggers, scenario);
        assertThat(new E2aArchiveSnapshotRestore(profile, source).snapshot(source, profile.attempt.resolve("storage"))).isEqualTo(expected);
        Files.writeString(profile.attempt.resolve("test-completed.marker"), "Five exact checkpoints + original binlog + real HTTP replays; synthetic lab only.\n");
    }

    private FaultWithdrawalRecoveryScenario sourceFlow(E2aCrossSourceProfile profile, JdbcTemplate source,
            List<E2aPointInTimeCheckpoint> checkpoints) throws Exception {
        try (var context = E2aCrossSourceHttpIT.start(profile,
                "--server.servlet.session.cookie.secure=false",
                "--outbound.recall.fault-withdrawal-enabled=true")) {
            var scenario = new FaultWithdrawalRecoveryScenario((WebApplicationContext) context);
            checkpoints.add(new E2aPointInTimeCheckpoint(profile, source, "before-withdrawal", "_copy"));
            scenario.withdraw(); checkpoints.add(new E2aPointInTimeCheckpoint(profile, source, "after-withdrawal", "_conflict"));
            scenario.publish(); checkpoints.add(new E2aPointInTimeCheckpoint(profile, source, "after-republish", "_missing"));
            scenario.claim(); checkpoints.add(new E2aPointInTimeCheckpoint(profile, source, "before-success", "_gap"));
            scenario.succeed();
            scenario.save(profile.attempt.resolve("private/scenario.json"));
            return scenario;
        }
    }

    private void replay(E2aCrossSourceProfile profile, JdbcTemplate source, List<E2aPointInTimeCheckpoint> checkpoints,
            JsonNode expected, JsonNode triggers, FaultWithdrawalRecoveryScenario scenario) throws Exception {
        var end = source.queryForMap("SHOW MASTER STATUS"); String file = end.get("File").toString();
        assertThat(Path.of(file).getNameCount()).isEqualTo(1);
        source.execute("FLUSH BINARY LOGS");
        Path binlog = profile.attempt.resolve("original-binlog.bin");
        Files.copy(profile.attempt.resolve("data").resolve(file), binlog);
        String pin = E2aPointInTimeCheckpoint.sha(binlog);
        Files.writeString(profile.attempt.resolve("original-binlog.sha256"), pin, StandardOpenOption.CREATE_NEW);
        for (var checkpoint : checkpoints) {
          try (checkpoint) {
            var target = checkpoint.rebuild(binlog, pin, file, ((Number) end.get("Position")).longValue(), expected);
            assertThat(E2aPointInTimeCheckpoint.triggers(target)).isEqualTo(triggers);
             try (var context = E2aCrossSourceHttpIT.start(profile, "--server.servlet.session.cookie.secure=false",
                     "--spring.datasource.url=" + checkpoint.url,
                    "--outbound.storage=" + checkpoint.directory.resolve("final-storage"),
                    "--outbound.signing.private-key=" + checkpoint.directory.resolve("config/server-private.der"),
                    "--outbound.signing.public-key=" + checkpoint.directory.resolve("config/server-public.der"),
                    "--outbound.recall.fault-withdrawal-enabled=true")) {
                scenario.verifyRestored((WebApplicationContext) context, checkpoint.directory.resolve("http-result.json"));
            }
          }
        }
    }

    private void saveSigning(E2aCrossSourceProfile profile) throws Exception {
        // The runner owns the authenticated client profile and creates it before the JVM starts.
        // Keep that credential file immutable; checkpoint commands deliberately reuse it.
        assertThat(profile.attempt.resolve("private/client.cnf")).isRegularFile();
        Path directory = profile.attempt.resolve("private-signing"); Files.createDirectory(directory);
        for (String name : List.of("server-private.der", "server-public.der"))
            Files.copy(profile.attempt.resolve("private/" + name), directory.resolve(name));
    }
}
