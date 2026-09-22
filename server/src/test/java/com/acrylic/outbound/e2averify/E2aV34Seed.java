package com.acrylic.outbound.e2averify;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Pure test seed for V34 non-empty migration checks; never creates marker tables. */
public final class E2aV34Seed {
    private E2aV34Seed() { }

    public static Map<String, String> seed(JdbcTemplate jdbc, Path storage) throws Exception {
        E2aV34BaseFixture.ensure(jdbc);
        requireSyntheticV34Base(jdbc);
        var template = jdbc.queryForMap("SELECT BIN_TO_UUID(c.warehouse_id) warehouseId,BIN_TO_UUID(c.source_order_id) orderId,BIN_TO_UUID(c.creator_device_id) deviceId,BIN_TO_UUID(s.id) submissionId,BIN_TO_UUID(s.operator_id) operatorId,BIN_TO_UUID(s.assignment_id) assignmentId,BIN_TO_UUID(s.temporary_authorization_id) authorizationId FROM submission_scope c JOIN submission s ON s.scope_id=c.id WHERE c.creator_device_id IS NOT NULL AND c.canonical_order_id IS NOT NULL ORDER BY c.id LIMIT 1");
        var result = new LinkedHashMap<String, String>();
        String warehouse=(String)template.get("warehouseId"), device=(String)template.get("deviceId"), operator=(String)template.get("operatorId");
        String sourceOrder=(String)template.get("orderId"), assignment=(String)template.get("assignmentId"), authorization=(String)template.get("authorizationId");
        for(String mode: new String[]{"mapping0","mapped","abort"}) {
            String scope=UUID.randomUUID().toString(), submission=UUID.randomUUID().toString(), clientOrder=UUID.randomUUID().toString();
            String mapping = UUID.randomUUID().toString();
            requireOne(jdbc.update("INSERT INTO submission_scope(id,warehouse_id,source_order_id,work_round,creator_device_id,client_order_id,canonical_order_id,mapping_version,current_submission_id) SELECT UUID_TO_BIN(?),c.warehouse_id,c.source_order_id,1,c.creator_device_id,UUID_TO_BIN(?),CASE WHEN ?=0 THEN NULL ELSE c.canonical_order_id END,?,NULL FROM submission_scope c WHERE c.source_order_id=UUID_TO_BIN(?) LIMIT 1", scope, clientOrder, "mapping0".equals(mode) ? 0 : 1, "mapping0".equals(mode) ? 0 : 1, sourceOrder);
            jdbc.update("INSERT INTO temporary_work_origin(creator_device_id,client_order_id,authorization_id) SELECT creator_device_id,UUID_TO_BIN(?),temporary_authorization_id FROM submission s WHERE s.id=UUID_TO_BIN(?)", clientOrder, (String)template.get("submissionId"));
            requireOne(jdbc.update("INSERT INTO temporary_source_mapping(id,warehouse_id,scope_id,mapping_version,canonical_order_id,origin,observed_at) SELECT UUID_TO_BIN(?),c.warehouse_id,UUID_TO_BIN(?),?,CASE WHEN ?=0 THEN NULL ELSE c.canonical_order_id END,'SOURCE_REGISTERED',UTC_TIMESTAMP(3) FROM submission_scope c WHERE c.id=UUID_TO_BIN(?)", mapping, scope, "mapping0".equals(mode) ? 0 : 1, "mapping0".equals(mode) ? 0 : 1, scope));
            requireOne(jdbc.update("INSERT INTO temporary_decision_state(scope_id,decision_version,origin,initialized_at) VALUES(UUID_TO_BIN(?),0,'SOURCE_REGISTERED',UTC_TIMESTAMP(3))", scope));
            requireOne(jdbc.update("INSERT INTO submission(id,scope_id,assignment_id,temporary_authorization_id,freeze_device_id,operator_id,work_round,source_recovery_epoch,status,created_at,updated_at) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),1,1,?,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))", submission, scope, assignment, authorization, device, operator, "abort".equals(mode)?"ABORTED":"REVIEW_REQUIRED"));
            requireOne(jdbc.update("UPDATE submission_scope SET current_submission_id=UUID_TO_BIN(?) WHERE id=UUID_TO_BIN(?)", submission, scope));
            result.put(mode+"ScopeId",scope); result.put(mode+"SubmissionId",submission); result.put(mode+"ClientOrderId",clientOrder); result.put(mode+"MappingId",mapping);
            seedEvidence(jdbc,storage,result,mode,warehouse,scope,submission,device,operator);
            if ("mapped".equals(mode)) seedReserved(jdbc,result,warehouse,scope,submission);
        }
        seedCommandReceipt(jdbc,result,"mapped",warehouse,device,operator,authorization);
        return result;
    }

    private static void seedEvidence(JdbcTemplate jdbc,Path storage,Map<String,String> result,String mode,String warehouse,String scope,String submission,String device,String operator) throws Exception {
        String object=UUID.randomUUID().toString(), set=UUID.randomUUID().toString(), verification=UUID.randomUUID().toString(); Path file=storage.resolve("e2a-v34-"+mode+"-"+object+".json"); Files.createDirectories(storage); Files.writeString(file,"{\"mode\":\""+mode+"\"}",java.nio.file.StandardOpenOption.CREATE_NEW); byte[] bytes=Files.readAllBytes(file); String sha=sha(bytes); String fileKey=file.getFileName().toString();
        requireOne(jdbc.update("INSERT INTO source_evidence_object(id,warehouse_id,scope_id,submission_id,work_round,kind,original_object_id,bytes_sha256,byte_count,file_key,origin_device_id,uploaded_by,received_at) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),1,'STOP_PROOF',UUID_TO_BIN(?),?,?,?,UUID_TO_BIN(?),UUID_TO_BIN(?),UTC_TIMESTAMP(3))",object,warehouse,scope,submission,object,sha,bytes.length,fileKey,device,operator));
        requireOne(jdbc.update("INSERT INTO source_evidence_set(id,warehouse_id,scope_id,submission_id,work_round,stop_object_id,manifest_bytes,set_sha256,capture_high_watermark,audit_through_seq,item_count,received_at) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),1,UUID_TO_BIN(?),CAST('{\"mode\":\""+mode+"\"}' AS BINARY),?,0,1,0,UTC_TIMESTAMP(3))",set,warehouse,scope,submission,object,sha));
        requireOne(jdbc.update("INSERT INTO source_evidence_item(set_id,warehouse_id,scope_id,submission_id,work_round,item_kind,original_record_id,object_id,original_device_seq,record_sha256,bale_id,original_cycle,capture_id,record_status) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),1,'STOP',UUID_TO_BIN(?),UUID_TO_BIN(?),1,?,NULL,NULL,NULL,'ACTIVE')",set,warehouse,scope,submission,UUID.randomUUID().toString(),object,sha));
        requireOne(jdbc.update("INSERT INTO source_evidence_verification(id,set_id,scope_id,submission_id,work_round,verification_version,algorithm_version,set_sha256,result,known_facts_sha256,findings_bytes,verified_at) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),1,1,1,?,'PARTIAL',REPEAT('a',64),CAST('{}' AS BINARY),UTC_TIMESTAMP(3))",verification,set,scope,submission,sha));
        result.put(mode+"ObjectId",object); result.put(mode+"SetId",set); result.put(mode+"VerificationId",verification); result.put(mode+"File",file.toString());
    }

    private static void seedCommandReceipt(JdbcTemplate jdbc,Map<String,String> result,String mode,String warehouse,String device,String operator,String authorization) {
        String scope=result.get(mode+"ScopeId"), submission=result.get(mode+"SubmissionId"), receipt=UUID.randomUUID().toString(), request=UUID.randomUUID().toString(), mapping=result.get(mode+"MappingId");
        jdbc.update("INSERT INTO api_idempotency_request(id,principal_id,route_scope,idempotency_key,request_sha256,resource_id,created_at) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),'r06.e2a.seed',?,REPEAT('a',64),UUID_TO_BIN(?),UTC_TIMESTAMP(3))",request,operator,receipt,submission);
        jdbc.update("INSERT INTO temporary_command_receipt(id,api_request_id,warehouse_id,scope_id,submission_id,work_round,creator_device_id,client_order_id,temporary_authorization_id,original_operator_id,source_recovery_epoch,original_mapping_id,command_version,command_type,canonical_command_bytes,command_sha256,receipt_version,original_response_bytes,original_response_sha256,unsigned_receipt_sha256,signature_key_id,signed_receipt,recorded_at) SELECT UUID_TO_BIN(?),UUID_TO_BIN(?),warehouse_id,scope_id,id,1,UUID_TO_BIN(?),client_order_id,temporary_authorization_id,operator_id,1,UUID_TO_BIN(?),1,'NORMAL_CONFIRM',CAST('{}' AS BINARY),REPEAT('a',64),1,CAST('{}' AS BINARY),REPEAT('b',64),REPEAT('c',64),'e2a-seed','seed',UTC_TIMESTAMP(3) FROM submission_scope c JOIN submission s ON s.scope_id=c.id WHERE c.id=UUID_TO_BIN(?)",receipt,request,device,mapping,scope);
        result.put("commandReceiptId",receipt); result.put("commandRequestId",request);
    }
    private static void seedReserved(JdbcTemplate jdbc,Map<String,String> result,String warehouse,String scope,String submission) {
        String bale=jdbc.queryForObject("SELECT BIN_TO_UUID(b.id) FROM bale b WHERE b.warehouse_id=UUID_TO_BIN(?) AND NOT EXISTS(SELECT 1 FROM bale_commitment k WHERE k.bale_id=b.id) ORDER BY b.id LIMIT 1",String.class,warehouse);
        jdbc.update("INSERT INTO bale_commitment(bale_id,order_id,submission_id,dispatch_cycle,state,created_at,source_scope_id,source_evidence_set_id,source_warehouse_id) VALUES(UUID_TO_BIN(?),NULL,NULL,1,'SOURCE_RESERVED',UTC_TIMESTAMP(3),UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?))",bale,scope,result.get("mappedSetId"),warehouse);
        result.put("reservedBaleId",bale);
    }
    private static void requireOne(int rows) { if(rows != 1) throw new IllegalStateException("E2a seed expected one row, got " + rows); }
    private static String sha(byte[] bytes) throws Exception { var d=MessageDigest.getInstance("SHA-256"); var out=d.digest(bytes); var s=new StringBuilder(64); for(byte b:out)s.append(String.format("%02x",b)); return s.toString(); }

    private static void requireSyntheticV34Base(JdbcTemplate jdbc) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM submission_scope c JOIN submission s ON s.scope_id=c.id JOIN order_assignment a ON a.id=s.assignment_id JOIN temporary_authorization t ON t.id=s.temporary_authorization_id JOIN inventory_snapshot i ON i.id=t.inventory_snapshot_id JOIN policy_version p ON p.id=t.policy_id WHERE c.creator_device_id IS NOT NULL", Integer.class);
        if (count == null || count == 0) throw new IllegalStateException("E2aV34Seed requires a pre-seeded synthetic V34 identity/order/authorization/inventory fixture; it will not clone an existing or real row");
    }
}
