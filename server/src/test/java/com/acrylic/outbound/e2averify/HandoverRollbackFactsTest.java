package com.acrylic.outbound.e2averify;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.assertj.core.api.Assertions.*;

class HandoverRollbackFactsTest {
    private static final LocalDateTime TIME = LocalDateTime.of(2026, 9, 30, 16, 21, 2, 123456789);

    @Test void databaseSnapshotPreservesJdbcTimeBinaryNullAndExactNumbers() throws Exception {
        var row = row();
        row.put("timestamp", Timestamp.valueOf(TIME));
        row.put("decimal", new BigDecimal("12345678901234567890.1234500"));
        row.put("big_integer", new BigInteger("18446744073709551615"));
        var parsed = new ObjectMapper().readTree(HandoverRollbackFacts.encodeRow(row));
        assertThat(parsed.path("created_at").path("value").asText()).isEqualTo(TIME.toString());
        assertThat(parsed.path("timestamp").path("value").asText()).isEqualTo(TIME.toString());
        assertThat(Base64.getDecoder().decode(parsed.path("signed_bytes").path("value").asText()))
            .containsExactly((byte)0, (byte)1, (byte)255);
        assertThat(parsed.get("nullable").isNull()).isTrue();
        assertThat(parsed.path("sequence").path("value").asText()).isEqualTo("9007199254740993");
        assertThat(parsed.path("decimal").path("value").asText()).isEqualTo("12345678901234567890.1234500");
        assertThat(parsed.path("big_integer").path("value").asText()).isEqualTo("18446744073709551615");
        assertThat(parsed.path("text").path("value").asText()).isEqualTo("line\n\"quoted\"\u0000");
    }

    @Test void columnOrderDoesNotChangeSnapshot() {
        var first = row();
        var reversed = new LinkedHashMap<String, Object>();
        var columns = new ArrayList<>(first.keySet()); Collections.reverse(columns);
        columns.forEach(column -> reversed.put(column, first.get(column)));
        assertThat(HandoverRollbackFacts.encodeRow(reversed)).isEqualTo(HandoverRollbackFacts.encodeRow(first));
    }

    @ParameterizedTest @MethodSource("changedValues")
    void changedRawValueIsVisible(String column, Object changed) {
        var original = row();
        var updated = row(); updated.put(column, changed);
        assertThat(HandoverRollbackFacts.encodeRow(updated)).isNotEqualTo(HandoverRollbackFacts.encodeRow(original));
    }

    private static Stream<Arguments> changedValues() {
        return Stream.of(Arguments.of("created_at", TIME.plusNanos(1)),
            Arguments.of("signed_bytes", new byte[]{0, 1, (byte)254}),
            Arguments.of("signed_bytes", "AAH/"), Arguments.of("nullable", "null"),
            Arguments.of("sequence", 9007199254740994L), Arguments.of("sequence", "9007199254740993"));
    }

    @Test void missingColumnDoesNotEqualNullColumn() {
        var original = row();
        var missing = row(); missing.remove("nullable");
        assertThat(HandoverRollbackFacts.encodeRow(missing)).isNotEqualTo(HandoverRollbackFacts.encodeRow(original));
    }

    @Test void unsupportedValueFailsInsteadOfUsingObjectIdentity() {
        assertThatThrownBy(() -> HandoverRollbackFacts.encodeRow(Map.of("unsupported", new Object())))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Unsupported JDBC snapshot type");
    }

    private static LinkedHashMap<String, Object> row() {
        var row = new LinkedHashMap<String, Object>();
        row.put("created_at", TIME); row.put("signed_bytes", new byte[]{0, 1, (byte)255});
        row.put("nullable", null); row.put("sequence", 9007199254740993L);
        row.put("text", "line\n\"quoted\"\u0000");
        return row;
    }
}
