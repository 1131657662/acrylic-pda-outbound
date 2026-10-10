package com.acrylic.outbound.handover;

import com.acrylic.outbound.recovery.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.*;

/** A later write in the owning transaction invalidates the captured inventory and rolls back. */
final class HandoverRecoveredReviewTailChecks {
    static void verify(WebApplicationContext app,HandoverRecoveredTemporaryFixture f,JsonNode fault) throws Exception {
        var before=f.snapshots.capture();
        var command=HandoverCommand.Input.parse(HandoverReceiptCodec.bytes(fault));
        var projection=app.getBean(HandoverRecoveredTemporaryProjection.class);
        var receipt=com.acrylic.outbound.submissions.SubmissionJson.parse(
            java.nio.file.Files.readAllBytes(f.directory.resolve("fault-receipt.json")));
        for(Runnable mutation:mutations(f)) {
        assertThatThrownBy(()->f.binding.consumer().consumeAfterIdentityLocks(f.binding.request(),f.binding.nativeFacts(),
            new RecoveryReceivedTemporaryArchiveConsumer.IdentityConsumer<RecoveryReleaseInventory>() {
                @Override public void lockIdentity() { }
                @Override public void requireOrderVersion(JsonNode original,JsonNode current) { projection.require(command,original,current); }
                @Override public RecoveryReleaseInventory apply(RecoveryRecoveredTemporaryArchive archive,
                        RecoveryTemporaryNativeHandoverFacts.Facts facts) {
                    var inventory=new RecoveryReleaseInventoryCapture(f.jdbc.getDataSource()).capture(archive,receipt);
                    mutation.run();
                    return inventory;
                }
            })).hasMessage("RECOVERY_RELEASE_INVENTORY_CHANGED");
        assertThat(f.snapshots.capture()).isEqualTo(before);
        }
    }

    private static java.util.List<Runnable> mutations(HandoverRecoveredTemporaryFixture f) {
        var warehouse=com.acrylic.outbound.common.Ids.bytes(f.binding.request().evidence().expected().warehouseId());
        return java.util.List.of(()->f.jdbc.update("UPDATE order_number_counter SET last_sequence=last_sequence+1"),
            ()->f.jdbc.update("UPDATE outbound_order SET order_no=CONCAT(order_no,'-tampered') WHERE warehouse_id<>?",warehouse),
            ()->f.jdbc.update("UPDATE inventory_import_row SET error_detail='late-tail' WHERE import_id IN "
                +"(SELECT id FROM inventory_import WHERE warehouse_id=?)",warehouse));
    }
}
