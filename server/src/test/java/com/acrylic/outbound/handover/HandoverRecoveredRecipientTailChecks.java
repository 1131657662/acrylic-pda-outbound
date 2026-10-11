package com.acrylic.outbound.handover;

import com.acrylic.outbound.auth.SessionUser;
import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.recovery.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.*;

/** A callback after the actual enrollment must not escape full original-row and identity guards. */
public final class HandoverRecoveredRecipientTailChecks {
    public static void verify(WebApplicationContext app,RecoveryRecipientAdmission admission,
            HandoverRecoveredPhysicalReview.Request request,SessionUser registrar) {
        var jdbc=app.getBean(JdbcTemplate.class); var review=app.getBean(HandoverRecoveredTemporaryReview.class);
        var projection=app.getBean(HandoverRecoveredTemporaryProjection.class); var command=HandoverCommand.Input.parse(request.originalFault());
        HandoverRecoveredTemporaryFixture.authenticate(registrar);
        try {
            var prepared=RecoveryReleaseReconciliation.prepare(review.inspectRecipient(request.originalFault(),request.binding(),admission),request.reconciliation());
            for(String sql:java.util.List.of("UPDATE warehouse SET name=CONCAT(name,'-recipient-tail') WHERE id<>?",
                    "UPDATE device SET name=CONCAT(name,'-recipient-tail') WHERE warehouse_id<>?")) {
                assertThatThrownBy(()->request.binding().consumer().consumeAfterIdentityLocks(request.binding().request(),request.binding().nativeFacts(),
                    new RecoveryReceivedTemporaryArchiveConsumer.IdentityConsumer<Void>() {
                        private RecoveryRecipientAdmission.IdentityLocks locked;
                        @Override public void lockIdentity() { locked=admission.lockIdentity(jdbc.getDataSource()); }
                        @Override public void requireOrderVersion(JsonNode old,JsonNode now) { projection.require(command,old,now); }
                        @Override public Void apply(RecoveryRecoveredTemporaryArchive archive,RecoveryTemporaryNativeHandoverFacts.Facts facts) {
                            admission.project(locked,new RecoveryRecipientAdmission.ProjectionContext(archive,review.receipt(command),prepared));
                            assertThat(jdbc.update(sql,Ids.bytes(command.uuid("warehouseId")))).isPositive(); return null;
                        }
                    })).isInstanceOf(RecoveryControlException.class);
            }
        } finally { SecurityContextHolder.clearContext(); }
    }
    private HandoverRecoveredRecipientTailChecks() { }
}
