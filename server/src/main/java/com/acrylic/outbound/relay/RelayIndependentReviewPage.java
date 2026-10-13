package com.acrylic.outbound.relay;

import com.acrylic.outbound.common.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Pagination limits display only; every cursor binds the complete reconciliation context. */
final class RelayIndependentReviewPage {
    private RelayIndependentReviewPage() { }
    static ObjectNode read(JsonNode full,Query query) {
        int limit=Page.checkedLimit(query.limit());String hash=sha(full);int offset=offset(query,hash);
        require(Set.of("candidates","inventory","peers").contains(query.section()),"核对分页类别无效");
        JsonNode rows=query.section().equals("candidates")?full.path("candidates").path("items"):full.path(query.section());
        if(offset>rows.size())throw new BusinessException(400,"INVALID_CURSOR","核对分页位置超出完整集合");
        var result=object().put("contextSha256",hash).put("section",query.section()).put("total",Integer.toString(rows.size()));
        var items=result.putArray("items");int end=Math.min(offset+limit,rows.size());for(int i=offset;i<end;i++)items.add(rows.get(i));
        result.put("hasMore",end<rows.size()).put("nextCursor",end<rows.size()?encode(hash,query.section(),end):null);
        result.set("original",full.path("original"));result.set("review",full.path("review"));result.set("summary",full.path("summary"));
        result.put("checkedAt",java.time.Instant.now().toString()).put("pendingReconciliation",true).put("associationAuthorized",false)
            .put("stockAvailable",false).put("businessProcessed",false).put("releaseAllowed",false).put("localDeletionAllowed",false);
        return result;
    }
    private static int offset(Query query,String hash) {
        if(query.cursor().isEmpty())return 0;
        String[] parts;
        try {
            if(query.cursor().length()>200)throw new IllegalArgumentException();
            byte[] bytes=Base64.getUrlDecoder().decode(query.cursor());
            if(!Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).equals(query.cursor()))throw new IllegalArgumentException();
            parts=new String(bytes,StandardCharsets.US_ASCII).split("\\|",-1);
            if(parts.length!=3 || !parts[0].matches("[0-9a-f]{64}") || !Set.of("candidates","inventory","peers").contains(parts[1])
                || !parts[2].matches("[1-9][0-9]{0,8}"))throw new IllegalArgumentException();
        } catch(IllegalArgumentException error) { throw new BusinessException(400,"INVALID_CURSOR","核对分页位置无效，请重新查询"); }
        if(!parts[0].equals(hash) || !parts[1].equals(query.section()))
            throw new BusinessException(409,"INDEPENDENT_REVIEW_CONTEXT_CHANGED","原件、候选、库存或其他原件已变化，请重新核对全部依据");
        return Integer.parseInt(parts[2]);
    }
    private static String encode(String hash,String section,int offset) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString((hash+"|"+section+"|"+offset).getBytes(StandardCharsets.US_ASCII));
    }
    record Query(String section,String cursor,int limit) { }
}
