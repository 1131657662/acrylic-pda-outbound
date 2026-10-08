package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.submissions.*;
import com.acrylic.outbound.temporary.continuation.TemporaryRoundControl;
import com.acrylic.outbound.temporary.continuation.TemporaryRoundFacts;
import com.acrylic.outbound.temporary.continuation.TemporaryRoundPredecessor;
import com.acrylic.outbound.temporary.evidence.*;
import com.acrylic.outbound.temporary.protocol.E2aPageItem;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Reads the immutable frozen TEMP body and binds every capture and scan to the revalidated manifest pages. */
@Component
final class HandoverTemporaryPayload {
    private final JdbcTemplate jdbc;
    private final SubmissionFiles submissions;
    private final SourceEvidenceIntakeObjectFiles objects;
    private final SourceEvidenceIntakePageObjects pages;
    private final SourceEvidenceIntakeFiles pageFiles;
    private final TemporaryRoundFacts continuations;
    private final TemporaryRoundPredecessor continuationOriginals;

    HandoverTemporaryPayload(JdbcTemplate jdbc, SubmissionFiles submissions, SourceEvidenceIntakeObjectFiles objects,
            SourceEvidenceIntakePageObjects pages, SourceEvidenceIntakeFiles pageFiles,
            TemporaryRoundFacts continuations, TemporaryRoundPredecessor continuationOriginals) {
        this.jdbc = jdbc; this.submissions = submissions; this.objects = objects; this.pages = pages; this.pageFiles = pageFiles;
        this.continuations = continuations; this.continuationOriginals = continuationOriginals;
    }

    JsonNode read(HandoverTemporaryRoots.Verified roots, HandoverStopStore.Origin origin) {
        var items = items(roots); var body = parse(original(origin, items));
        var control = origin.round() == 1 ? SubmissionControl.read(origin.submission(), body)
            : body.has("temporaryHandover") ? TemporaryHandoverControl.read(body).control() : TemporaryRoundControl.read(body).control();
        if (!uuid(body, "submissionId").equals(origin.submission()) || control.temporary() == null
                || !control.id().equals(origin.submission()) || control.round() != origin.round()
                || !control.device().equals(origin.device()) || !control.epoch().equals(origin.epoch())
                || !control.temporary().authorization().equals(origin.temporary().authorization())
                || !control.temporary().creator().equals(origin.temporary().creator())
                || !control.temporary().client().equals(origin.temporary().client())) throw HandoverTemporaryRoots.changed();
        var predecessor = predecessor(body, origin);
        if (predecessor != null) prefix(body, predecessor);
        records(items, array(body, "captures"), "CAPTURE", "id", predecessor);
        records(items, array(body, "scans"), "SCAN", "captureId", predecessor);
        return body;
    }

    private JsonNode predecessor(JsonNode body, HandoverStopStore.Origin origin) {
        if (body.has("temporaryContinuation")) {
            var request = TemporaryRoundControl.read(body);
            var source = continuations.read(request, new SubmissionAccess.Actor(origin.operator(), origin.warehouse(), ""));
            return continuationOriginals.read(request, source);
        }
        if (!body.has("temporaryHandover")) return null;
        var reference = body.path("temporaryHandover");
        var id = uuid(reference, "predecessorSubmissionId");
        var hashes = jdbc.queryForList("SELECT payload_sha256 FROM submission_payload WHERE submission_id=?",
            String.class, Ids.bytes(id));
        String expected = text(reference, "predecessorPayloadSha256");
        if (hashes.size() > 1 || hashes.size() == 1 && !hashes.getFirst().equals(expected)) throw HandoverTemporaryRoots.changed();
        try { return parse(canonical(hashes.isEmpty() ? localPredecessor(reference) : submissions.read(expected), expected)); }
        catch (IOException | java.io.UncheckedIOException unavailable) { throw HandoverTemporaryRoots.changed(); }
    }

    private byte[] localPredecessor(JsonNode reference) throws IOException {
        var files = jdbc.query("""
            SELECT o.file_key,o.bytes_sha256,o.byte_count
            FROM temporary_handover_activation_ticket t
            JOIN handover_case h ON h.id=t.case_id AND h.scope_id=t.scope_id AND h.status='ACTIVATED' AND h.source_kind='TEMP'
            JOIN submission s ON s.id=t.submission_id AND s.scope_id=t.scope_id AND s.work_round=t.from_work_round
            JOIN source_evidence_item i ON i.set_id=t.source_evidence_set_id AND i.scope_id=t.scope_id
              AND i.submission_id=s.id AND i.work_round=s.work_round AND i.warehouse_id=h.warehouse_id
            JOIN source_evidence_object o ON o.id=i.object_id AND o.scope_id=i.scope_id
              AND o.submission_id=i.submission_id AND o.work_round=i.work_round AND o.warehouse_id=i.warehouse_id
            WHERE t.id=? AND t.case_id=? AND t.submission_id=? AND t.payload_sha256=?
              AND i.item_kind='STOP' AND i.record_status='FROZEN' AND i.original_record_id=s.id
              AND o.kind='LOCAL_FROZEN_PAYLOAD' AND o.origin_device_id=s.freeze_device_id AND o.uploaded_by=s.operator_id
              AND i.record_sha256=o.bytes_sha256 AND o.bytes_sha256=t.payload_sha256
            """, (rs,n)->new SourceEvidenceIntakeObjectFiles.Stored(rs.getString(1),rs.getString(2),rs.getInt(3)),
            Ids.bytes(uuid(reference,"ticketId")),Ids.bytes(uuid(reference,"caseId")),
            Ids.bytes(uuid(reference,"predecessorSubmissionId")),text(reference,"predecessorPayloadSha256"));
        if (files.size()!=1) throw HandoverTemporaryRoots.changed();
        return objects.read(files.getFirst());
    }

    private void prefix(JsonNode body, JsonNode predecessor) {
        for (String field : List.of("captures", "scans", "events")) {
            var old = array(predecessor, field); var current = array(body, field);
            if (current.size() < old.size()) throw HandoverTemporaryRoots.changed();
            for (int index = 0; index < old.size(); index++)
                if (!old.get(index).equals(current.get(index))) throw HandoverTemporaryRoots.changed();
        }
    }

    private byte[] original(HandoverStopStore.Origin origin, List<E2aPageItem> items) {
        var hashes = jdbc.queryForList("SELECT payload_sha256 FROM submission_payload WHERE submission_id=?", String.class,
            Ids.bytes(origin.submission()));
        try {
            if (hashes.size() == 1) return canonical(submissions.read(hashes.getFirst()), hashes.getFirst());
            if (!hashes.isEmpty()) throw HandoverTemporaryRoots.changed();
            var frozen = items.stream().filter(i -> i.itemKind().equals("STOP") && i.objectKind().equals("LOCAL_FROZEN_PAYLOAD")
                && i.originalRecordId().equals(origin.submission()) && i.recordStatus().equals("FROZEN")).toList();
            if (frozen.size() != 1) throw HandoverTemporaryRoots.changed();
            var item = frozen.getFirst();
            var files = jdbc.query("""
                SELECT file_key,bytes_sha256,byte_count FROM source_evidence_object
                WHERE scope_id=? AND submission_id=? AND work_round=? AND warehouse_id=? AND origin_device_id=?
                  AND uploaded_by=? AND kind='LOCAL_FROZEN_PAYLOAD' AND original_object_id=? AND bytes_sha256=?
                """, (rs, n) -> new SourceEvidenceIntakeObjectFiles.Stored(rs.getString(1), rs.getString(2), rs.getInt(3)),
                Ids.bytes(origin.scope()), Ids.bytes(origin.submission()), origin.round(), Ids.bytes(origin.warehouse()),
                Ids.bytes(origin.device()), Ids.bytes(origin.operator()), Ids.bytes(item.originalObjectId()), item.itemBytesSha256());
            if (files.size() != 1 || files.getFirst().bytes() != item.byteCount()) throw HandoverTemporaryRoots.changed();
            return canonical(objects.read(files.getFirst()), item.recordSha256());
        } catch (IOException | java.io.UncheckedIOException unavailable) {
            throw new BusinessException(409, "HANDOVER_STOP_FILE_UNAVAILABLE", "原临时冻结正文不可读取或摘要不符");
        }
    }

    private byte[] canonical(byte[] bytes, String expected) {
        var body = parse(bytes);
        if (!sha(body).equals(expected) || !Arrays.equals(bytes, com.acrylic.outbound.submissions.SubmissionJson.canonical(body)
                .getBytes(StandardCharsets.UTF_8))) throw HandoverTemporaryRoots.changed();
        return bytes;
    }

    private List<E2aPageItem> items(HandoverTemporaryRoots.Verified roots) {
        var result = new ArrayList<E2aPageItem>();
        for (int number = 1; number <= roots.manifest().path("pageCount").intValue(); number++) {
            var page = pages.findAfterSourceLock(roots.binding(), number);
            if (page == null) throw HandoverTemporaryRoots.changed();
            try {
                var body = parse(pageFiles.readPage(page.stored()));
                result.addAll(E2aPageItem.parseAll(body.path("items"), body.path("byteCount").longValue()));
            } catch (IOException unavailable) { throw HandoverTemporaryRoots.changed(); }
        }
        return List.copyOf(result);
    }

    private void records(List<E2aPageItem> items, JsonNode records, String kind, String field, JsonNode predecessor) {
        var expected = new HashMap<UUID, String>();
        for (var item : items) if (kind.equals(item.itemKind())) {
            if (expected.put(item.originalRecordId(), item.recordSha256()) != null) throw HandoverTemporaryRoots.changed();
        }
        // The exact predecessor prefix is independently replayed; the new device's manifest covers its suffix.
        var seen = new HashSet<UUID>();
        int index = 0;
        int inherited = predecessor == null ? 0 : array(predecessor, "CAPTURE".equals(kind) ? "captures" : "scans").size();
        for (var row : records) {
            UUID id = uuid(row, field);
            if (!seen.add(id) || index++ >= inherited && !sha(row).equals(expected.get(id)))
                throw HandoverTemporaryRoots.changed();
        }
    }
}
