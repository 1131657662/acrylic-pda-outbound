package com.acrylic.outbound.handover;

import com.acrylic.outbound.pda.PdaAccess;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class HandoverPdaEvidenceConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(HandoverPdaEvidenceService.class, HandoverPdaEvidenceController.class)
        .withBean(PdaAccess.class, () -> mock(PdaAccess.class))
        .withBean(HandoverStore.class, () -> mock(HandoverStore.class))
        .withBean(HandoverPdaEvidenceStore.class, () -> mock(HandoverPdaEvidenceStore.class))
        .withBean(HandoverReadReceipt.class, () -> mock(HandoverReadReceipt.class))
        .withBean(HandoverApprovalEvidence.class, () -> mock(HandoverApprovalEvidence.class))
        .withBean(HandoverChecklist.class, () -> mock(HandoverChecklist.class));

    @Test void defaultClosesReceivingManifest() {
        runner.run(context -> {
            assertThat(context.getBeansOfType(HandoverPdaEvidenceService.class)).isEmpty();
            assertThat(context.getBeansOfType(HandoverPdaEvidenceController.class)).isEmpty();
        });
    }

    @Test void explicitCommandSwitchRegistersReceivingManifest() {
        runner.withPropertyValues("outbound.handover.commands.enabled=true").run(context -> {
            assertThat(context.getBeansOfType(HandoverPdaEvidenceService.class)).hasSize(1);
            assertThat(context.getBeansOfType(HandoverPdaEvidenceController.class)).hasSize(1);
        });
    }
}
