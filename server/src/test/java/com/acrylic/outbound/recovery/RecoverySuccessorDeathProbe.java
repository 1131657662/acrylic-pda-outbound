package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;
import static org.assertj.core.api.Assertions.*;

/** Disposable Python writer death; immutable command resumption and read-only pending observations. */
final class RecoverySuccessorDeathProbe {
    private final RecoverySuccessorDatabaseFixture fixture;
    private final RecoveryDatabaseSource.Pins source;
    private final Path home;
    private final String point;
    private Map<String, String> original;
    private long revision;

    RecoverySuccessorDeathProbe(RecoverySuccessorDatabaseFixture fixture, RecoveryDatabaseSource.Pins source, Path attempt) throws Exception {
        this.fixture = fixture; this.source = source;
        home = Files.createDirectory(attempt.resolve("private/successor-death"));
        point = System.getenv("E2A_SUCCESSOR_DEATH_POINT");
        assertThat(point).isIn("intent_appended", "intent_head_staged", "intent_head_committed",
            "history_appended", "pointer_staged", "pointer_committed");
    }

    void interrupt(JdbcTemplate target) throws Exception {
        var previous = fixture.head(); revision = revision(previous, "revision");
        var command = (ObjectNode) previous.path("command").deepCopy();
        command.put("formatVersion", 2).put("commandId", UUID.randomUUID().toString())
            .put("expectedRevision", text(previous, "revision")).put("phase", "QUIESCING")
            .put("recoverySessionId", UUID.randomUUID().toString())
            .put("predecessorReleaseSha256", source.predecessorReleaseSha256())
            .put("predecessorNormalIntentSha256", source.predecessorNormalIntentSha256())
            .put("sourceBindingSha256", source.sourceBindingSha256()).put("sourceNormalSha256", source.sourceNormalSha256())
            .put("targetServerUuid", target.queryForObject("SELECT @@server_uuid", String.class))
            .put("targetDatabaseSchema", target.queryForObject("SELECT DATABASE()", String.class));
        write(fixture.base().resolve("death-original.json"), command);
        write(home.resolve("command.json"), command); Files.copy(fixture.checkpoint(), home.resolve("checkpoint.json"));
        original = snapshot("before");
        assertThat(execute(point, "death-original.json", "death")).isEqualTo(73);
        assertThat(Files.readString(home.resolve("death.stdout"))).isEmpty();
        assertThat(Files.readString(home.resolve("death.stderr"))).isEmpty();
        snapshot("interrupted"); reader();
        var beforeConflict = snapshot("before-conflict");
        command.put("commandId", UUID.randomUUID().toString()); write(fixture.base().resolve("death-conflict.json"), command);
        assertThat(execute("apply", "death-conflict.json", "conflict")).isEqualTo(2);
        assertThat(Files.readString(home.resolve("conflict.stdout")))
            .containsAnyOf("CONTROL_INTENT_CONFLICT", "CONTROL_CAS_CONFLICT", "CONTROL_COMMAND_INCOMPLETE");
        assertThat(snapshot("after-conflict")).isEqualTo(beforeConflict);
    }

    private void reader() throws Exception {
        var location = fixture.location();
        var reader = new RecoveryControlReader(location);
        var result = RecoveryControlFixture.JSON.createObjectNode().put("point", point);
        String code = switch (point) {
            case "intent_appended" -> "CONTROL_INTENT_INCOMPLETE";
            case "intent_head_staged" -> "CONTROL_UNKNOWN_ARTIFACT";
            case "history_appended", "pointer_staged" -> "CONTROL_COMMAND_INCOMPLETE";
            default -> null;
        };
        if (code != null) {
            assertThatThrownBy(() -> reader.read(fixture.checkpoint())).hasMessage(code);
            result.put("readerCode", code);
        } else {
            var observed = reader.read(fixture.checkpoint());
            long expected = point.equals("pointer_committed") ? revision + 1 : revision;
            assertThat(observed.revision()).isEqualTo(Long.toString(expected));
            result.put("readerRevision", expected);
            if (point.equals("intent_head_committed")) {
                assertThatThrownBy(() -> RecoveryControlLock.locked(location.root(), () -> RecoveryWriterLease.acquire(location.root())))
                    .hasMessage("CONTROL_CLOSING_INTENT");
                result.put("writerCode", "CONTROL_CLOSING_INTENT");
            }
        }
        write(home.resolve("reader.json"), result);
    }

    void resume() throws Exception {
        var interrupted = snapshot("after-observers");
        assertThat(interrupted).isEqualTo(RecoveryControlFixture.JSON.readValue(
            Files.readAllBytes(home.resolve("after-conflict.json")), new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() { }));
        assertThat(execute("apply", "death-original.json", "resume")).isZero();
        var resumed = snapshot("resumed");
        assertThat(execute("apply", "death-original.json", "replay")).isZero();
        assertThat(snapshot("replayed")).isEqualTo(resumed);
        assertThat(Files.readAllBytes(home.resolve("resume.stdout"))).isEqualTo(Files.readAllBytes(home.resolve("replay.stdout")));
        assertThat(fixture.head().path("command")).isEqualTo(RecoveryControlHistory.read(home.resolve("command.json")));
        for (var item : original.entrySet())
            if (!List.of("current.json", "admission.json").contains(item.getKey()))
                assertThat(resumed.get(item.getKey())).as(item.getKey()).isEqualTo(item.getValue());
        write(home.resolve("result.json"), RecoveryControlFixture.JSON.valueToTree(Map.of("point", point, "deathExitCode", 73, "conflictExitCode", 2,
            "originalResumeExitCode", 0, "replayExitCode", 0, "originalsPreserved", true)));
    }

    private int execute(String mode, String request, String label) throws Exception {
        var root = Path.of("D:/acrylic-pda-outbound");
        var args = List.of(root.resolve(".local/r09-control-venv/Scripts/python.exe").toString(),
            root.resolve("docs/parallel/R09/tests/control_process.py").toString(), fixture.base().toString(), mode, request);
        write(home.resolve(label + "-command.json"), RecoveryControlFixture.JSON.valueToTree(args));
        var process = new ProcessBuilder(args).redirectOutput(home.resolve(label + ".stdout").toFile())
            .redirectError(home.resolve(label + ".stderr").toFile()).start();
        try {
            assertThat(process.waitFor(30, TimeUnit.SECONDS)).isTrue();
            write(home.resolve(label + "-exit.json"), RecoveryControlFixture.JSON.valueToTree(
                Map.of("pid", process.pid(), "exitCode", process.exitValue(), "aliveAfter", process.isAlive())));
            assertThat(Files.readString(home.resolve(label + ".stderr"))).isEmpty();
            return process.exitValue();
        } finally {
            if (process.isAlive()) { process.destroyForcibly(); assertThat(process.waitFor(10, TimeUnit.SECONDS)).isTrue(); }
        }
    }

    private Map<String, String> snapshot(String name) throws Exception {
        Path destination = Files.createDirectory(home.resolve(name));
        var result = new TreeMap<String, String>();
        try (var files = Files.walk(fixture.location().root())) {
            for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                Path relative = fixture.location().root().relativize(file); Path copy = destination.resolve(relative);
                Files.createDirectories(copy.getParent()); Files.copy(file, copy);
                String hash = sha256(Files.readAllBytes(file));
                assertThat(sha256(Files.readAllBytes(copy))).isEqualTo(hash);
                result.put(relative.toString().replace((char) 92, '/'), hash);
            }
        }
        write(home.resolve(name + ".json"), RecoveryControlFixture.JSON.valueToTree(result));
        return result;
    }

    private static void write(Path path, com.fasterxml.jackson.databind.JsonNode value) throws Exception {
        Files.writeString(path, canonical(value), StandardOpenOption.CREATE_NEW);
    }
}
