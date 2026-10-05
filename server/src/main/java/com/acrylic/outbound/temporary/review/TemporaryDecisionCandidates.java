package com.acrylic.outbound.temporary.review;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.temporary.review.TemporaryDuplicateModels.*;

/** Full server-selected default query under the caller's W barrier, never a browser page or filter. */
@Component
public class TemporaryDecisionCandidates {
    private final TemporaryDuplicateQueries queries; private final JdbcTemplate jdbc;
    public TemporaryDecisionCandidates(TemporaryDuplicateQueries queries,JdbcTemplate jdbc) {
        this.queries=queries;this.jdbc=jdbc;
    }
    public ObjectNode snapshot(UUID warehouse,UUID submission,JsonNode body) {
        var range=range(submission);
        var context=context(submission,body);
        var all=queries.snapshot(queries.parameters(warehouse,range,context));
        var result=SubmissionJson.object().put("policyVersion","REVIEW_DEFAULT_V1")
            .put("fromDate",range.fromDate().toString()).put("throughDate",range.throughDate().toString())
            .put("total",Long.toString(all.total())).put("fingerprint",all.fingerprint());
        result.put("contextSha256",digest(warehouse,context,result));return result;
    }
    public record Reviewed(ObjectNode snapshot,List<Head> heads) { }
    /**
     * The same server-selected default query as {@link #snapshot}, plus the bounded head list a write
     * service needs to name the reviewed target. The digest definition above is deliberately unchanged.
     */
    public Reviewed reviewed(UUID warehouse,UUID submission,JsonNode body) {
        var snapshot=snapshot(warehouse,submission,body);
        var range=range(submission);
        var heads=queries.page(queries.parameters(warehouse,range,context(submission,body)),null,2);
        return new Reviewed(snapshot,List.copyOf(heads.size()>2?heads.subList(0,2):heads));
    }
    public boolean authentic(UUID warehouse,UUID submission,JsonNode body,JsonNode candidates) {
        var range=range(submission);
        if(!range.fromDate().toString().equals(candidates.path("fromDate").asText())
            || !range.throughDate().toString().equals(candidates.path("throughDate").asText()))return false;
        var unsigned=(ObjectNode)candidates.deepCopy();unsigned.remove("contextSha256");
        return digest(warehouse,context(submission,body),unsigned).equals(candidates.path("contextSha256").asText());
    }
    private Range range(UUID submission) {
        var received=jdbc.queryForObject("SELECT received_at FROM submission_payload WHERE submission_id=UUID_TO_BIN(?)",
            java.sql.Timestamp.class,submission.toString()).toInstant();
        return Range.resolve(new Filter(null,null,"",1),received);
    }
    private String digest(UUID warehouse,JsonNode context,JsonNode candidates) {
        var value=SubmissionJson.object().put("warehouseId",warehouse.toString());
        value.set("context",context);value.set("candidates",candidates.deepCopy());return SubmissionJson.sha(value);
    }
    private ObjectNode context(UUID submission,JsonNode body) {
        var context=SubmissionJson.object();context.set("products",TemporaryReviewEvidence.products(body));
        context.set("vehicleNo",body.path("temporaryOrder").get("vehicleNo"));
        return context.put("submissionId",submission.toString()).put("payloadSha256",SubmissionJson.sha(body));
    }
    public static JsonNode products(JsonNode body) { return TemporaryReviewEvidence.products(body); }
}
