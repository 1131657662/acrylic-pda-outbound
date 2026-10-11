package com.acrylic.outbound.handover;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;

class HandoverRecoveredConfirmationConfigurationTest {
    private final ApplicationContextRunner runner=new ApplicationContextRunner()
        .withUserConfiguration(HandoverRecoveredConfirmationController.class,HandoverRecoveredConfirmationEndpoint.class);
    @Test void defaultClosesEndpointAndController() {
        runner.run(context->{assertThat(context).hasNotFailed();
            assertThat(context.getBeansOfType(HandoverRecoveredConfirmationEndpoint.class)).isEmpty();
            assertThat(context.getBeansOfType(HandoverRecoveredConfirmationController.class)).isEmpty();});
    }
    @Test void commandSwitchAloneDoesNotCreateRecoveryTrust() {
        runner.withPropertyValues("outbound.handover.commands.enabled=true").run(context->{assertThat(context).hasNotFailed();
            assertThat(context.getBeansOfType(HandoverRecoveredConfirmationEndpoint.class)).isEmpty();
            assertThat(context.getBeansOfType(HandoverRecoveredConfirmationController.class)).hasSize(1);});
    }
    @Test void explicitRecoveryWithoutIndependentTrustFailsStartup() {
        runner.withPropertyValues("outbound.handover.commands.enabled=true","outbound.handover.recovered-confirmation.enabled=true")
            .run(context->assertThat(context).hasFailed());
    }
}
