package com.acrylic.outbound.e2averify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;

/** One direct-JDK process per compatibility stage; no discovered/shared process control. */
final class E2aUpgradeRollbackProgram {
    record Reader(UUID id, String username, String password) { }
    record Target(JdbcTemplate jdbc, String url, Path storage, Path config) { }
    private static final ObjectMapper JSON = new ObjectMapper();
    private final E2aCrossSourceProfile profile;
    private final E2aUpgradeCheckpoint checkpoint;
    private final Map<String, String> seed;
    private final Reader reader;
    private ObjectNode business;

    E2aUpgradeRollbackProgram(E2aCrossSourceProfile profile, E2aUpgradeCheckpoint checkpoint,
            Map<String, String> seed, Reader reader) {
        this.profile = profile; this.checkpoint = checkpoint; this.seed = seed; this.reader = reader;
    }

    void read(String label, String binary, Target target) throws Exception {
        Path directory = Files.createDirectory(profile.attempt.resolve("private/program-" + label));
        var before = E2aProgramReadFacts.capture(checkpoint, target.jdbc(), target.storage());
        save(directory.resolve("before.json"), before);
        Process process = start(binary, target, directory);
        try {
            ready(process); listener(process, directory);
            var data = new E2aProgramReadHttp(directory.resolve("http")).read(seed, reader);
            if (business == null) business = data;
            assertThat(data).as(label + " business response").isEqualTo(business);
        } finally { stop(process, directory); }
        var after = E2aProgramReadFacts.capture(checkpoint, target.jdbc(), target.storage());
        save(directory.resolve("after.json"), after);
        E2aProgramReadFacts.verify(before, after, reader);
        Files.writeString(directory.resolve("verified.marker"), "Exact facts except one synthetic reader login; no product write.\n",
            StandardOpenOption.CREATE_NEW);
    }

    private Process start(String binary, Target target, Path directory) throws Exception {
        requirePortFree();
        String relative = binary.equals("old") ? ".local/goal-20261013/e06-conflict-audit-1535/server.jar"
            : "server/build/libs/acrylic-outbound-server-0.1.0.jar";
        String expected = binary.equals("old") ? "adbd314f0fd619997dea0f16e0de36a963c3650173e991cbcf259ad1ae7184e1"
            : "e130aa2b7844daf6380045e3f3e809672b9c15f5ff31c4a24adf4cdf6bdc92ba";
        Path jar = E2aCrossSourceProfile.ROOT.resolve(relative).toRealPath();
        String sha = HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(jar)));
        assertThat(sha).isEqualTo(expected);
        var args = arguments(jar, target);
        var builder = new ProcessBuilder(args).directory(profile.attempt.toFile())
            .redirectOutput(directory.resolve("api.stdout").toFile()).redirectError(directory.resolve("api.stderr").toFile());
        builder.environment().put("OUTBOUND_DB_PASSWORD", System.getenv("E2A_CROSS_PASSWORD"));
        var process = builder.start();
        var facts = JSON.createObjectNode().put("pid", process.pid()).put("jarSha256", sha).put("startedAt", Instant.now().toString());
        facts.set("args", JSON.valueToTree(args)); save(directory.resolve("process.json"), facts);
        return process;
    }

    private List<String> arguments(Path jar, Target target) {
        return List.of("C:/Program Files/Java/jdk-21.0.11/bin/java.exe", "-Xms64m", "-Xmx384m",
            "-Djava.io.tmpdir=" + profile.attempt.resolve("tmp"), "-jar", jar.toString(),
            "--server.address=127.0.0.1", "--server.port=" + E2aTestPorts.http(), "--spring.flyway.enabled=false",
            "--spring.datasource.url=" + target.url(), "--spring.datasource.username=" + profile.user,
            "--outbound.storage=" + target.storage(), "--outbound.worker.enabled=false", "--outbound.bootstrap.enabled=false",
            "--spring.session.jdbc.cleanup-cron=-", "--server.servlet.session.cookie.secure=false",
            "--outbound.signing.private-key=" + target.config().resolve("server-private.der"),
            "--outbound.signing.public-key=" + target.config().resolve("server-public.der"));
    }

    private void ready(Process process) throws Exception {
        for (int count = 0; count < 150; count++) {
            assertThat(process.isAlive()).as("API process before readiness").isTrue();
            if (!portFree()) return;
            Thread.sleep(400);
        }
        throw new AssertionError("API readiness timeout");
    }

    private void listener(Process process, Path directory) throws Exception {
        var command = new ProcessBuilder("powershell", "-NoProfile", "-Command",
            "Get-NetTCPConnection -State Listen -LocalPort " + E2aTestPorts.http()
            + " | Select-Object LocalPort,OwningProcess | ConvertTo-Json")
            .redirectOutput(directory.resolve("listener.json").toFile()).redirectError(directory.resolve("listener.stderr").toFile()).start();
        assertThat(command.waitFor(20, TimeUnit.SECONDS)).isTrue(); assertThat(command.exitValue()).isZero();
        var listener = JSON.readTree(directory.resolve("listener.json").toFile());
        assertThat(listener.path("OwningProcess").asLong()).isEqualTo(process.pid());
        assertThat(listener.path("LocalPort").asInt()).isEqualTo(E2aTestPorts.http());
    }

    private void stop(Process process, Path directory) throws Exception {
        if (process.isAlive()) process.destroy();
        assertThat(process.waitFor(30, TimeUnit.SECONDS)).isTrue();
        for (int count = 0; count < 80 && !portFree(); count++) Thread.sleep(250);
        requirePortFree();
        save(directory.resolve("stopped.json"), JSON.createObjectNode().put("pid", process.pid())
            .put("exitCode", process.exitValue()).put("listener", false).put("method", "Windows TerminateProcess; not graceful"));
    }

    private boolean portFree() throws Exception {
        try (var socket = new Socket()) { socket.connect(new InetSocketAddress("127.0.0.1", E2aTestPorts.http()), 200); return false; }
        catch (java.net.ConnectException error) { return true; }
    }

    private void requirePortFree() throws Exception { assertThat(portFree()).as("Dedicated HTTP port free").isTrue(); }
    private void save(Path path, ObjectNode facts) throws Exception {
        Files.writeString(path, facts.toPrettyString(), StandardOpenOption.CREATE_NEW);
    }
}
