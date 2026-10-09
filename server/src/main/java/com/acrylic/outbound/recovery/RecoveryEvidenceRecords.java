package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.protocol.E2aPageItem;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Reads every native page object; ARCHIVE references require a separate supported prefix path. */
final class RecoveryEvidenceRecords {
    private RecoveryEvidenceRecords() { }

    static Records read(List<E2aPageItem> items, Map<String, byte[]> originals) {
        var captures = new ArrayList<JsonNode>(); var scans = new ArrayList<JsonNode>(); var audits = new ArrayList<JsonNode>();
        JsonNode frozen = null; var seen = new HashSet<String>();
        for (var item : items) {
            var value = original(item, originals); seen.add(item.itemBytesSha256());
            switch (item.itemKind()) {
                case "CAPTURE" -> { capture(item, value); captures.add(value); }
                case "SCAN" -> { scan(item, value); scans.add(value); }
                case "AUDIT" -> { audit(item, value); audits.add(value); }
                case "STOP" -> {
                    require(frozen == null && item.objectKind().equals("LOCAL_FROZEN_PAYLOAD")
                        && item.recordStatus().equals("FROZEN") && uuid(value, "submissionId").equals(item.originalRecordId()),
                        "RECOVERY_E2A_FROZEN");
                    frozen = value;
                }
                default -> throw new RecoveryControlException("RECOVERY_E2A_ARCHIVE_UNSUPPORTED");
            }
        }
        require(frozen != null && seen.equals(originals.keySet()), "RECOVERY_E2A_OBJECT_SET");
        return new Records(List.copyOf(captures), List.copyOf(scans), List.copyOf(audits), frozen, items);
    }

    private static JsonNode original(E2aPageItem item, Map<String, byte[]> originals) {
        byte[] bytes = originals.get(item.itemBytesSha256());
        require(bytes != null && bytes.length == item.byteCount() && sha256(bytes).equals(item.itemBytesSha256()),
            "RECOVERY_E2A_OBJECT_MISMATCH");
        var value = SubmissionJson.parse(bytes);
        require(Arrays.equals(bytes, SubmissionJson.canonical(value).getBytes(StandardCharsets.UTF_8))
            && SubmissionJson.sha(value).equals(item.recordSha256()), "RECOVERY_E2A_RECORD_MISMATCH");
        return value;
    }

    private static void capture(E2aPageItem item, JsonNode value) {
        require(item.objectKind().equals("CAPTURE_RECORDS") && uuid(value, "id").equals(item.originalRecordId())
            && revision(value, "deviceCaptureSeq") == item.originalDeviceSeq() && text(value, "state").equals(item.recordStatus()),
            "RECOVERY_E2A_CAPTURE_RECORD");
    }

    private static void scan(E2aPageItem item, JsonNode value) {
        require(item.objectKind().equals("CAPTURE_RECORDS") && uuid(value, "captureId").equals(item.originalRecordId())
            && uuid(value, "captureId").equals(item.captureId()) && uuid(value, "baleId").equals(item.baleId())
            && revision(value, "expectedDispatchCycle") == item.originalCycle()
            && revision(value, "localSeq") == item.originalDeviceSeq() && text(value, "status").equals(item.recordStatus()),
            "RECOVERY_E2A_SCAN_RECORD");
    }

    private static void audit(E2aPageItem item, JsonNode value) {
        require(item.objectKind().equals("AUDIT_RECORDS") && uuid(value, "id").equals(item.originalRecordId())
            && revision(value, "localSeq") == item.originalDeviceSeq() && text(value, "type").equals(item.recordStatus()),
            "RECOVERY_E2A_AUDIT_RECORD");
    }

    record Records(List<JsonNode> captures, List<JsonNode> scans, List<JsonNode> audits, JsonNode frozen, List<E2aPageItem> items) { }
}
