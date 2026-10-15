package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.File;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;
import static org.assertj.core.api.Assertions.*;

/** Preserve a new actual restore interruption; never reuse or mutate earlier failure evidence. */
class RecoveryRekeyRestoreCrashIT {
    @TempDir(cleanup = CleanupMode.NEVER) Path parent;

    @Test void actualRestoreTerminationRetainsPreRestoreImageAndRefusesEveryRuntime() throws Exception {
        var rotation = RecoveryRekeyCliFixture.create(parent); addProcesses(rotation);
        var result = rotation.child(); assertThat(result.status()).isZero();
        var receipt = readCanonical(result.output().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Path backup = Path.of(text(receipt, "evidenceDirectory"));
        var inspector = new RecoveryRekeyInspectFixture(rotation, backup, RecoveryRekeyInspectFixture.keys(rotation));
        var f = new RecoveryRekeyRestoreFixture(inspector, text(receipt, "inventorySha256"));
        Path first;
        try (var files = Files.list(rotation.supply().roots().root().resolve("processes"))) { first = files.sorted().findFirst().orElseThrow(); }
        String initial = Files.readString(first); Process child = start(f);
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            while (Files.readString(first).equals(initial) && child.isAlive() && System.nanoTime() < deadline) Thread.sleep(1);
            assertThat(Files.readString(first)).isNotEqualTo(initial); assertThat(child.isAlive()).isTrue();
            child.destroyForcibly(); assertThat(child.waitFor(5, TimeUnit.SECONDS)).isTrue();
        } finally { if (child.isAlive()) { child.destroyForcibly(); child.waitFor(5, TimeUnit.SECONDS); } }
        assertThat(inspector.inspect("restore-crash", 0, 3)).isIn("MIXED", "PENDING", "UNKNOWN");
        assertThatThrownBy(() -> rotation.supply().authority().validateStartup()).isInstanceOf(Exception.class);
        var binding = RecoveryRekeyRequest.read(rotation.request()).binding();
        assertThatThrownBy(() -> RecoveryLeaseLifecycle.open(binding, receipt.path("markerSha256").asText(), hex(rotation.next())))
            .isInstanceOf(Exception.class);
        Files.writeString(rotation.supply().base().resolve("restore-crash-process.txt"), "pid=" + child.pid() + "; alive=" + child.isAlive());
    }

    private void addProcesses(RecoveryRekeyCliFixture f) throws Exception {
        var original = (ObjectNode) f.onlyProcess(); Path directory = f.supply().roots().root().resolve("processes");
        for (int i = 0; i < 512; i++) {
            var value = original.deepCopy(); UUID id = UUID.randomUUID(); value.put("processIdentity", id.toString()); value.remove("anchorTag");
            value.put("anchorTag", hmac(f.supply().roots().anchor(), value));
            Files.writeString(directory.resolve(id + ".json"), canonical(value), StandardOpenOption.CREATE_NEW);
        }
    }

    private Process start(RecoveryRekeyRestoreFixture f) throws Exception {
        var rotation = f.inspection().rotation();
        var paths = new ObjectMapper().readTree(Files.readString(Path.of("build/recovery-rekey-classpath.json")));
        var classpath = new ArrayList<String>(); paths.forEach(p -> classpath.add(p.asText()));
        Path java = Path.of(System.getProperty("java.home"), "bin", RecoveryControlPaths.WINDOWS ? "java.exe" : "java");
        var cmd = List.of(java.toString(), "-Dfile.encoding=UTF-8", "-cp", String.join(File.pathSeparator, classpath),
            RecoveryRekeyRestoreCli.class.getName(), "--request", rotation.request().toString(), "--backup", f.inspection().backup().toString(),
            "--inventory-sha256", f.inventoryHash());
        Path logs = Files.createDirectory(rotation.supply().base().resolve("restore-crash-child"));
        Files.writeString(logs.resolve("command.txt"), String.join(" ", cmd));
        Process child = new ProcessBuilder(cmd).redirectOutput(logs.resolve("stdout.json").toFile())
            .redirectError(logs.resolve("stderr.txt").toFile()).start();
        try (var pipe = child.getOutputStream()) { pipe.write(rotation.pipe()); } return child;
    }
}
