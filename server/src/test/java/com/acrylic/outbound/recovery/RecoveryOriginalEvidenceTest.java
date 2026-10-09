package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class RecoveryOriginalEvidenceTest {
    @Test void nativeNonemptyEvidenceRetainsSeparateSaveAndSourceAndStopSequences() throws Exception {
        var f = new RecoveryOriginalEvidenceFixture();

        var proof = new RecoveryOriginalEvidence().read(f.request());

        assertThat(proof.identity()).isEqualTo(f.identity);
        assertThat(proof.pageCount()).isEqualTo(3); assertThat(proof.itemCount()).isEqualTo(9);
        assertThat(proof.payloadSha256()).isEqualTo(SubmissionJson.sha(f.frozen));
        assertThat(proof.stopEventId()).isEqualTo(f.stopEvent.path("id").asText());
        assertThat(proof.ordinaryWritesAllowed()).isFalse(); assertThat(proof.localDeletionAllowed()).isFalse();
    }

    @Test void callerCannotRewriteBundleAfterConstruction() throws Exception {
        var f = new RecoveryOriginalEvidenceFixture(); var request = f.request();
        request.bundle().openBytes()[0] ^= 1; request.bundle().pageBytes().getFirst()[0] ^= 1;
        request.bundle().originalObjects().values().iterator().next()[0] ^= 1;
        f.originals.values().iterator().next()[0] ^= 1;

        assertThat(new RecoveryOriginalEvidence().read(request).itemCount()).isEqualTo(9);
    }

    @Test void anotherDevicePublicKeyCannotValidateTheOriginalStop() throws Exception {
        var f = new RecoveryOriginalEvidenceFixture(); var other = new RecoveryOriginalEvidenceFixture();

        assertThatThrownBy(() -> new RecoveryOriginalEvidence().read(f.request(other.key.getPublic(), f.identity)))
            .isInstanceOf(RecoveryControlException.class).hasMessage("RECOVERY_E2A_INVALID");
    }

    @ParameterizedTest @ValueSource(strings = {"CAPTURE", "SCAN", "AUDIT", "STOP"})
    void missingObjectCannotBecomeCompleteEvidence(String kind) throws Exception {
        var f = new RecoveryOriginalEvidenceFixture();
        f.originals.remove(f.entries.stream().filter(item -> item.path("itemKind").asText().equals(kind)).findFirst().orElseThrow().path("itemBytesSha256").asText());

        assertThatThrownBy(() -> new RecoveryOriginalEvidence().read(f.request())).hasMessage("RECOVERY_E2A_OBJECT_MISMATCH");
    }

    @Test void changedRawObjectIsRejectedAgainstTheSignedPageRoot() throws Exception {
        var f = new RecoveryOriginalEvidenceFixture(); f.originals.values().iterator().next()[0] ^= 1;

        assertThatThrownBy(() -> new RecoveryOriginalEvidence().read(f.request())).hasMessage("RECOVERY_E2A_OBJECT_MISMATCH");
    }

    @Test void duplicateOriginalObjectAcrossPagesIsRejected() throws Exception {
        var f = new RecoveryOriginalEvidenceFixture();
        f.entries.get(3).put("originalObjectId", f.entries.getFirst().path("originalObjectId").asText()); f.rebuild();

        assertThatThrownBy(() -> new RecoveryOriginalEvidence().read(f.request())).hasMessage("RECOVERY_E2A_ITEM_DUPLICATE");
    }

    @ParameterizedTest @ValueSource(strings = {"entryCount", "byteCount"})
    void signedManifestTotalsMustMatchActualPages(String field) throws Exception {
        var f = new RecoveryOriginalEvidenceFixture(); f.manifest.put(field, f.manifest.path(field).asLong() + 1); f.refreshStop();

        assertThatThrownBy(() -> new RecoveryOriginalEvidence().read(f.request())).hasMessage("RECOVERY_E2A_MANIFEST_TOTALS");
    }

    @Test void changedStopWatermarkIsNotInferredFromTheBundle() throws Exception {
        var f = new RecoveryOriginalEvidenceFixture(); f.stopCore.put("captureHighWatermark", 2); f.signStop();

        assertThatThrownBy(() -> new RecoveryOriginalEvidence().read(f.request())).hasMessage("RECOVERY_E2A_STOP_WATERMARK");
    }

    @Test void sourceIdentityUsesAuthorizationRatherThanSubmissionId() throws Exception {
        var f = new RecoveryOriginalEvidenceFixture(); f.open.put("sourceId", f.identity.submissionId().toString());

        assertThatThrownBy(() -> new RecoveryOriginalEvidence().read(f.request())).hasMessage("RECOVERY_E2A_SOURCE_ID");
    }

    @Test void wrongSavedReferenceIsRejectedEvenWithNewDevelopmentSignature() throws Exception {
        var f = new RecoveryOriginalEvidenceFixture(); var scan = (ObjectNode) f.frozen.path("scans").get(0); scan.put("localSeq", "3");
        f.replace("SCAN", scan); f.replace("STOP", f.frozen); f.rebuild();

        assertThatThrownBy(() -> new RecoveryOriginalEvidence().read(f.request())).hasMessage("RECOVERY_INITIAL_SAVE_EVENT");
    }

    @Test void failedInputKeepsItsSequenceWhileTheSavedScanRemainsSparse() throws Exception {
        var f = new RecoveryOriginalEvidenceFixture(RecoveryOriginalEvidenceFixture::failedCaptureBeforeSave);

        var proof = new RecoveryOriginalEvidence().read(f.request());

        assertThat(proof.identity().orderScanHighWatermark()).isEqualTo(2);
        assertThat(proof.itemCount()).isEqualTo(13);
    }

    @Test void frozenAuditMustRetainOriginalOperator() throws Exception {
        var f = new RecoveryOriginalEvidenceFixture();
        var event = (ObjectNode)f.frozen.path("events").get(4);
        event.put("operatorId", UUID.randomUUID().toString());
        f.replace("AUDIT", event); f.replace("STOP", f.frozen); f.rebuild();

        assertThatThrownBy(() -> new RecoveryOriginalEvidence().read(f.request())).hasMessage("RECOVERY_E2A_AUDIT_SOURCE");
    }

    @Test void frozenEventCannotReferToOneCapture() throws Exception {
        var f = new RecoveryOriginalEvidenceFixture();
        var event = (ObjectNode)f.frozen.path("events").get(4);
        event.put("captureId", f.frozen.path("captures").get(0).path("id").asText());
        f.replace("AUDIT", event); f.replace("STOP", f.frozen); f.rebuild();

        assertThatThrownBy(() -> new RecoveryOriginalEvidence().read(f.request())).hasMessage("RECOVERY_E2A_FREEZE_EVENT");
    }

    @ParameterizedTest @ValueSource(strings = {"auditEvents", "auditCoverage", "auditArchives", "temporaryContinuation", "temporaryHandover"})
    void unsupportedPrefixFieldsCannotBeIgnored(String field) throws Exception {
        var f = new RecoveryOriginalEvidenceFixture(); f.frozen.putArray(field);
        f.replace("STOP", f.frozen); f.rebuild();

        assertThatThrownBy(() -> new RecoveryOriginalEvidence().read(f.request())).hasMessage("RECOVERY_E2A_PREFIX_UNSUPPORTED");
    }

    @Test void archiveItemIsRefusedUntilAnArchivePrefixVerifierExists() throws Exception {
        var f = new RecoveryOriginalEvidenceFixture();
        f.entries.getFirst().put("itemKind", "ARCHIVE").put("objectKind", "AUDIT_ARCHIVE"); f.rebuild();

        assertThatThrownBy(() -> new RecoveryOriginalEvidence().read(f.request())).hasMessage("RECOVERY_E2A_ARCHIVE_UNSUPPORTED");
    }

    @Test void scanCannotChangeItsOriginalInputSequence() throws Exception {
        var f = new RecoveryOriginalEvidenceFixture(); var scan = (ObjectNode)f.frozen.path("scans").get(0);
        scan.put("orderScanSeq", "2"); f.replace("SCAN", scan); f.replace("STOP", f.frozen); f.rebuild();

        assertThatThrownBy(() -> new RecoveryOriginalEvidence().read(f.request())).hasMessage("RECOVERY_E2A_SCAN_CAPTURE");
    }

    @Test void pageChainCannotBeReplacedEvenWithNewSignedManifest() throws Exception {
        var f = new RecoveryOriginalEvidenceFixture(); f.pageCores.get(1).put("prevPageHash", "0".repeat(64));
        ((ObjectNode)f.manifest.path("pages").get(1)).put("pageHash", com.acrylic.outbound.temporary.protocol.E2aProtocol.pageHash(f.pageCores.get(1)));
        f.refreshStop();

        assertThatThrownBy(() -> new RecoveryOriginalEvidence().read(f.request())).hasMessage("RECOVERY_E2A_PAGE_CHAIN");
    }

    @Test void unreferencedOriginalCannotBeSilentlyDropped() throws Exception {
        var f = new RecoveryOriginalEvidenceFixture(); byte[] extra = RecoveryOriginalEvidenceFixture.bytes(SubmissionJson.object());
        f.originals.put(RecoveryControlFormat.sha256(extra), extra);

        assertThatThrownBy(() -> new RecoveryOriginalEvidence().read(f.request())).hasMessage("RECOVERY_E2A_OBJECT_SET");
    }

    @Test void futureStopTransactionCannotBeAssumedCompatible() throws Exception {
        var f = new RecoveryOriginalEvidenceFixture(); f.stopCore.put("stopTransactionVersion", 2); f.signStop();

        assertThatThrownBy(() -> new RecoveryOriginalEvidence().read(f.request())).hasMessage("RECOVERY_E2A_STOP_VERSION");
    }

    @Test void signedStopCannotNameAnotherAuditEvent() throws Exception {
        var f = new RecoveryOriginalEvidenceFixture(); f.stopCore.put("stopEventId", UUID.randomUUID().toString()); f.signStop();

        assertThatThrownBy(() -> new RecoveryOriginalEvidence().read(f.request())).hasMessage("RECOVERY_E2A_STOP_EVENT");
    }

    @Test void frozenPayloadCannotOmitASignedCapture() throws Exception {
        var f = new RecoveryOriginalEvidenceFixture(); ((com.fasterxml.jackson.databind.node.ArrayNode)f.frozen.path("captures")).removeAll();
        f.replace("STOP", f.frozen); f.rebuild();

        assertThatThrownBy(() -> new RecoveryOriginalEvidence().read(f.request())).hasMessage("RECOVERY_E2A_FROZEN_RECORDS");
    }

    @Test void deviceSequenceMustCoverFromOneInsteadOfTrustingTheLastWatermark() throws Exception {
        var f = new RecoveryOriginalEvidenceFixture(); var capture = (ObjectNode)f.frozen.path("captures").get(0);
        capture.put("deviceCaptureSeq", "2"); f.replace("CAPTURE", capture); f.entries.getFirst().put("originalDeviceSeq", 2);
        f.replace("STOP", f.frozen); f.rebuild();

        assertThatThrownBy(() -> new RecoveryOriginalEvidence().read(f.request())).hasMessage("RECOVERY_E2A_ITEM_SEQUENCE");
    }
}
