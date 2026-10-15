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

/** Stop only this test's real JVM after its first atomic rewrite; retain the entire crash image. */
class RecoveryRekeyCrashIT {
    @TempDir(cleanup = CleanupMode.NEVER) Path parent;

    @Test void abruptProductProcessTerminationLeavesClosedDiagnosableMixedState() throws Exception {
        var f = RecoveryRekeyCliFixture.create(parent); addProcesses(f, 512);
        Path first;
        try (var files = Files.list(f.supply().roots().root().resolve("processes"))) { first = files.sorted().findFirst().orElseThrow(); }
        String original = Files.readString(first); Process child = start(f);
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            while (Files.readString(first).equals(original) && child.isAlive() && System.nanoTime() < deadline) Thread.sleep(1);
            assertThat(Files.readString(first)).isNotEqualTo(original); assertThat(child.isAlive()).isTrue();
            child.destroyForcibly(); assertThat(child.waitFor(5, TimeUnit.SECONDS)).isTrue();
        } finally { if (child.isAlive()) { child.destroyForcibly(); child.waitFor(5, TimeUnit.SECONDS); } }
        Path backup;
        try (var files = Files.list(f.supply().base())) {
            backup = files.filter(p -> p.getFileName().toString().startsWith(".rekey-backup-")).findFirst().orElseThrow();
        }
        var manifest = RecoveryControlHistory.read(backup.resolve(RecoveryRekeyInventory.FILE));
        anchorTag(f.supply().roots().anchor(), manifest, "inventoryTag");
        assertThat(manifest.path("recordCount").asInt()).isEqualTo(515);
        var inspector = new RecoveryRekeyInspectFixture(f, backup, RecoveryRekeyInspectFixture.keys(f));
        assertThat(inspector.inspect("crash", 0, 3)).isIn("MIXED", "PENDING", "UNKNOWN");
        assertThatThrownBy(() -> f.supply().authority().validateStartup()).isInstanceOf(Exception.class);
        Files.writeString(f.supply().base().resolve("crash-process.txt"), "pid=" + child.pid() + "; alive=" + child.isAlive());
    }

    private void addProcesses(RecoveryRekeyCliFixture f, int count) throws Exception {
        var original = (ObjectNode) f.onlyProcess(); Path directory = f.supply().roots().root().resolve("processes");
        for (int i = 0; i < count; i++) {
            var value = original.deepCopy(); UUID id = UUID.randomUUID();
            value.put("processIdentity", id.toString()); value.remove("anchorTag");
            value.put("anchorTag", hmac(f.supply().roots().anchor(), value));
            Files.writeString(directory.resolve(id + ".json"), canonical(value), StandardOpenOption.CREATE_NEW);
        }
    }

    private Process start(RecoveryRekeyCliFixture f) throws Exception {
        var paths = new ObjectMapper().readTree(Files.readString(Path.of("build/recovery-rekey-classpath.json")));
        var classpath = new ArrayList<String>(); paths.forEach(p -> classpath.add(p.asText()));
        Path java = Path.of(System.getProperty("java.home"), "bin", RecoveryControlPaths.WINDOWS ? "java.exe" : "java");
        var cmd = List.of(java.toString(), "-Dfile.encoding=UTF-8", "-cp", String.join(File.pathSeparator, classpath),
            RecoveryRekeyCli.class.getName(), "--request", f.request().toString());
        Path logs = Files.createDirectory(f.supply().base().resolve("crash-child"));
        Files.writeString(logs.resolve("command.txt"), String.join(" ", cmd));
        Process child = new ProcessBuilder(cmd).redirectOutput(logs.resolve("stdout.txt").toFile())
            .redirectError(logs.resolve("stderr.txt").toFile()).start();
        try (var pipe = child.getOutputStream()) { pipe.write(f.pipe()); } return child;
    }
}
