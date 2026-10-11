package com.acrylic.outbound.handover;

import com.acrylic.outbound.recovery.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Closed read of the original archive for an already recorded, independently pinned receiving session. */
@Component
public final class HandoverRecoveredTemporaryDownload {
    private final HandoverCommandService commands;
    private final HandoverRecoveredTemporaryReview review;
    private final HandoverRecoveredTemporaryProjection projection;
    private final HandoverRecoveredDownloadDocument documents;
    private final JdbcTemplate jdbc;
    HandoverRecoveredTemporaryDownload(HandoverCommandService commands,HandoverRecoveredTemporaryReview review,
            HandoverRecoveredTemporaryProjection projection,HandoverRecoveredDownloadDocument documents,JdbcTemplate jdbc) {
        this.commands=commands; this.review=review; this.projection=projection; this.documents=documents; this.jdbc=jdbc;
    }
    public Result read(RecoveryRecipientSession session,HandoverRecoveredPhysicalReview.Request request,String requestId) {
        return read(session,request,requestId,()->{});
    }
    public Result read(RecoveryRecipientDownloadInput.Context input,String requestId) {
        input.requireUnchanged().run(); return read(input.session(),input.request(),requestId,input.requireUnchanged());
    }
    private Result read(RecoveryRecipientSession session,HandoverRecoveredPhysicalReview.Request request,String requestId,Runnable inputGuard) {
        commands.requireBoundary(requestId); Objects.requireNonNull(session); Objects.requireNonNull(request);
        var fault=HandoverCommand.Input.parse(request.originalFault()); request.binding().require(fault);
        var prepared=RecoveryReleaseReconciliation.prepare(review.inspectSession(request,session),request.reconciliation());
        return request.binding().consumer().consumeAfterIdentityLocks(request.binding().request(),request.binding().nativeFacts(),
            new RecoveryReceivedTemporaryArchiveConsumer.IdentityConsumer<>() {
                private RecoveryRecipientAdmission.IdentityLocks identity;
                @Override public void lockIdentity() { identity=session.lockIdentity(jdbc.getDataSource()); }
                @Override public void requireOrderVersion(JsonNode original,JsonNode current) { projection.require(fault,original,current); }
                @Override public Result apply(RecoveryRecoveredTemporaryArchive archive,RecoveryTemporaryNativeHandoverFacts.Facts facts) {
                    inputGuard.run();
                    org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                        new org.springframework.transaction.support.TransactionSynchronization() {
                            @Override public void beforeCommit(boolean readOnly) { inputGuard.run(); }
                        });
                    request.binding().requireArchive(fault,facts); var receipt=review.receipt(fault);
                    request.binding().requireAnchor(fault,receipt.path("caseContext"),archive);
                    session.qualification().requireSessionRead(identity,new RecoveryTemporaryQualification.Context(jdbc.getDataSource(),
                        new RecoveryRecipientAdmission.ProjectionContext(archive,receipt,prepared)),session);
                    return documents.read(session,archive);
                }
            });
    }
    public record Result(byte[] manifest,byte[] recoveryPackage) {
        public Result { manifest=manifest.clone(); recoveryPackage=recoveryPackage.clone(); }
        @Override public byte[] manifest() { return manifest.clone(); }
        @Override public byte[] recoveryPackage() { return recoveryPackage.clone(); }
        public boolean mayResumeScanning() { return false; }
        public boolean localDeletionAllowed() { return false; }
    }
}
