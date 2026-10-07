package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.submissions.*;
import com.acrylic.outbound.temporary.continuation.TemporaryRoundControl;
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

    HandoverTemporaryPayload(JdbcTemplate jdbc, SubmissionFiles submissions, SourceEvidenceIntakeObjectFiles objects,
            SourceEvidenceIntakePageObjects pages, SourceEvidenceIntakeFiles pageFiles) {
        this.jdbc = jdbc; this.submissions = submissions; this.objects = objects; this.pages = pages; this.pageFiles = pageFiles;
    }

    JsonNode read(HandoverTemporaryRoots.Verified roots, HandoverStopStore.Origin origin) {
        var items = items(roots); var body = parse(original(origin, items));
        var control = origin.round() == 1 ? SubmissionControl.read(origin.submission(), body) : TemporaryRoundControl.read(body).control();
        if (!uuid(body, "submissionId").equals(origin.submission()) || control.temporary() == null
                || !control.id().equals(origin.submission()) || control.round() != origin.round()
                || !control.device().equals(origin.device()) || !control.epoch().equals(origin.epoch())
                || !control.temporary().authorization().equals(origin.temporary().authorization())
                || !control.temporary().creator().equals(origin.temporary().creator())
                || !control.temporary().client().equals(origin.temporary().client())) throw HandoverTemporaryRoots.changed();
        records(items, array(body, "captures"), "CAPTURE", "id");
        records(items, array(body, "scans"), "SCAN", "captureId");
        return body;
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

    private void records(List<E2aPageItem> items, JsonNode records, String kind, String field) {
        var expected = new HashMap<UUID, String>();
        for (var item : items) if (kind.equals(item.itemKind())) {
            if (expected.put(item.originalRecordId(), item.recordSha256()) != null) throw HandoverTemporaryRoots.changed();
        }
        // An E2A manifest may also contain other sources. Every record of this body must be covered exactly once.
        var seen = new HashSet<UUID>();
        for (var row : records) if (!seen.add(uuid(row, field)) || !sha(row).equals(expected.get(uuid(row, field))))
            throw HandoverTemporaryRoots.changed();
    }
}
