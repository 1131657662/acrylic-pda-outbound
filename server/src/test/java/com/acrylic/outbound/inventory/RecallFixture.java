package com.acrylic.outbound.inventory;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class RecallFixture {
    final SubmissionFixture f;final ObjectNode command;final String key=UUID.randomUUID().toString();
    JsonNode recall;ObjectNode body;
    RecallFixture(SubmissionFixture f) throws Exception {
        this.f=f;command=SubmissionJson.object().put("type","RECALL").put("reason","主管核对车次，要求原机停止")
            .put("expectedVersion",new OrderTestDriver(f.web).detail(f.order).at("/order/rowVersion").asText());
    }
    MockHttpServletRequestBuilder request() { return post("/api/v1/orders/"+f.order+"/change-requests").header("Idempotency-Key",key).contentType("application/json").content(command.toString()); }
    void start(boolean empty) throws Exception { recall=f.web.data(request());body=recovery(empty); }
    ObjectNode recovery(boolean empty) {
        var b=f.body.deepCopy();b.remove("submissionId");String id=UUID.randomUUID().toString();
        b.put("recoveryPackageId",id).put("changeRequestId",recall.get("changeRequestId").asText()).put("orderId",f.order).put("unresolvedCount","0");
        if(empty) {
            b.put("expectedCount","0").put("expectedWeightGrams","0").put("captureHighWatermark","0");
            b.putArray("captures");b.putArray("scans");b.putArray("events");
            var summary=(ObjectNode)b.get("captureSummary");summary.fieldNames().forEachRemaining(k->summary.put(k,"0"));
        }
        var events=b.withArray("events");int last=events.size()+1;
        events.add(((ObjectNode)f.body.withArray("events").get(f.body.withArray("events").size()-1)).deepCopy().put("id",UUID.randomUUID().toString())
            .put("localSeq",Integer.toString(last)).put("type","RECALL_STOPPED").put("detail",id));
        b.set("auditEvents",events.deepCopy());b.putArray("auditCoverage").add(SubmissionJson.object()
            .put("originDeviceId",f.tokens.get("deviceId").asText()).put("afterSeq","0").put("throughSeq",Integer.toString(last)));
        return b;
    }
    MockHttpServletRequestBuilder upload(ObjectNode b) { return post("/api/v1/pda/recovery-packages").header("X-Server-Recovery-Epoch","1").contentType("application/json").content(b.toString()); }
    JsonNode upload() throws Exception { return f.pda.data(upload(body),f.tokens); }
    ObjectNode ackBody() { return SubmissionJson.object().put("recoveryPackageId",body.get("recoveryPackageId").asText())
        .put("payloadSha256",SubmissionJson.sha(body)).put("captureHighWatermark",body.get("captureHighWatermark").asText())
        .put("auditThroughSeq",body.at("/auditCoverage/0/throughSeq").asText()); }
    MockHttpServletRequestBuilder ack(ObjectNode b) { return post("/api/v1/pda/change-requests/"+recall.get("changeRequestId").asText()+"/stop-ack")
        .header("X-Server-Recovery-Epoch","1").contentType("application/json").content(b.toString()); }
    JsonNode stop() throws Exception { return f.pda.data(ack(ackBody()),f.tokens); }
    JsonNode read() throws Exception { return f.web.data(get("/api/v1/orders/"+f.order+"/change-requests")).get("request"); }
    ObjectNode cancelBody() throws Exception { return SubmissionJson.object().put("action","CANCEL_EMPTY").put("expectedVersion",read().get("rowVersion").asText())
        .put("recoveryPackageId",body.get("recoveryPackageId").asText()).put("payloadSha256",SubmissionJson.sha(body)).put("physicalEvidence","主管现场核验车厢空载，货物仍在原货位"); }
    MockHttpServletRequestBuilder cancel(ObjectNode b,String key) { return post("/api/v1/change-requests/"+recall.get("changeRequestId").asText()+"/resolve")
        .header("Idempotency-Key",key).contentType("application/json").content(b.toString()); }
}
