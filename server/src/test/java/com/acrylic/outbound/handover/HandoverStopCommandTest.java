package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class HandoverStopCommandTest {
    private final UUID kase = UUID.randomUUID();

    @Test void fieldOrderAndWhitespaceDoNotChangeIntentButRouteAndEpochDo() {
        var body = body(); var command = parse(body);
        var pretty = HandoverStopCommand.parse(kase, "1", body.toPrettyString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(pretty.digest()).isEqualTo(command.digest());
        assertThat(HandoverStopCommand.parse(UUID.randomUUID(), "1", HandoverReceiptCodec.bytes(body)).digest()).isNotEqualTo(command.digest());
        assertThat(HandoverStopCommand.parse(kase, "2", HandoverReceiptCodec.bytes(body)).digest()).isNotEqualTo(command.digest());
    }

    @ParameterizedTest @ValueSource(strings={"extra", "missing", "number", "negative", "leadingZero", "uuid", "hash", "zeroVersion"})
    void rejectsAmbiguousOrIncompleteCommands(String damage) {
        var body = body();
        switch (damage) {
            case "extra" -> body.put("mayResumeScanning", true);
            case "missing" -> body.remove("auditThroughSeq");
            case "number" -> body.put("auditThroughSeq", 3);
            case "negative" -> body.put("captureHighWatermark", "-1");
            case "leadingZero" -> body.put("auditThroughSeq", "01");
            case "uuid" -> body.put("commandId", "1-1-1-1-1");
            case "hash" -> body.put("payloadSha256", "A".repeat(64));
            case "zeroVersion" -> body.put("expectedCaseVersion", "0");
            default -> throw new AssertionError(damage);
        }
        assertThatThrownBy(() -> parse(body)).isInstanceOfSatisfying(BusinessException.class,
            error -> assertThat(error.code()).isEqualTo("INVALID_HANDOVER_STOP"));
    }

    @Test void rejectsOversizedDuplicateAndTrailingBody() {
        for (byte[] bytes : new byte[][] { new byte[8193], "{\"a\":1,\"a\":2}".getBytes(), "{} {}".getBytes() })
            assertThatThrownBy(() -> HandoverStopCommand.parse(kase, "1", bytes)).isInstanceOf(BusinessException.class);
    }

    private HandoverStopCommand parse(ObjectNode body) { return HandoverStopCommand.parse(kase, "1", HandoverReceiptCodec.bytes(body)); }
    private ObjectNode body() {
        return SubmissionJson.object().put("commandId", UUID.randomUUID().toString()).put("expectedCaseVersion", "1")
            .put("changeRequestId", UUID.randomUUID().toString()).put("recoveryPackageId", UUID.randomUUID().toString())
            .put("payloadSha256", "a".repeat(64)).put("captureHighWatermark", "2").put("auditThroughSeq", "8");
    }
}
