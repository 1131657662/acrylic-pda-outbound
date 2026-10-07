package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class HandoverTemporaryStopCommandTest {
    private final UUID kase = UUID.randomUUID();

    @Test void temporaryProofBindsItsOwnSourceAndDoesNotInventRecallOrPackage() {
        var body = body(); var command = parse(body);
        assertThat(command.temporary().evidenceSet().toString()).isEqualTo(body.path("sourceEvidenceSetId").asText());
        assertThat(command.temporary().verification().toString()).isEqualTo(body.path("verificationId").asText());
        assertThat(command.recallId()).isNull(); assertThat(command.packageId()).isNull();
        assertThat(parse(body.deepCopy().put("verificationId", UUID.randomUUID().toString())).digest()).isNotEqualTo(command.digest());
        assertThat(HandoverStopCommand.parse(kase, "2", HandoverReceiptCodec.bytes(body)).digest()).isNotEqualTo(command.digest());
    }

    @ParameterizedTest @ValueSource(strings={"sourceKind", "sourceEvidenceSetId", "verificationId", "setSha256", "captureHighWatermark"})
    void missingTemporaryProofCannotBeInterpretedAsFormal(String field) {
        var body = body(); body.remove(field);
        assertThatThrownBy(() -> parse(body)).isInstanceOf(BusinessException.class);
    }

    @Test void mixedSourceFieldsAndWrongSourceKindAreRejected() {
        assertThatThrownBy(() -> parse(body().put("changeRequestId", UUID.randomUUID().toString()))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> parse(body().put("sourceKind", "FORMAL"))).isInstanceOf(BusinessException.class);
    }

    private HandoverStopCommand parse(ObjectNode body) { return HandoverStopCommand.parse(kase, "1", HandoverReceiptCodec.bytes(body)); }
    private ObjectNode body() {
        return SubmissionJson.object().put("commandId", UUID.randomUUID().toString()).put("expectedCaseVersion", "1")
            .put("sourceKind", "TEMP").put("sourceEvidenceSetId", UUID.randomUUID().toString())
            .put("verificationId", UUID.randomUUID().toString()).put("setSha256", "a".repeat(64))
            .put("captureHighWatermark", "2").put("auditThroughSeq", "8");
    }
}
