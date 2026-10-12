package com.acrylic.outbound.correction;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** A PC response is an unsigned database original; its recorded actor, idempotency record and audit must agree. */
@Component
final class PcQuarantineReceipts {
    private final JdbcTemplate jdbc;
    PcQuarantineReceipts(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    ObjectNode read(JsonNode origin,Map<String,Object> proof) {
        var result=SubmissionJson.object();
        result.set("stop",receipt(origin,"EXCEPTION_STOP","caseId",origin.path("caseId").asText(),proof.get("stopped_by")));
        result.set("plan",receipt(origin,"EXCEPTION_PLAN","planId",origin.path("planId").asText(),proof.get("requested_by")));
        result.set("review",receipt(origin,"EXCEPTION_REVIEW","planId",origin.path("planId").asText(),proof.get("approved_by")));
        result.set("apply",receipt(origin,"EXCEPTION_APPLY","planId",origin.path("planId").asText(),proof.get("applied_by")));
        if(!result.at("/plan/result/payloadSha256").asText().equals(proof.get("payload_sha256"))) throw CorrectionCommand.corrupt();
        return result;
    }
    private JsonNode receipt(JsonNode origin,String action,String field,String id,Object actor) {
        var rows=jdbc.queryForList("""
            SELECT r.body_json,i.request_sha256 FROM pc_command_receipt r JOIN api_idempotency_request i ON i.resource_id=r.id
            WHERE r.order_id=UUID_TO_BIN(?) AND i.route_scope=? AND i.principal_id=UUID_TO_BIN(?)
              AND JSON_UNQUOTE(JSON_EXTRACT(r.body_json,?))=?
            """,origin.path("orderId").asText(),"pc."+action,actor,"$.result."+field,id);
        if(rows.size()!=1) throw CorrectionCommand.corrupt();
        var response=SubmissionJson.parse(rows.getFirst().get("body_json").toString());
        if(!action.equals(response.path("action").asText()) || !response.path("orderId").equals(origin.path("orderId"))
            || !rows.getFirst().get("request_sha256").toString().matches("[a-f0-9]{64}")) throw CorrectionCommand.corrupt();
        String status=java.util.Map.of("EXCEPTION_STOP","STOPPED","EXCEPTION_PLAN","PENDING","EXCEPTION_REVIEW","APPROVED","EXCEPTION_APPLY","APPLIED").get(action);
        if(!status.equals(response.at("/result/status").asText())) throw CorrectionCommand.corrupt();
        var audits=jdbc.queryForList("""
            SELECT details FROM operation_event WHERE actor_id=UUID_TO_BIN(?) AND event_type=? AND entity_type='ORDER'
              AND entity_id=UUID_TO_BIN(?) AND JSON_UNQUOTE(JSON_EXTRACT(details,'$.receiptId'))=?
            """,actor,"PC_"+action,origin.path("orderId").asText(),response.path("receiptId").asText());
        if(audits.size()!=1 || !response.equals(SubmissionJson.parse(audits.getFirst().get("details").toString()))) throw CorrectionCommand.corrupt();
        return response;
    }
}
