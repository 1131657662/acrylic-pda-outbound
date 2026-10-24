package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.*;

/** Exact P5 conservation across real TCP acknowledgement loss and persistent UNKNOWN observation. */
public final class RecoveryBoundWireCommitProof {
    private static Path home(Path attempt) { return attempt.resolve("private/restored-bound-wire-commit"); }
    private static Path supply(Path attempt) { return attempt.resolve("private/restored-admission-p5"); }

    public static void owner(Path attempt, boolean alive) throws Exception {
        var held = SubmissionJson.parse(Files.readString(home(attempt).resolve("worker-commit-held.json")));
        Path record = supply(attempt).resolve("p5/processes/" + held.at("/lease/processIdentity").asText() + ".json");
        assertThat(RecoveryControlHistory.read(record)).isEqualTo(held.path("process"));
        boolean locked;
        try (var channel = FileChannel.open(record, StandardOpenOption.READ, StandardOpenOption.WRITE);
                var probe = channel.tryLock(1L << 30, 1, false)) { locked = probe == null; }
        assertThat(locked).isEqualTo(alive);
        var result = SubmissionJson.object().put("workerPid", held.path("pid").asLong())
            .put("expectedAlive", alive).put("lifecycleLockHeld", locked).put("processRecordUnchanged", true);
        Files.writeString(home(attempt).resolve(alive ? "owner-before.json" : "owner-after.json"),
            result.toPrettyString(), StandardOpenOption.CREATE_NEW);
    }

    public static com.fasterxml.jackson.databind.node.ObjectNode snapshot(Path attempt, String name) throws Exception {
        Path root = supply(attempt).resolve("p5");
        var result = SubmissionJson.object();
        try (var files = Files.walk(root)) {
            for (Path path : files.filter(Files::isRegularFile).sorted().toList())
                result.putObject(root.relativize(path).toString().replace((char) 92, '/'))
                    .put("bytes", Files.size(path)).put("sha256", RecoveryControlFormat.sha256(Files.readAllBytes(path)));
        }
        Files.writeString(supply(attempt).resolve(name + "-p5.json"), result.toPrettyString(), StandardOpenOption.CREATE_NEW);
        return result;
    }

    public static void unknown(Path attempt) throws Exception {
        var held = SubmissionJson.parse(Files.readString(home(attempt).resolve("worker-commit-held.json")));
        String operation = held.at("/lease/operationId").asText();
        Path record = supply(attempt).resolve("p5/leases/" + operation + ".unknown.json");
        var unknown = RecoveryControlHistory.read(record);
        assertThat(unknown.path("operationId").asText()).isEqualTo(operation);
        assertThat(unknown.path("processIdentity").asText()).isEqualTo(held.at("/lease/processIdentity").asText());
        assertThat(unknown.path("event").asText()).isEqualTo("RECOVERY_LEASE_FENCED");
        var before = SubmissionJson.parse(Files.readString(supply(attempt).resolve("before-commit-p5.json")));
        var after = snapshot(attempt, "after-failure");
        assertThat(after.size()).isEqualTo(before.size() + 1);
        var existing = after.deepCopy(); existing.remove("leases/" + operation + ".unknown.json");
        assertThat(SubmissionJson.canonical(existing)).isEqualTo(SubmissionJson.canonical(before));
        Files.copy(record, home(attempt).resolve("unknown-original.json"));
    }

    public static void retirement(Path attempt) throws Exception {
        var before = SubmissionJson.parse(Files.readString(supply(attempt).resolve("after-failure-p5.json")));
        var held = SubmissionJson.parse(Files.readString(home(attempt).resolve("worker-commit-held.json")));
        String operation = held.at("/lease/operationId").asText();
        var after = snapshot(attempt, "after-observation");
        assertThat(after.has("leases/" + operation + ".json")).isFalse();
        var expected = (com.fasterxml.jackson.databind.node.ObjectNode) before.deepCopy();
        expected.remove("leases/" + operation + ".json"); expected.remove("leases/index.json");
        var actual = after.deepCopy(); actual.remove("leases/index.json");
        assertThat(SubmissionJson.canonical(actual)).isEqualTo(SubmissionJson.canonical(expected));
        assertThat(RecoveryControlHistory.read(supply(attempt).resolve("p5/leases/index.json")).path("entries")).isEmpty();
        assertThat(Files.readAllBytes(supply(attempt).resolve("p5/leases/" + operation + ".unknown.json")))
            .isEqualTo(Files.readAllBytes(home(attempt).resolve("unknown-original.json")));
    }

    public static void finish(Path attempt) throws Exception {
        var observed = SubmissionJson.parse(Files.readString(supply(attempt).resolve("after-observation-p5.json")));
        var finalState = snapshot(attempt, "final-unknown");
        assertThat(SubmissionJson.canonical(finalState)).isEqualTo(SubmissionJson.canonical(observed));
        assertThat(Files.readString(home(attempt).resolve("worker.stdout"))
            + Files.readString(home(attempt).resolve("worker.stderr"))).contains(
            "FENCE_F1_PRIME_CONFIRMED", "FENCE_F2_AUDIT_CONFIRMED", "FENCE_F3_RELEASE_CONFIRMED", "FENCE_F4_WRITERS_RETAINED");
        var result = SubmissionJson.object().put("syntheticTestOnly", true).put("unknownRetained", true)
            .put("freshProductApiRefused", true).put("noNewProcessRecord", true);
        Files.writeString(supply(attempt).resolve("unknown-finished.json"), result.toPrettyString(), StandardOpenOption.CREATE_NEW);
    }
}
