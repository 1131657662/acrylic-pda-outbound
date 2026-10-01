package com.acrylic.outbound.handover;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;

class HandoverCasePageConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(HandoverCasePageService.class, HandoverCasePageController.class)
        .withBean(HandoverCommand.Identity.class, () -> new HandoverCommand.Identity(null, null, null))
        .withBean(HandoverCasePageStore.class, () -> new HandoverCasePageStore(null));

    @Test void defaultClosesDiscoveryRoutes() {
        runner.run(context -> {
            assertThat(context.getBeansOfType(HandoverCasePageService.class)).isEmpty();
            assertThat(context.getBeansOfType(HandoverCasePageController.class)).isEmpty();
        });
    }
    @Test void explicitCommandSwitchRegistersDiscovery() {
        runner.withPropertyValues("outbound.handover.commands.enabled=true").run(context -> {
            assertThat(context.getBeansOfType(HandoverCasePageService.class)).hasSize(1);
            assertThat(context.getBeansOfType(HandoverCasePageController.class)).hasSize(1);
        });
    }
}
