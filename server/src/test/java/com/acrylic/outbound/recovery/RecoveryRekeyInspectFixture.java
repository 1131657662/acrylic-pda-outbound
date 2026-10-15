package com.acrylic.outbound.recovery;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;
import static org.assertj.core.api.Assertions.*;

/** Real Python read-only entry against independently retained product rotation outputs. */
record RecoveryRekeyInspectFixture(RecoveryRekeyCliFixture rotation, Path backup, Path keys) {
    static RecoveryRekeyInspectFixture rotated(Path parent) throws Exception {
        var rotation = RecoveryRekeyCliFixture.create(parent);
        var result = rotation.child(); assertThat(result.status()).isZero();
        var receipt = RecoveryControlFormat.readCanonical(result.output().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Path keys = keys(rotation);
        return new RecoveryRekeyInspectFixture(rotation, Path.of(receipt.path("evidenceDirectory").asText()), keys);
    }

    static Path keys(RecoveryRekeyCliFixture rotation) throws Exception {
        Path keys = Files.createDirectory(rotation.supply().base().resolve("inspection-keys"));
        Files.write(keys.resolve("old.bin"), rotation.supply().roots().anchor());
        Files.write(keys.resolve("next.bin"), rotation.next()); return keys;
    }

    String inspect(String scenario, int... expectedExits) throws Exception {
        String python = Objects.requireNonNull(System.getenv("P07_PYTHON"));
        Path tool = Path.of(Objects.requireNonNull(System.getenv("P07_TOOLS_ROOT")), "recovery_rekey_inspect.py");
        var cmd = List.of(python, tool.toString(), "--request", rotation.request().toString(), "--backup", backup.toString(),
            "--current-key-file", keys.resolve("old.bin").toString(), "--next-key-file", keys.resolve("next.bin").toString());
        Path logs = Files.createDirectory(rotation.supply().base().resolve("inspect-" + scenario));
        Files.writeString(logs.resolve("command.txt"), String.join(" ", cmd)); var before = facts();
        var child = new ProcessBuilder(cmd).redirectOutput(logs.resolve("stdout.json").toFile())
            .redirectError(logs.resolve("stderr.txt").toFile()).start();
        try {
            assertThat(child.waitFor(30, TimeUnit.SECONDS)).isTrue();
            assertThat(Arrays.stream(expectedExits).anyMatch(exit -> exit == child.exitValue())).isTrue();
            assertThat(Files.readString(logs.resolve("stderr.txt"))).isEmpty(); assertThat(facts()).isEqualTo(before);
            String output = Files.readString(logs.resolve("stdout.json"));
            assertThat(output).doesNotContain(rotation.supply().anchorHex(), hex(rotation.next()));
            var report = readCanonical(output.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            assertThat(report.path("keepWritersStopped").asBoolean()).isTrue();
            assertThat(report.path("recoveryAllowed").asBoolean()).isFalse(); return report.path("state").asText("UNKNOWN");
        } finally { if (child.isAlive()) { child.destroyForcibly(); child.waitFor(5, TimeUnit.SECONDS); } }
    }

    Map<String, String> facts() throws Exception {
        var result = new TreeMap<String, String>();
        for (Path root : List.of(rotation.supply().roots().root(), rotation.supply().roots().authorityRoot(), backup, keys)) {
            try (var files = Files.walk(root)) {
                for (Path file : files.filter(Files::isRegularFile).toList()) result.put(file.toString(), sha256(Files.readAllBytes(file)));
            }
        }
        return result;
    }

    List<Path> processFiles() throws Exception {
        try (var files = Files.list(backup.resolve("processes"))) { return files.sorted().toList(); }
    }

    void original(String relative) throws Exception {
        Path root = rotation.supply().roots().root(); Path target = root.resolve(relative);
        Files.copy(target, rotation.supply().base().resolve("retained-new-" + target.getFileName()));
        Files.copy(backup.resolve(relative), target, StandardCopyOption.REPLACE_EXISTING);
    }
}
