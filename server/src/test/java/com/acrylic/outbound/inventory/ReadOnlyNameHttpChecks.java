package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.pda.OfflineSigner;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.*;

public final class ReadOnlyNameHttpChecks {
    private static final String TEMP="/pda/temporary-preparations", IND="/pda/independent-credentials";
    private ReadOnlyNameHttpChecks() { }
    public record State(JsonNode tokens,UUID operator,UUID tempKey,JsonNode tempBody,JsonNode temp,
        UUID independentKey,JsonNode independentBody,JsonNode independent) { }
    public static State run(WebApplicationContext context,Path attempt) throws Exception {
        var w=new ReadOnlyNameHttpWire(context);var f=w.seed();UUID operator=f.web.identity().user().id();
        w.jdbc.update("UPDATE app_user SET display_name=? WHERE id=?","原姓名甲",Ids.bytes(operator));
        UUID tk=UUID.randomUUID(),ik=UUID.randomUUID();var tb=f.request();
        var ib=SubmissionJson.object().put("deviceId",f.device.id()).put("deviceKeySha256",ImportFiles.sha256(f.device.key().getPublic().getEncoded()));
        var t=w.post(TEMP,tb,tk,f.tokens);var i=w.post(IND,ib,ik,f.tokens);
        proof(t,"TEMP","authorizationId");proof(i,"INDEPENDENT","credentialId");
        var state=new State(f.tokens,operator,tk,tb,t,ik,ib,i);
        w.jdbc.update("UPDATE app_user SET display_name=? WHERE id=?","后改姓名乙",Ids.bytes(operator));
        replay(w,state);concurrent(w,state);rollback(w,state);denied(w,state);
        vector(w,f,state,attempt);missingName(w,state);
        Files.writeString(attempt.resolve("private/name-proof-restart.json"),context.getBean(ObjectMapper.class).writeValueAsString(state),StandardOpenOption.CREATE_NEW);
        return state;
    }
    public static void resumed(WebApplicationContext context,State s) throws Exception {
        replay(new ReadOnlyNameHttpWire(context),s);
    }
    private static void replay(ReadOnlyNameHttpWire w,State s) throws Exception {
        assertThat(w.post(TEMP,s.tempBody,s.tempKey,s.tokens)).isEqualTo(s.temp);
        assertThat(w.get(TEMP+"/"+s.temp.path("authorizationId").asText(),s.tokens).path("preparation")).isEqualTo(s.temp);
        assertThat(w.post(IND,s.independentBody,s.independentKey,s.tokens)).isEqualTo(s.independent);
        assertThat(w.get(IND+"/"+s.independent.path("credentialId").asText(),s.tokens)).isEqualTo(s.independent);
    }
    private static void concurrent(ReadOnlyNameHttpWire w,State s) throws Exception {
        var a=CompletableFuture.runAsync(()->uncheckedReplay(w,s));var b=CompletableFuture.runAsync(()->uncheckedReplay(w,s));
        a.get(45,TimeUnit.SECONDS);b.get(45,TimeUnit.SECONDS);
    }
    private static void uncheckedReplay(ReadOnlyNameHttpWire w,State s) {
        try { replay(w,s); } catch(Exception error) { throw new CompletionException(error); }
    }
    private static void proof(JsonNode receipt,String source,String id) {
        String token=receipt.path("operatorNameProof").asText();assertThat(token).isNotEmpty();
        var claims=SubmissionJson.parse(Base64.getUrlDecoder().decode(token.split("\\.")[1]));
        assertThat(claims.path("source").asText()).isEqualTo(source);
        assertThat(claims.path("authorizationId").asText()).isEqualTo(receipt.path(id).asText());
        assertThat(claims.path("displayName").asText()).isEqualTo("原姓名甲");
        assertThat(claims.has("scope") || claims.has("exp")).isFalse();
        assertThat(claims.path("originalSha256").asText()).isEqualTo(ImportFiles.sha256(receipt.path(source.equals("TEMP")?"offlineToken":"signedCredential").asText().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
    private static void rollback(ReadOnlyNameHttpWire w,State s) throws Exception {
        for(String path:List.of(TEMP,IND)) {
            String table=path.equals(TEMP)?"temporary_authorization":"independent_capture_credential";
            long before=w.jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Long.class);
            UUID key=UUID.randomUUID();
            w.jdbc.execute("CREATE TRIGGER synthetic_name_proof_last_write BEFORE INSERT ON operation_event FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='SYNTHETIC_NAME_PROOF_LAST_WRITE'");
            try {
                assertThat(w.call("POST",path,path.equals(TEMP)?s.tempBody:s.independentBody,key,s.tokens).statusCode()).isEqualTo(500);
                assertThat(w.jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Long.class)).isEqualTo(before);
                assertThat(w.jdbc.queryForObject("SELECT COUNT(*) FROM api_idempotency_request WHERE idempotency_key=?",Long.class,key.toString())).isZero();
            } finally { w.jdbc.execute("DROP TRIGGER synthetic_name_proof_last_write"); }
        }
    }
    private static void denied(ReadOnlyNameHttpWire w,State s) throws Exception {
        assertThat(w.call("POST",IND,s.independentBody,UUID.randomUUID(),null).statusCode()).isEqualTo(401);
        var changed=s.tempBody.deepCopy();((com.fasterxml.jackson.databind.node.ObjectNode)changed).put("policyVersion","999999");
        assertThat(w.call("POST",TEMP,changed,s.tempKey,s.tokens).statusCode()).isEqualTo(409);
    }
    private static void missingName(ReadOnlyNameHttpWire w,State s) throws Exception {
        w.jdbc.update("UPDATE app_user SET display_name=username WHERE id=?",Ids.bytes(s.operator));
        assertThat(w.post(TEMP,s.tempBody,UUID.randomUUID(),s.tokens).path("operatorNameProof").isNull()).isTrue();
        assertThat(w.post(IND,s.independentBody,UUID.randomUUID(),s.tokens).path("operatorNameProof").isNull()).isTrue();
        replay(w,s);
    }
    private static void vector(ReadOnlyNameHttpWire w,TemporaryFixture f,State s,Path attempt) throws Exception {
        var json=w.context.getBean(ObjectMapper.class);var fixture=json.createObjectNode();fixture.set("receipt",s.temp);
        fixture.set("signingKey",json.valueToTree(w.context.getBean(OfflineSigner.class).publicKey()));
        var session=fixture.putObject("session").put("serverRecoveryEpoch","1");session.set("capabilities",s.tokens.path("capabilities"));
        for(String field:List.of("userId","deviceId","warehouseId","userAuthVersion","deviceVersion"))session.set(field,s.tokens.path(field));
        var gzip=fixture.putObject("gzip");String id=s.temp.path("authorizationId").asText();
        for(String part:List.of("inventory","mapping","policy","rules","availability")) {
            var response=w.call("GET",TEMP+"/"+id+"/content/"+part+"?bundleId="+s.temp.path("bundleId").asText(),null,null,s.tokens);
            assertThat(response.statusCode()).isEqualTo(200);gzip.put(part,Base64.getEncoder().encodeToString(response.body()));
        }
        var ack=f.ack(s.temp).put("authorizationEpoch",f.manifest(s.temp).at("/identity/authorizationEpoch").asText())
            .put("activatedAt",java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS).toString());
        fixture.set("activationCommand",ack);fixture.set("activationReceipt",w.post(TEMP+"/"+id+"/download-ack",ack,UUID.randomUUID(),s.tokens));
        Files.writeString(attempt.resolve("private/name-proof-temp-vector.json"),json.writeValueAsString(fixture),StandardOpenOption.CREATE_NEW);
        Files.writeString(attempt.resolve("private/name-proof-independent-vector.json"),json.writeValueAsString(Map.of("receipt",s.independent,
            "signingKey",w.context.getBean(OfflineSigner.class).publicKey())),StandardOpenOption.CREATE_NEW);
    }
}
