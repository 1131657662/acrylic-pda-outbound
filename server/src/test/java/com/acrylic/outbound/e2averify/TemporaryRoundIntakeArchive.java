package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.temporary.evidence.SourceEvidenceIntakeObjectFiles;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Separates actual previously accepted rows from a local frozen predecessor with no business payload. */
final class TemporaryRoundIntakeArchive {
    private final RetentionDatabaseFixture f;
    TemporaryRoundIntakeArchive(RetentionDatabaseFixture f) { this.f=f; }
    void store(JsonNode body,boolean local) throws java.io.IOException {
        byte[] bytes=canonical(body).getBytes(java.nio.charset.StandardCharsets.UTF_8);String hash=sha(body);
        if(local) { local(bytes,hash);return; }
        f.jdbc.update("INSERT INTO submission_payload VALUES(UUID_TO_BIN(?),2,?,?,?,?,?,?,UTC_TIMESTAMP(3))",
            f.seed.get("mappedSubmissionId"),hash,"submissions/"+hash+".json",bytes.length,3,3000,4);
        for(var c:array(body,"captures"))f.jdbc.update("INSERT INTO submitted_capture VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),?,?,?)",
            text(c,"id"),f.seed.get("mappedSubmissionId"),f.seed.get("orderId"),f.seed.get("deviceId"),number(c,"deviceCaptureSeq"),number(c,"orderScanSeq"),sha(c));
        for(var e:array(body,"events"))f.jdbc.update("INSERT INTO submitted_event VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),?,?)",
            text(e,"id"),f.seed.get("mappedSubmissionId"),f.seed.get("deviceId"),number(e,"localSeq"),sha(e));
    }
    private void local(byte[] bytes,String hash) throws java.io.IOException {
        String object=UUID.randomUUID().toString();
        var stored=new SourceEvidenceIntakeObjectFiles(f.profile.attempt.resolve("storage").toString())
            .store(new java.io.ByteArrayInputStream(bytes),bytes.length,hash);
        f.jdbc.update("""
            INSERT INTO source_evidence_object(id,warehouse_id,scope_id,submission_id,work_round,kind,original_object_id,
              bytes_sha256,byte_count,file_key,origin_device_id,uploaded_by,received_at)
            VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),1,'LOCAL_FROZEN_PAYLOAD',UUID_TO_BIN(?),?,?,?,UUID_TO_BIN(?),UUID_TO_BIN(?),UTC_TIMESTAMP(3))
            """,object,f.seed.get("warehouseId"),f.seed.get("mappedScopeId"),f.seed.get("mappedSubmissionId"),UUID.randomUUID().toString(),
            hash,bytes.length,stored.key(),f.seed.get("deviceId"),f.seed.get("operatorId"));
        f.jdbc.update("""
            INSERT INTO source_evidence_item(set_id,warehouse_id,scope_id,submission_id,work_round,item_kind,
              original_record_id,object_id,original_device_seq,record_sha256,record_status)
            VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),1,'STOP',UUID_TO_BIN(?),UUID_TO_BIN(?),1,?,'FROZEN')
            """,f.seed.get("mappedSetId"),f.seed.get("warehouseId"),f.seed.get("mappedScopeId"),f.seed.get("mappedSubmissionId"),
            f.seed.get("mappedSubmissionId"),object,hash);
        f.jdbc.update("UPDATE source_evidence_set SET item_count=5 WHERE id=UUID_TO_BIN(?)",f.seed.get("mappedSetId"));
    }
}
