package com.acrylic.outbound.handover;

import com.acrylic.outbound.recovery.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.*;

/** Uses the production confirmation store under the same source transaction, then injects a late mutation. */
public final class HandoverRecoveredConfirmationTailChecks {
    public static void reject(WebApplicationContext app,byte[] raw,RecoveryRecipientSession session,HandoverRecoveredPhysicalReview.Request request,Runnable change) {
        var jdbc=app.getBean(JdbcTemplate.class); var review=app.getBean(HandoverRecoveredTemporaryReview.class);
        var projection=app.getBean(HandoverRecoveredTemporaryProjection.class); var fault=HandoverCommand.Input.parse(request.originalFault());
        var prepared=RecoveryReleaseReconciliation.prepare(review.inspectSession(request,session),request.reconciliation());
        assertThatThrownBy(()->request.binding().consumer().consumeAfterIdentityLocks(request.binding().request(),request.binding().nativeFacts(),
            new RecoveryReceivedTemporaryArchiveConsumer.IdentityConsumer<Void>() {
                private RecoveryRecipientAdmission.IdentityLocks identity;
                @Override public void lockIdentity() { identity=session.lockIdentity(jdbc.getDataSource()); }
                @Override public void requireOrderVersion(JsonNode old,JsonNode now) { projection.require(fault,old,now); }
                @Override public Void apply(RecoveryRecoveredTemporaryArchive archive,RecoveryTemporaryNativeHandoverFacts.Facts facts) {
                    session.qualification().requireSessionRead(identity,new RecoveryTemporaryQualification.Context(jdbc.getDataSource(),
                        new RecoveryRecipientAdmission.ProjectionContext(archive,review.receipt(fault),prepared)),session);
                    var document=app.getBean(HandoverRecoveredDownloadDocument.class).read(session,archive);
                    var command=HandoverRecoveredConfirmationCommand.parse(raw); command.bind(session,document);
                    app.getBean(HandoverRecoveredConfirmationStore.class).confirm(new HandoverRecoveredConfirmationReceipt.Context(command,session,document,
                        session.requirePrincipal(jdbc.getDataSource())),"confirmation-tail");
                    change.run(); return null;
                }
            })).isInstanceOfAny(RecoveryControlException.class,com.acrylic.outbound.common.BusinessException.class);
    }
    private HandoverRecoveredConfirmationTailChecks() { }
}
