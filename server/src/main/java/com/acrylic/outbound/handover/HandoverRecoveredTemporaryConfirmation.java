package com.acrylic.outbound.handover;

import com.acrylic.outbound.recovery.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Confirms one exact recovered archive inside its closed journal; no work or activation is created. */
@Component
public final class HandoverRecoveredTemporaryConfirmation {
    private final HandoverCommandService commands;
    private final HandoverRecoveredTemporaryReview review;
    private final HandoverRecoveredTemporaryProjection projection;
    private final HandoverRecoveredDownloadDocument documents;
    private final HandoverRecoveredConfirmationStore store;
    private final JdbcTemplate jdbc;
    HandoverRecoveredTemporaryConfirmation(HandoverCommandService commands,HandoverRecoveredTemporaryReview review,
            HandoverRecoveredTemporaryProjection projection,HandoverRecoveredDownloadDocument documents,
            HandoverRecoveredConfirmationStore store,JdbcTemplate jdbc) {
        this.commands=commands; this.review=review; this.projection=projection; this.documents=documents; this.store=store; this.jdbc=jdbc;
    }
    public byte[] confirm(byte[] raw,RecoveryRecipientDownloadInput.Context input,String requestId) {
        commands.requireBoundary(requestId); Objects.requireNonNull(input); input.requireUnchanged().run();
        var command=HandoverRecoveredConfirmationCommand.parse(raw); var session=input.session(); var request=input.request();
        var fault=HandoverCommand.Input.parse(request.originalFault()); request.binding().require(fault);
        var prepared=RecoveryReleaseReconciliation.prepare(review.inspectSession(request,session),request.reconciliation());
        return request.binding().consumer().consumeAfterIdentityLocks(request.binding().request(),request.binding().nativeFacts(),
            new RecoveryReceivedTemporaryArchiveConsumer.IdentityConsumer<>() {
                private RecoveryRecipientAdmission.IdentityLocks identity;
                @Override public void lockIdentity() { identity=session.lockIdentity(jdbc.getDataSource()); }
                @Override public void requireOrderVersion(JsonNode original,JsonNode current) { projection.require(fault,original,current); }
                @Override public byte[] apply(RecoveryRecoveredTemporaryArchive archive,RecoveryTemporaryNativeHandoverFacts.Facts facts) {
                    input.requireUnchanged().run(); tail(input.requireUnchanged());
                    request.binding().requireArchive(fault,facts); var receipt=review.receipt(fault);
                    request.binding().requireAnchor(fault,receipt.path("caseContext"),archive);
                    session.qualification().requireSessionRead(identity,new RecoveryTemporaryQualification.Context(jdbc.getDataSource(),
                        new RecoveryRecipientAdmission.ProjectionContext(archive,receipt,prepared)),session);
                    var document=documents.read(session,archive); command.bind(session,document);
                    var actor=session.requirePrincipal(jdbc.getDataSource());
                    return store.confirm(new HandoverRecoveredConfirmationReceipt.Context(command,session,document,actor),requestId);
                }
            });
    }
    private void tail(Runnable guard) {
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
            new org.springframework.transaction.support.TransactionSynchronization() {
                @Override public void beforeCommit(boolean readOnly) { guard.run(); }
            });
    }
}
