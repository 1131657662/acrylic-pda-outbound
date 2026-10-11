package com.acrylic.outbound.handover;

import com.acrylic.outbound.recovery.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** One explicit recovered enrollment seam; the owning archive transaction preserves all original facts. */
@Component
public final class HandoverRecoveredRecipientAdmission {
    private final HandoverRecoveredTemporaryReview review;
    private final HandoverRecoveredTemporaryProjection projection;
    private final JdbcTemplate jdbc;
    public HandoverRecoveredRecipientAdmission(HandoverRecoveredTemporaryReview review,
            HandoverRecoveredTemporaryProjection projection,JdbcTemplate jdbc) {
        this.review=review; this.projection=projection; this.jdbc=jdbc;
    }
    public Result admit(RecoveryRecipientAdmission admission,HandoverRecoveredPhysicalReview.Request request) {
        Objects.requireNonNull(admission); Objects.requireNonNull(request);
        var command=HandoverCommand.Input.parse(request.originalFault()); request.binding().require(command);
        HandoverCommand.corruptUnless("FAULT_FENCE".equals(command.kind()));
        var prepared=RecoveryReleaseReconciliation.prepare(review.inspectRecipient(request.originalFault(),request.binding(),admission),
            request.reconciliation());
        return request.binding().consumer().consumeAfterIdentityLocks(request.binding().request(),request.binding().nativeFacts(),
            new RecoveryReceivedTemporaryArchiveConsumer.IdentityConsumer<>() {
                private RecoveryRecipientAdmission.IdentityLocks identity;
                @Override public void lockIdentity() { identity=admission.lockIdentity(jdbc.getDataSource()); }
                @Override public void requireOrderVersion(JsonNode original,JsonNode current) { projection.require(command,original,current); }
                @Override public Result apply(RecoveryRecoveredTemporaryArchive archive,RecoveryTemporaryNativeHandoverFacts.Facts facts) {
                    request.binding().requireArchive(command,facts); var receipt=review.receipt(command);
                    request.binding().requireAnchor(command,receipt.path("caseContext"),archive);
                    admission.project(identity,new RecoveryRecipientAdmission.ProjectionContext(archive,receipt,prepared));
                    return new Result(admission.facts(),admission.sha256());
                }
            });
    }
    public record Result(JsonNode facts,String signedSha256) {
        public Result { facts=facts.deepCopy(); }
        @Override public JsonNode facts() { return facts.deepCopy(); }
        public boolean ordinaryWritesAllowed() { return false; }
        public boolean localDeletionAllowed() { return false; }
        public boolean newQualificationIssued() { return false; }
    }
}
