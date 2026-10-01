package com.acrylic.outbound.correction;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;

class CorrectionAttachmentConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(CorrectionCommandConfiguration.class, CorrectionAttachmentConsumer.class)
        .withBean(CorrectionStore.class, () -> new CorrectionStore(null))
        .withBean(CorrectionAttachmentStore.class, () -> new CorrectionAttachmentStore(null));

    @Test void defaultHasNoConsumerOrOperation() {
        runner.run(context -> {
            assertThat(context.getBeansOfType(CorrectionAttachmentConsumer.class)).isEmpty();
            assertThat(context.getBeansOfType(CorrectionCommand.Operation.class)).isEmpty();
        });
    }

    @Test void attachmentSwitchAloneCannotOpenCommands() {
        runner.withPropertyValues("outbound.corrections.attachments.enabled=true").run(context -> {
            assertThat(context.getBeansOfType(CorrectionAttachmentConsumer.class)).isEmpty();
            assertThat(context.getBeansOfType(CorrectionCommand.Operation.class)).isEmpty();
        });
    }

    @Test void commandsWithoutAttachmentsKeepExistingCreate() {
        runner.withPropertyValues("outbound.corrections.commands.enabled=true").run(context ->
            assertThat(context.getBean("correctionCreate")).isExactlyInstanceOf(CorrectionOperations.Create.class));
    }

    @Test void bothSwitchesEnforceControlledConsumptionForCreate() {
        runner.withPropertyValues("outbound.corrections.commands.enabled=true", "outbound.corrections.attachments.enabled=true")
            .run(context -> {
                assertThat(context.getBean("correctionCreate")).isExactlyInstanceOf(CorrectionAttachmentCreate.class);
                assertThat(context.getBeansOfType(CorrectionCommand.Operation.class)).hasSize(6);
            });
    }
}
