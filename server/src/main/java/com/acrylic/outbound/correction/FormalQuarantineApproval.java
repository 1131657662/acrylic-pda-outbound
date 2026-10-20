package com.acrylic.outbound.correction;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Compare original command bytes, original response and every recorded physical row. */
@Component
final class FormalQuarantineApproval {
    private final JdbcTemplate jdbc;
    FormalQuarantineApproval(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    ObjectNode read(JsonNode origin,JsonNode recovery) {
        var rows=jdbc.query("""
            SELECT r.command_bytes,r.command_sha256,r.response_bytes,r.response_sha256,r.request_sha256,
              BIN_TO_UUID(i.id) request_id,i.request_sha256 idempotency_sha256
            FROM formal_disposition_receipt r JOIN api_idempotency_request i ON i.resource_id=r.approval_batch_id
              AND i.principal_id=r.principal_id AND i.route_scope='order.disposition.approve'
            WHERE r.approval_batch_id=UUID_TO_BIN(?) AND r.recovery_package_id=UUID_TO_BIN(?) AND r.principal_id=UUID_TO_BIN(?)
            """,(rs,n)-> {
                var row=new java.util.HashMap<String,Object>();
                for(String column:java.util.List.of("command_bytes","response_bytes")) row.put(column,rs.getBytes(column));
                for(String column:java.util.List.of("command_sha256","response_sha256","request_sha256","request_id","idempotency_sha256"))
                    row.put(column,rs.getString(column));
                return row;
            },origin.path("approvalBatchId").asText(),origin.path("recoveryPackageId").asText(),origin.path("approvedBy").asText());
        if(rows.size()!=1) throw CorrectionCommand.corrupt(); var row=rows.getFirst();
        var command=bytes(row,"command"); var response=bytes(row,"response");
        var request=SubmissionJson.object().put("packageId",origin.path("recoveryPackageId").asText()); request.set("command",command);
        if(!SubmissionJson.sha(request).equals(row.get("request_sha256")) || !row.get("request_sha256").equals(row.get("idempotency_sha256"))
            || !command.path("payloadSha256").equals(recovery.path("payloadSha256")) || !command.path("expectedVersion").asText().matches("[1-9][0-9]*")) throw CorrectionCommand.corrupt();
        var expected=verifyRows(origin,command); if(!expected.equals(response)) throw CorrectionCommand.corrupt();
        var result=SubmissionJson.object().put("batchId",origin.path("approvalBatchId").asText()).put("requestId",row.get("request_id").toString())
            .put("requestSha256",row.get("request_sha256").toString());
        result.set("command",command); result.set("response",response); result.set("evidence",expected.path("evidence")); return result;
    }
    private JsonNode bytes(java.util.Map<String,Object> row,String field) {
        byte[] bytes=(byte[])row.get(field+"_bytes"); var parsed=SubmissionJson.parse(bytes);
        if(!com.acrylic.outbound.inventory.ImportFiles.sha256(bytes).equals(row.get(field+"_sha256"))
            || !SubmissionJson.canonical(parsed).equals(new String(bytes,java.nio.charset.StandardCharsets.UTF_8))) throw CorrectionCommand.corrupt();
        return parsed;
    }
    private ObjectNode verifyRows(JsonNode origin,JsonNode command) {
        var rows=jdbc.query("""
            SELECT JSON_OBJECT('approvedEvidenceId',BIN_TO_UUID(id),'captureId',BIN_TO_UUID(origin_scan_id),'baleId',BIN_TO_UUID(bale_id),
              'expectedDispatchCycle',CAST(expected_cycle AS CHAR),'action',action,'physicalEvidence',physical_evidence,
              'recoveryPackageId',BIN_TO_UUID(recovery_package_id)) data, BIN_TO_UUID(approved_by) actor
            FROM physical_disposition_evidence WHERE approval_batch_id=UUID_TO_BIN(?) ORDER BY id
            """,(rs,n)->java.util.Map.of("data",rs.getString("data"),"actor",rs.getString("actor")),origin.path("approvalBatchId").asText());
        if(rows.size()!=command.path("dispositions").size() || rows.isEmpty()) throw CorrectionCommand.corrupt();
        var result=SubmissionJson.object().put("stockReleased",false); var evidence=result.putArray("evidence");
        for(var row:rows) {
            var proof=SubmissionJson.parse(row.get("data").toString()); var inputs=java.util.stream.StreamSupport.stream(command.path("dispositions").spliterator(),false)
                .filter(d->d.path("captureId").equals(proof.path("captureId"))).toList();
            if(inputs.size()!=1 || !row.get("actor").equals(origin.path("approvedBy").asText())) throw CorrectionCommand.corrupt();
            for(String key:java.util.List.of("expectedDispatchCycle","action","physicalEvidence")) if(!inputs.getFirst().path(key).equals(proof.path(key))) throw CorrectionCommand.corrupt();
            evidence.add(proof);
        }
        return result;
    }
}
