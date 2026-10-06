package com.acrylic.outbound.temporary.protocol;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class TemporaryProtocolTest {
    @Test void canonicalPreservesNullArrayOrderAndControlCharacters() {
        assertThat(text(TemporaryProtocol.canonical(bytes("{\"z\":null,\"a\":[\"\\u0001\\n\\\\u0001\",2,1]}"))))
            .isEqualTo("{\"a\":[\"\\u0001\\n\\\\u0001\",2,1],\"z\":null}");
    }
    @ParameterizedTest @ValueSource(strings={"{\"a\":1,\"a\":2}","{} {}","{\"a\":1.0}","9007199254740992","\"\\uD800\"","{\"\\uDC00\":1}","\uFEFF{}"})
    void rejectsAmbiguousOrUnsupportedJson(String input) {
        assertThatThrownBy(()->TemporaryProtocol.canonical(bytes(input))).isInstanceOf(RuntimeException.class);
    }
    @Test void rejectsMalformedUtf8WithoutReplacement() {
        assertThatThrownBy(()->TemporaryProtocol.canonical(new byte[]{34,(byte)0xc0,(byte)0xaf,34})).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void originalResponseHashPreservesWhitespaceWhileDomainHashCanonicalizes() {
        byte[] first=bytes("{\"x\":1}"),second=bytes(" { \"x\":1 }\n");
        assertThat(TemporaryProtocol.unsignedReceiptSha256(first)).isEqualTo(TemporaryProtocol.unsignedReceiptSha256(second));
        assertThat(TemporaryProtocol.originalResponseSha256(first)).isNotEqualTo(TemporaryProtocol.originalResponseSha256(second));
        assertThat(TemporaryProtocol.unsignedReceiptSha256(first)).isNotEqualTo(TemporaryProtocol.derivedControlSha256(first));
    }
    @Test void exactResourceLimitIncludesOriginalAndCanonicalOutput() {
        assertThat(TemporaryProtocol.canonical(bytes("\""+"a".repeat(262142)+"\""))).hasSize(262144);
        assertThatThrownBy(()->TemporaryProtocol.canonical(bytes("\""+"a".repeat(262143)+"\""))).isInstanceOf(IllegalArgumentException.class);
    }
    @ParameterizedTest @ValueSource(longs={4294967297L,8589934593L})
    void commandVersionCannotTruncateToOne(long version) {
        String source="{\"commandVersion\":"+version+",\"commandType\":\"NORMAL_CONFIRM\",\"commandId\":null,\"warehouseId\":null,\"source\":null,\"submissionId\":null,\"workRound\":null,\"authorizationId\":null,\"freezeDeviceId\":null,\"sourceRecoveryEpoch\":null,\"transportRecoveryEpoch\":null,\"expected\":null,\"reason\":\"\"}";
        assertThatThrownBy(()->TemporaryProtocol.command(bytes(source))).isInstanceOf(IllegalArgumentException.class).hasMessage("VERSION_INVALID");
    }
    @Test void activationCommandIncludesItsTicketBindingInTheStrictBodyShape() {
        String id = UUID.randomUUID().toString();
        var body = SubmissionJson.object().put("commandVersion", 1).put("commandType", "ACTIVATE_CONTINUATION")
            .put("commandId", id).put("warehouseId", id).put("submissionId", id).put("workRound", "1")
            .put("authorizationId", id).put("freezeDeviceId", id).put("sourceRecoveryEpoch", "1")
            .put("transportRecoveryEpoch", "1").put("reason", "continue retained source")
            .put("nextOrderScanSeq", "5").put("continuationTicketId", id);
        body.putObject("source").put("kind", "TEMP").put("creatorDeviceId", id).put("clientOrderId", id);
        body.set("retainedManifest", manifest(id)); body.set("expected", expected(id));

        assertThat(TemporaryProtocol.command(bytes(SubmissionJson.canonical(body)))).isNotEmpty();
    }
    private static ObjectNode manifest(String id) {
        var result = SubmissionJson.object().put("version", 1);
        result.putArray("items").add(SubmissionJson.object().put("baleId", id).put("expectedDispatchCycle", "1")
            .put("recordSha256", "a".repeat(64)));
        return result;
    }
    private static ObjectNode expected(String id) {
        return SubmissionJson.object().putNull("canonicalOrderId").put("mappingVersion", "0")
            .put("currentSubmissionId", id).put("scopeRound", "1").putNull("slotVersion").put("decisionVersion", "0")
            .putNull("receivedPayloadSha256").putNull("declaredUploadSha256").putNull("planSha256")
            .putNull("targetRevision").putNull("candidateContextSha256").put("userAuthVersion", "1")
            .put("evidenceSetId", id).put("evidenceSetSha256", "b".repeat(64)).put("verificationId", id)
            .put("verificationVersion", "1").put("verificationAlgorithmVersion", "1")
            .putNull("dispositionManifestSha256").putNull("dispositionManifestVersion")
            .put("retainedManifestSha256", "c".repeat(64)).put("retainedManifestVersion", "1");
    }
    private static byte[] bytes(String value) { return value.getBytes(StandardCharsets.UTF_8); }
    private static String text(byte[] value) { return new String(value,StandardCharsets.UTF_8); }
}
