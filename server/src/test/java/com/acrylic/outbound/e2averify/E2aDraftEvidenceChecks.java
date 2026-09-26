package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.ImportFiles;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

final class E2aDraftEvidenceChecks {
    private final JdbcTemplate jdbc;
    private final Path attempt;
    private final JsonNode result;
    private final String token,evidence,base;
    private final HttpClient client=HttpClient.newHttpClient();
    private int sequence;
    E2aDraftEvidenceChecks(JdbcTemplate jdbc,Path attempt) throws Exception {
        this.jdbc=jdbc;this.attempt=attempt;
        result=SubmissionJson.parse(Files.readString(attempt.resolve("draft-android-result.json")));
        token=SubmissionJson.parse(Files.readString(attempt.resolve("private/android-http-input.json"))).path("accessToken").asText();
        evidence=result.path("evidenceId").asText();base="http://127.0.0.1:19434/api/v1/pda/local-draft-evidence/"+evidence;
    }
    void database() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM local_draft_evidence",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM local_draft_evidence_complete",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM local_draft_evidence_item WHERE item_kind='ARCHIVE'",Integer.class)).isEqualTo(1);
        int items=jdbc.queryForObject("SELECT COUNT(*) FROM local_draft_evidence_item",Integer.class);
        assertThat(items).isEqualTo(result.path("open").path("proof").path("core").path("recordCount").intValue());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM local_draft_evidence_receipt",Integer.class)).isEqualTo(items+2);
        for(String table:List.of("local_draft_evidence","local_draft_evidence_receipt","local_draft_evidence_item","local_draft_evidence_complete")) {
            assertThatThrownBy(()->jdbc.update("UPDATE "+table+" SET evidence_id=evidence_id WHERE evidence_id=UUID_TO_BIN(?)",evidence)).isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThatThrownBy(()->jdbc.update("DELETE FROM "+table+" WHERE evidence_id=UUID_TO_BIN(?)",evidence)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        }
        assertThatThrownBy(()->jdbc.update("""
            INSERT INTO local_draft_evidence_item SELECT UUID_TO_BIN(?),ordinal,item_kind,record_id,device_seq,record_status,byte_count,bytes_sha256,file_key
            FROM local_draft_evidence_item WHERE evidence_id=UUID_TO_BIN(?) LIMIT 1
            """,UUID.randomUUID().toString(),evidence)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("""
            INSERT INTO local_draft_evidence_receipt SELECT principal_id,UUID_TO_BIN(?),evidence_id,creator_device_id,UUID_TO_BIN(?),operation_kind,
              request_bytes,request_sha256,response_bytes,response_sha256,recorded_at FROM local_draft_evidence_receipt LIMIT 1
            """,UUID.randomUUID().toString(),UUID.randomUUID().toString())).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
    void httpAfterRestart() throws Exception {
        Files.createDirectory(attempt.resolve("draft-http"));byte[] original=bytes(result.path("verifyResponse"));
        String key=result.path("verifyKey").asText();assertThat(send("/receipts/"+key,null,token).body()).isEqualTo(original);
        assertThat(send("/receipts/"+key,null,null).statusCode()).isEqualTo(401);
        rejectChangedOpen();
        jdbc.update("UPDATE server_state SET service_mode='RECOVERY_PENDING' WHERE id=1");
        try {
            var body=SubmissionJson.object().put("evidenceId",evidence).put("rootSha256",result.path("verifyResponse").path("rootSha256").asText());
            assertThat(send("/verify?originalKey="+key,bytes(body),token).body()).isEqualTo(original);
            assertThat(send("/verify?originalKey="+UUID.randomUUID(),bytes(body),token).statusCode()).isEqualTo(503);
        } finally { jdbc.update("UPDATE server_state SET service_mode='NORMAL' WHERE id=1"); }
        corruptSnapshot(key,original);corruptArchive(key,original);
    }
    private void corruptSnapshot(String key,byte[] original) throws Exception {
        var row=jdbc.queryForMap("SELECT file_key,bytes_sha256 FROM local_draft_evidence_item WHERE evidence_id=UUID_TO_BIN(?) ORDER BY ordinal LIMIT 1",evidence);
        Path raw=attempt.resolve("storage").resolve((String)row.get("file_key"));byte[] saved=Files.readAllBytes(raw);
        Files.write(attempt.resolve("draft-original-before-corruption.bin"),saved,StandardOpenOption.CREATE_NEW);
        Files.write(raw,new byte[]{0});assertThat(send("/receipts/"+key,null,token).statusCode()).isEqualTo(409);
        assertThat(ImportFiles.sha256(saved)).isEqualTo(row.get("bytes_sha256"));Files.write(raw,saved);
        assertThat(send("/receipts/"+key,null,token).body()).isEqualTo(original);
    }
    private void corruptArchive(String key,byte[] original) throws Exception {
        String hash=result.path("archiveProof").path("payloadSha256").asText();
        Path file=attempt.resolve("storage/submissions").resolve(hash+".json");byte[] saved=Files.readAllBytes(file);
        Files.write(attempt.resolve("archive-original-before-corruption.bin"),saved,StandardOpenOption.CREATE_NEW);
        Files.write(file,new byte[]{0});assertThat(send("/receipts/"+key,null,token).statusCode()).isEqualTo(409);
        Files.write(file,saved);assertThat(send("/receipts/"+key,null,token).body()).isEqualTo(original);
        assertThat(ImportFiles.sha256(saved)).isEqualTo(hash);
    }
    private void rejectChangedOpen() throws Exception {
        for(String field:List.of("warehouseId","creatorDeviceId","operatorId","authorizationId")) {
            var open=result.path("open").deepCopy();((ObjectNode)open.path("metadata").path("draft")).put(field,UUID.randomUUID().toString());
            assertThat(send("/open?originalKey="+UUID.randomUUID(),bytes(open),token).statusCode()).isIn(400,404,409);
        }
        var open=result.path("open").deepCopy();((ObjectNode)open.path("proof")).put("signature","AAAA");
        assertThat(send("/open?originalKey="+UUID.randomUUID(),bytes(open),token).statusCode()).isEqualTo(409);
        assertThat(send("/open?originalKey="+UUID.randomUUID(),bytes(result.path("open")),token).statusCode()).isEqualTo(409);
    }
    private HttpResponse<byte[]> send(String suffix,byte[] body,String bearer) throws Exception {
        var request=HttpRequest.newBuilder(URI.create(base+suffix)).timeout(Duration.ofSeconds(30)).header("X-Server-Recovery-Epoch","1");
        if(bearer!=null)request.header("Authorization","Bearer "+bearer);
        if(body==null)request.GET();else request.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofByteArray(body));
        var response=client.send(request.build(),HttpResponse.BodyHandlers.ofByteArray());
        Files.write(attempt.resolve("draft-http").resolve(String.format("%02d-%d.json",++sequence,response.statusCode())),response.body(),StandardOpenOption.CREATE_NEW);
        return response;
    }
    private static byte[] bytes(JsonNode value) { return SubmissionJson.canonical(value).getBytes(StandardCharsets.UTF_8); }
}
