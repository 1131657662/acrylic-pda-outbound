package com.acrylic.outbound.relay;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import static com.acrylic.outbound.submissions.SubmissionJson.*;
import static org.assertj.core.api.Assertions.*;

class RelayIndependentInventoryTest {
    @Test void unknownBareSkuCannotCertifyAnInventoryBale() {
        var result=RelayIndependentInventory.compare(scan(),null,"warehouse");
        assertThat(result.path("blockers").toString()).contains("BALE_UNKNOWN");assertThat(result.path("server").isNull()).isTrue();
    }
    @Test void exactServerFactsRemainEvidenceWithoutGrantingStock() {
        var result=RelayIndependentInventory.compare(scan(),server(),"warehouse");
        assertThat(result.path("blockers")).isEmpty();assertThat(result.has("stockAvailable")).isFalse();
    }
    @Test void commitmentAndBlockedStockAreNotHiddenByExactSku() {
        var server=server().put("commitmentState","SOURCE_RESERVED").put("blockedReason","QUARANTINED");
        var blockers=RelayIndependentInventory.compare(scan(),server,"warehouse").path("blockers").toString();
        assertThat(blockers).contains("COMMITMENT_EXISTS","INVENTORY_BLOCKED");
    }
    @Test void changedWeightSnapshotAndUpstreamRemainDistinct() {
        var server=server().put("weightGrams","999").put("snapshotId","new").put("upstreamPresent",false);
        var blockers=RelayIndependentInventory.compare(scan(),server,"warehouse").path("blockers").toString();
        assertThat(blockers).contains("SKU_WEIGHTGRAMS_MISMATCH","SNAPSHOT_CHANGED","UPSTREAM_ABSENT");
    }
    @Test void missingPositionIsReportedWithoutTreatingNullAsAString() {
        var server=server().putNull("positionVersion").putNull("bookStatus");
        assertThat(RelayIndependentInventory.compare(scan(),server,"warehouse").path("blockers").toString()).contains("POSITION_MISSING","BOOK_UNAVAILABLE");
    }
    private ObjectNode scan() {
        return object().put("captureId","capture").put("sequence","1").put("barcode","00100000001")
            .put("deliveryCode","001").put("model","M").put("name","N").put("weightGrams","1000");
    }
    private ObjectNode server() {
        return scan().put("warehouseId","warehouse").put("positionVersion","1").put("upstreamPresent",true).put("bookStatus","NOT_DEDUCTED")
            .put("snapshotId","snapshot").put("positionSnapshotId","snapshot").putNull("blockedReason").putNull("commitmentState");
    }
}
