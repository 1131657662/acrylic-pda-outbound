package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;

/** Independent JVM, file-only standard streams and status-file synchronization. */
final class FenceChild implements AutoCloseable {
    final Process process;
    final Path directory;
    final Instant start = Instant.now();
    private boolean recorded;

    FenceChild(FenceTestSupply supply, String mode, String... extra) throws Exception {
        directory = Files.createTempDirectory(supply.base(), "child-");
        var paths = new ObjectMapper().readTree(Files.readString(Path.of("build/recovery-control-classpath.json")));
        var classpath = new ArrayList<String>(); paths.forEach(p -> classpath.add(p.asText()));
        var command = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin/java.exe").toString(),
            "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8", "-Duser.language=en", "-Duser.country=US",
            "-cp", String.join(java.io.File.pathSeparator, classpath), RecoveryFenceChildMain.class.getName(),
            supply.config().toString(), directory.toString(), mode));
        command.addAll(List.of(extra));
        Files.writeString(directory.resolve("command.txt"), String.join(" ", command));
        process = new ProcessBuilder(command).redirectOutput(directory.resolve("stdout.txt").toFile())
            .redirectError(directory.resolve("stderr.txt").toFile()).start();
    }
    String status() throws Exception {
        Path file = directory.resolve("status.txt"); return Files.exists(file) ? Files.readString(file) : "";
    }
    void await(String token) throws Exception {
        long limit = System.nanoTime() + TimeUnit.SECONDS.toNanos(25);
        while (System.nanoTime() < limit) {
            if (status().contains(token)) return;
            if (!process.isAlive()) break;
            Thread.sleep(20);
        }
        throw new AssertionError("Missing " + token + " in " + directory + " status=" + status());
    }
    void send(String command) throws Exception {
        Files.writeString(directory.resolve(command), command, StandardOpenOption.CREATE_NEW);
    }
    int exit() throws Exception {
        assertThat(process.waitFor(25, TimeUnit.SECONDS)).as(directory.toString()).isTrue();
        record(); return process.exitValue();
    }
    private void record() throws Exception {
        if (recorded) return;
        String record = "REAL|child|pid=" + process.pid() + "|jvm=" + status().lines().findFirst().orElse("MISSING")
            + "|started=" + start + "|ended=" + Instant.now() + "|exit=" + process.exitValue();
        Files.writeString(directory.resolve("receipt.txt"), record); EffectRealEnvironment.record(record); recorded = true;
    }
    @Override public void close() throws Exception {
        if (process.isAlive()) { process.destroyForcibly(); process.waitFor(20, TimeUnit.SECONDS); }
        record();
    }
}
