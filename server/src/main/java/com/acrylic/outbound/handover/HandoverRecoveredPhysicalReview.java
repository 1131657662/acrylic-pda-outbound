package com.acrylic.outbound.handover;

import com.acrylic.outbound.recovery.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Explicit offline APPROVE seam; the complete scope is verified before the outer transaction commits. */
@Component
public final class HandoverRecoveredPhysicalReview {
    private final HandoverCommandService commands;
    private final HandoverRecoveredTemporaryReview review;
    private final HandoverRecoveredTemporaryProjection projection;
    private final HandoverCommand.Identity identities;
    private final HandoverWriteAccess access;
    private final JdbcTemplate jdbc;
    HandoverRecoveredPhysicalReview(HandoverCommandService commands,HandoverRecoveredTemporaryReview review,
            HandoverRecoveredTemporaryProjection projection,HandoverCommand.Identity identities,HandoverWriteAccess access,JdbcTemplate jdbc) {
        this.commands=commands; this.review=review; this.projection=projection; this.identities=identities; this.access=access; this.jdbc=jdbc;
    }
    public Result approve(byte[] raw,String requestId,Request request) {
        commands.requireBoundary(requestId); Objects.requireNonNull(request);
        var input=HandoverCommand.Input.parse(raw); var fault=HandoverCommand.Input.parse(request.originalFault());
        request.binding().require(fault); bind(input,fault);
        var actor=identities.current(input.uuid("warehouseId"));
        var inventory=review.inspect(request.originalFault(),request.binding());
        var prepared=RecoveryReleaseReconciliation.prepare(inventory,request.reconciliation());
        return request.binding().consumer().consumeAfterIdentityLocks(request.binding().request(),request.binding().nativeFacts(),
            new RecoveryReceivedTemporaryArchiveConsumer.IdentityConsumer<>() {
                private HandoverCommand.Actor locked;
                @Override public void lockIdentity() { locked=access.lockRecovered(fault,actor); }
                @Override public void requireOrderVersion(JsonNode original,JsonNode current) { projection.require(fault,original,current); }
                @Override public Result apply(RecoveryRecoveredTemporaryArchive archive,RecoveryTemporaryNativeHandoverFacts.Facts facts) {
                    request.binding().requireArchive(fault,facts); var receipt=review.receipt(fault);
                    request.binding().requireAnchor(fault,receipt.path("caseContext"),archive);
                    byte[] approved=commands.approveRecoveredLocked(input,locked,requestId);
                    var current=new RecoveryReleaseInventoryCapture(jdbc.getDataSource()).capture(archive,receipt);
                    var proof=RecoveryReleasePhysicalReview.inspect(jdbc.getDataSource(),current,prepared);
                    return new Result(approved,proof);
                }
            });
    }
    private void bind(HandoverCommand.Input input,HandoverCommand.Input fault) {
        HandoverCommand.corruptUnless(input.kind().equals("APPROVE") && fault.kind().equals("FAULT_FENCE"));
        for(String field:java.util.List.of("caseId","warehouseId","transportRecoveryEpoch"))
            HandoverCommand.corruptUnless(Objects.equals(input.text(field),fault.text(field)));
    }
    public record Request(byte[] originalFault,HandoverRecoveredTemporaryBinding binding,RecoveryReleaseReconciliation.Request reconciliation) {
        public Request { originalFault=originalFault.clone(); Objects.requireNonNull(binding); Objects.requireNonNull(reconciliation); }
        @Override public byte[] originalFault() { return originalFault.clone(); }
    }
    public record Result(byte[] receipt,RecoveryReleasePhysicalReview proof) {
        public Result { receipt=receipt.clone(); }
        @Override public byte[] receipt() { return receipt.clone(); }
    }
}
