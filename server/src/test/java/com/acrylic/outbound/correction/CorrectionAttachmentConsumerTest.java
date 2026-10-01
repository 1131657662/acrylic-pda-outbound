package com.acrylic.outbound.correction;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CorrectionAttachmentConsumerTest {
    private final UUID warehouse = UUID.randomUUID(), user = UUID.randomUUID(), attachment = UUID.randomUUID();
    private final byte[] bytes = new byte[]{1, 2, 3};
    private final CorrectionAttachmentStore store = mock(CorrectionAttachmentStore.class);
    private final CorrectionAttachmentConsumer consumer = new CorrectionAttachmentConsumer(store);

    @Test void consumesMatchingPhysicalEvidence() {
        stored(bytes, CorrectionCommand.sha(bytes));
        assertThat(consumer.validate(write(line(), "PHYSICAL_RETURN")))
            .containsExactly(new CorrectionAttachmentStore.Use(1, attachment));
    }

    @Test void rejectsSameHashWithDifferentEvidenceId() {
        stored(bytes, CorrectionCommand.sha(bytes));
        fails(line().put("evidenceId", UUID.randomUUID().toString()), "CORRECTION_EVIDENCE_REQUIRED");
    }

    @Test void rejectsDeclaredSizeThatUnderstatesActualBytes() {
        stored(bytes, CorrectionCommand.sha(bytes));
        var line = line(); ((ObjectNode) line.path("attachments").get(0)).put("sizeBytes", "1");
        fails(line, "INVALID_CORRECTION_ATTACHMENT");
    }

    @Test void rejectsCorruptedStoredBytes() {
        stored(bytes, "0".repeat(64));
        fails(line(), "ATTACHMENT_HASH_MISMATCH");
    }

    @Test void rejectsMissingControlledId() {
        var line = line(); ((ObjectNode) line.path("attachments").get(0)).remove("attachmentId");
        fails(line, "INVALID_CORRECTION_ATTACHMENT");
    }

    @Test void rejectsMetadataOnlyPhysicalEvidence() {
        var line = line(); line.remove("attachments");
        fails(line, "CORRECTION_EVIDENCE_REQUIRED");
    }

    @Test void rejectsDuplicateReferenceInOneLine() {
        stored(bytes, CorrectionCommand.sha(bytes));
        var line = line(); line.withArray("attachments").add(line.path("attachments").get(0).deepCopy());
        fails(line, "INVALID_CORRECTION_ATTACHMENT");
    }

    @Test void countsEachLineReferenceTowardCommandCapacity() {
        byte[] large = new byte[CorrectionAttachmentBytes.MAX_BYTES]; stored(large, CorrectionCommand.sha(large));
        var body = body("WEIGHT_CORRECTION");
        for (int i = 0; i < 6; i++) {
            var line = line().put("evidenceSha256", CorrectionCommand.sha(large));
            ((ObjectNode) line.path("attachments").get(0)).put("evidenceSha256", CorrectionCommand.sha(large))
                .put("sizeBytes", Integer.toString(large.length));
            body.withArray("lines").add(line);
        }
        assertThatThrownBy(() -> consumer.validate(write(body)))
            .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo("ATTACHMENT_LIMIT_EXCEEDED"));
    }

    private void fails(ObjectNode line, String code) {
        assertThatThrownBy(() -> consumer.validate(write(line, "PHYSICAL_RETURN")))
            .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo(code));
    }

    private void stored(byte[] value, String sha) {
        var owner = new CorrectionAttachmentStore.Owner(warehouse, user);
        when(store.owned(owner, attachment)).thenReturn(new CorrectionAttachmentStore.Stored(attachment, owner, sha, value.length, value));
    }

    private ObjectNode line() {
        var line = SubmissionJson.object().put("dispatchId", UUID.randomUUID().toString())
            .put("evidenceId", attachment.toString()).put("evidenceSha256", CorrectionCommand.sha(bytes));
        line.putArray("attachments").addObject().put("attachmentId", attachment.toString())
            .put("evidenceSha256", CorrectionCommand.sha(bytes)).put("sizeBytes", "3");
        return line;
    }

    private ObjectNode body(String type) {
        return SubmissionJson.object().put("commandType", "CREATE").put("type", type);
    }

    private CorrectionCommand.Write write(ObjectNode line, String type) {
        var body = body(type); body.putArray("lines").add(line); return write(body);
    }

    private CorrectionCommand.Write write(ObjectNode body) {
        var actor = SubmissionJson.object().put("warehouseId", warehouse.toString()).put("principalId", user.toString());
        return new CorrectionCommand.Write(null, null, null, null, null, Instant.now(),
            new CorrectionCommand.Input(SubmissionJson.canonical(body).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
            SubmissionJson.object(), actor);
    }
}
