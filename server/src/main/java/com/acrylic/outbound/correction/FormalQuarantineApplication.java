package com.acrylic.outbound.correction;

import com.acrylic.outbound.submissions.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Only an authenticated original application can establish an applied isolation. */
@Component
final class FormalQuarantineApplication {
    private final JdbcTemplate jdbc;
    private final SubmissionFiles files;
    private final SubmissionArchiveProofs signatures;
    FormalQuarantineApplication(JdbcTemplate jdbc,SubmissionFiles files,SubmissionArchiveProofs signatures) {
        this.jdbc=jdbc; this.files=files; this.signatures=signatures;
    }
    ObjectNode read(JsonNode origin,JsonNode recovery) {
        var rows=jdbc.queryForList("""
            SELECT c.command_text,c.receipt_text,c.activation_text,c.status,c.to_round,c.recovery_package_id,
              s.receipt_text applied_receipt,s.status applied_status,p.payload_sha256,BIN_TO_UUID(s.freeze_device_id) device_id,
              BIN_TO_UUID(c.approved_by) approved_by,BIN_TO_UUID(c.requested_by) requested_by
            FROM order_continuation c JOIN submission s ON s.assignment_id=c.new_assignment_id
              JOIN submission_payload p ON p.submission_id=s.id WHERE c.id=UUID_TO_BIN(?) AND s.id=UUID_TO_BIN(?)
            """,origin.path("continuationId").asText(),origin.path("submissionId").asText());
        if(rows.size()!=1) throw CorrectionCommand.corrupt(); var row=rows.getFirst();
        if(row.get("receipt_text")==null || row.get("activation_text")==null || row.get("applied_receipt")==null
            || !java.util.Set.of("SUCCEEDED","ABORTED").contains(row.get("applied_status"))
            || !java.util.Set.of("ACTIVATED","COMPLETED").contains(row.get("status"))) throw CorrectionCommand.corrupt();
        var command=SubmissionJson.parse(row.get("command_text").toString()); var body=SubmissionJson.parse(files.read(row.get("payload_sha256").toString()));
        if(!"FORMAL".equals(body.at("/source/kind").asText()) || !body.at("/source/sourceOrderId").equals(origin.path("orderId"))
            || !body.path("submissionId").equals(origin.path("submissionId")) || !body.path("assignmentId").equals(origin.path("appliedAssignmentId"))
            || !body.path("continuationId").equals(origin.path("continuationId")) || !body.path("workRound").equals(origin.path("workRound"))
            || !body.path("freezeDeviceId").equals(recovery.at("/body/freezeDeviceId"))) throw CorrectionCommand.corrupt();
        unchanged(recovery.path("body"),body); disposition(command,body,origin);
        var approval=receipt(row.get("receipt_text"),"acrylic-pda-continuation"); var activation=receipt(row.get("activation_text"),"acrylic-pda-continuation");
        var applied=receipt(row.get("applied_receipt"),"acrylic-pda-receipt"); bind(origin,recovery,approval,activation,applied,row.get("payload_sha256").toString());
        continuation(origin,recovery,command,body,approval,activation,row.get("requested_by").toString());
        var result=SubmissionJson.object(); result.set("command",command); result.set("approval",approval); result.set("activation",activation);
        result.set("body",body); result.set("receipt",applied); result.put("payloadSha256",row.get("payload_sha256").toString());
        result.put("approvedBy",row.get("approved_by").toString()); result.put("requestedBy",row.get("requested_by").toString()); return result;
    }
    private JsonNode receipt(Object text,String audience) {
        var receipt=SubmissionJson.parse(text.toString()); signatures.verify(receipt,"signedReceipt",audience,"receipt"); return receipt;
    }
    private void unchanged(JsonNode previous,JsonNode body) {
        for(String field:java.util.List.of("captures","scans","events")) {
            var before=previous.path(field); var after=body.path(field);
            if(!before.isArray() || !after.isArray() || after.size()<before.size()) throw CorrectionCommand.corrupt();
            for(int i=0;i<before.size();i++) if(!before.get(i).equals(after.get(i))) throw CorrectionCommand.corrupt();
        }
    }
    private void disposition(JsonNode command,JsonNode body,JsonNode origin) {
        var original=java.util.stream.StreamSupport.stream(command.path("dispositions").spliterator(),false)
            .filter(d->d.path("approvedEvidenceId").equals(origin.path("dispositionId"))).toList();
        var applied=java.util.stream.StreamSupport.stream(body.path("dispositions").spliterator(),false)
            .filter(d->d.path("approvedEvidenceId").equals(origin.path("dispositionId"))).toList();
        if(original.size()!=1 || applied.size()!=1) throw CorrectionCommand.corrupt(); var proof=applied.getFirst();
        if(!"QUARANTINE".equals(original.getFirst().path("action").asText()) || !"QUARANTINE".equals(proof.path("action").asText())
            || !proof.path("baleId").equals(origin.path("baleId")) || !proof.path("captureId").equals(origin.path("captureId"))
            || !proof.path("expectedDispatchCycle").equals(origin.path("cycle"))) throw CorrectionCommand.corrupt();
    }
    private void bind(JsonNode origin,JsonNode recovery,JsonNode approval,JsonNode activation,JsonNode applied,String hash) {
        for(var value:java.util.List.of(approval,activation)) {
            if(!value.path("continuationId").equals(origin.path("continuationId")) || !value.path("recoveryPackageId").equals(origin.path("recoveryPackageId"))
                || !value.path("workRound").equals(origin.path("workRound")) || !value.path("issuedRecoveryEpoch").equals(origin.path("recoveryEpoch"))) throw CorrectionCommand.corrupt();
        }
        if(!"APPROVED".equals(approval.path("status").asText()) || !"ACTIVATED".equals(activation.path("status").asText())
            || !approval.path("assignmentId").equals(origin.path("appliedAssignmentId")) || !activation.at("/assignment/id").equals(origin.path("appliedAssignmentId"))
            || !activation.path("payloadSha256").equals(recovery.path("payloadSha256"))
            || !applied.path("submissionId").equals(origin.path("submissionId")) || !applied.path("orderId").equals(origin.path("orderId"))
            || !applied.path("payloadSha256").asText().equals(hash) || !java.util.Set.of("SUCCEEDED","ABORTED").contains(applied.path("status").asText())) throw CorrectionCommand.corrupt();
    }
    private void continuation(JsonNode origin,JsonNode recovery,JsonNode command,JsonNode body,JsonNode approval,JsonNode activation,String actor) {
        var expected=SubmissionJson.object().put("orderId",origin.path("orderId").asText()); expected.set("command",command);
        var hashes=jdbc.queryForList("SELECT request_sha256 FROM api_idempotency_request WHERE route_scope='order.continuation.request' AND resource_id=UUID_TO_BIN(?) AND principal_id=UUID_TO_BIN(?)",
            String.class,origin.path("continuationId").asText(),actor);
        var proofs=com.acrylic.outbound.continuation.DispositionProofs.compose(recovery.path("body"),command);
        var retained=com.acrylic.outbound.continuation.DispositionProofs.effective(recovery.path("body"),proofs);
        if(hashes.size()!=1 || !SubmissionJson.sha(expected).equals(hashes.getFirst()) || !proofs.equals(body.path("dispositions"))
            || !proofs.equals(activation.path("dispositions")) || !SubmissionJson.sha(retained).equals(approval.path("retainedManifestSha256").asText())
            || !approval.path("retainedManifestSha256").equals(activation.path("retainedManifestSha256"))) throw CorrectionCommand.corrupt();
    }
}
