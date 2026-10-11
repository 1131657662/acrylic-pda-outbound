package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;
import static com.acrylic.outbound.recovery.RecoveryReleaseRows.*;

/** Removes one exact signed raw row using the existing inventory confidentiality boundary. */
final class RecoveryRecipientSessionRows {
    static void remove(ObjectNode actual,JsonNode raw,String name,Map<String,String> expected) {
        var columns=raw.path("columns"); var names=new HashSet<String>(); columns.forEach(c->names.add(c.asText()));
        require(actual.path("columns").equals(columns) && expected.keySet().equals(names),"RECOVERY_SESSION_EFFECT_COLUMNS");
        var rawRows=decode(raw);
        require(rawRows.stream().filter(expected::equals).count()==1,"RECOVERY_SESSION_EFFECT_CHANGED");
        if(!actual.has("rows")) { removeDigest(actual,raw,rawRows,expected); return; }
        require(actual.path("rows").isArray(),"RECOVERY_SESSION_EFFECT_PROJECTION");
        boolean inventory=actual.has("rowCount");
        var projected=new LinkedHashMap<>(expected);
        if(inventory) {
            require(text(actual,"rowCount").equals(Integer.toString(actual.path("rows").size())),"RECOVERY_SESSION_ROW_COUNT");
            for(String column:names) if(RecoveryReleaseInventoryQueries.confidential(name,column) && expected.get(column)!=null)
                projected.put(column,"sha256:"+sha256(RecoveryReleaseRows.bytes(expected.get(column))));
        }
        JsonNode selected=encode(columns,List.of(projected)).get(0);
        var retained=com.acrylic.outbound.submissions.SubmissionJson.object().putArray("rows"); int matches=0;
        for(var row:actual.path("rows")) { if(row.equals(selected)) matches++; else retained.add(row.deepCopy()); }
        require(matches==1,"RECOVERY_SESSION_EFFECT_PROJECTION"); actual.set("rows",retained);
        if(inventory) actual.put("rowCount",Integer.toString(retained.size()));
    }
    private static void removeDigest(ObjectNode actual,JsonNode raw,List<Map<String,String>> rows,Map<String,String> expected) {
        require(text(actual,"rowsSha256").equals(sha256(RecoveryTemporaryEvidenceMaterial.encode(raw.path("rows"))))
            && text(actual,"rowCount").equals(Integer.toString(rows.size())),"RECOVERY_SESSION_RAW_DIGEST");
        require(rows.remove(expected),"RECOVERY_SESSION_EFFECT_PROJECTION");
        ArrayNode retained=encode(actual.path("columns"),rows);
        actual.put("rowsSha256",sha256(RecoveryTemporaryEvidenceMaterial.encode(retained)));
        actual.put("rowCount",Integer.toString(rows.size()));
    }
    private RecoveryRecipientSessionRows() { }
}
