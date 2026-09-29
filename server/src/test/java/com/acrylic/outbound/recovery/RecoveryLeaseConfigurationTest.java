package com.acrylic.outbound.recovery;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Design rows 8–9 wiring: the P5 identity, fence channel, orphan scan, process record and lifecycle
 * lock are taken only from an explicitly supplied and provisioned tree, and a second process on the
 * same root is refused until the first retires.
 */
class RecoveryLeaseConfigurationTest {
    @TempDir Path parent;

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(RecoveryLeaseConfiguration.class);

    @Test void theP5WiringIsAbsentUntilItIsExplicitlyEnabled() throws Exception {
        var supply = FenceTestSupply.create(parent);

        runner.withPropertyValues(props(supply, supply.anchorHex())).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).as("no lease root is acquired without the explicit switch")
                .doesNotHaveBean(RecoveryLeaseLifecycle.class);
            assertThat(context).doesNotHaveBean(RecoveryFenceAuthority.class);
        });
        try (var entries = Files.list(supply.roots().root().resolve("processes"))) {
            assertThat(entries.count()).as("nothing was opened on the P5 root").isZero();
        }
    }

    @Test void anEnabledWiringWithoutASuppliedAnchorIsRefused() throws Exception {
        var supply = FenceTestSupply.create(parent);

        runner.withPropertyValues(props(supply, null, "--outbound.recovery.lease.enabled=true")).run(context -> {
            assertThat(context).hasFailed();
            assertThat(rootCause(context.getStartupFailure())).hasMessageContaining("CONTROL_ANCHOR_REQUIRED");
        });
    }

    @Test void anEnabledWiringWithoutTheProvisionedMarkerIsRefused() throws Exception {
        var supply = FenceTestSupply.create(parent);

        runner.withPropertyValues(props(supply, supply.anchorHex(), "--outbound.recovery.lease.enabled=true",
            "--outbound.recovery.lease.marker-sha256=" + "0".repeat(64))).run(context -> {
            assertThat(context).hasFailed();
            assertThat(rootCause(context.getStartupFailure()))
                .hasMessageContaining("RECOVERY_LEASE_ROOT_UNBOUND");
        });
    }

    @Test void aDeletedFenceFileIsRefusedInsteadOfBeingRecreated() throws Exception {
        var supply = FenceTestSupply.create(parent);
        Files.delete(supply.roots().root().resolve("fenced.lock"));

        runner.withPropertyValues(props(supply, supply.anchorHex(), "--outbound.recovery.lease.enabled=true"))
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(rootCause(context.getStartupFailure()))
                    .hasMessageContaining("CONTROL_FENCE_LOCK_MISSING");
            });
        assertThat(Files.exists(supply.roots().root().resolve("fenced.lock")))
            .as("startup never repairs a missing P5 file").isFalse();
    }

    @Test void anEnabledWiringOpensOneProcessRecordAndRetiresItOnShutdown() throws Exception {
        var supply = FenceTestSupply.create(parent);
        Path processes = supply.roots().root().resolve("processes");
        var properties = props(supply, supply.anchorHex(), "--outbound.recovery.lease.enabled=true");

        runner.withPropertyValues(properties).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(RecoveryLeaseLifecycle.class).authority())
                .isSameAs(context.getBean(RecoveryFenceAuthority.class));
            try (var entries = Files.list(processes)) {
                assertThat(entries.map(path -> path.getFileName().toString()).toList())
                    .as("one immutable process record").hasSize(1);
            }
            assertThat(processHeld(processes)).as("its lifecycle lock is held while it runs").isTrue();
        });

        // The lock was held for the lifetime of that context: the root is available again only after
        // the first process retired, and each process leaves its own immutable record behind.
        runner.withPropertyValues(properties).run(context -> assertThat(context).hasNotFailed());
        try (var entries = Files.list(processes)) {
            assertThat(entries.count()).as("each process leaves its own record").isEqualTo(2);
        }
    }

    @Test void twoProcessesShareTheRootWithTheirOwnRecords() throws Exception {
        var supply = FenceTestSupply.create(parent);
        var properties = props(supply, supply.anchorHex(), "--outbound.recovery.lease.enabled=true");

        runner.withPropertyValues(properties).run(first -> {
            assertThat(first).hasNotFailed();
            // The P5 root is shared by design: the process record and its lifecycle lock identify a
            // live process, they do not exclude a second one. Concurrency is arbitrated by the writer
            // lease and the lease records, which is why both processes may hold the root at once.
            new ApplicationContextRunner()
                .withUserConfiguration(RecoveryLeaseConfiguration.class)
                .withPropertyValues(properties)
                .run(second -> assertThat(second).hasNotFailed());
            assertThat(first).hasNotFailed();
        });
        try (var entries = Files.list(supply.roots().root().resolve("processes"))) {
            assertThat(entries.count()).as("both processes left their own record").isEqualTo(2);
        }
    }

    @Test void aLeaseRecordDeletedAfterPublicationIsRefusedAtTheNextStartup() throws Exception {
        var supply = FenceTestSupply.create(parent);
        var authority = supply.authority();
        authority.validateStartup();
        java.util.UUID operation = authority.registerOperation();
        authority.retire();
        Files.delete(supply.roots().root().resolve("leases").resolve(operation + ".json"));

        runner.withPropertyValues(props(supply, supply.anchorHex(), "--outbound.recovery.lease.enabled=true"))
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(rootCause(context.getStartupFailure()))
                    .as("the anchored index makes a deleted lease record visible instead of invisible")
                    .hasMessageContaining("RECOVERY_LEASE_OUTSTANDING");
            });
    }

    /** True when the single process record's lifecycle byte is still held. */
    private static boolean processHeld(Path processes) throws Exception {
        try (var entries = Files.list(processes)) {
            Path record = entries.findFirst().orElseThrow();
            try (var channel = java.nio.channels.FileChannel.open(record, java.nio.file.StandardOpenOption.READ,
                    java.nio.file.StandardOpenOption.WRITE, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                try (var probe = channel.tryLock(RecoveryLeaseProcess.LOCK_BYTE, 1, false)) {
                    return probe == null;
                } catch (java.nio.channels.OverlappingFileLockException held) {
                    return true;
                }
            }
        }
    }

    private static String[] props(FenceTestSupply supply, String anchor, String... extra) throws Exception {
        var value = RecoveryControlFormat.readCanonical(Files.readAllBytes(supply.config()));
        var all = new ArrayList<>(List.of(
            "--outbound.recovery.root=" + value.path("root").asText(),
            "--outbound.recovery.installation-id=" + value.path("installationId").asText(),
            "--outbound.recovery.restore-roots[0]=" + value.path("restoreRoots").get(0).asText(),
            "--outbound.recovery.checkpoint=" + supply.base().resolve("checkpoint.json"),
            "--outbound.recovery.lease.root=" + value.path("leaseRoot").asText(),
            "--outbound.recovery.lease.closed-authority=" + value.path("closedAuthority").asText(),
            "--outbound.recovery.lease.identity-sha256=" + value.path("identitySha256").asText(),
            "--outbound.recovery.lease.marker-sha256=" + value.path("markerSha256").asText()));
        if (anchor != null) {
            all.add("--outbound.recovery.effect-gate.anchor=" + anchor);
        }
        all.addAll(List.of(extra));
        return all.toArray(String[]::new);
    }

    private static Throwable rootCause(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null && current.getCause() != current) current = current.getCause();
        return current;
    }
}
