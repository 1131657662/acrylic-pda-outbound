package com.acrylic.outbound.recovery;

import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

/** Synthetic P5 supply for actual product HTTP/session and scheduler retirement. */
public final class RecoveryRetirementApplicationFixture {
    private final Path home;
    private final RecoveryControlFixture control;
    private final RecoveryInitialAuthorityProvision.Provisioned pin;

    public RecoveryRetirementApplicationFixture(Path attempt) throws Exception {
        home = Files.createDirectory(attempt.resolve("private/p5-retirement"));
        var supplied = new RecoveryInitialAuthorityProvision.Provisioned[1];
        control = RecoveryWriterLeaseTest.protocol(f -> {
            supplied[0] = RecoveryInitialAuthorityProvision.provision(f.root, home.resolve("p5"), home.resolve("closed"));
            return RecoveryControlHistory.read(f.root.resolve("identity.json"));
        });
        pin = supplied[0];
        RecoveryRetirementProcess.config(home, control, pin);
    }

    public String[] options() {
        return new String[]{"--outbound.recovery.admission.enabled=true",
            "--outbound.recovery.root=" + control.location().root(),
            "--outbound.recovery.installation-id=" + control.location().installation(),
            "--outbound.recovery.restore-roots[0]=" + control.location().restoreRoots().getFirst(),
            "--outbound.recovery.checkpoint=" + control.checkpoint,
            "--outbound.recovery.lease.enabled=true", "--outbound.recovery.lease.root=" + home.resolve("p5"),
            "--outbound.recovery.lease.closed-authority=" + home.resolve("closed"),
            "--outbound.recovery.lease.identity-sha256=" + pin.identitySha256(),
            "--outbound.recovery.lease.marker-sha256=" + pin.markerSha256(),
            "--outbound.recovery.effect-gate.enabled=true", "--outbound.recovery.effect-gate.cross-process-enabled=true",
            "--outbound.recovery.effect-gate.anchor=" + RecoveryControlFormat.hex(pin.anchor())};
    }

    public void drain(ConfigurableApplicationContext web, ConfigurableApplicationContext worker) throws Exception {
        worker.getBean(ThreadPoolTaskScheduler.class).shutdown();
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(web.getBean(RecoveryOperationRegistry.class).liveRoots()).isZero();
            assertThat(worker.getBean(RecoveryOperationRegistry.class).liveRoots()).isZero();
        });
        web.getBean(RecoveryFenceAuthority.class).checkEffect();
        worker.getBean(RecoveryFenceAuthority.class).checkEffect();
        var index = RecoveryControlHistory.read(home.resolve("p5/leases/index.json"));
        assertThat(index.path("entries")).isEmpty();
        try (var files = Files.list(home.resolve("p5/leases"))) {
            assertThat(files.map(p -> p.getFileName().toString()).toList()).containsExactly("index.json");
        }
        try (var files = Files.list(home.resolve("p5/processes"))) { assertThat(files.count()).isEqualTo(2); }
        Files.writeString(home.resolve("drained-index.json"), index.toPrettyString(), StandardOpenOption.CREATE_NEW);
    }

    public void nextProcessStarts() throws Exception {
        Path out = home.resolve("successor.stdout");
        assertThat(RecoveryRetirementProcess.finish(RecoveryRetirementProcess.launch(home.resolve("config.json"), "startup", out))).isZero();
        assertThat(Files.readString(out)).contains("STARTED");
        Files.writeString(home.resolve("confirmed.txt"), "HTTP/Session and scheduler roots retired; independent successor started",
            StandardOpenOption.CREATE_NEW);
    }
}
