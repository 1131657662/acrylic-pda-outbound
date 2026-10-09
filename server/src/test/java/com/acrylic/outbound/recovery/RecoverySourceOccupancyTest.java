package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class RecoverySourceOccupancyTest {
    @ParameterizedTest @ValueSource(strings = {"FORMAL", "TEMP"})
    void provesOnlyOriginalRetainedReviewOwner(String kind) throws Exception {
        var f = new RecoverySourceStateFixture(kind); var source = f.read();

        var proof = f.occupancyReader().verify(source);

        assertThat(proof.rows()).hasSize(1);
        assertThat(proof.rows().getFirst().ownerSubmissionId()).isEqualTo(source.identity().submissionId());
        assertThat(source.ordinaryWritesAllowed()).isFalse(); assertThat(source.localDeletionAllowed()).isFalse();
    }

    @Test void provesTemporaryLocalSourceReservationWithoutInventingRelease() throws Exception {
        var f = new RecoverySourceStateFixture("TEMP"); f.local = true; f.sourceReserved(); f.publish();

        assertThat(f.occupancyReader().verify(f.read()).rows().getFirst().sourceEvidenceSetId()).isNotNull();
    }

    @ParameterizedTest @ValueSource(strings = {"ownerSubmissionId", "ownerOrderId"})
    void refusesForeignFormalOwner(String field) throws Exception {
        var f = new RecoverySourceStateFixture("FORMAL"); f.row().put(field, UUID.randomUUID().toString()); f.publish();

        assertThatThrownBy(() -> f.occupancyReader().verify(f.read())).hasMessage("RECOVERY_SOURCE_OCCUPANCY_OWNER");
    }

    @ParameterizedTest @ValueSource(strings = {"ownerScopeId", "ownerWarehouseId", "sourceEvidenceSetId", "ownerOrderId"})
    void refusesForeignTemporaryOwner(String field) throws Exception {
        var f = new RecoverySourceStateFixture("TEMP"); f.sourceReserved(); f.row().put(field, UUID.randomUUID().toString()); f.publish();

        assertThatThrownBy(() -> f.occupancyReader().verify(f.read())).hasMessage("RECOVERY_SOURCE_OCCUPANCY_OWNER");
    }

    @ParameterizedTest @ValueSource(strings = {"continuationId", "recoveryPackageId", "dispositionId", "dispositionEvidenceId",
        "pcWorkId", "pcDispositionId", "temporaryContinuationId", "temporaryHandoverActivationId"})
    void refusesCompetingOwnershipChains(String field) throws Exception {
        var f = new RecoverySourceStateFixture("TEMP"); f.row().put(field, UUID.randomUUID().toString()); f.publish();

        assertThatThrownBy(() -> f.occupancyReader().verify(f.read())).hasMessage("RECOVERY_SOURCE_OCCUPANCY_CHAIN_UNSUPPORTED");
    }

    @ParameterizedTest @ValueSource(strings = {"FREE", "COMMITTED", "CONTINUATION_RESERVED", "RECOVERY_RESERVED"})
    void refusesUnsupportedOwnerState(String state) throws Exception {
        var f = new RecoverySourceStateFixture("FORMAL"); f.row().put("ownerState", state); f.publish();

        assertThatThrownBy(() -> f.occupancyReader().verify(f.read())).hasMessage("RECOVERY_SOURCE_OCCUPANCY_OWNER_STATE");
    }

    @ParameterizedTest @ValueSource(strings = {"baleId", "captureId", "barcode", "dispatchCycle", "orderScanSeq"})
    void refusesForeignOrChangedActiveRow(String field) throws Exception {
        var f = new RecoverySourceStateFixture("FORMAL");
        f.row().put(field, field.endsWith("Id") ? UUID.randomUUID().toString() : field.equals("barcode") ? "01234567891" : "3"); f.publish();

        assertThatThrownBy(() -> f.occupancyReader().verify(f.read())).hasMessage("RECOVERY_SOURCE_OCCUPANCY_SCAN_MISMATCH");
    }

    @Test void refusesDuplicateRows() throws Exception {
        var f = new RecoverySourceStateFixture("FORMAL"); ((ArrayNode)f.occupancy.path("rows")).add(f.row().deepCopy()); f.publish();

        assertThatThrownBy(() -> f.occupancyReader().verify(f.read())).hasMessage("RECOVERY_SOURCE_OCCUPANCY_DUPLICATE");
    }

    @Test void refusesMissingActiveRow() throws Exception {
        var f = new RecoverySourceStateFixture("FORMAL"); ((ArrayNode)f.occupancy.path("rows")).removeAll(); f.publish();

        assertThatThrownBy(() -> f.occupancyReader().verify(f.read())).hasMessage("RECOVERY_SOURCE_OCCUPANCY_ROW_SET");
    }

    @Test void refusesReorderedRows() throws Exception {
        var f = new RecoverySourceStateFixture("FORMAL"); f.secondScan(); var rows = (ArrayNode)f.occupancy.path("rows");
        var first = rows.remove(0); rows.add(first); f.publish();

        assertThatThrownBy(() -> f.occupancyReader().verify(f.read())).hasMessage("RECOVERY_SOURCE_OCCUPANCY_SCAN_MISMATCH");
    }

    @Test void excludesVoidScansFromReservationProof() throws Exception {
        var f = new RecoverySourceStateFixture("FORMAL"); ((ObjectNode)f.payload.path("scans").get(0)).put("status", "VOID");
        ((ArrayNode)f.occupancy.path("rows")).removeAll(); f.publish();

        assertThat(f.occupancyReader().verify(f.read()).rows()).isEmpty();
    }

    @Test void refusesExtraRowsForVoidScans() throws Exception {
        var f = new RecoverySourceStateFixture("FORMAL"); ((ObjectNode)f.payload.path("scans").get(0)).put("status", "VOID"); f.publish();

        assertThatThrownBy(() -> f.occupancyReader().verify(f.read())).hasMessage("RECOVERY_SOURCE_OCCUPANCY_ROW_SET");
    }

    @ParameterizedTest @ValueSource(strings = {"upstreamPresent", "bookStatus", "blockedReason"})
    void refusesUnavailableOriginalStock(String field) throws Exception {
        var f = new RecoverySourceStateFixture("FORMAL");
        if (field.equals("upstreamPresent")) f.row().put(field, false); else f.row().put(field, "BLOCKED"); f.publish();

        assertThatThrownBy(() -> f.occupancyReader().verify(f.read())).hasMessage("RECOVERY_SOURCE_OCCUPANCY_UNAVAILABLE");
    }

    @ParameterizedTest @ValueSource(strings = {"continuationId", "handoverSessionId", "recoveryPackageId", "handoverId"})
    void refusesUnsupportedPayloadChain(String field) throws Exception {
        var f = new RecoverySourceStateFixture("FORMAL"); f.payload.put(field, UUID.randomUUID().toString()); f.publish();

        assertThatThrownBy(() -> f.occupancyReader().verify(f.read())).hasMessage("RECOVERY_SOURCE_OCCUPANCY_CHAIN_UNSUPPORTED");
    }

    @Test void refusesSuccessAsRetainedReservation() throws Exception {
        var f = new RecoverySourceStateFixture("FORMAL"); f.status = "SUCCEEDED"; f.publish();

        assertThatThrownBy(() -> f.occupancyReader().verify(f.read())).hasMessage("RECOVERY_SOURCE_OCCUPANCY_TERMINAL_UNSUPPORTED");
    }

    @Test void refusesDifferentPinnedPayloadDigest() throws Exception {
        var f = new RecoverySourceStateFixture("FORMAL"); f.occupancy.put("payloadSha256", "a".repeat(64)); f.object("occupancy", f.occupancy);

        assertThatThrownBy(() -> f.occupancyReader().verify(f.read())).hasMessage("RECOVERY_SOURCE_OCCUPANCY_PAYLOAD");
    }

    @Test void refusesDifferentSource() throws Exception {
        var f = new RecoverySourceStateFixture("TEMP"); ((ObjectNode)f.occupancy.path("source")).put("scopeId", UUID.randomUUID().toString());
        f.object("occupancy", f.occupancy);

        assertThatThrownBy(() -> f.occupancyReader().verify(f.read())).hasMessage("RECOVERY_SOURCE_OCCUPANCY_SOURCE");
    }

    @Test void refusesOmittedCompetingOwnerColumn() throws Exception {
        var f = new RecoverySourceStateFixture("FORMAL"); f.row().remove("continuationId"); f.publish();

        assertThatThrownBy(() -> f.occupancyReader().verify(f.read())).hasMessage("CONTROL_FIELDS");
    }

    @Test void refusesHigherRoundWithoutItsOriginalChain() throws Exception {
        var f = new RecoverySourceStateFixture("FORMAL"); var later = RecoverySourceFixture.create();

        assertThatThrownBy(() -> f.occupancyReader().verify(later.read())).hasMessage("RECOVERY_SOURCE_OCCUPANCY_ROUND_UNSUPPORTED");
    }

    @Test void refusesDispositionEvenIfItDoesNotAlterTheActiveRows() throws Exception {
        var f = new RecoverySourceStateFixture("FORMAL"); f.payload.putArray("dispositions").addObject().put("action", "RETAIN"); f.publish();

        assertThatThrownBy(() -> f.occupancyReader().verify(f.read())).hasMessage("RECOVERY_SOURCE_OCCUPANCY_CHAIN_UNSUPPORTED");
    }

    @Test void refusesMissingAbortedPayloadBinding() throws Exception {
        var f = new RecoverySourceStateFixture("FORMAL");
        var receipt = f.authority.key.receipt(f.authority.source, "ABORTED").putNull("payloadSha256").put("payloadPresent", false);
        f.object("result", f.authority.key.signed(receipt));

        assertThatThrownBy(() -> f.occupancyReader().verify(f.read())).hasMessage("RECOVERY_SOURCE_OCCUPANCY_TERMINAL_PAYLOAD");
    }
}
