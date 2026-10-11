package com.acrylic.outbound.handover;

import com.acrylic.outbound.recovery.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Explicit new-epoch qualification, after retained recipient enrollment and full original-scope verification. */
@Component
public final class HandoverRecoveredTemporaryQualification {
    private final HandoverRecoveredTemporaryReview review;
    private final HandoverRecoveredTemporaryProjection projection;
    private final JdbcTemplate jdbc;
    public HandoverRecoveredTemporaryQualification(HandoverRecoveredTemporaryReview review,
            HandoverRecoveredTemporaryProjection projection,JdbcTemplate jdbc) {
        this.review=review; this.projection=projection; this.jdbc=jdbc;
    }
    public RecoveryTemporaryQualification.Result issue(RecoveryTemporaryQualification qualification,HandoverRecoveredPhysicalReview.Request request) {
        Objects.requireNonNull(qualification); Objects.requireNonNull(request);
        var recipient=qualification.recipient(); var command=HandoverCommand.Input.parse(request.originalFault());
        request.binding().require(command); HandoverCommand.corruptUnless(command.kind().equals("FAULT_FENCE"));
        var prepared=RecoveryReleaseReconciliation.prepare(review.inspectRecipient(request.originalFault(),request.binding(),recipient),request.reconciliation());
        return request.binding().consumer().consumeAfterIdentityLocks(request.binding().request(),request.binding().nativeFacts(),
            new RecoveryReceivedTemporaryArchiveConsumer.IdentityConsumer<>() {
                private RecoveryRecipientAdmission.IdentityLocks identity;
                @Override public void lockIdentity() { identity=recipient.lockIdentity(jdbc.getDataSource()); }
                @Override public void requireOrderVersion(JsonNode original,JsonNode current) { projection.require(command,original,current); }
                @Override public RecoveryTemporaryQualification.Result apply(RecoveryRecoveredTemporaryArchive archive,RecoveryTemporaryNativeHandoverFacts.Facts facts) {
                    request.binding().requireArchive(command,facts); var receipt=review.receipt(command);
                    request.binding().requireAnchor(command,receipt.path("caseContext"),archive);
                    return qualification.issue(identity,new RecoveryTemporaryQualification.Context(jdbc.getDataSource(),
                        new RecoveryRecipientAdmission.ProjectionContext(archive,receipt,prepared)));
                }
            });
    }
}
