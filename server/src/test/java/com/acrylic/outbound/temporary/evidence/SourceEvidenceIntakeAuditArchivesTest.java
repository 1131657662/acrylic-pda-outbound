package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.audit.AuditArchiveReader;
import com.acrylic.outbound.audit.AuditArchiveStore;
import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.inventory.ImportFiles;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.protocol.E2aPageItem;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SourceEvidenceIntakeAuditArchivesTest {
    private final UUID device = UUID.randomUUID(), warehouse = UUID.randomUUID();
    private final SourceEvidenceIntakeAuditArchives.Context context =
        new SourceEvidenceIntakeAuditArchives.Context(device, warehouse, 3, 2);
    private final AuditArchiveStore store = mock(AuditArchiveStore.class);
    private final AuditArchiveReader reader = mock(AuditArchiveReader.class);
    private final SourceEvidenceIntakeAuditPrefix prefix = mock(SourceEvidenceIntakeAuditPrefix.class);
    private final SourceEvidenceIntakeAuditArchives archives =
        new SourceEvidenceIntakeAuditArchives(store, reader, prefix);

    @Test void originalProofAndSignedFileMustMatchAllArchivedDeviceEvents() {
        var proof = proof();
        var events = SubmissionJson.object().putArray("events");
        var first = SubmissionJson.object().put("localSeq", "1");
        var second = SubmissionJson.object().put("localSeq", "2");
        events.add(first).add(second);
        when(store.find("2", device, 0)).thenReturn(proof);
        when(reader.read(proof, device, warehouse)).thenReturn(events);

        archives.requireItem(item(proof), bytes(proof), context);

        verify(prefix).requireArchiveEvent(first, device, warehouse, 3);
        verify(prefix).requireArchiveEvent(second, device, warehouse, 3);
        assertThat(SourceEvidenceIntakeAuditArchives.recordId(device, 2, 0))
            .isEqualTo(item(proof).originalRecordId());
    }

    @Test void missingOrSubstitutedServerArchiveIndexFailsBeforeReadingSignedFile() {
        var proof = proof();
        assertUnverifiable(() -> archives.requireItem(item(proof), bytes(proof), context));
        var other = proof.deepCopy().put("payloadSha256", "f".repeat(64));
        when(store.find("2", device, 0)).thenReturn(other);

        assertUnverifiable(() -> archives.requireItem(item(proof), bytes(proof), context));
        verify(reader, never()).read(any(), any(), any());
    }

    @Test void changedRawBytesOrClaimsCannotBorrowAnotherProof() {
        var proof = proof();
        var item = item(proof);
        assertUnverifiable(() -> archives.requireItem(item, (" " + SubmissionJson.canonical(proof))
            .getBytes(StandardCharsets.UTF_8), context));
        var wrongId = new E2aPageItem("ARCHIVE", UUID.randomUUID(), "AUDIT_ARCHIVE",
            item.originalObjectId(), item.itemBytesSha256(), item.byteCount(), 2,
            item.recordSha256(), "SAVED", null, null, null);
        assertUnverifiable(() -> archives.requireItem(wrongId, bytes(proof), context));
    }

    @Test void futureEpochOrArchiveBeyondSignedStopCannotBeAccepted() {
        var proof = proof();
        proof.put("recoveryEpoch", "4");
        assertUnverifiable(() -> archives.requireItem(item(proof), bytes(proof), context));
        proof.put("recoveryEpoch", "2").put("throughSeq", "3");
        assertUnverifiable(() -> archives.requireItem(item(proof), bytes(proof), context));
    }

    @Test void archiveEventConflictingWithDatabaseFailsClosed() {
        var proof = proof();
        var events = SubmissionJson.object().putArray("events");
        var first = SubmissionJson.object().put("localSeq", "1");
        events.add(first).add(SubmissionJson.object().put("localSeq", "2"));
        when(store.find("2", device, 0)).thenReturn(proof);
        when(reader.read(proof, device, warehouse)).thenReturn(events);
        doThrow(new BusinessException(409, "E2A_AUDIT_PREFIX_UNVERIFIABLE", "conflict"))
            .when(prefix).requireArchiveEvent(first, device, warehouse, 3);

        assertUnverifiable(() -> archives.requireItem(item(proof), bytes(proof), context));
    }

    private ObjectNode proof() {
        return SubmissionJson.object().put("archiveVersion", 1)
            .put("originDeviceId", device.toString()).put("warehouseId", warehouse.toString())
            .put("recoveryEpoch", "2").put("afterSeq", "0").put("throughSeq", "2")
            .put("payloadSha256", "a".repeat(64)).put("signedArchive", "synthetic");
    }

    private E2aPageItem item(ObjectNode proof) {
        byte[] original = bytes(proof);
        String digest = SubmissionJson.sha(proof);
        return new E2aPageItem("ARCHIVE", SourceEvidenceIntakeAuditArchives.recordId(device,
            SubmissionJson.number(proof, "recoveryEpoch"), SubmissionJson.number(proof, "afterSeq")),
            "AUDIT_ARCHIVE", UUID.randomUUID(), ImportFiles.sha256(original), original.length,
            SubmissionJson.number(proof, "throughSeq"), digest, "SAVED", null, null, null);
    }

    private static byte[] bytes(ObjectNode proof) {
        return SubmissionJson.canonical(proof).getBytes(StandardCharsets.UTF_8);
    }

    private static void assertUnverifiable(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
            error -> assertThat(error.code()).isEqualTo("E2A_AUDIT_ARCHIVE_UNVERIFIABLE"));
    }
}
