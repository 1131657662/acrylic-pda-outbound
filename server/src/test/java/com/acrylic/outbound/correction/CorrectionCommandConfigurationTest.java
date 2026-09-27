package com.acrylic.outbound.correction;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The switch is the whole capability boundary: with the default configuration there is no correction operation bean
 * at all, so every kind answers {@code CORRECTION_COMMAND_NOT_OPEN} no matter what a caller sends. Enabling it
 * registers exactly the operations this batch implements (CREATE for now).
 */
class CorrectionCommandConfigurationTest {
    private final ApplicationContextRunner runner=new ApplicationContextRunner()
        .withUserConfiguration(CorrectionCommandConfiguration.class)
        // The CREATE bean only needs the store to exist; no SQL runs in this test.
        .withBean(CorrectionStore.class,() -> new CorrectionStore(null));

    @Test void defaultConfigurationRegistersNoOperation() {
        runner.run(context -> assertThat(context.getBeanNamesForType(CorrectionCommand.Operation.class)).isEmpty());
    }

    @Test void enablingTheSwitchRegistersOnlyTheImplementedCreate() {
        runner.withPropertyValues("outbound.corrections.commands.enabled=true").run(context ->
            assertThat(context.getBeanNamesForType(CorrectionCommand.Operation.class))
                .as("APPROVE/WITHDRAW stay unregistered until their steps land").hasSize(1));
    }
}
