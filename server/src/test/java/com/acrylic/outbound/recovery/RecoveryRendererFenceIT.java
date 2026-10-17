package com.acrylic.outbound.recovery;

import com.acrylic.outbound.exports.RendererDrainReceiptFixture;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

/** Real renderer/native locks and production F1-F5; the missing termination receipt is a MODEL fault. */
@EnabledIfEnvironmentVariable(named = "E2A_CROSS_VERIFY", matches = "true")
class RecoveryRendererFenceIT {
    @Test void unconfirmedRendererTerminationInvokesRealHaltAndLeavesAnObservableOrphan() throws Exception {
        Path evidence = Path.of(System.getenv("E2A_CROSS_ROOT"), ".local/e2a-cross-20260925-" + System.getenv("E2A_CROSS_RUN"));
        Path home = Files.createDirectory(evidence.resolve("renderer-fence"));
        RendererDrainReceiptFixture.prepare(home);
        Path lease = home.resolve("p5"), closed = home.resolve("closed");
        var provisioned = new RecoveryInitialAuthorityProvision.Provisioned[1];
        var control = RecoveryWriterLeaseTest.protocol(f -> {
            provisioned[0] = RecoveryInitialAuthorityProvision.provision(f.root, lease, closed);
            return RecoveryControlHistory.read(f.root.resolve("identity.json"));
        });
        Path config = config(evidence.resolve("private"), control, lease, closed, provisioned[0]);
        var process = launch(home, config);
        try {
            long renderer = started(home, process);
            assertWriterHeld(control.root);
            Files.writeString(home.resolve("attempt/renderer-release"), "release", StandardOpenOption.CREATE_NEW);
            assertThat(process.waitFor(30, TimeUnit.SECONDS)).isTrue();
            assertThat(process.exitValue()).isEqualTo(70);
            assertThat(ProcessHandle.of(renderer).map(ProcessHandle::isAlive).orElse(false)).isFalse();
            verify(home, control, lease, closed, provisioned[0]);
        } finally {
            if (process.isAlive()) { process.destroyForcibly(); assertThat(process.waitFor(15, TimeUnit.SECONDS)).isTrue(); }
        }
    }

    private static long started(Path home, Process child) throws Exception {
        Path marker = home.resolve("attempt/renderer-started");
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(child.isAlive()).isTrue(); assertThat(marker).exists();
            assertThat(Files.readString(marker)).matches("[0-9]+");
        });
        return Long.parseLong(Files.readString(marker));
    }

    private static void assertWriterHeld(Path control) throws Exception {
        try (var file = FileChannel.open(control.resolve("writers.lock"), StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            try (var lock = file.tryLock(0, 1, false)) { assertThat(lock).as("independent parent sees live writer").isNull(); }
        }
    }

    private static void verify(Path home, RecoveryControlFixture control, Path lease, Path closed,
            RecoveryInitialAuthorityProvision.Provisioned pin) throws Exception {
        assertThat(home.resolve("incorrect-return.txt")).doesNotExist();
        assertThat(Files.readString(home.resolve("termination-observed.txt")))
            .contains("alive=false|childrenAlive=0", "MODEL=lost receipt after real native drain");
        String stderr = Files.readString(home.resolve("child.stderr"));
        assertThat(stderr).contains("FENCE_F1_PRIME_CONFIRMED", "FENCE_F2_AUDIT_CONFIRMED",
            "FENCE_F3_RELEASE_CONFIRMED", "FENCE_F4_WRITERS_RETAINED");
        List<Path> records;
        try (var files = Files.list(lease.resolve("leases"))) { records = files.filter(p -> p.toString().endsWith(".unknown.json")).toList(); }
        assertThat(records).hasSize(1);
        Path record = records.getFirst().resolveSibling(records.getFirst().getFileName().toString().replace(".unknown", ""));
        Files.copy(record, home.resolve("orphan-original.json"));
        Files.copy(records.getFirst(), home.resolve("unknown-original.json"));
        var binding = new RecoveryLeaseRoots.Binding(control.location(), lease, closed, pin.identitySha256());
        var observer = new RecoveryFenceAuthority(new RecoveryLeaseRoots(binding, pin.markerSha256(), pin.anchor()));
        assertThatThrownBy(observer::validateStartup).hasMessageContaining("RECOVERY_LEASE_OUTSTANDING");
        Files.writeString(home.resolve("result.json"), "{\"exit\":70,\"writerHeld\":true,\"rendererStopped\":true,"
            + "\"realFenceSequence\":true,\"unknownRecords\":1,\"nextProcessRefused\":true,\"modelLostReceipt\":true}", StandardOpenOption.CREATE_NEW);
    }

    private static Path config(Path privateDirectory, RecoveryControlFixture control, Path lease, Path closed,
            RecoveryInitialAuthorityProvision.Provisioned pin) throws Exception {
        var value = RecoveryControlFixture.JSON.createObjectNode().put("root", control.root.toString())
            .put("installation", control.installation.toString()).put("restore", control.restoreRoot.toString())
            .put("checkpoint", control.checkpoint.toString()).put("lease", lease.toString()).put("closed", closed.toString())
            .put("identitySha256", pin.identitySha256()).put("markerSha256", pin.markerSha256())
            .put("anchor", RecoveryControlFormat.hex(pin.anchor()));
        Path path = privateDirectory.resolve("renderer-fence-config.json");
        Files.writeString(path, RecoveryControlFormat.canonical(value), StandardOpenOption.CREATE_NEW);
        return path;
    }

    private static Process launch(Path home, Path config) throws Exception {
        var paths = RecoveryControlFixture.JSON.readTree(Files.readString(Path.of("build/recovery-control-classpath.json")));
        var classpath = new ArrayList<String>(); paths.forEach(p -> classpath.add(p.asText()));
        var command = List.of(Path.of(System.getProperty("java.home"), "bin/java.exe").toString(), "-Xmx256m",
            "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8",
            "-Djava.io.tmpdir=" + home.resolve("tmp"), "-Dfile.encoding=UTF-8", "-cp", String.join(java.io.File.pathSeparator, classpath),
            RecoveryRendererFenceChild.class.getName(), config.toString(), home.toString());
        Files.createDirectories(home.resolve("tmp"));
        Files.writeString(home.resolve("child-command.json"), RecoveryControlFixture.JSON.writeValueAsString(command));
        return new ProcessBuilder(command).redirectOutput(home.resolve("child.stdout").toFile())
            .redirectError(home.resolve("child.stderr").toFile()).start();
    }
}
