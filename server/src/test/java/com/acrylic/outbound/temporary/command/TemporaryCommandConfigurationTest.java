package com.acrylic.outbound.temporary.command;

import com.acrylic.outbound.submissions.SubmissionAccounting;
import com.acrylic.outbound.submissions.SubmissionStock;
import com.acrylic.outbound.temporary.review.TemporaryDecisionCandidates;
import com.acrylic.outbound.temporary.review.TemporaryLinkTarget;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** The production default must stay identical: no operation, no route, every kind unsupported. */
class TemporaryCommandConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(TemporaryCommandConfiguration.class, TemporaryCommandController.class)
        .withBean(TemporaryConfirmationSource.class, () -> mock(TemporaryConfirmationSource.class))
        .withBean(TemporaryDecisionCandidates.class, () -> mock(TemporaryDecisionCandidates.class))
        .withBean(TemporaryShortApprovals.class, () -> mock(TemporaryShortApprovals.class))
        .withBean(SubmissionStock.class, () -> mock(SubmissionStock.class))
        .withBean(SubmissionAccounting.class, () -> mock(SubmissionAccounting.class))
        .withBean(TemporaryLinkTarget.class, () -> mock(TemporaryLinkTarget.class))
        .withBean(TemporaryLinkMapping.class, () -> mock(TemporaryLinkMapping.class))
        .withBean(TemporaryCommandService.class, () -> mock(TemporaryCommandService.class));

    @Test void disabledByDefaultRegistersNoOperationAndNoRoute() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBeansOfType(CommandOperation.class)).isEmpty();
            assertThat(context.getBeanNamesForType(TemporaryCommandController.class)).isEmpty();
        });
    }

    @Test void anyOtherValueThanTrueKeepsTheDefault() {
        runner.withPropertyValues("outbound.temporary.commands.enabled=false").run(context -> {
            assertThat(context.getBeansOfType(CommandOperation.class)).isEmpty();
            assertThat(context.getBeanNamesForType(TemporaryCommandController.class)).isEmpty();
        });
    }

    @Test void enabledRegistersExactlyOneOperationPerSupportedKind() {
        runner.withPropertyValues("outbound.temporary.commands.enabled=true").run(context -> {
            assertThat(context).hasNotFailed();
            List<CommandOperation> operations = List.copyOf(context.getBeansOfType(CommandOperation.class).values());
            assertThat(operations).hasSize(3);
            assertThat(operations.stream().map(CommandOperation::commandType))
                .containsExactlyInAnyOrder("NORMAL_CONFIRM", "SHORT_APPROVE", "LINK");
            // Ambiguity is rejected by CommandUnitOfWork, so one operation per kind is the contract.
            assertThat(operations.stream().map(CommandOperation::commandType).distinct().count()).isEqualTo(3);
            assertThat(context.getBeanNamesForType(TemporaryCommandController.class)).hasSize(1);
        });
    }

    @Test void everyRegisteredOperationKeepsTheHumanConfirmationGate() {
        runner.withPropertyValues("outbound.temporary.commands.enabled=true").run(context -> {
            assertThat(context.getBeansOfType(CommandOperation.class).values())
                .allSatisfy(operation -> {
                    assertThat(operation.recordsDecision()).isTrue();
                    assertThat(operation.requiresCandidateContext()).isTrue();
                });
        });
    }
}
