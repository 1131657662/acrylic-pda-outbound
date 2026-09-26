package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.security.*;
import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.require;

/** Exact Python ensure_ascii=False, sorted-codepoint JSON with one terminal LF. */
final class RecoveryControlJson {
    static final int MAX_BYTES = 65536;
    private static final ObjectMapper JSON = new ObjectMapper(JsonFactory.builder()
        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(64).build()).build())
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private RecoveryControlJson() { }

    static JsonNode decode(byte[] bytes) {
        require(bytes.length > 0 && bytes.length <= MAX_BYTES, "CONTROL_JSON_SIZE");
        try {
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            var value = JSON.readTree(text);
            require(Arrays.equals(bytes, encode(value)), "CONTROL_NONCANONICAL_JSON");
            return value;
        } catch (java.io.IOException error) {
            throw new RecoveryControlException("CONTROL_JSON_INVALID", error);
        }
    }

    static byte[] encode(JsonNode value) {
        var out = new StringBuilder();
        append(value, out, 0);
        byte[] bytes = out.append('\n').toString().getBytes(StandardCharsets.UTF_8);
        require(bytes.length <= MAX_BYTES, "CONTROL_JSON_SIZE");
        return bytes;
    }

    private static void append(JsonNode value, StringBuilder out, int depth) {
        require(value != null && depth <= 64, "CONTROL_JSON_INVALID");
        if (value.isObject()) { object(value, out, depth); return; }
        if (value.isArray()) {
            out.append('[');
            for (int i = 0; i < value.size(); i++) {
                if (i > 0) out.append(',');
                append(value.get(i), out, depth + 1);
            }
            out.append(']'); return;
        }
        if (value.isTextual()) { string(value.textValue(), out); return; }
        require(value.isBoolean() || value.isNull() || value.isIntegralNumber(), "CONTROL_JSON_INVALID");
        out.append(value);
    }

    private static void object(JsonNode value, StringBuilder out, int depth) {
        var keys = new ArrayList<String>();
        value.fieldNames().forEachRemaining(keys::add);
        keys.sort(RecoveryControlJson::compareText);
        out.append('{');
        for (int i = 0; i < keys.size(); i++) {
            if (i > 0) out.append(',');
            string(keys.get(i), out);
            out.append(':');
            append(value.get(keys.get(i)), out, depth + 1);
        }
        out.append('}');
    }

    static int compareText(String left, String right) {
        var a = left.codePoints().iterator(); var b = right.codePoints().iterator();
        while (a.hasNext() && b.hasNext()) {
            int delta = Integer.compare(a.nextInt(), b.nextInt());
            if (delta != 0) return delta;
        }
        return Boolean.compare(a.hasNext(), b.hasNext());
    }

    private static void string(String text, StringBuilder out) {
        out.append('"');
        for (var chars = text.codePoints().iterator(); chars.hasNext();) {
            int ch = chars.nextInt();
            require(ch < 0xd800 || ch > 0xdfff, "CONTROL_JSON_INVALID");
            switch (ch) {
                case '"' -> out.append("\\\""); case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b"); case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n"); case '\r' -> out.append("\\r"); case '\t' -> out.append("\\t");
                default -> { if (ch < 32) out.append(String.format(Locale.ROOT, "\\u%04x", ch)); else out.appendCodePoint(ch); }
            }
        }
        out.append('"');
    }

    static String hash(JsonNode value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(encode(value))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
