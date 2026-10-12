package com.acrylic.outbound.temporary.command;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.review.TemporaryDecisionCandidates;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Resolve signed exclusions against the full current set under the caller's warehouse/source locks. */
@Component
public class TemporaryCandidateSelection {
    private final JdbcTemplate jdbc;private final CommandStore store;private final CommandReceiptIntegrity integrity;
    private final TemporaryDecisionCandidates candidates;private final TemporaryExclusionFacts exclusions;
    public TemporaryCandidateSelection(JdbcTemplate jdbc,CommandStore store,CommandReceiptIntegrity integrity,
        TemporaryDecisionCandidates candidates,TemporaryExclusionFacts exclusions) {
        this.jdbc=jdbc;this.store=store;this.integrity=integrity;this.candidates=candidates;this.exclusions=exclusions;
    }
    public record Selection(TemporaryDecisionCandidates.Reviewed reviewed,ObjectNode proof) {
        public long total() { return proof==null?reviewed.snapshot().path("total").asLong():proof.path("effectiveTotal").asLong(); }
        public void capture(ObjectNode decision) { if(proof!=null)decision.set("selection",proof.deepCopy()); }
    }
    public Selection read(CommandContext context,JsonNode body) {
        var snapshot=candidates.snapshot(context.id("warehouseId"),context.id("submissionId"),body);
        var ids=receipts(context,snapshot.path("contextSha256").asText());
        if(ids.size()>1000)throw new BusinessException(409,"TEMPORARY_EXCLUSION_LIMIT","本集合排除记录超过单次核验上限，请保全并核对");
        var selected=new TreeMap<String,JsonNode>();
        for(String id:ids) {
            var row=store.byReceipt(UUID.fromString(id));var receipt=integrity.verify(row);
            CommandSelectionDecision.bind(context.json(),snapshot,receipt);
            var captured=receipt.at("/businessDecision/exclusion");
            var input=(ObjectNode)captured.deepCopy();input.remove(List.of("candidate","work","withdrawalReceipt"));
            if(!exclusions.read(context,body,input).equals(captured))throw CommandErrors.stale();
            selected.put(input.path("orderId").asText(),SubmissionJson.object().put("orderId",input.path("orderId").asText())
                .put("receiptId",id).put("originalResponseSha256",row.text("original_response_sha256"))
                .put("resultingDecisionVersion",receipt.path("resultingDecisionVersion").asText()));
        }
        if(selected.isEmpty())return new Selection(candidates.reviewed(context.id("warehouseId"),context.id("submissionId"),body),null);
        var excluded=new HashSet<UUID>();selected.keySet().forEach(id->excluded.add(UUID.fromString(id)));
        var reviewed=candidates.remaining(context.id("warehouseId"),context.id("submissionId"),body,excluded);
        if(!reviewed.snapshot().equals(snapshot))throw CommandErrors.stale();
        var proof=SubmissionJson.object().put("effectiveTotal",Long.toString(snapshot.path("total").asLong()-selected.size()));
        var references=proof.putArray("exclusions");selected.values().forEach(references::add);return new Selection(reviewed,proof);
    }
    private List<String> receipts(CommandContext context,String hash) {
        return jdbc.queryForList("""
            SELECT BIN_TO_UUID(id) FROM temporary_command_receipt
            WHERE warehouse_id=? AND scope_id=? AND submission_id=? AND work_round=? AND command_type='DUPLICATE_EXCLUDE'
              AND JSON_UNQUOTE(JSON_EXTRACT(CONVERT(canonical_command_bytes USING utf8mb4),'$.expected.candidateContextSha256'))=?
            ORDER BY recorded_at,id LIMIT 1001
            """,String.class,Ids.bytes(context.id("warehouseId")),Ids.bytes(context.id("scopeId")),Ids.bytes(context.id("submissionId")),
            context.version("workRound"),hash);
    }
}
