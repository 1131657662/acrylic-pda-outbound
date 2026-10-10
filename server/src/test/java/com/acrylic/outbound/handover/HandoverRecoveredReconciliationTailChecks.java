package com.acrylic.outbound.handover;

import com.acrylic.outbound.recovery.*;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.*;

/** Changes outside the scoped inventory must still invalidate the full original-fact proof. */
final class HandoverRecoveredReconciliationTailChecks {
    static void verify(WebApplicationContext app,HandoverRecoveredTemporaryBinding binding,Input input) throws Exception {
        var jdbc=app.getBean(JdbcTemplate.class); var review=app.getBean(HandoverRecoveredTemporaryReview.class);
        var prepared=RecoveryReleaseReconciliation.prepare(review.inspect(input.command(),binding),input.request());
        var command=HandoverCommand.Input.parse(input.command()); var projection=app.getBean(HandoverRecoveredTemporaryProjection.class);
        var warehouse=com.acrylic.outbound.common.Ids.bytes(command.uuid("warehouseId"));
        for(String sql:List.of("UPDATE device SET name=CONCAT(name,'-tail') WHERE warehouse_id<>?",
                "UPDATE warehouse SET name=CONCAT(name,'-tail') WHERE id<>?")) {
            var before=input.snapshots().capture();
            assertThatThrownBy(()->binding.consumer().consumeAfterIdentityLocks(binding.request(),binding.nativeFacts(),
                new RecoveryReceivedTemporaryArchiveConsumer.IdentityConsumer<RecoveryReleaseReconciliation>() {
                    @Override public void lockIdentity() { }
                    @Override public void requireOrderVersion(JsonNode old,JsonNode current) { projection.require(command,old,current); }
                    @Override public RecoveryReleaseReconciliation apply(RecoveryRecoveredTemporaryArchive archive,RecoveryTemporaryNativeHandoverFacts.Facts facts) {
                        var receipt=SubmissionJson.parse(input.receipt()); ((com.fasterxml.jackson.databind.node.ObjectNode)receipt).remove("signedReceipt");
                        var inventory=new RecoveryReleaseInventoryCapture(jdbc.getDataSource()).capture(archive,receipt);
                        var proof=RecoveryReleaseReconciliation.inspect(jdbc.getDataSource(),inventory,prepared);
                        assertThat(jdbc.update(sql,warehouse)).isPositive(); return proof;
                    }
                })).hasMessage("RECOVERY_SCOPE_CHANGED");
            assertThat(input.snapshots().capture()).isEqualTo(before);
        }
    }
    record Input(byte[] command,byte[] receipt,RecoveryReleaseReconciliation.Request request,
            com.acrylic.outbound.e2averify.E2aMigrationSnapshot snapshots) { }
    private HandoverRecoveredReconciliationTailChecks() { }
}
