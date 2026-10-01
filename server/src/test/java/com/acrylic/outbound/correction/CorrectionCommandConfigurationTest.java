package com.acrylic.outbound.correction;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;

class CorrectionCommandConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(CorrectionCommandConfiguration.class)
        .withBean(CorrectionStore.class, () -> new CorrectionStore(null))
        .withBean(CorrectionAccounting.class, () -> new CorrectionAccounting(null));
    @Test void defaultConfigurationRegistersNoOperation() {
        runner.run(context -> assertThat(context.getBeanNamesForType(CorrectionCommand.Operation.class)).isEmpty());
    }
    @Test void enablingTheSwitchRegistersOnlyTheImplementedOperations() {
        runner.withPropertyValues("outbound.corrections.commands.enabled=true").run(context ->
            assertThat(context.getBeanNamesForType(CorrectionCommand.Operation.class)).hasSize(6));
    }
}
