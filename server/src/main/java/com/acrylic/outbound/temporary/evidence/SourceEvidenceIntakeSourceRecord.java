package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.protocol.E2aPageItem;

final class SourceEvidenceIntakeSourceRecord {
    private SourceEvidenceIntakeSourceRecord() { }

    static void require(E2aPageItem item, byte[] original) {
        if (!"CAPTURE_RECORDS".equals(item.objectKind())) throw conflict();
        try {
            var record = SubmissionJson.parse(original);
            if (!SubmissionJson.sha(record).equals(item.recordSha256())) throw conflict();
            if ("CAPTURE".equals(item.itemKind())) {
                if (!SubmissionJson.uuid(record, "id").equals(item.originalRecordId())
                    || SubmissionJson.number(record, "deviceCaptureSeq") != item.originalDeviceSeq()
                    || !SubmissionJson.text(record, "state").equals(item.recordStatus())) throw conflict();
            } else if ("SCAN".equals(item.itemKind())) {
                if (!SubmissionJson.uuid(record, "captureId").equals(item.originalRecordId())
                    || SubmissionJson.number(record, "localSeq") != item.originalDeviceSeq()
                    || !SubmissionJson.uuid(record, "baleId").equals(item.baleId())
                    || SubmissionJson.number(record, "expectedDispatchCycle") != item.originalCycle()
                    || !SubmissionJson.text(record, "status").equals(item.recordStatus())) throw conflict();
            } else throw conflict();
        } catch (RuntimeException invalid) { throw conflict(); }
    }

    private static BusinessException conflict() {
        return new BusinessException(409, "E2A_SOURCE_RECORD_CONFLICT", "原机逐包对象与页记录不一致");
    }
}
