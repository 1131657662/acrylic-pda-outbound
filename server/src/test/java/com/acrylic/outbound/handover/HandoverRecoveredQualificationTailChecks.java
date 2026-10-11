package com.acrylic.outbound.handover;

import com.acrylic.outbound.auth.SessionUser;
import com.acrylic.outbound.recovery.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.*;

/** Mutations after the actual grant must be rejected by the outer transaction's final guards. */
public final class HandoverRecoveredQualificationTailChecks {
    public static void reject(WebApplicationContext app,Input input) {
        var jdbc=app.getBean(JdbcTemplate.class); var review=app.getBean(HandoverRecoveredTemporaryReview.class);
        var projection=app.getBean(HandoverRecoveredTemporaryProjection.class); var request=input.request();
        var command=HandoverCommand.Input.parse(request.originalFault()); var recipient=input.qualification().recipient();
        HandoverRecoveredTemporaryFixture.authenticate(input.registrar());
        try {
            var prepared=RecoveryReleaseReconciliation.prepare(review.inspectRecipient(request.originalFault(),request.binding(),recipient),request.reconciliation());
            assertThatThrownBy(()->request.binding().consumer().consumeAfterIdentityLocks(request.binding().request(),request.binding().nativeFacts(),
                new RecoveryReceivedTemporaryArchiveConsumer.IdentityConsumer<Void>() {
                    private RecoveryRecipientAdmission.IdentityLocks identity;
                    @Override public void lockIdentity() { identity=recipient.lockIdentity(jdbc.getDataSource()); }
                    @Override public void requireOrderVersion(JsonNode old,JsonNode now) { projection.require(command,old,now); }
                    @Override public Void apply(RecoveryRecoveredTemporaryArchive archive,RecoveryTemporaryNativeHandoverFacts.Facts facts) {
                        input.qualification().issue(identity,new RecoveryTemporaryQualification.Context(jdbc.getDataSource(),
                            new RecoveryRecipientAdmission.ProjectionContext(archive,review.receipt(command),prepared)));
                        input.change().run(); return null;
                    }
                })).isInstanceOfAny(RecoveryControlException.class,com.acrylic.outbound.common.BusinessException.class);
        } finally { SecurityContextHolder.clearContext(); }
    }
    public record Input(RecoveryTemporaryQualification qualification,HandoverRecoveredPhysicalReview.Request request,SessionUser registrar,Runnable change) { }
    private HandoverRecoveredQualificationTailChecks() { }
}
