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

    static String hex(byte[] bytes) {
        var text = new StringBuilder();
        for (byte value : bytes) text.append(String.format("%02x", value));
        return text.toString();
    }

    /** An anchor secret is exactly 32 bytes: short or malformed supplies fail closed. */
    static byte[] bytes(String hex) {
        require(hex != null && hex.matches("(?:[a-f0-9]{2}){32}"), "CONTROL_ANCHOR_TAG");
        var out = new byte[32];
        for (int index = 0; index < out.length; index++)
            out[index] = (byte) Integer.parseInt(hex.substring(index * 2, index * 2 + 2), 16);
        return out;
    }

    static String sha256(byte[] bytes) {
        try { return hex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException error) {
            throw new RecoveryControlException("CONTROL_ANCHOR_UNAVAILABLE", error);
        }
    }

    static String hmac(byte[] secret, JsonNode value) {
        require(secret != null && secret.length > 0, "CONTROL_ANCHOR_REQUIRED");
        try {
            var mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(secret, "HmacSHA256"));
            return hex(mac.doFinal(RecoveryControlJson.encode(value)));
        } catch (java.security.GeneralSecurityException error) {
            throw new RecoveryControlException("CONTROL_ANCHOR_UNAVAILABLE", error);
        }
    }

    /** Verifies the anchor tag over the record body: every field except the tag itself. */
    static void anchorTag(byte[] secret, JsonNode value) {
        String tag = text(value, "anchorTag");
        require(tag.matches("[a-f0-9]{64}"), "CONTROL_ANCHOR_TAG");
        var body = value.deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) body).remove("anchorTag");
        require(tag.equals(hmac(secret, body)), "CONTROL_LEASE_ANCHOR_MISMATCH");
    }

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
