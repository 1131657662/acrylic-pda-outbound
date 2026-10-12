package com.acrylic.outbound.temporary.review;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.submissions.SubmissionFiles;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.command.CommandFacts;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.acrylic.outbound.temporary.review.TemporaryDuplicateModels.*;

@Service
public class TemporaryDuplicateService {
    private final TemporaryReviewService reviews;private final TemporaryDuplicateQueries queries;private final JsonText json;
    private final TemporaryDecisionCandidates candidates;private final CommandFacts facts;
    private final SubmissionFiles files;
    private final boolean commandsEnabled;
    public TemporaryDuplicateService(TemporaryReviewService reviews,TemporaryDuplicateQueries queries,JsonText json,
        TemporaryDecisionCandidates candidates,CommandFacts facts,SubmissionFiles files,
        @org.springframework.beans.factory.annotation.Value("${outbound.temporary.commands.enabled:false}") boolean commandsEnabled) {
        this.reviews=reviews;this.queries=queries;this.json=json;this.candidates=candidates;this.facts=facts;
        this.files=files;this.commandsEnabled=commandsEnabled;
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Response read(UUID submission,UUID warehouse,Filter filter) {
        int limit=Page.checkedLimit(filter.limit());var context=context(reviews.detail(submission,warehouse));
        var range=Range.resolve(filter,Instant.parse(context.path("receivedAt").asText()));
        String hash=json.digest(List.of(warehouse.toString(),context,range));var params=queries.parameters(warehouse,range,context);
        var snapshot=queries.snapshot(params);var cursor=TemporaryDuplicateCursor.parse(filter.cursor(),hash,snapshot.fingerprint());
        var rows=queries.page(params,cursor,limit);boolean more=rows.size()>limit;var heads=rows.subList(0,Math.min(limit,rows.size()));
        var products=queries.products(heads);var items=heads.stream().map(h->TemporaryDuplicateComparison.compare(h,products.getOrDefault(h.revisionId(),List.of()),context,commandsEnabled)).toList();
        String next=more?TemporaryDuplicateCursor.encode(hash,snapshot.fingerprint(),heads.getLast()):null;
        // A write is offered only when the command surface is on and at least one candidate can actually be
        // linked. Release stays closed: it is a separate production capability and never follows from this flag.
        boolean writeAvailable=commandsEnabled && items.stream().anyMatch(Candidate::linkAvailable);
        boolean exclusionAvailable=commandsEnabled && context.path("status").asText().equals("REVIEW_REQUIRED")
            && context.path("current").asBoolean() && context.path("serviceMode").asText().equals("NORMAL");
        return new Response(context,range,Instant.now(),hash,snapshot.fingerprint(),commandContext(submission),
            snapshot.total(),items,next,more,writeAvailable,false,exclusionAvailable);
    }
    /**
     * 命令口径的候选上下文摘要：与 {@code /temporary-commands} 命令体 {@code expected.candidateContextSha256}
     * 由**同一套服务端默认候选查询**（{@link TemporaryDecisionCandidates#snapshot}）产生，客户端必须原样回显。
     * 它只是把该取值显式发给客户端（此前 HTTP 面没有任何读取来源，Web 复核页只能错用本页自己的 contextSha256）；
     * 不授予任何能力，也不改变 linkAvailable/writeAvailable/releaseAllowed 恒 false 的口径。
     * 只有本批次仍是可读的待确认来源时才有值；否则返回 null，客户端据此拒绝发起命令。
     */
    private String commandContext(UUID submission) {
        try {
            var hint=facts.read(submission);
            String payload=hint.text("receivedPayloadSha256");
            if(payload==null) return null;
            var body=SubmissionJson.parse(files.read(payload));
            return candidates.snapshot(hint.id("warehouseId"),submission,body).path("contextSha256").asText();
        } catch(RuntimeException unavailable) {
            // 不是命令面：读取失败只让本字段为空（客户端不得据此发命令），页面其余只读内容照常。
            return null;
        }
    }
    private JsonNode context(JsonNode detail) {
        var context=SubmissionJson.object();
        for(String key:List.of("source","submissionId","payloadSha256","planSha256","workRound","currentSubmissionId","current","status",
            "orderRevision","vehicleNo","containerNo","products","inventorySnapshotId","sourceRecoveryEpoch","serverRecoveryEpoch",
            "serviceMode","reviewerPermissionVersion","receivedAt","closedAt")) context.set(key,detail.get(key));
        context.set("canonicalOrderId",detail.get("orderId"));return context;
    }
}
