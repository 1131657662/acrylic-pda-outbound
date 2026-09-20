package com.acrylic.outbound.temporary.review;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.acrylic.outbound.temporary.review.TemporaryDuplicateModels.*;

@Service
public class TemporaryDuplicateService {
    private final TemporaryReviewService reviews;private final TemporaryDuplicateQueries queries;private final JsonText json;
    public TemporaryDuplicateService(TemporaryReviewService reviews,TemporaryDuplicateQueries queries,JsonText json) {
        this.reviews=reviews;this.queries=queries;this.json=json;
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Response read(UUID submission,UUID warehouse,Filter filter) {
        int limit=Page.checkedLimit(filter.limit());var context=context(reviews.detail(submission,warehouse));
        var range=Range.resolve(filter,Instant.parse(context.path("receivedAt").asText()));
        String hash=json.digest(List.of(warehouse.toString(),context,range));var params=queries.parameters(warehouse,range,context);
        var snapshot=queries.snapshot(params);var cursor=TemporaryDuplicateCursor.parse(filter.cursor(),hash,snapshot.fingerprint());
        var rows=queries.page(params,cursor,limit);boolean more=rows.size()>limit;var heads=rows.subList(0,Math.min(limit,rows.size()));
        var products=queries.products(heads);var items=heads.stream().map(h->TemporaryDuplicateComparison.compare(h,products.getOrDefault(h.revisionId(),List.of()),context)).toList();
        String next=more?TemporaryDuplicateCursor.encode(hash,snapshot.fingerprint(),heads.getLast()):null;
        return new Response(context,range,Instant.now(),hash,snapshot.fingerprint(),snapshot.total(),items,next,more,false,false);
    }
    private JsonNode context(JsonNode detail) {
        var context=SubmissionJson.object();
        for(String key:List.of("source","submissionId","payloadSha256","planSha256","workRound","currentSubmissionId","current","status",
            "orderRevision","vehicleNo","containerNo","products","inventorySnapshotId","sourceRecoveryEpoch","serverRecoveryEpoch",
            "serviceMode","reviewerPermissionVersion","receivedAt","closedAt")) context.set(key,detail.get(key));
        context.set("canonicalOrderId",detail.get("orderId"));return context;
    }
}
