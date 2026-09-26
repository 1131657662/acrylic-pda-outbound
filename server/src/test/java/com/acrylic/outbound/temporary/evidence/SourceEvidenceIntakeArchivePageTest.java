package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.inventory.ImportFiles;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.protocol.E2aPageItem;
import com.acrylic.outbound.temporary.protocol.E2aProtocol;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SourceEvidenceIntakeArchivePageTest {
    @TempDir Path directory;
    private final UUID openKey = UUID.randomUUID();
    private final SourceEvidenceIntakePageObjects.Binding binding = new SourceEvidenceIntakePageObjects.Binding(
        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1, UUID.randomUUID(), UUID.randomUUID(), openKey);
    private final SourceEvidenceIntakePageObjects pages = mock(SourceEvidenceIntakePageObjects.class);
    private final SourceEvidenceIntakePageItems staged = mock(SourceEvidenceIntakePageItems.class);
    private final SourceEvidenceIntakeRawObjects objects = mock(SourceEvidenceIntakeRawObjects.class);
    private final SourceEvidenceIntakeAuditPrefix audits = mock(SourceEvidenceIntakeAuditPrefix.class);
    private final SourceEvidenceIntakeAuditArchives archives = mock(SourceEvidenceIntakeAuditArchives.class);

    @Test void storedArchivePageReadsOriginalProofAndChecksSignedServerArchive() throws Exception {
        var fixture = fixture("ARCHIVE", "AUDIT_ARCHIVE");

        assertThat(fixture.closure().inspect(fixture.stop(), binding).auditCovered()).isTrue();

        verify(archives).requireItem(eq(fixture.item()), eq(fixture.original()),
            eq(new SourceEvidenceIntakeAuditArchives.Context(binding.creatorDeviceId(),
                binding.warehouseId(), 2, 2)));
    }

    @Test void archiveVerifierConflictRejectsPageClosure() throws Exception {
        var fixture = fixture("ARCHIVE", "AUDIT_ARCHIVE");
        doThrow(new BusinessException(409, "E2A_AUDIT_ARCHIVE_UNVERIFIABLE", "different proof"))
            .when(archives).requireItem(any(), any(), any());

        assertThatThrownBy(() -> fixture.closure().inspect(fixture.stop(), binding))
            .isInstanceOfSatisfying(BusinessException.class,
                error -> org.assertj.core.api.Assertions.assertThat(error.code())
                    .isEqualTo("E2A_AUDIT_ARCHIVE_UNVERIFIABLE"));
    }

    @Test void archiveRawObjectCannotBeDisguisedAsAnotherItemKind() throws Exception {
        var fixture = fixture("STOP", "AUDIT_ARCHIVE");

        assertThatThrownBy(() -> fixture.closure().inspect(fixture.stop(), binding))
            .isInstanceOfSatisfying(BusinessException.class,
                error -> org.assertj.core.api.Assertions.assertThat(error.code())
                    .isEqualTo("E2A_AUDIT_OBJECT_KIND_CONFLICT"));
        verifyNoInteractions(archives);
    }

    private Fixture fixture(String kind, String objectKind) throws Exception {
        var pageFiles = new SourceEvidenceIntakeFiles(directory.toString());
        var rawFiles = new SourceEvidenceIntakeObjectFiles(directory.toString());
        byte[] original = bytes(SubmissionJson.object().put("signedArchive", "original")
            .put("afterSeq", "0").put("throughSeq", "2"));
        var raw = rawFiles.store(new ByteArrayInputStream(original), original.length, ImportFiles.sha256(original));
        UUID record = UUID.randomUUID(), object = UUID.randomUUID(), rawId = UUID.randomUUID();
        var item = item(kind, objectKind, record, object, raw);
        var page = page(item, raw.bytes());
        var manifest = manifest(page, raw.bytes());
        var hashes = E2aProtocol.manifestHashes(manifest);
        var stop = new SourceEvidenceIntakeStop.Parsed(new byte[] {1}, "0".repeat(64),
            E2aProtocol.canonical(bytes(manifest)), hashes.manifestBytesHash(), hashes.setHash(),
            bytes(SubmissionJson.object().set("core", SubmissionJson.object()
                .put("sourceRecoveryEpoch", 2).put("auditThroughSeq", 2))), openKey, "stop");
        UUID pageId = UUID.randomUUID();
        when(pages.findAfterSourceLock(binding, 1)).thenReturn(new SourceEvidenceIntakePageObjects.PageObject(
            pageId, SourceEvidenceIntakePageObjects.originalId(openKey, 1),
            pageFiles.storePage(E2aProtocol.canonical(bytes(page)))));
        when(staged.existing(new SourceEvidenceIntakePageItems.PageContext(binding, 1, pageId)))
            .thenReturn(List.of(new SourceEvidenceIntakePageItems.Indexed(0, kind, record, object, rawId, raw.bytes())));
        when(staged.totals(binding)).thenReturn(new SourceEvidenceIntakePageItems.Totals(raw.bytes(), 1));
        when(objects.findAfterSourceLock(any())).thenReturn(
            new SourceEvidenceIntakeRawObjects.RawObject(rawId, object, raw));
        return new Fixture(new SourceEvidenceIntakePageClosure(pages, staged, pageFiles, objects,
            rawFiles, audits, archives), stop, E2aPageItem.parse(item), original);
    }

    private static ObjectNode item(String kind, String objectKind, UUID record, UUID object,
        SourceEvidenceIntakeObjectFiles.Stored raw) {
        return SubmissionJson.object().put("itemKind", kind).put("originalRecordId", record.toString())
            .put("objectKind", objectKind).put("originalObjectId", object.toString())
            .put("itemBytesSha256", raw.sha256()).put("byteCount", raw.bytes())
            .put("originalDeviceSeq", 2).put("recordSha256", "a".repeat(64))
            .put("recordStatus", "SAVED").putNull("baleId").putNull("originalCycle").putNull("captureId");
    }

    private static ObjectNode page(ObjectNode item, int length) {
        var page = SubmissionJson.object().put("manifestId", "manifest").put("manifestVersion", 1)
            .put("snapshotId", "snapshot").put("pageNo", 1).put("pageCount", 1).put("isFinal", true)
            .put("itemCount", 1).put("byteCount", length).putNull("prevPageHash");
        page.putArray("items").add(item);
        return page;
    }

    private ObjectNode manifest(ObjectNode page, int length) {
        var manifest = SubmissionJson.object().put("schemaVersion", 1).put("manifestId", "manifest")
            .put("manifestVersion", 1).put("snapshotId", "snapshot").put("source", "TEMP")
            .put("sourceId", UUID.randomUUID().toString()).put("submissionId", binding.submissionId().toString())
            .put("workRound", 1).put("pageCount", 1).put("entryCount", 1)
            .put("byteCount", length).put("endPageNo", 1);
        manifest.putArray("pages").add(SubmissionJson.object().put("pageNo", 1)
            .put("pageHash", E2aProtocol.pageHash(page)));
        return manifest;
    }

    private static byte[] bytes(ObjectNode node) {
        return node.toString().getBytes(StandardCharsets.UTF_8);
    }

    private record Fixture(SourceEvidenceIntakePageClosure closure, SourceEvidenceIntakeStop.Parsed stop,
        E2aPageItem item, byte[] original) { }
}
