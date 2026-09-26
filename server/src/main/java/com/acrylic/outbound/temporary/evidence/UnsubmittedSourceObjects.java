package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.protocol.E2aPageItem;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.springframework.stereotype.Component;

/** Reads original bytes already stored under the current evidence source lock. */
@Component
public class UnsubmittedSourceObjects {
    private final SourceEvidenceIntakeRawObjects objects;
    private final SourceEvidenceIntakeObjectFiles files;

    public UnsubmittedSourceObjects(SourceEvidenceIntakeRawObjects objects, SourceEvidenceIntakeObjectFiles files) {
        this.objects = objects; this.files = files;
    }

    JsonNode read(SourceEvidenceIntakePageObjects.Binding binding, E2aPageItem item) {
        var key = new SourceEvidenceIntakeRawObjects.Binding(binding.warehouseId(), binding.scopeId(),
            binding.submissionId(), binding.workRound(), binding.creatorDeviceId(), binding.originalOperatorId(),
            item.objectKind(), item.originalObjectId());
        var object = objects.findAfterSourceLock(key);
        require(object != null && item.itemBytesSha256().equals(object.stored().sha256())
            && item.byteCount() == object.stored().bytes());
        try {
            byte[] bytes = files.read(object.stored());
            SourceEvidenceIntakeSourceRecord.require(item, bytes);
            var record = SubmissionJson.parse(bytes);
            require(Arrays.equals(bytes, SubmissionJson.canonical(record).getBytes(StandardCharsets.UTF_8)));
            return record;
        } catch (java.io.IOException failure) { throw conflict(); }
    }

    private static void require(boolean valid) { if (!valid) throw conflict(); }
    private static BusinessException conflict() {
        return new BusinessException(409, "E2A_OTHER_ORIGINAL_UNAVAILABLE", "其他未提交来源原件缺失或已损坏");
    }
}
