package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.devices.DeviceCrypto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import static com.acrylic.outbound.submissions.SubmissionJson.*;
import static org.assertj.core.api.Assertions.*;

final class TemporaryRoundStopHttp {
    final TemporaryRoundConfirmationHttpFixture f;final TemporaryRoundStopWire wire;final KeyPair key;
    private final HttpClient http=HttpClient.newHttpClient();private int sequence;
    TemporaryRoundStopHttp(TemporaryRoundConfirmationHttpFixture f) throws Exception {
        this(f,deviceKey());
    }
    TemporaryRoundStopHttp(TemporaryRoundConfirmationHttpFixture f,KeyPair key) {
        this.f=f;this.key=key;
        var prior=f.http.database.jdbc.queryForList("SELECT payload FROM pda_audit_event WHERE origin_device_id=UUID_TO_BIN(?) ORDER BY local_seq",
            String.class,text(f.body,"freezeDeviceId")).stream().map(s->parse(s)).toList();
        wire=new TemporaryRoundStopWire(f.body,prior);
        String publicKey=Base64.getEncoder().encodeToString(key.getPublic().getEncoded());
        f.http.database.jdbc.update("UPDATE device SET public_key=?,key_sha256=? WHERE id=UUID_TO_BIN(?)",publicKey,
            DeviceCrypto.sha(publicKey),text(f.body,"freezeDeviceId"));
    }
    private static KeyPair deviceKey() throws Exception {
        var generator=KeyPairGenerator.getInstance("EC");generator.initialize(new java.security.spec.ECGenParameterSpec("secp256r1"));return generator.generateKeyPair();
    }
    ObjectNode verify() throws Exception {
        var audit=object().put("originDeviceId",text(f.body,"freezeDeviceId"));var events=audit.putArray("events");
        wire.records.stream().filter(r->"AUDIT".equals(r.item().itemKind())).forEach(r->events.add(r.body()));
        ok(send("pda/audit/events",audit,f.http.token),"audit");
        byte[] opened=ok(protocol("open",wire.openKey,wire.open()),"open");
        assertThat(ok(protocol("open",wire.openKey,wire.open()),"open replay")).isEqualTo(opened);
        for(var record:wire.records)raw(record);
        ok(protocol("page",wire.pageKey,wire.pageRequest()),"page");
        var stop=wire.stop(f.http.database.seed.get("warehouseId"),f.http.database.seed.get("operatorId"),key);
        ok(protocol("stop",wire.stopKey,stop),"stop proof");
        byte[] verified=ok(protocol("verify",wire.verifyKey,wire.verify()),"verify");
        assertThat(ok(protocol("verify",wire.verifyKey,wire.verify()),"verify replay")).isEqualTo(verified);
        var receipt=parse(verified);
        assertThat(receipt.at("/originalContext/clientOrderId").asText()).isEqualTo(text(f.body.path("source"),"clientOrderId"));
        return object().put("submissionId",text(f.body,"submissionId"))
            .put("reason","isolated technical stop; all owners retained").put("sourceEvidenceSetId",text(receipt,"evidenceSetId"))
            .put("verificationId",text(receipt,"evidenceVerificationId")).put("setSha256",wire.hashes.setHash());
    }
    HttpResponse<byte[]> stop(ObjectNode body,String token) throws Exception {
        return send("pda/temporary-orders/continuation-submissions/"+text(f.body,"submissionId")+"/stop",body,token);
    }
    private HttpResponse<byte[]> protocol(String action,UUID original,JsonNode body) throws Exception {
        return send("pda/temporary-source-evidence/"+action+query()+"&originalKey="+original
            +"&openOriginalKey="+wire.openKey+"&stopOriginalKey="+wire.stopKey,body,f.http.token);
    }
    private void raw(TemporaryRoundStopWire.Record record) throws Exception {
        var item=record.item();String route="pda/temporary-source-evidence/raw"+query()+"&openOriginalKey="+wire.openKey
            +"&originalObjectId="+item.originalObjectId()+"&kind="+item.objectKind()+"&byteCount="+item.byteCount()+"&bytesSha256="+item.itemBytesSha256();
        ok(send(route,canonical(record.body()).getBytes(java.nio.charset.StandardCharsets.UTF_8),"application/octet-stream",f.http.token),"raw");
    }
    private String query() {
        return "?warehouseId="+f.http.database.seed.get("warehouseId")+"&submissionId="+text(f.body,"submissionId")+"&workRound="+text(f.body,"workRound");
    }
    private HttpResponse<byte[]> send(String route,JsonNode body,String token) throws Exception {
        return send(route,canonical(body).getBytes(java.nio.charset.StandardCharsets.UTF_8),"application/json",token);
    }
    private HttpResponse<byte[]> send(String route,byte[] bytes,String content,String token) throws Exception {
        String name="stop-protocol-round-"+text(f.body,"workRound")+"-"+(++sequence);var dir=f.http.database.profile.attempt.resolve("http");
        Files.write(dir.resolve(name+"-request.bin"),bytes,StandardOpenOption.CREATE_NEW);
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+f.http.app.getWebServer().getPort()+"/api/v1/"+route))
            .timeout(java.time.Duration.ofSeconds(30)).header("Authorization","Bearer "+token).header("Content-Type",content)
            .header("X-Server-Recovery-Epoch","1").POST(HttpRequest.BodyPublishers.ofByteArray(bytes)).build();
        var response=http.send(request,HttpResponse.BodyHandlers.ofByteArray());
        Files.write(dir.resolve(name+"-response.json"),response.body(),StandardOpenOption.CREATE_NEW);
        Files.writeString(dir.resolve(name+"-status.txt"),Integer.toString(response.statusCode()));return response;
    }
    static byte[] ok(HttpResponse<byte[]> response,String label) {
        assertThat(response.statusCode()).as(label+": "+new String(response.body())).isEqualTo(200);return response.body();
    }
}
