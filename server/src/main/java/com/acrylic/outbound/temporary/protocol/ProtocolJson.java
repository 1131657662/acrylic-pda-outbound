package com.acrylic.outbound.temporary.protocol;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.ByteBuffer;
import java.nio.charset.*;

/** Strict UTF-8 boundary for the new protocol; existing submission bytes are unchanged. */
final class ProtocolJson {
    static final int MAX_BYTES=262144;
    private ProtocolJson() { }
    static JsonNode parse(byte[] bytes) {
        String text=utf8(bytes);
        require(!text.startsWith("\uFEFF"),"BOM_INVALID");
        JsonNode value=SubmissionJson.parse(text);
        unicode(value);
        return value;
    }
    static String utf8(byte[] bytes) {
        require(bytes!=null && bytes.length>0 && bytes.length<=MAX_BYTES,"SIZE_INVALID");
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch(CharacterCodingException error) { throw new IllegalArgumentException("UTF8_INVALID",error); }
    }
    static byte[] canonical(JsonNode value) {
        byte[] result=SubmissionJson.canonical(value).getBytes(StandardCharsets.UTF_8);
        require(result.length<=MAX_BYTES,"SIZE_INVALID");
        return result;
    }
    private static void unicode(JsonNode value) {
        if(value.isTextual())validString(value.textValue());
        if(value.isObject())value.fieldNames().forEachRemaining(ProtocolJson::validString);
        if(value.isContainerNode())value.forEach(ProtocolJson::unicode);
    }
    private static void validString(String text) {
        for(int i=0;i<text.length();i++) {
            char c=text.charAt(i);
            if(Character.isHighSurrogate(c)) {
                require(i+1<text.length() && Character.isLowSurrogate(text.charAt(++i)),"SURROGATE_INVALID");
            } else require(!Character.isLowSurrogate(c),"SURROGATE_INVALID");
        }
    }
    static void require(boolean valid,String code) {
        if(!valid)throw new IllegalArgumentException(code);
    }
}
