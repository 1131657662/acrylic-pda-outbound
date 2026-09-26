package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.protocol.E2aPageItem;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SourceEvidenceIntakeSourceRecordTest {
    @Test void captureRawRecordMustMatchPageReference() {
        var capture = SubmissionJson.object().put("id", UUID.randomUUID().toString())
            .put("deviceCaptureSeq", "5").put("state", "COMMITTED");
        SourceEvidenceIntakeSourceRecord.require(item("CAPTURE", capture), bytes(capture));

        var unrelated = capture.deepCopy().put("id", UUID.randomUUID().toString());
        assertConflict(item("CAPTURE", capture), bytes(unrelated));
    }

    @Test void scanRawRecordMustMatchPageReference() {
        var scan = SubmissionJson.object().put("captureId", UUID.randomUUID().toString())
            .put("localSeq", "8").put("baleId", UUID.randomUUID().toString())
            .put("expectedDispatchCycle", "3").put("status", "ACTIVE");
        SourceEvidenceIntakeSourceRecord.require(item("SCAN", scan), bytes(scan));

        var changed = scan.deepCopy().put("status", "VOID");
        assertConflict(item("SCAN", scan), bytes(changed));
    }

    @Test void sourceRawRecordCannotUseUnrelatedObjectKind() {
        var capture = SubmissionJson.object().put("id", UUID.randomUUID().toString())
            .put("deviceCaptureSeq", "5").put("state", "COMMITTED");
        var item = item("CAPTURE", capture);
        var wrongKind = new E2aPageItem(item.itemKind(), item.originalRecordId(), "LOCAL_FROZEN_PAYLOAD",
            item.originalObjectId(), item.itemBytesSha256(), item.byteCount(), item.originalDeviceSeq(),
            item.recordSha256(), item.recordStatus(), null, null, null);

        assertConflict(wrongKind, bytes(capture));
    }

    private static E2aPageItem item(String kind, ObjectNode record) {
        boolean scan = kind.equals("SCAN");
        return new E2aPageItem(kind, SubmissionJson.uuid(record, scan ? "captureId" : "id"),
            "CAPTURE_RECORDS", UUID.randomUUID(), SubmissionJson.sha(record), bytes(record).length,
            SubmissionJson.number(record, scan ? "localSeq" : "deviceCaptureSeq"), SubmissionJson.sha(record),
            SubmissionJson.text(record, scan ? "status" : "state"),
            scan ? SubmissionJson.uuid(record, "baleId") : null,
            scan ? SubmissionJson.number(record, "expectedDispatchCycle") : null,
            scan ? SubmissionJson.uuid(record, "captureId") : null);
    }

    private static byte[] bytes(ObjectNode record) {
        return SubmissionJson.canonical(record).getBytes(StandardCharsets.UTF_8);
    }

    private static void assertConflict(E2aPageItem item, byte[] bytes) {
        assertThatThrownBy(() -> SourceEvidenceIntakeSourceRecord.require(item, bytes))
            .isInstanceOfSatisfying(BusinessException.class, error ->
                assertThat(error.code()).isEqualTo("E2A_SOURCE_RECORD_CONFLICT"));
    }
}
