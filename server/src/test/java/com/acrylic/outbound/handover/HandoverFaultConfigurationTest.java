package com.acrylic.outbound.handover;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class HandoverFaultConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(HandoverFaultConfiguration.class)
        .withBean(HandoverStopStore.class, () -> mock(HandoverStopStore.class))
        .withBean(HandoverFaultArchive.class, () -> mock(HandoverFaultArchive.class))
        .withBean(HandoverTemporaryApprovalEvidence.class, () -> mock(HandoverTemporaryApprovalEvidence.class))
        .withBean(HandoverFaultStore.class, () -> mock(HandoverFaultStore.class))
        .withBean(HandoverAttachmentConsumer.class, () -> mock(HandoverAttachmentConsumer.class))
        .withBean(HandoverStore.class, () -> mock(HandoverStore.class))
        .withBean(com.acrylic.outbound.devices.DeviceStore.class, () -> mock(com.acrylic.outbound.devices.DeviceStore.class));

    @Test void defaultHasNoFaultWriter() {
        runner.run(context -> assertThat(context).hasNotFailed().doesNotHaveBean("handoverFaultFence"));
    }

    @Test void ordinaryHandoverGateCannotOpenFaultWrites() {
        runner.withPropertyValues("outbound.handover.commands.enabled=true")
            .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean("handoverFaultFence"));
    }

    @Test void faultGateCannotOpenWithoutHandoverGate() {
        runner.withPropertyValues("outbound.handover.fault.enabled=true")
            .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean("handoverFaultFence"));
    }

    @Test void explicitIsolatedGatesInstallTheFaultWriter() {
        runner.withPropertyValues("outbound.handover.fault.enabled=true", "outbound.handover.commands.enabled=true")
            .run(context -> assertThat(context).hasNotFailed().hasBean("handoverFaultFence"));
    }
}
