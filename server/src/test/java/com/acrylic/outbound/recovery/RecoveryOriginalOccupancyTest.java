package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class RecoveryOriginalOccupancyTest {
    @ParameterizedTest @ValueSource(strings={"FORMAL", "TEMP"})
    void consumesPinnedObservationAgainstOriginalSignedTerminal(String kind) throws Exception {
        var f = new RecoveryOriginalOccupancyFixture(kind);
        var proof = f.reader().read(f.request(), f.state.read());
        assertThat(proof.occupancy().rows()).hasSize(1);
        assertThat(proof.occupancy().identity()).isEqualTo(f.state.authority.source.identity);
        assertThat(proof.ordinaryWritesAllowed()).isFalse(); assertThat(proof.localDeletionAllowed()).isFalse();
    }

    @ParameterizedTest @ValueSource(strings={"mysqlServerUuid", "databaseSchema", "sourceRecoveryEpoch", "observedAt", "payloadSha256", "terminalReceipt", "observationKind"})
    void rejectsRepinnedWrongOriginalContext(String field) throws Exception {
        var f = new RecoveryOriginalOccupancyFixture("TEMP");
        String wrong = switch (field) {
            case "mysqlServerUuid" -> UUID.randomUUID().toString(); case "observedAt" -> "2026-09-20T02:00:00.001Z";
            case "sourceRecoveryEpoch" -> "2"; case "payloadSha256" -> "a".repeat(64); default -> "wrong original";
        };
        f.observation.put(field, wrong); f.publish();
        assertThatThrownBy(() -> f.reader().read(f.request(), f.state.read())).isInstanceOf(RecoveryControlException.class);
    }

    @ParameterizedTest @ValueSource(strings={"ownerSubmissionId", "ownerOrderId", "sourceEvidenceSetId", "continuationId", "recoveryPackageId",
        "dispositionId", "dispositionEvidenceId", "pcWorkId", "pcDispositionId", "temporaryContinuationId", "temporaryHandoverActivationId"})
    void rejectsEveryCompetingOwnerColumn(String field) throws Exception {
        var f = new RecoveryOriginalOccupancyFixture("FORMAL");
        ((ObjectNode)f.observation.at("/occupancy/rows/0")).put(field, UUID.randomUUID().toString()); f.publish();
        assertThatThrownBy(() -> f.reader().read(f.request(), f.state.read())).isInstanceOf(RecoveryControlException.class);
    }

    @ParameterizedTest @ValueSource(strings={"scopeId", "warehouseId", "deviceId", "operatorId", "qualificationId", "canonicalOrderId", "creatorDeviceId", "clientOrderId",
        "workRound", "sourceRecoveryEpoch", "currentSubmissionId", "scopeRound", "continuationId", "supersededBy", "status", "payload_sha256"})
    void refusesWrongDatabaseSubmissionFacts(String field) throws Exception {
        var f = new RecoveryOriginalOccupancyFixture("TEMP"); var head = f.head();
        head.put(field, field.endsWith("Id") || field.equals("supersededBy") ? UUID.randomUUID().toString() : "wrong");
        assertThatThrownBy(() -> RecoveryOriginalOccupancyFacts.head(head, f.state.authority.source.identity, SubmissionJson.sha(f.state.payload)))
            .isInstanceOf(RecoveryControlException.class);
    }

    @Test void refusesModifiedBytesWithOriginalIndependentPin() throws Exception {
        var f = new RecoveryOriginalOccupancyFixture("TEMP");
        var file = f.request().materialRoot().resolve(f.pin + ".json");
        f.observation.put("observedAt", "2026-09-20T01:59:00Z"); Files.write(file, RecoveryControlJson.encode(f.observation));
        assertThatThrownBy(() -> f.reader().read(f.request(), f.state.read())).hasMessage("RECOVERY_ORIGINAL_OCCUPANCY_PIN_MISMATCH");
    }

    @Test void refusesChangedControlEvenWithIntactObservation() throws Exception {
        var f = new RecoveryOriginalOccupancyFixture("TEMP"); var source = f.state.read();
        f.state.authority.source.control.append(f.state.authority.source.control.next("VERIFYING"));
        assertThatThrownBy(() -> f.reader().read(f.request(), source)).hasMessage("RECOVERY_ORIGINAL_OCCUPANCY_CONTROL");
    }

    @Test void refusesAuthorityDirectoryAsOriginalCustody() throws Exception {
        var f = new RecoveryOriginalOccupancyFixture("TEMP"); var request = f.request();
        var inside = new RecoveryOriginalOccupancy.Request(f.state.authority.source.control.root, f.pin, f.database, request.notAfter());
        assertThatThrownBy(() -> f.reader().read(inside, f.state.read())).hasMessage("RECOVERY_SOURCE_INSIDE_AUTHORITY");
    }

    @ParameterizedTest @ValueSource(strings={"barcode", "dispatchCycle", "orderScanSeq", "captureId", "upstreamPresent", "bookStatus", "blockedReason", "ownerScopeId", "ownerWarehouseId"})
    void rejectsRepinnedUnavailableOrDifferentStock(String field) throws Exception {
        var f = new RecoveryOriginalOccupancyFixture("TEMP"); var row = (ObjectNode)f.observation.at("/occupancy/rows/0");
        if (field.equals("upstreamPresent")) row.put(field, false);
        else row.put(field, field.endsWith("Id") ? UUID.randomUUID().toString() : "changed");
        f.publish();
        assertThatThrownBy(() -> f.reader().read(f.request(), f.state.read())).isInstanceOf(RecoveryControlException.class);
    }

    @Test void refusesObservationSourceWatermarkSubstitution() throws Exception {
        var f = new RecoveryOriginalOccupancyFixture("TEMP");
        ((ObjectNode)f.observation.path("source")).put("auditThroughSeq", "13"); f.publish();
        assertThatThrownBy(() -> f.reader().read(f.request(), f.state.read())).hasMessage("RECOVERY_ORIGINAL_OCCUPANCY_SOURCE");
    }

    @Test void refusesOmittedReservationRow() throws Exception {
        var f = new RecoveryOriginalOccupancyFixture("TEMP");
        ((ObjectNode)f.observation.path("occupancy")).putArray("rows"); f.publish();
        assertThatThrownBy(() -> f.reader().read(f.request(), f.state.read())).hasMessage("RECOVERY_SOURCE_OCCUPANCY_ROW_SET");
    }
}
