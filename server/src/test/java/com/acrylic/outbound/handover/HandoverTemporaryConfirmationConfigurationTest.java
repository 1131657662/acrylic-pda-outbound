package com.acrylic.outbound.handover;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;

class HandoverTemporaryConfirmationConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(HandoverTemporaryConfirmationService.class, HandoverTemporaryConfirmationController.class);

    @Test void defaultConfigurationDoesNotExposeConfirmationConsumer() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBeansOfType(HandoverTemporaryConfirmationService.class)).isEmpty();
            assertThat(context.getBeansOfType(HandoverTemporaryConfirmationController.class)).isEmpty();
        });
    }

    @Test void explicitFalseKeepsConfirmationClosed() {
        runner.withPropertyValues("outbound.handover.commands.enabled=false").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBeansOfType(HandoverTemporaryConfirmationService.class)).isEmpty();
            assertThat(context.getBeansOfType(HandoverTemporaryConfirmationController.class)).isEmpty();
        });
    }
}
