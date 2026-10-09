package com.acrylic.outbound.handover;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;

class HandoverReadConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(HandoverReadService.class, HandoverReadController.class)
        .withBean(HandoverCommand.Identity.class, () -> new HandoverCommand.Identity(null, null, null))
        .withBean(HandoverStore.class, () -> new HandoverStore(null))
        .withBean(HandoverAttachmentConsumer.class, () -> new HandoverAttachmentConsumer(null, false))
        .withBean(HandoverFaultReplay.class, () -> new HandoverFaultReplay(null, null, null))
        .withBean(HandoverReadStore.class, () -> new HandoverReadStore(null))
        .withBean(HandoverFaultView.class, () -> new HandoverFaultView(null, null, false, false))
        .withBean(HandoverReadReceipt.class, () -> new HandoverReadReceipt(null));

    @Test void defaultClosesReadRoutes() {
        runner.run(context -> {
            assertThat(context.getBeansOfType(HandoverReadService.class)).isEmpty();
            assertThat(context.getBeansOfType(HandoverReadController.class)).isEmpty();
        });
    }
    @Test void explicitCommandSwitchRegistersReadRoutes() {
        runner.withPropertyValues("outbound.handover.commands.enabled=true").run(context -> {
            assertThat(context.getBeansOfType(HandoverReadService.class)).hasSize(1);
            assertThat(context.getBeansOfType(HandoverReadController.class)).hasSize(1);
        });
    }
}
