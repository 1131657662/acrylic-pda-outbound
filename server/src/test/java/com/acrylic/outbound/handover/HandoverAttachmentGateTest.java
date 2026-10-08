package com.acrylic.outbound.handover;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

class HandoverAttachmentGateTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(HandoverAttachmentService.class)
        .withBean(HandoverAttachmentStore.class, () -> mock(HandoverAttachmentStore.class))
        .withBean(HandoverAttachmentAccess.class, () -> mock(HandoverAttachmentAccess.class))
        .withBean(com.acrylic.outbound.common.IdentityWriteGuard.class, () -> mock(com.acrylic.outbound.common.IdentityWriteGuard.class))
        .withBean(HandoverCommand.Locks.class, () -> mock(HandoverCommand.Locks.class))
        .withBean(com.acrylic.outbound.audit.AuditWriter.class, () -> mock(com.acrylic.outbound.audit.AuditWriter.class));

    @Test void defaultHasNoAttachmentWriter() {
        runner.run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(HandoverAttachmentService.class));
    }

    @ParameterizedTest @ValueSource(strings={"commands", "fault", "attachments"})
    void anyClosedGatePreventsAttachmentWriter(String gate) {
        runner.withPropertyValues("outbound.handover.commands.enabled=true", "outbound.handover.fault.enabled=true",
            "outbound.handover.attachments.enabled=true", "outbound.handover." + gate + ".enabled=false")
            .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(HandoverAttachmentService.class));
    }

    @Test void explicitIsolatedGatesInstallAttachmentWriter() {
        runner.withPropertyValues("outbound.handover.commands.enabled=true", "outbound.handover.fault.enabled=true", "outbound.handover.attachments.enabled=true")
            .run(context -> assertThat(context).hasNotFailed().hasSingleBean(HandoverAttachmentService.class));
    }
}
