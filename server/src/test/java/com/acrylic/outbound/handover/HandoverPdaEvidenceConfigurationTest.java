package com.acrylic.outbound.handover;

import com.acrylic.outbound.pda.PdaAccess;
import com.acrylic.outbound.pda.AssignmentStore;
import com.acrylic.outbound.common.ApiResponses;
import com.acrylic.outbound.submissions.SubmissionFiles;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class HandoverPdaEvidenceConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(HandoverPdaEvidenceService.class, HandoverPdaPackageService.class,
            HandoverPdaAssignmentService.class,
            HandoverPdaEvidenceController.class)
        .withBean(PdaAccess.class, () -> mock(PdaAccess.class))
        .withBean(HandoverStore.class, () -> mock(HandoverStore.class))
        .withBean(HandoverPdaEvidenceStore.class, () -> mock(HandoverPdaEvidenceStore.class))
        .withBean(HandoverReadReceipt.class, () -> mock(HandoverReadReceipt.class))
        .withBean(HandoverApprovalEvidence.class, () -> mock(HandoverApprovalEvidence.class))
        .withBean(HandoverChecklist.class, () -> mock(HandoverChecklist.class))
        .withBean(SubmissionFiles.class, () -> mock(SubmissionFiles.class))
        .withBean(AssignmentStore.class, () -> mock(AssignmentStore.class))
        .withBean(ApiResponses.class, () -> mock(ApiResponses.class));

    @Test void defaultClosesReceivingReads() {
        runner.run(context -> {
            assertThat(context.getBeansOfType(HandoverPdaEvidenceService.class)).isEmpty();
            assertThat(context.getBeansOfType(HandoverPdaPackageService.class)).isEmpty();
            assertThat(context.getBeansOfType(HandoverPdaAssignmentService.class)).isEmpty();
            assertThat(context.getBeansOfType(HandoverPdaEvidenceController.class)).isEmpty();
        });
    }

    @Test void explicitCommandSwitchRegistersReceivingReads() {
        runner.withPropertyValues("outbound.handover.commands.enabled=true").run(context -> {
            assertThat(context.getBeansOfType(HandoverPdaEvidenceService.class)).hasSize(1);
            assertThat(context.getBeansOfType(HandoverPdaPackageService.class)).hasSize(1);
            assertThat(context.getBeansOfType(HandoverPdaAssignmentService.class)).hasSize(1);
            assertThat(context.getBeansOfType(HandoverPdaEvidenceController.class)).hasSize(1);
        });
    }
}
