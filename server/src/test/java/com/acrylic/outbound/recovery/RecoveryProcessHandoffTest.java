package com.acrylic.outbound.recovery;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import static org.assertj.core.api.Assertions.*;

class RecoveryProcessHandoffTest {
    @Test void defaultOffNeedsNoOperationRoot() {
        try (var handoff = service(null, null).begin()) { handoff.unconfirmed(); }
    }

    @Test void enabledAdmissionRefusesRootlessHandoff() throws Exception {
        var registry = registry();
        assertThatThrownBy(() -> service(registry, null).begin()).hasMessage("RECOVERY_OPERATION_REQUIRED");
        assertThat(registry.liveRoots()).isZero();
    }

    @Test void rootlessRendererCannotCreateAnAttemptDirectory() throws Exception {
        var registry = registry();
        var home = java.nio.file.Files.createTempDirectory(
            java.nio.file.Path.of("../.local/export-process-drain"), "rootless-").toAbsolutePath();
        com.acrylic.outbound.exports.RendererDrainReceiptFixture.prepare(home);
        var renderer = com.acrylic.outbound.exports.RendererDrainReceiptFixture.lostReceipt(home, service(registry, null));
        var output = home.resolve("attempt/report.xlsx");
        assertThatThrownBy(() -> renderer.render(org.mockito.Mockito.mock(com.acrylic.outbound.jobs.BackgroundJobExecution.class),
            home.resolve("snapshot.json"), output, com.acrylic.outbound.exports.ExportModels.Format.XLSX))
            .hasMessage("RECOVERY_OPERATION_REQUIRED");
        assertThat(output.getParent()).doesNotExist();
        assertThat(home.resolve("termination-observed.txt")).doesNotExist();
    }

    @Test void httpRootCannotHandOffARenderer() throws Exception {
        var registry = registry();
        try (var root = root(registry, RecoveryOperationKind.HTTP_REQUEST)) {
            try {
                root.bindDispatch(Thread.currentThread());
                assertThatThrownBy(() -> service(registry, null).begin()).hasMessage("CONTROL_SUBOPERATION_FORBIDDEN");
                assertThat(root.openSubOperations()).isZero();
            } finally { root.unbindAll(); }
        }
    }

    @Test void confirmedHandoffReleasesItsWorkBeforeRootClosure() throws Exception {
        var registry = registry();
        try (var root = root(registry, RecoveryOperationKind.WORKER_JOB)) {
            try {
                root.bindDispatch(Thread.currentThread());
                try (var handoff = service(registry, null).begin()) {
                    assertThat(root.openSubOperations()).isEqualTo(1);
                }
                assertThat(root.openSubOperations()).isZero();
            } finally { root.unbindAll(); }
        }
        assertThat(registry.liveRoots()).isZero();
    }

    @Test void returnedUnknownSequenceCannotReleaseTheQuarantinedWriter() throws Exception {
        var registry = registry();
        var authority = new ReturningAuthority(registry);
        var gate = RecoveryEffectGate.enabled(authority);
        var root = root(registry, RecoveryOperationKind.WORKER_JOB);
        root.bindDispatch(Thread.currentThread());
        try (var handoff = service(registry, gate).begin()) {
            handoff.unconfirmed(); handoff.unconfirmed();
        } finally { root.unbindAll(); root.close(); }
        assertThat(authority.unknown).isEqualTo(1);
        assertThat(root.phase()).isEqualTo(RecoveryOperationPhase.FENCED);
        assertThat(root.openSubOperations()).isEqualTo(1);
        assertThat(registry.liveRoots()).isEqualTo(1);
        assertThatThrownBy(gate::check).hasMessage("RECOVERY_LEASE_FENCED");
        assertThatThrownBy(() -> root(registry, RecoveryOperationKind.WORKER_JOB)).hasMessage("CONTROL_WRITERS_ACTIVE");
    }

    private static final class ReturningAuthority implements RecoveryEffectGate.Authority {
        private final RecoveryOperationRegistry registry;
        private int unknown;
        ReturningAuthority(RecoveryOperationRegistry registry) { this.registry = registry; }
        public void validateStartup() { }
        public void checkEffect() { }
        public void fenceUnknown() {
            unknown++;
            assertThatThrownBy(() -> root(registry, RecoveryOperationKind.WORKER_JOB))
                .as("writer must be poisoned before invoking the unknown sequence").hasMessage("CONTROL_WRITERS_ACTIVE");
        }
    }

    static RecoveryProcessHandoff service(RecoveryOperationRegistry registry, RecoveryEffectGate gate) {
        var beans = new StaticListableBeanFactory();
        if (registry != null) beans.addBean("operations", registry);
        if (gate != null) beans.addBean("effects", gate);
        return new RecoveryProcessHandoff(beans.getBeanProvider(RecoveryOperationRegistry.class),
            beans.getBeanProvider(RecoveryEffectGate.class));
    }

    private static RecoveryOperationRegistry registry() throws Exception {
        var fixture = RecoveryWriterLeaseTest.protocol();
        return new RecoveryOperationRegistry(fixture.location(), fixture.checkpoint);
    }

    static RecoveryOperationLease root(RecoveryOperationRegistry registry, RecoveryOperationKind kind) {
        return registry.admit(new RecoveryOperationToken(UUID.randomUUID(), kind,
            registry.startup().installationId(), registry.startup().recordSha256(), null));
    }
}
