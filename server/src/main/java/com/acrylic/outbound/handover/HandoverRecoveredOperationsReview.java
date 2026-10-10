package com.acrylic.outbound.handover;

import com.acrylic.outbound.recovery.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Offline operations verification under the original closed source and supervisor identity locks. */
@Component
public final class HandoverRecoveredOperationsReview {
    private final HandoverRecoveredTemporaryReview review;
    private final HandoverRecoveredTemporaryProjection projection;
    private final HandoverRecoveredReviewAccess access;
    private final JdbcTemplate jdbc;
    HandoverRecoveredOperationsReview(HandoverRecoveredTemporaryReview review,HandoverRecoveredTemporaryProjection projection,
            HandoverRecoveredReviewAccess access,JdbcTemplate jdbc) {
        this.review=review; this.projection=projection; this.access=access; this.jdbc=jdbc;
    }
    public RecoveryOperationsApproval verify(RecoveryOperationsApproval.Request approval,
            RecoveryOperationsApproval.Context reviewed,HandoverRecoveredPhysicalReview.Request request) {
        var checked=RecoveryOperationsApproval.verify(approval,reviewed);
        inspect(request,checked); return checked;
    }
    /** Capture a new fixed review after APPROVE, including history hashes bound to that approved inventory. */
    public RecoveryOperationsApproval.Context inspect(HandoverRecoveredPhysicalReview.Request request) {
        return inspect(request,null);
    }
    private RecoveryOperationsApproval.Context inspect(HandoverRecoveredPhysicalReview.Request request,RecoveryOperationsApproval checked) {
        var command=HandoverCommand.Input.parse(request.originalFault()); request.binding().require(command);
        HandoverCommand.corruptUnless("FAULT_FENCE".equals(command.kind())); var actor=access.current(command);
        var prepared=RecoveryReleaseReconciliation.prepare(review.inspect(request.originalFault(),request.binding()),request.reconciliation());
        return request.binding().consumer().consumeAfterIdentityLocks(request.binding().request(),request.binding().nativeFacts(),
            new RecoveryReceivedTemporaryArchiveConsumer.IdentityConsumer<>() {
                @Override public void lockIdentity() {
                    if(!org.springframework.transaction.support.TransactionSynchronizationManager.hasResource(jdbc.getDataSource()))
                        throw new RecoveryControlException("RECOVERY_OPERATIONS_TRANSACTION_REQUIRED");
                    access.lock(command,actor);
                }
                @Override public void requireOrderVersion(JsonNode original,JsonNode current) { projection.require(command,original,current); }
                @Override public RecoveryOperationsApproval.Context apply(RecoveryRecoveredTemporaryArchive archive,RecoveryTemporaryNativeHandoverFacts.Facts facts) {
                    request.binding().requireArchive(command,facts); var receipt=review.receipt(command);
                    request.binding().requireAnchor(command,receipt.path("caseContext"),archive);
                    var inventory=new RecoveryReleaseInventoryCapture(jdbc.getDataSource()).capture(archive,receipt);
                    var physical=RecoveryReleasePhysicalReview.inspect(jdbc.getDataSource(),inventory,prepared);
                    var current=new RecoveryOperationsApproval.Context(physical,inventory);
                    if(checked!=null) checked.revalidate(current,request.reconciliation().authority()); return current;
                }
            });
    }
}
