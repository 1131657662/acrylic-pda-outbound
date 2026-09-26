package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.protocol.E2aPageItem;
import java.io.IOException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SourceEvidenceIntakeSetWriter {
    private static final String FIND = """
        SELECT id,stop_object_id,manifest_bytes,set_sha256,capture_high_watermark,audit_through_seq,item_count
        FROM source_evidence_set
        WHERE scope_id=? AND submission_id=? AND work_round=? FOR UPDATE
        """;
    private static final String INSERT_SET = """
        INSERT INTO source_evidence_set
        (id,warehouse_id,scope_id,submission_id,work_round,stop_object_id,manifest_bytes,set_sha256,
         capture_high_watermark,audit_through_seq,item_count,received_at)
        VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
        """;
    private static final String INSERT_ITEM = """
        INSERT INTO source_evidence_item
        (set_id,warehouse_id,scope_id,submission_id,work_round,item_kind,original_record_id,object_id,
         original_device_seq,record_sha256,bale_id,original_cycle,capture_id,record_status)
        VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
        """;
    private static final String MATCH_ITEM = """
        SELECT COUNT(*) FROM source_evidence_item
        WHERE set_id=? AND warehouse_id=? AND scope_id=? AND submission_id=? AND work_round=?
          AND item_kind=? AND original_record_id=? AND object_id=? AND original_device_seq=?
          AND record_sha256=? AND bale_id <=> ? AND original_cycle <=> ?
          AND capture_id <=> ? AND record_status=?
        """;
    private final JdbcTemplate jdbc;
    private final SourceEvidenceIntakePageClosure closure;
    private final SourceEvidenceIntakePageObjects pages;
    private final SourceEvidenceIntakePageItems staged;
    private final SourceEvidenceIntakeFiles pageFiles;
    private final SourceEvidenceIntakeBusinessFacts businessFacts;
    private final SourceEvidenceIntakeReservations reservations;

    public SourceEvidenceIntakeSetWriter(JdbcTemplate jdbc, SourceEvidenceIntakePageClosure closure,
        SourceEvidenceIntakePageObjects pages, SourceEvidenceIntakePageItems staged,
        SourceEvidenceIntakeFiles pageFiles, SourceEvidenceIntakeBusinessFacts businessFacts,
        SourceEvidenceIntakeReservations reservations) {
        this.jdbc = jdbc; this.closure = closure; this.pages = pages; this.staged = staged;
        this.pageFiles = pageFiles; this.businessFacts = businessFacts; this.reservations = reservations;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public SetLink createIfComplete(SourceEvidenceIntakeStop.Parsed stop,
        SourceEvidenceIntakePageObjects.Binding binding, UUID stopObjectId) {
        var assessment = closure.inspect(stop, binding);
        if (!assessment.missingPageNumbers().isEmpty() || !assessment.auditCovered()
            || !assessment.captureCovered()) return null;
        var existing = find(binding);
        var proof = SubmissionJson.parse(stop.canonicalStopProofBytes()).path("core");
        long capture = proof.path("captureHighWatermark").longValue();
        long audit = proof.path("auditThroughSeq").longValue();
        var items = readItems(binding, stop.manifestBytes());
        var facts = items.stream().map(Item::item).toList();
        if (existing != null) {
            verifyExisting(existing, stop, stopObjectId, assessment);
            for (var entry : items) verifyItem(existing.id(), binding, entry);
        }
        var ownScans = businessFacts.require(binding, facts, capture, existing == null ? null : existing.id(), audit);
        if (existing != null) {
            reservations.reserve(binding, existing.id(), ownScans);
            return new SetLink(existing.id(), existing.itemCount());
        }
        var setId = Ids.next();
        insertSet(setId, stop, binding, stopObjectId, capture, audit, assessment.receivedEntries());
        for (var entry : items) insertItem(setId, binding, entry.item(), entry.index());
        reservations.reserve(binding, setId, ownScans);
        return new SetLink(setId, assessment.receivedEntries());
    }

    private SetRow find(SourceEvidenceIntakePageObjects.Binding binding) {
        var rows = jdbc.queryForList(FIND, Ids.bytes(binding.scopeId()), Ids.bytes(binding.submissionId()),
            binding.workRound());
        if (rows.isEmpty()) return null;
        if (rows.size() != 1) throw conflict("E2A_SOURCE_SET_DUPLICATE");
        var row = rows.getFirst();
        return new SetRow(uuid(row, "id"), uuid(row, "stop_object_id"), manifest(row),
            text(row, "set_sha256"), number(row, "capture_high_watermark"), number(row, "audit_through_seq"),
            number(row, "item_count"));
    }

    private void verifyExisting(SetRow existing, SourceEvidenceIntakeStop.Parsed stop, UUID stopObjectId,
        SourceEvidenceIntakePageClosure.Assessment assessment) {
        var proof = SubmissionJson.parse(stop.canonicalStopProofBytes()).path("core");
        if (!existing.stopObjectId().equals(stopObjectId) || !Arrays.equals(existing.manifestBytes(), stop.manifestBytes())
            || !existing.setSha256().equals(stop.setHash()) || existing.captureHighWatermark() != proof.path("captureHighWatermark").longValue()
            || existing.auditThroughSeq() != proof.path("auditThroughSeq").longValue()
            || existing.itemCount() != assessment.receivedEntries() || itemCount(existing.id()) != existing.itemCount())
            throw conflict("E2A_SOURCE_SET_CONFLICT");
    }

    private void insertSet(UUID setId, SourceEvidenceIntakeStop.Parsed stop,
        SourceEvidenceIntakePageObjects.Binding binding, UUID stopObjectId, long capture, long audit, long items) {
        if (jdbc.update(INSERT_SET, Ids.bytes(setId), Ids.bytes(binding.warehouseId()), Ids.bytes(binding.scopeId()),
            Ids.bytes(binding.submissionId()), binding.workRound(), Ids.bytes(stopObjectId), stop.manifestBytes(),
            stop.setHash(), capture, audit, items, Timestamp.from(Instant.now())) != 1)
            throw new IllegalStateException("SOURCE_SET_INSERT_COUNT");
    }

    private List<Item> readItems(SourceEvidenceIntakePageObjects.Binding binding, byte[] manifestBytes) {
        var manifest = SubmissionJson.parse(manifestBytes);
        int pageCount = manifest.path("pageCount").intValue();
        var result = new java.util.ArrayList<Item>();
        for (int pageNo = 1; pageNo <= pageCount; pageNo++) result.addAll(readPageItems(binding, pageNo));
        return List.copyOf(result);
    }

    private List<Item> readPageItems(SourceEvidenceIntakePageObjects.Binding binding, int pageNo) {
        var page = pages.findAfterSourceLock(binding, pageNo);
        if (page == null) throw conflict("E2A_PAGE_INCOMPLETE");
        try {
            var core = SubmissionJson.parse(pageFiles.readPage(page.stored()));
            var items = E2aPageItem.parseAll(core.path("items"), core.path("byteCount").longValue());
            var indexed = staged.existing(new SourceEvidenceIntakePageItems.PageContext(binding, pageNo, page.id()));
            if (items.size() != indexed.size()) throw conflict("E2A_PAGE_STAGE_CONFLICT");
            var result = new java.util.ArrayList<Item>();
            for (int i = 0; i < items.size(); i++) result.add(new Item(items.get(i), indexed.get(i)));
            return List.copyOf(result);
        } catch (IOException failure) { throw conflict("E2A_PAGE_FILE_UNAVAILABLE"); }
    }

    private void insertItem(UUID setId, SourceEvidenceIntakePageObjects.Binding binding, E2aPageItem item,
        SourceEvidenceIntakePageItems.Indexed index) {
        if (!item.originalRecordId().equals(index.recordId()) || !item.originalObjectId().equals(index.originalObjectId())
            || !item.itemKind().equals(index.kind()) || item.byteCount() != index.byteCount())
            throw conflict("E2A_PAGE_STAGE_CONFLICT");
        if (jdbc.update(INSERT_ITEM, Ids.bytes(setId), Ids.bytes(binding.warehouseId()), Ids.bytes(binding.scopeId()),
            Ids.bytes(binding.submissionId()), binding.workRound(), item.itemKind(), Ids.bytes(item.originalRecordId()),
            Ids.bytes(index.rawObjectId()), item.originalDeviceSeq(), item.recordSha256(), optional(item.baleId()),
            item.originalCycle(), optional(item.captureId()), item.recordStatus()) != 1)
            throw new IllegalStateException("SOURCE_ITEM_INSERT_COUNT");
    }

    private void verifyItem(UUID setId, SourceEvidenceIntakePageObjects.Binding binding, Item entry) {
        var item = entry.item(); var index = entry.index();
        if (!item.originalRecordId().equals(index.recordId()) || !item.originalObjectId().equals(index.originalObjectId())
            || !item.itemKind().equals(index.kind()) || item.byteCount() != index.byteCount())
            throw conflict("E2A_SOURCE_ITEM_CONFLICT");
        Integer count = jdbc.queryForObject(MATCH_ITEM, Integer.class, Ids.bytes(setId), Ids.bytes(binding.warehouseId()),
            Ids.bytes(binding.scopeId()), Ids.bytes(binding.submissionId()), binding.workRound(), item.itemKind(),
            Ids.bytes(item.originalRecordId()), Ids.bytes(index.rawObjectId()), item.originalDeviceSeq(),
            item.recordSha256(), optional(item.baleId()), item.originalCycle(), optional(item.captureId()), item.recordStatus());
        if (count == null || count != 1) throw conflict("E2A_SOURCE_ITEM_CONFLICT");
    }

    private long itemCount(UUID setId) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM source_evidence_item WHERE set_id=?", Long.class,
            Ids.bytes(setId));
        return count == null ? -1 : count;
    }

    private static byte[] optional(UUID value) { return value == null ? null : Ids.bytes(value); }
    private static UUID uuid(java.util.Map<String, Object> row, String key) {
        var buffer = java.nio.ByteBuffer.wrap(bytes(row, key));
        return new UUID(buffer.getLong(), buffer.getLong());
    }
    private static byte[] bytes(java.util.Map<String, Object> row, String key) { if (!(row.get(key) instanceof byte[] value) || value.length != 16) throw conflict("E2A_SOURCE_SET_CORRUPT"); return value; }
    private static byte[] manifest(java.util.Map<String, Object> row) {
        if (!(row.get("manifest_bytes") instanceof byte[] value) || value.length < 1 || value.length > 262144)
            throw conflict("E2A_SOURCE_SET_CORRUPT");
        return value.clone();
    }
    private static String text(java.util.Map<String, Object> row, String key) { if (!(row.get(key) instanceof String value)) throw conflict("E2A_SOURCE_SET_CORRUPT"); return value; }
    private static long number(java.util.Map<String, Object> row, String key) { if (!(row.get(key) instanceof Number value)) throw conflict("E2A_SOURCE_SET_CORRUPT"); return value.longValue(); }
    private static BusinessException conflict(String code) { return new BusinessException(409, code, "原机证据集合不完整或已发生冲突"); }

    public record SetLink(UUID id, long itemCount) { }
    private record Item(E2aPageItem item, SourceEvidenceIntakePageItems.Indexed index) { }
    private record SetRow(UUID id, UUID stopObjectId, byte[] manifestBytes, String setSha256,
        long captureHighWatermark, long auditThroughSeq, long itemCount) {
        private SetRow { manifestBytes = manifestBytes.clone(); }
        @Override public byte[] manifestBytes() { return manifestBytes.clone(); }
    }
}
