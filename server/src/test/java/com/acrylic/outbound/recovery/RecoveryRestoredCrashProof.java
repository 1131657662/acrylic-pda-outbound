package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.*;

/** Reads the exact held process descriptor and proves OS lifecycle-lock release after halt. */
public final class RecoveryRestoredCrashProof {
    public static void owner(Path attempt, boolean alive) throws Exception {
        Path home = attempt.resolve("private/restored-p5-crash");
        var config = RecoveryControlHistory.read(attempt.resolve("private/p5-stale-authority/config.json"));
        var held = SubmissionJson.parse(Files.readString(home.resolve("worker-crash-held.json")));
        Path record = Path.of(config.path("lease").asText()).resolve("processes/"
            + held.path("lease").path("processIdentity").asText() + ".json");
        assertThat(RecoveryControlHistory.read(record)).isEqualTo(held.path("process"));
        boolean locked;
        try (var channel = FileChannel.open(record, StandardOpenOption.READ, StandardOpenOption.WRITE);
                var probe = channel.tryLock(1L << 30, 1, false)) { locked = probe == null; }
        assertThat(locked).isEqualTo(alive);
        var result = SubmissionJson.object().put("workerPid", held.path("pid").asLong())
            .put("expectedAlive", alive).put("lifecycleLockHeld", locked).put("processRecordUnchanged", true);
        Files.writeString(home.resolve(alive ? "owner-before.json" : "owner-after.json"), result.toPrettyString(),
            StandardOpenOption.CREATE_NEW);
    }

    public static com.fasterxml.jackson.databind.node.ObjectNode snapshot(Path attempt, String name) throws Exception {
        Path home = attempt.resolve("private/p5-stale-authority");
        var result = SubmissionJson.object();
        try (var files = Files.walk(home.resolve("p5"))) {
            for (Path path : files.filter(Files::isRegularFile).sorted().toList())
                result.putObject(home.resolve("p5").relativize(path).toString().replace((char) 92, '/'))
                    .put("bytes", Files.size(path)).put("sha256", RecoveryControlFormat.sha256(Files.readAllBytes(path)));
        }
        Files.writeString(home.resolve(name + "-p5.json"), result.toPrettyString(), StandardOpenOption.CREATE_NEW);
        return result;
    }

    public static void retirement(Path attempt) throws Exception {
        Path home = attempt.resolve("private/p5-stale-authority");
        var before = SubmissionJson.parse(Files.readString(home.resolve("before-crash-p5.json")));
        var after = snapshot(attempt, "after-crash");
        var held = SubmissionJson.parse(Files.readString(attempt.resolve("private/restored-p5-crash/worker-crash-held.json")));
        String lease = "leases/" + held.path("lease").path("operationId").asText() + ".json";
        assertThat(after.has(lease)).isFalse();
        var expected = before.deepCopy(); ((com.fasterxml.jackson.databind.node.ObjectNode) expected).remove(lease);
        ((com.fasterxml.jackson.databind.node.ObjectNode) expected).remove("leases/index.json");
        var actual = after.deepCopy(); actual.remove("leases/index.json");
        assertThat(SubmissionJson.canonical(actual)).isEqualTo(SubmissionJson.canonical(expected));
        var config = RecoveryControlHistory.read(home.resolve("config.json"));
        Path p5 = Path.of(config.path("lease").asText());
        assertThat(RecoveryControlHistory.read(p5.resolve("leases/index.json")).path("entries")).isEmpty();
    }
}
