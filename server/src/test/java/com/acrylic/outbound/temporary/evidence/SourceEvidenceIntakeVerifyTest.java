package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.submissions.SubmissionJson;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.node.ObjectNode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SourceEvidenceIntakeVerifyTest {
    private final UUID submission = UUID.randomUUID();
    private final UUID openKey = UUID.randomUUID(), stopKey = UUID.randomUUID();
    private SourceEvidenceIntakeOpen.Parsed open;
    private SourceEvidenceIntakeStop.Parsed stop;

    @BeforeEach void setUp() {
        open = SourceEvidenceIntakeOpen.parse(bytes(SubmissionJson.object().put("operation", "OPEN")
            .put("manifestId", "manifest").put("manifestVersion", 2).put("snapshotId", "snapshot")
            .put("source", "TEMP").put("sourceId", UUID.randomUUID().toString())
            .put("submissionId", submission.toString()).put("workRound", 3)), submission, 3);
        stop = new SourceEvidenceIntakeStop.Parsed(bytes(SubmissionJson.object()), "a".repeat(64),
            bytes(SubmissionJson.object()), "m".repeat(64), "s".repeat(64), bytes(SubmissionJson.object()), openKey, "stop");
    }

    @Test void acceptsTheStrictVerificationEnvelope() {
        var parsed = SourceEvidenceIntakeVerify.parse(bytes(request()), openKey, stopKey, open, stop);

        assertThat(parsed.verificationVersion()).isEqualTo(1);
        assertThat(parsed.algorithmVersion()).isEqualTo(1);
        assertThat(parsed.setHash()).isEqualTo(stop.setHash());
    }

    @Test void rejectsUnknownFieldsAndWrongSetHash() {
        var extra = request(); extra.put("unexpected", true);
        assertThatThrownBy(() -> SourceEvidenceIntakeVerify.parse(bytes(extra), openKey, stopKey, open, stop))
            .isInstanceOf(IllegalArgumentException.class);
        var wrong = request().put("setHash", "f".repeat(64));
        assertThatThrownBy(() -> SourceEvidenceIntakeVerify.parse(bytes(wrong), openKey, stopKey, open, stop))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private ObjectNode request() {
        return SubmissionJson.object().put("operation", "VERIFY").put("openOriginalKey", openKey.toString())
            .put("stopOriginalKey", stopKey.toString()).put("manifestId", open.manifestId())
            .put("manifestVersion", open.manifestVersion()).put("setHash", stop.setHash())
            .put("verificationVersion", 1).put("algorithmVersion", 1);
    }

    private static byte[] bytes(ObjectNode value) { return value.toString().getBytes(StandardCharsets.UTF_8); }
}
