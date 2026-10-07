package com.acrylic.outbound.e2averify;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static com.acrylic.outbound.submissions.SubmissionJson.*;
import static org.assertj.core.api.Assertions.*;

/** Exact original bytes and HTTP results are retained for every request, including rejected requests. */
final class TemporaryRoundChunkHttp {
    private final ContinuationReceiptHttpFixture f;
    final byte[] bytes;final ObjectNode manifest;final String base;
    TemporaryRoundChunkHttp(TemporaryRoundConfirmationHttpFixture fixture) {
        f=fixture.http;bytes=canonical(fixture.body).getBytes(StandardCharsets.UTF_8);
        base="/api/v1/pda/temporary-orders/continuation-submissions/"+text(fixture.body,"submissionId");manifest=object();
        for(String field:List.of("schemaVersion","submissionId","source","assignmentId","continuationId","authorizationId",
            "freezeDeviceId","workRound","sourceRecoveryEpoch","temporaryContinuation"))manifest.set(field,fixture.body.get(field));
        manifest.put("payloadSha256",sha(fixture.body)).put("byteCount",Integer.toString(bytes.length))
            .put("chunkCount",Integer.toString((bytes.length+262143)/262144));
    }
    byte[] chunk(int index) { return Arrays.copyOfRange(bytes,index*262144,Math.min(bytes.length,(index+1)*262144)); }
    int count() { return (bytes.length+262143)/262144; }
    HttpResponse<byte[]> send(String path,byte[] body,Request request) throws Exception {
        var target=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+f.app.getWebServer().getPort()+path))
            .timeout(java.time.Duration.ofSeconds(40)).header("Authorization","Bearer "+request.token())
            .header("Content-Type",path.contains("/chunks/")?"application/octet-stream":"application/json")
            .header("X-Server-Recovery-Epoch",request.epoch()).header("X-Request-Id",request.label())
            .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
        var root=f.database.profile.attempt.resolve("http");
        Files.write(root.resolve(request.label()+"-request.bin"),body,StandardOpenOption.CREATE_NEW);
        var response=HttpClient.newHttpClient().send(target,HttpResponse.BodyHandlers.ofByteArray());
        Files.write(root.resolve(request.label()+"-response.json"),response.body(),StandardOpenOption.CREATE_NEW);
        Files.writeString(root.resolve(request.label()+"-status.txt"),Integer.toString(response.statusCode()),StandardOpenOption.CREATE_NEW);
        assertThat(response.statusCode()).as(request.label()+": "+new String(response.body(),StandardCharsets.UTF_8)).isEqualTo(request.status());return response;
    }
    HttpResponse<byte[]> send(String suffix,byte[] body,String label) throws Exception { return send(base+suffix,body,new Request(f.token,"1",label,200)); }
    byte[] manifestBytes() { return canonical(manifest).getBytes(StandardCharsets.UTF_8); }
    record Request(String token,String epoch,String label,int status) { }

    static void addInvalidInputs(ObjectNode body) {
        var captures=array(body,"captures");var events=array(body,"events");
        var freeze=(ObjectNode)events.remove(events.size()-1);var template=(ObjectNode)captures.get(0);
        for(int i=0;i<600;i++) {
            var capture=template.deepCopy().put("id",UUID.randomUUID().toString()).put("orderScanSeq",Integer.toString(captures.size()+1))
                .put("deviceCaptureSeq",Integer.toString(captures.size()+1)).put("clientOrderId",text(body.path("source"),"clientOrderId"))
                .put("authorizationId",text(body,"authorizationId")).put("authorizationEpoch",text(body,"authorizationEpoch"))
                .put("error","x".repeat(500));captures.add(capture);
            for(String type:List.of("RECEIVED","INVALID","FAILURE_ACKNOWLEDGED")) {
                var event=freeze.deepCopy().put("id",UUID.randomUUID().toString()).put("captureId",text(capture,"id"))
                    .put("localSeq",Integer.toString(events.size()+1)).put("type",type).put("detail","");events.add(event);
            }
        }
        freeze.put("localSeq",Integer.toString(events.size()+1));events.add(freeze);
        body.put("captureHighWatermark",Integer.toString(captures.size()));
        ((ObjectNode)body.path("captureSummary")).put("received",Integer.toString(captures.size())).put("invalid","601");
    }
}
