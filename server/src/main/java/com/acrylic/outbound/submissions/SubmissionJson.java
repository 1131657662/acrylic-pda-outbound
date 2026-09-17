package com.acrylic.outbound.submissions;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.inventory.ImportFiles;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Fixed integer/string protocol; object keys sort lexically, array order is evidence. */
public final class SubmissionJson {
    private static final ObjectMapper JSON=new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private SubmissionJson() { }
    public static JsonNode parse(byte[] bytes) {
        require(bytes.length>0 && bytes.length<=33554432,"正文大小不受支持");
        try { var value=JSON.readTree(bytes);canonical(value);return value; }
        catch(java.io.IOException error) { throw bad("正文JSON无效"); }
    }
    public static String canonical(JsonNode value) { return normalized(value).toString(); }
    public static String sha(JsonNode value) { return ImportFiles.sha256(canonical(value).getBytes(StandardCharsets.UTF_8)); }
    private static JsonNode normalized(JsonNode value) {
        if(value.isObject()) {
            var result=JSON.createObjectNode();var fields=new TreeMap<String,JsonNode>();value.fields().forEachRemaining(e->fields.put(e.getKey(),e.getValue()));
            fields.forEach((k,v)->result.set(k,normalized(v)));return result;
        }
        if(value.isArray()) { var result=JSON.createArrayNode();value.forEach(v->result.add(normalized(v)));return result; }
        require(value.isTextual() || value.isNull() || value.isBoolean() ||
            (value.isIntegralNumber() && value.canConvertToLong() && value.longValue()>=-9007199254740991L && value.longValue()<=9007199254740991L),"正文仅支持字符串、安全整数、布尔及null");
        return value;
    }
    public static String text(JsonNode node,String key) {
        var value=node.get(key);require(value!=null && value.isTextual(),"缺少字符串字段："+key);return value.textValue();
    }
    public static long number(JsonNode node,String key) {
        String value=text(node,key);require(value.matches("0|[1-9][0-9]{0,17}"),"无效整数字段："+key);return Long.parseLong(value);
    }
    public static UUID uuid(JsonNode node,String key) {
        try { String s=text(node,key);UUID id=UUID.fromString(s);require(id.toString().equals(s),"UUID必须为规范格式");return id; }
        catch(IllegalArgumentException error) { throw bad("无效UUID字段："+key); }
    }
    public static ArrayNode array(JsonNode node,String key) {
        var value=node.get(key);require(value!=null && value.isArray(),"缺少数组："+key);return (ArrayNode)value;
    }
    public static void equal(Object a,Object b,String message) { require(Objects.equals(a,b),message); }
    public static void require(boolean valid,String message) { if(!valid) throw bad(message); }
    public static BusinessException bad(String message) { return new BusinessException(400,"INVALID_SUBMISSION",message); }
    public static ObjectNode object() { return JSON.createObjectNode(); }
}
