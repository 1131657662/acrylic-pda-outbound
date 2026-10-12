package com.acrylic.outbound.temporary.command;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.submissions.*;
import com.acrylic.outbound.temporary.review.TemporaryDecisionCandidates;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Captures a selected member of the unfiltered default set while the warehouse/source barrier is held. */
@Component
public class TemporaryExclusionFacts {
    private final JdbcTemplate jdbc;private final TemporaryDecisionCandidates candidates;
    private final JsonText json;private final SubmissionArchiveProofs proofs;
    public TemporaryExclusionFacts(JdbcTemplate jdbc,TemporaryDecisionCandidates candidates,JsonText json,SubmissionArchiveProofs proofs) {
        this.jdbc=jdbc;this.candidates=candidates;this.json=json;this.proofs=proofs;
    }
    public ObjectNode read(CommandContext context,JsonNode body,JsonNode input) {
        var id=UUID.fromString(input.path("orderId").asText());
        if(id.equals(context.id("canonicalOrderId")))throw conflict("不能排除当前关联目标");
        var locked=jdbc.queryForList("SELECT id FROM outbound_order WHERE id=? AND warehouse_id=? AND source='WEB' FOR UPDATE",
            Ids.bytes(id),Ids.bytes(context.id("warehouseId")));
        if(locked.size()!=1)throw conflict("排除候选不存在或不属于原仓库");
        var candidate=candidates.candidate(context.id("warehouseId"),context.id("submissionId"),body,id);
        if(candidate==null)throw conflict("候选不属于服务端完整默认集合");
        if(candidate.order().revision()!=input.path("revision").asInt(-1)
            || !candidate.order().rowVersion().equals(input.path("rowVersion").asText()))throw CommandErrors.stale();
        var value=(ObjectNode)input.deepCopy();value.set("candidate",SubmissionJson.parse(json.encode(candidate)));
        value.set("work",work(id));value.set("withdrawalReceipt",withdrawal(context,id,input));
        TemporaryExclusionPolicy.validate(body,value);return value;
    }
    private JsonNode work(UUID id) {
        return jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM order_assignment a WHERE a.order_id=o.id AND a.active_marker=1),
              EXISTS(SELECT 1 FROM pc_work p WHERE p.order_id=o.id AND p.status<>'CANCELLED'),
              EXISTS(SELECT 1 FROM submission s JOIN submission_scope c ON c.id=s.scope_id
                WHERE (c.source_order_id=o.id OR c.canonical_order_id=o.id) AND s.status<>'ABORTED'),
              EXISTS(SELECT 1 FROM bale_commitment k WHERE k.order_id=o.id),
              EXISTS(SELECT 1 FROM order_result_version v WHERE v.order_id=o.id)
                OR EXISTS(SELECT 1 FROM dispatch_record d WHERE d.order_id=o.id),
              EXISTS(SELECT 1 FROM order_submission_slot t WHERE t.order_id=o.id AND t.current_submission_id IS NOT NULL)
            FROM outbound_order o WHERE o.id=?
            """,(rs,n)->SubmissionJson.object().put("activeAssignment",rs.getBoolean(1)).put("unresolvedPc",rs.getBoolean(2))
                .put("unresolvedSubmission",rs.getBoolean(3)).put("stockConflict",rs.getBoolean(4))
                .put("hasResult",rs.getBoolean(5)).put("openSlot",rs.getBoolean(6)),Ids.bytes(id));
    }
    private JsonNode withdrawal(CommandContext context,UUID order,JsonNode input) {
        if(!input.path("basis").asText().equals("WITHDRAWN"))return com.fasterxml.jackson.databind.node.NullNode.instance;
        var rows=jdbc.queryForList("""
            SELECT resolution_receipt FROM order_change_request WHERE id=? AND warehouse_id=? AND order_id=? AND status='WITHDRAWN'
            """,String.class,Ids.bytes(UUID.fromString(input.path("withdrawalRequestId").asText())),
            Ids.bytes(context.id("warehouseId")),Ids.bytes(order));
        if(rows.size()!=1 || rows.getFirst()==null)throw conflict("原故障撤回回执不可取回，不能排除");
        var proof=SubmissionJson.parse(rows.getFirst());
        proofs.verify(proof,"signedReceipt","acrylic-pda-fault-withdrawal","receipt");return proof;
    }
    private static BusinessException conflict(String message) { return new BusinessException(409,"TEMPORARY_EXCLUSION_CANDIDATE_INVALID",message); }
}
