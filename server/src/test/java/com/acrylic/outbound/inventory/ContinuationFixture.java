package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class ContinuationFixture {
    final RecallFixture r;final ObjectNode full;JsonNode ticket;JsonNode activation;
    ContinuationFixture(SubmissionFixture f) throws Exception {
        full=f.body.deepCopy();f.body.withArray("captures").remove(1);f.body.withArray("scans").remove(1);
        var events=f.body.withArray("events");while(events.size()>3)events.remove(events.size()-1);
        events.add(((ObjectNode)full.withArray("events").get(6)).deepCopy().put("localSeq","4"));
        f.body.put("expectedCount","1").put("expectedWeightGrams","300500").put("captureHighWatermark","1").put("shortShipmentReason","退审补扫测试");
        ((ObjectNode)f.body.get("captureSummary")).put("received","1").put("saved","1");
        f.submit();f.review("REJECT");r=new RecallFixture(f);r.start(false);r.upload();r.stop();
        f.jdbc.update("INSERT INTO user_role SELECT ?,id FROM role WHERE code='SUPERVISOR'",Ids.bytes(f.web.identity().user().id()));
    }
    ObjectNode requestBody() {
        var c=SubmissionJson.object().put("type","REOPEN_WORK").put("previousSubmissionId",r.f.id()).put("expectedWorkRound","1")
            .put("expectedRevision",1).put("recoveryPackageId",r.body.get("recoveryPackageId").asText())
            .put("requestedDeviceId",r.f.tokens.get("deviceId").asText()).put("reason","原车保留全部已装包，补扫剩余产品");
        var rows=c.putArray("dispositions");for(var s:r.body.withArray("scans"))if(s.get("status").asText().equals("ACTIVE"))
            rows.add(SubmissionJson.object().put("barcode",s.get("barcode").asText()).put("action","RETAIN").put("expectedDispatchCycle",s.get("expectedDispatchCycle").asText()));
        return c;
    }
    MockHttpServletRequestBuilder request(ObjectNode body,String key) { return post("/api/v1/orders/"+r.f.order+"/change-requests")
        .header("Idempotency-Key",key).contentType("application/json").content(body.toString()); }
    void request() throws Exception { ticket=r.f.web.data(request(requestBody(),UUID.randomUUID().toString())); }
    ObjectNode approveBody() throws Exception { return SubmissionJson.object().put("action","REOPEN_WORK").put("expectedVersion",r.read().get("rowVersion").asText())
        .put("retainedManifestSha256",ticket.get("retainedManifestSha256").asText()).put("physicalEvidence","主管现场核验：原车原货堆已装包全部保留，无卸货"); }
    MockHttpServletRequestBuilder approve(ObjectNode body,String key) { return post("/api/v1/change-requests/"+ticket.get("continuationId").asText()+"/resolve")
        .header("Idempotency-Key",key).contentType("application/json").content(body.toString()); }
    JsonNode approve() throws Exception { return r.f.web.data(approve(approveBody(),UUID.randomUUID().toString())); }
    ObjectNode activateBody() { return SubmissionJson.object().put("payloadSha256",SubmissionJson.sha(r.body))
        .put("retainedManifestSha256",ticket.get("retainedManifestSha256").asText()).put("nextOrderScanSeq",ticket.get("nextOrderScanSeq").asText())
        .put("planSha256",r.f.assignment.get("planSha256").asText()); }
    MockHttpServletRequestBuilder activate(ObjectNode body) { return post("/api/v1/pda/continuations/"+ticket.get("continuationId").asText()+"/activate")
        .header("X-Server-Recovery-Epoch","1").contentType("application/json").content(body.toString()); }
    void activate() throws Exception { activation=r.f.pda.data(activate(activateBody()),r.f.tokens); }
    ObjectNode second() {
        var body=r.f.body.deepCopy();var a=activation.get("assignment");String id=UUID.randomUUID().toString();
        String time=java.time.Instant.parse(a.get("issuedAt").asText()).plusMillis(200).toString();
        body.put("submissionId",id).put("assignmentId",a.get("id").asText()).put("assignmentEpoch",a.get("epoch").asText())
            .put("workRound","2").put("continuationId",ticket.get("continuationId").asText()).put("closedAt",time)
            .put("expectedCount","2").put("expectedWeightGrams","601500").put("captureHighWatermark","2").putNull("shortShipmentReason");
        ((ObjectNode)body.get("captureSummary")).put("received","2").put("saved","2");
        body.set("events",r.body.withArray("events").deepCopy());
        var c=((ObjectNode)full.withArray("captures").get(1)).deepCopy().put("assignmentId",a.get("id").asText())
            .put("assignmentEpoch",a.get("epoch").asText()).put("authorizationId",a.get("offlineAuthorizationId").asText()).put("confirmedAt",time);
        body.withArray("captures").add(c);body.withArray("scans").add(((ObjectNode)full.withArray("scans").get(1)).deepCopy().put("savedAt",time).put("localSeq","8"));
        for(int i=3;i<7;i++)body.withArray("events").add(((ObjectNode)full.withArray("events").get(i)).deepCopy()
            .put("id",UUID.randomUUID().toString()).put("assignmentId",a.get("id").asText()).put("localSeq",Integer.toString(i+3)).put("detail",i==6?id:full.withArray("events").get(i).get("detail").asText()));
        return body;
    }
}
