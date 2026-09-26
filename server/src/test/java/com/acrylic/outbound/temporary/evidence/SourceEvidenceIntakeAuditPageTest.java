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
import static org.mockito.Mockito.when;

class SourceEvidenceIntakeAuditPageTest {
    @TempDir Path directory;
    private final UUID openKey = UUID.randomUUID();
    private final SourceEvidenceIntakePageObjects.Binding binding = new SourceEvidenceIntakePageObjects.Binding(
        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1, UUID.randomUUID(), UUID.randomUUID(), openKey);
    private final SourceEvidenceIntakePageObjects objects = mock(SourceEvidenceIntakePageObjects.class);
    private final SourceEvidenceIntakePageItems staged = mock(SourceEvidenceIntakePageItems.class);
    private final SourceEvidenceIntakeRawObjects rawObjects = mock(SourceEvidenceIntakeRawObjects.class);
    private final SourceEvidenceIntakeAuditPrefix audits = mock(SourceEvidenceIntakeAuditPrefix.class);

    @Test void pageRootReadsOriginalAuditBytesAndChecksStoredEvent() throws Exception {
        var fixture = fixture(1);
        assertThat(fixture.closure().inspect(fixture.stop(), binding).auditCovered()).isTrue();

        verify(audits).requireItem(eq(fixture.item()), eq(fixture.original()),
            eq(binding.creatorDeviceId()), eq(binding.warehouseId()), eq(2L));
    }

    @Test void unsignedClaimBeyondStopAuditCutoffFailsBeforeAuditLookup() throws Exception {
        var fixture = fixture(2);

        assertThatThrownBy(() -> fixture.closure().inspect(fixture.stop(), binding))
            .isInstanceOfSatisfying(BusinessException.class,
                error -> org.assertj.core.api.Assertions.assertThat(error.code())
                    .isEqualTo("E2A_AUDIT_ITEM_OUTSIDE_STOP"));
        org.mockito.Mockito.verifyNoInteractions(audits);
    }

    @Test void storedAuditConflictRejectsPageClosure() throws Exception {
        var fixture = fixture(1);
        doThrow(new BusinessException(409, "E2A_AUDIT_PREFIX_UNVERIFIABLE", "different source"))
            .when(audits).requireItem(any(), any(), any(), any(), eq(2L));

        assertThatThrownBy(() -> fixture.closure().inspect(fixture.stop(), binding))
            .isInstanceOfSatisfying(BusinessException.class,
                error -> org.assertj.core.api.Assertions.assertThat(error.code())
                    .isEqualTo("E2A_AUDIT_PREFIX_UNVERIFIABLE"));
    }

    private Fixture fixture(long sequence) throws Exception {
        var pageFiles = new SourceEvidenceIntakeFiles(directory.toString());
        var objectFiles = new SourceEvidenceIntakeObjectFiles(directory.toString());
        var event = SubmissionJson.object().put("id", UUID.randomUUID().toString()).putNull("captureId")
            .put("originDeviceId", binding.creatorDeviceId().toString())
            .put("operatorId", binding.originalOperatorId().toString())
            .put("localSeq", Long.toString(sequence)).put("type", "STOP").put("detail", "saved")
            .put("wallMs", "1").put("elapsedMs", "1").put("boot", 1)
            .put("assignmentId", UUID.randomUUID().toString());
        byte[] original = SubmissionJson.canonical(event).getBytes(StandardCharsets.UTF_8);
        var stored = objectFiles.store(new ByteArrayInputStream(original), original.length, ImportFiles.sha256(original));
        UUID originalObjectId = UUID.randomUUID(), rawId = UUID.randomUUID();
        var item = SubmissionJson.object().put("itemKind", "AUDIT").put("originalRecordId", event.path("id").asText())
            .put("objectKind", "AUDIT_RECORDS").put("originalObjectId", originalObjectId.toString())
            .put("itemBytesSha256", stored.sha256()).put("byteCount", stored.bytes())
            .put("originalDeviceSeq", sequence).put("recordSha256", SubmissionJson.sha(event))
            .put("recordStatus", "SAVED").putNull("baleId").putNull("originalCycle").putNull("captureId");
        var page = SubmissionJson.object().put("manifestId", "m").put("manifestVersion", 1)
            .put("snapshotId", "s").put("pageNo", 1).put("pageCount", 1).put("isFinal", true)
            .put("itemCount", 1).put("byteCount", stored.bytes()).putNull("prevPageHash");
        page.putArray("items").add(item);
        var manifest = SubmissionJson.object().put("schemaVersion", 1).put("manifestId", "m")
            .put("manifestVersion", 1).put("snapshotId", "s").put("source", "TEMP")
            .put("sourceId", UUID.randomUUID().toString()).put("submissionId", binding.submissionId().toString())
            .put("workRound", 1).put("pageCount", 1).put("entryCount", 1)
            .put("byteCount", stored.bytes()).put("endPageNo", 1);
        manifest.putArray("pages").add(SubmissionJson.object().put("pageNo", 1)
            .put("pageHash", E2aProtocol.pageHash(page)));
        var hashes = E2aProtocol.manifestHashes(manifest);
        var stop = new SourceEvidenceIntakeStop.Parsed(new byte[]{1}, "0".repeat(64),
            E2aProtocol.canonical(bytes(manifest)), hashes.manifestBytesHash(), hashes.setHash(),
            bytes(SubmissionJson.object().set("core", SubmissionJson.object()
                .put("sourceRecoveryEpoch", 2).put("auditThroughSeq", 1))), openKey, "stop");
        UUID pageId = UUID.randomUUID();
        when(objects.findAfterSourceLock(binding, 1)).thenReturn(new SourceEvidenceIntakePageObjects.PageObject(
            pageId, SourceEvidenceIntakePageObjects.originalId(openKey, 1),
            pageFiles.storePage(E2aProtocol.canonical(bytes(page)))));
        when(staged.existing(new SourceEvidenceIntakePageItems.PageContext(binding, 1, pageId)))
            .thenReturn(List.of(new SourceEvidenceIntakePageItems.Indexed(0, "AUDIT",
                UUID.fromString(event.path("id").asText()), originalObjectId, rawId, stored.bytes())));
        when(staged.totals(binding)).thenReturn(new SourceEvidenceIntakePageItems.Totals(stored.bytes(), 1));
        when(rawObjects.findAfterSourceLock(any())).thenReturn(
            new SourceEvidenceIntakeRawObjects.RawObject(rawId, originalObjectId, stored));
        return new Fixture(new SourceEvidenceIntakePageClosure(objects, staged, pageFiles, rawObjects,
            objectFiles, audits, org.mockito.Mockito.mock(SourceEvidenceIntakeAuditArchives.class)),
            stop, E2aPageItem.parse(item), original);
    }

    private static byte[] bytes(ObjectNode value) { return value.toString().getBytes(StandardCharsets.UTF_8); }

    private record Fixture(SourceEvidenceIntakePageClosure closure, SourceEvidenceIntakeStop.Parsed stop,
        E2aPageItem item, byte[] original) { }
}
