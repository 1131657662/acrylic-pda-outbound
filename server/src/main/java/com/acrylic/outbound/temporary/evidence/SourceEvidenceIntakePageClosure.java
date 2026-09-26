package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.protocol.E2aPageItem;
import com.acrylic.outbound.temporary.protocol.E2aProtocol;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SourceEvidenceIntakePageClosure {
    private final SourceEvidenceIntakePageObjects objects;
    private final SourceEvidenceIntakePageItems staged;
    private final SourceEvidenceIntakeFiles files;
    private final SourceEvidenceIntakeRawObjects rawObjects;
    private final SourceEvidenceIntakeObjectFiles objectFiles;
    private final SourceEvidenceIntakeAuditPrefix audits;
    private final SourceEvidenceIntakeAuditArchives archives;

    public SourceEvidenceIntakePageClosure(SourceEvidenceIntakePageObjects objects,
        SourceEvidenceIntakePageItems staged, SourceEvidenceIntakeFiles files,
        SourceEvidenceIntakeRawObjects rawObjects, SourceEvidenceIntakeObjectFiles objectFiles,
        SourceEvidenceIntakeAuditPrefix audits, SourceEvidenceIntakeAuditArchives archives) {
        this.objects = objects; this.staged = staged; this.files = files;
        this.rawObjects = rawObjects; this.objectFiles = objectFiles;
        this.audits = audits; this.archives = archives;
    }

    @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
    public Assessment inspect(SourceEvidenceIntakeStop.Parsed stop,
        SourceEvidenceIntakePageObjects.Binding binding) {
        if (stop == null || binding == null || !binding.openKey().equals(stop.openOriginalKey()))
            throw new IllegalArgumentException("PAGE_CLOSURE_BINDING");
        var manifest = manifest(stop, binding);
        var proof=SubmissionJson.parse(stop.canonicalStopProofBytes()).path("core");
        long epoch=proof.path("sourceRecoveryEpoch").longValue();
        long auditThrough=proof.path("auditThroughSeq").longValue();
        var missing = new ArrayList<Long>();
        var auditRanges = new ArrayList<SourceEvidenceIntakeAuditCoverage.Range>();
        var captureSequences = new ArrayList<Long>();
        long entries = 0, bytes = 0;
        for (int number = 1; number <= manifest.path("pageCount").intValue(); number++) {
            var object = objects.findAfterSourceLock(binding, number);
            if (object == null) { missing.add((long) number); continue; }
            var page = page(object, manifest, number);
            compareItems(binding, object, page, number, epoch, auditThrough, auditRanges, captureSequences);
            entries += page.path("itemCount").longValue();
            bytes += page.path("byteCount").longValue();
        }
        if (missing.isEmpty()) {
            require(entries == manifest.path("entryCount").longValue()
                && bytes == manifest.path("byteCount").longValue(), "E2A_MANIFEST_TOTALS_CONFLICT");
            var totals = staged.totals(binding);
            require(totals.entries() == entries && totals.bytes() == bytes, "E2A_PAGE_STAGE_CONFLICT");
        }
        long captureHighWatermark = proof.path("captureHighWatermark").longValue();
        return new Assessment(List.copyOf(missing), entries, bytes,
            missing.isEmpty() && SourceEvidenceIntakeAuditCoverage.complete(auditRanges, auditThrough),
            missing.isEmpty() && SourceEvidenceIntakeCaptureCoverage.complete(captureSequences, captureHighWatermark));
    }

    private ObjectNode manifest(SourceEvidenceIntakeStop.Parsed stop,
        SourceEvidenceIntakePageObjects.Binding binding) {
        try {
            var parsed = SubmissionJson.parse(stop.manifestBytes());
            require(parsed instanceof ObjectNode, "E2A_MANIFEST_INTEGRITY_FAILURE");
            var core = (ObjectNode) parsed;
            var hashes = E2aProtocol.manifestHashes(core);
            require(Arrays.equals(stop.manifestBytes(), canonical(core))
                && hashes.manifestBytesHash().equals(stop.manifestBytesHash())
                && hashes.setHash().equals(stop.setHash())
                && binding.submissionId().toString().equals(core.path("submissionId").textValue())
                && binding.workRound() == core.path("workRound").longValue(), "E2A_MANIFEST_INTEGRITY_FAILURE");
            return core;
        } catch (RuntimeException error) { throw conflict("E2A_MANIFEST_INTEGRITY_FAILURE"); }
    }

    private ObjectNode page(SourceEvidenceIntakePageObjects.PageObject object, ObjectNode manifest, int number) {
        byte[] stored;
        try { stored = files.readPage(object.stored()); }
        catch (IOException failure) { throw conflict("E2A_PAGE_FILE_UNAVAILABLE"); }
        try {
            var parsed = SubmissionJson.parse(stored);
            require(parsed instanceof ObjectNode, "E2A_PAGE_ROOT_CONFLICT");
            var core = (ObjectNode) parsed;
            String hash = E2aProtocol.pageHash(core);
            JsonNode expected = manifest.path("pages").get(number - 1);
            require(Arrays.equals(stored, canonical(core)) && hash.equals(expected.path("pageHash").textValue())
                && number == core.path("pageNo").longValue()
                && manifest.path("pageCount").longValue() == core.path("pageCount").longValue()
                && manifest.path("manifestId").textValue().equals(core.path("manifestId").textValue())
                && manifest.path("manifestVersion").longValue() == core.path("manifestVersion").longValue()
                && manifest.path("snapshotId").textValue().equals(core.path("snapshotId").textValue())
                && previous(manifest, number).equals(core.path("prevPageHash")), "E2A_PAGE_ROOT_CONFLICT");
            return core;
        } catch (RuntimeException error) { throw conflict("E2A_PAGE_ROOT_CONFLICT"); }
    }

    private void compareItems(SourceEvidenceIntakePageObjects.Binding binding,
        SourceEvidenceIntakePageObjects.PageObject object, ObjectNode page, int number, long epoch,
        long auditThrough, List<SourceEvidenceIntakeAuditCoverage.Range> auditRanges,
        List<Long> captureSequences) {
        var stored = staged.existing(new SourceEvidenceIntakePageItems.PageContext(binding, number, object.id()));
        var claimed = E2aPageItem.parseAll(page.path("items"), page.path("byteCount").longValue());
        require(stored.size() == claimed.size(), "E2A_PAGE_STAGE_CONFLICT");
        for (int index = 0; index < claimed.size(); index++) {
            var item = claimed.get(index);
            var row = stored.get(index);
            require(row.ordinal() == index && row.kind().equals(item.itemKind())
                && row.recordId().equals(item.originalRecordId())
                && row.originalObjectId().equals(item.originalObjectId())
                && row.byteCount() == item.byteCount(), "E2A_PAGE_STAGE_CONFLICT");
            if ("CAPTURE".equals(item.itemKind())) captureSequences.add(item.originalDeviceSeq());
            var range = compareRaw(binding, item, row, epoch, auditThrough);
            if (range != null) auditRanges.add(range);
        }
    }

    private SourceEvidenceIntakeAuditCoverage.Range compareRaw(SourceEvidenceIntakePageObjects.Binding binding,
        E2aPageItem item, SourceEvidenceIntakePageItems.Indexed row, long epoch, long auditThrough) {
        var key = new SourceEvidenceIntakeRawObjects.Binding(binding.warehouseId(), binding.scopeId(),
            binding.submissionId(), binding.workRound(), binding.creatorDeviceId(),
            binding.originalOperatorId(), item.objectKind(), item.originalObjectId());
        var raw = rawObjects.findAfterSourceLock(key);
        require(raw != null && row.rawObjectId().equals(raw.id())
            && item.itemBytesSha256().equals(raw.stored().sha256())
            && item.byteCount() == raw.stored().bytes(), "E2A_RAW_OBJECT_CONFLICT");
        try {
            if("AUDIT".equals(item.itemKind())) {
                require(item.originalDeviceSeq()<=auditThrough,"E2A_AUDIT_ITEM_OUTSIDE_STOP");
                var original=objectFiles.read(raw.stored());
                audits.requireItem(item, original, binding.creatorDeviceId(), binding.warehouseId(), epoch);
                return new SourceEvidenceIntakeAuditCoverage.Range(
                    item.originalDeviceSeq(), item.originalDeviceSeq());
            } else if("ARCHIVE".equals(item.itemKind())) {
                var original=objectFiles.read(raw.stored());
                archives.requireItem(item, original, new SourceEvidenceIntakeAuditArchives.Context(
                    binding.creatorDeviceId(), binding.warehouseId(), epoch, auditThrough));
                var proof = SubmissionJson.parse(original);
                return new SourceEvidenceIntakeAuditCoverage.Range(
                    SubmissionJson.number(proof, "afterSeq") + 1, item.originalDeviceSeq());
            } else if ("CAPTURE".equals(item.itemKind()) || "SCAN".equals(item.itemKind())) {
                SourceEvidenceIntakeSourceRecord.require(item, objectFiles.read(raw.stored()));
                return null;
            } else {
                require(!"AUDIT_RECORDS".equals(item.objectKind())
                    && !"AUDIT_ARCHIVE".equals(item.objectKind()), "E2A_AUDIT_OBJECT_KIND_CONFLICT");
                objectFiles.verify(raw.stored());
                return null;
            }
        }
        catch (IOException failure) { throw conflict("E2A_RAW_OBJECT_FILE_UNAVAILABLE"); }
    }

    private static JsonNode previous(ObjectNode manifest, int number) {
        return number == 1 ? NullNode.instance
            : manifest.path("pages").get(number - 2).path("pageHash");
    }

    private static byte[] canonical(ObjectNode node) {
        return E2aProtocol.canonical(node.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static void require(boolean valid, String code) {
        if (!valid) throw conflict(code);
    }

    private static BusinessException conflict(String code) {
        return new BusinessException(409, code, "已存证据页与停止时的 manifest 不一致或不可核验");
    }

    public record Assessment(List<Long> missingPageNumbers, long receivedEntries, long receivedBytes,
        boolean auditCovered, boolean captureCovered) {
        public Assessment(List<Long> missingPageNumbers, long receivedEntries, long receivedBytes,
            boolean auditCovered) {
            this(missingPageNumbers, receivedEntries, receivedBytes, auditCovered, true);
        }
        public Assessment(List<Long> missingPageNumbers, long receivedEntries, long receivedBytes) {
            this(missingPageNumbers, receivedEntries, receivedBytes, false, true);
        }
        public Assessment { missingPageNumbers = List.copyOf(missingPageNumbers); }
    }
}
