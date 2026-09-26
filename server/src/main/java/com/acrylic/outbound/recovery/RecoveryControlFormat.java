package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

/** Strict field primitives shared only by the external control protocol. */
final class RecoveryControlFormat {
    static final String ZERO_HASH = "0".repeat(64);
    private RecoveryControlFormat() { }

    static JsonNode readCanonical(byte[] bytes) { return RecoveryControlJson.decode(bytes); }
    static String canonical(JsonNode value) { return new String(RecoveryControlJson.encode(value), StandardCharsets.UTF_8); }
    static String hash(JsonNode value) { return RecoveryControlJson.hash(value); }

    static void fields(JsonNode value, String names) {
        require(value != null && value.isObject(), "CONTROL_FIELDS");
        var actual = new HashSet<String>();
        value.fieldNames().forEachRemaining(actual::add);
        require(actual.equals(Set.of(names.split(" "))), "CONTROL_FIELDS");
    }

    static void version(JsonNode value) {
        var version = value.path("formatVersion");
        require(version.isIntegralNumber() && version.canConvertToInt() && version.intValue() == 1, "CONTROL_FORMAT_UNSUPPORTED");
    }

    static String text(JsonNode value, String key) {
        require(value.path(key).isTextual(), "CONTROL_TEXT");
        return value.path(key).textValue();
    }

    static UUID uuid(JsonNode value, String key) {
        try {
            String text = text(value, key); var id = UUID.fromString(text);
            require(id.toString().equals(text) && !id.equals(new UUID(0, 0)), "CONTROL_UUID");
            return id;
        } catch (IllegalArgumentException error) { throw new RecoveryControlException("CONTROL_UUID", error); }
    }

    static void identifier(JsonNode value, String key) {
        require(text(value, key).matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}"), "CONTROL_IDENTIFIER");
    }

    static long revision(JsonNode value, String key) {
        String text = text(value, key);
        require(text.matches("0|[1-9][0-9]{0,17}"), "CONTROL_REVISION");
        return Long.parseLong(text);
    }

    static Instant instant(JsonNode value, String key) {
        String text = text(value, key);
        require(text.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(?:\\.[0-9]{1,6})?Z"), "CONTROL_TIME");
        try {
            var date = LocalDateTime.parse(text.substring(0, text.length() - 1));
            require(date.getYear() > 0, "CONTROL_TIME");
            return date.toInstant(ZoneOffset.UTC);
        } catch (DateTimeException error) { throw new RecoveryControlException("CONTROL_TIME", error); }
    }

    static void hashField(JsonNode value, String key) {
        require(text(value, key).matches("[a-f0-9]{64}"), "CONTROL_HASH");
    }

    static void require(boolean valid, String code) {
        if (!valid) throw new RecoveryControlException(code);
    }
}
