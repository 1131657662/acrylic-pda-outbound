package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.temporary.command.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Synthetic STOP/verification input only. The accepted predecessor, ticket and later HTTP are real. */
final class TemporaryHigherRoundStopSeed {
    private final TemporaryRoundConfirmationHttpFixture f;
    private final JsonNode body;
    final UUID set=UUID.randomUUID(),verification=UUID.randomUUID(),object=UUID.randomUUID();
    TemporaryHigherRoundStopSeed(TemporaryRoundConfirmationHttpFixture fixture) throws Exception {
        f=fixture;body=f.body.deepCopy();
        String marker=canonical(object().put("fixture","synthetic STOP; not original device proof")
            .put("submissionId",text(body,"submissionId")).put("payloadSha256",sha(body)));
        String hash=sha(parse(marker));String key="synthetic-stop-"+object+".json";
        Files.writeString(f.http.database.profile.attempt.resolve("storage").resolve(key),marker,StandardOpenOption.CREATE_NEW);
        f.http.database.tx.executeWithoutResult(status->{
            insert(marker,key,hash);retain(hash);
        });
    }
    private void insert(String marker,String key,String hash) {
        var j=f.http.database.jdbc;String scope=f.http.database.seed.get("mappedScopeId"),warehouse=f.http.database.seed.get("warehouseId");
        String submission=text(body,"submissionId");long round=number(body,"workRound");
        j.update("UPDATE submission SET status='ABORTED',receipt_text=? WHERE id=UUID_TO_BIN(?) AND status='REVIEW_REQUIRED'",
            canonical(object().put("status","ABORTED").put("fixture","synthetic STOP input")),submission);
        j.update("""
            INSERT INTO source_evidence_object(id,warehouse_id,scope_id,submission_id,work_round,kind,original_object_id,
              bytes_sha256,byte_count,file_key,origin_device_id,uploaded_by,received_at)
            VALUES(?,UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),?,'STOP_PROOF',?,?,?,?,UUID_TO_BIN(?),UUID_TO_BIN(?),UTC_TIMESTAMP(3))
            """,Ids.bytes(object),warehouse,scope,submission,round,Ids.bytes(object),hash,marker.getBytes(StandardCharsets.UTF_8).length,key,
            text(body,"freezeDeviceId"),f.http.database.seed.get("operatorId"));
        j.update("""
            INSERT INTO source_evidence_set(id,warehouse_id,scope_id,submission_id,work_round,stop_object_id,manifest_bytes,
              set_sha256,capture_high_watermark,audit_through_seq,item_count,received_at,order_scan_high_watermark)
            VALUES(?,UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),?,?,?,?,?,?,?,UTC_TIMESTAMP(3),?)
            """,Ids.bytes(set),warehouse,scope,submission,round,Ids.bytes(object),marker.getBytes(StandardCharsets.UTF_8),hash,
            number(body,"captureHighWatermark"),array(body,"events").size(),array(body,"scans").size(),number(body,"captureHighWatermark"));
        for(var scan:array(body,"scans"))scan(scan);
        j.update("""
            INSERT INTO source_evidence_verification(id,set_id,scope_id,submission_id,work_round,verification_version,
              algorithm_version,set_sha256,result,known_facts_sha256,findings_bytes,verified_at)
            VALUES(?,?,UUID_TO_BIN(?),UUID_TO_BIN(?),?,1,1,?,'VERIFIED_COMPLETE_AT_STOP',?,?,UTC_TIMESTAMP(3))
            """,Ids.bytes(verification),Ids.bytes(set),scope,submission,round,hash,sha(body),marker.getBytes(StandardCharsets.UTF_8));
    }
    private void scan(JsonNode scan) {
        f.http.database.jdbc.update("""
            INSERT INTO source_evidence_item(set_id,warehouse_id,scope_id,submission_id,work_round,item_kind,
              original_record_id,object_id,original_device_seq,record_sha256,bale_id,original_cycle,capture_id,record_status)
            VALUES(?,UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),?,'SCAN',UUID_TO_BIN(?),?,?,?,UUID_TO_BIN(?),?,UUID_TO_BIN(?),'ACTIVE')
            """,Ids.bytes(set),f.http.database.seed.get("warehouseId"),f.http.database.seed.get("mappedScopeId"),text(body,"submissionId"),
            number(body,"workRound"),text(scan,"captureId"),Ids.bytes(object),number(scan,"orderScanSeq"),sha(scan),text(scan,"baleId"),
            number(scan,"expectedDispatchCycle"),text(scan,"captureId"));
    }
    private void retain(String hash) {
        var db=f.http.database;
        var proof=new PhysicalDispositionVerification(db.jdbc).readForContinuation(db.id("warehouseId"),db.id("mappedScopeId"),
            uuid(body,"submissionId"),number(body,"workRound"),set,verification,hash,1,1);
        db.apply(proof,Collections.nCopies(proof.activeBales().size(),"RETAIN"));
    }
}
