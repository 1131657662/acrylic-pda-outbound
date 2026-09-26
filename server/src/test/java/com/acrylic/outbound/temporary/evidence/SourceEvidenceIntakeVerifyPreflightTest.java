package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.inventory.ImportFiles;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.protocol.E2aProtocol;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SourceEvidenceIntakeVerifyPreflightTest {
    private final UUID warehouse = UUID.randomUUID(), scope = UUID.randomUUID();
    private final UUID submission = UUID.randomUUID(), device = UUID.randomUUID();
    private final UUID operator = UUID.randomUUID(), authorization = UUID.randomUUID();
    private final UUID openKey = UUID.randomUUID(), stopKey = UUID.randomUUID(), objectId = UUID.randomUUID();
    private byte[] request;
    private SourceEvidenceIntakeStop.Parsed stop;
    private SourceEvidenceIntakeStop.Expected expected;
    private final SourceEvidenceIntakeReceipts.BoundKey key = new SourceEvidenceIntakeReceipts.BoundKey(
        operator, stopKey, warehouse, submission, 1, device, authorization);
    private final SourceEvidenceIntakeStopObjects.Binding stopBinding = new SourceEvidenceIntakeStopObjects.Binding(
        warehouse, scope, submission, 1, device, operator, openKey);
    private final SourceEvidenceIntakePageObjects.Binding pageBinding = new SourceEvidenceIntakePageObjects.Binding(
        warehouse, scope, submission, 1, device, operator, openKey);
    private SourceEvidenceIntakeObjectFiles.Stored stored;
    private final SourceEvidenceIntakeStopReceipts receipts = mock(SourceEvidenceIntakeStopReceipts.class);
    private final SourceEvidenceIntakeReceipts opens = mock(SourceEvidenceIntakeReceipts.class);
    private final SourceEvidenceIntakeStopObjects objects = mock(SourceEvidenceIntakeStopObjects.class);
    private final SourceEvidenceIntakeObjectFiles files = mock(SourceEvidenceIntakeObjectFiles.class);
    private final SourceEvidenceIntakePageClosure pages = mock(SourceEvidenceIntakePageClosure.class);
    private final SourceEvidenceIntakeDeviceKeys deviceKeys = mock(SourceEvidenceIntakeDeviceKeys.class);
    private final SourceEvidenceIntakeAuditPrefix audits = mock(SourceEvidenceIntakeAuditPrefix.class);
    private final SourceEvidenceIntakeVerifyPreflight preflight =
        new SourceEvidenceIntakeVerifyPreflight(receipts, opens, objects, files, pages, deviceKeys, audits);

    @BeforeEach void storedStop() throws Exception {
        var generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        var keys = generator.generateKeyPair();
        var open = SubmissionJson.object().put("operation", "OPEN").put("manifestId", "manifest")
            .put("manifestVersion", 1).put("snapshotId", "snapshot").put("source", "TEMP")
            .put("sourceId", authorization.toString()).put("submissionId", submission.toString())
            .put("workRound", 1);
        expected = new SourceEvidenceIntakeStop.Expected(openKey,
            SourceEvidenceIntakeOpen.parse(bytes(open), submission, 1), warehouse, operator, device, 2, keys.getPublic());
        when(opens.openBound(new SourceEvidenceIntakeReceipts.BoundKey(operator, openKey, warehouse,
            submission, 1, device, authorization))).thenReturn(expected.open());
        when(deviceKeys.trusted(warehouse, device)).thenReturn(keys.getPublic());
        request = signedRequest(keys);
        stop = SourceEvidenceIntakeStop.parse(request, expected);
        var proof = stop.canonicalStopProofBytes();
        stored = new SourceEvidenceIntakeObjectFiles.Stored(
            "source-evidence-intake/objects/" + ImportFiles.sha256(proof) + ".bin",
            ImportFiles.sha256(proof), proof.length);
        when(receipts.objectIdForLockedReplay(key, request)).thenReturn(objectId);
        when(objects.findAfterSourceLock(stopBinding, stop.stopEventId())).thenReturn(
            new SourceEvidenceIntakeStopObjects.StopObject(objectId,
                SourceEvidenceIntakeStopObjects.originalId(openKey, stop.stopEventId()), stored));
    }

    @Test void completeStoredRootsDoNotReturnATrustedVerification() throws IOException {
        when(pages.inspect(any(), eq(pageBinding))).thenReturn(
            new SourceEvidenceIntakePageClosure.Assessment(List.of(), 1, 10, true));

        preflight.requireStoredRoots(request, expected, key, stopBinding, pageBinding);
        verify(audits).require(device, warehouse, 2, 1);
        verify(audits).requireStopEvent(device, warehouse, operator, authorization, 2, 1, stop.stopEventId(), submission);

        verify(files).verify(stored);
        verify(pages).inspect(argThat(parsed -> Arrays.equals(parsed.canonicalBytes(), request)), eq(pageBinding));
    }

    @Test void differentReceiptObjectCannotAdvanceToPages() {
        when(receipts.objectIdForLockedReplay(key, request)).thenReturn(UUID.randomUUID());

        assertCode("E2A_STOP_OBJECT_INTEGRITY_FAILURE", () ->
            preflight.requireStoredRoots(request, expected, key, stopBinding, pageBinding));
        verify(pages, never()).inspect(any(), any());
    }

    @Test void missingStopObjectCannotAdvanceToPages() throws IOException {
        when(objects.findAfterSourceLock(stopBinding, stop.stopEventId())).thenReturn(null);

        assertCode("E2A_STOP_OBJECT_INTEGRITY_FAILURE", () ->
            preflight.requireStoredRoots(request, expected, key, stopBinding, pageBinding));
        verify(files, never()).verify(any());
    }

    @Test void wrongStopProofDigestCannotAdvanceToPages() {
        var changed = new SourceEvidenceIntakeObjectFiles.Stored(stored.key(), "f".repeat(64), stored.bytes());
        when(objects.findAfterSourceLock(stopBinding, stop.stopEventId())).thenReturn(
            new SourceEvidenceIntakeStopObjects.StopObject(objectId,
                SourceEvidenceIntakeStopObjects.originalId(openKey, stop.stopEventId()), changed));

        assertCode("E2A_STOP_OBJECT_INTEGRITY_FAILURE", () ->
            preflight.requireStoredRoots(request, expected, key, stopBinding, pageBinding));
        verify(pages, never()).inspect(any(), any());
    }

    @Test void inaccessibleProofCannotAdvanceToPages() throws IOException {
        org.mockito.Mockito.doThrow(new IOException("missing")).when(files).verify(stored);

        assertCode("E2A_STOP_FILE_UNAVAILABLE", () ->
            preflight.requireStoredRoots(request, expected, key, stopBinding, pageBinding));
        verify(pages, never()).inspect(any(), any());
    }

    @Test void missingPageDoesNotAdvance() {
        when(pages.inspect(any(), eq(pageBinding))).thenReturn(
            new SourceEvidenceIntakePageClosure.Assessment(List.of(2L), 0, 0));

        assertCode("E2A_PAGE_INCOMPLETE", () ->
            preflight.requireStoredRoots(request, expected, key, stopBinding, pageBinding));
        verify(audits, never()).require(any(), any(), org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.anyLong());
    }

    @Test void completePageRootsWithoutDeviceAuditCoverageCannotAdvance() {
        when(pages.inspect(any(), eq(pageBinding))).thenReturn(
            new SourceEvidenceIntakePageClosure.Assessment(List.of(), 1, 10, false));

        assertCode("E2A_AUDIT_COVERAGE_INCOMPLETE", () ->
            preflight.requireStoredRoots(request, expected, key, stopBinding, pageBinding));
        verify(audits, never()).require(any(), any(), org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.anyLong());
    }

    @Test void incompleteCaptureWatermarkCannotAdvance() {
        when(pages.inspect(any(), eq(pageBinding))).thenReturn(
            new SourceEvidenceIntakePageClosure.Assessment(List.of(), 1, 10, true, false));

        assertCode("E2A_CAPTURE_WATERMARK_INCOMPLETE", () ->
            preflight.requireStoredRoots(request, expected, key, stopBinding, pageBinding));
        verify(audits, never()).require(any(), any(), org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.anyLong());
    }

    @Test void incompleteDeviceAuditBlocksStoredRoots() {
        when(pages.inspect(any(), eq(pageBinding))).thenReturn(
            new SourceEvidenceIntakePageClosure.Assessment(List.of(), 0, 0, true));
        org.mockito.Mockito.doThrow(new BusinessException(409, "E2A_AUDIT_PREFIX_UNVERIFIABLE", "gap"))
            .when(audits).require(device, warehouse, 2, 1);

        assertCode("E2A_AUDIT_PREFIX_UNVERIFIABLE", () ->
            preflight.requireStoredRoots(request, expected, key, stopBinding, pageBinding));
        verify(audits, never()).requireStopEvent(any(), any(), any(), any(),
            org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(), any(), any());
    }

    @Test void mismatchedContextCannotReadAnyEvidence() {
        var foreign = new SourceEvidenceIntakePageObjects.Binding(warehouse, UUID.randomUUID(),
            submission, 1, device, operator, openKey);

        assertThatThrownBy(() -> preflight.requireStoredRoots(request, expected, key, stopBinding, foreign))
            .isInstanceOf(IllegalArgumentException.class);
        verify(receipts, never()).objectIdForLockedReplay(any(), any());
    }

    @Test void replacedOriginalOpenCannotAdvanceToStopProof() {
        when(opens.openBound(any())).thenReturn(null);

        assertCode("E2A_OPEN_CHANGED", () ->
            preflight.requireStoredRoots(request, expected, key, stopBinding, pageBinding));
        verify(deviceKeys, never()).trusted(any(), any());
    }

    @Test void tamperedSignedStopCannotReadReceiptOrPages() {
        var malformed = (ObjectNode) SubmissionJson.parse(request);
        ((ObjectNode) malformed.path("stopProof")).put("signature", "AA==");
        var altered = E2aProtocol.canonical(bytes(malformed));

        assertCode("E2A_STOP_PROOF_UNVERIFIABLE", () ->
            preflight.requireStoredRoots(altered, expected, key, stopBinding, pageBinding));
        verify(receipts, never()).objectIdForLockedReplay(any(), any());
    }

    @Test void wrongTrustedKeyCannotBorrowOriginalStop() throws Exception {
        var generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        var foreign = new SourceEvidenceIntakeStop.Expected(openKey, expected.open(), warehouse,
            operator, device, 2, generator.generateKeyPair().getPublic());

        assertCode("E2A_STOP_KEY_UNVERIFIABLE", () ->
            preflight.requireStoredRoots(request, foreign, key, stopBinding, pageBinding));
        verify(pages, never()).inspect(any(), any());
    }

    @Test void absentServerKeyFailsBeforeSignatureParsing() {
        when(deviceKeys.trusted(warehouse, device)).thenReturn(null);

        assertCode("E2A_STOP_KEY_UNVERIFIABLE", () ->
            preflight.requireStoredRoots(request, expected, key, stopBinding, pageBinding));
        verify(receipts, never()).objectIdForLockedReplay(any(), any());
    }

    private byte[] signedRequest(KeyPair keys) {
        var page = SubmissionJson.object().put("manifestId", "manifest").put("manifestVersion", 1)
            .put("snapshotId", "snapshot").put("pageNo", 1).put("pageCount", 1)
            .put("isFinal", true).put("itemCount", 0).put("byteCount", 0).putNull("prevPageHash");
        page.putArray("items");
        var manifest = SubmissionJson.object().put("schemaVersion", 1).put("manifestId", "manifest")
            .put("manifestVersion", 1).put("snapshotId", "snapshot").put("source", "TEMP")
            .put("sourceId", authorization.toString()).put("submissionId", submission.toString())
            .put("workRound", 1).put("pageCount", 1).put("entryCount", 0).put("byteCount", 0).put("endPageNo", 1);
        manifest.putArray("pages").add(SubmissionJson.object().put("pageNo", 1).put("pageHash", E2aProtocol.pageHash(page)));
        var hashes = E2aProtocol.manifestHashes(manifest);
        var core = SubmissionJson.object().put("warehouseId", warehouse.toString())
            .put("sourceId", authorization.toString()).put("submissionId", submission.toString())
            .put("workRound", 1).put("originalOperatorId", operator.toString())
            .put("freezeDeviceId", device.toString()).put("sourceRecoveryEpoch", 2)
            .put("stopEventId", "event").put("captureHighWatermark", 0).put("auditThroughSeq", 1)
            .put("stopTransactionVersion", 1).put("manifestId", "manifest").put("manifestVersion", 1)
            .put("manifestBytesHash", hashes.manifestBytesHash()).put("setHash", hashes.setHash());
        var body = SubmissionJson.object().put("operation", "STOP").put("openOriginalKey", openKey.toString())
            .put("manifestBytesHash", hashes.manifestBytesHash()).put("setHash", hashes.setHash());
        body.set("manifestCore", manifest);
        body.set("stopProof", E2aProtocol.signStop(core, keys.getPrivate(),
            ImportFiles.sha256(keys.getPublic().getEncoded())));
        return E2aProtocol.canonical(bytes(body));
    }

    private static byte[] bytes(ObjectNode value) { return value.toString().getBytes(StandardCharsets.UTF_8); }

    private static void assertCode(String code, Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
            error -> assertThat(error.code()).isEqualTo(code));
    }
}
