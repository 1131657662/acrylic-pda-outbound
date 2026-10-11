package com.acrylic.outbound.handover;

import com.acrylic.outbound.recovery.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.*;

/** Full original comparison and session guards reject changes after the archive has been read. */
public final class HandoverRecoveredDownloadTailChecks {
    public static void reject(WebApplicationContext app,RecoveryRecipientSession session,HandoverRecoveredPhysicalReview.Request request,Runnable change) {
        var jdbc=app.getBean(JdbcTemplate.class); var review=app.getBean(HandoverRecoveredTemporaryReview.class);
        var projection=app.getBean(HandoverRecoveredTemporaryProjection.class); var command=HandoverCommand.Input.parse(request.originalFault());
        var prepared=RecoveryReleaseReconciliation.prepare(review.inspectSession(request,session),request.reconciliation());
        assertThatThrownBy(()->request.binding().consumer().consumeAfterIdentityLocks(request.binding().request(),request.binding().nativeFacts(),
            new RecoveryReceivedTemporaryArchiveConsumer.IdentityConsumer<Void>() {
                private RecoveryRecipientAdmission.IdentityLocks identity;
                @Override public void lockIdentity() { identity=session.lockIdentity(jdbc.getDataSource()); }
                @Override public void requireOrderVersion(JsonNode old,JsonNode now) { projection.require(command,old,now); }
                @Override public Void apply(RecoveryRecoveredTemporaryArchive archive,RecoveryTemporaryNativeHandoverFacts.Facts facts) {
                    session.qualification().requireSessionRead(identity,new RecoveryTemporaryQualification.Context(jdbc.getDataSource(),
                        new RecoveryRecipientAdmission.ProjectionContext(archive,review.receipt(command),prepared)),session);
                    app.getBean(HandoverRecoveredDownloadDocument.class).read(session,archive); change.run(); return null;
                }
            })).isInstanceOfAny(RecoveryControlException.class,com.acrylic.outbound.common.BusinessException.class);
    }
    private HandoverRecoveredDownloadTailChecks() { }
}
