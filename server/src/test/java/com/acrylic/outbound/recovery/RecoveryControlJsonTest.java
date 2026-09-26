package com.acrylic.outbound.recovery;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class RecoveryControlJsonTest {
    @ParameterizedTest
    @ValueSource(strings={"{\"a\":1,\"a\":2}\n", "{} {}", "{ \"a\":1}\n", "{\"a\":NaN}\n",
        "{\"a\":1.0}\n", "\"\\ud800\"\n", "{}\r\n", "\ufeff{}\n"})
    void invalidOrAmbiguousWireIsRejected(String input) {
        assertThatThrownBy(() -> RecoveryControlJson.decode(input.getBytes(StandardCharsets.UTF_8)))
            .isInstanceOf(RecoveryControlException.class);
    }

    @Test void codepointOrderingAndLowercaseEscapesMatchPython() {
        var node = RecoveryControlFixture.JSON.createObjectNode().put("😀", "\u001b\n中").put("\ue000", "z");
        String expected = "{\"\ue000\":\"z\",\"😀\":\"\\u001b\\n中\"}\n";
        assertThat(new String(RecoveryControlJson.encode(node), StandardCharsets.UTF_8)).isEqualTo(expected);
        assertThat(RecoveryControlJson.decode(expected.getBytes(StandardCharsets.UTF_8))).isEqualTo(node);
    }

    @Test void malformedUtf8IsNeverReplacementDecoded() {
        assertThatThrownBy(() -> RecoveryControlJson.decode(new byte[]{34, (byte)0xc0, (byte)0xaf, 34, 10}))
            .hasMessage("CONTROL_JSON_INVALID");
    }

    @Test void zeroUuidLeapSecondsInvalidDatesAndExcessWarehousesAreClosed() throws Exception {
        var f = RecoveryControlFixture.create();
        for (String field : List.of("commandId", "possibleLossFrom", "requestedAt", "affectedWarehouseIds")) {
            var command = f.next("RECOVERING");
            switch (field) {
                case "commandId" -> command.put(field, new UUID(0, 0).toString());
                case "possibleLossFrom" -> command.put(field, "2026-02-30T00:00:00Z");
                case "requestedAt" -> command.put(field, "2026-09-22T23:59:60Z");
                default -> { var array = command.putArray(field); for (int i = 0; i <= 1000; i++) array.add("W" + i); }
            }
            assertThatThrownBy(() -> RecoveryControlCommand.validate(command)).isInstanceOf(RecoveryControlException.class);
        }
    }
}
