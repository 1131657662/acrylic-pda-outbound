package com.acrylic.outbound.e2averify;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Test-only V34 business seed; inserts real rows and never uses marker tables. */
public final class E2aV34Seed {
    private E2aV34Seed() { }

    public static Map<String, String> seed(JdbcTemplate jdbc, Path storage) throws Exception {
        Map<String, String> result = new LinkedHashMap<>();
        var source = jdbc.queryForMap("SELECT BIN_TO_UUID(c.id) scopeId,BIN_TO_UUID(c.warehouse_id) warehouseId,BIN_TO_UUID(s.id) submissionId,s.work_round workRound,BIN_TO_UUID(s.freeze_device_id) deviceId,BIN_TO_UUID(s.operator_id) operatorId FROM submission_scope c JOIN submission s ON s.scope_id=c.id WHERE c.creator_device_id IS NOT NULL ORDER BY c.id LIMIT 1");
        String scope = (String) source.get("scopeId");
        String submission = (String) source.get("submissionId");
        String warehouse = (String) source.get("warehouseId");
        long round = ((Number) source.get("workRound")).longValue();
        result.put("scopeId", scope); result.put("submissionId", submission); result.put("warehouseId", warehouse); result.put("workRound", Long.toString(round));
        String object = UUID.randomUUID().toString();
        Path file = storage.resolve("e2a-v34-" + object + ".json"); Files.createDirectories(storage); Files.writeString(file, "{\"seed\":\"e2a-v34\"}");
        byte[] bytes = Files.readAllBytes(file); String sha = hex(bytes);
        jdbc.update("INSERT INTO source_evidence_object(id,warehouse_id,scope_id,submission_id,work_round,kind,original_object_id,bytes_sha256,byte_count,file_key,origin_device_id,uploaded_by,received_at) SELECT UUID_TO_BIN(?),c.warehouse_id,c.id,s.id,s.work_round,'STOP_PROOF',UUID_TO_BIN(?),?,?,?,s.freeze_device_id,s.operator_id,UTC_TIMESTAMP(3) FROM submission s JOIN submission_scope c ON c.id=s.scope_id WHERE s.id=UUID_TO_BIN(?)", object, object, sha, bytes.length, file.getFileName().toString(), submission);
        String set = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO source_evidence_set(id,warehouse_id,scope_id,submission_id,work_round,stop_object_id,manifest_bytes,set_sha256,capture_high_watermark,audit_through_seq,item_count,received_at) SELECT UUID_TO_BIN(?),warehouse_id,scope_id,submission_id,work_round,UUID_TO_BIN(?),CAST('{\"seed\":true}' AS BINARY),?,0,1,0,UTC_TIMESTAMP(3) FROM source_evidence_object WHERE id=UUID_TO_BIN(?)", set, object, sha, object);
        String verification = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO source_evidence_verification(id,set_id,scope_id,submission_id,work_round,verification_version,algorithm_version,set_sha256,result,known_facts_sha256,findings_bytes,verified_at) SELECT UUID_TO_BIN(?),id,scope_id,submission_id,work_round,1,1,set_sha256,'PARTIAL',REPEAT('a',64),CAST('{}' AS BINARY),UTC_TIMESTAMP(3) FROM source_evidence_set WHERE id=UUID_TO_BIN(?)", verification, set);
        result.put("objectId", object); result.put("setId", set); result.put("verificationId", verification); result.put("fileKey", file.toString()); result.put("fileSha256", sha);
        return result;
    }

    private static String hex(byte[] bytes) throws Exception { var digest = MessageDigest.getInstance("SHA-256"); var out = digest.digest(bytes); var text = new StringBuilder(64); for (byte value : out) text.append(String.format("%02x", value)); return text.toString(); }
}
