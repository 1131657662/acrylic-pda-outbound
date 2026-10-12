package com.acrylic.outbound.correction;

import com.acrylic.outbound.submissions.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Re-read private original bytes and the signed STOP, then bind the exact active scan. */
@Component
final class FormalQuarantineRecovery {
    private final JdbcTemplate jdbc;
    private final SubmissionFiles files;
    private final SubmissionArchiveProofs signatures;
    FormalQuarantineRecovery(JdbcTemplate jdbc,SubmissionFiles files,SubmissionArchiveProofs signatures) {
        this.jdbc=jdbc; this.files=files; this.signatures=signatures;
    }
    ObjectNode read(JsonNode origin) {
        var rows=jdbc.queryForList("""
            SELECT p.payload_sha256,p.byte_count,p.issues_json,r.stop_receipt,r.recovery_epoch,r.status
            FROM recovery_package p JOIN order_change_request r ON r.id=p.request_id WHERE p.id=UUID_TO_BIN(?)
            """,origin.path("recoveryPackageId").asText());
        if(rows.size()!=1) throw CorrectionCommand.corrupt(); var row=rows.getFirst();
        byte[] bytes=files.read(row.get("payload_sha256").toString()); var body=SubmissionJson.parse(bytes);
        if(bytes.length!=((Number)row.get("byte_count")).intValue() || !SubmissionJson.parse(row.get("issues_json").toString()).isEmpty()
            || row.get("stop_receipt")==null || !"STOPPED".equals(row.get("status"))
            || !row.get("recovery_epoch").toString().equals(origin.path("recoveryEpoch").asText())) throw CorrectionCommand.corrupt();
        var stop=SubmissionJson.parse(row.get("stop_receipt").toString()); signatures.verify(stop,"signedReceipt","acrylic-pda-recall","receipt");
        for(var field:java.util.Map.of("recoveryPackageId","recoveryPackageId","changeRequestId","requestId","orderId","orderId","assignmentId","sourceAssignmentId").entrySet()) {
            if(!body.path(field.getKey()).equals(origin.path(field.getValue())) || !stop.path(field.getKey()).equals(origin.path(field.getValue())))
                throw CorrectionCommand.corrupt();
        }
        if(!"STOPPED".equals(stop.path("status").asText()) || !stop.path("payloadSha256").asText().equals(row.get("payload_sha256"))
            || !stop.path("issuedRecoveryEpoch").equals(origin.path("recoveryEpoch"))) throw CorrectionCommand.corrupt();
        scan(body,origin);
        var result=SubmissionJson.object(); result.set("body",body); result.set("stop",stop);
        result.put("payloadSha256",row.get("payload_sha256").toString()); return result;
    }
    private void scan(JsonNode body,JsonNode origin) {
        var scans=java.util.stream.StreamSupport.stream(body.path("scans").spliterator(),false)
            .filter(s->s.path("captureId").equals(origin.path("captureId"))).toList();
        if(scans.size()!=1) throw CorrectionCommand.corrupt(); var scan=scans.getFirst();
        if(!"ACTIVE".equals(scan.path("status").asText()) || !scan.path("baleId").equals(origin.path("baleId"))
            || !scan.path("expectedDispatchCycle").equals(origin.path("cycle"))
            || !SubmissionJson.sha(scan).equals(origin.path("recordSha256").asText())) throw CorrectionCommand.corrupt();
        for(var disposition:body.path("dispositions")) if(disposition.path("captureId").equals(origin.path("captureId"))) throw CorrectionCommand.corrupt();
    }
}
