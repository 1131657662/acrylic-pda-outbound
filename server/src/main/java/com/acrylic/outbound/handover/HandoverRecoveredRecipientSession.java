package com.acrylic.outbound.handover;

import com.acrylic.outbound.recovery.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Actual receiving PDA identity plus exact independent login audit; ordinary work remains closed. */
@Component
public final class HandoverRecoveredRecipientSession {
    private final HandoverCommandService commands;
    private final HandoverRecoveredTemporaryReview review;
    private final HandoverRecoveredTemporaryProjection projection;
    private final JdbcTemplate jdbc;
    HandoverRecoveredRecipientSession(HandoverCommandService commands,HandoverRecoveredTemporaryReview review,
            HandoverRecoveredTemporaryProjection projection,JdbcTemplate jdbc) {
        this.commands=commands; this.review=review; this.projection=projection; this.jdbc=jdbc;
    }
    public Result admit(RecoveryRecipientSession session,HandoverRecoveredPhysicalReview.Request request,String requestId) {
        commands.requireBoundary(requestId); Objects.requireNonNull(session); Objects.requireNonNull(request);
        var fault=HandoverCommand.Input.parse(request.originalFault()); var qualification=session.qualification(); request.binding().require(fault);
        var prepared=RecoveryReleaseReconciliation.prepare(review.inspectSession(request,session),request.reconciliation());
        return request.binding().consumer().consumeAfterIdentityLocks(request.binding().request(),request.binding().nativeFacts(),
            new RecoveryReceivedTemporaryArchiveConsumer.IdentityConsumer<>() {
                private RecoveryRecipientAdmission.IdentityLocks identity;
                @Override public void lockIdentity() { identity=session.lockIdentity(jdbc.getDataSource()); }
                @Override public void requireOrderVersion(JsonNode original,JsonNode current) { projection.require(fault,original,current); }
                @Override public Result apply(RecoveryRecoveredTemporaryArchive archive,RecoveryTemporaryNativeHandoverFacts.Facts facts) {
                    request.binding().requireArchive(fault,facts); var receipt=review.receipt(fault);
                    request.binding().requireAnchor(fault,receipt.path("caseContext"),archive);
                    bindPreparation(session);
                    var context=new RecoveryTemporaryQualification.Context(jdbc.getDataSource(),
                        new RecoveryRecipientAdmission.ProjectionContext(archive,receipt,prepared));
                    qualification.admitSession(identity,context,session); return new Result(session.facts(),session.sha256());
                }
            });
    }
    private void bindPreparation(RecoveryRecipientSession session) {
        var facts=session.facts(); var rows=jdbc.queryForList("SELECT response_bytes,response_sha256 FROM handover_command_receipt WHERE case_id=? AND command_type='PREPARE' FOR UPDATE",
            com.acrylic.outbound.common.Ids.bytes(java.util.UUID.fromString(facts.path("caseId").asText())));
        HandoverCommand.corruptUnless(rows.size()==1 && HandoverCommand.sha((byte[])rows.getFirst().get("response_bytes"))
            .equals(facts.path("preparedReceiptSha256").asText()));
    }
    public record Result(JsonNode facts,String signedSha256) {
        public Result { facts=facts.deepCopy(); }
        @Override public JsonNode facts() { return facts.deepCopy(); }
        public boolean ordinaryWritesAllowed() { return false; }
        public boolean localDeletionAllowed() { return false; }
        public boolean mayResumeScanning() { return false; }
    }
}
