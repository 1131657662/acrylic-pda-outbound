package com.acrylic.outbound.inventory;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/** Original Android STOP and real rejection remain unchanged; only a new isolated physical decision is made. */
public final class OpenTempQuarantineSource {
    private OpenTempQuarantineSource() { }
    public static void prepare(WebApplicationContext app,Path attempt) throws Exception {
        var jdbc=app.getBean(JdbcTemplate.class);
        var original=SubmissionJson.parse(Files.readAllBytes(attempt.resolve("original-android-result.json")));
        String submission=original.path("submissionId").asText();
        var row=jdbc.queryForMap("SELECT BIN_TO_UUID(c.warehouse_id) warehouse,s.status FROM submission s "
            +"JOIN submission_scope c ON c.id=s.scope_id WHERE s.id=UUID_TO_BIN(?)",submission);
        assertThat(row.get("status")).isEqualTo("REJECTED");
        originals(jdbc,original,submission);
        var actor=IdentityFixture.create(jdbc,app.getBean(PasswordEncoder.class),"WAREHOUSE_ADMIN");
        jdbc.update("INSERT INTO user_warehouse VALUES(?,UUID_TO_BIN(?))",com.acrylic.outbound.common.Ids.bytes(actor.user().id()),row.get("warehouse"));
        var http=new TemporaryDispositionHttp(attempt.resolve("open-temp-source-http")); http.login(actor.user().username());
        var facts=http.data("/temporary-commands/disposition-facts?warehouseId="+row.get("warehouse")+"&submissionId="+submission);
        var command=physical(jdbc,facts.path("facts"));
        byte[] receipt=http.command(command,200); assertThat(http.command(command,200)).containsExactly(receipt);
        verifyTemporaryReceipt(receipt,attempt.resolve("private/server-public.der"));
        assertThat(jdbc.queryForObject("SELECT close_mode FROM temporary_physical_disposition",Boolean.class)).isFalse();
        assertThat(jdbc.queryForObject("SELECT BIN_TO_UUID(current_submission_id) FROM submission_scope WHERE id=UUID_TO_BIN(?)",
            String.class,SubmissionJson.parse(receipt).at("/originalContext/scopeId").asText())).isEqualTo(submission);
        originals(jdbc,original,submission);
        Files.write(attempt.resolve("original-physical-receipt.json"),receipt);
    }
    private static ObjectNode physical(JdbcTemplate jdbc,com.fasterxml.jackson.databind.JsonNode facts) {
        var command=SubmissionJson.object().put("commandVersion",1).put("commandType","PHYSICAL_DISPOSITION")
            .put("commandId",UUID.randomUUID().toString()).put("transportRecoveryEpoch","1").put("reason","隔离核验原Android退审包，逐包隔离但保留原来源");
        for(String key:java.util.List.of("warehouseId","submissionId","workRound","authorizationId","freezeDeviceId","sourceRecoveryEpoch","source","expected"))
            command.set(key,facts.path(key).deepCopy());
        var proof=jdbc.queryForMap("SELECT BIN_TO_UUID(set_id) evidenceSetId,set_sha256 evidenceSetSha256,BIN_TO_UUID(id) verificationId,"
            +"CAST(verification_version AS CHAR) verificationVersion,CAST(algorithm_version AS CHAR) verificationAlgorithmVersion "
            +"FROM source_evidence_verification WHERE submission_id=UUID_TO_BIN(?) AND result='VERIFIED_COMPLETE_AT_STOP'",command.path("submissionId").asText());
        var expected=(ObjectNode)command.path("expected"); proof.forEach((key,value)->expected.put(key,value.toString()));
        var manifest=SubmissionJson.object().put("version",1); var items=manifest.putArray("items");
        jdbc.queryForList("SELECT BIN_TO_UUID(bale_id) bale FROM bale_commitment WHERE submission_id=UUID_TO_BIN(?) ORDER BY bale_id",
            String.class,command.path("submissionId").asText()).forEach(bale->items.addObject().put("baleId",bale).put("disposition","QUARANTINE"));
        assertThat(items.size()).isEqualTo(1);
        expected.put("dispositionManifestVersion","1").put("dispositionManifestSha256",SubmissionJson.sha(manifest));
        command.set("dispositionManifest",manifest); return command;
    }
    private static void originals(JdbcTemplate jdbc,com.fasterxml.jackson.databind.JsonNode original,String submission) {
        var receipts=jdbc.queryForList("SELECT BIN_TO_UUID(original_key) originalKey,original_response_bytes FROM source_evidence_intake_receipt WHERE submission_id=UUID_TO_BIN(?)",submission);
        assertThat(receipts).hasSize(original.path("receipts").size());
        for(var row:receipts) assertThat((byte[])row.get("original_response_bytes")).containsExactly(
            java.util.Base64.getDecoder().decode(original.path("receipts").path(row.get("originalKey").toString()).asText()));
    }
    private static void verifyTemporaryReceipt(byte[] bytes,Path publicKey) throws Exception {
        var body=SubmissionJson.parse(bytes); var parts=body.path("signedReceipt").asText().split("\\.",-1);
        assertThat(parts).hasSize(3);
        var header=SubmissionJson.parse(Base64.getUrlDecoder().decode(parts[0]));
        assertThat(header.path("alg").asText()).isEqualTo("RS256");
        var key=KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Files.readAllBytes(publicKey)));
        var verifier=Signature.getInstance("SHA256withRSA"); verifier.initVerify(key);
        verifier.update((parts[0]+"."+parts[1]).getBytes(StandardCharsets.US_ASCII));
        assertThat(verifier.verify(Base64.getUrlDecoder().decode(parts[2]))).isTrue();
        var claims=SubmissionJson.parse(Base64.getUrlDecoder().decode(parts[1]));
        assertThat(claims.path("iss").asText()).isEqualTo("acrylic-outbound");
        assertThat(claims.path("aud").asText()).isEqualTo("acrylic-r06-command-receipt-v1");
        var unsigned=((ObjectNode)body).deepCopy(); unsigned.remove("signedReceipt");
        assertThat(claims.path("receipt")).isEqualTo(unsigned);
    }
}
