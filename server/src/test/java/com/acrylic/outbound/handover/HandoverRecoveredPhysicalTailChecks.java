package com.acrylic.outbound.handover;

import com.acrylic.outbound.recovery.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.*;

/** Late mutations after an approved proof still roll back under the original closed transaction. */
final class HandoverRecoveredPhysicalTailChecks {
    static void verify(WebApplicationContext app,HandoverRecoveredTemporaryFixture f,HandoverRecoveredPhysicalReview.Request request) throws Exception {
        var review=app.getBean(HandoverRecoveredTemporaryReview.class);
        HandoverRecoveredTemporaryFixture.authenticate(f.supervisor);
        try {
            var inventory=review.inspect(request.originalFault(),f.binding);
            var prepared=RecoveryReleaseReconciliation.prepare(inventory,request.reconciliation());
            var command=HandoverCommand.Input.parse(request.originalFault());
            var projection=app.getBean(HandoverRecoveredTemporaryProjection.class);
            for(String sql:List.of("UPDATE warehouse SET name=CONCAT(name,'-tail') WHERE id<>?",
                    "UPDATE device SET name=CONCAT(name,'-tail') WHERE warehouse_id<>?")) {
                var before=f.snapshots.capture();
                assertThatThrownBy(()->f.binding.consumer().consumeAfterIdentityLocks(f.binding.request(),f.binding.nativeFacts(),
                    new RecoveryReceivedTemporaryArchiveConsumer.IdentityConsumer<RecoveryReleasePhysicalReview>() {
                        @Override public void lockIdentity() { }
                        @Override public void requireOrderVersion(JsonNode old,JsonNode current) { projection.require(command,old,current); }
                        @Override public RecoveryReleasePhysicalReview apply(RecoveryRecoveredTemporaryArchive archive,RecoveryTemporaryNativeHandoverFacts.Facts facts) {
                            var current=new RecoveryReleaseInventoryCapture(f.jdbc.getDataSource()).capture(archive,review.receipt(command));
                            var proof=RecoveryReleasePhysicalReview.inspect(f.jdbc.getDataSource(),current,prepared);
                            assertThat(f.jdbc.update(sql,com.acrylic.outbound.common.Ids.bytes(command.uuid("warehouseId")))).isPositive(); return proof;
                        }
                    })).hasMessage("RECOVERY_SCOPE_CHANGED");
                assertThat(f.snapshots.capture()).isEqualTo(before);
            }
        } finally { SecurityContextHolder.clearContext(); }
    }
    private HandoverRecoveredPhysicalTailChecks() { }
}
