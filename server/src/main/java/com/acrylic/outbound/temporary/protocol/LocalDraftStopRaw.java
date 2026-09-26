package com.acrylic.outbound.temporary.protocol;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.Arrays;
import static com.acrylic.outbound.temporary.protocol.ProtocolJson.require;

/** Strict bounded original bytes; larger than the small proof/manifest JSON boundary. */
final class LocalDraftStopRaw {
    private LocalDraftStopRaw() { }
    static JsonNode parse(byte[] bytes) {
        require(bytes != null && bytes.length > 0 && bytes.length <= 33554432, "DRAFT_RAW_SIZE");
        try {
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            require(!text.startsWith("\uFEFF"), "DRAFT_RAW_BOM");
            JsonNode value = SubmissionJson.parse(text);
            unicode(value, 0);
            require(Arrays.equals(bytes, SubmissionJson.canonical(value).getBytes(StandardCharsets.UTF_8)), "DRAFT_RAW_CANONICAL");
            return value;
        } catch (CharacterCodingException error) { throw new IllegalArgumentException("DRAFT_RAW_UTF8", error); }
    }
    private static void unicode(JsonNode value, int depth) {
        require(depth <= 16, "DRAFT_RAW_DEPTH");
        if (value.isTextual()) string(value.textValue());
        if (value.isObject()) value.fieldNames().forEachRemaining(LocalDraftStopRaw::string);
        if (value.isContainerNode()) value.forEach(child -> unicode(child, depth + 1));
    }
    private static void string(String text) {
        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);
            if (Character.isHighSurrogate(current))
                require(i + 1 < text.length() && Character.isLowSurrogate(text.charAt(++i)), "DRAFT_RAW_SURROGATE");
            else require(!Character.isLowSurrogate(current), "DRAFT_RAW_SURROGATE");
        }
    }
    static long number(JsonNode node, String name) {
        String text = LocalDraftStopClaims.text(node, name);
        require(text.matches("0|[1-9][0-9]{0,15}"), "DRAFT_RAW_NUMBER");
        long value = Long.parseLong(text); require(value <= 9007199254740991L, "DRAFT_RAW_NUMBER"); return value;
    }
}
