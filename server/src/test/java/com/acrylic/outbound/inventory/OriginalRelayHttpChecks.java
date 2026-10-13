package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.relay.RelayBundleFixture;
import com.acrylic.outbound.submissions.SubmissionJson;
import java.nio.file.*;
import java.security.*;
import java.security.spec.*;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.*;

public final class OriginalRelayHttpChecks {
    private OriginalRelayHttpChecks() { }
    public static void verify(WebApplicationContext context,Path attempt) throws Exception {
        var evidence=attempt.resolve("original-relay");Files.createDirectories(evidence);
        positive(context,evidence);concurrent(context);tailRollback(context);evidenceOnly(context);identityRevocation(context);
        OriginalRelayBoundaryChecks.verify(context,evidence);
    }
    private static void positive(WebApplicationContext context,Path evidence) throws Exception {
        var f=new OriginalRelayHttpFixture(context);UUID receiveKey=UUID.randomUUID();
        var receipt=f.data(f.supervisor.archive("/original-relays",f.archive,receiveKey),200);
        assertThat(f.data(f.supervisor.archive("/original-relays",f.archive,receiveKey),200)).isEqualTo(receipt);
        assertThat(receipt.path("sourceTrust").asText()).isEqualTo("TRUSTED");String id=receipt.path("relayId").asText();
        assertThat(f.data(f.supervisor.get("/original-relays/"+id+"/submission"),200).path("state").asText()).isEqualTo("NOT_FOUND");
        UUID key=UUID.randomUUID();var result=f.data(f.process(id,key),200);
        assertThat(result.at("/businessReceipt/status").asText()).isEqualTo("SUCCEEDED");
        assertThat(f.data(f.process(id,key),200)).isEqualTo(result);assertThat(f.data(f.process(id,UUID.randomUUID()),200)).isEqualTo(result);
        assertThat(f.original.count("inventory_ledger")).isEqualTo(2);assertThat(f.original.count("dispatch_record")).isEqualTo(2);
        assertThat(f.data(f.pdaSubmit(f.original.body),200)).isEqualTo(result.path("businessReceipt"));
        var changed=f.original.body.deepCopy().put("shortShipmentReason","same ID different original");
        assertThat(f.supervisor.archive("/original-relays",f.bundle(changed),receiveKey).statusCode()).isEqualTo(409);
        String other=f.receive(f.bundle(changed)).path("relayId").asText();assertThat(f.process(other,UUID.randomUUID()).statusCode()).isEqualTo(409);
        assertThat(f.operator.archive("/original-relays",f.archive,UUID.randomUUID()).statusCode()).isEqualTo(403);
        assertThat(f.supervisor.get("/original-relays/"+id+"/original").body()).isEqualTo(f.archive);
        var returned=f.supervisor.get("/original-relays/results/"+result.path("resultId").asText());
        assertThat(SubmissionJson.parse(returned.body())).isEqualTo(result);signature(context,result);
        Files.write(evidence.resolve("original.zip"),f.archive);Files.write(evidence.resolve("receipt.json"),returned.body());
        var input=SubmissionJson.object().put("relayId",id).put("resultId",result.path("resultId").asText())
            .put("warehouseId",f.original.web.identity().warehouse().toString()).put("operatorId",f.relayUser.user().id().toString())
            .put("processKey",key.toString()).put("receiveKey",receiveKey.toString());
        input.set("body",f.original.body);Files.writeString(evidence.resolve("restart-input.json"),input.toString());
        OriginalRelayExportChecks.verify(context,f,evidence);
    }
    private static void concurrent(WebApplicationContext context) throws Exception {
        var f=new OriginalRelayHttpFixture(context);String id=f.receive(f.archive).path("relayId").asText();var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var pc=pool.submit(()->{start.await();return f.process(id,UUID.randomUUID());});
            var pda=pool.submit(()->{start.await();return f.pdaSubmit(f.original.body);});start.countDown();
            assertThat(f.data(pc.get(45,TimeUnit.SECONDS),200).path("businessReceipt")).isEqualTo(f.data(pda.get(45,TimeUnit.SECONDS),200));
        }
        assertThat(f.original.count("inventory_ledger")).isEqualTo(2);assertThat(f.original.count("order_result_version")).isEqualTo(1);
    }
    private static void tailRollback(WebApplicationContext context) throws Exception {
        var f=new OriginalRelayHttpFixture(context);String id=f.receive(f.archive).path("relayId").asText();UUID key=UUID.randomUUID();
        String trigger="relay_tail_"+UUID.randomUUID().toString().replace("-","");
        f.jdbc.execute("CREATE TRIGGER "+trigger+" BEFORE INSERT ON api_idempotency_request FOR EACH ROW BEGIN IF NEW.route_scope='relay.process' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='relay-tail'; END IF; END");
        try { assertThat(f.process(id,key).statusCode()).isEqualTo(500); }
        finally { f.jdbc.execute("DROP TRIGGER "+trigger); }
        assertThat(f.original.count("dispatch_record")).isZero();assertThat(f.original.count("inventory_ledger")).isZero();
        assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM original_relay_result WHERE relay_id=?",Integer.class,Ids.bytes(UUID.fromString(id)))).isZero();
        assertThat(f.data(f.process(id,key),200).at("/businessReceipt/status").asText()).isEqualTo("SUCCEEDED");
    }
    private static void evidenceOnly(WebApplicationContext context) throws Exception {
        var f=new OriginalRelayHttpFixture(context);String id=f.receive(f.bundle(null)).path("relayId").asText();
        assertThat(f.process(id,UUID.randomUUID()).statusCode()).isEqualTo(409);assertThat(f.original.count("dispatch_record")).isZero();
        var generator=KeyPairGenerator.getInstance("EC");generator.initialize(new ECGenParameterSpec("secp256r1"));
        byte[] unknown=RelayBundleFixture.build(RelayBundleFixture.files(f.order,f.original.body),generator.generateKeyPair());
        var receipt=f.receive(unknown);assertThat(receipt.path("sourceTrust").asText()).isEqualTo("UNVERIFIED");
        assertThat(f.process(receipt.path("relayId").asText(),UUID.randomUUID()).statusCode()).isEqualTo(409);
        assertThat(f.original.count("bale_commitment")).isZero();
    }
    private static void identityRevocation(WebApplicationContext context) throws Exception {
        var f=new OriginalRelayHttpFixture(context);String id=f.receive(f.archive).path("relayId").asText();
        f.jdbc.update("DELETE FROM user_role WHERE user_id=?",Ids.bytes(f.original.web.identity().user().id()));
        assertThat(f.process(id,UUID.randomUUID()).statusCode()).isEqualTo(403);assertThat(f.original.count("dispatch_record")).isZero();
        f.jdbc.update("DELETE FROM user_role WHERE user_id=?",Ids.bytes(f.relayUser.user().id()));
        assertThat(f.supervisor.get("/original-relays/"+id).statusCode()).isEqualTo(403);
    }
    private static void signature(WebApplicationContext context,com.fasterxml.jackson.databind.JsonNode result) throws Exception {
        var signer=context.getBean(com.acrylic.outbound.pda.OfflineSigner.class);String[] parts=result.path("signedRelayReceipt").asText().split("\\.");
        var key=KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(signer.publicKey().publicKey())));
        var verifier=Signature.getInstance("SHA256withRSA");verifier.initVerify(key);verifier.update((parts[0]+"."+parts[1]).getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        assertThat(verifier.verify(Base64.getUrlDecoder().decode(parts[2]))).isTrue();
        var claims=SubmissionJson.parse(Base64.getUrlDecoder().decode(parts[1]));assertThat(claims.path("aud").asText()).isEqualTo("acrylic-pda-relay-result");
        var unsigned=result.deepCopy();((com.fasterxml.jackson.databind.node.ObjectNode)unsigned).remove("signedRelayReceipt");assertThat(claims.path("receipt")).isEqualTo(unsigned);
    }
}
