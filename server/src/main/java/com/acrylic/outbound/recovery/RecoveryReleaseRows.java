package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.acrylic.outbound.submissions.SubmissionJson;
import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.require;

/** Exact original column values; decoding never turns null into an empty value. */
final class RecoveryReleaseRows {
    static List<Map<String,String>> decode(JsonNode table) {
        var columns=table.path("columns"); var rows=table.path("rows");
        require(columns.isArray() && !columns.isEmpty() && rows.isArray() && rows.size()<=65536,"RECOVERY_SCOPE_TABLE_SHAPE");
        var names=new ArrayList<String>(); columns.forEach(c->{require(c.isTextual(),"RECOVERY_SCOPE_COLUMNS"); names.add(c.asText());});
        require(new HashSet<>(names).size()==names.size(),"RECOVERY_SCOPE_COLUMNS");
        var result=new ArrayList<Map<String,String>>();
        for(var row:rows) {
            require(row.isArray() && row.size()==names.size(),"RECOVERY_SCOPE_ROW_SHAPE"); var item=new LinkedHashMap<String,String>();
            for(int i=0;i<names.size();i++) {
                var cell=row.get(i); require(cell.isNull() || cell.isTextual(),"RECOVERY_SCOPE_CELL");
                if(!cell.isNull()) bytes(cell.asText()); item.put(names.get(i),cell.isNull()?null:cell.asText());
            }
            result.add(item);
        }
        return result;
    }

    static byte[] bytes(String encoded) {
        require(encoded!=null,"RECOVERY_SCOPE_NULL");
        try {
            var value=Base64.getDecoder().decode(encoded);
            require(Base64.getEncoder().encodeToString(value).equals(encoded),"RECOVERY_SCOPE_ENCODING"); return value;
        } catch(IllegalArgumentException invalid) { throw new RecoveryControlException("RECOVERY_SCOPE_ENCODING",invalid); }
    }
    static String text(Map<String,String> row,String column) {
        return new String(bytes(row.get(column)),java.nio.charset.StandardCharsets.UTF_8);
    }
    static String encoded(String value) { return value==null?null:Base64.getEncoder().encodeToString(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
    static String id(String value) { return Base64.getEncoder().encodeToString(com.acrylic.outbound.common.Ids.bytes(UUID.fromString(value))); }
    static String hashBytes(String hex) { return Base64.getEncoder().encodeToString(HexFormat.of().parseHex(hex)); }
    static java.time.Instant time(Map<String,String> row,String column) {
        return java.time.LocalDateTime.parse(text(row,column).replace(' ','T')).toInstant(java.time.ZoneOffset.UTC);
    }
    static void eq(Map<String,String> row,String column,String value) {
        require(row.containsKey(column) && Objects.equals(row.get(column),encoded(value)),"RECOVERY_SCOPE_EFFECT_"+column);
    }
    static void uuid(Map<String,String> row,String column,String value) {
        require(Objects.equals(row.get(column),id(value)),"RECOVERY_SCOPE_EFFECT_"+column);
    }
    static ArrayNode encode(JsonNode columns,List<Map<String,String>> rows) {
        var values=new ArrayList<JsonNode>();
        for(var row:rows) { var value=SubmissionJson.object().putArray("rows"); columns.forEach(c->value.add(row.get(c.asText()))); values.add(value); }
        values.sort(Comparator.comparing(Object::toString)); var result=SubmissionJson.object().putArray("rows"); values.forEach(result::add); return result;
    }
    private RecoveryReleaseRows() { }
}
