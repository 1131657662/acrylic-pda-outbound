package com.acrylic.outbound.inventory;

import com.acrylic.outbound.exports.ExportModels;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Synthetic device identity; new order, claim, preparation, content, ACK and submission travel over real HTTP. */
final class ReturnedStockRedispatchHttp {
    private final ReturnedStockHttpFixture f;
    private final CorrectionExportHttpClient web;
    private final HttpClient pda = HttpClient.newHttpClient();
    private JsonNode tokens, assignment;
    private String order;
    ReturnedStockRedispatchHttp(ReturnedStockHttpFixture f) throws Exception {
        this.f = f;
        web = CorrectionExportHttpClient.login(f.original.context, new ExportModels.Scope(f.original.makerIdentity.user().id(),
            UUID.fromString(f.original.warehouse),UUID.fromString(f.original.order)));
    }
    void verify() throws Exception {
        createAndClaim(); prepare();
        var current = body("2");
        byte[] original = send("/pda/orders/"+order+"/submissions",current,tokens,null).body();
        var result = SubmissionJson.parse(original);
        assertThat(result.at("/data/status").asText()).isEqualTo("SUCCEEDED");
        assertThat(result.at("/data/inventoryDeducted").asBoolean()).isTrue();
        var replay = data(send("/pda/orders/"+order+"/submissions",current,tokens,null),200);
        assertThat(replay).isEqualTo(result.path("data"));
        String succeededOrder = order;
        Files.writeString(f.original.evidence.resolve("redispatch-command.json"),SubmissionJson.canonical(current));
        Files.write(f.original.evidence.resolve("redispatch-receipt.json"),original);
        assertThat(f.original.jdbc.queryForObject("SELECT COUNT(*) FROM dispatch_record WHERE order_id=UUID_TO_BIN(?)"
            + " AND bale_id=UUID_TO_BIN(?) AND dispatch_cycle=2",Integer.class,order,f.bale)).isEqualTo(1);
        createAndClaim(); prepare();
        var stale = data(send("/pda/orders/"+order+"/submissions",body("1"),tokens,null),200);
        assertThat(stale.path("status").asText()).isEqualTo("REVIEW_REQUIRED");
        assertThat(stale.path("issues").toString()).contains("DISPATCH_CYCLE_CONFLICT");
        assertThat(f.original.jdbc.queryForObject("SELECT COUNT(*) FROM dispatch_record WHERE order_id=UUID_TO_BIN(?)",Integer.class,order)).isZero();
        assertThat(f.original.jdbc.queryForObject("SELECT COUNT(*) FROM inventory_ledger WHERE dispatch_id IN"
            + " (SELECT id FROM dispatch_record WHERE order_id=UUID_TO_BIN(?))",Integer.class,succeededOrder)).isEqualTo(1);
        Files.writeString(f.original.evidence.resolve("old-cycle-refusal.json"),stale.toPrettyString());
    }
    private void createAndClaim() throws Exception {
        var baseline = data(web.get("/inventory/current"),200);
        var plan = SubmissionJson.object().put("vehicleNo","ISOLATED-RETURN").put("containerNo","").put("notes","new independent dispatch");
        plan.set("expectedActiveSnapshot",baseline.path("activeSnapshotId")); plan.set("expectedMappingRevision",baseline.path("mappingRevision"));
        String code = f.original.facts(f.original.dispatch).path("deliveryCode").asText();
        plan.putArray("lines").addObject().put("deliveryCode",code).put("plannedCount",1);
        order = data(web.post("/orders",plan,UUID.randomUUID()),200).path("orderId").asText();
        var publish = SubmissionJson.object().put("expectedVersion","1").put("expectedPolicyVersion","1").put("reason","ISOLATED new dispatch");
        data(web.post("/orders/"+order+"/publish",publish,UUID.randomUUID()),200);
        var identity = f.original.formal.pda;
        var login = identity.loginBody(identity.register());
        tokens = data(send("/pda/auth/login",login,SubmissionJson.object(),null),200);
        assignment = data(send("/pda/orders/"+order+"/assignments",identity.claimBody(),tokens,UUID.randomUUID()),200);
    }
    private void prepare() throws Exception {
        String base = "/pda/assignments/"+assignment.path("id").asText();
        var manifest = data(send(base+"/preparation",SubmissionJson.object(),tokens,null),200);
        var decoded = SubmissionJson.parse(manifest.path("manifest").asText());
        for (var part : decoded.path("parts")) {
            var request = request(base+"/content/"+part.path("name").asText()+"?bundleId="+manifest.path("bundleId").asText(),tokens).GET().build();
            var response = pda.send(request,HttpResponse.BodyHandlers.ofByteArray());
            assertThat(response.statusCode()).isEqualTo(200); assertThat(response.body()).isNotEmpty();
        }
        var ack = SubmissionJson.object().put("bundleId",manifest.path("bundleId").asText()).put("manifestSha256",manifest.path("manifestSha256").asText())
            .put("assignmentEpoch",assignment.path("epoch").asText()).put("inventorySnapshotId",assignment.path("inventorySnapshotId").asText())
            .put("activatedAt",Instant.now().toString());
        data(send(base+"/download-ack",ack,tokens,null),200);
        Files.writeString(f.original.evidence.resolve(order+"-preparation.json"),manifest.toPrettyString());
    }
    private ObjectNode body(String cycle) {
        var b = f.original.formal.body.deepCopy();
        String time = Instant.parse(assignment.path("issuedAt").asText()).plusMillis(100).toString();
        b.put("submissionId",UUID.randomUUID().toString()).put("assignmentId",assignment.path("id").asText())
            .put("assignmentEpoch",assignment.path("epoch").asText()).put("orderRevision",assignment.path("revision").asInt())
            .put("inventorySnapshotId",assignment.path("inventorySnapshotId").asText()).put("freezeDeviceId",tokens.path("deviceId").asText())
            .put("workRound",assignment.path("workRound").asText()).put("startedAt",time).put("closedAt",time)
            .put("expectedCount","1").put("expectedWeightGrams",Long.toString(f.original.originalDispatchWeight)).put("captureHighWatermark","1");
        ((ObjectNode)b.path("source")).put("sourceOrderId",order);
        ((ObjectNode)b.path("captureSummary")).put("received","1").put("saved","1");
        JsonNode selected = null;
        for (var row : b.path("scans")) if (row.path("baleId").asText().equals(f.bale)) selected = row;
        if (selected==null) throw new AssertionError("original bale missing");
        var scan = (ObjectNode) selected.deepCopy();
        var capture = ((ObjectNode)b.path("captures").get(0)).deepCopy();
        String id = UUID.randomUUID().toString();
        capture.put("id",id).put("orderId",order).put("assignmentId",assignment.path("id").asText()).put("originDeviceId",tokens.path("deviceId").asText())
            .put("operatorId",tokens.path("userId").asText()).put("authorizationId",assignment.path("offlineAuthorizationId").asText())
            .put("assignmentEpoch",assignment.path("epoch").asText()).put("orderRevision",assignment.path("revision").asInt()).put("confirmedAt",time)
            .put("rawInput",scan.path("barcode").asText()).put("barcode",scan.path("barcode").asText()).put("expectedDispatchCycle",cycle);
        scan.put("captureId",id).put("orderScanSeq","1").put("localSeq","3").put("savedAt",time).put("expectedDispatchCycle",cycle);
        b.putArray("captures").add(capture); b.putArray("scans").add(scan); events(b,id); return b;
    }
    private void events(ObjectNode b,String capture) {
        var events = b.putArray("events"); var types = List.of("RECEIVED","CONFIRM_REQUESTED","SAVED","FROZEN");
        for (int i=0;i<types.size();i++) events.add(SubmissionJson.object().put("id",UUID.randomUUID().toString())
            .put("captureId",i==3?null:capture).put("assignmentId",assignment.path("id").asText())
            .put("originDeviceId",tokens.path("deviceId").asText()).put("operatorId",tokens.path("userId").asText())
            .put("localSeq",Integer.toString(i+1)).put("type",types.get(i)).put("detail",i==0?"AUTO_AFTER_VALIDATION":i==3?b.path("submissionId").asText():"")
            .put("wallMs","1000").put("elapsedMs","1000").put("boot",1));
    }
    private HttpResponse<byte[]> send(String path,JsonNode body,JsonNode identity,UUID key) throws Exception {
        var request = request(path,identity).header("Content-Type","application/json").header("X-Server-Recovery-Epoch","1");
        if(key!=null) request.header("Idempotency-Key",key.toString());
        var response = pda.send(request.POST(HttpRequest.BodyPublishers.ofString(SubmissionJson.canonical(body))).build(),HttpResponse.BodyHandlers.ofByteArray());
        Files.writeString(f.original.evidence.resolve("redispatch-http.log"),"POST "+path+" "+response.statusCode()+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);
        assertThat(response.statusCode()).as("%s response %s",path,new String(response.body(),java.nio.charset.StandardCharsets.UTF_8)).isEqualTo(200);
        return response;
    }
    private HttpRequest.Builder request(String path,JsonNode identity) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:19434/api/v1"+path)).timeout(Duration.ofSeconds(40))
            .header("Authorization","Bearer "+identity.path("accessToken").asText());
    }
    private static JsonNode data(HttpResponse<byte[]> response,int status) { return CorrectionExportHttpClient.data(response,status); }
}
