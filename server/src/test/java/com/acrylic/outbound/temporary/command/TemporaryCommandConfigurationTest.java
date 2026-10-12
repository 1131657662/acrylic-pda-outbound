package com.acrylic.outbound.temporary.command;

import com.acrylic.outbound.submissions.SubmissionAccounting;
import com.acrylic.outbound.submissions.SubmissionEvents;
import com.acrylic.outbound.submissions.SubmissionStock;
import com.acrylic.outbound.submissions.SubmissionStore;
import com.acrylic.outbound.temporary.review.TemporaryDecisionCandidates;
import com.acrylic.outbound.temporary.review.TemporaryLinkTarget;
import com.acrylic.outbound.temporary.continuation.TemporaryRoundCompletion;
import com.acrylic.outbound.common.ApiResponses;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** The production default must stay identical: no operation, no route, every kind unsupported. */
class TemporaryCommandConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(TemporaryCommandConfiguration.class, TemporaryCommandController.class,
            TemporaryCloseDispositionController.class, TemporaryDispositionFactsController.class)
        .withBean(TemporaryDispositionFacts.class, () -> mock(TemporaryDispositionFacts.class))
        .withBean(ApiResponses.class, () -> mock(ApiResponses.class))
        .withBean(TemporaryConfirmationSource.class, () -> mock(TemporaryConfirmationSource.class))
        .withBean(TemporaryDecisionCandidates.class, () -> mock(TemporaryDecisionCandidates.class))
        .withBean(TemporaryShortApprovals.class, () -> mock(TemporaryShortApprovals.class))
        .withBean(SubmissionStock.class, () -> mock(SubmissionStock.class))
        .withBean(SubmissionAccounting.class, () -> mock(SubmissionAccounting.class))
        .withBean(SubmissionStore.class, () -> mock(SubmissionStore.class))
        .withBean(SubmissionEvents.class, () -> mock(SubmissionEvents.class))
        .withBean(TemporaryLinkTarget.class, () -> mock(TemporaryLinkTarget.class))
        .withBean(TemporaryLinkMapping.class, () -> mock(TemporaryLinkMapping.class))
        .withBean(PhysicalDispositionVerification.class, () -> mock(PhysicalDispositionVerification.class))
        .withBean(PhysicalDispositionStore.class, () -> mock(PhysicalDispositionStore.class))
        .withBean(TemporaryContinuationTicketStore.class, () -> mock(TemporaryContinuationTicketStore.class))
        .withBean(TemporaryContinuationAuthorizationIssuer.class, () -> mock(TemporaryContinuationAuthorizationIssuer.class))
        .withBean(TemporaryRoundCompletion.class, () -> mock(TemporaryRoundCompletion.class))
        .withBean(TemporaryCandidateSelection.class, () -> mock(TemporaryCandidateSelection.class))
        .withBean(TemporaryExclusionFacts.class, () -> mock(TemporaryExclusionFacts.class))
        .withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class))
        .withBean(TemporaryCommandService.class, () -> mock(TemporaryCommandService.class));

    @Test void disabledByDefaultRegistersNoOperationAndNoRoute() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBeansOfType(CommandOperation.class)).isEmpty();
            assertThat(context.getBeanNamesForType(TemporaryCommandController.class)).isEmpty();
            assertThat(context.getBeanNamesForType(TemporaryCloseDispositionController.class)).isEmpty();
            assertThat(context.getBeanNamesForType(TemporaryDispositionFactsController.class)).isEmpty();
        });
    }

    @Test void anyOtherValueThanTrueKeepsTheDefault() {
        runner.withPropertyValues("outbound.temporary.commands.enabled=false").run(context -> {
            assertThat(context.getBeansOfType(CommandOperation.class)).isEmpty();
            assertThat(context.getBeanNamesForType(TemporaryCommandController.class)).isEmpty();
            assertThat(context.getBeanNamesForType(TemporaryCloseDispositionController.class)).isEmpty();
            assertThat(context.getBeanNamesForType(TemporaryDispositionFactsController.class)).isEmpty();
        });
    }

    @Test void enabledRegistersExactlyOneOperationPerSupportedKind() {
        runner.withPropertyValues("outbound.temporary.commands.enabled=true").run(context -> {
            assertThat(context).hasNotFailed();
            List<CommandOperation> operations = List.copyOf(context.getBeansOfType(CommandOperation.class).values());
            assertThat(operations).hasSize(9);
            assertThat(operations.stream().map(CommandOperation::commandType))
                .containsExactlyInAnyOrder("NORMAL_CONFIRM", "SHORT_APPROVE", "LINK", "REJECT", "DUPLICATE_EXCLUDE", "PHYSICAL_DISPOSITION", "CLOSE_DISPOSITION", "REOPEN_WORK", "ACTIVATE_CONTINUATION");
            // Ambiguity is rejected by CommandUnitOfWork, so one operation per kind is the contract.
            assertThat(operations.stream().map(CommandOperation::commandType).distinct().count()).isEqualTo(9);
            assertThat(context.getBeanNamesForType(TemporaryCommandController.class)).hasSize(1);
            assertThat(context.getBeanNamesForType(TemporaryCloseDispositionController.class)).hasSize(1);
            assertThat(context.getBeanNamesForType(TemporaryDispositionFactsController.class)).hasSize(1);
        });
    }

    @Test void everyRegisteredOperationKeepsTheHumanConfirmationGate() {
        runner.withPropertyValues("outbound.temporary.commands.enabled=true").run(context -> {
            assertThat(context.getBeansOfType(CommandOperation.class).values())
                .allSatisfy(operation -> {
                    assertThat(operation.recordsDecision()).isTrue();
                    if (Set.of("PHYSICAL_DISPOSITION", "CLOSE_DISPOSITION", "REOPEN_WORK", "ACTIVATE_CONTINUATION").contains(operation.commandType()))
                        assertThat(operation.requiresCandidateContext()).isFalse();
                    else assertThat(operation.requiresCandidateContext()).isTrue();
                });
        });
    }
}
