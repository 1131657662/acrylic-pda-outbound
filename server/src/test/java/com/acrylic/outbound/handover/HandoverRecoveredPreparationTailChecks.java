package com.acrylic.outbound.handover;

import com.acrylic.outbound.auth.SessionUser;
import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.recovery.*;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.*;

/** Real late mutations after signed PREPARE must roll back the entire closed transaction. */
public final class HandoverRecoveredPreparationTailChecks {
    public static void verify(WebApplicationContext app,Input input) {
        var jdbc=app.getBean(JdbcTemplate.class); var q=input.qualification();
        for(Runnable change:List.<Runnable>of(
            ()->jdbc.update("UPDATE warehouse SET name=CONCAT(name,'-prepare-tail') WHERE id<>?",Ids.bytes(SubmissionJson.uuid(q.facts(),"warehouseId"))),
            ()->jdbc.update("UPDATE handover_case SET prepared_ticket_sha256=UNHEX(REPEAT('aa',32)) WHERE id=?",Ids.bytes(SubmissionJson.uuid(q.facts(),"caseId"))),
            ()->jdbc.update("UPDATE app_user SET auth_version=auth_version+1 WHERE id=?",Ids.bytes(SubmissionJson.uuid(q.facts().path("recipient"),"userId"))),
            ()->expire(jdbc,q)))
            reject(app,input,change);
    }
    private static void expire(JdbcTemplate jdbc,RecoveryTemporaryQualification q) {
        long future=java.time.Instant.parse(q.facts().path("expiresAt").asText()).plusSeconds(60).getEpochSecond();
        jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>)connection->{
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override public void afterCompletion(int status) {
                        try(var statement=connection.createStatement()) { statement.execute("SET timestamp=0"); }
                        catch(java.sql.SQLException error) { throw new IllegalStateException(error); }
                    }
                });
            try(var statement=connection.createStatement()) { statement.execute("SET timestamp="+future); }
            return null;
        });
        assertThat(jdbc.queryForObject("SELECT UTC_TIMESTAMP(3)",java.sql.Timestamp.class).toInstant().getEpochSecond()).isEqualTo(future);
    }
    public static void reject(WebApplicationContext app,Input input,Runnable change) {
        var jdbc=app.getBean(JdbcTemplate.class); var review=app.getBean(HandoverRecoveredTemporaryReview.class);
        var q=input.qualification(); var request=input.request(); var fault=HandoverCommand.Input.parse(request.originalFault());
        HandoverRecoveredTemporaryFixture.authenticate(input.actor());
        try {
            var prepared=RecoveryReleaseReconciliation.prepare(review.inspectPreparation(request,q),request.reconciliation());
            assertThatThrownBy(()->request.binding().consumer().consumeAfterIdentityLocks(request.binding().request(),request.binding().nativeFacts(),
                new RecoveryReceivedTemporaryArchiveConsumer.IdentityConsumer<Void>() {
                    private RecoveryRecipientAdmission.IdentityLocks identity;
                    @Override public void lockIdentity() { identity=q.lockPreparationIdentity(jdbc.getDataSource()); }
                    @Override public void requireOrderVersion(JsonNode old,JsonNode now) { app.getBean(HandoverRecoveredTemporaryProjection.class).require(fault,old,now); }
                    @Override public Void apply(RecoveryRecoveredTemporaryArchive archive,RecoveryTemporaryNativeHandoverFacts.Facts facts) {
                        var context=new RecoveryTemporaryQualification.Context(jdbc.getDataSource(),
                            new RecoveryRecipientAdmission.ProjectionContext(archive,review.receipt(fault),prepared));
                        q.requirePreparationUse(context);
                        app.getBean(HandoverCommandService.class).prepareRecoveredLocked(HandoverCommand.Input.parse(HandoverReceiptCodec.bytes(input.command())),
                            app.getBean(HandoverCommand.Identity.class).current(SubmissionJson.uuid(q.facts(),"warehouseId")),"prepare-tail",q);
                        q.verifyPreparation(identity,context); change.run(); return null;
                    }
                })).isInstanceOfAny(RecoveryControlException.class,com.acrylic.outbound.common.BusinessException.class);
        } finally { SecurityContextHolder.clearContext(); }
    }
    public record Input(RecoveryTemporaryQualification qualification,HandoverRecoveredPhysicalReview.Request request,SessionUser actor,JsonNode command) { }
    private HandoverRecoveredPreparationTailChecks() { }
}
