package com.acrylic.outbound.handover;

import com.acrylic.outbound.recovery.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Existing PREPARE protocol, explicitly qualified inside the original closed archive transaction. */
@Component
public final class HandoverRecoveredTemporaryPreparation {
    private final HandoverCommandService commands;
    private final HandoverRecoveredTemporaryReview review;
    private final HandoverRecoveredTemporaryProjection projection;
    private final HandoverCommand.Identity identities;
    private final JdbcTemplate jdbc;
    HandoverRecoveredTemporaryPreparation(HandoverCommandService commands,HandoverRecoveredTemporaryReview review,
            HandoverRecoveredTemporaryProjection projection,HandoverCommand.Identity identities,JdbcTemplate jdbc) {
        this.commands=commands; this.review=review; this.projection=projection; this.identities=identities; this.jdbc=jdbc;
    }
    public byte[] prepare(byte[] raw,String requestId,RecoveryTemporaryQualification qualification,HandoverRecoveredPhysicalReview.Request request) {
        commands.requireBoundary(requestId); Objects.requireNonNull(qualification); Objects.requireNonNull(request);
        var input=HandoverCommand.Input.parse(raw); var fault=HandoverCommand.Input.parse(request.originalFault());
        request.binding().require(fault); bind(input,qualification);
        var prepared=RecoveryReleaseReconciliation.prepare(review.inspectPreparation(request,qualification),request.reconciliation());
        return request.binding().consumer().consumeAfterIdentityLocks(request.binding().request(),request.binding().nativeFacts(),
            new RecoveryReceivedTemporaryArchiveConsumer.IdentityConsumer<>() {
                private RecoveryRecipientAdmission.IdentityLocks identity;
                @Override public void lockIdentity() { identity=qualification.lockPreparationIdentity(jdbc.getDataSource()); }
                @Override public void requireOrderVersion(JsonNode original,JsonNode current) { projection.require(fault,original,current); }
                @Override public byte[] apply(RecoveryRecoveredTemporaryArchive archive,RecoveryTemporaryNativeHandoverFacts.Facts facts) {
                    request.binding().requireArchive(fault,facts); var receipt=review.receipt(fault);
                    request.binding().requireAnchor(fault,receipt.path("caseContext"),archive);
                    var context=new RecoveryTemporaryQualification.Context(jdbc.getDataSource(),
                        new RecoveryRecipientAdmission.ProjectionContext(archive,receipt,prepared));
                    qualification.requirePreparationUse(context);
                    byte[] result=commands.prepareRecoveredLocked(input,identities.current(input.uuid("warehouseId")),requestId,qualification);
                    qualification.verifyPreparation(identity,context); return result;
                }
            });
    }
    private void bind(HandoverCommand.Input input,RecoveryTemporaryQualification qualification) {
        var q=qualification.facts(); var recipient=q.path("recipient");
        RecoveryPreparationCommand.require(input.body(),q);
        HandoverCommand.corruptUnless(input.kind().equals("PREPARE") && input.text("caseId").equals(q.path("caseId").asText())
            && input.text("warehouseId").equals(q.path("warehouseId").asText())
            && input.text("targetQualificationId").equals(q.path("qualificationId").asText())
            && input.text("targetDeviceId").equals(recipient.path("deviceId").asText())
            && input.text("targetUserId").equals(recipient.path("userId").asText())
            && input.text("transportRecoveryEpoch").equals(q.path("control").path("transportRecoveryEpoch").asText()));
    }
}
