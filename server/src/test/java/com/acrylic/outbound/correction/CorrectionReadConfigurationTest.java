package com.acrylic.outbound.correction;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CorrectionReadConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(CorrectionReadService.class, CorrectionReadController.class)
        .withBean(CorrectionHeaderReadService.class, () -> mock(CorrectionHeaderReadService.class))
        .withBean(CorrectionCommand.Identity.class, () -> new CorrectionCommand.Identity(null, null, null))
        .withBean(CorrectionStore.class, () -> new CorrectionStore(null))
        .withBean(CorrectionReadStore.class, () -> new CorrectionReadStore(null))
        .withBean(CorrectionReadReceipt.class, () -> new CorrectionReadReceipt(null));

    @Test void defaultClosesReadRoutes() {
        runner.run(context -> {
            assertThat(context.getBeansOfType(CorrectionReadService.class)).isEmpty();
            assertThat(context.getBeansOfType(CorrectionReadController.class)).isEmpty();
        });
    }

    @Test void explicitCommandSwitchRegistersReadRoutes() {
        runner.withPropertyValues("outbound.corrections.commands.enabled=true").run(context -> {
            assertThat(context.getBeansOfType(CorrectionReadService.class)).hasSize(1);
            assertThat(context.getBeansOfType(CorrectionReadController.class)).hasSize(1);
        });
    }
}
