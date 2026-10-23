package com.acrylic.outbound.recovery;

import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

/** Per-case synthetic authority and owned process receipts, retained under the private evidence root. */
final class HttpCloseFenceProcess implements AutoCloseable {
    final Path home, config;
    final RecoveryControlFixture control;
    Process process;

    HttpCloseFenceProcess(Path parent) throws Exception {
        home = Files.createTempDirectory(parent, "http-close-");
        control = RecoveryControlFixture.retainedAt(Files.createDirectory(home.resolve("control")),
            Files.createDirectory(home.resolve("business")));
        var provisioned = RecoveryInitialAuthorityProvision.provision(control.root, home.resolve("p5"), home.resolve("closed"));
        provisionWriter(provisioned.identitySha256());
        config = RecoveryRetirementProcess.config(home, control, provisioned);
        preserve(control.root.getParent(), home.resolve("control-originals"));
    }

    private void provisionWriter(String identitySha256) throws Exception {
        Files.writeString(control.root.resolve("writers.lock"), "0", StandardOpenOption.CREATE_NEW);
        Files.createDirectory(control.root.resolve("intents"));
        RecoveryControlFixture.write(control.root.resolve("admission.json"), RecoveryControlFixture.JSON.createObjectNode()
            .put("formatVersion", 1).put("installationId", control.installation.toString())
            .put("targetRevision", "1").put("intentSha256", RecoveryControlFormat.ZERO_HASH));
        RecoveryControlFixture.write(control.root.resolve("writers.json"), RecoveryControlFixture.JSON.createObjectNode()
            .put("formatVersion", 2).put("installationId", control.installation.toString())
            .put("identitySha256", identitySha256)
            .put("fileIdentity", RecoveryWriterProtocol.fileIdentity(control.root.resolve("writers.lock"))));
    }

    Process launch(String fault, String table, String label) throws Exception {
        Path temp = Files.createDirectory(home.resolve(label + "-tmp"));
        var command = List.of(Path.of(System.getProperty("java.home"), "bin/java.exe").toString(), "-Xmx256m",
            "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8",
            "-Djava.io.tmpdir=" + temp, "-cp", System.getProperty("java.class.path"),
            RequestCloseFenceChild.class.getName(), config.toString(), home.toString(), fault, table);
        Files.writeString(home.resolve(label + "-command.json"), RecoveryControlFixture.JSON.writeValueAsString(command),
            StandardOpenOption.CREATE_NEW);
        Process child = new ProcessBuilder(command).redirectOutput(home.resolve(label + ".stdout").toFile())
            .redirectError(home.resolve(label + ".stderr").toFile()).start();
        Files.writeString(home.resolve(label + "-pid.txt"), Long.toString(child.pid()), StandardOpenOption.CREATE_NEW);
        if (label.equals("owner")) process = child;
        return child;
    }

    int port() throws Exception {
        awaitFile("ready.json");
        var ready = RecoveryControlFixture.JSON.readTree(Files.readAllBytes(home.resolve("ready.json")));
        assertThat(ready.path("pid").asLong()).isEqualTo(process.pid());
        return ready.path("port").asInt();
    }

    void awaitFile(String name) {
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(process.isAlive()).as(home.toString()).isTrue();
            assertThat(home.resolve(name)).exists();
        });
    }

    void normalRootsDrained() {
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(Files.readAllLines(home.resolve("destroyed.tsv"))).containsExactly("roots=0", "roots=0");
            var index = RecoveryControlHistory.read(home.resolve("p5/leases/index.json"));
            assertThat(index.path("entries")).isEmpty();
        });
    }

    void writerHeld() throws Exception {
        try (var channel = FileChannel.open(control.root.resolve("writers.lock"), StandardOpenOption.READ, StandardOpenOption.WRITE);
                var lock = channel.tryLock(0, 1, false)) {
            assertThat(lock).as("independent parent sees the active writer").isNull();
        }
    }

    int exit(Process child, String label) throws Exception {
        assertThat(child.waitFor(35, TimeUnit.SECONDS)).as(home + "/" + label).isTrue();
        Files.writeString(home.resolve(label + "-exit.txt"), Integer.toString(child.exitValue()), StandardOpenOption.CREATE_NEW);
        return child.exitValue();
    }

    void assertUnknown() throws Exception {
        var prepared = RecoveryControlFixture.JSON.readTree(Files.readAllBytes(home.resolve("prepared.json")));
        String id = prepared.path("lease").asText();
        var lease = RecoveryControlHistory.read(home.resolve("p5/leases/" + id + ".json"));
        var unknown = RecoveryControlHistory.read(home.resolve("p5/leases/" + id + ".unknown.json"));
        assertThat(unknown.path("operationId").asText()).isEqualTo(id);
        assertThat(unknown.path("event").asText()).isEqualTo("RECOVERY_LEASE_FENCED");
        String identity = lease.path("processIdentity").asText();
        assertThat(unknown.path("processIdentity").asText()).isEqualTo(identity);
        Path owner = home.resolve("p5/processes/" + identity + ".json");
        assertThat(RecoveryControlHistory.read(owner).path("pid").asLong()).isEqualTo(process.pid());
        try (var channel = FileChannel.open(owner, StandardOpenOption.READ, StandardOpenOption.WRITE);
                var lock = channel.tryLock(1L << 30, 1, false)) { assertThat(lock).isNotNull(); }
        assertThat(Files.readString(home.resolve("owner.stderr"))).contains("FENCE_F1_PRIME_CONFIRMED",
            "FENCE_F2_AUDIT_CONFIRMED", "FENCE_F3_RELEASE_CONFIRMED", "FENCE_F4_WRITERS_RETAINED");
        preserve(home.resolve("p5"), home.resolve("p5-before-successor"));
    }

    private static void preserve(Path source, Path destination) throws Exception {
        try (var files = Files.walk(source)) {
            for (Path path : files.filter(Files::isRegularFile).toList()) {
                Path copy = destination.resolve(source.relativize(path));
                Files.createDirectories(copy.getParent());
                Files.copy(path, copy);
            }
        }
    }

    @Override public void close() throws Exception {
        if (process != null && process.isAlive()) {
            process.destroyForcibly();
            assertThat(process.waitFor(15, TimeUnit.SECONDS)).isTrue();
            Files.writeString(home.resolve("owner-forced-cleanup.marker"), "test failed before observed halt",
                StandardOpenOption.CREATE_NEW);
        }
    }
}
