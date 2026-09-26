package com.acrylic.outbound.temporary.protocol;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class LocalDraftStopSnapshotProtocolTest {
    @ParameterizedTest @ValueSource(strings = {"empty-draft", "pending-manual", "void-and-pending", "committed-active", "other-source", "archive-prefix"})
    void verifiesOriginalAndroidRoomSnapshot(String name) throws Exception {
        var vector = new LocalDraftSnapshotVector(name); var result = vector.verify();
        assertThat(result.signature().recordCount()).isEqualTo(vector.items.size());
        assertThat(result.signature().rootSha256()).isEqualTo(vector.proof.core().path("rootSha256").textValue());
        if (name.equals("other-source")) {
            assertThat(result.ownCaptureCount()).isZero(); assertThat(result.otherCaptureCount()).isEqualTo(1);
        }
        if (name.equals("archive-prefix")) assertThat(result.archiveCount()).isEqualTo(1);
    }

    @Test void missingOrExtraOriginalFailsSignedCount() throws Exception {
        var v = new LocalDraftSnapshotVector("pending-manual"); v.items.remove(0); rejects(v);
        v = new LocalDraftSnapshotVector("pending-manual"); v.items.add(v.items.get(0)); rejects(v);
    }
    @Test void alteredRawCannotPassOriginalDigest() throws Exception {
        var v = new LocalDraftSnapshotVector("pending-manual"); v.items.get(0).rawBytes()[0] = 'x'; rejects(v);
    }
    @Test void recomputedItemDigestCannotChangeSignedRoot() throws Exception {
        var v = new LocalDraftSnapshotVector("pending-manual"); v.raw(0, raw -> raw.put("error", "changed")); rejects(v);
    }
    @Test void reorderedSetFailsEvenAfterResigningRoot() throws Exception {
        var v = new LocalDraftSnapshotVector("pending-manual"); Collections.swap(v.items, 0, 1); v.resignSet(); rejects(v);
    }
    @Test void duplicateRecordFailsEvenAfterResigningRoot() throws Exception {
        var v = new LocalDraftSnapshotVector("pending-manual"); v.items.add(1, v.items.get(0)); v.resignSet(); rejects(v);
    }
    @Test void gapInCaptureHighWatermarkFailsEvenAfterResigning() throws Exception {
        var v = new LocalDraftSnapshotVector("pending-manual"); v.proof.core().put("captureHighWatermark", 2);
        v.proof.metadata.put("captureHighWatermark", "2"); v.resignSet(); rejects(v);
    }
    @Test void gapInAuditPrefixFailsEvenAfterResigning() throws Exception {
        var v = new LocalDraftSnapshotVector("pending-manual"); v.items.remove(v.index("AUDIT")); v.resignSet(); rejects(v);
    }
    @Test void missingStopEventFailsEvenWithSignedReducedBoundary() throws Exception {
        var v = new LocalDraftSnapshotVector("pending-manual"); v.items.removeLast();
        long through = v.proof.core().path("auditThroughSeq").longValue() - 1;
        v.proof.core().put("auditThroughSeq", through); v.proof.metadata.put("auditThroughSeq", Long.toString(through));
        v.resignSet(); rejects(v);
    }
    @Test void wrongStopEvidenceOrSourceFails() throws Exception {
        var v = new LocalDraftSnapshotVector("pending-manual"); v.raw(v.items.size() - 1, raw -> raw.put("detail", UUID.randomUUID().toString()));
        v.resignSet(); rejects(v);
        v = new LocalDraftSnapshotVector("pending-manual");
        v.raw(v.items.size() - 1, raw -> raw.withObject("/source").put("clientOrderId", UUID.randomUUID().toString()));
        v.resignSet(); rejects(v);
    }
    @Test void wrongOriginDeviceFailsEvenWhenResigned() throws Exception {
        var v = new LocalDraftSnapshotVector("pending-manual"); v.raw(0, raw -> raw.put("originDeviceId", UUID.randomUUID().toString()));
        v.resignSet(); rejects(v);
    }
    @Test void wrongOwnAuthorizationOrSnapshotFails() throws Exception {
        for (String field : List.of("authorizationId", "snapshotId", "operatorId")) {
            var v = new LocalDraftSnapshotVector("pending-manual"); v.raw(0, raw -> raw.put(field, UUID.randomUUID().toString()));
            v.resignSet(); rejects(v);
        }
    }
    @Test void ownCaptureCannotBeHiddenAsAnotherSource() throws Exception {
        var v = new LocalDraftSnapshotVector("pending-manual"); v.raw(0, raw -> raw.put("clientOrderId", UUID.randomUUID().toString()));
        v.resignSet(); rejects(v);
    }
    @Test void otherSourceAuditCannotReuseAnUnrelatedCapture() throws Exception {
        var v = new LocalDraftSnapshotVector("other-source");
        for (int i = 0; i < v.items.size(); i++) {
            var item = v.items.get(i);
            if (item.kind().equals("AUDIT") && item.recordStatus().equals("RECEIVED")) {
                v.raw(i, raw -> raw.withObject("/source").put("clientOrderId", UUID.randomUUID().toString()));
                break;
            }
        }
        v.resignSet(); rejects(v);
    }
    @Test void committedCaptureRequiresMatchingScan() throws Exception {
        var v = new LocalDraftSnapshotVector("committed-active"); v.items.remove(v.index("SCAN")); v.resignSet(); rejects(v);
    }
    @Test void pendingCaptureCannotAcquireScan() throws Exception {
        var v = new LocalDraftSnapshotVector("committed-active"); v.raw(0, raw -> raw.put("state", "RECEIVED"));
        v.descriptor(0, 1, "RECEIVED"); v.resignSet(); rejects(v);
    }
    @Test void scanMustMatchBarcodeAndCycle() throws Exception {
        var v = new LocalDraftSnapshotVector("committed-active"); v.raw(v.index("SCAN"), raw -> raw.put("barcode", "different"));
        v.resignSet(); rejects(v);
        v = new LocalDraftSnapshotVector("committed-active"); v.raw(v.index("SCAN"), raw -> raw.put("expectedDispatchCycle", "99"));
        v.resignSet(); rejects(v);
    }
    @Test void scanRequiresMatchingSavedAuditEvent() throws Exception {
        var v = new LocalDraftSnapshotVector("committed-active"); int scan = v.index("SCAN");
        v.raw(scan, raw -> raw.put("localSeq", "1")); v.descriptor(scan, 1, "ACTIVE"); v.resignSet(); rejects(v);
    }
    @Test void archiveCannotClaimOtherWarehouse() throws Exception {
        var v = new LocalDraftSnapshotVector("archive-prefix");
        v.raw(v.index("ARCHIVE"), raw -> {
            var proof = (com.fasterxml.jackson.databind.node.ObjectNode) com.acrylic.outbound.submissions.SubmissionJson.parse(raw.path("originalProof").asText());
            proof.put("warehouseId", UUID.randomUUID().toString());
            raw.put("originalProof", com.acrylic.outbound.submissions.SubmissionJson.canonical(proof));
        });
        v.resignSet(); rejects(v);
    }
    @Test void earlierFormalCaptureRemainsOutsideOwnTemporaryCount() throws Exception {
        var v = new LocalDraftSnapshotVector("other-source");
        String assignment = UUID.randomUUID().toString();
        for (int i = 0; i < v.items.size(); i++) {
            var item = v.items.get(i);
            if (item.kind().equals("CAPTURE")) v.raw(i, raw -> { raw.remove("clientOrderId"); raw.put("assignmentId", assignment); });
            if (item.kind().equals("AUDIT")) v.raw(i, raw -> {
                if (!raw.path("source").path("clientOrderId").textValue().equals(v.expected.clientOrderId().toString())) {
                    raw.remove("source"); raw.put("assignmentId", assignment);
                }
            });
        }
        v.resignSet(); var result = v.verify();
        assertThat(result.ownCaptureCount()).isZero(); assertThat(result.otherCaptureCount()).isEqualTo(1);
    }

    @Test void mixedFormalTemporarySourceCannotPassStructuralVerification() throws Exception {
        var v = new LocalDraftSnapshotVector("pending-manual");
        v.raw(0, raw -> raw.put("assignmentId", UUID.randomUUID().toString())); v.resignSet(); rejects(v);
    }
    @Test void noncanonicalRawBytesFailEvenWithFreshDigestAndRoot() throws Exception {
        var v = new LocalDraftSnapshotVector("pending-manual"); var old = v.items.get(0);
        byte[] bytes = (" " + new String(old.rawBytes(), java.nio.charset.StandardCharsets.UTF_8)).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        v.items.set(0, new LocalDraftStopSnapshotProtocol.Item(old.ordinal(), old.kind(), old.recordId(), old.deviceSeq(), old.recordStatus(),
            com.acrylic.outbound.inventory.ImportFiles.sha256(bytes), bytes));
        v.resignSet(); rejects(v);
    }
    private static void rejects(LocalDraftSnapshotVector vector) { assertThatThrownBy(vector::verify).isInstanceOf(IllegalArgumentException.class); }
}
