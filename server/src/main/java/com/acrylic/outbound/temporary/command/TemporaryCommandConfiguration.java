package com.acrylic.outbound.temporary.command;

import com.acrylic.outbound.submissions.SubmissionAccounting;
import com.acrylic.outbound.submissions.SubmissionStock;
import com.acrylic.outbound.temporary.review.TemporaryDecisionCandidates;
import com.acrylic.outbound.temporary.review.TemporaryLinkTarget;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Opt-in assembly for the TEMP decision operations.
 *
 * <p>Production runs with an empty operation set: {@code CommandUnitOfWork} then rejects every kind with
 * {@code COMMAND_EXECUTION_NOT_IMPLEMENTED}, which is the behaviour this repository ships and verifies.
 * Setting {@code outbound.temporary.commands.enabled=true} is an explicit operator decision, not a test
 * result: enabling it lets NORMAL_CONFIRM/SHORT_APPROVE perform real stock and accounting effects and lets
 * LINK install a mapping version. It still does not advertise any capability — the signed receipt keeps
 * {@code writeConfirmationAvailable/releaseAllowed/mayResumeScanning/stockReleased/localDeletionAllowed=false}
 * from {@link CommandReceiptCodec#capabilities()}, and the published contract keeps those fields
 * {@code const: false}.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "outbound.temporary.commands", name = "enabled", havingValue = "true")
public class TemporaryCommandConfiguration {

    @Bean
    CommandOperation temporaryNormalConfirmation(TemporaryConfirmationSource source,
            TemporaryDecisionCandidates candidates, TemporaryShortApprovals approvals, SubmissionStock stock,
            SubmissionAccounting accounting) {
        return new TemporaryConfirmationOperation("NORMAL_CONFIRM",
            new TemporaryConfirmationOperation.Dependencies(source, candidates, approvals, stock, accounting));
    }

    @Bean
    CommandOperation temporaryShortApproval(TemporaryConfirmationSource source,
            TemporaryDecisionCandidates candidates, TemporaryShortApprovals approvals, SubmissionStock stock,
            SubmissionAccounting accounting) {
        return new TemporaryConfirmationOperation("SHORT_APPROVE",
            new TemporaryConfirmationOperation.Dependencies(source, candidates, approvals, stock, accounting));
    }

    @Bean
    CommandOperation temporaryControlledLink(TemporaryConfirmationSource source,
            TemporaryDecisionCandidates candidates, TemporaryLinkTarget targets, TemporaryLinkMapping mappings) {
        return new TemporaryLinkOperation(
            new TemporaryLinkOperation.Dependencies(source, candidates, targets, mappings));
    }

    @Bean
    CommandOperation temporaryRejection(TemporaryConfirmationSource source, TemporaryDecisionCandidates candidates,
            com.acrylic.outbound.submissions.SubmissionStore submissions,
            com.acrylic.outbound.submissions.SubmissionEvents events) {
        return new TemporaryRejectionOperation(
            new TemporaryRejectionOperation.Dependencies(source, candidates, submissions, events));
    }

    @Bean
    CommandOperation temporaryPhysicalDisposition(PhysicalDispositionVerification verification,
            PhysicalDispositionStore store) {
        return new PhysicalDispositionOperation(verification, store);
    }

    @Bean
    CommandOperation temporaryCloseDisposition(PhysicalDispositionVerification verification,
            PhysicalDispositionStore store) {
        return new CloseDispositionOperation(verification, store);
    }

    @Bean
    CommandOperation temporaryReopenWork(PhysicalDispositionVerification verification,
            TemporaryContinuationTicketStore tickets) {
        return new TemporaryReopenWorkOperation(verification, tickets);
    }

    @Bean
    CommandOperation temporaryContinuationActivation(org.springframework.jdbc.core.JdbcTemplate jdbc,
            TemporaryContinuationAuthorizationIssuer authorizations) {
        return new TemporaryContinuationActivationOperation(jdbc, authorizations);
    }
}
