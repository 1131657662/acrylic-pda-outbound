package com.acrylic.outbound.temporary.review;

import com.acrylic.outbound.common.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

final class TemporaryReviewEvidence {
    private TemporaryReviewEvidence() { }
    static ArrayNode products(JsonNode body) {
        var totals=new HashMap<String,long[]>();
        for(var scan:body.path("scans")) if(scan.path("status").asText().equals("ACTIVE")) {
            var value=totals.computeIfAbsent(scan.path("deliveryCode").asText(),key->new long[2]);
            value[0]++; value[1]=Math.addExact(value[1],scan.path("weightGrams").asLong());
        }
        var result=object().putArray("products");
        for(var plan:body.at("/temporaryOrder/lines")) {
            String code=plan.path("deliveryCode").asText(); var total=totals.getOrDefault(code,new long[2]);
            var row=object();for(String field:List.of("deliveryCode","productId","model","name","plannedCount")) row.set(field,plan.get(field));
            row.put("actualCount",Long.toString(total[0])).put("weightGrams",Long.toString(total[1]));
            row.put("shortCount",Long.toString(plan.path("plannedCount").asLong()-total[0]));result.add(row);
        }
        return result;
    }
    static ArrayNode originalIssues(String stored) {
        var counts=new TreeMap<String,Long>();
        if(stored!=null) for(var issue:parse(stored)) counts.merge(issue.path("code").asText(),1L,Long::sum);
        var result=object().putArray("issues");
        counts.forEach((code,count)->result.add(object().put("code",code).put("count",count.toString())));return result;
    }
    static Page<JsonNode> scans(JsonNode body,UUID id,String hash,String cursor,int requestedLimit) {
        int limit=Page.checkedLimit(requestedLimit);long after=scanCursor(cursor,id,hash);
        var rows=new ArrayList<JsonNode>();
        for(var scan:body.path("scans")) if(scan.path("orderScanSeq").asLong()>after) rows.add(scan);
        rows.sort(Comparator.comparingLong(row->row.path("orderScanSeq").asLong()));
        return Page.from(rows,limit,row->Base64.getUrlEncoder().withoutPadding().encodeToString(
            (id+"|"+hash+"|"+row.path("orderScanSeq").asText()).getBytes(StandardCharsets.US_ASCII)));
    }
    private static long scanCursor(String cursor,UUID id,String hash) {
        if(cursor.isEmpty()) return 0;
        try {
            if(cursor.length()>200) throw new IllegalArgumentException();
            var parts=new String(Base64.getUrlDecoder().decode(cursor),StandardCharsets.US_ASCII).split("\\|",-1);
            if(parts.length!=3 || !parts[0].equals(id.toString()) || !parts[1].equals(hash) || !parts[2].matches("[1-9][0-9]{0,18}")) throw new IllegalArgumentException();
            return Long.parseLong(parts[2]);
        } catch(IllegalArgumentException error) { throw new BusinessException(400,"INVALID_CURSOR","明细分页位置与原批次不符，请返回首页"); }
    }
}
