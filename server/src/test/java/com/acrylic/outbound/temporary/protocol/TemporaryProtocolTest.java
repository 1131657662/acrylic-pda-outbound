package com.acrylic.outbound.temporary.protocol;

import java.nio.charset.StandardCharsets;
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
    private static byte[] bytes(String value) { return value.getBytes(StandardCharsets.UTF_8); }
    private static String text(byte[] value) { return new String(value,StandardCharsets.UTF_8); }
}
