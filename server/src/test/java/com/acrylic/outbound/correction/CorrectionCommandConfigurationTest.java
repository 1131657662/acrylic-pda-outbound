package com.acrylic.outbound.correction;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CorrectionCommandConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(CorrectionCommandConfiguration.class)
        .withBean(CorrectionStore.class, () -> new CorrectionStore(null))
        .withBean(CorrectionAccounting.class, () -> new CorrectionAccounting(null))
        .withBean(CorrectionHeaderEvidence.class, () -> mock(CorrectionHeaderEvidence.class))
        .withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class));
    @Test void defaultConfigurationRegistersNoOperation() {
        runner.run(context -> assertThat(context.getBeanNamesForType(CorrectionCommand.Operation.class)).isEmpty());
    }
    @Test void enablingTheSwitchRegistersOnlyTheImplementedOperations() {
        runner.withPropertyValues("outbound.corrections.commands.enabled=true").run(context ->
            assertThat(context.getBeanNamesForType(CorrectionCommand.Operation.class)).hasSize(6));
    }
}
