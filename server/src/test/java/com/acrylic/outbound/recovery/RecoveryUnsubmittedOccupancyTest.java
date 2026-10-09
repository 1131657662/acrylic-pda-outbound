package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class RecoveryUnsubmittedOccupancyTest {
    @Test void bindsUnsubmittedTerminalToOriginalSignedE2aAndReservedSet() throws Exception {
        var f = new RecoveryUnsubmittedOccupancyFixture(); var proof = f.read();
        assertThat(proof.occupancy().sourceEvidenceSetId()).isEqualTo(f.setId);
        assertThat(proof.occupancy().rows()).hasSize(1);
        assertThat(proof.occupancy().payloadSha256()).isEqualTo(SubmissionJson.sha(f.evidence.frozen));
        assertThat(proof.ordinaryWritesAllowed()).isFalse(); assertThat(proof.localDeletionAllowed()).isFalse();
    }

    @Test void oldEntryStillRejectsAbsentTerminalHash() throws Exception {
        var f = new RecoveryUnsubmittedOccupancyFixture();
        assertThatThrownBy(() -> new RecoverySourceOccupancy(f.initial.keys()).verify(f.initial.source()))
            .hasMessage("RECOVERY_SOURCE_OCCUPANCY_TERMINAL_PAYLOAD");
    }

    @Test void distinguishesRawManifestDigestFromDomainSeparatedE2aDigest() throws Exception {
        var f = new RecoveryUnsubmittedOccupancyFixture(); var stop = SubmissionJson.parse(f.evidence.request().bundle().stopBytes());
        String raw = RecoveryControlFormat.sha256(SubmissionJson.canonical(stop.path("manifestCore")).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(f.observation.at("/evidenceSet/manifestBytesSha256").asText()).isEqualTo(raw).isNotEqualTo(stop.path("manifestBytesHash").asText());
        f.read();
    }

    @Test void refusesAValidDifferentE2a() throws Exception {
        var f = new RecoveryUnsubmittedOccupancyFixture(); var other = new RecoveryOriginalEvidenceFixture();
        assertThatThrownBy(() -> f.reader().read(f.request(), f.initial.source(), other.request()))
            .hasMessage("RECOVERY_UNSUBMITTED_E2A_SOURCE");
    }

    @Test void refusesResignedChangedFrozenPayload() throws Exception {
        var f = new RecoveryUnsubmittedOccupancyFixture(); f.evidence.frozen.put("expectedWeightGrams", "999");
        f.evidence.replace("STOP", f.evidence.frozen); f.evidence.rebuild();
        assertThatThrownBy(() -> f.read()).isInstanceOf(RecoveryControlException.class);
    }

    @Test void refusesWrongTrustedHistoricalDeviceKey() throws Exception {
        var f = new RecoveryUnsubmittedOccupancyFixture(); var other = new RecoveryOriginalEvidenceFixture();
        var request = f.evidence.request(other.key.getPublic(), f.evidence.identity);
        assertThatThrownBy(() -> f.reader().read(f.request(), f.initial.source(), request)).isInstanceOf(RecoveryControlException.class);
    }

    @ParameterizedTest @ValueSource(strings={"setSha256", "manifestBytesSha256", "evidenceItemsSha256", "stopProofSha256",
        "captureHighWatermark", "auditThroughSeq", "orderScanHighWatermark", "itemCount", "id"})
    void refusesRepinnedDifferentEvidenceSet(String field) throws Exception {
        var f = new RecoveryUnsubmittedOccupancyFixture();
        ((ObjectNode)f.observation.path("evidenceSet")).put(field, field.equals("id") ? UUID.randomUUID().toString() : "0"); f.publish();
        assertThatThrownBy(() -> f.read()).isInstanceOf(RecoveryControlException.class);
    }

    @ParameterizedTest @ValueSource(strings={"ownerSubmissionId", "ownerOrderId", "sourceEvidenceSetId", "continuationId", "recoveryPackageId",
        "dispositionId", "dispositionEvidenceId", "pcWorkId", "pcDispositionId", "temporaryContinuationId", "temporaryHandoverActivationId"})
    void refusesCompetingOwnerEvenWhenBothMaterialPinsAreUpdated(String field) throws Exception {
        var f = new RecoveryUnsubmittedOccupancyFixture(); f.initial.state.row().put(field, UUID.randomUUID().toString());
        f.initial.state.object("occupancy", f.initial.state.occupancy);
        f.observation.set("occupancy", f.initial.state.occupancy.deepCopy()); f.publish();
        assertThatThrownBy(() -> f.read()).isInstanceOf(RecoveryControlException.class);
    }

    @Test void refusesReceivedPayloadTerminalOnUnsubmittedEntry() throws Exception {
        var f = new RecoveryUnsubmittedOccupancyFixture(); f.initial.publish();
        f.observation.put("terminalReceipt", new String(f.initial.source().original("result"), java.nio.charset.StandardCharsets.UTF_8)); f.publish();
        assertThatThrownBy(() -> f.read()).hasMessage("RECOVERY_UNSUBMITTED_TERMINAL");
    }

    @Test void refusesPinMismatch() throws Exception {
        var f = new RecoveryUnsubmittedOccupancyFixture(); var request = f.request();
        f.observation.put("observedAt", "2026-09-20T01:00:00Z");
        Files.write(request.materialRoot().resolve(f.pin + ".json"), RecoveryControlJson.encode(f.observation));
        assertThatThrownBy(() -> f.reader().read(request, f.initial.source(), f.evidence.request())).hasMessage("RECOVERY_UNSUBMITTED_PIN_MISMATCH");
    }

    @Test void refusesChangedControl() throws Exception {
        var f = new RecoveryUnsubmittedOccupancyFixture(); var source = f.initial.source();
        f.initial.state.authority.source.control.append(f.initial.state.authority.source.control.next("VERIFYING"));
        assertThatThrownBy(() -> f.reader().read(f.request(), source, f.evidence.request())).hasMessage("RECOVERY_UNSUBMITTED_CONTROL");
    }
}
