package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.protocol.E2aPageItem;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.jdbc.core.JdbcTemplate;

final class SourceEvidenceIntakeOtherFacts {
    private static final String CAPTURE = """
        SELECT BIN_TO_UUID(c.submission_id) FROM submitted_capture c
        JOIN submission s ON s.id=c.submission_id JOIN submission_scope scope ON scope.id=s.scope_id
        WHERE c.id=? AND c.origin_device_id=? AND c.device_capture_seq=? AND c.record_sha256=?
          AND scope.warehouse_id=? AND c.submission_id<>?
        """;
    private final JdbcTemplate jdbc;
    private final Function<UUID, JsonNode> payload;
    private final UnsubmittedSourceFacts unsubmitted;

    SourceEvidenceIntakeOtherFacts(JdbcTemplate jdbc, Function<UUID, JsonNode> payload, UnsubmittedSourceFacts unsubmitted) {
        this.jdbc = jdbc;
        this.payload = payload;
        this.unsubmitted = unsubmitted;
    }

    void require(SourceEvidenceIntakePageObjects.Binding binding, List<E2aPageItem> items, long auditThrough) {
        var missing = new java.util.ArrayList<E2aPageItem>();
        var captures = requireCaptures(binding, items, missing);
        if (!missing.isEmpty()) {
            var ids = missing.stream().map(E2aPageItem::originalRecordId).collect(java.util.stream.Collectors.toSet());
            missing.addAll(items.stream().filter(item -> "SCAN".equals(item.itemKind()) && ids.contains(item.captureId())).toList());
            var scans = unsubmitted.validate(binding, missing, auditThrough);
            for (var id : ids) captures.put(id, scans.get(id));
        }
        requireScans(captures, items);
    }

    private Map<UUID, JsonNode> requireCaptures(SourceEvidenceIntakePageObjects.Binding binding,
        List<E2aPageItem> items, List<E2aPageItem> missing) {
        var captures = new HashMap<UUID, JsonNode>();
        var payloads = new HashMap<UUID, JsonNode>();
        var ids = new HashSet<UUID>();
        for (var item : items) {
            if (!"CAPTURE".equals(item.itemKind())) continue;
            require(ids.add(item.originalRecordId()), "E2A_OTHER_CAPTURE_DUPLICATE");
            UUID submission = storedSubmission(binding, item);
            if (submission == null) { missing.add(item); continue; }
            var body = payloads.computeIfAbsent(submission, payload);
            requireCapture(binding, item, find(body, "captures", "id", item.originalRecordId()));
            require(!captures.containsKey(item.originalRecordId()), "E2A_OTHER_CAPTURE_DUPLICATE");
            captures.put(item.originalRecordId(), find(body, "scans", "captureId", item.originalRecordId()));
        }
        return captures;
    }

    private UUID storedSubmission(SourceEvidenceIntakePageObjects.Binding binding, E2aPageItem item) {
        var rows = jdbc.queryForList(CAPTURE, String.class, Ids.bytes(item.originalRecordId()),
            Ids.bytes(binding.creatorDeviceId()), item.originalDeviceSeq(), item.recordSha256(),
            Ids.bytes(binding.warehouseId()), Ids.bytes(binding.submissionId()));
        if (rows.isEmpty()) {
            var owners = jdbc.queryForList("""
                SELECT BIN_TO_UUID(submission_id) FROM submitted_capture
                WHERE id=? OR (origin_device_id=? AND device_capture_seq=?)
                """, String.class, Ids.bytes(item.originalRecordId()), Ids.bytes(binding.creatorDeviceId()), item.originalDeviceSeq());
            require(owners.isEmpty() && unsubmitted != null, "E2A_OTHER_CAPTURE_NOT_STORED");
            return null;
        }
        require(rows.size() == 1, "E2A_OTHER_CAPTURE_NOT_STORED");
        UUID submission = UUID.fromString(rows.getFirst());
        require(!submission.equals(binding.submissionId()), "E2A_OTHER_CAPTURE_SOURCE_CONFLICT");
        return submission;
    }

    private static void requireCapture(SourceEvidenceIntakePageObjects.Binding binding, E2aPageItem item,
        JsonNode original) {
        require(original != null && SubmissionJson.sha(original).equals(item.recordSha256())
            && SubmissionJson.uuid(original, "originDeviceId").equals(binding.creatorDeviceId())
            && SubmissionJson.number(original, "deviceCaptureSeq") == item.originalDeviceSeq()
            && SubmissionJson.text(original, "state").equals(item.recordStatus()), "E2A_OTHER_CAPTURE_FACT_CONFLICT");
    }

    private static void requireScans(Map<UUID, JsonNode> captures, List<E2aPageItem> items) {
        var seen = new HashSet<UUID>();
        for (var item : items) {
            if (!"SCAN".equals(item.itemKind())) continue;
            require(item.originalRecordId().equals(item.captureId()), "E2A_OTHER_SCAN_ID_CONFLICT");
            require("ACTIVE".equals(item.recordStatus()) || "VOID".equals(item.recordStatus()),
                "E2A_OTHER_SCAN_STATUS_CONFLICT");
            require(captures.containsKey(item.captureId()), "E2A_OTHER_SCAN_CAPTURE_MISSING");
            require(seen.add(item.captureId()), "E2A_OTHER_SCAN_FACT_DUPLICATE");
            requireScan(item, captures.get(item.captureId()));
        }
        long expected = captures.values().stream().filter(java.util.Objects::nonNull).count();
        require(seen.size() == expected, "E2A_OTHER_SCAN_FACT_COUNT_CONFLICT");
    }

    private static void requireScan(E2aPageItem item, JsonNode original) {
        require(original != null && SubmissionJson.sha(original).equals(item.recordSha256())
            && SubmissionJson.number(original, "localSeq") == item.originalDeviceSeq()
            && SubmissionJson.uuid(original, "baleId").equals(item.baleId())
            && SubmissionJson.number(original, "expectedDispatchCycle") == item.originalCycle()
            && SubmissionJson.text(original, "status").equals(item.recordStatus()), "E2A_OTHER_SCAN_FACT_CONFLICT");
    }

    private static JsonNode find(JsonNode payload, String array, String id, UUID wanted) {
        if (payload == null) return null;
        JsonNode result = null;
        for (var row : SubmissionJson.array(payload, array)) {
            if (!SubmissionJson.uuid(row, id).equals(wanted)) continue;
            require(result == null, "E2A_OTHER_FACT_DUPLICATE");
            result = row;
        }
        return result;
    }

    private static void require(boolean value, String code) {
        if (!value) throw new BusinessException(409, code, "其他来源原机事实不可核验");
    }
}
