package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.inventory.ImportFiles;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.protocol.E2aProtocol;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SourceEvidenceIntakePageClosureTest {
    @TempDir Path directory;
    private final UUID openKey = UUID.randomUUID(), submission = UUID.randomUUID();
    private final SourceEvidenceIntakePageObjects.Binding binding = new SourceEvidenceIntakePageObjects.Binding(
        UUID.randomUUID(), UUID.randomUUID(), submission, 3, UUID.randomUUID(), UUID.randomUUID(), openKey);
    private final SourceEvidenceIntakePageObjects objects = mock(SourceEvidenceIntakePageObjects.class);
    private final SourceEvidenceIntakePageItems staged = mock(SourceEvidenceIntakePageItems.class);
    private final SourceEvidenceIntakeRawObjects rawObjects = mock(SourceEvidenceIntakeRawObjects.class);
    private final SourceEvidenceIntakeAuditPrefix audits = mock(SourceEvidenceIntakeAuditPrefix.class);
    private final SourceEvidenceIntakeAuditArchives archives = mock(SourceEvidenceIntakeAuditArchives.class);
    private final UUID rawId = UUID.randomUUID();
    private SourceEvidenceIntakePageClosure closure;
    private SourceEvidenceIntakeFiles files;
    private SourceEvidenceIntakeObjectFiles objectFiles;
    private ObjectNode first;
    private ObjectNode second;
    private ObjectNode manifest;

    @BeforeEach void setUp() throws Exception {
        files = new SourceEvidenceIntakeFiles(directory.toString());
        objectFiles = new SourceEvidenceIntakeObjectFiles(directory.toString());
        closure = new SourceEvidenceIntakePageClosure(objects, staged, files, rawObjects, objectFiles, audits, archives);
        first = page(1, 2, null);
        second = page(2, 2, E2aProtocol.pageHash(first));
        manifest = manifest(first, second);
        when(staged.existing(any())).thenReturn(List.of());
        when(staged.totals(binding)).thenReturn(new SourceEvidenceIntakePageItems.Totals(0, 0));
    }

    @Test void missingPageStaysPartialEvenWithValidExistingRoot() throws Exception {
        store(1, first);

        var result = closure.inspect(stop(manifest), binding);

        assertThat(result.missingPageNumbers()).containsExactly(2L);
        assertThat(result.receivedEntries()).isZero();
        verify(staged, never()).totals(any());
    }

    @Test void allRootsAndStageCountsMatchWithoutGrantingTrustedStatus() throws Exception {
        store(1, first);
        store(2, second);

        var result = closure.inspect(stop(manifest), binding);

        assertThat(result.missingPageNumbers()).isEmpty();
        assertThat(result.receivedBytes()).isZero();
        verify(staged).totals(binding);
    }

    @Test void manifestCannotSubstituteAnotherStoredPageRoot() throws Exception {
        store(1, first);
        var altered = manifest.deepCopy();
        ((ObjectNode) altered.path("pages").get(0)).put("pageHash", "0".repeat(64));

        assertCode("E2A_PAGE_ROOT_CONFLICT", () -> closure.inspect(stop(altered), binding));
    }

    @Test void matchingPageRootWithBrokenPreviousLinkFails() throws Exception {
        store(1, first);
        var broken = page(2, 2, "f".repeat(64));
        store(2, broken);
        var changed = manifest(first, broken);

        assertCode("E2A_PAGE_ROOT_CONFLICT", () -> closure.inspect(stop(changed), binding));
    }

    @Test void missingStageRowDoesNotCloseAManifest() throws Exception {
        store(1, first);
        when(staged.existing(any())).thenReturn(List.of(new SourceEvidenceIntakePageItems.Indexed(
            0, "SCAN", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 100)));

        assertCode("E2A_PAGE_STAGE_CONFLICT", () -> closure.inspect(stop(manifest), binding));
    }

    @Test void claimedManifestTotalCannotOverrideRecountedPages() throws Exception {
        store(1, first);
        store(2, second);
        var altered = manifest.deepCopy();
        altered.put("entryCount", 1);

        assertCode("E2A_MANIFEST_TOTALS_CONFLICT", () -> closure.inspect(stop(altered), binding));
    }

    @Test void missingFileFailsClosedRatherThanLookingLikeAMissingPage() throws Exception {
        var stored = new SourceEvidenceIntakeFiles.Stored(
            "source-evidence-intake/" + "0".repeat(64) + ".json", "0".repeat(64), 100);
        when(objects.findAfterSourceLock(binding, 1)).thenReturn(new SourceEvidenceIntakePageObjects.PageObject(
            UUID.randomUUID(), SourceEvidenceIntakePageObjects.originalId(openKey, 1), stored));

        assertCode("E2A_PAGE_FILE_UNAVAILABLE", () -> closure.inspect(stop(manifest), binding));
    }

    @Test void malformedPersistedPageIsAnIntegrityFailure() throws Exception {
        var stored = files.storePage("{".getBytes(StandardCharsets.UTF_8));
        when(objects.findAfterSourceLock(binding, 1)).thenReturn(new SourceEvidenceIntakePageObjects.PageObject(
            UUID.randomUUID(), SourceEvidenceIntakePageObjects.originalId(openKey, 1), stored));

        assertCode("E2A_PAGE_ROOT_CONFLICT", () -> closure.inspect(stop(manifest), binding));
    }

    @Test void nonemptyPageChecksExactStagedRecordAndTotals() throws Exception {
        var item = addCapturedItem();
        store(1, first);
        store(2, second);
        when(staged.existing(new SourceEvidenceIntakePageItems.PageContext(binding, 1,
            objects.findAfterSourceLock(binding, 1).id()))).thenReturn(List.of(indexed(item)));
        when(staged.totals(binding)).thenReturn(new SourceEvidenceIntakePageItems.Totals(
            item.path("byteCount").intValue(), 1));

        assertThat(closure.inspect(stop(manifest), binding).receivedEntries()).isEqualTo(1);
    }

    @Test void vanishedRawObjectFailsDespiteMatchingPageAndStage() throws Exception {
        var item = addCapturedItem(); store(1, first);
        var pageObject = objects.findAfterSourceLock(binding, 1);
        when(staged.existing(new SourceEvidenceIntakePageItems.PageContext(binding, 1, pageObject.id())))
            .thenReturn(List.of(indexed(item)));
        when(rawObjects.findAfterSourceLock(any())).thenReturn(null);

        assertCode("E2A_RAW_OBJECT_CONFLICT", () -> closure.inspect(stop(manifest), binding));
    }

    @Test void missingRawFileFailsDespiteMatchingPageAndStage() throws Exception {
        var item = addCapturedItem(); store(1, first);
        var pageObject = objects.findAfterSourceLock(binding, 1);
        when(staged.existing(new SourceEvidenceIntakePageItems.PageContext(binding, 1, pageObject.id())))
            .thenReturn(List.of(indexed(item)));
        var raw = rawObjects.findAfterSourceLock(new SourceEvidenceIntakeRawObjects.Binding(
            binding.warehouseId(), binding.scopeId(), binding.submissionId(), binding.workRound(),
            binding.creatorDeviceId(), binding.originalOperatorId(), "CAPTURE_RECORDS",
            UUID.fromString(item.path("originalObjectId").asText())));
        Files.delete(directory.resolve(raw.stored().key()));

        assertCode("E2A_RAW_OBJECT_FILE_UNAVAILABLE", () -> closure.inspect(stop(manifest), binding));
    }

    @Test void stagedRecordWithCorrectCountButWrongIdentityFails() throws Exception {
        var item = addCapturedItem();
        store(1, first);
        var object = objects.findAfterSourceLock(binding, 1);
        when(staged.existing(new SourceEvidenceIntakePageItems.PageContext(binding, 1, object.id())))
            .thenReturn(List.of(new SourceEvidenceIntakePageItems.Indexed(0, "CAPTURE", UUID.randomUUID(),
                UUID.fromString(item.path("originalObjectId").asText()), UUID.randomUUID(), 4)));

        assertCode("E2A_PAGE_STAGE_CONFLICT", () -> closure.inspect(stop(manifest), binding));
    }

    private void store(long number, ObjectNode page) throws Exception {
        var stored = files.storePage(E2aProtocol.canonical(bytes(page)));
        when(objects.findAfterSourceLock(binding, number)).thenReturn(new SourceEvidenceIntakePageObjects.PageObject(
            UUID.randomUUID(), SourceEvidenceIntakePageObjects.originalId(openKey, number), stored));
    }

    private ObjectNode addCapturedItem() throws Exception {
        var capture = SubmissionJson.object().put("id", UUID.randomUUID().toString())
            .put("originDeviceId", binding.creatorDeviceId().toString())
            .put("deviceCaptureSeq", "1").put("state", "VALID");
        byte[] original = SubmissionJson.canonical(capture).getBytes(StandardCharsets.UTF_8);
        var stored = objectFiles.store(new ByteArrayInputStream(original), original.length,
            ImportFiles.sha256(original));
        var item = SubmissionJson.object().put("itemKind", "CAPTURE")
            .put("originalRecordId", capture.path("id").asText())
            .put("objectKind", "CAPTURE_RECORDS").put("originalObjectId", UUID.randomUUID().toString())
            .put("itemBytesSha256", stored.sha256()).put("byteCount", original.length)
            .put("originalDeviceSeq", 1).put("recordSha256", SubmissionJson.sha(capture))
            .put("recordStatus", "VALID").putNull("baleId").putNull("originalCycle").putNull("captureId");
        first.put("itemCount", 1).put("byteCount", original.length).withArray("items").add(item);
        second = page(2, 2, E2aProtocol.pageHash(first));
        manifest = manifest(first, second).put("entryCount", 1).put("byteCount", original.length);
        when(rawObjects.findAfterSourceLock(any())).thenReturn(new SourceEvidenceIntakeRawObjects.RawObject(
            rawId, UUID.fromString(item.path("originalObjectId").asText()), stored));
        return item;
    }

    private SourceEvidenceIntakePageItems.Indexed indexed(ObjectNode item) {
        return new SourceEvidenceIntakePageItems.Indexed(0, "CAPTURE",
            UUID.fromString(item.path("originalRecordId").asText()),
            UUID.fromString(item.path("originalObjectId").asText()), rawId, item.path("byteCount").intValue());
    }

    private ObjectNode page(int number, int count, String previous) {
        var core = SubmissionJson.object().put("manifestId", "manifest").put("manifestVersion", 2)
            .put("snapshotId", "snapshot").put("pageNo", number).put("pageCount", count)
            .put("isFinal", number == count).put("itemCount", 0).put("byteCount", 0);
        if (previous == null) core.putNull("prevPageHash"); else core.put("prevPageHash", previous);
        core.putArray("items");
        return core;
    }

    private ObjectNode manifest(ObjectNode left, ObjectNode right) {
        var core = SubmissionJson.object().put("schemaVersion", 1).put("manifestId", "manifest")
            .put("manifestVersion", 2).put("snapshotId", "snapshot").put("source", "TEMP")
            .put("sourceId", UUID.randomUUID().toString()).put("submissionId", submission.toString())
            .put("workRound", 3).put("pageCount", 2).put("entryCount", 0)
            .put("byteCount", 0).put("endPageNo", 2);
        core.putArray("pages").add(SubmissionJson.object().put("pageNo", 1)
            .put("pageHash", E2aProtocol.pageHash(left)));
        core.withArray("pages").add(SubmissionJson.object().put("pageNo", 2)
            .put("pageHash", E2aProtocol.pageHash(right)));
        return core;
    }

    private SourceEvidenceIntakeStop.Parsed stop(ObjectNode core) {
        var hashes = E2aProtocol.manifestHashes(core);
        return new SourceEvidenceIntakeStop.Parsed(new byte[] {1}, "0".repeat(64),
            E2aProtocol.canonical(bytes(core)), hashes.manifestBytesHash(), hashes.setHash(),
            bytes(SubmissionJson.object().set("core", SubmissionJson.object()
                .put("sourceRecoveryEpoch", 2).put("auditThroughSeq", 3))), openKey, "stop-event");
    }

    private static byte[] bytes(ObjectNode value) { return value.toString().getBytes(StandardCharsets.UTF_8); }

    private static void assertCode(String code, Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
            error -> assertThat(error.code()).isEqualTo(code));
    }
}
